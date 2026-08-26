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
    public boolean updateUserRole(Long id, String newRole) {
        SysUser user = sysUserRepository.findById(id).orElse(null);
        if (user == null) {
            return false;
        }
        // 保护 admin 用户不被修改
        if ("admin".equals(user.getUsername()) || "admin".equals(user.getRole())) {
            return false;
        }
        user.setRole(newRole);
        sysUserRepository.save(user);
        return true;
    }

    // 删除用户
    public boolean deleteUser(Long id) {
        SysUser user = sysUserRepository.findById(id).orElse(null);
        if (user == null) {
            return false;
        }
        // 保护 admin 用户不被删除
        if ("admin".equals(user.getUsername()) || "admin".equals(user.getRole())) {
            return false;
        }
        sysUserRepository.delete(user);
        return true;
    }
}
