package com.hiveapp.shared.audit.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Collection;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    List<AuditLog> findAllByActorUserIdOrderByOccurredAtDesc(UUID actorUserId);

    List<AuditLog> findAllByTargetAccountIdOrderByOccurredAtDesc(UUID targetAccountId);

    List<AuditLog> findAllByResourceTypeAndResourceIdOrderByOccurredAtDesc(
            String resourceType, String resourceId);

    Page<AuditLog> findAllByResourceTypeAndResourceId(
            String resourceType, String resourceId, Pageable pageable);

    Page<AuditLog> findAllByResourceTypeAndResourceIdIn(
            String resourceType, Collection<String> resourceIds, Pageable pageable);
}
