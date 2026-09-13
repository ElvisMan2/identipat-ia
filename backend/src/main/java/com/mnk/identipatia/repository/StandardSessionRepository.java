package com.mnk.identipatia.repository;

import com.mnk.identipatia.model.StandardSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface StandardSessionRepository extends JpaRepository<StandardSession, UUID> {
    Optional<StandardSession> findByTokenHash(byte[] tokenHash);
    boolean existsByUserUserId(Long userId);
}
