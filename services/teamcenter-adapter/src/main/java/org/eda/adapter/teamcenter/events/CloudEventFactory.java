package org.eda.adapter.teamcenter.events;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.eda.adapter.teamcenter.teamcenter.TeamcenterResource;
import org.eda.adapter.teamcenter.teamcenter.TeamcenterTrsEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Builds {@code eda.resource.changed} events with the ADR-0026 attributes. The
 * CloudEvent {@code id} is deterministic per {@code (source, trsOrder, subject)}
 * so replays never duplicate even without a transactional outbox (FR-B04).
 *
 * <p>Risk #2 assumption (shared with the Polarion adapter): {@code trsOrder} is
 * monotonic across the source tool's lifetime. See the Polarion adapter's
 * CloudEventFactory for the full rationale and ADR-0025 for the deferred
 * hardening (cutoff-IRI seed).
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

    public CloudEventFactory(@Value("${eda.teamcenter.source-id}") String source,
                             @Value("${eda.mapping-version}") String mappingVersion,
                             Clock clock) {
        this.source = source;
        this.mappingVersion = mappingVersion;
        this.clock = clock;
    }

    public CloudEvent resourceChanged(TeamcenterTrsEvent trsEvent, TeamcenterResource resource) {
        return baseBuilder(trsEvent)
                .withExtension(EXT_VERSION, resource.version())
                .withDataContentType("application/ld+json")
                .withData(resource.jsonLdBytes())
                .build();
    }

    public CloudEvent resourceDeleted(TeamcenterTrsEvent trsEvent) {
        return baseBuilder(trsEvent).build();
    }

    private CloudEventBuilder baseBuilder(TeamcenterTrsEvent trsEvent) {
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

    public UUID deterministicId(TeamcenterTrsEvent trsEvent) {
        String seed = source + ":" + trsEvent.order() + ":" + trsEvent.resourceUri();
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
    }

    public String source() {
        return source;
    }
}
