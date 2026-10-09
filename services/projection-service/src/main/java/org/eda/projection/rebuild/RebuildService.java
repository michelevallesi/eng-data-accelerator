package org.eda.projection.rebuild;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.eda.projection.fuseki.FusekiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.stereotype.Component;

/**
 * Rebuilds the projected graph from scratch (FR-M03, AD-01). Flow:
 *
 * <ol>
 *   <li>Stop the live Kafka listener so no concurrent writes land in Fuseki.</li>
 *   <li>{@code DROP ALL} on the Fuseki dataset.</li>
 *   <li>Seek the consumer group back to the earliest offset for every partition.</li>
 *   <li>Resume the listener; events are replayed, {@code EventProjector} is
 *       idempotent per {@code ce_id}, so re-ingesting is safe.</li>
 *   <li>Wait until the listener's current offset catches up to the end offset
 *       captured in step 3.</li>
 * </ol>
 */
@Component
public class RebuildService {

    private static final Logger log = LoggerFactory.getLogger(RebuildService.class);

    private final FusekiClient fuseki;
    private final KafkaListenerEndpointRegistry registry;
    private final ConsumerFactory<?, ?> consumerFactory;
    private final String listenerId;
    private final String groupId;
    private final List<String> topics;

    public RebuildService(FusekiClient fuseki,
                          KafkaListenerEndpointRegistry registry,
                          ConsumerFactory<?, ?> consumerFactory,
                          @Value("${eda.projection.listener-id}") String listenerId,
                          @Value("${eda.kafka.topics.polarion-changes}") String polarionTopic,
                          @Value("${eda.kafka.topics.teamcenter-changes}") String teamcenterTopic) {
        this.fuseki = fuseki;
        this.registry = registry;
        this.consumerFactory = consumerFactory;
        this.listenerId = listenerId;
        this.groupId = String.valueOf(
                consumerFactory.getConfigurationProperties().get("group.id"));
        this.topics = List.of(polarionTopic, teamcenterTopic);
    }

    public RebuildResult rebuild() {
        MessageListenerContainer container = registry.getListenerContainer(listenerId);
        if (container == null) {
            throw new IllegalStateException("listener " + listenerId + " not registered");
        }
        log.warn("rebuild: dropping Fuseki dataset and rewinding offsets");
        container.stop();
        waitUntilStopped(container);
        try {
            fuseki.clearAll();
            Map<TopicPartition, Long> endOffsets = rewindToEarliest();
            container.start();
            waitForCatchUp(endOffsets);
            return new RebuildResult(endOffsets.size(), total(endOffsets));
        } finally {
            if (!container.isRunning()) {
                container.start();
            }
        }
    }

    /**
     * {@link MessageListenerContainer#stop()} returns after the configured shutdown
     * timeout even if the consumer is still finishing an in-flight record. Spin
     * until the container actually reports stopped so the rewind consumer joins the
     * group alone and our standalone commit cannot be overwritten.
     */
    private static void waitUntilStopped(MessageListenerContainer container) {
        long deadline = System.currentTimeMillis() + Duration.ofSeconds(10).toMillis();
        while (container.isRunning() && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("rebuild interrupted while stopping listener", e);
            }
        }
        if (container.isRunning()) {
            throw new IllegalStateException("listener did not stop within 10 seconds");
        }
    }

    private Map<TopicPartition, Long> rewindToEarliest() {
        Map<TopicPartition, Long> endOffsets = new HashMap<>();
        try (KafkaConsumer<?, ?> consumer = (KafkaConsumer<?, ?>)
                consumerFactory.createConsumer(groupId, "rebuild-rewind-" + System.nanoTime())) {
            List<TopicPartition> assignable = new ArrayList<>();
            for (String topic : topics) {
                List<PartitionInfo> info = consumer.partitionsFor(topic);
                for (PartitionInfo p : info) {
                    assignable.add(new TopicPartition(topic, p.partition()));
                }
            }
            consumer.assign(assignable);
            consumer.poll(Duration.ofMillis(200));
            endOffsets.putAll(consumer.endOffsets(assignable));
            Map<TopicPartition, Long> beginning = consumer.beginningOffsets(assignable);
            for (TopicPartition tp : assignable) {
                consumer.seek(tp, beginning.get(tp));
                consumer.commitSync(Map.of(tp,
                        new org.apache.kafka.clients.consumer.OffsetAndMetadata(
                                beginning.get(tp))));
            }
        }
        return endOffsets;
    }

    private void waitForCatchUp(Map<TopicPartition, Long> endOffsets) {
        long deadline = System.currentTimeMillis() + Duration.ofMinutes(2).toMillis();
        while (System.currentTimeMillis() < deadline) {
            try (KafkaConsumer<?, ?> consumer = (KafkaConsumer<?, ?>)
                    consumerFactory.createConsumer(groupId, "rebuild-check-" + System.nanoTime())) {
                List<TopicPartition> parts = new ArrayList<>(endOffsets.keySet());
                consumer.assign(parts);
                Map<TopicPartition, org.apache.kafka.clients.consumer.OffsetAndMetadata> commits =
                        consumer.committed(new java.util.HashSet<>(parts));
                boolean done = true;
                for (TopicPartition tp : parts) {
                    long end = endOffsets.get(tp);
                    org.apache.kafka.clients.consumer.OffsetAndMetadata committed = commits.get(tp);
                    long position = committed == null ? 0L : committed.offset();
                    if (position < end) {
                        done = false;
                        break;
                    }
                }
                if (done) {
                    return;
                }
            }
            sleep();
        }
        throw new IllegalStateException("rebuild did not catch up within 2 minutes");
    }

    private static void sleep() {
        try {
            Thread.sleep(200);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("rebuild interrupted", ie);
        }
    }

    private static long total(Map<TopicPartition, Long> offsets) {
        long sum = 0;
        for (long v : offsets.values()) {
            sum += v;
        }
        return sum;
    }

    public record RebuildResult(int partitionsRewound, long totalEventsReplayed) {
    }
}
