package org.eda.adapter.teamcenter.events;

import static org.assertj.core.api.Assertions.assertThat;

import io.cloudevents.CloudEvent;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.apache.jena.rdf.model.ModelFactory;
import org.eda.adapter.teamcenter.teamcenter.TeamcenterResource;
import org.eda.adapter.teamcenter.teamcenter.TeamcenterTrsEvent;
import org.junit.jupiter.api.Test;

class CloudEventFactoryTest {

    private static final String SOURCE = "urn:eda:adapter:teamcenter";
    private static final String MAPPING_VERSION = "1";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-07T12:00:00Z"), ZoneOffset.UTC);

    private final CloudEventFactory factory = new CloudEventFactory(SOURCE, MAPPING_VERSION, CLOCK);

    private static final String ITEM_URI =
            "https://eda.example.org/r/tc/53c7253e-fec0-44b3-8a7d-88629ebb0a07";

    private static TeamcenterResource resource(int version) {
        return new TeamcenterResource(ITEM_URI, ModelFactory.createDefaultModel(),
                "{}".getBytes(StandardCharsets.UTF_8), version);
    }

    @Test
    void FR_B04_deterministic_id_for_same_source_order_subject() {
        TeamcenterTrsEvent trs = new TeamcenterTrsEvent(42, TeamcenterTrsEvent.Kind.CREATION, ITEM_URI);

        CloudEvent first = factory.resourceChanged(trs, resource(1));
        CloudEvent second = factory.resourceChanged(trs, resource(2));

        assertThat(first.getId()).isEqualTo(second.getId());
    }

    @Test
    void ADR_0026_mandatory_attributes_are_populated() {
        TeamcenterTrsEvent trs = new TeamcenterTrsEvent(7, TeamcenterTrsEvent.Kind.CREATION, ITEM_URI);
        CloudEvent event = factory.resourceChanged(trs, resource(5));

        assertThat(event.getType()).isEqualTo(CloudEventFactory.TYPE_RESOURCE_CHANGED);
        assertThat(event.getSource().toString()).isEqualTo(SOURCE);
        assertThat(event.getSubject()).isEqualTo(ITEM_URI);
        assertThat(event.getId()).isNotBlank();
        assertThat(event.getTime()).isNotNull();
        assertThat(event.getDataContentType()).isEqualTo("application/ld+json");

        assertThat(event.getExtension(CloudEventFactory.EXT_MAPPING_VERSION)).isEqualTo(MAPPING_VERSION);
        assertThat(event.getExtension(CloudEventFactory.EXT_VERSION)).isEqualTo(5);
        assertThat(event.getExtension(CloudEventFactory.EXT_SOURCE_TIME)).isNotNull();
        assertThat(event.getExtension(CloudEventFactory.EXT_TRS_ORDER)).isEqualTo(7L);
        assertThat(event.getExtension(CloudEventFactory.EXT_KIND)).isEqualTo("CREATION");
    }

    @Test
    void deletion_event_carries_no_payload_but_the_mandatory_attributes() {
        TeamcenterTrsEvent trs = new TeamcenterTrsEvent(3, TeamcenterTrsEvent.Kind.DELETION, ITEM_URI);
        CloudEvent event = factory.resourceDeleted(trs);

        assertThat(event.getType()).isEqualTo(CloudEventFactory.TYPE_RESOURCE_CHANGED);
        assertThat(event.getExtension(CloudEventFactory.EXT_KIND)).isEqualTo("DELETION");
        assertThat(event.getData()).isNull();
    }

    @Test
    void ADR_0026_edasourcetime_extension_is_RFC3339_timestamp() {
        TeamcenterTrsEvent trs = new TeamcenterTrsEvent(1, TeamcenterTrsEvent.Kind.CREATION, ITEM_URI);
        CloudEvent event = factory.resourceChanged(trs, resource(1));

        Object edasourcetime = event.getExtension(CloudEventFactory.EXT_SOURCE_TIME);
        assertThat(edasourcetime).isNotNull();
        java.time.OffsetDateTime parsed =
                java.time.OffsetDateTime.parse(edasourcetime.toString());
        assertThat(parsed).isEqualTo(
                java.time.OffsetDateTime.ofInstant(CLOCK.instant(), java.time.ZoneOffset.UTC));
    }
}
