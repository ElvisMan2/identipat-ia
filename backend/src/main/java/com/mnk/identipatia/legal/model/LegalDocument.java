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
@Table(name = "legal_document")
@Getter
@Setter
@NoArgsConstructor
public class LegalDocument {
    @Id
    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "singleton_key", nullable = false, insertable = false, updatable = false)
    private short singletonKey;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(name = "content_html", nullable = false, columnDefinition = "text")
    private String contentHtml;

    @Column(name = "content_sha256", nullable = false, length = 64, columnDefinition = "char(64)")
    @JdbcTypeCode(Types.CHAR)
    private String contentSha256;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}

