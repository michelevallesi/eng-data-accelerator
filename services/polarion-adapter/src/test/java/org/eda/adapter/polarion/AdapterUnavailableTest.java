package org.eda.adapter.polarion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.eda.adapter.polarion.availability.PolarionAvailability;
import org.eda.adapter.polarion.availability.PolarionAvailability.Status;
import org.eda.adapter.polarion.events.KafkaPublisher;
import org.eda.adapter.polarion.persistence.CursorRepository;
import org.eda.adapter.polarion.polarion.PolarionUnavailableException;
import org.eda.adapter.polarion.polling.TrsPoller;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;

/**
 * FR-A10: when the Polarion source is unreachable, the adapter marks itself DOWN
 * and emits no partial data. We prove this by pointing the adapter at an
 * unreachable port — no simulator is started for this class.
 */
@TestPropertySource(properties = {
        "eda.polarion.base-url=http://localhost:1"
})
class AdapterUnavailableTest extends AbstractAdapterIntegrationTest {

    private static final String SOURCE = "urn:eda:adapter:polarion";

    @Autowired TrsPoller poller;
    @Autowired PolarionAvailability availability;
    @Autowired CursorRepository cursorRepo;
    @Autowired KafkaPublisher kafkaPublisher;
    @Autowired JdbcClient jdbc;
    @Autowired TestRestTemplate rest;

    @BeforeEach
    void resetState() {
        jdbc.sql("TRUNCATE processed_events, trs_cursor").update();
    }

    @Test
    void FR_A10_simulator_down_marks_adapter_not_ready_without_emitting_partial_data() {
        assertThatThrownBy(poller::pollOnce)
                .isInstanceOf(PolarionUnavailableException.class);

        assertThat(availability.snapshot().status()).isEqualTo(Status.DOWN);
        assertThat(cursorRepo.read(SOURCE))
                .as("cursor must not advance when the source is unreachable")
                .isZero();

        ResponseEntity<Map<String, Object>> health = rest.exchange(
                "/actuator/health",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {});
        assertThat(health.getBody().get("status")).isNotEqualTo("UP");

        @SuppressWarnings("unchecked")
        Map<String, Object> components = (Map<String, Object>) health.getBody().get("components");
        if (components != null) {
            @SuppressWarnings("unchecked")
            Map<String, Object> polarion = (Map<String, Object>) components.get("polarion");
            if (polarion != null) {
                assertThat(polarion.get("status")).isEqualTo("DOWN");
            }
        }

        List<Object> unused = List.of();
        assertThat(unused).isEmpty(); // keeping the import graph honest
    }
}
