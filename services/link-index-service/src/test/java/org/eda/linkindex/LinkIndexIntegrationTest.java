package org.eda.linkindex;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.kafka.CloudEventDeserializer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.eda.linkindex.domain.ArtefactRecord;
import org.eda.linkindex.domain.DispositionRecord;
import org.eda.linkindex.domain.LinkRecord;
import org.eda.linkindex.domain.LinkState;
import org.eda.linkindex.events.KafkaRelay;
import org.eda.linkindex.events.LinkCloudEventFactory;
import org.eda.linkindex.persistence.ArtefactRepository;
import org.eda.linkindex.persistence.DispositionRepository;
import org.eda.linkindex.persistence.LinkRepository;
import org.eda.linkindex.persistence.OutboxRepository;
import org.eda.linkindex.seed.FixtureSeeder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Integration coverage for S07. Exercises:
 *
 * <ul>
 *   <li>Fixture seeding from {@code skid400}.</li>
 *   <li>Resource-change-event-driven supersession and disposition opening (FR-S08).</li>
 *   <li>Deletion-event-driven dangling transition (FR-F06, FR-S07).</li>
 *   <li>Atomic outbox write + state update (ADR-0016).</li>
 *   <li>Replay of the outbox produces the same final link_link state.</li>
 * </ul>
 */
class LinkIndexIntegrationTest extends AbstractLinkIndexIntegrationTest {

    private static final String REQ_019_URI =
            "https://eda.example.org/r/pol/7d33b7bc-90e1-461c-97c1-8845d01a68d8";
    private static final String TC_211_URI =
            "https://eda.example.org/r/pol/b872f211-d6fb-41e0-bb78-65e5e03b168a";
    private static final String REQ_019_LINK_ID =
            "https://eda.example.org/link/134a08bb-eba5-4a11-8782-54ec9c5d57e0";

    @Autowired ArtefactRepository artefacts;
    @Autowired LinkRepository links;
    @Autowired DispositionRepository dispositions;
    @Autowired OutboxRepository outbox;
    @Autowired KafkaRelay relay;
    @Autowired FixtureSeeder seeder;
    @Autowired JdbcClient jdbc;
    @Value("${eda.kafka.topics.polarion-changes}") String polarionTopic;
    @Value("${eda.kafka.topics.link-state-changed}") String linkTopic;

    private KafkaProducer<String, CloudEvent> producer;

    @BeforeEach
    void resetState() {
        jdbc.sql("TRUNCATE link_disposition, link_event_outbox, link_link, link_artefact").update();
        seeder.seed();
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                io.cloudevents.kafka.CloudEventSerializer.class);
        producer = new KafkaProducer<>(props);
    }

    // ---- Fixture seeding ----

    @Test
    void S07_seed_from_skid400_matches_json_counts() {
        assertThat(artefacts.count()).isEqualTo(27);
        assertThat(links.count()).isEqualTo(18);

        LinkRecord req19 = links.findById(REQ_019_LINK_ID).orElseThrow();
        assertThat(req19.state()).isEqualTo(LinkState.ASSERTED);
        assertThat(req19.sourceNativeId()).isEqualTo("REQ-019");
        assertThat(req19.targetNativeId()).isEqualTo("LC-11");
        assertThat(req19.sourceVersion()).isEqualTo("1");
        assertThat(req19.targetVersion()).isEqualTo("1");
    }

    // ---- FR-S08: a new version of an endpoint supersedes the link ----

    @Test
    void FR_S08_new_version_of_REQ_019_supersedes_link_and_opens_disposition() {
        CloudEvent event = resourceChanged(REQ_019_URI, "MODIFICATION", "2", 1L);
        producer.send(new ProducerRecord<>(polarionTopic, REQ_019_URI, event));
        producer.flush();

        await().atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(200))
                .until(() -> links.findById(REQ_019_LINK_ID)
                        .map(l -> l.state() == LinkState.SUPERSEDED)
                        .orElse(false));

        LinkRecord link = links.findById(REQ_019_LINK_ID).orElseThrow();
        assertThat(link.state()).isEqualTo(LinkState.SUPERSEDED);
        assertThat(link.cause()).startsWith("resource-changed:");

        List<DispositionRecord> open = dispositions.filter(null, true);
        assertThat(open).extracting(DispositionRecord::linkId).contains(REQ_019_LINK_ID);
        DispositionRecord d = open.stream()
                .filter(x -> x.linkId().equals(REQ_019_LINK_ID))
                .findFirst().orElseThrow();
        assertThat(d.owner()).isEqualTo("requirements-engineer");
        assertThat(d.previousVersion()).isEqualTo("1");
        assertThat(d.newVersion()).isEqualTo("2");

        ArtefactRecord req19 = artefacts.findByUri(REQ_019_URI).orElseThrow();
        assertThat(req19.version()).isEqualTo("2");
    }

    // ---- FR-F06 / FR-S07: deletion → dangling, link kept ----

    @Test
    void FR_S07_deletion_of_TC_211_makes_its_links_dangling() {
        long linksBefore = links.count();
        CloudEvent tombstone = tombstone(TC_211_URI, "DELETION", "1", 2L);
        producer.send(new ProducerRecord<>(polarionTopic, TC_211_URI, tombstone));
        producer.flush();

        await().atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(200))
                .until(() -> links.all().stream()
                        .filter(l -> l.state() == LinkState.DANGLING)
                        .count() >= 1);

        assertThat(links.count()).as("links are never silently deleted (FR-F06)")
                .isEqualTo(linksBefore);
        List<LinkRecord> dangling = links.all().stream()
                .filter(l -> l.state() == LinkState.DANGLING).toList();
        assertThat(dangling)
                .allMatch(l -> l.sourceUri().equals(TC_211_URI) || l.targetUri().equals(TC_211_URI))
                .allMatch(l -> l.cause() != null && l.cause().startsWith("resource-deleted:"));
    }

    // ---- ADR-0016: outbox write and state update share one transaction ----

    @Test
    void ADR_0016_every_state_transition_lands_on_outbox_in_one_transaction() {
        CloudEvent event = resourceChanged(REQ_019_URI, "MODIFICATION", "2", 1L);
        producer.send(new ProducerRecord<>(polarionTopic, REQ_019_URI, event));
        producer.flush();

        await().atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(200))
                .until(() -> outbox.countPending() >= 1);

        assertThat(outbox.countPending())
                .as("transition for REQ-019 link should appear in the outbox")
                .isGreaterThanOrEqualTo(1);

        int sent = relay.relayOnce();
        assertThat(sent).isPositive();
        assertThat(outbox.countPending()).isZero();

        List<ConsumerRecord<String, CloudEvent>> published = drain(linkTopic);
        assertThat(published).isNotEmpty();
        assertThat(published)
                .anySatisfy(rec -> {
                    assertThat(headerString(rec, "ce_type"))
                            .isEqualTo(LinkCloudEventFactory.TYPE);
                    assertThat(headerString(rec, "ce_subject"))
                            .isEqualTo(REQ_019_LINK_ID);
                    assertThat(headerString(rec,
                            "ce_" + LinkCloudEventFactory.EXT_NEW_STATE))
                            .isEqualTo("superseded");
                });
    }

    // ---- Rebuild from outbox ----

    @Test
    void S07_outbox_replay_reproduces_the_live_link_state() {
        // Drive a supersession.
        CloudEvent event = resourceChanged(REQ_019_URI, "MODIFICATION", "2", 1L);
        producer.send(new ProducerRecord<>(polarionTopic, REQ_019_URI, event));
        producer.flush();

        await().atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(200))
                .until(() -> links.findById(REQ_019_LINK_ID)
                        .map(l -> l.state() == LinkState.SUPERSEDED)
                        .orElse(false));
        relay.relayOnce();

        // Snapshot live state.
        LinkRecord live = links.findById(REQ_019_LINK_ID).orElseThrow();

        // Wipe everything and replay the link-state-changed events to rebuild.
        jdbc.sql("TRUNCATE link_disposition, link_event_outbox, link_link, link_artefact").update();
        seeder.seed();

        List<ConsumerRecord<String, CloudEvent>> stateEvents = drain(linkTopic);
        for (ConsumerRecord<String, CloudEvent> rec : stateEvents) {
            if (!LinkCloudEventFactory.TYPE.equals(headerString(rec, "ce_type"))) continue;
            if (!REQ_019_LINK_ID.equals(headerString(rec, "ce_subject"))) continue;
            String newState = headerString(rec, "ce_" + LinkCloudEventFactory.EXT_NEW_STATE);
            String cause = headerString(rec, "ce_" + LinkCloudEventFactory.EXT_CAUSE);
            String newSourceV = headerString(rec,
                    "ce_" + LinkCloudEventFactory.EXT_NEW_SOURCE_VERSION);
            String newTargetV = headerString(rec,
                    "ce_" + LinkCloudEventFactory.EXT_NEW_TARGET_VERSION);
            LinkRecord current = links.findById(REQ_019_LINK_ID).orElseThrow();
            LinkRecord replayed = new LinkRecord(current.id(), current.relation(),
                    current.sourceNativeId(), current.targetNativeId(),
                    current.sourceUri(), current.targetUri(),
                    newSourceV == null ? current.sourceVersion() : newSourceV,
                    newTargetV == null ? current.targetVersion() : newTargetV,
                    LinkState.fromWire(newState), cause, null, OffsetDateTime.now());
            links.update(replayed);
        }
        LinkRecord replayed = links.findById(REQ_019_LINK_ID).orElseThrow();
        assertThat(replayed.state()).isEqualTo(live.state());
        assertThat(replayed.sourceVersion()).isEqualTo(live.sourceVersion());
        assertThat(replayed.targetVersion()).isEqualTo(live.targetVersion());
    }

    // ---- helpers ----

    private CloudEvent resourceChanged(String subject, String kind, String version, long trsOrder) {
        String payload = """
                {"@context":{"dcterms":"http://purl.org/dc/terms/"},
                 "@id":"%s", "dcterms:description":"rev %s"}
                """.formatted(subject, version);
        return CloudEventBuilder.v1()
                .withId(UUID.randomUUID().toString())
                .withSource(URI.create("urn:eda:adapter:polarion"))
                .withType("eda.resource.changed")
                .withSubject(subject)
                .withTime(OffsetDateTime.now())
                .withExtension("edakind", kind)
                .withExtension("edaversion", version)
                .withExtension("edatrsorder", trsOrder)
                .withDataContentType("application/ld+json")
                .withData(payload.getBytes(StandardCharsets.UTF_8))
                .build();
    }

    private CloudEvent tombstone(String subject, String kind, String version, long trsOrder) {
        return CloudEventBuilder.v1()
                .withId(UUID.randomUUID().toString())
                .withSource(URI.create("urn:eda:adapter:polarion"))
                .withType("eda.resource.changed")
                .withSubject(subject)
                .withTime(OffsetDateTime.now())
                .withExtension("edakind", kind)
                .withExtension("edaversion", version)
                .withExtension("edatrsorder", trsOrder)
                .build();
    }

    /**
     * link.state-changed events carry everything in headers (no body), so
     * CloudEventDeserializer returns null — read raw ConsumerRecords and
     * expose a header view.
     */
    private List<ConsumerRecord<String, CloudEvent>> drain(String topic) {
        List<ConsumerRecord<String, CloudEvent>> out = new ArrayList<>();
        Properties p = new Properties();
        p.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        p.put(ConsumerConfig.GROUP_ID_CONFIG, "drain-" + System.nanoTime());
        p.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        p.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        p.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, CloudEventDeserializer.class);
        try (KafkaConsumer<String, CloudEvent> c = new KafkaConsumer<>(p)) {
            TopicPartition tp = new TopicPartition(topic, 0);
            c.assign(List.of(tp));
            c.seekToBeginning(List.of(tp));
            long deadline = System.currentTimeMillis() + 8_000;
            while (System.currentTimeMillis() < deadline) {
                ConsumerRecords<String, CloudEvent> records = c.poll(Duration.ofMillis(500));
                records.forEach(out::add);
                if (!records.isEmpty()) {
                    deadline = Math.min(deadline, System.currentTimeMillis() + 1500);
                }
            }
        }
        return out;
    }

    private static String headerString(ConsumerRecord<?, ?> rec, String name) {
        var h = rec.headers().lastHeader(name);
        return h == null ? null : new String(h.value(), StandardCharsets.UTF_8);
    }

    private static String valueOf(Object ext) {
        return ext == null ? null : ext.toString();
    }
}
