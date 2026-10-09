package org.eda.projection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.kafka.CloudEventSerializer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.eda.projection.fuseki.FusekiClient;
import org.eda.projection.vocab.EdaVocab;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

/**
 * S06 integration coverage. Produces CloudEvents directly to Kafka (bypassing
 * the adapters) and verifies the projection service writes the expected named
 * graphs into Fuseki. The projection listener is auto-started by the Spring
 * context.
 */
class ProjectionIntegrationTest extends AbstractProjectionIntegrationTest {

    private static final String SOURCE = "urn:eda:adapter:polarion";

    @Autowired FusekiClient fuseki;
    @Autowired TestRestTemplate rest;
    @Value("${eda.kafka.topics.polarion-changes}") String polarionTopic;
    @Value("${eda.kafka.topics.teamcenter-changes}") String teamcenterTopic;

    private KafkaProducer<String, CloudEvent> producer;
    private String subject;

    @BeforeEach
    void resetState() {
        // Deliberately do NOT clear Fuseki between tests — each test uses a fresh
        // random subject URI (and the FR-B04 duplicate test filters by ce_id), so
        // accumulated state does not interfere. Leaving Fuseki populated is also
        // what RebuildServiceIntegrationTest's AC needs: live vs rebuilt must
        // reflect the same event history.
        subject = "https://eda.example.org/r/pol/" + UUID.randomUUID();
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, CloudEventSerializer.class);
        producer = new KafkaProducer<>(props);
    }

    private CloudEvent change(String id, long trsOrder, int version, String payload) {
        return CloudEventBuilder.v1()
                .withId(id)
                .withSource(URI.create(SOURCE))
                .withType("eda.resource.changed")
                .withSubject(subject)
                .withTime(OffsetDateTime.parse("2026-10-07T12:00:00Z"))
                .withExtension("edakind", "MODIFICATION")
                .withExtension("edaversion", version)
                .withExtension("edatrsorder", trsOrder)
                .withExtension("edasourcetime", "2026-10-07T12:00:00Z")
                .withDataContentType("application/ld+json")
                .withData(payload.getBytes(StandardCharsets.UTF_8))
                .build();
    }

    private CloudEvent deletion(String id, long trsOrder, int version) {
        return CloudEventBuilder.v1()
                .withId(id)
                .withSource(URI.create(SOURCE))
                .withType("eda.resource.changed")
                .withSubject(subject)
                .withTime(OffsetDateTime.parse("2026-10-07T12:01:00Z"))
                .withExtension("edakind", "DELETION")
                .withExtension("edaversion", version)
                .withExtension("edatrsorder", trsOrder)
                .build();
    }

    private String jsonLd(String descr) {
        return """
                {"@context":{"dcterms":"http://purl.org/dc/terms/"},
                 "@id":"%s",
                 "dcterms:description":"%s"}
                """.formatted(subject, descr);
    }

    private void publish(CloudEvent event) {
        producer.send(new ProducerRecord<>(polarionTopic, subject, event));
        producer.flush();
    }

    private void awaitAsk(String ask) {
        await().atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(250))
                .until(() -> fuseki.ask(ask));
    }

    @Test
    void FR_M01_resource_changed_lands_in_a_version_named_graph() {
        String id = UUID.randomUUID().toString();
        publish(change(id, 1L, 1, jsonLd("v1 payload")));

        String versionGraph = EdaVocab.versionIri(subject, "1");
        awaitAsk(String.format("ASK { GRAPH <%s> { <%s> ?p ?o } }", versionGraph, subject));
    }

    @Test
    void FR_M02_every_projected_triple_traces_to_a_prov_Activity_with_the_cloudEvent_id() {
        String id = UUID.randomUUID().toString();
        publish(change(id, 1L, 1, jsonLd("v1 payload")));

        String versionGraph = EdaVocab.versionIri(subject, "1");
        awaitAsk(String.format("ASK { GRAPH <urn:eda:prov> { <%s> a <%s> } }",
                EdaVocab.eventIri(id), EdaVocab.PROV_ACTIVITY.getURI()));
        boolean traceable = fuseki.ask(String.format(
                "ASK { GRAPH <%s> { <%s> <%s> <%s> } }",
                versionGraph, subject, EdaVocab.PROV_WAS_GENERATED_BY.getURI(),
                EdaVocab.eventIri(id)));
        assertThat(traceable).isTrue();
    }

    @Test
    void FR_M05_after_second_revision_both_versions_are_queryable_only_latest_is_eda_current() {
        publish(change(UUID.randomUUID().toString(), 1L, 1, jsonLd("v1")));
        publish(change(UUID.randomUUID().toString(), 2L, 2, jsonLd("v2")));

        String v1 = EdaVocab.versionIri(subject, "1");
        String v2 = EdaVocab.versionIri(subject, "2");
        String concept = EdaVocab.conceptIri(subject);

        awaitAsk(String.format("ASK { GRAPH <%s> { ?s ?p ?o } }", v1));
        awaitAsk(String.format("ASK { GRAPH <%s> { ?s ?p ?o } }", v2));
        awaitAsk(String.format(
                "ASK { GRAPH <%s> { <%s> <%s> <%s> } }",
                concept, concept, EdaVocab.EDA_CURRENT.getURI(), v2));
        boolean currentIsV1 = fuseki.ask(String.format(
                "ASK { GRAPH <%s> { <%s> <%s> <%s> } }",
                concept, concept, EdaVocab.EDA_CURRENT.getURI(), v1));
        assertThat(currentIsV1).as("eda:current must point only to v2").isFalse();
    }

    @Test
    void FR_M08_eda_dataAge_tracks_edasourcetime() {
        publish(change(UUID.randomUUID().toString(), 1L, 1, jsonLd("v1")));
        String concept = EdaVocab.conceptIri(subject);
        awaitAsk(String.format(
                "ASK { GRAPH <%s> { <%s> <%s> \"2026-10-07T12:00:00Z\" } }",
                concept, concept, EdaVocab.EDA_DATA_AGE.getURI()));
    }

    @Test
    void FR_M05_DELETION_event_tombstones_version_and_drops_eda_current() {
        String createId = UUID.randomUUID().toString();
        publish(change(createId, 1L, 1, jsonLd("v1")));
        awaitAsk(String.format(
                "ASK { GRAPH <urn:eda:prov> { <%s> a <%s> } }",
                EdaVocab.eventIri(createId), EdaVocab.PROV_ACTIVITY.getURI()));

        String deleteId = UUID.randomUUID().toString();
        publish(deletion(deleteId, 2L, 1));
        // The prov:Activity is written LAST by EventProjector, so this ASK
        // guarantees projectDeletion's version + concept writes have landed.
        awaitAsk(String.format(
                "ASK { GRAPH <urn:eda:prov> { <%s> a <%s> } }",
                EdaVocab.eventIri(deleteId), EdaVocab.PROV_ACTIVITY.getURI()));

        String v1 = EdaVocab.versionIri(subject, "1");
        String concept = EdaVocab.conceptIri(subject);
        boolean currentRemoved = !fuseki.ask(String.format(
                "ASK { GRAPH <%s> { <%s> <%s> ?v } }",
                concept, concept, EdaVocab.EDA_CURRENT.getURI()));
        assertThat(currentRemoved).as("deletion drops eda:current").isTrue();
        // FR-M05: version payload survives for temporal queries, tagged with
        // eda:deletedAt rather than wiped.
        boolean payloadSurvives = fuseki.ask(String.format(
                "ASK { GRAPH <%s> { <%s> <http://purl.org/dc/terms/description> ?o } }",
                v1, subject));
        boolean deletedAt = fuseki.ask(String.format(
                "ASK { GRAPH <%s> { <%s> <%s> ?t } }",
                v1, subject, EdaVocab.EDA_DELETED_AT.getURI()));
        assertThat(payloadSurvives).as("version payload survives DELETION").isTrue();
        assertThat(deletedAt).as("version carries eda:deletedAt").isTrue();
    }

    @Test
    void FR_B04_duplicate_cloudEvent_id_is_a_noop() throws InterruptedException {
        String id = UUID.randomUUID().toString();
        publish(change(id, 1L, 1, jsonLd("v1 payload")));
        publish(change(id, 1L, 1, jsonLd("v1 again")));

        String versionGraph = EdaVocab.versionIri(subject, "1");
        awaitAsk(String.format("ASK { GRAPH <%s> { ?s ?p ?o } }", versionGraph));

        await().atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(250))
                .until(() -> countActivitiesForEvent(id) >= 1);
        Thread.sleep(1000);
        assertThat(countActivitiesForEvent(id))
                .as("only one prov:Activity per ce_id even after a replay")
                .isEqualTo(1);
    }

    private long countActivitiesForEvent(String ceId) {
        long n = 0;
        org.apache.jena.query.ResultSet rs = fuseki.select(String.format(
                "SELECT ?a WHERE { GRAPH <urn:eda:prov> { ?a a <%s> FILTER(str(?a) = \"%s\") } }",
                EdaVocab.PROV_ACTIVITY.getURI(), EdaVocab.eventIri(ceId)));
        while (rs.hasNext()) {
            rs.next();
            n++;
        }
        return n;
    }

    // ---- Review follow-ups: admin endpoints, Teamcenter topic, health ----

    @Test
    void S06_teamcenter_topic_events_are_projected_the_same_way_as_polarion() {
        String tcSubject = "https://eda.example.org/r/tc/" + UUID.randomUUID();
        String ceId = UUID.randomUUID().toString();
        CloudEvent tc = CloudEventBuilder.v1()
                .withId(ceId)
                .withSource(URI.create("urn:eda:adapter:teamcenter"))
                .withType("eda.resource.changed")
                .withSubject(tcSubject)
                .withTime(OffsetDateTime.parse("2026-10-07T12:00:00Z"))
                .withExtension("edakind", "CREATION")
                .withExtension("edaversion", 1)
                .withExtension("edatrsorder", 1L)
                .withExtension("edasourcetime", "2026-10-07T12:00:00Z")
                .withDataContentType("application/ld+json")
                .withData("""
                        {"@context":{"dcterms":"http://purl.org/dc/terms/"},
                         "@id":"%s", "dcterms:description":"tc body"}
                        """.formatted(tcSubject).getBytes(StandardCharsets.UTF_8))
                .build();
        producer.send(new ProducerRecord<>(teamcenterTopic, tcSubject, tc));
        producer.flush();

        // Await the prov:Activity — written LAST by EventProjector, so this
        // guarantees the whole projection is visible before we assert.
        awaitAsk(String.format(
                "ASK { GRAPH <urn:eda:prov> { <%s> <%s> <urn:eda:adapter:teamcenter> } }",
                EdaVocab.eventIri(ceId), EdaVocab.PROV_WAS_ATTRIBUTED_TO.getURI()));
        String tcV1 = EdaVocab.versionIri(tcSubject, "1");
        assertThat(fuseki.ask(String.format(
                "ASK { GRAPH <%s> { <%s> <http://www.w3.org/ns/prov#wasGeneratedBy> <%s> } }",
                tcV1, tcSubject, EdaVocab.eventIri(ceId)))).isTrue();
    }

    @Test
    void S06_admin_rebuild_without_confirm_returns_400() {
        ResponseEntity<Map<String, Object>> resp = rest.exchange(
                "/admin/rebuild", HttpMethod.POST, null,
                new ParameterizedTypeReference<>() {});
        assertThat(resp.getStatusCode().value()).isEqualTo(400);
        assertThat(resp.getBody()).containsEntry("error",
                "pass ?confirm=true to acknowledge a destructive rebuild");
    }

    @Test
    void S06_admin_projection_age_returns_the_data_age_for_a_concept() {
        publish(change(UUID.randomUUID().toString(), 1L, 1, jsonLd("v1 payload")));
        awaitAsk(String.format(
                "ASK { GRAPH <%s> { <%s> <%s> ?t } }",
                EdaVocab.conceptIri(subject), EdaVocab.conceptIri(subject),
                EdaVocab.EDA_DATA_AGE.getURI()));

        ResponseEntity<Map<String, Object>> resp = rest.exchange(
                "/admin/projection/age?concept={c}",
                HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {},
                subject);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody())
                .containsEntry("concept", EdaVocab.conceptIri(subject))
                .containsEntry("dataAge", "2026-10-07T12:00:00Z");
    }

    @Test
    void FR_A10_health_endpoint_reports_fuseki_UP_when_reachable() {
        ResponseEntity<Map<String, Object>> resp = rest.exchange(
                "/actuator/health/fuseki", HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {});
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).containsEntry("status", "UP");
    }
}
