package com.mnk.identipatia.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Decisión explícita para la versión de consentimiento mostrada.")
public class ConsentRequest {
    @NotBlank
    private String consentVersion;
    @NotBlank
    private String decision;

    public String getConsentVersion() { return consentVersion; }
    public void setConsentVersion(String consentVersion) { this.consentVersion = consentVersion; }
    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }

    @JsonAnySetter
    void rejectUnknown(String name, Object value) {
        throw new IllegalArgumentException("Unsupported consent field: " + name);
    }
}
