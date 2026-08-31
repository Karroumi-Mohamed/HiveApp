package com.hiveapp.shared.observability;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "hiveapp.observability")
public class ObservabilityProperties {
    private Logs logs = new Logs();

    @Getter
    @Setter
    public static class Logs {
        private String provider;
        private String url;
    }
}
