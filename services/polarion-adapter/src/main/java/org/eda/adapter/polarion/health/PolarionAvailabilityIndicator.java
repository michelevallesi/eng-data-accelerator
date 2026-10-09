package org.eda.adapter.polarion.health;

import org.eda.adapter.polarion.availability.PolarionAvailability;
import org.eda.adapter.polarion.availability.PolarionAvailability.Snapshot;
import org.springframework.boot.actuate.availability.ReadinessStateHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Wires {@link PolarionAvailability} into Spring Boot's readiness probe.
 * <p>Not reusing {@link ReadinessStateHealthIndicator} because our signal is
 * continuous (last ping outcome), not a lifecycle state.
 */
@Component("polarion")
public class PolarionAvailabilityIndicator implements HealthIndicator {

    private final PolarionAvailability availability;

    public PolarionAvailabilityIndicator(PolarionAvailability availability) {
        this.availability = availability;
    }

    @Override
    public Health health() {
        Snapshot s = availability.snapshot();
        Health.Builder b = switch (s.status()) {
            case UP -> Health.up();
            case DOWN -> Health.down();
            case UNKNOWN -> Health.unknown();
        };
        if (s.reason() != null) {
            b.withDetail("reason", s.reason());
        }
        if (s.observedAt() != null) {
            b.withDetail("observedAt", s.observedAt().toString());
        }
        return b.build();
    }
}
