package com.hiveapp.shared.quota;

import java.util.List;

/**
 * Implemented by a @Component in each module to register that module's AppFeature enums.
 * FeatureSeeder collects all FeatureProvider beans at startup and seeds Module + Feature rows.
 *
 * Each module must declare exactly one FeatureProvider. Example:
 *
 *   @Component
 *   public class HrFeatureProvider implements FeatureProvider {
 *       public List<AppFeature> features() { return List.of(HrFeature.values()); }
 *   }
 */
public interface FeatureProvider {
    List<AppFeature> features();
}
