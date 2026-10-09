package org.eda.projection.projection;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.eda.projection.fuseki.InMemoryGraphStore;
import org.eda.projection.vocab.EdaVocab;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Pure-unit coverage for {@link EventProjector}. The projector is driven against
 * an {@link InMemoryGraphStore}, so no Fuseki container is required.
 */
class EventProjectorUnitTest {

    private static final String PROV_GRAPH = "urn:eda:prov";
    private static final String SUBJECT =
            "https://eda.example.org/r/pol/7d33b7bc-90e1-461c-97c1-8845d01a68d8";
    private static final String SOURCE = "urn:eda:adapter:polarion";
    private static final OffsetDateTime TIME = OffsetDateTime.parse("2026-10-07T12:00:00Z");

    private InMemoryGraphStore store;
    private EventProjector projector;

    @BeforeEach
    void setup() {
        store = new InMemoryGraphStore();
        projector = new EventProjector(store, PROV_GRAPH);
    }

    private IncomingEvent event(String id, String kind, String version, long trsOrder,
                                String payload, String sourceTime) {
        return new IncomingEvent(id, SOURCE, SUBJECT, kind, TIME, sourceTime,
                version, trsOrder,
                payload == null ? null : payload.getBytes(StandardCharsets.UTF_8));
    }

    private IncomingEvent change(String id, String version, long trsOrder, String payload) {
        return event(id, "MODIFICATION", version, trsOrder, payload, null);
    }

    private IncomingEvent creation(String id, String version, long trsOrder, String payload) {
        return event(id, "CREATION", version, trsOrder, payload, null);
    }

    private IncomingEvent deletion(String id, String version, long trsOrder) {
        return event(id, "DELETION", version, trsOrder, null, null);
    }

    private static String jsonLd(String descr) {
        return """
                {"@context":{"dcterms":"http://purl.org/dc/terms/"},
                 "@id":"%s",
                 "dcterms:description":"%s"}
                """.formatted(SUBJECT, descr);
    }

    @Test
    void FR_M01_creation_lands_in_version_graph_with_prov_wasGeneratedBy() {
        String id = UUID.randomUUID().toString();
        projector.project(creation(id, "1", 1L, jsonLd("v1 payload")));

        String versionIri = EdaVocab.versionIri(SUBJECT, "1");
        boolean inVersion = store.ask(String.format(
                "ASK { GRAPH <%s> { <%s> <%s> <%s> } }",
                versionIri, SUBJECT, EdaVocab.PROV_WAS_GENERATED_BY.getURI(),
                EdaVocab.eventIri(id)));
        assertThat(inVersion).as("subject must carry prov:wasGeneratedBy").isTrue();
    }

    @Test
    void FR_M02_every_version_subject_resolves_to_a_prov_Activity() {
        String id = UUID.randomUUID().toString();
        projector.project(creation(id, "1", 1L, jsonLd("v1 payload")));

        boolean activityExists = store.ask(String.format(
                "ASK { GRAPH <%s> { <%s> a <%s> } }",
                PROV_GRAPH, EdaVocab.eventIri(id), EdaVocab.PROV_ACTIVITY.getURI()));
        assertThat(activityExists).isTrue();
    }

    @Test
    void FR_M02_concept_rewrite_carries_prov_wasGeneratedBy_backlink() {
        // Risk-1 mitigation: concept-graph triples must be traceable to the event
        // that produced them. Each rewrite stamps the concept resource with
        // prov:wasGeneratedBy pointing at the current event.
        String id = UUID.randomUUID().toString();
        projector.project(creation(id, "1", 1L, jsonLd("v1 payload")));

        String concept = EdaVocab.conceptIri(SUBJECT);
        boolean conceptBacklink = store.ask(String.format(
                "ASK { GRAPH <%s> { <%s> <%s> <%s> } }",
                concept, concept, EdaVocab.PROV_WAS_GENERATED_BY.getURI(),
                EdaVocab.eventIri(id)));
        assertThat(conceptBacklink).isTrue();
    }

    @Test
    void FR_M05_after_second_revision_both_versions_are_queryable_only_latest_is_eda_current() {
        projector.project(creation(UUID.randomUUID().toString(), "1", 1L, jsonLd("v1")));
        projector.project(change(UUID.randomUUID().toString(), "2", 2L, jsonLd("v2")));

        String v1 = EdaVocab.versionIri(SUBJECT, "1");
        String v2 = EdaVocab.versionIri(SUBJECT, "2");
        String concept = EdaVocab.conceptIri(SUBJECT);

        assertThat(store.ask(String.format("ASK { GRAPH <%s> { ?s ?p ?o } }", v1))).isTrue();
        assertThat(store.ask(String.format("ASK { GRAPH <%s> { ?s ?p ?o } }", v2))).isTrue();
        assertThat(store.ask(String.format(
                "ASK { GRAPH <%s> { <%s> <%s> <%s> } }",
                concept, concept, EdaVocab.EDA_CURRENT.getURI(), v2))).isTrue();
        assertThat(store.ask(String.format(
                "ASK { GRAPH <%s> { <%s> <%s> <%s> } }",
                concept, concept, EdaVocab.EDA_CURRENT.getURI(), v1))).isFalse();
        assertThat(store.ask(String.format(
                "ASK { GRAPH <%s> { <%s> <%s> <%s> } }",
                concept, v2, EdaVocab.PROV_REVISION_OF.getURI(), v1))).isTrue();
    }

    @Test
    void FR_M05_three_revision_chain_links_each_version_to_its_predecessor() {
        projector.project(creation(UUID.randomUUID().toString(), "1", 1L, jsonLd("v1")));
        projector.project(change(UUID.randomUUID().toString(), "2", 2L, jsonLd("v2")));
        projector.project(change(UUID.randomUUID().toString(), "3", 3L, jsonLd("v3")));

        String v1 = EdaVocab.versionIri(SUBJECT, "1");
        String v2 = EdaVocab.versionIri(SUBJECT, "2");
        String v3 = EdaVocab.versionIri(SUBJECT, "3");
        String concept = EdaVocab.conceptIri(SUBJECT);

        assertThat(store.ask(String.format(
                "ASK { GRAPH <%s> { <%s> <%s> <%s> } }",
                concept, concept, EdaVocab.EDA_CURRENT.getURI(), v3))).isTrue();
        assertThat(store.ask(String.format(
                "ASK { GRAPH <%s> { <%s> <%s> <%s> } }",
                concept, v3, EdaVocab.PROV_REVISION_OF.getURI(), v2))).isTrue();
        assertThat(store.ask(String.format(
                "ASK { GRAPH <%s> { <%s> <%s> <%s> } }",
                concept, v2, EdaVocab.PROV_REVISION_OF.getURI(), v1))).isTrue();
    }

    @Test
    void FR_M08_eda_dataAge_matches_ce_time_when_edasourcetime_is_absent() {
        projector.project(creation(UUID.randomUUID().toString(), "1", 1L, jsonLd("v1")));
        String concept = EdaVocab.conceptIri(SUBJECT);
        boolean hasAge = store.ask(String.format(
                "ASK { GRAPH <%s> { <%s> <%s> \"2026-10-07T12:00:00Z\" } }",
                concept, concept, EdaVocab.EDA_DATA_AGE.getURI()));
        assertThat(hasAge).isTrue();
    }

    @Test
    void FR_M08_eda_dataAge_prefers_edasourcetime_over_ce_time() {
        // Risk-6 mitigation: adapter publish time is a weak proxy for data age.
        // Prefer edasourcetime (what the source tool actually recorded).
        projector.project(event(UUID.randomUUID().toString(), "CREATION", "1", 1L,
                jsonLd("v1"), "2026-09-30T08:15:30Z"));
        String concept = EdaVocab.conceptIri(SUBJECT);
        assertThat(store.ask(String.format(
                "ASK { GRAPH <%s> { <%s> <%s> \"2026-09-30T08:15:30Z\" } }",
                concept, concept, EdaVocab.EDA_DATA_AGE.getURI()))).isTrue();
    }

    @Test
    void FR_B04_duplicate_cloudEvent_id_is_a_noop() {
        String id = UUID.randomUUID().toString();
        assertThat(projector.project(creation(id, "1", 1L, jsonLd("v1")))).isTrue();
        assertThat(projector.project(creation(id, "1", 1L, jsonLd("v1 again"))))
                .as("second project() with the same ce_id must return false")
                .isFalse();
    }

    @Test
    void FR_M05_DELETION_preserves_version_payload_with_eda_deletedAt_marker() {
        // Risk-2 mitigation: historical versions must stay queryable (FR-M05).
        // DELETION keeps the payload and adds eda:deletedAt + prov:wasGeneratedBy
        // instead of wiping the version graph.
        projector.project(creation(UUID.randomUUID().toString(), "1", 1L, jsonLd("v1 payload")));
        String deletionId = UUID.randomUUID().toString();
        projector.project(deletion(deletionId, "1", 2L));

        String v1 = EdaVocab.versionIri(SUBJECT, "1");
        String concept = EdaVocab.conceptIri(SUBJECT);

        // Payload still there.
        assertThat(store.ask(String.format("ASK { GRAPH <%s> { <%s> ?p ?o } }", v1, SUBJECT)))
                .as("version payload must survive deletion for temporal queries")
                .isTrue();
        // Tombstone marker and backlink.
        assertThat(store.ask(String.format(
                "ASK { GRAPH <%s> { <%s> <%s> ?t } }",
                v1, SUBJECT, EdaVocab.EDA_DELETED_AT.getURI())))
                .as("version must carry eda:deletedAt")
                .isTrue();
        assertThat(store.ask(String.format(
                "ASK { GRAPH <%s> { <%s> <%s> <%s> } }",
                v1, SUBJECT, EdaVocab.PROV_WAS_GENERATED_BY.getURI(),
                EdaVocab.eventIri(deletionId))))
                .as("version must trace to the deletion event")
                .isTrue();
        // Concept rewrite: no current, backlink to deletion event.
        assertThat(store.ask(String.format(
                "ASK { GRAPH <%s> { <%s> <%s> ?v } }",
                concept, concept, EdaVocab.EDA_CURRENT.getURI())))
                .as("deletion must drop eda:current")
                .isFalse();
        assertThat(store.ask(String.format(
                "ASK { GRAPH <%s> { <%s> <%s> <%s> } }",
                concept, concept, EdaVocab.PROV_WAS_GENERATED_BY.getURI(),
                EdaVocab.eventIri(deletionId))))
                .as("concept rewrite must trace to the deletion event")
                .isTrue();
    }

    @Test
    void DELETION_of_never_created_subject_is_a_tombstone_without_payload() {
        // Edge case: a tombstone for a subject the projection never saw. The
        // version graph exists only as a tombstone marker; the concept graph is
        // seeded with rdf:type + wasGeneratedBy.
        String id = UUID.randomUUID().toString();
        projector.project(deletion(id, "1", 1L));

        String v1 = EdaVocab.versionIri(SUBJECT, "1");
        String concept = EdaVocab.conceptIri(SUBJECT);
        assertThat(store.ask(String.format(
                "ASK { GRAPH <%s> { <%s> <%s> ?t } }",
                v1, SUBJECT, EdaVocab.EDA_DELETED_AT.getURI()))).isTrue();
        assertThat(store.ask(String.format(
                "ASK { GRAPH <%s> { <%s> <%s> <%s> } }",
                concept, concept, EdaVocab.PROV_WAS_GENERATED_BY.getURI(),
                EdaVocab.eventIri(id)))).isTrue();
        assertThat(store.ask(String.format(
                "ASK { GRAPH <%s> { <%s> <%s> ?v } }",
                concept, concept, EdaVocab.EDA_CURRENT.getURI()))).isFalse();
    }

    @Test
    void malformed_event_without_subject_or_kind_is_logged_and_skipped() {
        IncomingEvent noSubject = new IncomingEvent(UUID.randomUUID().toString(), SOURCE,
                null, "CREATION", TIME, null, "1", 1L, jsonLd("v1").getBytes(StandardCharsets.UTF_8));
        IncomingEvent noKind = new IncomingEvent(UUID.randomUUID().toString(), SOURCE,
                SUBJECT, null, TIME, null, "1", 1L, jsonLd("v1").getBytes(StandardCharsets.UTF_8));

        assertThat(projector.project(noSubject)).isFalse();
        assertThat(projector.project(noKind)).isFalse();

        assertThat(store.ask(String.format("ASK { GRAPH <%s> { ?s a <%s> } }",
                PROV_GRAPH, EdaVocab.PROV_ACTIVITY.getURI())))
                .as("malformed events must not land in the prov graph")
                .isFalse();
    }

    @Test
    void prov_wasAttributedTo_tracks_the_event_source() {
        // A Teamcenter-sourced event must attribute its prov:Activity to the
        // Teamcenter adapter, not to a hardcoded source.
        String tcSource = "urn:eda:adapter:teamcenter";
        String tcSubject = "https://eda.example.org/r/tc/aa8c9b2d-1b2c-4e5f-9a0b-123456789abc";
        String id = UUID.randomUUID().toString();
        IncomingEvent tcEvent = new IncomingEvent(id, tcSource, tcSubject, "CREATION",
                TIME, null, "1", 1L,
                """
                {"@context":{"dcterms":"http://purl.org/dc/terms/"},
                 "@id":"%s", "dcterms:description":"tc body"}
                """.formatted(tcSubject).getBytes(StandardCharsets.UTF_8));
        projector.project(tcEvent);

        assertThat(store.ask(String.format(
                "ASK { GRAPH <%s> { <%s> <%s> <%s> } }",
                PROV_GRAPH, EdaVocab.eventIri(id),
                EdaVocab.PROV_WAS_ATTRIBUTED_TO.getURI(), tcSource))).isTrue();
    }
}
