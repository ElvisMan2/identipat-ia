package com.mnk.identipatia.reporting;

import com.mnk.identipatia.analysis.model.AnalysisInputType;
import com.mnk.identipatia.analysis.model.AnalysisStatus;
import com.mnk.identipatia.exception.ApiException;
import com.mnk.identipatia.reporting.dto.OperationalAnalysisExportCriteria;
import com.mnk.identipatia.reporting.dto.OperationalAnalysisOrigin;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OperationalAnalysisExportCriteriaTest {

    @Test
    void defaultsToLegacyAscendingOrderAndNormalizesEverySharedFilter() {
        OperationalAnalysisExportCriteria defaults = criteria(
                null, null, null, null, null, null, null);
        OperationalAnalysisExportCriteria filtered = criteria(
                "  JOSE   zuniga ", LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 3),
                " completed ", " standard ", " pdf ", "status,desc");

        assertThat(defaults.filters().searchTerms()).isEmpty();
        assertThat(defaults.ordering().field()).isEqualTo("createdAt");
        assertThat(defaults.ordering().direction()).isEqualTo(Sort.Direction.ASC);
        assertThat(defaults.toSort().getOrderFor("analysisId").getDirection())
                .isEqualTo(Sort.Direction.ASC);
        assertThat(defaults.hasAdditionalFilters()).isFalse();

        assertThat(filtered.filters().searchTerms()).containsExactly("jose", "zuniga");
        assertThat(filtered.filters().status()).isEqualTo(AnalysisStatus.COMPLETED);
        assertThat(filtered.filters().origin()).isEqualTo(OperationalAnalysisOrigin.STANDARD);
        assertThat(filtered.filters().inputType()).isEqualTo(AnalysisInputType.PDF);
        assertThat(filtered.filters().fromInclusive()).isEqualTo(Instant.parse("2026-11-03T05:00:00Z"));
        assertThat(filtered.filters().toExclusive()).isEqualTo(Instant.parse("2026-11-04T05:00:00Z"));
        assertThat(filtered.toSort().getOrderFor("status").getDirection())
                .isEqualTo(Sort.Direction.DESC);
        assertThat(filtered.toSort().getOrderFor("analysisId").getDirection())
                .isEqualTo(Sort.Direction.DESC);
        assertThat(filtered.hasAdditionalFilters()).isTrue();
    }

    @Test
    void sortAloneDoesNotMarkTheFilenameAsFiltered() {
        OperationalAnalysisExportCriteria criteria = criteria(
                null, null, null, null, null, null, "createdAt,desc");

        assertThat(criteria.hasAdditionalFilters()).isFalse();
        assertThat(criteria.ordering().direction()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void rejectsInvalidSharedFiltersRangeAndSortWithControlledCodes() {
        assertInvalidQuery(() -> criteria(null, null, null, "unknown", null, null, null));
        assertInvalidQuery(() -> criteria(null, null, null, null, "customer", null, null));
        assertInvalidQuery(() -> criteria(null, null, null, null, null, "image", null));
        assertInvalidQuery(() -> criteria(null, null, null, null, null, null, "password,asc"));
        assertInvalidQuery(() -> criteria(null, null, null, null, null, null, "createdAt,up"));
        assertInvalidQuery(() -> criteria(null, null, null, null, null, null, "createdAt"));

        assertThatThrownBy(() -> criteria(
                null, LocalDate.of(2026, 11, 4), LocalDate.of(2026, 11, 3),
                null, null, null, null))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("INVALID_DATE_RANGE"));
    }

    private static OperationalAnalysisExportCriteria criteria(
            String search, LocalDate from, LocalDate to,
            String status, String origin, String inputType, String sort) {
        return OperationalAnalysisExportCriteria.of(
                search, from, to, status, origin, inputType, sort);
    }

    private static void assertInvalidQuery(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("INVALID_ANALYSIS_QUERY"));
    }
}
