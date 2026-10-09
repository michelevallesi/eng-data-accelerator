package org.eda.linkindex.events;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.provider.EventFormatProvider;
import io.cloudevents.jackson.JsonFormat;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.eda.linkindex.persistence.OutboxRepository;
import org.eda.linkindex.persistence.OutboxRepository.Row;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Reads pending {@link OutboxRepository} rows and publishes them to Kafka.
 * At-least-once: on failure, the row stays pending and the next tick retries.
 * The deterministic CloudEvent id built by {@link LinkCloudEventFactory} means
 * downstream dedup is a lookup, not a diff.
 */
@Component
public class KafkaRelay {

    private static final Logger log = LoggerFactory.getLogger(KafkaRelay.class);

    private final OutboxRepository outbox;
    private final KafkaTemplate<String, CloudEvent> template;
    private final int batchSize;

    public KafkaRelay(OutboxRepository outbox,
                      KafkaTemplate<String, CloudEvent> template,
                      @Value("${eda.link-index.outbox-relay-batch-size:100}") int batchSize) {
        this.outbox = outbox;
        this.template = template;
        this.batchSize = batchSize;
    }

    @Scheduled(
            fixedDelayString = "${eda.link-index.outbox-relay-interval-ms}",
            initialDelayString = "${eda.link-index.outbox-relay-initial-delay-ms:500}")
    public void relayScheduled() {
        try {
            relayOnce();
        } catch (Exception e) {
            log.warn("outbox relay batch failed: {}", e.getMessage());
        }
    }

    /** Test seam. Returns the number of records sent in this pass. */
    public int relayOnce() {
        int sent = 0;
        for (Row row : outbox.loadPending(batchSize)) {
            CloudEvent event = EventFormatProvider.getInstance()
                    .resolveFormat(JsonFormat.CONTENT_TYPE)
                    .deserialize(row.cloudEventJson());
            try {
                template.send(new ProducerRecord<>(row.topic(), row.kafkaKey(), event)).get();
                outbox.markPublished(row.id());
                sent++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("interrupted publishing outbox id " + row.id(), e);
            } catch (Exception e) {
                log.warn("failed to publish outbox id {}: {}", row.id(), e.toString());
                break;
            }
        }
        return sent;
    }
}
