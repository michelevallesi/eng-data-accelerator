package org.eda.resolver;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.eda.resolver.domain.ResolverService;
import org.eda.resolver.persistence.OutboxRepository;
import org.eda.resolver.persistence.OutboxRepository.OutboxRow;
import org.eda.resolver.persistence.UriRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;

class ResolverIntegrationTest extends AbstractResolverIntegrationTest {

    private static final Pattern OPAQUE_URI = Pattern.compile(
            "https://eda\\.example\\.org/r/pol/"
                    + "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_SO =
            new ParameterizedTypeReference<>() {};

    @Autowired TestRestTemplate rest;
    @Autowired ResolverService service;
    @Autowired UriRepository uriRepo;
    @Autowired OutboxRepository outbox;
    @Autowired JdbcClient jdbc;
    @Autowired ObjectMapper mapper;

    @BeforeEach
    void truncate() {
        jdbc.sql("TRUNCATE outbox, uri_tombstone, uri_alias, uri CASCADE").update();
    }

    private ResponseEntity<Map<String, Object>> post(String path, Map<String, Object> body) {
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body), MAP_SO);
    }

    private ResponseEntity<Map<String, Object>> get(String path) {
        return rest.exchange(path, HttpMethod.GET, null, MAP_SO);
    }

    @Test
    void FR_F02_AD_03_mint_is_idempotent_per_tool_and_nativeId() {
        Map<String, Object> body = Map.of("tool", "Polarion", "nativeId", "REQ-019");
        ResponseEntity<Map<String, Object>> first = post("/resolver/mint", body);
        ResponseEntity<Map<String, Object>> second = post("/resolver/mint", body);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(first.getBody().get("uri")).isEqualTo(second.getBody().get("uri"));
        assertThat(first.getBody().get("uri").toString()).matches(OPAQUE_URI);

        assertThat(outbox.countByType("eda.uri.minted"))
                .as("second mint must not duplicate the outbox event")
                .isEqualTo(1);
    }

    @Test
    void AD_03_rename_does_not_change_the_URI() {
        String uri1 = service.mint("Polarion", "REQ-019").uri();
        String uri2 = service.mint("Polarion", "REQ-019").uri();
        assertThat(uri1).isEqualTo(uri2);
        assertThat(uri1).matches(OPAQUE_URI);
    }

    @Test
    void AD_03_mint_URI_never_embeds_the_native_id_or_attribute_strings() {
        String weirdNativeId = "/evil/../../path?name=REQ-019&foo=bar#frag";
        String uri = service.mint("Polarion", weirdNativeId).uri();

        assertThat(uri).matches(OPAQUE_URI);
        assertThat(uri).doesNotContain(weirdNativeId);
        assertThat(uri).doesNotContain("REQ-019");
        assertThat(uri).doesNotContain("foo");
    }

    @Test
    void FR_A09_tombstoned_URI_resolves_to_410_Gone() {
        String uri = service.mint("Polarion", "TC-211").uri();

        service.tombstone(uri, "deleted via simulator");

        ResponseEntity<Map<String, Object>> response = get("/resolve?uri=" + uri);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GONE);
        assertThat(response.getBody())
                .containsEntry("status", 410)
                .containsEntry("reason", "deleted via simulator");
    }

    @Test
    void FR_F02_alias_resolves_old_URI_to_new_target() {
        String uriA = service.mint("Teamcenter", "400-1105/C").uri();
        String uriB = service.mint("Teamcenter", "400-1105/D").uri();

        service.alias(uriA, uriB);

        ResponseEntity<Map<String, Object>> response = get("/resolve?uri=" + uriA);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("canonicalUri")).isEqualTo(uriB);
        assertThat(response.getBody().get("nativeId")).isEqualTo("400-1105/D");

        String linkHeader = response.getHeaders().getFirst("Link");
        assertThat(linkHeader).contains(uriB).contains("canonical");

        assertThat(outbox.countByType("eda.uri.aliased")).isEqualTo(1);
    }

    @Test
    void ADR_0016_outbox_is_written_in_the_same_transaction_as_the_URI_row() {
        long before = outbox.count();
        service.mint("Capella", "LC-11");
        long after = outbox.count();

        assertThat(after - before).isEqualTo(1);
        List<OutboxRow> rows = outbox.all();
        OutboxRow last = rows.get(rows.size() - 1);
        assertThat(last.eventType()).isEqualTo("eda.uri.minted");
    }

    @Test
    void ADR_0026_mint_event_carries_mandatory_CloudEvents_attributes() throws Exception {
        String uri = service.mint("COMOS", "PSV-418").uri();

        List<OutboxRow> rows = outbox.all();
        OutboxRow row = rows.stream()
                .filter(r -> r.eventType().equals("eda.uri.minted"))
                .findFirst()
                .orElseThrow();

        assertThat(row.eventId()).isNotNull();
        assertThat(row.eventSource()).isEqualTo("urn:eda:resolver");
        assertThat(row.eventSubject()).isEqualTo(uri);
        assertThat(row.eventTime()).isAfter(OffsetDateTime.now().minusMinutes(1));
        JsonNode data = mapper.readTree(row.dataJson());
        assertThat(data.get("uri").asText()).isEqualTo(uri);
        assertThat(data.get("tool").asText()).isEqualTo("COMOS");
        assertThat(data.get("nativeId").asText()).isEqualTo("PSV-418");
    }
}
