package com.lym.ai.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 聊天系统用户表 PO
 *
 * @author lym
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ChatUser {

    private Long id;

    private String userId;

    private String tenantId;

    private String username;

    private String passwordHash;

    private String nickname;

    private String email;

    private String mobile;

    private String avatar;

    private String roleCode;

    /**
     * 状态：0禁用，1启用，2锁定
     */
    private Integer status;

    private LocalDateTime lastLoginTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /**
     * 逻辑删除：0未删除，1已删除
     */
    private Integer deleted;

}
