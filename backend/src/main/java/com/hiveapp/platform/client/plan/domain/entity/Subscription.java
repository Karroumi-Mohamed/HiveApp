package com.hiveapp.platform.client.plan.domain.entity;

import com.hiveapp.platform.client.account.domain.entity.Account;
import com.hiveapp.shared.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;

@Entity
@Table(name = "subscriptions")
@Getter @Setter
public class Subscription extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "custom_overrides")
    private Object customOverrides;

    @Column(nullable = false)
    private String status; // ACTIVE, PAST_DUE, CANCELLED

    @Column(name = "current_period_end")
    private LocalDateTime currentPeriodEnd;
}
