package com.mnk.identipatia.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Documento de un registro STANDARD. No es una credencial ni verifica identidad real.")
public class StandardSessionCreateRequest {
    @NotBlank
    private String doi;
    @NotBlank
    private String doiType;

    public String getDoi() { return doi; }
    public void setDoi(String doi) { this.doi = doi; }
    public String getDoiType() { return doiType; }
    public void setDoiType(String doiType) { this.doiType = doiType; }

    @JsonAnySetter
    void rejectUnknown(String name, Object value) {
        throw new IllegalArgumentException("Unsupported STANDARD session field: " + name);
    }
}
