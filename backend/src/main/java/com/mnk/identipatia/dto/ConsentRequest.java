package com.mnk.identipatia.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Decisión explícita para el contenido legal mostrado por el backend.")
public class ConsentRequest {
    @NotBlank
    private String contentHash;
    @NotBlank
    private String decision;

    public String getContentHash() { return contentHash; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }
    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }

    @JsonAnySetter
    void rejectUnknown(String name, Object value) {
        throw new IllegalArgumentException("Unsupported consent field: " + name);
    }
}
