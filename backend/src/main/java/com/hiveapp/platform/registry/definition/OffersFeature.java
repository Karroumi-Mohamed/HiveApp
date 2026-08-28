package com.hiveapp.platform.registry.definition;

public final class OffersFeature {
 public static final String KEY="offers", CODE="platform."+KEY;
 private OffersFeature(){}
 public static FeatureDefinition definition(){return FeatureDefinition.platformControl(CODE).displayName("Commercial Offers").description("Versioned opt-in commercial offers and redemptions").sortOrder(32).build();}
}
