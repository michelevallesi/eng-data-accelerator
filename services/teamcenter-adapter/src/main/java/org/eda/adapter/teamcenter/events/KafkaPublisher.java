package org.eda.adapter.teamcenter.events;

import io.cloudevents.CloudEvent;
import org.eda.adapter.teamcenter.persistence.OutboxRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Enqueues a CloudEvent for the Teamcenter main topic through the outbox
 * (ADR-0016). Keyed by resource URI (FR-B06).
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
