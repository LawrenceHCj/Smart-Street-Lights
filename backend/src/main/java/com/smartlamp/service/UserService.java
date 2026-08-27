package com.smartlamp.service;

import com.smartlamp.dto.UserDTO;
import com.smartlamp.entity.SysUser;
import com.smartlamp.repository.SysUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserService {

    @Autowired
    private SysUserRepository sysUserRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // 获取所有用户
    public List<UserDTO> getAllUsers() {
        return sysUserRepository.findAll().stream()
                .map(user -> new UserDTO(
                        user.getId(),
                        user.getUsername(),
                        user.getRole(),
                        user.getStatus(),
                        user.getCreatedAt() == null ? null :
                                user.getCreatedAt().toInstant(ZoneOffset.UTC).toEpochMilli()
                ))
                .collect(Collectors.toList());
    }

    // 新增用户
    public boolean createUser(String username, String password, String role) {
        if (sysUserRepository.existsByUsername(username)) {
            return false; // 用户名已存在
        }
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(role);
        user.setStatus("ENABLED");
        user.setCreatedAt(LocalDateTime.now());
        sysUserRepository.save(user);
        return true;
    }

    // 修改用户角色
    public boolean updateUserRole(Long id, String newRole, String actorUsername, boolean actorIsAdmin) {
        if (!actorIsAdmin || actorUsername == null || actorUsername.isBlank()) {
            return false;
        }
        SysUser user = sysUserRepository.findById(id).orElse(null);
        if (user == null) {
            return false;
        }
        // 管理员也不能修改自己的角色，避免当前会话权限与数据库角色不一致。
        if (actorUsername.equals(user.getUsername())
                || "admin".equals(user.getUsername())) {
            return false;
        }
        user.setRole(newRole);
        sysUserRepository.save(user);
        return true;
    }

    // 删除用户
    public boolean deleteUser(Long id, String actorUsername, boolean actorIsAdmin) {
        if (!actorIsAdmin || actorUsername == null || actorUsername.isBlank()) {
            return false;
        }
        SysUser user = sysUserRepository.findById(id).orElse(null);
        if (user == null) {
            return false;
        }
        // 只保护内置 admin 和当前登录账号；其他管理员账号仍可由管理员删除。
        if ("admin".equals(user.getUsername()) || actorUsername.equals(user.getUsername())) {
            return false;
        }
        sysUserRepository.delete(user);
        return true;
    }
}
