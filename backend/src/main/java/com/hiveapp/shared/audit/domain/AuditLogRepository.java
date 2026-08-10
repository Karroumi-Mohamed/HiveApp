package com.hiveapp.shared.audit.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    List<AuditLog> findAllByActorUserIdOrderByOccurredAtDesc(UUID actorUserId);

    List<AuditLog> findAllByTargetAccountIdOrderByOccurredAtDesc(UUID targetAccountId);
}
