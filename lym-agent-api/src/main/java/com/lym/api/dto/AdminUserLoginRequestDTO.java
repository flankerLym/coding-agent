package com.lym.api.dto;

import lombok.Data;

@Data
public class AdminUserLoginRequestDTO {
    private String username;
    private String password;
}