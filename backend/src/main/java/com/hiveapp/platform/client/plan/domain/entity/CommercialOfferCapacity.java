package com.hiveapp.platform.client.plan.domain.entity;

import com.hiveapp.shared.domain.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.*;

@Entity
@Table(
    name = "commercial_offer_capacities",
    uniqueConstraints =
        @UniqueConstraint(name = "uk_offer_capacity_lineage", columnNames = "lineage_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommercialOfferCapacity extends BaseEntity {
  @Column(name = "lineage_id", nullable = false, updatable = false)
  private UUID lineageId;

  @Column(name = "reserved_count", nullable = false)
  private long reservedCount;

  @Column(name = "applied_count", nullable = false)
  private long appliedCount;

  @Version
  @Column(name = "row_version", nullable = false)
  private long version;

  public static CommercialOfferCapacity create(UUID lineage) {
    var c = new CommercialOfferCapacity();
    c.lineageId = lineage;
    return c;
  }

  public void reserve(long globalLimit, long accountUsed, long accountLimit) {
    if (globalLimit >= 0 && reservedCount + appliedCount >= globalLimit)
      throw new com.hiveapp.shared.exception.OfferRedemptionBlockedException();
    if (accountLimit >= 0 && accountUsed >= accountLimit)
      throw new com.hiveapp.shared.exception.OfferRedemptionBlockedException();
    reservedCount++;
  }

  public void apply() {
    if (reservedCount < 1) throw new IllegalStateException("Offer has no reserved capacity.");
    reservedCount--;
    appliedCount++;
  }

  public void release() {
    if (reservedCount < 1) throw new IllegalStateException("Offer capacity was already released.");
    reservedCount--;
  }
}
