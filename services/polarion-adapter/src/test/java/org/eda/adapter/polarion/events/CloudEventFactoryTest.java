package org.eda.adapter.polarion.events;

import static org.assertj.core.api.Assertions.assertThat;

import io.cloudevents.CloudEvent;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.apache.jena.rdf.model.ModelFactory;
import org.eda.adapter.polarion.polarion.PolarionResource;
import org.eda.adapter.polarion.polarion.PolarionTrsEvent;
import org.junit.jupiter.api.Test;

class CloudEventFactoryTest {

    private static final String SOURCE = "urn:eda:adapter:polarion";
    private static final String MAPPING_VERSION = "1";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-01T12:00:00Z"), ZoneOffset.UTC);

    private final CloudEventFactory factory = new CloudEventFactory(SOURCE, MAPPING_VERSION, CLOCK);

    private static final String REQ_019_URI =
            "https://eda.example.org/r/pol/7d33b7bc-90e1-461c-97c1-8845d01a68d8";

    private static PolarionResource resource(int version) {
        return new PolarionResource(REQ_019_URI, ModelFactory.createDefaultModel(),
                "{}".getBytes(StandardCharsets.UTF_8), version);
    }

    @Test
    void FR_B04_deterministic_id_for_same_source_order_subject() {
        PolarionTrsEvent trs = new PolarionTrsEvent(42, PolarionTrsEvent.Kind.MODIFICATION, REQ_019_URI);

        CloudEvent first = factory.resourceChanged(trs, resource(2));
        CloudEvent second = factory.resourceChanged(trs, resource(3));

        assertThat(first.getId())
                .as("id must depend only on (source, trsOrder, subject), not version")
                .isEqualTo(second.getId());
    }

    @Test
    void FR_B04_different_order_or_subject_yields_different_id() {
        PolarionTrsEvent a = new PolarionTrsEvent(1, PolarionTrsEvent.Kind.MODIFICATION, REQ_019_URI);
        PolarionTrsEvent b = new PolarionTrsEvent(2, PolarionTrsEvent.Kind.MODIFICATION, REQ_019_URI);
        PolarionTrsEvent c = new PolarionTrsEvent(1, PolarionTrsEvent.Kind.MODIFICATION, REQ_019_URI + "-x");

        assertThat(factory.resourceChanged(a, resource(1)).getId())
                .isNotEqualTo(factory.resourceChanged(b, resource(1)).getId());
        assertThat(factory.resourceChanged(a, resource(1)).getId())
                .isNotEqualTo(factory.resourceChanged(c, resource(1)).getId());
    }

    @Test
    void ADR_0026_mandatory_attributes_are_populated() {
        PolarionTrsEvent trs = new PolarionTrsEvent(7, PolarionTrsEvent.Kind.MODIFICATION, REQ_019_URI);
        CloudEvent event = factory.resourceChanged(trs, resource(5));

        assertThat(event.getType()).isEqualTo(CloudEventFactory.TYPE_RESOURCE_CHANGED);
        assertThat(event.getSource().toString()).isEqualTo(SOURCE);
        assertThat(event.getSubject()).isEqualTo(REQ_019_URI);
        assertThat(event.getId()).isNotBlank();
        assertThat(event.getTime()).isNotNull();
        assertThat(event.getDataContentType()).isEqualTo("application/ld+json");

        assertThat(event.getExtension(CloudEventFactory.EXT_MAPPING_VERSION)).isEqualTo(MAPPING_VERSION);
        assertThat(event.getExtension(CloudEventFactory.EXT_VERSION)).isEqualTo(5);
        assertThat(event.getExtension(CloudEventFactory.EXT_SOURCE_TIME)).isNotNull();
        assertThat(event.getExtension(CloudEventFactory.EXT_TRS_ORDER)).isEqualTo(7L);
        assertThat(event.getExtension(CloudEventFactory.EXT_KIND)).isEqualTo("MODIFICATION");
    }

    @Test
    void deletion_event_carries_no_payload_but_the_mandatory_attributes() {
        PolarionTrsEvent trs = new PolarionTrsEvent(3, PolarionTrsEvent.Kind.DELETION, REQ_019_URI);
        CloudEvent event = factory.resourceDeleted(trs);

        assertThat(event.getType()).isEqualTo(CloudEventFactory.TYPE_RESOURCE_CHANGED);
        assertThat(event.getSubject()).isEqualTo(REQ_019_URI);
        assertThat(event.getExtension(CloudEventFactory.EXT_KIND)).isEqualTo("DELETION");
        assertThat(event.getData()).isNull();
    }

    @Test
    void ADR_0026_edasourcetime_extension_is_RFC3339_timestamp() {
        PolarionTrsEvent trs = new PolarionTrsEvent(1, PolarionTrsEvent.Kind.MODIFICATION, REQ_019_URI);
        CloudEvent event = factory.resourceChanged(trs, resource(1));

        Object edasourcetime = event.getExtension(CloudEventFactory.EXT_SOURCE_TIME);
        assertThat(edasourcetime).isNotNull();
        java.time.OffsetDateTime parsed =
                java.time.OffsetDateTime.parse(edasourcetime.toString());
        assertThat(parsed).isEqualTo(
                java.time.OffsetDateTime.ofInstant(CLOCK.instant(), java.time.ZoneOffset.UTC));
    }
}
