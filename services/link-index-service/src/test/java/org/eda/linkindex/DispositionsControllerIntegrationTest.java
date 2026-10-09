package org.eda.linkindex;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.eda.linkindex.domain.ArtefactRecord;
import org.eda.linkindex.domain.DispositionOutcome;
import org.eda.linkindex.domain.DispositionRecord;
import org.eda.linkindex.domain.LinkState;
import org.eda.linkindex.persistence.ArtefactRepository;
import org.eda.linkindex.persistence.DispositionRepository;
import org.eda.linkindex.persistence.LinkRepository;
import org.eda.linkindex.seed.FixtureSeeder;
import org.eda.linkindex.state.LinkStateMachine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;

class DispositionsControllerIntegrationTest extends AbstractLinkIndexIntegrationTest {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP =
            new ParameterizedTypeReference<>() {};
    private static final String REQ_019_LINK_ID =
            "https://eda.example.org/link/134a08bb-eba5-4a11-8782-54ec9c5d57e0";
    private static final String REQ_019_URI =
            "https://eda.example.org/r/pol/7d33b7bc-90e1-461c-97c1-8845d01a68d8";

    @Autowired TestRestTemplate rest;
    @Autowired LinkStateMachine machine;
    @Autowired LinkRepository links;
    @Autowired ArtefactRepository artefacts;
    @Autowired DispositionRepository dispositions;
    @Autowired FixtureSeeder seeder;
    @Autowired JdbcClient jdbc;

    @BeforeEach
    void resetState() {
        jdbc.sql("TRUNCATE link_disposition, link_event_outbox, link_link, link_artefact").update();
        seeder.seed();
    }

    private UUID driveSupersessionAndFindDispositionId() {
        // Simulate the Kafka listener directly (keeps the test narrow + fast).
        machine.onResourceChanged(null, REQ_019_URI, "MODIFICATION", "2", UUID.randomUUID().toString());
        DispositionRecord d = dispositions.filter(null, true).stream()
                .filter(r -> REQ_019_LINK_ID.equals(r.linkId()))
                .findFirst().orElseThrow();
        assertThat(d.owner()).isEqualTo("requirements-engineer");
        return d.id();
    }

    @Test
    void FR_S14_disposition_resolved_confirmed_re_asserts_link_on_new_version() {
        UUID dispositionId = driveSupersessionAndFindDispositionId();

        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Eda-User", "alice");
        ResponseEntity<Map<String, Object>> resp = rest.exchange(
                "/dispositions/{id}/resolve?outcome=confirmed",
                HttpMethod.POST, new HttpEntity<>(Map.of(), headers), MAP, dispositionId);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody())
                .containsEntry("outcome", "confirmed")
                .containsEntry("actor", "alice");

        assertThat(links.findById(REQ_019_LINK_ID).orElseThrow().state())
                .isEqualTo(LinkState.ASSERTED);
        ArtefactRecord req19 = artefacts.findByUri(REQ_019_URI).orElseThrow();
        assertThat(links.findById(REQ_019_LINK_ID).orElseThrow().sourceVersion())
                .isEqualTo(req19.version());
    }

    @Test
    void FR_S15_disposition_resolved_updated_closes_without_re_assertion() {
        UUID dispositionId = driveSupersessionAndFindDispositionId();

        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Eda-User", "alice");
        ResponseEntity<Map<String, Object>> resp = rest.exchange(
                "/dispositions/{id}/resolve?outcome=updated",
                HttpMethod.POST, new HttpEntity<>(Map.of(), headers), MAP, dispositionId);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).containsEntry("outcome", "updated");

        // Link stays superseded; the disposition is just closed.
        assertThat(links.findById(REQ_019_LINK_ID).orElseThrow().state())
                .isEqualTo(LinkState.SUPERSEDED);
        DispositionRecord resolved = dispositions.findById(dispositionId).orElseThrow();
        assertThat(resolved.isOpen()).isFalse();
        assertThat(resolved.outcome()).isEqualTo(DispositionOutcome.UPDATED);
    }

    @Test
    void S07_resolve_without_actor_returns_400() {
        UUID dispositionId = driveSupersessionAndFindDispositionId();
        ResponseEntity<Map<String, Object>> resp = rest.exchange(
                "/dispositions/{id}/resolve?outcome=confirmed",
                HttpMethod.POST, new HttpEntity<>(Map.of()), MAP, dispositionId);
        assertThat(resp.getStatusCode().value()).isEqualTo(400);
    }
}
