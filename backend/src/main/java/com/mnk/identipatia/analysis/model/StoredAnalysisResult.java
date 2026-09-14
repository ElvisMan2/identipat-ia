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
@Table(name = "analysis_results")
@Getter
@Setter
@NoArgsConstructor
public class StoredAnalysisResult {
    @Id
    @Column(name = "analysis_id", nullable = false)
    private UUID analysisId;
    @Column(name = "schema_version", nullable = false, length = 128)
    private String schemaVersion;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_json", nullable = false, columnDefinition = "jsonb")
    private JsonNode resultJson;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
