package com.smartlamp.config;

import com.smartlamp.entity.SysUser;
import com.smartlamp.repository.SysUserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.security.SecureRandom;

/** Creates the documented development administrator only when the database is empty. */
@Slf4j
@Configuration
public class InitialDataConfig {

    @Value("${initial.admin.password:}")
    private String initialAdminPassword;

    @Value("${spring.profiles.active:}")
    private String activeProfile;

    @Bean
    ApplicationRunner initializeDefaultAdmin(SysUserRepository userRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            if (userRepository.existsByUsername("admin")) {
                return;
            }
            String password = initialAdminPassword;
            boolean generated = false;
            if (password == null || password.isBlank()) {
                if (isProdProfile()) {
                    throw new IllegalStateException(
                            "生产环境首次启动必须通过 INITIAL_ADMIN_PASSWORD 显式提供初始 admin 密码，拒绝使用默认弱密码。");
                }
                password = generateRandomPassword(12);
                generated = true;
            }
            SysUser admin = new SysUser();
            admin.setUsername("admin");
            admin.setPassword(passwordEncoder.encode(password));
            admin.setRole("admin");
            admin.setStatus("ENABLED");
            admin.setCreatedAt(java.time.LocalDateTime.now());
            userRepository.save(admin);
            if (generated) {
                // 仅首次启动日志可见，提示运维登录后立即修改。
                log.warn("========================================================");
                log.warn("首次启动：已自动创建 admin 账号，初始密码：{}", password);
                log.warn("请立即登录并修改密码！此密码仅在本日志中显示一次。");
                log.warn("========================================================");
            } else {
                log.info("首次启动：已根据 INITIAL_ADMIN_PASSWORD 创建 admin 账号。");
            }
        };
    }

    private boolean isProdProfile() {
        if (activeProfile == null || activeProfile.isBlank()) return false;
        return activeProfile.toLowerCase().contains("prod");
    }

    private static String generateRandomPassword(int length) {
        SecureRandom random = new SecureRandom();
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
