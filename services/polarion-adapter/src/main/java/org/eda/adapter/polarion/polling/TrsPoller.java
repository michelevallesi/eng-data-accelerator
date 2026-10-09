package org.eda.adapter.polarion.polling;

import io.cloudevents.CloudEvent;
import java.util.List;
import java.util.UUID;
import org.apache.jena.shacl.ValidationReport;
import org.eda.adapter.polarion.events.CloudEventFactory;
import org.eda.adapter.polarion.events.DlqPublisher;
import org.eda.adapter.polarion.events.KafkaPublisher;
import org.eda.adapter.polarion.persistence.CursorRepository;
import org.eda.adapter.polarion.persistence.ProcessedEventsRepository;
import org.eda.adapter.polarion.persistence.ProcessedEventsRepository.Status;
import org.eda.adapter.polarion.polarion.PolarionClient;
import org.eda.adapter.polarion.polarion.PolarionResource;
import org.eda.adapter.polarion.polarion.PolarionTrsEvent;
import org.eda.adapter.polarion.polarion.PolarionUnavailableException;
import org.eda.adapter.polarion.validation.ShaclValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * TRS pull loop. One pass per invocation. Fail-fast on the first unavailable fetch —
 * no partial batches are committed (FR-A10).
 *
 * <p>Per-event flow: skip if {@code processed_events} already has it; otherwise fetch
 * the resource (unless the event is a {@code Deletion}), validate against SHACL,
 * publish to the main topic or DLQ, then record the row. The cursor advances only
 * after the batch completes.
 */
@Component
public class TrsPoller {

    private static final Logger log = LoggerFactory.getLogger(TrsPoller.class);

    private final PolarionClient client;
    private final CursorRepository cursorRepo;
    private final ProcessedEventsRepository processedRepo;
    private final ShaclValidator validator;
    private final CloudEventFactory eventFactory;
    private final KafkaPublisher kafkaPublisher;
    private final DlqPublisher dlqPublisher;
    private final TransactionTemplate txTemplate;
    private final String source;

    public TrsPoller(PolarionClient client,
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
            fixedDelayString = "${eda.polarion.poll-interval-ms}",
            initialDelayString = "${eda.polarion.initial-delay-ms:1000}")
    public void pollScheduled() {
        try {
            pollOnce();
        } catch (PolarionUnavailableException e) {
            log.warn("Polarion unavailable; readiness probe will reflect DOWN: {}", e.getMessage());
        }
    }

    /** Test seam: run exactly one poll pass synchronously. */
    public void pollOnce() {
        long cursor = cursorRepo.read(source);
        List<PolarionTrsEvent> events = client.fetchChangesSince(cursor);

        long highestProcessed = cursor;
        for (PolarionTrsEvent e : events) {
            if (processedRepo.isProcessed(source, e.order())) {
                highestProcessed = Math.max(highestProcessed, e.order());
                continue;
            }
            // Outbox + processed_events must commit atomically (ADR-0016). The
            // fetch happens inside the tx so a slow/failing fetch also rolls back,
            // leaving no half-written state for the next poll.
            txTemplate.executeWithoutResult(status -> processOne(e));
            highestProcessed = Math.max(highestProcessed, e.order());
        }

        if (highestProcessed > cursor) {
            cursorRepo.write(source, highestProcessed);
        }
    }

    void processOne(PolarionTrsEvent event) {
        if (event.isDeletion()) {
            CloudEvent ce = eventFactory.resourceDeleted(event);
            kafkaPublisher.publish(event.resourceUri(), ce);
            processedRepo.record(source, event.order(), Status.PUBLISHED, UUID.fromString(ce.getId()));
            return;
        }

        PolarionResource resource = client.fetchByUri(event.resourceUri());
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
