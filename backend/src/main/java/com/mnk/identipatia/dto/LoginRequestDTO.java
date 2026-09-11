package com.mnk.identipatia.dto;

import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.ToString;

@Data
@Schema(description = "Credenciales de un usuario ADMIN.")
public class LoginRequestDTO {

    @NotBlank
    private String doi;

    @NotBlank
    @ToString.Exclude
    private String password;
}

