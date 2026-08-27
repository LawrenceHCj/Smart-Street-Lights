package com.smartlamp.config;

import com.smartlamp.entity.SysUser;
import com.smartlamp.repository.SysUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class InitialDataConfig implements CommandLineRunner {

    @Autowired
    private SysUserRepository sysUserRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Value("${admin.default.password:}")
    private String adminDefaultPassword;

    @Override
    public void run(String... args) {
        if (sysUserRepository.findByUsername("admin").isPresent()) {
            return;
        }

        String password = adminDefaultPassword;
        if (password == null || password.isEmpty()) {
            password = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            System.out.println("==============================================");
            System.out.println(" 初始管理员账号已创建");
            System.out.println(" 用户名: admin");
            System.out.println(" 密码: " + password);
            System.out.println(" 请立即登录并修改密码");
            System.out.println("==============================================");
        }

        SysUser admin = new SysUser();
        admin.setUsername("admin");
        admin.setPassword(passwordEncoder.encode(password));
        admin.setRole("ADMIN");        // 字符串
        admin.setStatus("ENABLED");    // 字符串
        admin.setCreatedAt(LocalDateTime.now());
        sysUserRepository.save(admin);
    }
}