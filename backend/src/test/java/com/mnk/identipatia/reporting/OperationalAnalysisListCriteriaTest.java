package com.mnk.identipatia.reporting;

import com.mnk.identipatia.analysis.model.AnalysisInputType;
import com.mnk.identipatia.analysis.model.AnalysisStatus;
import com.mnk.identipatia.exception.ApiException;
import com.mnk.identipatia.reporting.dto.OperationalAnalysisListCriteria;
import com.mnk.identipatia.reporting.dto.OperationalAnalysisOrigin;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OperationalAnalysisListCriteriaTest {

    @Test
    void defaultsNormalizeSearchAndCreateStablePageable() {
        OperationalAnalysisListCriteria defaults = criteria(
                0, 20, null, null, null, null, null, null, null);
        OperationalAnalysisListCriteria searched = criteria(
                2, 10, "  JOSE   zuniga ", null, null,
                " completed ", " standard ", " pdf ", "status,asc");

        assertThat(defaults.page()).isZero();
        assertThat(defaults.size()).isEqualTo(20);
        assertThat(defaults.searchTerms()).isEmpty();
        assertThat(defaults.sortField()).isEqualTo("createdAt");
        assertThat(defaults.sortDirection()).isEqualTo(Sort.Direction.DESC);
        assertThat(defaults.toPageable().getSort().getOrderFor("createdAt").getDirection())
                .isEqualTo(Sort.Direction.DESC);
        assertThat(defaults.toPageable().getSort().getOrderFor("analysisId").getDirection())
                .isEqualTo(Sort.Direction.DESC);

        assertThat(searched.searchTerms()).containsExactly("jose", "zuniga");
        assertThat(searched.status()).isEqualTo(AnalysisStatus.COMPLETED);
        assertThat(searched.origin()).isEqualTo(OperationalAnalysisOrigin.STANDARD);
        assertThat(searched.inputType()).isEqualTo(AnalysisInputType.PDF);
        assertThat(searched.toPageable().getPageNumber()).isEqualTo(2);
        assertThat(searched.toPageable().getPageSize()).isEqualTo(10);
        assertThat(searched.toPageable().getSort().getOrderFor("status").getDirection())
                .isEqualTo(Sort.Direction.ASC);
        assertThat(searched.toPageable().getSort().getOrderFor("analysisId").getDirection())
                .isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void usesLimaInclusiveExclusiveBounds() {
        OperationalAnalysisListCriteria criteria = criteria(
                0, 20, null, LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 3),
                null, null, null, null);

        assertThat(criteria.fromInclusive()).isEqualTo(Instant.parse("2026-11-03T05:00:00Z"));
        assertThat(criteria.toExclusive()).isEqualTo(Instant.parse("2026-11-04T05:00:00Z"));
    }

    @Test
    void rejectsInvalidPageSizeEnumsSortAndRangeWithControlledCodes() {
        assertInvalidQuery(() -> criteria(-1, 20, null, null, null, null, null, null, null));
        assertInvalidQuery(() -> criteria(0, 0, null, null, null, null, null, null, null));
        assertInvalidQuery(() -> criteria(0, 101, null, null, null, null, null, null, null));
        assertInvalidQuery(() -> criteria(0, 20, null, null, null, "unknown", null, null, null));
        assertInvalidQuery(() -> criteria(0, 20, null, null, null, null, "customer", null, null));
        assertInvalidQuery(() -> criteria(0, 20, null, null, null, null, null, "image", null));
        assertInvalidQuery(() -> criteria(0, 20, null, null, null, null, null, null, "password,asc"));
        assertInvalidQuery(() -> criteria(0, 20, null, null, null, null, null, null, "createdAt,up"));
        assertInvalidQuery(() -> criteria(0, 20, null, null, null, null, null, null, "createdAt"));

        assertThatThrownBy(() -> criteria(
                0, 20, null, LocalDate.of(2026, 11, 4), LocalDate.of(2026, 11, 3),
                null, null, null, null))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("INVALID_DATE_RANGE"));
    }

    private static OperationalAnalysisListCriteria criteria(
            int page, int size, String search, LocalDate from, LocalDate to,
            String status, String origin, String inputType, String sort) {
        return OperationalAnalysisListCriteria.of(
                page, size, search, from, to, status, origin, inputType, sort);
    }

    private static void assertInvalidQuery(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("INVALID_ANALYSIS_QUERY"));
    }
}
