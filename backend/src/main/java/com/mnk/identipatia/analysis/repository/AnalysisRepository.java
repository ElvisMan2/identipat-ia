package com.mnk.identipatia.analysis.repository;

import com.mnk.identipatia.analysis.model.Analysis;
import com.mnk.identipatia.analysis.model.AnalysisStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AnalysisRepository extends JpaRepository<Analysis, UUID> {
    Optional<Analysis> findByAnalysisIdAndSessionId(UUID analysisId, UUID sessionId);
    Optional<Analysis> findByAnalysisIdAndUserIdAndSessionIdIsNull(UUID analysisId, Long userId);
    Optional<Analysis> findByAnalysisIdAndLeaseOwnerAndStatus(
            UUID analysisId, String leaseOwner, AnalysisStatus status);

    default List<Analysis> findAllForOperationalReport(Instant fromInclusive, Instant toExclusive) {
        if (fromInclusive == null && toExclusive == null) {
            return findAllForOperationalReportWithoutBounds();
        }
        if (fromInclusive == null) {
            return findAllForOperationalReportTo(toExclusive);
        }
        if (toExclusive == null) {
            return findAllForOperationalReportFrom(fromInclusive);
        }
        return findAllForOperationalReportBetween(fromInclusive, toExclusive);
    }

    @Query("""
            select analysis from Analysis analysis
            order by analysis.createdAt asc, analysis.analysisId asc
            """)
    List<Analysis> findAllForOperationalReportWithoutBounds();

    @Query("""
            select analysis from Analysis analysis
            where analysis.createdAt >= :fromInclusive
            order by analysis.createdAt asc, analysis.analysisId asc
            """)
    List<Analysis> findAllForOperationalReportFrom(@Param("fromInclusive") Instant fromInclusive);

    @Query("""
            select analysis from Analysis analysis
            where analysis.createdAt < :toExclusive
            order by analysis.createdAt asc, analysis.analysisId asc
            """)
    List<Analysis> findAllForOperationalReportTo(@Param("toExclusive") Instant toExclusive);

    @Query("""
            select analysis from Analysis analysis
            where analysis.createdAt >= :fromInclusive
              and analysis.createdAt < :toExclusive
            order by analysis.createdAt asc, analysis.analysisId asc
            """)
    List<Analysis> findAllForOperationalReportBetween(
            @Param("fromInclusive") Instant fromInclusive,
            @Param("toExclusive") Instant toExclusive);
}
