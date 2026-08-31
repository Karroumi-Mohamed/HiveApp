package com.hiveapp.platform.client.plan.domain.constant;

/** Shared audit contract for direct and composite ProductPrice mutations. */
public final class ProductPriceAudit {

    public static final String RESOURCE_TYPE = "PRODUCT_PRICE_ADMIN";
    public static final String CREATE_ACTION = "platform.price_books.create";
    public static final String ACTIVATE_ACTION = "platform.price_books.activate";

    private ProductPriceAudit() {}
}
