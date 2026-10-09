package org.eda.adapter.teamcenter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.cloudevents.CloudEvent;
import io.cloudevents.kafka.CloudEventDeserializer;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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
import org.eda.adapter.teamcenter.availability.TeamcenterAvailability;
import org.eda.adapter.teamcenter.events.CloudEventFactory;
import org.eda.adapter.teamcenter.events.DlqPublisher;
import org.eda.adapter.teamcenter.events.KafkaPublisher;
import org.eda.adapter.teamcenter.events.KafkaRelay;
import org.eda.adapter.teamcenter.persistence.CursorRepository;
import org.eda.adapter.teamcenter.persistence.OutboxRepository;
import org.eda.adapter.teamcenter.persistence.ProcessedEventsRepository;
import org.eda.adapter.teamcenter.polling.TrsPoller;
import org.eda.adapter.teamcenter.teamcenter.TeamcenterClient;
import org.eda.adapter.teamcenter.teamcenter.TeamcenterResource;
import org.eda.adapter.teamcenter.teamcenter.TeamcenterTrsEvent;
import org.eda.adapter.teamcenter.teamcenter.TeamcenterUnavailableException;
import org.eda.adapter.teamcenter.validation.ShaclValidator;
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

    private static final String SOURCE = "urn:eda:adapter:teamcenter";

    @DynamicPropertySource
    static void simulatorProperties(DynamicPropertyRegistry registry) {
        SimulatorHarness.start();
        registry.add("eda.teamcenter.base-url", SimulatorHarness::baseUrl);
    }

    @Autowired TrsPoller poller;
    @Autowired KafkaRelay relay;
    @Autowired CursorRepository cursorRepo;
    @Autowired ProcessedEventsRepository processedRepo;
    @Autowired OutboxRepository outbox;
    @Autowired KafkaPublisher kafkaPublisher;
    @Autowired DlqPublisher dlqPublisher;
    @Autowired CloudEventFactory eventFactory;
    @Autowired TeamcenterAvailability availability;
    @Autowired JdbcClient jdbc;
    @Autowired TestRestTemplate rest;

    private final Map<String, Long> baselineOffsets = new HashMap<>();

    @BeforeEach
    void resetState() {
        jdbc.sql("TRUNCATE tc_processed_events, tc_trs_cursor, tc_event_outbox").update();
        SimulatorHarness.state().reset();
        ToggleableValidatorConfig.FAIL_NEXT.set(false);
        FailingClientConfig.FAIL_FOR_URI.set(uri -> false);
        ThrowingProcessedConfig.FAIL_NEXT_RECORD.set(false);
        captureBaseline(kafkaPublisher.topic());
        captureBaseline(dlqPublisher.topic());
    }

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
    void S05_revising_400_1105_C_emits_one_CloudEvent_with_the_new_version_URI() {
        SimulatorHarness.ReviseResult revised =
                SimulatorHarness.revise("400-1105", "C", "D", "new revision D");

        pollAndFlush();

        List<ConsumerRecord<String, CloudEvent>> records = drain(kafkaPublisher.topic());
        assertThat(records).hasSize(1);
        CloudEvent event = records.get(0).value();

        assertThat(event.getType()).isEqualTo(CloudEventFactory.TYPE_RESOURCE_CHANGED);
        assertThat(event.getSource().toString()).isEqualTo(SOURCE);
        assertThat(event.getSubject())
                .as("CloudEvent subject must be the newly-minted URI")
                .isEqualTo(revised.uri());
        assertThat(event.getExtension(CloudEventFactory.EXT_KIND)).isEqualTo("CREATION");

        byte[] payload = event.getData().toBytes();
        Model m = ModelFactory.createDefaultModel();
        RDFDataMgr.read(m, new ByteArrayInputStream(payload), Lang.JSONLD);
        assertThat(m.contains(
                ResourceFactory.createResource(revised.uri()),
                DCTerms.identifier,
                "400-1105/D")).isTrue();
    }

    @Test
    void FR_B04_replaying_the_poll_does_not_duplicate_the_event() {
        SimulatorHarness.revise("400-1105", "C", "D", "revised");

        pollAndFlush();
        assertThat(drain(kafkaPublisher.topic())).hasSize(1);

        cursorRepo.write(SOURCE, 0L);
        pollAndFlush();

        captureBaseline(kafkaPublisher.topic());
        pollAndFlush();

        assertThat(drain(kafkaPublisher.topic())).isEmpty();
    }

    @Test
    void FR_A08_invalid_resource_lands_on_DLQ_and_can_be_reprocessed() {
        ToggleableValidatorConfig.FAIL_NEXT.set(true);
        SimulatorHarness.revise("400-1105", "C", "D", "bad");

        pollAndFlush();

        List<ConsumerRecord<String, CloudEvent>> onDlq = drain(dlqPublisher.topic());
        List<ConsumerRecord<String, CloudEvent>> onMain = drain(kafkaPublisher.topic());
        assertThat(onDlq).hasSize(1);
        assertThat(onMain).isEmpty();
        assertThat(onDlq.get(0).value().getType()).isEqualTo(DlqPublisher.TYPE_RESOURCE_INVALID);
        assertThat(header(onDlq.get(0), "ce_edaviolation"))
                .contains("simulated SHACL failure for S05 DLQ test");

        String statusAfterDlq = jdbc.sql(
                "SELECT status FROM tc_processed_events WHERE source = :source AND trs_order = :order")
                .param("source", SOURCE)
                .param("order", 1L)
                .query(String.class)
                .single();
        assertThat(statusAfterDlq).isEqualTo("DLQ");

        ToggleableValidatorConfig.FAIL_NEXT.set(false);
        captureBaseline(kafkaPublisher.topic());

        ResponseEntity<Void> adminResponse = rest.exchange(
                "/admin/dlq/reprocess?trsOrder=1&pollNow=true",
                HttpMethod.POST, null, Void.class);
        assertThat(adminResponse.getStatusCode().value()).isEqualTo(204);
        relay.relayOnce();

        List<ConsumerRecord<String, CloudEvent>> reprocessed = drain(kafkaPublisher.topic());
        assertThat(reprocessed).hasSize(1);
        assertThat(reprocessed.get(0).value().getType())
                .isEqualTo(CloudEventFactory.TYPE_RESOURCE_CHANGED);
    }

    @Test
    void S05_deletion_event_is_emitted_without_fetch_and_without_payload() {
        SimulatorHarness.state().deleteItem("400-1180/A");
        pollAndFlush();

        List<ConsumerRecord<String, CloudEvent>> records = drain(kafkaPublisher.topic());
        assertThat(records).hasSize(1);
        ConsumerRecord<String, CloudEvent> rec = records.get(0);
        assertThat(rec.value()).isNull();
        assertThat(rec.serializedValueSize()).isEqualTo(-1);
        assertThat(header(rec, "ce_edakind")).isEqualTo("DELETION");
    }

    @Test
    void FR_A10_health_transitions_from_DOWN_back_to_UP() {
        availability.markDown("test-induced DOWN");
        ResponseEntity<Map<String, Object>> downHealth = rest.exchange(
                "/actuator/health", HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {});
        @SuppressWarnings("unchecked")
        Map<String, Object> downTc = (Map<String, Object>) ((Map<String, Object>)
                downHealth.getBody().get("components")).get("teamcenter");
        assertThat(downTc.get("status")).isEqualTo("DOWN");

        availability.markUp();
        ResponseEntity<Map<String, Object>> upHealth = rest.exchange(
                "/actuator/health", HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {});
        @SuppressWarnings("unchecked")
        Map<String, Object> upTc = (Map<String, Object>) ((Map<String, Object>)
                upHealth.getBody().get("components")).get("teamcenter");
        assertThat(upTc.get("status")).isEqualTo("UP");
    }

    @Test
    void S05_single_batch_preserves_TRS_order_across_events() {
        // Risk: multi-event batch ordering under one poll must honour TRS order.
        SimulatorHarness.ReviseResult a = SimulatorHarness.revise("400-1105", "C", "D", "rev-D");
        SimulatorHarness.ReviseResult b = SimulatorHarness.revise("400-1180", "A", "B", "rev-B");
        SimulatorHarness.ReviseResult c = SimulatorHarness.revise("400-1105", "D", "E", "rev-E");

        pollAndFlush();

        List<ConsumerRecord<String, CloudEvent>> records = drain(kafkaPublisher.topic());
        assertThat(records).hasSize(3);
        assertThat(records.stream().map(r -> header(r, "ce_edatrsorder")).toList())
                .as("TRS order must be preserved within a single poll batch")
                .containsExactly("1", "2", "3");
        assertThat(records.stream().map(r -> header(r, "ce_subject")).toList())
                .containsExactly(a.uri(), b.uri(), c.uri());
    }

    @Test
    void S05_partial_batch_leaves_cursor_unchanged_and_commits_only_the_prior_events() {
        // Risk: a mid-batch fetch failure must not advance the cursor; prior events
        // in the same batch stay committed and the whole range retries next poll.
        SimulatorHarness.ReviseResult first =
                SimulatorHarness.revise("400-1105", "C", "D", "rev-D");
        SimulatorHarness.ReviseResult second =
                SimulatorHarness.revise("400-1180", "A", "B", "rev-B");
        SimulatorHarness.revise("400-1105", "D", "E", "rev-E");

        FailingClientConfig.FAIL_FOR_URI.set(uri -> uri.equals(second.uri()));

        assertThatThrownBy(() -> poller.pollOnce())
                .isInstanceOf(TeamcenterUnavailableException.class);
        relay.relayOnce();

        List<ConsumerRecord<String, CloudEvent>> records = drain(kafkaPublisher.topic());
        assertThat(records)
                .as("only the event before the mid-batch failure should be committed")
                .hasSize(1);
        assertThat(records.get(0).value().getSubject()).isEqualTo(first.uri());

        assertThat(cursorRepo.read(SOURCE))
                .as("cursor must not advance when the batch did not complete")
                .isZero();

        long processedRows = jdbc.sql("SELECT count(*) FROM tc_processed_events WHERE source = :src")
                .param("src", SOURCE)
                .query(Long.class)
                .single();
        assertThat(processedRows).isEqualTo(1);
    }

    @Test
    void ADR_0016_outbox_write_rolls_back_when_processed_events_insert_fails() {
        // Risk: outbox and processed_events must share one DB tx. If record() throws,
        // the outbox row rolls back too, so nothing hits Kafka from the failed pass.
        // Retry produces exactly one record with the deterministic id.
        SimulatorHarness.ReviseResult revised =
                SimulatorHarness.revise("400-1105", "C", "D", "retry me");

        ThrowingProcessedConfig.FAIL_NEXT_RECORD.set(true);

        assertThatThrownBy(() -> poller.pollOnce())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("test-induced record failure");

        assertThat(outbox.count())
                .as("outbox row must roll back with tc_processed_events")
                .isZero();
        relay.relayOnce();
        assertThat(drain(kafkaPublisher.topic()))
                .as("nothing to publish from a rolled-back transaction")
                .isEmpty();
        assertThat(cursorRepo.read(SOURCE)).isZero();

        pollAndFlush();

        List<ConsumerRecord<String, CloudEvent>> records = drain(kafkaPublisher.topic());
        assertThat(records).hasSize(1);
        String expectedId = eventFactory.deterministicId(
                new TeamcenterTrsEvent(1, TeamcenterTrsEvent.Kind.CREATION, revised.uri()))
                .toString();
        assertThat(records.get(0).value().getId()).isEqualTo(expectedId);
        assertThat(cursorRepo.read(SOURCE)).isEqualTo(1L);
    }

    @Test
    void S05_DLQ_publisher_handles_null_resource_without_dropping_attributes() {
        // Risk: DlqPublisher must not crash on a null resource. The DLQ event is
        // emitted body-less (Kafka tombstone, same shape as a DELETION event); all
        // attributes still make it through via Kafka headers.
        TeamcenterTrsEvent trs = new TeamcenterTrsEvent(
                999, TeamcenterTrsEvent.Kind.CREATION,
                "https://eda.example.org/r/tc/" + UUID.randomUUID());

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
                                     sh:message "simulated SHACL failure for S05 DLQ test" ] .
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
        TeamcenterClient toggleableTeamcenterClient(
                @Value("${eda.teamcenter.base-url}") String baseUrl,
                TeamcenterAvailability availability) {
            TeamcenterClient delegate = new TeamcenterClient(baseUrl, availability);
            return new TeamcenterClient(baseUrl, availability) {
                @Override
                public List<TeamcenterTrsEvent> fetchChangesSince(long sinceOrder) {
                    return delegate.fetchChangesSince(sinceOrder);
                }

                @Override
                public TeamcenterResource fetchByUri(String uri) {
                    if (FAIL_FOR_URI.get().test(uri)) {
                        availability.markDown("test-induced failure for " + uri);
                        throw new TeamcenterUnavailableException(
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
