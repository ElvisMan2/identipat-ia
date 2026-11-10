package com.mnk.identipatia.analysis.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnalysisIdempotencyConfigurationTest {

    @Test
    void claimTtlMustOutliveAdmissionReservation() {
        assertThatThrownBy(() -> validator(Duration.ofMinutes(8), Duration.ofHours(48)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("claim TTL");
        assertThatCode(() -> validator(Duration.ofMinutes(9), Duration.ofHours(48)))
                .doesNotThrowAnyException();
    }

    @Test
    void retentionMustStrictlyOutliveClaimTtl() {
        assertThatThrownBy(() -> validator(Duration.ofHours(25), Duration.ofHours(25)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("retention")
                .hasMessageContaining("claim TTL");
        assertThatThrownBy(() -> validator(Duration.ofHours(25).plusNanos(1), Duration.ofHours(25)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("claim TTL");
        assertThatCode(() -> validator(Duration.ofHours(25), Duration.ofHours(25).plusNanos(1)))
                .doesNotThrowAnyException();
    }

    @Test
    void retentionMustStrictlyOutliveLongestQuotaWindow() {
        assertThatThrownBy(() -> validator(Duration.ofMinutes(9), Duration.ofHours(24)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("retention");
        assertThatCode(() -> validator(Duration.ofMinutes(9), Duration.ofHours(24).plusNanos(1)))
                .doesNotThrowAnyException();
    }

    @Test
    void unresolvedHmacSecretPlaceholderIsRejected() {
        assertThatThrownBy(() -> new AnalysisIdempotencyProperties(
                false, 64, Duration.ofMinutes(10), Duration.ofHours(48), Duration.ofSeconds(5),
                "${ANALYSIS_IDEMPOTENCY_HMAC_SECRET}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unresolved placeholder");
    }

    private static void validator(Duration claimTtl, Duration retention) {
        var idempotency = new AnalysisIdempotencyProperties(
                false, 64, claimTtl, retention, Duration.ofSeconds(5),
                "test-idempotency-hmac-secret-at-least-32-bytes");
        var admission = new AnalysisAdmissionProperties(
                true, 3, 10, 3, 1, 8, 1, Duration.ofMinutes(8),
                Duration.ofSeconds(60), Duration.ofSeconds(10));
        new AnalysisIdempotencyConfiguration.IdempotencyConfigurationValidator(idempotency, admission);
    }
}
