package org.eda.projection.kafka;

import io.cloudevents.CloudEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.eda.projection.projection.EventProjector;
import org.eda.projection.projection.IncomingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class ChangeEventListener {

    private static final Logger log = LoggerFactory.getLogger(ChangeEventListener.class);

    private final EventProjector projector;

    public ChangeEventListener(EventProjector projector) {
        this.projector = projector;
    }

    @KafkaListener(
            id = "${eda.projection.listener-id}",
            groupId = "${spring.kafka.consumer.group-id}",
            topics = {
                    "${eda.kafka.topics.polarion-changes}",
                    "${eda.kafka.topics.teamcenter-changes}"
            })
    public void onEvent(ConsumerRecord<String, CloudEvent> record, Acknowledgment ack) {
        try {
            IncomingEvent event = IncomingEvent.from(record);
            boolean projected = projector.project(event);
            log.debug("record topic={} offset={} ce_id={} projected={}",
                    record.topic(), record.offset(), event.id(), projected);
            ack.acknowledge();
        } catch (RuntimeException e) {
            log.error("failed to project record topic={} offset={}: {}",
                    record.topic(), record.offset(), e.toString(), e);
            throw e;
        }
    }
}
