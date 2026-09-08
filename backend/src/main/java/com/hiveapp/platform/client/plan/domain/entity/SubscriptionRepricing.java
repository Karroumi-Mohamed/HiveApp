package com.hiveapp.platform.client.plan.domain.entity;

import com.hiveapp.platform.client.plan.dto.RepricingModels;
import com.hiveapp.shared.domain.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "subscription_repricings")
@Getter
@Setter
public class SubscriptionRepricing extends BaseEntity {
  @Version private long version;

  @Column(nullable = false)
  private UUID actorUserId;

  @Column(nullable = false, length = 24)
  private String status = "PREVIEWED";

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false)
  private RepricingModels.Request request;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  private ProductPrice sourcePrice;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  private ProductPrice targetPrice;

  private long targetPriceVersion;
  private long sourcePriceVersion;
  private long catalogVersion;

  @Column(nullable = false)
  private String registryVersion;

  @Column(nullable = false, length = 64)
  private String fingerprint;

  private UUID segmentActivationId;
  private int targetCount;
  private Instant confirmedAt;
  private UUID cancelledBy;

  @Column(length = 2000)
  private String cancellationReason;
}
