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

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "guidance_views")
@Getter
@Setter
@NoArgsConstructor
public class GuidanceView {
    @Id
    @Column(name = "guidance_view_id", nullable = false)
    private UUID guidanceViewId;

    @Column(name = "analysis_id", nullable = false)
    private UUID analysisId;

    @Enumerated(EnumType.STRING)
    @Column(name = "protection_type", nullable = false, length = 40)
    private ProtectionType protectionType;

    @Column(name = "guide_version", nullable = false)
    private Long guideVersion;

    @Column(name = "first_viewed_at", nullable = false)
    private Instant firstViewedAt;
}

