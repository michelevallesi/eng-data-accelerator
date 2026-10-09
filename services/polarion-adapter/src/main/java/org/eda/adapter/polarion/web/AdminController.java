package org.eda.adapter.polarion.web;

import org.eda.adapter.polarion.events.CloudEventFactory;
import org.eda.adapter.polarion.persistence.CursorRepository;
import org.eda.adapter.polarion.persistence.ProcessedEventsRepository;
import org.eda.adapter.polarion.polling.TrsPoller;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Operator surface for the adapter. Right now only a DLQ-reprocess endpoint, which
 * closes risk #6 from the S04 review: resetting a {@code processed_events} row and
 * rewinding the cursor must be callable from a supported API, not just from tests.
 *
 * <p>Deliberately not under {@code /actuator} — the actuator surface stays
 * read-only for probes and metrics. This is {@code /admin/*} and should be gated
 * by the Spring Security story that comes with FR-A07's real OIDC.
 */
@RestController
@RequestMapping("/admin")
public class AdminController {

    private final ProcessedEventsRepository processedRepo;
    private final CursorRepository cursorRepo;
    private final TrsPoller poller;
    private final String source;

    public AdminController(ProcessedEventsRepository processedRepo,
                           CursorRepository cursorRepo,
                           TrsPoller poller,
                           CloudEventFactory eventFactory) {
        this.processedRepo = processedRepo;
        this.cursorRepo = cursorRepo;
        this.poller = poller;
        this.source = eventFactory.source();
    }

    /**
     * Marks a {@code processed_events} row as not-yet-seen and rewinds the cursor
     * past the given {@code trsOrder}, so the next poll re-pulls and re-processes
     * the TRS event. Idempotent: safe to call on a non-existent order.
     */
    @PostMapping("/dlq/reprocess")
    public ResponseEntity<Void> reprocess(@RequestParam("trsOrder") long trsOrder,
                                          @RequestParam(value = "pollNow", defaultValue = "false")
                                          boolean pollNow) {
        processedRepo.forget(source, trsOrder);
        long current = cursorRepo.read(source);
        if (trsOrder - 1 < current) {
            cursorRepo.write(source, trsOrder - 1);
        }
        if (pollNow) {
            poller.pollOnce();
        }
        return ResponseEntity.noContent().build();
    }
}
