package org.eda.adapter.teamcenter.health;

import org.eda.adapter.teamcenter.availability.TeamcenterAvailability;
import org.eda.adapter.teamcenter.availability.TeamcenterAvailability.Snapshot;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("teamcenter")
public class TeamcenterAvailabilityIndicator implements HealthIndicator {

    private final TeamcenterAvailability availability;

    public TeamcenterAvailabilityIndicator(TeamcenterAvailability availability) {
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
