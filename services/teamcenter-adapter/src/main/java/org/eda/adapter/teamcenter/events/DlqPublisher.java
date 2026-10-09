package org.eda.adapter.teamcenter.events;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import java.net.URI;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.eda.adapter.teamcenter.persistence.OutboxRepository;
import org.eda.adapter.teamcenter.teamcenter.TeamcenterResource;
import org.eda.adapter.teamcenter.teamcenter.TeamcenterTrsEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DlqPublisher {

    public static final String TYPE_RESOURCE_INVALID = "eda.resource.invalid";

    private final OutboxRepository outbox;
    private final String topic;
    private final String source;
    private final Clock clock;
    private final CloudEventFactory eventFactory;

    public DlqPublisher(OutboxRepository outbox,
                        @Value("${eda.kafka.topics.dlq}") String topic,
                        @Value("${eda.teamcenter.source-id}") String source,
                        Clock clock,
                        CloudEventFactory eventFactory) {
        this.outbox = outbox;
        this.topic = topic;
        this.source = source;
        this.clock = clock;
        this.eventFactory = eventFactory;
    }

    public CloudEvent publish(TeamcenterTrsEvent trsEvent, TeamcenterResource resource, String violation) {
        io.cloudevents.core.v1.CloudEventBuilder builder = CloudEventBuilder.v1()
                .withId(eventFactory.deterministicId(trsEvent).toString())
                .withType(TYPE_RESOURCE_INVALID)
                .withSource(URI.create(source))
                .withSubject(trsEvent.resourceUri())
                .withTime(OffsetDateTime.now(clock))
                .withExtension(CloudEventFactory.EXT_TRS_ORDER, trsEvent.order())
                .withExtension(CloudEventFactory.EXT_KIND, trsEvent.kind().name())
                .withExtension("edaviolation", violation);
        if (resource != null) {
            builder.withDataContentType("application/ld+json").withData(resource.jsonLdBytes());
        }
        CloudEvent event = builder.build();

        outbox.enqueue(topic, trsEvent.resourceUri(), event);
        return event;
    }

    public String topic() {
        return topic;
    }
}
