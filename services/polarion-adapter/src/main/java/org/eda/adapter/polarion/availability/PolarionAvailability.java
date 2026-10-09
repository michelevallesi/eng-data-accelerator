package org.eda.adapter.polarion.availability;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

@Component
public class PolarionAvailability {

    public enum Status { UP, DOWN, UNKNOWN }

    public record Snapshot(Status status, String reason, OffsetDateTime observedAt) {
    }

    private final Clock clock;
    private final AtomicReference<Snapshot> ref =
            new AtomicReference<>(new Snapshot(Status.UNKNOWN, "not yet polled", null));

    public PolarionAvailability(Clock clock) {
        this.clock = clock;
    }

    public void markUp() {
        ref.set(new Snapshot(Status.UP, null, OffsetDateTime.now(clock)));
    }

    public void markDown(String reason) {
        ref.set(new Snapshot(Status.DOWN, reason, OffsetDateTime.now(clock)));
    }

    public Snapshot snapshot() {
        return ref.get();
    }
}
