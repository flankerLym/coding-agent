package com.lym.trigger.http.user.interceptor;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.lym.trigger.http.user.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * JWT 鉴权拦截器。
 *
 * 注意：OPTIONS 必须放行，否则 63344 -> 8099 的跨域预检会被拦截。
 */
@Component
public class JwtAuthInterceptor implements HandlerInterceptor {

    @Value("${auth.jwt.secret:lym-agent-jwt-secret-change-me-please-2026}")
    private String jwtSecret;

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String authorization = request.getHeader("Authorization");
        String token = JwtUtil.parseBearerToken(authorization);

        if (token == null || token.trim().isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":\"0003\",\"info\":\"未登录或登录已过期\",\"data\":null}");
            return false;
        }

        try {
            DecodedJWT jwt = JwtUtil.verify(token, jwtSecret);
            request.setAttribute("jwtUser", jwt);
            request.setAttribute("userId", jwt.getClaim("userId").asString());
            request.setAttribute("tenantId", jwt.getClaim("tenantId").asString());
            request.setAttribute("username", jwt.getClaim("username").asString());
            request.setAttribute("roleCode", jwt.getClaim("roleCode").asString());
            return true;
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":\"0003\",\"info\":\"登录已过期，请重新登录\",\"data\":null}");
            return false;
        }
    }
}
