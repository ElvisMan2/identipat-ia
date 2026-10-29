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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "protection_guides")
@Getter
@Setter
@NoArgsConstructor
public class ProtectionGuide {
    @Id
    @Column(name = "guide_id", nullable = false)
    private UUID guideId;

    @Enumerated(EnumType.STRING)
    @Column(name = "protection_type", nullable = false, length = 40)
    private ProtectionType protectionType;

    @Column(nullable = false)
    private Long version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ProtectionGuideStatus status;

    @Column(name = "short_description", nullable = false, length = 240)
    private String shortDescription;

    @Column(name = "next_steps_content_html", nullable = false, columnDefinition = "text")
    private String nextStepsContentHtml;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "content_sha256", nullable = false, length = 64, columnDefinition = "char(64)")
    private String contentSha256;

    @Enumerated(EnumType.STRING)
    @Column(name = "created_source", nullable = false, length = 16)
    private ProtectionGuideSource createdSource;

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

    @Column(name = "last_verified_at")
    private Instant lastVerifiedAt;
}
