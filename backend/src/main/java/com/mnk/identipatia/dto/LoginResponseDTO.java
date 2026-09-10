package com.mnk.identipatia.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginResponseDTO {
    private String tokenType;
    private String accessToken;
}

