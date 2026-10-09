package org.eda.linkindex;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.eda.linkindex.persistence.LinkRepository;
import org.eda.linkindex.seed.FixtureSeeder;
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

class LinksControllerIntegrationTest extends AbstractLinkIndexIntegrationTest {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP =
            new ParameterizedTypeReference<>() {};

    @Autowired TestRestTemplate rest;
    @Autowired LinkRepository links;
    @Autowired FixtureSeeder seeder;
    @Autowired JdbcClient jdbc;

    @BeforeEach
    void resetState() {
        jdbc.sql("TRUNCATE link_disposition, link_event_outbox, link_link, link_artefact").update();
        seeder.seed();
    }

    @Test
    void S07_POST_links_creates_a_proposed_link() {
        ResponseEntity<Map<String, Object>> resp = rest.exchange(
                "/links", HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "source", "REQ-047",
                        "target", "PC-02",
                        "relation", "proposedBy")),
                MAP);
        assertThat(resp.getStatusCode().value()).isEqualTo(201);
        assertThat(resp.getBody()).containsEntry("state", "proposed");
    }

    @Test
    void AD_06_POST_confirm_without_actor_returns_400() {
        ResponseEntity<Map<String, Object>> created = rest.exchange(
                "/links", HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "source", "REQ-047",
                        "target", "PC-02",
                        "relation", "satisfiedBy")),
                MAP);
        String uuid = tailUuid((String) created.getBody().get("id"));

        ResponseEntity<Map<String, Object>> resp = rest.exchange(
                "/links/" + uuid + "/confirm",
                HttpMethod.POST, new HttpEntity<>(Map.of()), MAP);
        assertThat(resp.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void AD_06_POST_confirm_with_actor_moves_proposed_to_asserted_and_pins_versions() {
        ResponseEntity<Map<String, Object>> created = rest.exchange(
                "/links", HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "source", "REQ-047",
                        "target", "PC-02",
                        "relation", "satisfiedBy")),
                MAP);
        String uuid = tailUuid((String) created.getBody().get("id"));

        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Eda-User", "alice");
        ResponseEntity<Map<String, Object>> resp = rest.exchange(
                "/links/" + uuid + "/confirm",
                HttpMethod.POST, new HttpEntity<>(Map.of(), headers), MAP);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody())
                .containsEntry("state", "asserted")
                .containsEntry("actor", "alice");
        assertThat(resp.getBody().get("sourceVersion")).isEqualTo("1");
        assertThat(resp.getBody().get("targetVersion")).isEqualTo("1");
    }

    @Test
    void S07_GET_links_inverse_returns_mirror_endpoints() {
        String uuid = "134a08bb-eba5-4a11-8782-54ec9c5d57e0";
        ResponseEntity<Map<String, Object>> resp = rest.exchange(
                "/links/{id}/inverse", HttpMethod.GET, null, MAP, uuid);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody())
                .containsEntry("source", "LC-11")
                .containsEntry("target", "REQ-019")
                .containsEntry("relation", "inverseOf:satisfiedBy");
    }

    private static String tailUuid(String fullId) {
        return fullId.substring(fullId.lastIndexOf('/') + 1);
    }
}
