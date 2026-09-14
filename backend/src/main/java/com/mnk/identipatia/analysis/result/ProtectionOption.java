package com.mnk.identipatia.analysis.result;

import java.util.Objects;

public record ProtectionOption(
        ProtectionType type,
        ProtectionApplicability applicability,
        String rationale) {
    public ProtectionOption {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(applicability, "applicability must not be null");
        PatentabilityAssessment.requireText(rationale, "rationale");
    }
}
