package com.smartlamp.service;

import com.smartlamp.dto.LoginRequest;
import com.smartlamp.dto.LoginResponse;
import com.smartlamp.entity.SysUser;
import com.smartlamp.repository.SysUserRepository;
import com.smartlamp.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock
    private SysUserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtUtil jwtUtil;
    @InjectMocks
    private AuthService authService;

    private LoginRequest request;

    @BeforeEach
    void setUp() {
        request = new LoginRequest();
        request.setUsername("operator");
        request.setPassword("secret");
    }

    @Test
    void enabledUserCanLogin() {
        SysUser user = user("ENABLED");
        when(userRepository.findByUsername("operator")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", "encoded")).thenReturn(true);
        when(jwtUtil.generateToken("operator", "operator")).thenReturn("token");

        LoginResponse response = authService.login(request);

        assertThat(response).isNotNull();
        assertThat(response.getToken()).isEqualTo("token");
        assertThat(response.getUsername()).isEqualTo("operator");
    }

    @Test
    void disabledUserCannotLogin() {
        when(userRepository.findByUsername("operator")).thenReturn(Optional.of(user("DISABLED")));

        assertThat(authService.login(request)).isNull();
        verifyNoInteractions(passwordEncoder, jwtUtil);
    }

    @Test
    void blankCredentialsAreRejectedBeforeDatabaseLookup() {
        request.setUsername(" ");

        assertThat(authService.login(request)).isNull();
        verifyNoInteractions(userRepository, passwordEncoder, jwtUtil);
    }

    private SysUser user(String status) {
        SysUser user = new SysUser();
        user.setUsername("operator");
        user.setPassword("encoded");
        user.setRole("operator");
        user.setStatus(status);
        return user;
    }
}
