package com.smartlamp.controller;

import com.smartlamp.dto.ApiResponse;
import com.smartlamp.dto.CreateUserRequest;
import com.smartlamp.dto.UpdateUserRoleRequest;
import com.smartlamp.dto.UserDTO;
import com.smartlamp.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('admin')")
public class UserController {

    @Autowired
    private UserService userService;

    // GET /api/users
    @GetMapping
    public ApiResponse<List<UserDTO>> listUsers() {
        return ApiResponse.success(userService.getAllUsers());
    }

    // POST /api/users
    @PostMapping
    public ApiResponse<Void> createUser(@RequestBody CreateUserRequest request) {
        // 参数校验
        if (request.getUsername() == null || request.getUsername().isBlank()) {
            return ApiResponse.error(400, "用户名不能为空");
        }
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            return ApiResponse.error(400, "密码不能为空");
        }
        if (request.getRole() == null || !request.getRole().matches("admin|municipal|operator")) {
            return ApiResponse.error(400, "角色必须为 admin / municipal / operator");
        }
        boolean success = userService.createUser(request.getUsername(), request.getPassword(), request.getRole());
        if (!success) {
            return ApiResponse.error(400, "用户名已存在");
        }
        return ApiResponse.success(null);
    }

    // PUT /api/users/{id}/role
    @PutMapping("/{id}/role")
    public ApiResponse<Void> updateRole(@PathVariable Long id,
                                        @RequestBody UpdateUserRoleRequest request,
                                        Authentication authentication) {
        if (request.getRole() == null || !request.getRole().matches("admin|municipal|operator")) {
            return ApiResponse.error(400, "角色必须为 admin / municipal / operator");
        }
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_admin".equals(authority.getAuthority()));
        boolean success = userService.updateUserRole(
                id,
                request.getRole(),
                authentication == null ? null : authentication.getName(),
                isAdmin
        );
        if (!success) {
            return ApiResponse.error(403, "仅管理员可调整角色，且不能修改自己的角色");
        }
        return ApiResponse.success(null);
    }

    // DELETE /api/users/{id}
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteUser(@PathVariable Long id) {
        boolean success = userService.deleteUser(id);
        if (!success) {
            return ApiResponse.error(400, "用户不存在或不允许删除管理员");
        }
        return ApiResponse.success(null);
    }
}
