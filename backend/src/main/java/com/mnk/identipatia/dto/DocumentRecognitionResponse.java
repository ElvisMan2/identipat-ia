package com.mnk.identipatia.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
@Schema(description = "Resultado de reconocimiento sin datos personales ni credenciales.")
public class DocumentRecognitionResponse {
    @Schema(description = "Indica si el documento ya está registrado", example = "true")
    private boolean registered;

    @Schema(description = "Indica si el siguiente paso requiere contraseña administrativa", example = "false")
    private boolean passwordRequired;
}
