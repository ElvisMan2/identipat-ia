package com.mnk.identipatia.analysis.model;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "analysis_inputs")
@Getter
@Setter
@NoArgsConstructor
public class AnalysisInput {
    @Id
    @Column(name = "analysis_id", nullable = false)
    private UUID analysisId;

    @Column(name = "original_text", columnDefinition = "text")
    private String originalText;

    @Column(name = "processed_text", columnDefinition = "text")
    private String processedText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_metadata", nullable = false, columnDefinition = "jsonb")
    private JsonNode sourceMetadata;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "processed_at")
    private Instant processedAt;
}
