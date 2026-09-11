package com.mnk.identipatia.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DocumentRecognitionRequest {

    @NotBlank
    private String doi;

    @NotBlank
    private String doiType;
}
