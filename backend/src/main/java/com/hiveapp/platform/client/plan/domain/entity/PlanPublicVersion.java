package com.hiveapp.platform.client.plan.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

/** Explicit catalogue choice. Publishing a successor never changes this record. */
@Entity
@Table(name = "plan_public_versions")
@Getter @Setter
public class PlanPublicVersion {
    @Id
    private UUID lineageId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;
    @Column(nullable = false)
    private UUID selectedBy;
    @Column(nullable = false)
    private Instant selectedAt;
    @Version
    private long version;
}
