package com.smartlamp.config;

import com.smartlamp.entity.SysUser;
import com.smartlamp.repository.SysUserRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Creates the documented development administrator only when the database is empty. */
@Configuration
public class InitialDataConfig {

    @Bean
    ApplicationRunner initializeDefaultAdmin(SysUserRepository userRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            if (userRepository.existsByUsername("admin")) {
                return;
            }
            SysUser admin = new SysUser();
            admin.setUsername("admin");
            admin.setPassword(passwordEncoder.encode("123456"));
            admin.setRole("admin");
            admin.setStatus("ENABLED");
            admin.setCreatedAt(java.time.LocalDateTime.now());
            userRepository.save(admin);
        };
    }
}
