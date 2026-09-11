package com.mnk.identipatia.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

class SensitiveDtoLoggingTest {

    @Test
    void generatedToStringDoesNotExposePasswordsOrAccessTokens() {
        LoginRequestDTO request = new LoginRequestDTO();
        request.setDoi("admin-001");
        request.setPassword("plain-text-password");

        UserDTO user = new UserDTO();
        user.setPassword("encoded-password");

        LoginResponseDTO response = new LoginResponseDTO("Bearer", "complete-jwt-token");

        assertFalse(request.toString().contains("plain-text-password"));
        assertFalse(user.toString().contains("encoded-password"));
        assertFalse(response.toString().contains("complete-jwt-token"));
    }
}
