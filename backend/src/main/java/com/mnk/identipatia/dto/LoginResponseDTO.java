package com.mnk.identipatia.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;

@Data
@AllArgsConstructor
@Schema(description = "JWT emitido únicamente tras autenticar un ADMIN activo.")
public class LoginResponseDTO {
    private String tokenType;
    @ToString.Exclude
    private String accessToken;
}

