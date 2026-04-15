package com.hiveapp.platform.client.feature;

import com.hiveapp.shared.quota.AppFeature;
import com.hiveapp.shared.quota.FeatureProvider;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PlatformFeatureProvider implements FeatureProvider {

    @Override
    public List<AppFeature> features() {
        return List.of(PlatformFeature.values());
    }
}
