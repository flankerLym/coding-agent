package com.lym.ai.infrastructure.adapter.repository;

import com.lym.ai.infrastructure.config.LegalWebCodeSearchProperties;
import com.lym.domain.agent.adapter.repository.ILegalCodeSearchRepository;
import com.lym.domain.agent.model.entity.LegalCodeSearchCommandEntity;
import com.lym.domain.agent.model.entity.LegalCodeSearchResultEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Repository
@RequiredArgsConstructor
public class LegalCodeSearchRepository implements ILegalCodeSearchRepository {

    private static final Pattern ARTICLE_PATTERN =
            Pattern.compile("第[一二三四五六七八九十百千万〇零0-9]+条");

    private final LegalWebCodeSearchProperties properties;

    private final Map<String, CachedPage> cache = new ConcurrentHashMap<>();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Override
    public List<LegalCodeSearchResultEntity> searchLegalCode(LegalCodeSearchCommandEntity command) {
        if (command == null) {
            return List.of();
        }

        int limit = normalizeTopK(command.getTopK());

        List<String> lawCodeList = split(command.getLawCodes());
        List<String> keywordList = split(command.getKeywords());
        Set<String> articleNoSet = buildArticleNoSet(command.getArticleNo());

        if (lawCodeList.isEmpty()) {
            return List.of();
        }

        if (keywordList.isEmpty() && articleNoSet.isEmpty()) {
            return List.of();
        }

        List<LegalCodeSearchResultEntity> resultList = new ArrayList<>();

        for (String lawCode : lawCodeList) {
            LegalWebCodeSearchProperties.DocumentConfig document =
                    properties.getDocuments().get(lawCode);

            if (document == null || Boolean.FALSE.equals(document.getEnabled())) {
                continue;
            }

            if (document.getUrl() == null || document.getUrl().isBlank()) {
                continue;
            }

            try {
                String text = getPageText(lawCode, document);
                List<ArticleBlock> articleBlocks = splitArticleBlocks(text);

                for (ArticleBlock block : articleBlocks) {
                    int score = calcScore(block, keywordList, articleNoSet);
                    if (score <= 0) {
                        continue;
                    }

                    resultList.add(
                            LegalCodeSearchResultEntity.builder()
                                    .lawCode(lawCode)
                                    .lawName(document.getName())
                                    .sourceUrl(document.getUrl())
                                    .articleNo(block.articleNo())
                                    .content(limitContent(block.content(), 900))
                                    .matchType(matchType(block, articleNoSet))
                                    .score(score)
                                    .build()
                    );
                }

            } catch (Exception e) {
                log.warn("法条网页检索失败 lawCode:{} url:{}", lawCode, document.getUrl(), e);
            }
        }

        return resultList.stream()
                .sorted(Comparator.comparing(LegalCodeSearchResultEntity::getScore).reversed())
                .limit(limit)
                .toList();
    }

    private String getPageText(String lawCode,
                               LegalWebCodeSearchProperties.DocumentConfig document) throws Exception {
        long now = System.currentTimeMillis();

        CachedPage cachedPage = cache.get(lawCode);
        if (cachedPage != null && cachedPage.expireAt() > now) {
            return cachedPage.text();
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(document.getUrl()))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", "LegalFlow-WebCodeSearch/1.0")
                .GET()
                .build();

        HttpResponse<InputStream> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RuntimeException("网页请求失败，status=" + response.statusCode());
        }

        byte[] bytes = readBytes(response.body(), properties.getMaxPageSizeBytes());
        String html = new String(bytes, StandardCharsets.UTF_8);
        String text = htmlToText(html);

        cache.put(
                lawCode,
                new CachedPage(text, now + properties.getCacheSeconds() * 1000L)
        );

        return text;
    }

    private byte[] readBytes(InputStream inputStream, long maxBytes) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        byte[] buffer = new byte[8192];
        long total = 0;

        int len;
        while ((len = inputStream.read(buffer)) != -1) {
            total += len;

            if (total > maxBytes) {
                throw new RuntimeException("网页内容过大，超过限制：" + maxBytes);
            }

            outputStream.write(buffer, 0, len);
        }

        return outputStream.toByteArray();
    }

    private String htmlToText(String html) {
        if (html == null) {
            return "";
        }

        String text = html
                .replaceAll("(?is)<script.*?>.*?</script>", " ")
                .replaceAll("(?is)<style.*?>.*?</style>", " ")
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</p>", "\n")
                .replaceAll("(?i)</div>", "\n")
                .replaceAll("<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&#12288;", " ")
                .replaceAll("[ \\t\\r]+", " ")
                .replaceAll("\\n\\s*\\n+", "\n");

        return text.replaceAll(
                "(第[一二三四五六七八九十百千万〇零0-9]+条)",
                "\n$1"
        ).trim();
    }

    private List<ArticleBlock> splitArticleBlocks(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        Matcher matcher = ARTICLE_PATTERN.matcher(text);

        List<Integer> starts = new ArrayList<>();
        List<String> articleNos = new ArrayList<>();

        while (matcher.find()) {
            starts.add(matcher.start());
            articleNos.add(matcher.group());
        }

        if (starts.isEmpty()) {
            return List.of(new ArticleBlock(null, text));
        }

        List<ArticleBlock> blocks = new ArrayList<>();

        for (int i = 0; i < starts.size(); i++) {
            int start = starts.get(i);
            int end = i + 1 < starts.size() ? starts.get(i + 1) : text.length();

            String content = text.substring(start, end).trim();
            if (!content.isBlank()) {
                blocks.add(new ArticleBlock(articleNos.get(i), content));
            }
        }

        return blocks;
    }

    private int calcScore(ArticleBlock block,
                          List<String> keywords,
                          Set<String> articleNoSet) {
        int score = 0;

        if (block.articleNo() != null && articleNoSet.contains(block.articleNo())) {
            score += 1000;
        }

        for (String keyword : keywords) {
            if (block.content().contains(keyword)) {
                score += 30;
            }
        }

        if (!keywords.isEmpty() && keywords.stream().allMatch(block.content()::contains)) {
            score += 150;
        }

        return score;
    }

    private String matchType(ArticleBlock block, Set<String> articleNoSet) {
        if (block.articleNo() != null && articleNoSet.contains(block.articleNo())) {
            return "article_no";
        }
        return "keyword";
    }

    private List<String> split(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }

        return Arrays.stream(value.split("[,，、\\s]+"))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .distinct()
                .toList();
    }

    private Set<String> buildArticleNoSet(String articleNo) {
        if (articleNo == null || articleNo.isBlank()) {
            return Set.of();
        }

        Set<String> set = new HashSet<>();
        String value = articleNo.trim();

        set.add(value);

        if (value.matches("\\d+")) {
            set.add("第" + value + "条");
            set.add("第" + toChineseNumber(Integer.parseInt(value)) + "条");
        }

        if (value.matches("第\\d+条")) {
            String number = value.replace("第", "").replace("条", "");
            set.add("第" + toChineseNumber(Integer.parseInt(number)) + "条");
        }

        return set;
    }

    private String toChineseNumber(int number) {
        if (number <= 0 || number > 999) {
            return String.valueOf(number);
        }

        String[] nums = {"零", "一", "二", "三", "四", "五", "六", "七", "八", "九"};

        if (number < 10) {
            return nums[number];
        }

        if (number < 20) {
            return "十" + (number % 10 == 0 ? "" : nums[number % 10]);
        }

        if (number < 100) {
            return nums[number / 10] + "十" + (number % 10 == 0 ? "" : nums[number % 10]);
        }

        int hundred = number / 100;
        int rest = number % 100;

        if (rest == 0) {
            return nums[hundred] + "百";
        }

        if (rest < 10) {
            return nums[hundred] + "百零" + nums[rest];
        }

        return nums[hundred] + "百" + toChineseNumber(rest);
    }

    private int normalizeTopK(Integer topK) {
        if (topK == null || topK <= 0) {
            return 5;
        }

        return Math.min(topK, 20);
    }

    private String limitContent(String content, int maxLength) {
        if (content == null) {
            return "";
        }

        return content.length() <= maxLength ? content : content.substring(0, maxLength) + "...";
    }

    private record CachedPage(String text, long expireAt) {
    }

    private record ArticleBlock(String articleNo, String content) {
    }
}