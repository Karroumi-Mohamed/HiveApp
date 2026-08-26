package com.hiveapp.platform.registry.definition;

public final class PriceBooksFeature {

    public static final String KEY = "price_books";
    public static final String CODE = "platform." + KEY;

    private PriceBooksFeature() {
    }

    public static FeatureDefinition definition() {
        return FeatureDefinition.platformControl(CODE)
                .displayName("Price Books")
                .description("Authoritative recurring prices for exact commercial product revisions")
                .sortOrder(25)
                .build();
    }
}
