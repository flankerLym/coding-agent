package com.lym.domain.agent.service.advisors;

import com.lym.domain.agent.adapter.repository.IAgentConversationRepository;
import com.lym.domain.agent.model.entity.AgentMessageEntity;
import com.lym.domain.agent.model.entity.AgentSessionEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 对话记录 Advisor。
 *
 * <p>职责边界：</p>
 * <ul>
 *     <li>before：保存用户提问到 agent_message，并确保 agent_session 存在；</li>
 *     <li>after：保存模型回答到 agent_message；</li>
 *     <li>仅写 agent_session、agent_message 两张表，不写 agent_run 或其他表。</li>
 * </ul>
 *
 * <p>上下文参数兼容 camelCase 和 snake_case：tenantId/tenant_id、userId/user_id、
 * projectId/project_id、aiAgentId/agentId/agent_id、sessionId/session_id、requestId/request_id、
 * intent/currentIntent、skillCode/skill_code。调用层没有显式传入时使用安全默认值，避免落库空字段失败。</p>
 */
@Slf4j
public class ConversationRecordAdvisor implements BaseAdvisor {

    /** 在 ChatClientRequest.context 中透传 requestId，保证 before/after 共用同一请求ID */
    private static final String CONTEXT_RECORD_REQUEST_ID = "agent_record_request_id";

    /** 标记用户消息已保存，避免同一个请求在 Advisor 链路中重复保存 */
    private static final String CONTEXT_USER_MESSAGE_SAVED = "agent_record_user_message_saved";

    private final IAgentConversationRepository agentConversationRepository;

    /**
     * ChatClient 所属客户端ID。若调用层没有传入 aiAgentId/agentId，则作为兜底 agentId 使用。
     */
    private final String defaultAgentId;

    public ConversationRecordAdvisor(IAgentConversationRepository agentConversationRepository, String defaultAgentId) {
        this.agentConversationRepository = agentConversationRepository;
        this.defaultAgentId = defaultIfBlank(defaultAgentId, "unknown-agent");
    }

    @Override
    public ChatClientRequest before(ChatClientRequest chatClientRequest, AdvisorChain advisorChain) {
        Map<String, Object> context = new HashMap<>(chatClientRequest.context());
        ConversationMetadata metadata = buildMetadata(context);
        context.put(CONTEXT_RECORD_REQUEST_ID, metadata.requestId());

        String userText = "";
        try {
            userText = chatClientRequest.prompt().getUserMessage().getText();
        } catch (Exception e) {
            log.warn("获取用户提问失败，将使用空文本保存对话记录", e);
        }

        try {
            // 1. 先确保会话存在，只写 agent_session 表。
            agentConversationRepository.saveAgentSession(AgentSessionEntity.builder()
                    .sessionId(metadata.sessionId())
                    .tenantId(metadata.tenantId())
                    .userId(metadata.userId())
                    .projectId(metadata.projectId())
                    .agentId(metadata.agentId())
                    .currentIntent(metadata.intent())
                    .contextSummary(summary(userText))
                    .status(1)
                    .build());

            // 2. before 保存用户提问，只写 agent_message 表。
            if (!Boolean.TRUE.equals(context.get(CONTEXT_USER_MESSAGE_SAVED))) {
                agentConversationRepository.saveAgentMessage(AgentMessageEntity.builder()
                        .messageId(newMessageId())
                        .requestId(metadata.requestId())
                        .sessionId(metadata.sessionId())
                        .tenantId(metadata.tenantId())
                        .userId(metadata.userId())
                        .projectId(metadata.projectId())
                        .roleType("user")
                        .content(defaultIfBlank(userText, ""))
                        .contentSummary(summary(userText))
                        .intent(metadata.intent())
                        .skillCode(metadata.skillCode())
                        .build());
                context.put(CONTEXT_USER_MESSAGE_SAVED, true);
            }
        } catch (Exception e) {
            // 对话记录不能影响主流程回答，因此只记录日志，不向上抛出。
            log.error("保存用户提问对话记录失败，sessionId={}, requestId={}", metadata.sessionId(), metadata.requestId(), e);
        }

        return ChatClientRequest.builder()
                .prompt(chatClientRequest.prompt())
                .context(context)
                .build();
    }

    @Override
    public ChatClientResponse after(ChatClientResponse chatClientResponse, AdvisorChain advisorChain) {
        Map<String, Object> context = new HashMap<>(chatClientResponse.context());
        ConversationMetadata metadata = buildMetadata(context);
        String answerText = extractAssistantText(chatClientResponse);
        saveAssistantMessage(metadata, answerText);

        return ChatClientResponse.builder()
                .chatResponse(chatClientResponse.chatResponse())
                .context(context)
                .build();
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
        ChatClientRequest beforeRequest = this.before(chatClientRequest, callAdvisorChain);
        ChatClientResponse chatClientResponse = callAdvisorChain.nextCall(beforeRequest);
        return this.after(chatClientResponse, callAdvisorChain);
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest, StreamAdvisorChain streamAdvisorChain) {
        ChatClientRequest beforeRequest = this.before(chatClientRequest, streamAdvisorChain);
        AtomicReference<ConversationMetadata> metadataRef = new AtomicReference<>(buildMetadata(beforeRequest.context()));
        StringBuilder answerBuilder = new StringBuilder();

        return streamAdvisorChain.nextStream(beforeRequest)
                .doOnNext(response -> {
                    metadataRef.set(buildMetadata(response.context()));
                    String chunk = extractAssistantText(response);
                    if (chunk != null && !chunk.isBlank()) {
                        answerBuilder.append(chunk);
                    }
                })
                .doOnComplete(() -> saveAssistantMessage(metadataRef.get(), answerBuilder.toString()))
                .doOnError(e -> log.error("流式回答过程中保存对话记录失败", e));
    }

    @Override
    public int getOrder() {
        // 优先于 RAG 等 Advisor 执行 before，保存用户最原始提问。
        return -1000;
    }

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

    private void saveAssistantMessage(ConversationMetadata metadata, String answerText) {
        try {
            agentConversationRepository.saveAgentMessage(AgentMessageEntity.builder()
                    .messageId(newMessageId())
                    .requestId(metadata.requestId())
                    .sessionId(metadata.sessionId())
                    .tenantId(metadata.tenantId())
                    .userId(metadata.userId())
                    .projectId(metadata.projectId())
                    .roleType("assistant")
                    .content(defaultIfBlank(answerText, ""))
                    .contentSummary(summary(answerText))
                    .intent(metadata.intent())
                    .skillCode(metadata.skillCode())
                    .build());
        } catch (Exception e) {
            // 对话记录不能影响模型回答返回。
            log.error("保存模型回答对话记录失败，sessionId={}, requestId={}", metadata.sessionId(), metadata.requestId(), e);
        }
    }

    private ConversationMetadata buildMetadata(Map<String, Object> context) {
        String requestId = firstNotBlank(context,
                CONTEXT_RECORD_REQUEST_ID, "request_id", "requestId");
        if (isBlank(requestId)) {
            requestId = newMessageId();
            context.put(CONTEXT_RECORD_REQUEST_ID, requestId);
        }

        String tenantId = defaultIfBlank(firstNotBlank(context, "tenant_id", "tenantId"), "default");
        String userId = defaultIfBlank(firstNotBlank(context, "user_id", "userId"), "anonymous");
        String projectId = firstNotBlank(context, "project_id", "projectId");
        String agentId = defaultIfBlank(firstNotBlank(context, "aiAgentId", "agentId", "agent_id"), defaultAgentId);
        String sessionId = defaultIfBlank(firstNotBlank(context,
                "session_id", "sessionId", "conversation_id", "conversationId", "chat_memory_conversation_id"),
                buildDefaultSessionId(tenantId, userId, agentId));
        String intent = firstNotBlank(context, "current_intent", "currentIntent", "intent");
        String skillCode = firstNotBlank(context, "skill_code", "skillCode");

        return new ConversationMetadata(requestId, sessionId, tenantId, userId, projectId, agentId, intent, skillCode);
    }

    private String extractAssistantText(ChatClientResponse chatClientResponse) {
        try {
            ChatResponse chatResponse = chatClientResponse.chatResponse();
            if (chatResponse == null || chatResponse.getResult() == null || chatResponse.getResult().getOutput() == null) {
                return "";
            }
            String text = chatResponse.getResult().getOutput().getText();
            return defaultIfBlank(text, "");
        } catch (Exception e) {
            log.warn("获取模型回答文本失败，将使用空文本保存", e);
            return "";
        }
    }

    private String firstNotBlank(Map<String, Object> context, String... keys) {
        if (context == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            Object value = context.get(key);
            if (value != null && !value.toString().isBlank()) {
                return value.toString();
            }
        }
        return null;
    }

    private String buildDefaultSessionId(String tenantId, String userId, String agentId) {
        return String.join("_", defaultIfBlank(tenantId, "default"), defaultIfBlank(userId, "anonymous"), defaultIfBlank(agentId, "unknown-agent"));
    }

    private String newMessageId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String summary(String content) {
        if (content == null) {
            return null;
        }
        String normalized = content.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 200 ? normalized : normalized.substring(0, 200);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String defaultIfBlank(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value;
    }

    private record ConversationMetadata(String requestId,
                                        String sessionId,
                                        String tenantId,
                                        String userId,
                                        String projectId,
                                        String agentId,
                                        String intent,
                                        String skillCode) {
    }

}
