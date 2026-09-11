package com.mnk.identipatia.config;

import com.mnk.identipatia.model.User;
import com.mnk.identipatia.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecurityConfigTest {

    @Mock
    private UserRepository userRepository;

    private final SecurityConfig securityConfig = new SecurityConfig();

    @Test
    void standardUserIsRejectedByTheAuthenticationUserDetailsService() {
        User standardUser = user("STANDARD", "A", "12345678");
        when(userRepository.findByDoi("12345678")).thenReturn(Optional.of(standardUser));

        assertThrows(DisabledException.class,
                () -> securityConfig.userDetailsService(userRepository).loadUserByUsername("12345678"));
    }

    @Test
    void activeAdminIsEnabledForAuthentication() {
        User admin = user("ADMIN", "A", "admin-001");
        when(userRepository.findByDoi("admin-001")).thenReturn(Optional.of(admin));

        UserDetails userDetails = securityConfig.userDetailsService(userRepository).loadUserByUsername("admin-001");

        assertTrue(userDetails.isEnabled());
        assertTrue(userDetails.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN")));
    }

    @Test
    void inactiveAdminCannotBeAuthenticatedFromUserDetails() {
        User admin = user("ADMIN", "I", "admin-inactive");
        when(userRepository.findByDoi("admin-inactive")).thenReturn(Optional.of(admin));

        UserDetails userDetails = securityConfig.userDetailsService(userRepository).loadUserByUsername("admin-inactive");

        assertFalse(userDetails.isEnabled());
    }

    private static User user(String userType, String status, String doi) {
        User user = new User();
        user.setDoi(doi);
        user.setPassword("$2a$10$abcdefghijklmnopqrstuuF2T4R5uX3ebx4w9UU5N8EBfQZ2eWvcu");
        user.setUserType(userType);
        user.setStatus(status);
        return user;
    }
}
