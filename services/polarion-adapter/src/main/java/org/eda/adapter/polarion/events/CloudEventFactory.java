package org.eda.adapter.polarion.events;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.eda.adapter.polarion.polarion.PolarionResource;
import org.eda.adapter.polarion.polarion.PolarionTrsEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Builds {@code eda.resource.changed} events with the ADR-0026 attributes. The
 * CloudEvent {@code id} is deterministic per {@code (source, trsOrder, subject)} so
 * replays never duplicate even without a transactional outbox (FR-B04).
 */
@Component
public class CloudEventFactory {

    public static final String TYPE_RESOURCE_CHANGED = "eda.resource.changed";

    public static final String EXT_VERSION = "edaversion";
    public static final String EXT_MAPPING_VERSION = "edamappingversion";
    public static final String EXT_SOURCE_TIME = "edasourcetime";
    public static final String EXT_TRS_ORDER = "edatrsorder";
    public static final String EXT_KIND = "edakind";

    private final String source;
    private final String mappingVersion;
    private final Clock clock;

    public CloudEventFactory(@Value("${eda.polarion.source-id}") String source,
                             @Value("${eda.mapping-version}") String mappingVersion,
                             Clock clock) {
        this.source = source;
        this.mappingVersion = mappingVersion;
        this.clock = clock;
    }

    public CloudEvent resourceChanged(PolarionTrsEvent trsEvent, PolarionResource resource) {
        return baseBuilder(trsEvent)
                .withExtension(EXT_VERSION, resource.version())
                .withDataContentType("application/ld+json")
                .withData(resource.jsonLdBytes())
                .build();
    }

    public CloudEvent resourceDeleted(PolarionTrsEvent trsEvent) {
        return baseBuilder(trsEvent).build();
    }

    private CloudEventBuilder baseBuilder(PolarionTrsEvent trsEvent) {
        return CloudEventBuilder.v1()
                .withId(deterministicId(trsEvent).toString())
                .withType(TYPE_RESOURCE_CHANGED)
                .withSource(URI.create(source))
                .withSubject(trsEvent.resourceUri())
                .withTime(OffsetDateTime.now(clock))
                .withExtension(EXT_MAPPING_VERSION, mappingVersion)
                .withExtension(EXT_SOURCE_TIME, OffsetDateTime.now(clock).toString())
                .withExtension(EXT_TRS_ORDER, trsEvent.order())
                .withExtension(EXT_KIND, trsEvent.kind().name());
    }

    /**
     * Deterministic UUIDv3 over {@code (source, trsOrder, subject)}. A replay of
     * the same TRS event produces the same id, so downstream dedup is trivial.
     *
     * <p><strong>Risk #2 assumption</strong>: {@code trsOrder} is monotonic across
     * the source tool's lifetime. Real Polarion satisfies that; the simulator
     * resets its counter on {@code state.reset()}, so test ids can collide across
     * runs. Mitigated at the Kafka layer by per-test baseline-offset capture and
     * at the DB layer by truncating {@code processed_events} in @BeforeEach. A
     * future hardening story (ADR-0025) may fold a stable cutoff IRI into the
     * seed when real-size runs demand belt-and-braces.
     */
    public UUID deterministicId(PolarionTrsEvent trsEvent) {
        String seed = source + ":" + trsEvent.order() + ":" + trsEvent.resourceUri();
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
    }

    public String source() {
        return source;
    }
}
