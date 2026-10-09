package org.eda.adapter.teamcenter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.eda.adapter.teamcenter.availability.TeamcenterAvailability;
import org.eda.adapter.teamcenter.availability.TeamcenterAvailability.Status;
import org.eda.adapter.teamcenter.events.KafkaPublisher;
import org.eda.adapter.teamcenter.persistence.CursorRepository;
import org.eda.adapter.teamcenter.polling.TrsPoller;
import org.eda.adapter.teamcenter.teamcenter.TeamcenterUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
        "eda.teamcenter.base-url=http://localhost:1"
})
class AdapterUnavailableTest extends AbstractAdapterIntegrationTest {

    private static final String SOURCE = "urn:eda:adapter:teamcenter";

    @Autowired TrsPoller poller;
    @Autowired TeamcenterAvailability availability;
    @Autowired CursorRepository cursorRepo;
    @Autowired KafkaPublisher kafkaPublisher;
    @Autowired JdbcClient jdbc;
    @Autowired TestRestTemplate rest;

    @BeforeEach
    void resetState() {
        jdbc.sql("TRUNCATE tc_processed_events, tc_trs_cursor, tc_event_outbox").update();
    }

    @Test
    void FR_A10_simulator_down_marks_adapter_not_ready_without_emitting_partial_data() {
        assertThatThrownBy(poller::pollOnce)
                .isInstanceOf(TeamcenterUnavailableException.class);

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
            Map<String, Object> teamcenter = (Map<String, Object>) components.get("teamcenter");
            if (teamcenter != null) {
                assertThat(teamcenter.get("status")).isEqualTo("DOWN");
            }
        }
    }
}
