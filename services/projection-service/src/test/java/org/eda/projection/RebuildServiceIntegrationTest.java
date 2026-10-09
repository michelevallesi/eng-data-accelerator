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
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.sparql.util.IsoMatcher;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.eda.projection.fuseki.FusekiClient;
import org.eda.projection.rebuild.RebuildService;
import org.eda.projection.vocab.EdaVocab;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

/**
 * Covers AD-01 / FR-M03: a rebuild from scratch yields a dataset isomorphic to
 * the live one. Isomorphism beats a byte hash because RDF serialisation isn't
 * canonical (blank-node labels, triple order), but the triple set is what the
 * AC actually promises.
 */
class RebuildServiceIntegrationTest extends AbstractProjectionIntegrationTest {

    private static final String SOURCE = "urn:eda:adapter:polarion";

    @Autowired FusekiClient fuseki;
    @Autowired RebuildService rebuildService;
    @Value("${eda.kafka.topics.polarion-changes}") String polarionTopic;

    private KafkaProducer<String, CloudEvent> producer;

    @BeforeEach
    void setup() {
        // Deliberately NOT calling fuseki.clearAll() here: the AC compares the
        // live projection (which includes everything already processed by the
        // listener) with the rebuild from offset 0. Wiping Fuseki mid-stream
        // would make "before" smaller than "after".
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, CloudEventSerializer.class);
        producer = new KafkaProducer<>(props);
    }

    private CloudEvent change(String subject, String id, long trsOrder, int version) {
        String payload = """
                {"@context":{"dcterms":"http://purl.org/dc/terms/"},
                 "@id":"%s", "dcterms:description":"body v%d"}
                """.formatted(subject, version);
        return CloudEventBuilder.v1()
                .withId(id)
                .withSource(URI.create(SOURCE))
                .withType("eda.resource.changed")
                .withSubject(subject)
                .withTime(OffsetDateTime.parse("2026-10-07T12:00:00Z"))
                .withExtension("edakind", "MODIFICATION")
                .withExtension("edaversion", version)
                .withExtension("edatrsorder", trsOrder)
                .withDataContentType("application/ld+json")
                .withData(payload.getBytes(StandardCharsets.UTF_8))
                .build();
    }

    @Test
    void AD_01_FR_M03_rebuild_yields_dataset_isomorphic_to_live() {
        String subjectA = "https://eda.example.org/r/pol/" + UUID.randomUUID();
        String subjectB = "https://eda.example.org/r/pol/" + UUID.randomUUID();
        String lastCeId = UUID.randomUUID().toString();

        producer.send(new ProducerRecord<>(polarionTopic, subjectA,
                change(subjectA, UUID.randomUUID().toString(), 1L, 1)));
        producer.send(new ProducerRecord<>(polarionTopic, subjectA,
                change(subjectA, UUID.randomUUID().toString(), 2L, 2)));
        producer.send(new ProducerRecord<>(polarionTopic, subjectB,
                change(subjectB, lastCeId, 3L, 1)));
        producer.flush();

        // Await the LAST event's prov:Activity — the projector writes the activity
        // after the version graph and concept rewrite, so this guarantees the whole
        // triple set for the last event has landed before the "before" snapshot.
        awaitActivity(lastCeId);

        Dataset before = snapshot();
        RebuildService.RebuildResult result = rebuildService.rebuild();
        assertThat(result.partitionsRewound()).isPositive();

        awaitActivity(lastCeId);
        Dataset after = snapshot();

        boolean iso = IsoMatcher.isomorphic(before.asDatasetGraph(), after.asDatasetGraph());
        if (!iso) {
            dumpDifferences(before, after);
        }
        assertThat(iso)
                .as("rebuilt dataset must be isomorphic to the live one")
                .isTrue();
    }

    private static void dumpDifferences(Dataset before, Dataset after) {
        java.io.ByteArrayOutputStream bOut = new java.io.ByteArrayOutputStream();
        java.io.ByteArrayOutputStream aOut = new java.io.ByteArrayOutputStream();
        org.apache.jena.riot.RDFDataMgr.write(bOut, before, org.apache.jena.riot.Lang.NQUADS);
        org.apache.jena.riot.RDFDataMgr.write(aOut, after, org.apache.jena.riot.Lang.NQUADS);
        java.util.Set<String> beforeQ = new java.util.TreeSet<>(
                java.util.Arrays.asList(bOut.toString(StandardCharsets.UTF_8).split("\n")));
        java.util.Set<String> afterQ = new java.util.TreeSet<>(
                java.util.Arrays.asList(aOut.toString(StandardCharsets.UTF_8).split("\n")));
        java.util.Set<String> onlyBefore = new java.util.TreeSet<>(beforeQ);
        onlyBefore.removeAll(afterQ);
        java.util.Set<String> onlyAfter = new java.util.TreeSet<>(afterQ);
        onlyAfter.removeAll(beforeQ);
        System.err.println("=== Only in BEFORE (" + onlyBefore.size() + ") ===");
        onlyBefore.forEach(q -> System.err.println(q));
        System.err.println("=== Only in AFTER (" + onlyAfter.size() + ") ===");
        onlyAfter.forEach(q -> System.err.println(q));
    }

    private void awaitActivity(String ceId) {
        await().atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(250))
                .until(() -> fuseki.ask(String.format(
                        "ASK { GRAPH <urn:eda:prov> { <%s> a <%s> } }",
                        EdaVocab.eventIri(ceId), EdaVocab.PROV_ACTIVITY.getURI())));
    }

    private Dataset snapshot() {
        Dataset remote = fuseki.fetchDataset();
        Dataset copy = DatasetFactory.createTxnMem();
        copy.begin(ReadWrite.WRITE);
        try {
            copy.asDatasetGraph().addAll(remote.asDatasetGraph());
            copy.commit();
        } finally {
            copy.end();
        }
        return copy;
    }
}
