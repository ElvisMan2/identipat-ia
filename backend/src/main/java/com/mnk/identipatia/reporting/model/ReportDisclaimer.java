package com.mnk.identipatia.reporting.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "report_disclaimers")
@Getter
@Setter
@NoArgsConstructor
public class ReportDisclaimer {
    @Id
    @Column(name = "disclaimer_id", nullable = false)
    private UUID disclaimerId;

    @Column(nullable = false, unique = true)
    private Long version;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "content_sha256", nullable = false, length = 64, columnDefinition = "char(64)")
    private String contentSha256;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ReportDisclaimerStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "created_source", nullable = false, length = 16)
    private ReportDisclaimerSource createdSource;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "activated_by")
    private Long activatedBy;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "retired_at")
    private Instant retiredAt;
}
