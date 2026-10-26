package com.mnk.identipatia.reporting.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "analysis_report_contexts")
@Getter
@Setter
@NoArgsConstructor
public class AnalysisReportContext {
    @Id
    @Column(name = "analysis_id", nullable = false)
    private UUID analysisId;

    @Column(name = "disclaimer_id", nullable = false)
    private UUID disclaimerId;

    @Column(name = "report_version", nullable = false, length = 64)
    private String reportVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
