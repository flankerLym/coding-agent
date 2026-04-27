package com.lym.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * Chat 用户登录响应 DTO
 *
 * @author lym
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ChatUserLoginResponseDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** JWT token */
    private String token;

    /** token 过期秒数 */
    private Long expireSeconds;

    /** 用户ID */
    private String userId;

    /** 租户ID */
    private String tenantId;

    /** 用户名 */
    private String username;

    /** 昵称 */
    private String nickname;

    /** 角色编码 */
    private String roleCode;

}
