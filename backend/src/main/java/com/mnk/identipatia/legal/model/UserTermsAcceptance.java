package com.mnk.identipatia.legal.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;

import java.time.Instant;
import java.sql.Types;
import java.util.UUID;

@Entity
@Table(name = "user_terms_acceptances")
@Getter
@Setter
@NoArgsConstructor
public class UserTermsAcceptance {
    @Id
    @Column(name = "acceptance_id", nullable = false)
    private UUID acceptanceId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "accepted_content_sha256", nullable = false, length = 64, columnDefinition = "char(64)")
    @JdbcTypeCode(Types.CHAR)
    private String acceptedContentSha256;

    @Column(name = "accepted_document_updated_at", nullable = false)
    private Instant acceptedDocumentUpdatedAt;

    @Column(name = "accepted_at", nullable = false)
    private Instant acceptedAt;

    @Column(nullable = false, length = 32)
    private String source;
}

