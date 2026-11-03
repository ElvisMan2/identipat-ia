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

@Entity
@Table(name = "legal_document_history")
@Getter
@Setter
@NoArgsConstructor
public class LegalDocumentHistory {
    @Id
    @Column(name = "content_sha256", nullable = false, length = 64, columnDefinition = "char(64)")
    @JdbcTypeCode(Types.CHAR)
    private String contentSha256;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(name = "content_html", nullable = false, columnDefinition = "text")
    private String contentHtml;

    @Column(name = "captured_at", nullable = false)
    private Instant capturedAt;
}

