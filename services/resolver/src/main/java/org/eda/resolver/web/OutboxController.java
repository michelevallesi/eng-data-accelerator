package org.eda.resolver.web;

import java.util.List;
import org.eda.resolver.persistence.OutboxRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test and operator observability over the outbox table.
 * <p>Will become redundant once the Kafka relay publishes events in a later story.
 */
@RestController
@RequestMapping("/outbox")
public class OutboxController {

    private final OutboxRepository outbox;

    public OutboxController(OutboxRepository outbox) {
        this.outbox = outbox;
    }

    @GetMapping
    public List<OutboxRepository.OutboxRow> all() {
        return outbox.all();
    }
}
