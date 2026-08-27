package com.hiveapp.platform.client.plan.domain.entity;

import com.hiveapp.platform.client.plan.domain.constant.CommercialSegmentProductType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Locale;

/** One safe, typed product-holding criterion. */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommercialSegmentProductSelection {

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false, length = 24)
    private CommercialSegmentProductType type;

    @Column(name = "product_code", nullable = false, length = 100)
    private String code;

    public static CommercialSegmentProductSelection of(
            CommercialSegmentProductType type,
            String code
    ) {
        if (type == null || code == null || code.isBlank()) {
            throw new IllegalArgumentException("A product criterion requires type and code.");
        }
        CommercialSegmentProductSelection selection = new CommercialSegmentProductSelection();
        selection.type = type;
        selection.code = code.trim().toUpperCase(Locale.ROOT);
        return selection;
    }
}
