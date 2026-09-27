package com.mnk.identipatia.analysis.result.v2;

import java.util.List;
import java.util.Objects;

public record ProtectionOption(
        ProtectionType type,
        ProtectionApplicability applicability,
        String protectedSubjectMatter,
        String rationale,
        List<LegalBasis> legalBasis) {
    public ProtectionOption {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(applicability, "applicability must not be null");
        ResultValidation.requireText(protectedSubjectMatter, "protectedSubjectMatter");
        ResultValidation.requireText(rationale, "rationale");
        legalBasis = ResultValidation.immutable(legalBasis, "legalBasis");
        if (legalBasis.isEmpty()) {
            throw new IllegalArgumentException("legalBasis must not be empty");
        }
    }
}
