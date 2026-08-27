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

    public List<UserDTO> getAllUsers() {
        return sysUserRepository.findAll().stream()
                .map(user -> new UserDTO(
                        user.getId(),
                        user.getUsername(),
                        user.getRole(),      // 字符串
                        user.getStatus(),     // 字符串
                        user.getCreatedAt() == null ? null :
                                user.getCreatedAt().toInstant(ZoneOffset.UTC).toEpochMilli()
                ))
                .collect(Collectors.toList());
    }

    public boolean createUser(String username, String password, String role) {
        if (sysUserRepository.existsByUsername(username)) {
            return false;
        }
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(role);                  // 字符串
        user.setStatus("ENABLED");           // 字符串
        user.setCreatedAt(LocalDateTime.now());
        sysUserRepository.save(user);
        return true;
    }

    public boolean updateUserRole(Long id, String newRole, String actorUsername, boolean actorIsAdmin) {
        if (!actorIsAdmin || actorUsername == null || actorUsername.isBlank()) {
            return false;
        }
        SysUser user = sysUserRepository.findById(id).orElse(null);
        if (user == null) {
            return false;
        }
        if (actorUsername.equals(user.getUsername())
                || "admin".equals(user.getUsername())
                || "admin".equals(user.getRole())) {   // 字符串比较
            return false;
        }
        user.setRole(newRole);               // 字符串
        sysUserRepository.save(user);
        return true;
    }

    public boolean deleteUser(Long id) {
        SysUser user = sysUserRepository.findById(id).orElse(null);
        if (user == null) {
            return false;
        }
        if ("admin".equals(user.getUsername()) || "admin".equals(user.getRole())) {
            return false;
        }
        sysUserRepository.delete(user);
        return true;
    }
}