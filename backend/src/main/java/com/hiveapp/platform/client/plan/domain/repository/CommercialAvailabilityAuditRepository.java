package com.hiveapp.platform.client.plan.domain.repository;

import com.hiveapp.shared.audit.domain.AuditLog;
import com.hiveapp.shared.audit.domain.AuditOutcome;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.UUID;

/** Paged history of detailed successes plus every attempted failure/denial. */
public interface CommercialAvailabilityAuditRepository extends Repository<AuditLog, UUID> {

    @Query("""
            select log from AuditLog log
            where log.resourceType = :resourceType
              and log.resourceId = :resourceId
              and (log.outcome <> :succeeded or log.action in :detailedSuccessActions)
            """)
    Page<AuditLog> findHistory(
            @Param("resourceType") String resourceType,
            @Param("resourceId") String resourceId,
            @Param("succeeded") AuditOutcome succeeded,
            @Param("detailedSuccessActions") Collection<String> detailedSuccessActions,
            Pageable pageable);
}
