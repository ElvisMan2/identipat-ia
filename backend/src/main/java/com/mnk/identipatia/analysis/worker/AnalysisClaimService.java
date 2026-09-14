package com.mnk.identipatia.analysis.worker;

import com.mnk.identipatia.analysis.config.AnalysisProperties;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class AnalysisClaimService {
    private final EntityManager entityManager;
    private final AnalysisProperties properties;
    private final Clock clock;

    public AnalysisClaimService(EntityManager entityManager, AnalysisProperties properties, Clock clock) {
        this.entityManager = entityManager;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public Optional<UUID> claimNext(String workerId) {
        Instant now = clock.instant();
        Query query = entityManager.createNativeQuery("""
                WITH candidate AS (
                    SELECT analysis_id
                    FROM analyses
                    WHERE status IN ('RECEIVED', 'ANALYZING')
                      AND (next_attempt_at IS NULL OR next_attempt_at <= :now)
                      AND (lease_until IS NULL OR lease_until < :now)
                    ORDER BY created_at, analysis_id
                    FOR UPDATE SKIP LOCKED
                    LIMIT 1
                )
                UPDATE analyses AS analysis
                SET status = 'ANALYZING',
                    started_at = COALESCE(analysis.started_at, :now),
                    updated_at = :now,
                    lease_owner = :workerId,
                    lease_until = :leaseUntil
                FROM candidate
                WHERE analysis.analysis_id = candidate.analysis_id
                RETURNING analysis.analysis_id
                """);
        query.setParameter("now", now);
        query.setParameter("workerId", workerId);
        query.setParameter("leaseUntil", now.plus(properties.leaseDuration()));
        @SuppressWarnings("unchecked")
        List<Object> rows = query.getResultList();
        return rows.isEmpty() ? Optional.empty() : Optional.of((UUID) rows.getFirst());
    }

    @Transactional
    public boolean release(UUID analysisId, String workerId) {
        int changed = entityManager.createNativeQuery("""
                UPDATE analyses
                SET lease_owner = NULL, lease_until = NULL, updated_at = :now
                WHERE analysis_id = :analysisId
                  AND lease_owner = :workerId
                  AND status IN ('RECEIVED', 'ANALYZING')
                """)
                .setParameter("now", clock.instant())
                .setParameter("analysisId", analysisId)
                .setParameter("workerId", workerId)
                .executeUpdate();
        return changed == 1;
    }
}
