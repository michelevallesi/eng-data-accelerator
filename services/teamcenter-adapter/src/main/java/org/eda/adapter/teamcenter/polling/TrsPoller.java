package org.eda.adapter.teamcenter.polling;

import io.cloudevents.CloudEvent;
import java.util.List;
import java.util.UUID;
import org.apache.jena.shacl.ValidationReport;
import org.eda.adapter.teamcenter.events.CloudEventFactory;
import org.eda.adapter.teamcenter.events.DlqPublisher;
import org.eda.adapter.teamcenter.events.KafkaPublisher;
import org.eda.adapter.teamcenter.persistence.CursorRepository;
import org.eda.adapter.teamcenter.persistence.ProcessedEventsRepository;
import org.eda.adapter.teamcenter.persistence.ProcessedEventsRepository.Status;
import org.eda.adapter.teamcenter.teamcenter.TeamcenterClient;
import org.eda.adapter.teamcenter.teamcenter.TeamcenterResource;
import org.eda.adapter.teamcenter.teamcenter.TeamcenterTrsEvent;
import org.eda.adapter.teamcenter.teamcenter.TeamcenterUnavailableException;
import org.eda.adapter.teamcenter.validation.ShaclValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * TRS pull loop for the Teamcenter adapter. Mirrors the Polarion adapter's
 * contract: fail-fast on the first unavailable fetch, no partial batches
 * committed (FR-A10). Each event's outbox write + {@code tc_processed_events}
 * record happens in one DB transaction (ADR-0016).
 */
@Component
public class TrsPoller {

    private static final Logger log = LoggerFactory.getLogger(TrsPoller.class);

    private final TeamcenterClient client;
    private final CursorRepository cursorRepo;
    private final ProcessedEventsRepository processedRepo;
    private final ShaclValidator validator;
    private final CloudEventFactory eventFactory;
    private final KafkaPublisher kafkaPublisher;
    private final DlqPublisher dlqPublisher;
    private final TransactionTemplate txTemplate;
    private final String source;

    public TrsPoller(TeamcenterClient client,
                     CursorRepository cursorRepo,
                     ProcessedEventsRepository processedRepo,
                     ShaclValidator validator,
                     CloudEventFactory eventFactory,
                     KafkaPublisher kafkaPublisher,
                     DlqPublisher dlqPublisher,
                     PlatformTransactionManager txManager) {
        this.client = client;
        this.cursorRepo = cursorRepo;
        this.processedRepo = processedRepo;
        this.validator = validator;
        this.eventFactory = eventFactory;
        this.kafkaPublisher = kafkaPublisher;
        this.dlqPublisher = dlqPublisher;
        this.txTemplate = new TransactionTemplate(txManager);
        this.source = eventFactory.source();
    }

    @Scheduled(
            fixedDelayString = "${eda.teamcenter.poll-interval-ms}",
            initialDelayString = "${eda.teamcenter.initial-delay-ms:1000}")
    public void pollScheduled() {
        try {
            pollOnce();
        } catch (TeamcenterUnavailableException e) {
            log.warn("Teamcenter unavailable; readiness probe will reflect DOWN: {}", e.getMessage());
        }
    }

    /** Test seam: run exactly one poll pass synchronously. */
    public void pollOnce() {
        long cursor = cursorRepo.read(source);
        List<TeamcenterTrsEvent> events = client.fetchChangesSince(cursor);

        long highestProcessed = cursor;
        for (TeamcenterTrsEvent e : events) {
            if (processedRepo.isProcessed(source, e.order())) {
                highestProcessed = Math.max(highestProcessed, e.order());
                continue;
            }
            txTemplate.executeWithoutResult(status -> processOne(e));
            highestProcessed = Math.max(highestProcessed, e.order());
        }

        if (highestProcessed > cursor) {
            cursorRepo.write(source, highestProcessed);
        }
    }

    void processOne(TeamcenterTrsEvent event) {
        if (event.isDeletion()) {
            CloudEvent ce = eventFactory.resourceDeleted(event);
            kafkaPublisher.publish(event.resourceUri(), ce);
            processedRepo.record(source, event.order(), Status.PUBLISHED, UUID.fromString(ce.getId()));
            return;
        }

        TeamcenterResource resource = client.fetchByUri(event.resourceUri());
        ValidationReport report = validator.validate(resource.model());
        if (!report.conforms()) {
            String violation = validator.firstViolation(report);
            CloudEvent ce = dlqPublisher.publish(event, resource, violation);
            processedRepo.record(source, event.order(), Status.DLQ, UUID.fromString(ce.getId()));
            return;
        }

        CloudEvent ce = eventFactory.resourceChanged(event, resource);
        kafkaPublisher.publish(event.resourceUri(), ce);
        processedRepo.record(source, event.order(), Status.PUBLISHED, UUID.fromString(ce.getId()));
    }
}
