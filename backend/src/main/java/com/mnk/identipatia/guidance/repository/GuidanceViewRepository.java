package com.mnk.identipatia.guidance.repository;

import com.mnk.identipatia.analysis.result.v2.ProtectionType;
import com.mnk.identipatia.guidance.model.GuidanceView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface GuidanceViewRepository extends JpaRepository<GuidanceView, UUID> {
    Optional<GuidanceView> findByAnalysisIdAndProtectionTypeAndGuideVersion(
            UUID analysisId, ProtectionType protectionType, Long guideVersion);

    @Modifying
    @Query(value = """
            insert into guidance_views
                (guidance_view_id, analysis_id, protection_type, guide_version, first_viewed_at)
            values (:guidanceViewId, :analysisId, cast(:protectionType as varchar), :guideVersion, :firstViewedAt)
            on conflict (analysis_id, protection_type, guide_version) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(UUID guidanceViewId, UUID analysisId, String protectionType,
            long guideVersion, Instant firstViewedAt);
}

