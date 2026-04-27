package com.lym.trigger.http.user.util;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;

import java.util.Date;

/**
 * JWT 工具类：快速集成版
 *
 * @author lym
 */
public class JwtUtil {

    private JwtUtil() {
    }

    public static String createToken(String secret,
                                     long expireSeconds,
                                     String userId,
                                     String tenantId,
                                     String username,
                                     String roleCode) {
        Date now = new Date();
        Date expireAt = new Date(now.getTime() + expireSeconds * 1000L);

        return JWT.create()
                .withIssuer("lym-agent")
                .withSubject(userId)
                .withIssuedAt(now)
                .withExpiresAt(expireAt)
                .withClaim("userId", userId)
                .withClaim("tenantId", tenantId)
                .withClaim("username", username)
                .withClaim("roleCode", roleCode)
                .sign(Algorithm.HMAC256(secret));
    }

    public static DecodedJWT verify(String token, String secret) {
        return JWT.require(Algorithm.HMAC256(secret))
                .withIssuer("lym-agent")
                .build()
                .verify(token);
    }

    public static String parseBearerToken(String authorization) {
        if (authorization == null || authorization.trim().isEmpty()) {
            return null;
        }
        if (authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }
        return authorization;
    }

}
