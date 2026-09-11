package com.mnk.identipatia.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;

@Data
@AllArgsConstructor
public class LoginResponseDTO {
    private String tokenType;
    @ToString.Exclude
    private String accessToken;
}

