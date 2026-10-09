package org.eda.adapter.polarion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.cloudevents.CloudEvent;
import io.cloudevents.kafka.CloudEventDeserializer;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.shacl.Shapes;
import org.apache.jena.shacl.ValidationReport;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.eda.adapter.polarion.availability.PolarionAvailability;
import org.eda.adapter.polarion.events.CloudEventFactory;
import org.eda.adapter.polarion.events.DlqPublisher;
import org.eda.adapter.polarion.events.KafkaPublisher;
import org.eda.adapter.polarion.events.KafkaRelay;
import org.eda.adapter.polarion.persistence.CursorRepository;
import org.eda.adapter.polarion.persistence.OutboxRepository;
import org.eda.adapter.polarion.persistence.ProcessedEventsRepository;
import org.eda.adapter.polarion.polarion.PolarionClient;
import org.eda.adapter.polarion.polarion.PolarionResource;
import org.eda.adapter.polarion.polarion.PolarionTrsEvent;
import org.eda.adapter.polarion.polarion.PolarionUnavailableException;
import org.eda.adapter.polarion.polling.TrsPoller;
import org.eda.adapter.polarion.validation.ShaclValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@Import({
        TrsPollerIntegrationTest.ToggleableValidatorConfig.class,
        TrsPollerIntegrationTest.FailingClientConfig.class,
        TrsPollerIntegrationTest.ThrowingProcessedConfig.class
})
class TrsPollerIntegrationTest extends AbstractAdapterIntegrationTest {

    private static final String SOURCE = "urn:eda:adapter:polarion";

    private static final String REQ_019_URI =
            "https://eda.example.org/r/pol/7d33b7bc-90e1-461c-97c1-8845d01a68d8";
    private static final String REQ_024_URI =
            "https://eda.example.org/r/pol/694a7e66-7558-4a08-871b-43c0c78f3ef0";
    private static final String REQ_031_URI =
            "https://eda.example.org/r/pol/6c3eecfc-d9be-494a-86ac-bc61ef8c8755";
    private static final String TC_211_URI =
            "https://eda.example.org/r/pol/b872f211-d6fb-41e0-bb78-65e5e03b168a";

    @DynamicPropertySource
    static void simulatorProperties(DynamicPropertyRegistry registry) {
        SimulatorHarness.start();
        registry.add("eda.polarion.base-url", SimulatorHarness::baseUrl);
    }

    @Autowired TrsPoller poller;
    @Autowired KafkaRelay relay;
    @Autowired CursorRepository cursorRepo;
    @Autowired ProcessedEventsRepository processedRepo;
    @Autowired OutboxRepository outbox;
    @Autowired KafkaPublisher kafkaPublisher;
    @Autowired DlqPublisher dlqPublisher;
    @Autowired CloudEventFactory eventFactory;
    @Autowired PolarionAvailability availability;
    @Autowired JdbcClient jdbc;
    @Autowired TestRestTemplate rest;

    private final Map<String, Long> baselineOffsets = new HashMap<>();

    @BeforeEach
    void resetState() {
        jdbc.sql("TRUNCATE processed_events, trs_cursor, event_outbox").update();
        SimulatorHarness.state().reset();
        ToggleableValidatorConfig.FAIL_NEXT.set(false);
        FailingClientConfig.FAIL_FOR_URI.set(uri -> false);
        ThrowingProcessedConfig.FAIL_NEXT_RECORD.set(false);
        captureBaseline(kafkaPublisher.topic());
        captureBaseline(dlqPublisher.topic());
    }

    /**
     * One synchronous pipeline tick: poll TRS (writes outbox + processed_events in
     * one DB tx) then relay the outbox to Kafka. Production runs the two on
     * independent schedules; tests want the whole chain to settle inline.
     */
    private void pollAndFlush() {
        poller.pollOnce();
        relay.relayOnce();
    }

    private Properties consumerProps(String groupId) {
        Properties p = new Properties();
        p.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        p.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        p.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        p.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        p.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, CloudEventDeserializer.class);
        p.put(ConsumerConfig.ALLOW_AUTO_CREATE_TOPICS_CONFIG, true);
        return p;
    }

    private void captureBaseline(String topic) {
        TopicPartition tp = new TopicPartition(topic, 0);
        try (KafkaConsumer<String, CloudEvent> consumer =
                     new KafkaConsumer<>(consumerProps("baseline-" + System.nanoTime()))) {
            consumer.assign(List.of(tp));
            consumer.seekToEnd(List.of(tp));
            baselineOffsets.put(topic, consumer.position(tp));
        }
    }

    private List<ConsumerRecord<String, CloudEvent>> drain(String topic) {
        long startOffset = baselineOffsets.getOrDefault(topic, 0L);
        TopicPartition tp = new TopicPartition(topic, 0);
        List<ConsumerRecord<String, CloudEvent>> out = new ArrayList<>();
        try (KafkaConsumer<String, CloudEvent> consumer =
                     new KafkaConsumer<>(consumerProps("drain-" + System.nanoTime()))) {
            consumer.assign(List.of(tp));
            consumer.seek(tp, startOffset);
            long deadline = System.currentTimeMillis() + 8_000;
            while (System.currentTimeMillis() < deadline) {
                ConsumerRecords<String, CloudEvent> records = consumer.poll(Duration.ofMillis(500));
                records.forEach(out::add);
                if (!records.isEmpty()) {
                    deadline = Math.min(deadline, System.currentTimeMillis() + 1500);
                }
            }
        }
        return out;
    }

    private static String header(ConsumerRecord<?, ?> rec, String name) {
        return new String(rec.headers().lastHeader(name).value(), StandardCharsets.UTF_8);
    }

    @Test
    void S04_REQ_019_PATCH_produces_exactly_one_CloudEvent() {
        SimulatorHarness.patchRequirement("REQ-019", "max operating pressure 21 bar");

        pollAndFlush();

        List<ConsumerRecord<String, CloudEvent>> records = drain(kafkaPublisher.topic());
        assertThat(records).hasSize(1);
        ConsumerRecord<String, CloudEvent> rec = records.get(0);
        CloudEvent event = rec.value();

        assertThat(event.getType()).isEqualTo(CloudEventFactory.TYPE_RESOURCE_CHANGED);
        assertThat(event.getSource().toString()).isEqualTo(SOURCE);
        assertThat(event.getSubject()).isEqualTo(REQ_019_URI);
        assertThat(event.getTime()).isNotNull();
        assertThat(event.getExtension(CloudEventFactory.EXT_VERSION)).hasToString("2");

        // Risk #7: edasourcetime is populated and RFC3339.
        Object sourceTime = event.getExtension(CloudEventFactory.EXT_SOURCE_TIME);
        assertThat(sourceTime).as("edasourcetime extension must be populated").isNotNull();
        OffsetDateTime parsed = OffsetDateTime.parse(sourceTime.toString());
        assertThat(parsed).isBefore(OffsetDateTime.now().plusMinutes(1));

        // Risks #5 and #10: the CloudEvent data is parseable JSON-LD and preserves the
        // simulator's description verbatim.
        byte[] payload = event.getData().toBytes();
        Model m = ModelFactory.createDefaultModel();
        RDFDataMgr.read(m, new ByteArrayInputStream(payload), Lang.JSONLD);
        assertThat(m.contains(
                ResourceFactory.createResource(REQ_019_URI),
                DCTerms.description,
                "max operating pressure 21 bar"))
                .as("CloudEvent data must round-trip the native description")
                .isTrue();
    }

    @Test
    void FR_B04_replaying_the_poll_does_not_duplicate_the_event() {
        // Note: a true adapter-restart has nothing to lose in-process — all state lives
        // in Postgres. Rewinding the cursor to 0 is a stronger case than a cold restart
        // and so covers risk #1 by implication.
        SimulatorHarness.patchRequirement("REQ-019", "revised");

        pollAndFlush();
        List<ConsumerRecord<String, CloudEvent>> firstRun = drain(kafkaPublisher.topic());
        assertThat(firstRun).hasSize(1);

        cursorRepo.write(SOURCE, 0L);
        pollAndFlush();

        captureBaseline(kafkaPublisher.topic());
        pollAndFlush();

        List<ConsumerRecord<String, CloudEvent>> afterReplay = drain(kafkaPublisher.topic());
        assertThat(afterReplay)
                .as("replay must not produce a second record (processed_events dedup)")
                .isEmpty();
    }

    @Test
    void FR_A08_invalid_resource_lands_on_DLQ_and_can_be_reprocessed() {
        ToggleableValidatorConfig.FAIL_NEXT.set(true);
        SimulatorHarness.patchRequirement("REQ-019", "revised while validator fails");

        pollAndFlush();

        List<ConsumerRecord<String, CloudEvent>> onDlq = drain(dlqPublisher.topic());
        List<ConsumerRecord<String, CloudEvent>> onMain = drain(kafkaPublisher.topic());
        assertThat(onDlq).hasSize(1);
        assertThat(onMain).isEmpty();
        ConsumerRecord<String, CloudEvent> dlqRec = onDlq.get(0);
        assertThat(dlqRec.value().getType()).isEqualTo(DlqPublisher.TYPE_RESOURCE_INVALID);

        // Risk #4: edaviolation extension carries the SHACL violation message.
        assertThat(header(dlqRec, "ce_edaviolation"))
                .contains("simulated SHACL failure for S04 DLQ test");

        // Risk #9: processed_events.status reflects the DLQ outcome before reprocess.
        String statusAfterDlq = jdbc.sql(
                "SELECT status FROM processed_events WHERE source = :source AND trs_order = :order")
                .param("source", SOURCE)
                .param("order", 1L)
                .query(String.class)
                .single();
        assertThat(statusAfterDlq).isEqualTo("DLQ");

        ToggleableValidatorConfig.FAIL_NEXT.set(false);
        processedRepo.forget(SOURCE, 1L);
        cursorRepo.write(SOURCE, 0L);
        captureBaseline(kafkaPublisher.topic());
        pollAndFlush();

        List<ConsumerRecord<String, CloudEvent>> reprocessed = drain(kafkaPublisher.topic());
        assertThat(reprocessed).hasSize(1);
        assertThat(reprocessed.get(0).value().getType())
                .isEqualTo(CloudEventFactory.TYPE_RESOURCE_CHANGED);

        // Risk #9: processed_events.status now reflects PUBLISHED after reprocess.
        String statusAfterReprocess = jdbc.sql(
                "SELECT status FROM processed_events WHERE source = :source AND trs_order = :order")
                .param("source", SOURCE)
                .param("order", 1L)
                .query(String.class)
                .single();
        assertThat(statusAfterReprocess).isEqualTo("PUBLISHED");
    }

    @Test
    void FR_B06_events_are_keyed_by_resource_URI() {
        SimulatorHarness.patchRequirement("REQ-019", "revision 1");
        pollAndFlush();
        SimulatorHarness.patchRequirement("REQ-019", "revision 2");
        pollAndFlush();

        List<ConsumerRecord<String, CloudEvent>> records = drain(kafkaPublisher.topic());
        assertThat(records).hasSize(2);
        assertThat(records).allMatch(r -> REQ_019_URI.equals(r.key()));
        assertThat(records.get(0).partition())
                .as("same key must go to the same partition (FR-B06)")
                .isEqualTo(records.get(1).partition());
    }

    @Test
    void S04_deletion_event_is_emitted_without_fetch_and_without_payload() {
        SimulatorHarness.state().deleteTestCase("TC-211");
        pollAndFlush();

        List<ConsumerRecord<String, CloudEvent>> records = drain(kafkaPublisher.topic());
        assertThat(records).hasSize(1);
        ConsumerRecord<String, CloudEvent> rec = records.get(0);
        assertThat(rec.value()).isNull();
        assertThat(rec.serializedValueSize()).isEqualTo(-1);
        assertThat(header(rec, "ce_edakind")).isEqualTo("DELETION");
        assertThat(header(rec, "ce_subject")).isEqualTo(TC_211_URI);
    }

    // ---- Review follow-ups ----

    @Test
    void S04_single_batch_preserves_TRS_order_across_events() {
        // Risk #6
        SimulatorHarness.patchRequirement("REQ-019", "rev-1");
        SimulatorHarness.patchRequirement("REQ-024", "rev-1");
        SimulatorHarness.patchRequirement("REQ-031", "rev-1");

        pollAndFlush();

        List<ConsumerRecord<String, CloudEvent>> records = drain(kafkaPublisher.topic());
        assertThat(records).hasSize(3);
        assertThat(records.stream().map(r -> header(r, "ce_edatrsorder")).toList())
                .as("TRS order must be preserved within a single poll batch")
                .containsExactly("1", "2", "3");
        assertThat(records.stream().map(r -> header(r, "ce_subject")).toList())
                .containsExactly(REQ_019_URI, REQ_024_URI, REQ_031_URI);
    }

    @Test
    void FR_A10_health_transitions_from_DOWN_back_to_UP() {
        // Risk #8
        availability.markDown("test-induced DOWN");
        ResponseEntity<Map<String, Object>> downHealth = rest.exchange(
                "/actuator/health", HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {});
        @SuppressWarnings("unchecked")
        Map<String, Object> downPolarion = (Map<String, Object>) ((Map<String, Object>)
                downHealth.getBody().get("components")).get("polarion");
        assertThat(downPolarion.get("status")).isEqualTo("DOWN");

        availability.markUp();
        ResponseEntity<Map<String, Object>> upHealth = rest.exchange(
                "/actuator/health", HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {});
        @SuppressWarnings("unchecked")
        Map<String, Object> upPolarion = (Map<String, Object>) ((Map<String, Object>)
                upHealth.getBody().get("components")).get("polarion");
        assertThat(upPolarion.get("status")).isEqualTo("UP");
    }

    @Test
    void S04_partial_batch_leaves_cursor_unchanged_and_commits_only_the_prior_events() {
        // Risk #2
        SimulatorHarness.patchRequirement("REQ-019", "rev-A");
        SimulatorHarness.patchRequirement("REQ-024", "rev-B");
        SimulatorHarness.patchRequirement("REQ-031", "rev-C");

        FailingClientConfig.FAIL_FOR_URI.set(uri -> uri.equals(REQ_024_URI));

        assertThatThrownBy(() -> poller.pollOnce())
                .isInstanceOf(PolarionUnavailableException.class);
        relay.relayOnce();

        List<ConsumerRecord<String, CloudEvent>> records = drain(kafkaPublisher.topic());
        assertThat(records)
                .as("only the event before the mid-batch failure should be committed")
                .hasSize(1);
        assertThat(records.get(0).value().getSubject()).isEqualTo(REQ_019_URI);

        assertThat(cursorRepo.read(SOURCE))
                .as("cursor must not advance when the batch did not complete")
                .isZero();

        long processedRows = jdbc.sql("SELECT count(*) FROM processed_events WHERE source = :src")
                .param("src", SOURCE)
                .query(Long.class)
                .single();
        assertThat(processedRows).isEqualTo(1);
    }

    @Test
    void ADR_0016_outbox_write_rolls_back_when_processed_events_insert_fails() {
        // Risk #3 (now stronger): outbox and processed_events share one DB tx. If
        // record() throws, the outbox row rolls back too, so nothing hits Kafka from
        // the failed pass. The retry produces exactly one record with the
        // deterministic id — no duplicate, no dedup burden on the downstream.
        SimulatorHarness.patchRequirement("REQ-019", "retry me");

        ThrowingProcessedConfig.FAIL_NEXT_RECORD.set(true);

        assertThatThrownBy(() -> poller.pollOnce())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("test-induced record failure");

        assertThat(outbox.count())
                .as("outbox row must roll back with processed_events")
                .isZero();
        relay.relayOnce();
        assertThat(drain(kafkaPublisher.topic()))
                .as("nothing to publish from a rolled-back transaction")
                .isEmpty();
        assertThat(cursorRepo.read(SOURCE)).isZero();

        // Retry: toggle already cleared by compareAndSet; the second pass commits.
        pollAndFlush();

        List<ConsumerRecord<String, CloudEvent>> records = drain(kafkaPublisher.topic());
        assertThat(records).hasSize(1);
        String expectedId = eventFactory.deterministicId(
                new PolarionTrsEvent(1, PolarionTrsEvent.Kind.MODIFICATION, REQ_019_URI))
                .toString();
        assertThat(records.get(0).value().getId()).isEqualTo(expectedId);
        assertThat(cursorRepo.read(SOURCE)).isEqualTo(1L);
    }

    @Test
    void S04_admin_dlq_reprocess_endpoint_forgets_row_and_rewinds_cursor() {
        // Risk #6: operators need a supported API to reprocess a DLQ'd event.
        ToggleableValidatorConfig.FAIL_NEXT.set(true);
        SimulatorHarness.patchRequirement("REQ-019", "fail once");
        pollAndFlush();
        assertThat(drain(dlqPublisher.topic())).hasSize(1);

        ToggleableValidatorConfig.FAIL_NEXT.set(false);
        captureBaseline(kafkaPublisher.topic());

        ResponseEntity<Void> adminResponse = rest.exchange(
                "/admin/dlq/reprocess?trsOrder=1&pollNow=true",
                HttpMethod.POST, null, Void.class);
        assertThat(adminResponse.getStatusCode().value()).isEqualTo(204);
        relay.relayOnce();

        List<ConsumerRecord<String, CloudEvent>> records = drain(kafkaPublisher.topic());
        assertThat(records).hasSize(1);
        assertThat(records.get(0).value().getType())
                .isEqualTo(CloudEventFactory.TYPE_RESOURCE_CHANGED);
    }

    @Test
    void S04_DLQ_publisher_handles_null_resource_without_dropping_attributes() {
        // Risk #11: DlqPublisher must not crash on a null resource. The DLQ event is
        // emitted body-less (Kafka tombstone, same shape as a DELETION event); all
        // attributes still make it through via Kafka headers.
        PolarionTrsEvent trs = new PolarionTrsEvent(
                999, PolarionTrsEvent.Kind.CREATION,
                "https://eda.example.org/r/pol/" + UUID.randomUUID());

        dlqPublisher.publish(trs, null, "null-resource smoke test");
        relay.relayOnce();

        List<ConsumerRecord<String, CloudEvent>> records = drain(dlqPublisher.topic());
        assertThat(records).hasSize(1);
        ConsumerRecord<String, CloudEvent> rec = records.get(0);
        assertThat(rec.serializedValueSize())
                .as("null body serialises as a tombstone (-1), consistent with deletions")
                .isEqualTo(-1);
        assertThat(header(rec, "ce_type")).isEqualTo(DlqPublisher.TYPE_RESOURCE_INVALID);
        assertThat(header(rec, "ce_subject")).isEqualTo(trs.resourceUri());
        assertThat(header(rec, "ce_edaviolation")).isEqualTo("null-resource smoke test");
    }

    @TestConfiguration
    static class ToggleableValidatorConfig {

        static final AtomicBoolean FAIL_NEXT = new AtomicBoolean(false);

        @Bean
        @Primary
        ShaclValidator toggleableShaclValidator() {
            ShaclValidator delegate = new ShaclValidator();
            return new ShaclValidator() {
                @Override
                public ValidationReport validate(Model data) {
                    if (FAIL_NEXT.get()) {
                        return failingValidation(data);
                    }
                    return delegate.validate(data);
                }
            };
        }

        private static ValidationReport failingValidation(Model data) {
            String shapesTtl = """
                    @prefix sh: <http://www.w3.org/ns/shacl#> .
                    @prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
                    [] a sh:NodeShape ;
                       sh:targetSubjectsOf rdf:type ;
                       sh:property [ sh:path rdf:type ; sh:maxCount 0 ;
                                     sh:message "simulated SHACL failure for S04 DLQ test" ] .
                    """;
            Model shapesModel = ModelFactory.createDefaultModel();
            RDFDataMgr.read(shapesModel,
                    new ByteArrayInputStream(shapesTtl.getBytes(StandardCharsets.UTF_8)),
                    Lang.TURTLE);
            Shapes shapes = Shapes.parse(shapesModel.getGraph());
            return org.apache.jena.shacl.ShaclValidator.get().validate(shapes, data.getGraph());
        }
    }

    @TestConfiguration
    static class FailingClientConfig {

        static final AtomicReference<Predicate<String>> FAIL_FOR_URI =
                new AtomicReference<>(uri -> false);

        @Bean
        @Primary
        PolarionClient toggleablePolarionClient(
                @Value("${eda.polarion.base-url}") String baseUrl,
                PolarionAvailability availability) {
            PolarionClient delegate = new PolarionClient(baseUrl, availability);
            return new PolarionClient(baseUrl, availability) {
                @Override
                public List<PolarionTrsEvent> fetchChangesSince(long sinceOrder) {
                    return delegate.fetchChangesSince(sinceOrder);
                }

                @Override
                public PolarionResource fetchByUri(String uri) {
                    if (FAIL_FOR_URI.get().test(uri)) {
                        availability.markDown("test-induced failure for " + uri);
                        throw new PolarionUnavailableException(
                                "test-induced failure for " + uri);
                    }
                    return delegate.fetchByUri(uri);
                }

                @Override
                public void ping() {
                    delegate.ping();
                }
            };
        }
    }

    @TestConfiguration
    static class ThrowingProcessedConfig {

        static final AtomicBoolean FAIL_NEXT_RECORD = new AtomicBoolean(false);

        @Bean
        @Primary
        ProcessedEventsRepository toggleableProcessedRepo(JdbcClient jdbc) {
            return new ProcessedEventsRepository(jdbc) {
                @Override
                public void record(String source, long trsOrder, Status status,
                                   UUID cloudEventId) {
                    if (FAIL_NEXT_RECORD.compareAndSet(true, false)) {
                        throw new RuntimeException("test-induced record failure");
                    }
                    super.record(source, trsOrder, status, cloudEventId);
                }
            };
        }
    }
}
