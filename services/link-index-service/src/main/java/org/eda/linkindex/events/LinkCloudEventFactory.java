package org.eda.linkindex.events;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.eda.linkindex.domain.DispositionOutcome;
import org.eda.linkindex.domain.LinkRecord;
import org.eda.linkindex.domain.LinkState;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Builds {@code eda.link.state-changed} CloudEvents for every link transition
 * (ADR-0016). The CloudEvent id is deterministic over
 * {@code (linkId, previousState, newState, cause)} so a replay of the outbox
 * produces byte-identical ids and downstream dedup is trivial.
 */
@Component
public class LinkCloudEventFactory {

    public static final String TYPE = "eda.link.state-changed";

    public static final String EXT_RELATION = "edarelation";
    public static final String EXT_PREV_STATE = "edaprevstate";
    public static final String EXT_NEW_STATE = "edanewstate";
    public static final String EXT_CAUSE = "edacause";
    public static final String EXT_ACTOR = "edaactor";
    public static final String EXT_PREV_SOURCE_VERSION = "edaprevsourceversion";
    public static final String EXT_PREV_TARGET_VERSION = "edaprevtargetversion";
    public static final String EXT_NEW_SOURCE_VERSION = "edanewsourceversion";
    public static final String EXT_NEW_TARGET_VERSION = "edanewtargetversion";
    public static final String EXT_DISPOSITION_OUTCOME = "edadispositionoutcome";

    private final String source;
    private final Clock clock;

    public LinkCloudEventFactory(@Value("${eda.link-index.source-id}") String source, Clock clock) {
        this.source = source;
        this.clock = clock;
    }

    public CloudEvent stateChanged(LinkRecord previous, LinkRecord next,
                                   DispositionOutcome outcome) {
        LinkState prevState = previous == null ? null : previous.state();
        String prevSourceVersion = previous == null ? null : previous.sourceVersion();
        String prevTargetVersion = previous == null ? null : previous.targetVersion();

        CloudEventBuilder builder = CloudEventBuilder.v1()
                .withId(deterministicId(next.id(), prevState, next.state(), next.cause()).toString())
                .withType(TYPE)
                .withSource(URI.create(source))
                .withSubject(next.id())
                .withTime(OffsetDateTime.now(clock))
                .withExtension(EXT_RELATION, next.relation())
                .withExtension(EXT_NEW_STATE, next.state().wire())
                .withExtension(EXT_NEW_SOURCE_VERSION, next.sourceVersion())
                .withExtension(EXT_NEW_TARGET_VERSION, next.targetVersion());
        if (prevState != null) {
            builder.withExtension(EXT_PREV_STATE, prevState.wire());
        }
        if (prevSourceVersion != null) {
            builder.withExtension(EXT_PREV_SOURCE_VERSION, prevSourceVersion);
        }
        if (prevTargetVersion != null) {
            builder.withExtension(EXT_PREV_TARGET_VERSION, prevTargetVersion);
        }
        if (next.cause() != null) {
            builder.withExtension(EXT_CAUSE, next.cause());
        }
        if (next.actor() != null) {
            builder.withExtension(EXT_ACTOR, next.actor());
        }
        if (outcome != null) {
            builder.withExtension(EXT_DISPOSITION_OUTCOME, outcome.wire());
        }
        return builder.build();
    }

    public UUID deterministicId(String linkId, LinkState prev, LinkState next, String cause) {
        String seed = source + "|" + linkId
                + "|" + (prev == null ? "" : prev.wire())
                + "|" + next.wire()
                + "|" + (cause == null ? "" : cause);
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
    }
}
