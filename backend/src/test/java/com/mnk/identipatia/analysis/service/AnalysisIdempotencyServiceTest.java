package com.mnk.identipatia.analysis.service;

import com.mnk.identipatia.analysis.config.AnalysisIdempotencyProperties;
import com.mnk.identipatia.analysis.model.AnalysisInputType;
import com.mnk.identipatia.exception.ApiException;
import com.mnk.identipatia.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class AnalysisIdempotencyServiceTest {

    @Test
    void requiredCompatibilityFlagRejectsMissingHeaderWithStableCode() {
        var properties = new AnalysisIdempotencyProperties(
                true, 64, Duration.ofMinutes(10), Duration.ofHours(48), Duration.ofSeconds(5),
                "test-idempotency-hmac-secret-at-least-32-bytes");
        var service = new AnalysisIdempotencyService(mock(JdbcTemplate.class), properties);
        User user = new User();
        user.setUserId(1L);

        assertThatThrownBy(() -> service.claimOrReplay(
                AnalysisAccessContext.admin(user), null, AnalysisInputType.TEXT, "digest"))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus().value()).isEqualTo(400);
                    assertThat(exception.getCode()).isEqualTo("IDEMPOTENCY_KEY_REQUIRED");
                });
    }
}
