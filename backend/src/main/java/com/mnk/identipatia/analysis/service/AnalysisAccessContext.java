package com.mnk.identipatia.analysis.service;

import com.mnk.identipatia.model.StandardSession;
import com.mnk.identipatia.model.User;

import java.util.Objects;

/**
 * Identifies the mutually exclusive context that owns an analysis. A STANDARD
 * context has a session and later requires its consent event; an ADMIN context
 * is derived from JWT and persists both access-context columns as null.
 */
public record AnalysisAccessContext(Mode mode, User user, StandardSession session) {

    public AnalysisAccessContext {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(user, "user");
        if ((mode == Mode.STANDARD_SESSION) != (session != null)) {
            throw new IllegalArgumentException("Only STANDARD access has a session");
        }
    }

    public static AnalysisAccessContext standard(User user, StandardSession session) {
        return new AnalysisAccessContext(Mode.STANDARD_SESSION, user, session);
    }

    public static AnalysisAccessContext admin(User user) {
        return new AnalysisAccessContext(Mode.ADMIN_JWT, user, null);
    }

    public enum Mode {
        STANDARD_SESSION,
        ADMIN_JWT
    }
}
