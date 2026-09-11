package com.mnk.identipatia.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
@Schema(description = "Confirmación mínima del registro STANDARD.")
public class StandardUserRegistrationResponse {
    private boolean registered;
}
