package com.smartlamp.controller;

import com.smartlamp.dto.ApiResponse;
import com.smartlamp.dto.LoginRequest;
import com.smartlamp.dto.LoginResponse;
import com.smartlamp.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@RequestBody LoginRequest request) {
        LoginResponse data = authService.login(request);
        if (data == null) {
            return ApiResponse.error(401, "用户名或密码错误");
        }
        return ApiResponse.success(data);
    }
}