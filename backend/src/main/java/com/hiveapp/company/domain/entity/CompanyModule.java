package com.hiveapp.company.domain.entity;

import com.hiveapp.shared.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "company_modules")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompanyModule extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "module_id", nullable = false)
    private UUID moduleId;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "deactivated_at")
    private Instant deactivatedAt;

    public void activate() {
        this.isActive = true;
        this.activatedAt = Instant.now();
        this.deactivatedAt = null;
    }

    public void deactivate() {
        this.isActive = false;
        this.deactivatedAt = Instant.now();
    }
}
