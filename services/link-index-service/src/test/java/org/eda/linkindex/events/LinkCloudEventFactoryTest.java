package org.eda.linkindex.events;

import static org.assertj.core.api.Assertions.assertThat;

import io.cloudevents.CloudEvent;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.eda.linkindex.domain.DispositionOutcome;
import org.eda.linkindex.domain.LinkRecord;
import org.eda.linkindex.domain.LinkState;
import org.junit.jupiter.api.Test;

class LinkCloudEventFactoryTest {

    private static final String SOURCE = "urn:eda:service:link-index";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-07T12:00:00Z"), ZoneOffset.UTC);

    private final LinkCloudEventFactory factory = new LinkCloudEventFactory(SOURCE, CLOCK);

    private LinkRecord link(LinkState state, String sourceVer, String targetVer, String cause) {
        return new LinkRecord(
                "https://eda.example.org/link/abc",
                "satisfiedBy", "REQ-019", "LC-11",
                "https://eda.example.org/r/pol/req", "https://eda.example.org/r/pol/lc",
                sourceVer, targetVer,
                state, cause, "alice", OffsetDateTime.now(CLOCK));
    }

    @Test
    void FR_B04_deterministic_id_for_same_tuple() {
        LinkRecord prev = link(LinkState.ASSERTED, "1", "1", "api-confirm");
        LinkRecord next = link(LinkState.SUPERSEDED, "1", "1", "resource-changed:X");

        CloudEvent a = factory.stateChanged(prev, next, null);
        CloudEvent b = factory.stateChanged(prev, next, null);
        assertThat(a.getId()).isEqualTo(b.getId());
    }

    @Test
    void ADR_0016_transition_carries_prev_and_new_state_as_extensions() {
        LinkRecord prev = link(LinkState.ASSERTED, "1", "1", "api-confirm");
        LinkRecord next = link(LinkState.SUPERSEDED, "1", "1", "resource-changed:X");

        CloudEvent event = factory.stateChanged(prev, next, null);
        assertThat(event.getType()).isEqualTo(LinkCloudEventFactory.TYPE);
        assertThat(event.getSource().toString()).isEqualTo(SOURCE);
        assertThat(event.getSubject()).isEqualTo(prev.id());
        assertThat(event.getExtension(LinkCloudEventFactory.EXT_PREV_STATE)).isEqualTo("asserted");
        assertThat(event.getExtension(LinkCloudEventFactory.EXT_NEW_STATE)).isEqualTo("superseded");
        assertThat(event.getExtension(LinkCloudEventFactory.EXT_CAUSE)).isEqualTo("resource-changed:X");
        assertThat(event.getExtension(LinkCloudEventFactory.EXT_ACTOR)).isEqualTo("alice");
    }

    @Test
    void disposition_outcome_rides_the_event_when_present() {
        LinkRecord prev = link(LinkState.SUPERSEDED, "1", "1", "resource-changed:X");
        LinkRecord next = link(LinkState.ASSERTED, "2", "1", "disposition-confirmed:D1");

        CloudEvent event = factory.stateChanged(prev, next, DispositionOutcome.CONFIRMED);
        assertThat(event.getExtension(LinkCloudEventFactory.EXT_DISPOSITION_OUTCOME))
                .isEqualTo("confirmed");
    }

    @Test
    void propose_event_without_previous_state_omits_prev_state_extension() {
        LinkRecord next = link(LinkState.PROPOSED, "1", "1", "api-propose");

        CloudEvent event = factory.stateChanged(null, next, null);
        assertThat(event.getExtension(LinkCloudEventFactory.EXT_PREV_STATE)).isNull();
        assertThat(event.getExtension(LinkCloudEventFactory.EXT_NEW_STATE)).isEqualTo("proposed");
    }
}
