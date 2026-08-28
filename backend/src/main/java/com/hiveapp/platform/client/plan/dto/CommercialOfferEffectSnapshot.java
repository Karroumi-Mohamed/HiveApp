package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.platform.client.plan.domain.constant.CommercialOfferDiscountType;
import com.hiveapp.shared.money.ExactDecimal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/** Narrow V1 Offer effects. There is intentionally no generic policy effect escape hatch. */
public record CommercialOfferEffectSnapshot(
    @NotNull CommercialOfferDiscountType discountType,
    @ExactDecimal BigDecimal discountAmount,
    @DecimalMin("0.0001") @DecimalMax("100.0000") BigDecimal percentage,
    @ExactDecimal BigDecimal percentageCap,
    @Valid @Size(max = 100) List<QuotaBonus> finiteQuotaBonuses) {
  public CommercialOfferEffectSnapshot {
    finiteQuotaBonuses = finiteQuotaBonuses == null ? List.of() : List.copyOf(finiteQuotaBonuses);
  }

  public record QuotaBonus(
      @NotNull @Size(max = 160) String featureCode,
      @NotNull @Size(max = 160) String resource,
      @jakarta.validation.constraints.Positive long quantity) {}
}
