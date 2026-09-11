package com.mnk.identipatia.dto;

import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Documento usado exclusivamente para reconocer si existe un registro STANDARD.")
public class DocumentRecognitionRequest {

    @NotBlank
    private String doi;

    @NotBlank
    private String doiType;
}
