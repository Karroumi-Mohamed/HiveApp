package com.hiveapp.shared.audit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_log", indexes = {
        @Index(name = "idx_audit_actor_time", columnList = "actor_user_id, occurred_at"),
        @Index(name = "idx_audit_account_time", columnList = "target_account_id, occurred_at"),
        @Index(name = "idx_audit_resource", columnList = "resource_type, resource_id"),
        @Index(name = "idx_audit_action_time", columnList = "action, occurred_at"),
        @Index(name = "idx_audit_request_id", columnList = "request_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_surface", nullable = false, updatable = false, length = 32)
    private AuditActorSurface actorSurface;

    @Column(name = "actor_user_id", updatable = false)
    private UUID actorUserId;

    @Column(name = "client_account_id", updatable = false)
    private UUID clientAccountId;

    @Column(name = "target_account_id", updatable = false)
    private UUID targetAccountId;

    @Column(name = "target_company_id", updatable = false)
    private UUID targetCompanyId;

    @Column(name = "collaboration_id", updatable = false)
    private UUID collaborationId;

    @Column(nullable = false, updatable = false, length = 180)
    private String action;

    @Column(name = "resource_type", nullable = false, updatable = false, length = 100)
    private String resourceType;

    @Column(name = "resource_id", updatable = false, length = 100)
    private String resourceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 16)
    private AuditOutcome outcome;

    @Column(name = "request_method", updatable = false, length = 12)
    private String requestMethod;

    @Column(name = "request_path", updatable = false, length = 500)
    private String requestPath;

    @Column(name = "request_id", updatable = false, length = 128)
    private String requestId;

    @Column(name = "request_data", updatable = false, columnDefinition = "TEXT")
    private String requestData;

    @Column(name = "result_data", updatable = false, columnDefinition = "TEXT")
    private String resultData;

    @Column(name = "failure_type", updatable = false, length = 180)
    private String failureType;

    @Builder
    private AuditLog(
            Instant occurredAt,
            AuditActorSurface actorSurface,
            UUID actorUserId,
            UUID clientAccountId,
            UUID targetAccountId,
            UUID targetCompanyId,
            UUID collaborationId,
            String action,
            String resourceType,
            String resourceId,
            AuditOutcome outcome,
            String requestMethod,
            String requestPath,
            String requestId,
            String requestData,
            String resultData,
            String failureType
    ) {
        this.occurredAt = occurredAt;
        this.actorSurface = actorSurface;
        this.actorUserId = actorUserId;
        this.clientAccountId = clientAccountId;
        this.targetAccountId = targetAccountId;
        this.targetCompanyId = targetCompanyId;
        this.collaborationId = collaborationId;
        this.action = action;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.outcome = outcome;
        this.requestMethod = requestMethod;
        this.requestPath = requestPath;
        this.requestId = requestId;
        this.requestData = requestData;
        this.resultData = resultData;
        this.failureType = failureType;
    }

    @PreUpdate
    @PreRemove
    private void rejectMutation() {
        throw new IllegalStateException("Audit records are append-only");
    }
}
