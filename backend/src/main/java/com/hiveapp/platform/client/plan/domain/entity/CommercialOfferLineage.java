package com.hiveapp.platform.client.plan.domain.entity;

import com.hiveapp.platform.admin.domain.entity.AdminUser;
import com.hiveapp.platform.client.plan.domain.constant.*;
import com.hiveapp.shared.domain.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import lombok.*;

@Entity
@Table(
    name = "commercial_offer_lineages",
    uniqueConstraints = {
      @UniqueConstraint(name = "uk_offer_lineage_business_code", columnNames = "business_code")
    },
    indexes = {
      @Index(name = "idx_offer_lineage_campaign", columnList = "campaign_id"),
      @Index(name = "idx_offer_lineage_owner", columnList = "owner_admin_user_id")
    })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommercialOfferLineage extends BaseEntity {
  @Column(name = "business_code", nullable = false, updatable = false, length = 100)
  private String businessCode;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "campaign_id", nullable = false, updatable = false)
  private CommercialCampaign campaign;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private CommercialOfferDiscovery discovery;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private CommercialOfferAcceptance acceptance;

  @Column(name = "customer_code_hash", length = 64)
  private String customerCodeHash;

  @Column(name = "global_limit")
  private Long globalLimit;

  @Column(name = "per_account_limit")
  private Long perAccountLimit;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "owner_admin_user_id", nullable = false)
  private AdminUser owner;

  /** Durable lineage boundary; code reservation is optional and cannot represent publication. */
  @Column(name = "first_published_at")
  private Instant firstPublishedAt;

  @Version
  @Column(name = "row_version", nullable = false)
  private long version;

  public static CommercialOfferLineage create(
      String code,
      CommercialCampaign campaign,
      CommercialOfferDiscovery discovery,
      CommercialOfferAcceptance acceptance,
      String customerCodeHash,
      Long globalLimit,
      Long perAccountLimit,
      AdminUser owner) {
    var l = new CommercialOfferLineage();
    l.businessCode = Objects.requireNonNull(code).trim().toUpperCase(Locale.ROOT);
    l.campaign = Objects.requireNonNull(campaign);
    l.discovery = Objects.requireNonNull(discovery);
    l.acceptance = Objects.requireNonNull(acceptance);
    l.customerCodeHash = customerCodeHash;
    l.globalLimit = globalLimit;
    l.perAccountLimit = perAccountLimit;
    l.owner = Objects.requireNonNull(owner);
    if (globalLimit != null && globalLimit < 1 || perAccountLimit != null && perAccountLimit < 1)
      throw new IllegalArgumentException("Offer limits must be positive.");
    return l;
  }

  public void reassignOwner(AdminUser value) {
    owner = Objects.requireNonNull(value);
  }

  public void editBeforeFirstPublication(
      CommercialOfferDiscovery discovery,
      CommercialOfferAcceptance acceptance,
      String customerCodeHash,
      Long globalLimit,
      Long perAccountLimit) {
    if (firstPublishedAt != null) {
      throw new IllegalStateException("Published Offer lineage terms are immutable.");
    }
    this.discovery = Objects.requireNonNull(discovery);
    this.acceptance = Objects.requireNonNull(acceptance);
    this.customerCodeHash = customerCodeHash;
    this.globalLimit = globalLimit;
    this.perAccountLimit = perAccountLimit;
    if (globalLimit != null && globalLimit < 1 || perAccountLimit != null && perAccountLimit < 1)
      throw new IllegalArgumentException("Offer limits must be positive.");
  }

  public void markFirstPublished(Instant now) {
    if (firstPublishedAt == null) {
      firstPublishedAt = Objects.requireNonNull(now);
    }
  }
}
