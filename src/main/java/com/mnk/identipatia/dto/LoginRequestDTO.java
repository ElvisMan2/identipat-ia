package com.mnk.identipatia.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequestDTO {

    @NotBlank
    private String doi;

    @NotBlank
    private String password;
}

