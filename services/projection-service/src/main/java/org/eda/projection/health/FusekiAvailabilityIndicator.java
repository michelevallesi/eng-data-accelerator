package org.eda.projection.health;

import org.eda.projection.fuseki.FusekiClient;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("fuseki")
public class FusekiAvailabilityIndicator implements HealthIndicator {

    private final FusekiClient fuseki;

    public FusekiAvailabilityIndicator(FusekiClient fuseki) {
        this.fuseki = fuseki;
    }

    @Override
    public Health health() {
        return fuseki.ping()
                ? Health.up().withDetail("url", fuseki.datasetUrl()).build()
                : Health.down().withDetail("url", fuseki.datasetUrl()).build();
    }
}
