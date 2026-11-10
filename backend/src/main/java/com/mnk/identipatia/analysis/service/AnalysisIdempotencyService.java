package com.mnk.identipatia.analysis.service;

import com.mnk.identipatia.analysis.config.AnalysisIdempotencyProperties;
import com.mnk.identipatia.analysis.dto.AnalysisCreatedResponse;
import com.mnk.identipatia.analysis.model.AnalysisInputType;
import com.mnk.identipatia.analysis.model.AnalysisStatus;
import com.mnk.identipatia.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class AnalysisIdempotencyService {
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final JdbcTemplate jdbc;
    private final AnalysisIdempotencyProperties properties;
    private final SecretKeySpec hmacKey;

    public AnalysisIdempotencyService(JdbcTemplate jdbc, AnalysisIdempotencyProperties properties) {
        this.jdbc = jdbc;
        this.properties = properties;
        this.hmacKey = new SecretKeySpec(
                properties.hmacSecret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
    }

    public String fingerprintText(String description) {
        return digest(framed(
                AnalysisInputType.TEXT.name().getBytes(StandardCharsets.UTF_8),
                (description == null ? "" : description).getBytes(StandardCharsets.UTF_8)));
    }

    public String fingerprintFile(AnalysisInputType inputType, String mediaType, byte[] bytes) {
        return digest(framed(
                inputType.name().getBytes(StandardCharsets.UTF_8),
                mediaType.getBytes(StandardCharsets.UTF_8),
                bytes));
    }

    @Transactional
    public Claim claimOrReplay(
            AnalysisAccessContext access,
            String rawKey,
            AnalysisInputType inputType,
            String payloadDigest) {
        if (rawKey == null) {
            if (properties.required()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED",
                        "Idempotency-Key is required");
            }
            return Claim.disabled();
        }

        UUID key = validateKey(rawKey);
        String keyDigest = digest(key.toString().getBytes(StandardCharsets.UTF_8));
        lock();
        Instant now = databaseNow();
        purgeExpired(now);

        List<Row> matches = find(access, keyDigest);
        if (matches.isEmpty()) {
            UUID id = UUID.randomUUID();
            UUID token = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO analysis_idempotency_keys
                        (idempotency_id, user_id, actor_mode, session_id, key_digest,
                         payload_digest, input_type, state, claim_token, created_at, updated_at,
                         claim_expires_at, expires_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, 'IN_PROGRESS', ?, ?, ?, ?, ?)
                    """, id, access.user().getUserId(), access.mode().name(), sessionId(access),
                    keyDigest, payloadDigest, inputType.name(), token, atUtc(now), atUtc(now),
                    atUtc(now.plus(properties.claimTtl())), atUtc(now.plus(properties.retention())));
            return Claim.claimed(id, token);
        }

        Row row = matches.getFirst();
        if (!row.payloadDigest().equals(payloadDigest) || !row.inputType().equals(inputType.name())) {
            throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_CONFLICT",
                    "Idempotency-Key was already used for a different analysis request");
        }
        if ("ACCEPTED".equals(row.state())) {
            return Claim.replay(row.idempotencyId(), acceptedResponse(row.analysisId()));
        }
        if ("IN_PROGRESS".equals(row.state()) && row.claimExpiresAt().toInstant().isAfter(now)) {
            throw inProgress();
        }

        UUID token = UUID.randomUUID();
        int changed = jdbc.update("""
                UPDATE analysis_idempotency_keys
                SET state = 'IN_PROGRESS', claim_token = ?, claim_expires_at = ?,
                    updated_at = ?, expires_at = ?
                WHERE idempotency_id = ? AND state IN ('IN_PROGRESS', 'RELEASED')
                """, token, atUtc(now.plus(properties.claimTtl())), atUtc(now),
                atUtc(now.plus(properties.retention())), row.idempotencyId());
        if (changed != 1) {
            throw inProgress();
        }
        return Claim.claimed(row.idempotencyId(), token);
    }

    void accept(Claim claim, UUID analysisId) {
        if (!claim.enabled()) {
            return;
        }
        if (!claim.claimed()) {
            throw new IllegalArgumentException("A replay cannot be accepted again");
        }
        Instant now = databaseNow();
        int changed = jdbc.update("""
                UPDATE analysis_idempotency_keys
                SET state = 'ACCEPTED', claim_token = NULL, claim_expires_at = NULL,
                    analysis_id = ?, accepted_at = ?, updated_at = ?, expires_at = ?
                WHERE idempotency_id = ? AND state = 'IN_PROGRESS'
                  AND claim_token = ? AND claim_expires_at > ?
                """, analysisId, atUtc(now), atUtc(now), atUtc(now.plus(properties.retention())),
                claim.idempotencyId(), claim.claimToken(), atUtc(now));
        if (changed != 1) {
            throw inProgress();
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void release(Claim claim) {
        if (!claim.enabled() || !claim.claimed()) {
            return;
        }
        lock();
        Instant now = databaseNow();
        jdbc.update("""
                UPDATE analysis_idempotency_keys
                SET state = 'RELEASED', claim_token = NULL, claim_expires_at = NULL,
                    updated_at = ?, expires_at = ?
                WHERE idempotency_id = ? AND state = 'IN_PROGRESS' AND claim_token = ?
                """, atUtc(now), atUtc(now.plus(properties.retention())),
                claim.idempotencyId(), claim.claimToken());
    }

    private List<Row> find(AnalysisAccessContext access, String keyDigest) {
        String sql;
        Object[] arguments;
        if (access.mode() == AnalysisAccessContext.Mode.STANDARD_SESSION) {
            sql = """
                    SELECT idempotency_id, payload_digest, input_type, state,
                           claim_expires_at, analysis_id
                    FROM analysis_idempotency_keys
                    WHERE actor_mode = 'STANDARD_SESSION' AND user_id = ?
                      AND session_id = ? AND key_digest = ?
                    """;
            arguments = new Object[] {access.user().getUserId(), sessionId(access), keyDigest};
        } else {
            sql = """
                    SELECT idempotency_id, payload_digest, input_type, state,
                           claim_expires_at, analysis_id
                    FROM analysis_idempotency_keys
                    WHERE actor_mode = 'ADMIN_JWT' AND user_id = ? AND key_digest = ?
                    """;
            arguments = new Object[] {access.user().getUserId(), keyDigest};
        }
        return jdbc.query(sql, (result, rowNumber) -> new Row(
                result.getObject("idempotency_id", UUID.class),
                result.getString("payload_digest"),
                result.getString("input_type"),
                result.getString("state"),
                result.getObject("claim_expires_at", OffsetDateTime.class),
                result.getObject("analysis_id", UUID.class)), arguments);
    }

    private AnalysisCreatedResponse acceptedResponse(UUID analysisId) {
        return jdbc.queryForObject("""
                SELECT created_at FROM analyses WHERE analysis_id = ?
                """, (result, rowNumber) -> new AnalysisCreatedResponse(
                analysisId, AnalysisStatus.RECEIVED,
                result.getObject("created_at", OffsetDateTime.class).toInstant()), analysisId);
    }

    private UUID validateKey(String rawKey) {
        if (rawKey.isBlank() || rawKey.length() > properties.maxKeyLength()) {
            throw invalidKey();
        }
        try {
            UUID parsed = UUID.fromString(rawKey);
            if (parsed.version() != 4 || !parsed.toString().equals(rawKey.toLowerCase(Locale.ROOT))) {
                throw invalidKey();
            }
            return parsed;
        } catch (IllegalArgumentException exception) {
            throw invalidKey();
        }
    }

    private void purgeExpired(Instant now) {
        jdbc.update("""
                DELETE FROM analysis_idempotency_keys
                WHERE expires_at <= ?
                  AND (state <> 'IN_PROGRESS' OR claim_expires_at <= ?)
                """, atUtc(now), atUtc(now));
    }

    private void lock() {
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(?)", Object.class,
                AnalysisAdmissionService.ADVISORY_LOCK_KEY);
    }

    private Instant databaseNow() {
        OffsetDateTime value = jdbc.queryForObject("SELECT clock_timestamp()", OffsetDateTime.class);
        if (value == null) {
            throw new IllegalStateException("PostgreSQL did not return the idempotency timestamp");
        }
        return value.toInstant();
    }

    private String digest(byte[] value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(hmacKey);
            return HexFormat.of().formatHex(mac.doFinal(value));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", exception);
        }
    }

    private static byte[] framed(byte[]... values) {
        int size = 0;
        for (byte[] value : values) {
            size += Integer.BYTES + value.length;
        }
        ByteBuffer buffer = ByteBuffer.allocate(size);
        for (byte[] value : values) {
            buffer.putInt(value.length).put(value);
        }
        return buffer.array();
    }

    private static UUID sessionId(AnalysisAccessContext access) {
        return access.session() == null ? null : access.session().getSessionId();
    }

    private ApiException inProgress() {
        return new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_REQUEST_IN_PROGRESS",
                "The analysis request is still being received", retryAfterSeconds(properties.inProgressRetryAfter()));
    }

    private static ApiException invalidKey() {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_IDEMPOTENCY_KEY",
                "Idempotency-Key must be a canonical UUID v4");
    }

    private static long retryAfterSeconds(Duration duration) {
        return Math.max(1, (duration.toMillis() + 999) / 1000);
    }

    private static OffsetDateTime atUtc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private record Row(
            UUID idempotencyId,
            String payloadDigest,
            String inputType,
            String state,
            OffsetDateTime claimExpiresAt,
            UUID analysisId) {
    }

    public record Claim(
            UUID idempotencyId,
            UUID claimToken,
            AnalysisCreatedResponse replay,
            boolean enabled,
            boolean claimed) {

        static Claim disabled() {
            return new Claim(null, null, null, false, false);
        }

        static Claim claimed(UUID idempotencyId, UUID claimToken) {
            return new Claim(idempotencyId, claimToken, null, true, true);
        }

        static Claim replay(UUID idempotencyId, AnalysisCreatedResponse response) {
            return new Claim(idempotencyId, null, response, true, false);
        }

        public boolean replayed() {
            return replay != null;
        }
    }
}
