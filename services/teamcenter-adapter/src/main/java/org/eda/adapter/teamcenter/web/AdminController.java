package org.eda.adapter.teamcenter.web;

import org.eda.adapter.teamcenter.events.CloudEventFactory;
import org.eda.adapter.teamcenter.persistence.CursorRepository;
import org.eda.adapter.teamcenter.persistence.ProcessedEventsRepository;
import org.eda.adapter.teamcenter.polling.TrsPoller;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
