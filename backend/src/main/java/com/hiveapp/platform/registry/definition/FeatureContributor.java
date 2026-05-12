package com.hiveapp.platform.registry.definition;

import java.util.List;

/**
 * Implemented by feature-owned code to expose hard-coded feature definitions.
 */
public interface FeatureContributor {

    List<FeatureDefinition> featureDefinitions();
}
