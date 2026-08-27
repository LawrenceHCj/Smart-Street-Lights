package com.smartlamp.service;

import com.smartlamp.entity.SysUser;
import com.smartlamp.repository.SysUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    @Mock
    private SysUserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @InjectMocks
    private UserService userService;

    @Test
    void nonAdminCannotPromoteOwnAccount() {
        boolean updated = userService.updateUserRole(2L, "admin", "operator", false);

        assertThat(updated).isFalse();
        verifyNoInteractions(userRepository);
    }

    @Test
    void administratorCannotChangeOwnRole() {
        SysUser admin = user(1L, "admin", "admin");
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

        boolean updated = userService.updateUserRole(1L, "operator", "admin", true);

        assertThat(updated).isFalse();
        verify(userRepository, never()).save(admin);
    }

    @Test
    void administratorCanChangeAnotherNonAdminRole() {
        SysUser operator = user(2L, "operator", "operator");
        when(userRepository.findById(2L)).thenReturn(Optional.of(operator));

        boolean updated = userService.updateUserRole(2L, "municipal", "admin", true);

        assertThat(updated).isTrue();
        assertThat(operator.getRole()).isEqualTo("municipal");
        verify(userRepository).save(operator);
    }

    @Test
    void administratorCanChangeAnotherAdministratorRole() {
        SysUser otherAdmin = user(2L, "Lawrence", "admin");
        when(userRepository.findById(2L)).thenReturn(Optional.of(otherAdmin));

        boolean updated = userService.updateUserRole(2L, "operator", "admin", true);

        assertThat(updated).isTrue();
        assertThat(otherAdmin.getRole()).isEqualTo("operator");
        verify(userRepository).save(otherAdmin);
    }

    @Test
    void administratorCanDeleteAnotherAdministratorAccount() {
        SysUser otherAdmin = user(2L, "Lawrence", "admin");
        when(userRepository.findById(2L)).thenReturn(Optional.of(otherAdmin));

        boolean deleted = userService.deleteUser(2L, "admin", true);

        assertThat(deleted).isTrue();
        verify(userRepository).delete(otherAdmin);
    }

    @Test
    void administratorCannotDeleteOwnAccount() {
        SysUser currentAdmin = user(2L, "Lawrence", "admin");
        when(userRepository.findById(2L)).thenReturn(Optional.of(currentAdmin));

        boolean deleted = userService.deleteUser(2L, "Lawrence", true);

        assertThat(deleted).isFalse();
        verify(userRepository, never()).delete(currentAdmin);
    }

    @Test
    void administratorCannotDeleteBuiltInAdminAccount() {
        SysUser builtInAdmin = user(1L, "admin", "admin");
        when(userRepository.findById(1L)).thenReturn(Optional.of(builtInAdmin));

        boolean deleted = userService.deleteUser(1L, "Lawrence", true);

        assertThat(deleted).isFalse();
        verify(userRepository, never()).delete(builtInAdmin);
    }

    private SysUser user(Long id, String username, String role) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername(username);
        user.setRole(role);
        user.setStatus("ENABLED");
        return user;
    }
}
