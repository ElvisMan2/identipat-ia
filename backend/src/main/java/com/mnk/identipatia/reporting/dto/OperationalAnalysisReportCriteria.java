package com.mnk.identipatia.reporting.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

public record OperationalAnalysisReportCriteria(LocalDate from, LocalDate to) {
    private static final ZoneId REPORT_ZONE = ZoneId.of("America/Lima");

    public OperationalAnalysisReportCriteria {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("from must not be after to");
        }
    }

    public Instant fromInclusive() {
        return from == null ? null : from.atStartOfDay(REPORT_ZONE).toInstant();
    }

    public Instant toExclusive() {
        return to == null ? null : to.plusDays(1).atStartOfDay(REPORT_ZONE).toInstant();
    }
}
