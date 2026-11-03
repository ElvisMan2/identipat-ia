package com.mnk.identipatia.legal.repository;

import com.mnk.identipatia.legal.model.UserTermsAcceptance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface UserTermsAcceptanceRepository extends JpaRepository<UserTermsAcceptance, UUID> {
    Optional<UserTermsAcceptance> findByUserIdAndAcceptedContentSha256AndAcceptedDocumentUpdatedAt(
            Long userId, String contentSha256, Instant documentUpdatedAt);

    boolean existsByUserId(Long userId);

    @Modifying
    @Query(value = """
            INSERT INTO user_terms_acceptances
                (acceptance_id, user_id, accepted_content_sha256,
                 accepted_document_updated_at, accepted_at, source)
            VALUES (:acceptanceId, :userId, :contentHash,
                    :documentUpdatedAt, :acceptedAt, :source)
            ON CONFLICT (user_id, accepted_content_sha256, accepted_document_updated_at) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("acceptanceId") UUID acceptanceId,
            @Param("userId") Long userId,
            @Param("contentHash") String contentHash,
            @Param("documentUpdatedAt") Instant documentUpdatedAt,
            @Param("acceptedAt") Instant acceptedAt,
            @Param("source") String source);
}

