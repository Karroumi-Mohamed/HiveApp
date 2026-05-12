package com.hiveapp.platform.registry.definition.service;

import com.hiveapp.platform.registry.definition.FeatureContributor;
import com.hiveapp.platform.registry.definition.FeatureDefinition;

import java.util.List;

public abstract class AbstractFeatureService implements FeatureContributor {

    protected abstract FeatureDefinition featureDefinition();

    @Override
    public final List<FeatureDefinition> featureDefinitions() {
        return List.of(featureDefinition());
    }
}
