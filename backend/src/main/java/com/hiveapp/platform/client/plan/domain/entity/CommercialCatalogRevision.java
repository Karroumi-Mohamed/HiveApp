package com.hiveapp.platform.client.plan.domain.entity;

import com.hiveapp.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Singleton database authority used to serialize commercial-catalogue mutations and invalidate
 * previews that were assembled from more than one catalogue table.
 */
@Entity
@Table(name = "commercial_catalog_revisions")
@Getter
@Setter
public class CommercialCatalogRevision extends BaseEntity {

    @Column(name = "lock_name", nullable = false, unique = true, updatable = false)
    private String lockName;

    @Column(nullable = false)
    private long revision;
}
