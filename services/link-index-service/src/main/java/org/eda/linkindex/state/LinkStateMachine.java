package org.eda.linkindex.state;

import io.cloudevents.CloudEvent;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.eda.linkindex.domain.ArtefactRecord;
import org.eda.linkindex.domain.DispositionOutcome;
import org.eda.linkindex.domain.DispositionRecord;
import org.eda.linkindex.domain.LinkRecord;
import org.eda.linkindex.domain.LinkState;
import org.eda.linkindex.events.LinkCloudEventFactory;
import org.eda.linkindex.persistence.ArtefactRepository;
import org.eda.linkindex.persistence.DispositionRepository;
import org.eda.linkindex.persistence.LinkRepository;
import org.eda.linkindex.persistence.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Core S07 state machine. Every method here that mutates link state runs
 * inside a single DB transaction that writes BOTH the {@code link_link} /
 * {@code link_disposition} update AND the {@code link_event_outbox} row —
 * ADR-0016's "every change emits an event in the same transaction".
 *
 * <p>Rules:
 *
 * <ul>
 *   <li>{@code proposed → asserted} requires an explicit confirmation with an
 *       actor (AD-06). Any other direct state write is rejected.</li>
 *   <li>On a resource.changed event with a bumped version, every
 *       {@code asserted}/{@code validated} link whose source or target was
 *       pinned to the previous version transitions to {@code superseded} and a
 *       disposition is opened for the owning role (FR-S08).</li>
 *   <li>On a resource.changed DELETION event, every non-{@code dangling} link
 *       referencing the subject URI transitions to {@code dangling} — the link
 *       is kept, never deleted (FR-F06, FR-S07).</li>
 *   <li>A disposition resolved {@code confirmed} re-asserts the link on the
 *       new version (FR-S14 / S15 "Confirmed re-asserts"). Other outcomes close
 *       the disposition without touching the link state.</li>
 * </ul>
 */
@Component
public class LinkStateMachine {

    private static final Logger log = LoggerFactory.getLogger(LinkStateMachine.class);

    private final LinkRepository linkRepo;
    private final ArtefactRepository artefactRepo;
    private final DispositionRepository dispositionRepo;
    private final OutboxRepository outbox;
    private final LinkCloudEventFactory eventFactory;
    private final OwnerResolver ownerResolver;
    private final TransactionTemplate txTemplate;
    private final Clock clock;
    private final String topic;

    public LinkStateMachine(LinkRepository linkRepo,
                            ArtefactRepository artefactRepo,
                            DispositionRepository dispositionRepo,
                            OutboxRepository outbox,
                            LinkCloudEventFactory eventFactory,
                            OwnerResolver ownerResolver,
                            PlatformTransactionManager txManager,
                            Clock clock,
                            @Value("${eda.kafka.topics.link-state-changed}") String topic) {
        this.linkRepo = linkRepo;
        this.artefactRepo = artefactRepo;
        this.dispositionRepo = dispositionRepo;
        this.outbox = outbox;
        this.eventFactory = eventFactory;
        this.ownerResolver = ownerResolver;
        this.txTemplate = new TransactionTemplate(txManager);
        this.clock = clock;
        this.topic = topic;
    }

    // ---------------------------------------------------------------- API ----

    public LinkRecord propose(String id, String relation, String sourceNativeId,
                              String targetNativeId) {
        ArtefactRecord source = requireArtefact(sourceNativeId);
        ArtefactRecord target = requireArtefact(targetNativeId);
        LinkRecord record = new LinkRecord(id, relation,
                source.nativeId(), target.nativeId(),
                source.uri(), target.uri(),
                source.version(), target.version(),
                LinkState.PROPOSED, "api-propose", null,
                OffsetDateTime.now(clock));
        linkRepo.insert(record);
        return record;
    }

    public LinkRecord confirm(String id, String actor) {
        if (actor == null || actor.isBlank()) {
            throw new IllegalArgumentException("actor is required to confirm a proposed link");
        }
        return txTemplate.execute(status -> {
            LinkRecord existing = linkRepo.findById(id)
                    .orElseThrow(() -> new NoSuchElementException("link not found: " + id));
            if (existing.state() != LinkState.PROPOSED) {
                throw new IllegalStateException(
                        "confirm requires a proposed link, found: " + existing.state());
            }
            ArtefactRecord source = requireArtefact(existing.sourceNativeId());
            ArtefactRecord target = requireArtefact(existing.targetNativeId());
            LinkRecord asserted = new LinkRecord(id, existing.relation(),
                    existing.sourceNativeId(), existing.targetNativeId(),
                    source.uri(), target.uri(),
                    source.version(), target.version(),
                    LinkState.ASSERTED, "api-confirm", actor,
                    OffsetDateTime.now(clock));
            linkRepo.update(asserted);
            enqueueEvent(existing, asserted, null);
            return asserted;
        });
    }

    /**
     * React to a resource.changed CloudEvent. Called from the Kafka listener;
     * runs the whole state transition + outbox write inside one DB tx.
     */
    public void onResourceChanged(CloudEvent event, String subject, String kind,
                                  String newVersion, String ceId) {
        txTemplate.executeWithoutResult(status -> {
            Optional<ArtefactRecord> maybeArtefact = artefactRepo.findByUri(subject);
            if (maybeArtefact.isEmpty()) {
                log.debug("resource.changed for unknown subject (not in link index): {}", subject);
                return;
            }
            ArtefactRecord artefact = maybeArtefact.get();
            if ("DELETION".equalsIgnoreCase(kind)) {
                applyDeletion(artefact, ceId);
            } else {
                applyNewVersion(artefact, newVersion, ceId);
            }
        });
    }

    public DispositionRecord resolveDisposition(UUID dispositionId, DispositionOutcome outcome,
                                                String actor) {
        if (actor == null || actor.isBlank()) {
            throw new IllegalArgumentException("actor is required to resolve a disposition");
        }
        return txTemplate.execute(status -> {
            DispositionRecord d = dispositionRepo.findById(dispositionId)
                    .orElseThrow(() -> new NoSuchElementException(
                            "disposition not found: " + dispositionId));
            if (!d.isOpen()) {
                throw new IllegalStateException("disposition already resolved: " + dispositionId);
            }
            OffsetDateTime now = OffsetDateTime.now(clock);
            dispositionRepo.resolve(dispositionId, outcome, actor, now);

            LinkRecord existing = linkRepo.findById(d.linkId())
                    .orElseThrow(() -> new NoSuchElementException("link not found: " + d.linkId()));

            LinkRecord next;
            if (outcome == DispositionOutcome.CONFIRMED) {
                ArtefactRecord source = requireArtefact(existing.sourceNativeId());
                ArtefactRecord target = requireArtefact(existing.targetNativeId());
                next = new LinkRecord(existing.id(), existing.relation(),
                        existing.sourceNativeId(), existing.targetNativeId(),
                        source.uri(), target.uri(),
                        source.version(), target.version(),
                        LinkState.ASSERTED,
                        "disposition-confirmed:" + dispositionId, actor, now);
                linkRepo.update(next);
            } else {
                // Non-confirming outcomes don't touch the link's state. We still
                // emit an event so downstream can mirror the disposition closure.
                next = existing;
            }
            enqueueEvent(existing, next, outcome);
            return dispositionRepo.findById(dispositionId).orElseThrow();
        });
    }

    // -------------------------------------------------------- helpers --------

    private void applyNewVersion(ArtefactRecord artefact, String newVersion, String ceId) {
        String previousVersion = artefact.version();
        if (newVersion == null || newVersion.equals(previousVersion)) {
            // No visible version bump: just note the touch, no supersession.
            return;
        }
        artefactRepo.markVersion(artefact.uri(), newVersion);
        List<LinkRecord> affected = linkRepo.findByEndpointUri(artefact.uri());
        for (LinkRecord link : affected) {
            if (link.state() != LinkState.ASSERTED && link.state() != LinkState.VALIDATED) {
                continue;
            }
            boolean stale = isSourceStale(link, artefact, previousVersion)
                    || isTargetStale(link, artefact, previousVersion);
            if (!stale) {
                continue;
            }
            LinkRecord superseded = new LinkRecord(link.id(), link.relation(),
                    link.sourceNativeId(), link.targetNativeId(),
                    link.sourceUri(), link.targetUri(),
                    link.sourceVersion(), link.targetVersion(),
                    LinkState.SUPERSEDED,
                    "resource-changed:" + ceId, null,
                    OffsetDateTime.now(clock));
            linkRepo.update(superseded);
            openDisposition(superseded, previousVersion, newVersion, ceId);
            enqueueEvent(link, superseded, null);
        }
    }

    private void applyDeletion(ArtefactRecord artefact, String ceId) {
        artefactRepo.markDeleted(artefact.uri(), OffsetDateTime.now(clock));
        List<LinkRecord> affected = linkRepo.findByEndpointUri(artefact.uri());
        for (LinkRecord link : affected) {
            if (link.state() == LinkState.DANGLING) {
                continue;
            }
            LinkRecord dangling = new LinkRecord(link.id(), link.relation(),
                    link.sourceNativeId(), link.targetNativeId(),
                    link.sourceUri(), link.targetUri(),
                    link.sourceVersion(), link.targetVersion(),
                    LinkState.DANGLING,
                    "resource-deleted:" + ceId, null,
                    OffsetDateTime.now(clock));
            linkRepo.update(dangling);
            enqueueEvent(link, dangling, null);
        }
    }

    private void openDisposition(LinkRecord link, String previousVersion, String newVersion,
                                 String ceId) {
        ArtefactRecord changed = artefactRepo.findByUri(
                link.sourceUri().equals(link.targetUri())
                        ? link.sourceUri()
                        : (link.sourceVersion().equals(previousVersion)
                            ? link.sourceUri()
                            : link.targetUri()))
                .orElse(null);
        String tool = changed == null ? null : changed.tool();
        DispositionRecord d = new DispositionRecord(
                UUID.randomUUID(),
                link.id(),
                ownerResolver.resolveForTool(tool),
                previousVersion, newVersion, ceId,
                OffsetDateTime.now(clock),
                null, null, null);
        dispositionRepo.insert(d);
    }

    private static boolean isSourceStale(LinkRecord link, ArtefactRecord changed, String prev) {
        return link.sourceUri().equals(changed.uri())
                && link.sourceVersion().equals(prev);
    }

    private static boolean isTargetStale(LinkRecord link, ArtefactRecord changed, String prev) {
        return link.targetUri().equals(changed.uri())
                && link.targetVersion().equals(prev);
    }

    private void enqueueEvent(LinkRecord previous, LinkRecord next, DispositionOutcome outcome) {
        CloudEvent event = eventFactory.stateChanged(previous, next, outcome);
        outbox.enqueue(topic, next.id(), event);
    }

    private ArtefactRecord requireArtefact(String nativeId) {
        return artefactRepo.findByNativeId(nativeId)
                .orElseThrow(() -> new NoSuchElementException(
                        "artefact not found in link index: " + nativeId));
    }

    // Exposed for the fixture-driven list-all operation (dispositions carry the id).
    public List<DispositionRecord> openDispositionsForLink(String linkId) {
        return new ArrayList<>(dispositionRepo.openByLink(linkId));
    }
}
