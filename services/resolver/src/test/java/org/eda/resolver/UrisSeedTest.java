package org.eda.resolver;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.eda.resolver.persistence.OutboxRepository;
import org.eda.resolver.persistence.UriRepository;
import org.eda.resolver.seed.UrisSeedRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Verifies the walking-skeleton bootstrap: {@code eda.fixtures.seed=skid400} loads
 * every entry in {@code fixtures/skid400/uris.json} using the pre-minted URIs.
 */
class UrisSeedTest extends AbstractResolverIntegrationTest {

    @Autowired TestRestTemplate rest;
    @Autowired UriRepository uriRepo;
    @Autowired OutboxRepository outbox;
    @Autowired UrisSeedRunner seedRunner;
    @Autowired JdbcClient jdbc;

    @BeforeEach
    void truncateAndReseed() throws Exception {
        jdbc.sql("TRUNCATE outbox, uri_tombstone, uri_alias, uri CASCADE").update();
        seedRunner.run(null);
    }

    @Test
    void seed_populates_27_URIs_and_one_minted_event_each() {
        assertThat(uriRepo.count()).isEqualTo(27);
        assertThat(outbox.countByType("eda.uri.minted")).isEqualTo(27);
    }

    @Test
    void seeded_URI_resolves_without_a_prior_mint_call() {
        String reqUri = "https://eda.example.org/r/pol/7d33b7bc-90e1-461c-97c1-8845d01a68d8";

        ResponseEntity<Map<String, Object>> response = rest.exchange(
                "/resolve?uri=" + reqUri,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .containsEntry("tool", "Polarion")
                .containsEntry("nativeId", "REQ-019")
                .containsEntry("uri", reqUri)
                .containsEntry("canonicalUri", reqUri);
    }

    @Test
    void seed_is_idempotent_against_a_populated_DB() {
        long before = uriRepo.count();
        seedRunner.run(null);
        long after = uriRepo.count();

        assertThat(after).isEqualTo(before);
    }
}
