package com.mnk.identipatia.guidance.model;

import com.mnk.identipatia.analysis.result.v2.ProtectionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "protection_guide_version_counters")
@Getter
@Setter
@NoArgsConstructor
public class ProtectionGuideVersionCounter {
    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "protection_type", nullable = false, length = 40)
    private ProtectionType protectionType;

    @Column(name = "next_version", nullable = false)
    private Long nextVersion;
}
