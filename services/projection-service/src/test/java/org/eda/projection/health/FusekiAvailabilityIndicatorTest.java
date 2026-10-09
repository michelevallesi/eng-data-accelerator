package org.eda.projection.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.eda.projection.fuseki.FusekiClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

/**
 * Pure-unit coverage for the FR-A10 "explicit degradation" readiness probe.
 * Simulating a DOWN Fuseki in integration would need stopping the container
 * mid-test, which is noisy; the indicator's logic is simple enough that
 * mocking the ping is enough.
 */
class FusekiAvailabilityIndicatorTest {

    @Test
    void FR_A10_reports_UP_when_ping_succeeds() {
        FusekiClient fuseki = mock(FusekiClient.class);
        when(fuseki.ping()).thenReturn(true);
        when(fuseki.datasetUrl()).thenReturn("http://localhost:3030/eda");

        Health h = new FusekiAvailabilityIndicator(fuseki).health();
        assertThat(h.getStatus()).isEqualTo(Status.UP);
        assertThat(h.getDetails()).containsEntry("url", "http://localhost:3030/eda");
    }

    @Test
    void FR_A10_reports_DOWN_when_ping_fails() {
        FusekiClient fuseki = mock(FusekiClient.class);
        when(fuseki.ping()).thenReturn(false);
        when(fuseki.datasetUrl()).thenReturn("http://localhost:3030/eda");

        Health h = new FusekiAvailabilityIndicator(fuseki).health();
        assertThat(h.getStatus()).isEqualTo(Status.DOWN);
        assertThat(h.getDetails()).containsEntry("url", "http://localhost:3030/eda");
    }
}
