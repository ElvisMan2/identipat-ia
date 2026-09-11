package com.mnk.identipatia.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.ToString;

@Data
public class LoginRequestDTO {

    @NotBlank
    private String doi;

    @NotBlank
    @ToString.Exclude
    private String password;
}

