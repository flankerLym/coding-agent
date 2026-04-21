package com.lym.trigger.http.admin;

import com.lym.api.dto.AdminUserLoginRequestDTO;
import com.lym.api.response.Response;

import com.lym.types.enums.ResponseCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import com.lym.api.IAdminUserService;
import java.util.HashMap;
import java.util.Map;

/**
 * 管理员登录控制器
 * @author lym
 * @description 管理员用户登录、验证接口
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/admin-user")
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.OPTIONS})
public class LoginController implements IAdminUserService {

    @Override
    @PostMapping("/validate-login")
    public Response<Boolean> validateAdminUserLogin(@RequestBody AdminUserLoginRequestDTO request) {
        try {
            log.info("管理员登录请求: {}", request);

            // 登录校验
            boolean success = "admin".equals(request.getUsername()) &&
                    "123456".equals(request.getPassword());

            if (!success) {
                return Response.<Boolean>builder()
                        .code(ResponseCode.SUCCESS.getCode())
                        .info("账号或密码错误")
                        .data(false)
                        .build();
            }

            return Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(true)
                    .build();

        } catch (Exception e) {
            log.error("管理员登录异常", e);
            return Response.<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(false)
                    .build();
        }
    }

}