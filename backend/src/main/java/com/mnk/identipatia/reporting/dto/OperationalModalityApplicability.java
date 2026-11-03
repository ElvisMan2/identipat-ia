package com.mnk.identipatia.reporting.dto;

import java.util.Objects;

public record OperationalModalityApplicability(String modality, String applicability) {
    public OperationalModalityApplicability {
        Objects.requireNonNull(modality, "modality must not be null");
        Objects.requireNonNull(applicability, "applicability must not be null");
    }
}
