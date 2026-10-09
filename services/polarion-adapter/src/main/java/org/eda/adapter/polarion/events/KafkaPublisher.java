package org.eda.adapter.polarion.events;

import io.cloudevents.CloudEvent;
import org.eda.adapter.polarion.persistence.OutboxRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Enqueues a CloudEvent for the main resource-changes topic through the outbox
 * (ADR-0016). Keyed by resource URI so events for the same resource stay on the
 * same partition at relay time (FR-B06).
 *
 * <p>Historically this class published directly to Kafka; it now writes a row to
 * {@code event_outbox} so publish and {@code processed_events} share one DB
 * transaction. {@link KafkaRelay} drains the outbox asynchronously.
 */
@Component
public class KafkaPublisher {

    private final OutboxRepository outbox;
    private final String topic;

    public KafkaPublisher(OutboxRepository outbox,
                          @Value("${eda.kafka.topics.changes}") String topic) {
        this.outbox = outbox;
        this.topic = topic;
    }

    public void publish(String key, CloudEvent event) {
        outbox.enqueue(topic, key, event);
    }

    public String topic() {
        return topic;
    }
}
