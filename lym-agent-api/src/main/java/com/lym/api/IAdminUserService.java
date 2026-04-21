package com.lym.api;

import com.lym.api.dto.AdminUserLoginRequestDTO;
import com.lym.api.response.Response;

public interface IAdminUserService {

    /**
     * 管理员登录验证
     */
    Response<Boolean> validateAdminUserLogin(AdminUserLoginRequestDTO request);

}