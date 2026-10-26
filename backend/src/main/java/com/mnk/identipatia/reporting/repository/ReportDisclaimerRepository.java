package com.mnk.identipatia.reporting.repository;

import com.mnk.identipatia.reporting.model.ReportDisclaimer;
import com.mnk.identipatia.reporting.model.ReportDisclaimerStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReportDisclaimerRepository extends JpaRepository<ReportDisclaimer, UUID> {
    List<ReportDisclaimer> findAllByOrderByVersionDesc();

    Optional<ReportDisclaimer> findByStatus(ReportDisclaimerStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from ReportDisclaimer d order by d.disclaimerId")
    List<ReportDisclaimer> lockAllInStableOrder();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from ReportDisclaimer d where d.disclaimerId = :id")
    Optional<ReportDisclaimer> findByIdForUpdate(UUID id);

    @Query(value = "select nextval('report_disclaimer_version_seq')", nativeQuery = true)
    long nextVersion();
}
