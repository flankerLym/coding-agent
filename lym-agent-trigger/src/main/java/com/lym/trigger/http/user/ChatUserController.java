package com.lym.trigger.http.user;

import com.alibaba.fastjson.JSON;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.lym.ai.infrastructure.dao.IChatUserDao;
import com.lym.ai.infrastructure.dao.po.ChatUser;
import com.lym.api.dto.AdminUserLoginRequestDTO;
import com.lym.api.dto.ChatUserLoginResponseDTO;
import com.lym.api.response.Response;
import com.lym.trigger.http.user.util.JwtUtil;
import com.lym.types.enums.ResponseCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * Chat 用户登录接口：JWT 快速集成版
 *
 * @author lym
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/chat/user")
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.OPTIONS})
public class ChatUserController {

    @Resource
    private IChatUserDao chatUserDao;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Value("${auth.jwt.secret:lym-agent-jwt-secret-change-me-please-2026}")
    private String jwtSecret;

    @Value("${auth.jwt.expire-seconds:7200}")
    private Long jwtExpireSeconds;

    @PostMapping("login")
    public Response<ChatUserLoginResponseDTO> login(@RequestBody AdminUserLoginRequestDTO request) {
        log.info("Chat用户登录请求开始，请求信息：{}", JSON.toJSONString(request));

        if (request == null
                || request.getUsername() == null || request.getUsername().trim().isEmpty()
                || request.getPassword() == null || request.getPassword().trim().isEmpty()) {
            return Response.<ChatUserLoginResponseDTO>builder()
                    .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                    .info("用户名或密码不能为空")
                    .data(null)
                    .build();
        }

        String tenantId = "default";
        String username = request.getUsername().trim();

        ChatUser chatUser = chatUserDao.queryByUsername(tenantId, username);
        if (chatUser == null) {
            return Response.<ChatUserLoginResponseDTO>builder()
                    .code(ResponseCode.LOGIN_FAILED.getCode())
                    .info("用户名或密码错误")
                    .data(null)
                    .build();
        }

        if (chatUser.getStatus() == null || chatUser.getStatus() != 1) {
            return Response.<ChatUserLoginResponseDTO>builder()
                    .code(ResponseCode.LOGIN_FAILED.getCode())
                    .info("账号不可用，请联系管理员")
                    .data(null)
                    .build();
        }

        boolean passwordMatched = passwordEncoder.matches(request.getPassword(), chatUser.getPasswordHash());
        if (!passwordMatched) {
            return Response.<ChatUserLoginResponseDTO>builder()
                    .code(ResponseCode.LOGIN_FAILED.getCode())
                    .info("用户名或密码错误")
                    .data(null)
                    .build();
        }

        String token = JwtUtil.createToken(
                jwtSecret,
                jwtExpireSeconds,
                chatUser.getUserId(),
                chatUser.getTenantId(),
                chatUser.getUsername(),
                chatUser.getRoleCode()
        );

        chatUserDao.updateLastLoginTime(chatUser.getUserId());

        return Response.<ChatUserLoginResponseDTO>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info("登录成功")
                .data(ChatUserLoginResponseDTO.builder()
                        .token(token)
                        .expireSeconds(jwtExpireSeconds)
                        .userId(chatUser.getUserId())
                        .tenantId(chatUser.getTenantId())
                        .username(chatUser.getUsername())
                        .nickname(chatUser.getNickname())
                        .roleCode(chatUser.getRoleCode())
                        .build())
                .build();
    }

    @GetMapping("me")
    public Response<ChatUserLoginResponseDTO> me(HttpServletRequest request) {
        DecodedJWT jwt = (DecodedJWT) request.getAttribute("jwtUser");
        if (jwt == null) {
            return Response.<ChatUserLoginResponseDTO>builder()
                    .code(ResponseCode.LOGIN_FAILED.getCode())
                    .info("未登录或登录已过期")
                    .data(null)
                    .build();
        }

        return Response.<ChatUserLoginResponseDTO>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info("查询成功")
                .data(ChatUserLoginResponseDTO.builder()
                        .userId(jwt.getClaim("userId").asString())
                        .tenantId(jwt.getClaim("tenantId").asString())
                        .username(jwt.getClaim("username").asString())
                        .roleCode(jwt.getClaim("roleCode").asString())
                        .build())
                .build();
    }

    @PostMapping("logout")
    public Response<Boolean> logout() {
        // JWT 无状态版退出：前端删除 token 即可。
        // 如需服务端强制失效，再接 Redis 黑名单。
        return Response.<Boolean>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info("退出成功")
                .data(true)
                .build();
    }

}
