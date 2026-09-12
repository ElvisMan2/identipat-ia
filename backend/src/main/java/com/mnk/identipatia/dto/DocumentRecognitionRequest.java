package com.mnk.identipatia.dto;

import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Documento usado para reconocer el registro y determinar el siguiente paso de acceso.")
public class DocumentRecognitionRequest {

    @NotBlank
    private String doi;

    @NotBlank
    private String doiType;
}
