package org.eda.simulator.teamcenter.state;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.eda.fixtures.FixtureSet;
import org.eda.fixtures.model.Artefact;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * In-memory Teamcenter state seeded from {@code fixtures/<scenario>}. Thread-safe.
 * Mirrors the Polarion simulator's state model but with a single resource type
 * ({@link Item}) and a {@code revise} mutation that mints a brand-new URI for the
 * next revision (satisfying the S05 AC).
 */
@Component
public class InMemoryTeamcenterState {

    private final Map<String, Item> items = new ConcurrentHashMap<>();
    /** URI → native id for O(1) dereference. */
    private final Map<String, String> uriIndex = new ConcurrentHashMap<>();
    private final List<TrsEvent> changeLog = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
    private final AtomicLong orderCounter = new AtomicLong();
    private final Clock clock;
    private final String scenario;
    private final String baseDomain;

    public InMemoryTeamcenterState(@Value("${eda.fixtures.scenario}") String scenario,
                                   @Value("${eda.base-domain}") String baseDomain,
                                   Clock clock) {
        this.scenario = scenario;
        this.baseDomain = stripTrailingSlash(baseDomain);
        this.clock = clock;
        reset();
    }

    /** Re-seed from the fixture. Test-only. */
    public synchronized void reset() {
        items.clear();
        uriIndex.clear();
        changeLog.clear();
        orderCounter.set(0);
        FixtureSet fixtures = FixtureSet.load(scenario);
        for (Artefact a : fixtures.artefacts()) {
            if (!"Teamcenter".equals(a.tool())) {
                continue;
            }
            Item item = new Item(a.nativeId(), a.uri(), a.description(), a.nativeState(), 1);
            items.put(a.nativeId(), item);
            uriIndex.put(a.uri(), a.nativeId());
        }
    }

    public Optional<Item> findItem(String nativeId) {
        return Optional.ofNullable(items.get(nativeId));
    }

    public Optional<Item> findItemByUri(String uri) {
        String nativeId = uriIndex.get(uri);
        return nativeId == null ? Optional.empty() : findItem(nativeId);
    }

    public List<Item> allItems() {
        return List.copyOf(items.values());
    }

    public List<String> currentUris() {
        return items.values().stream()
                .map(Item::uri)
                .sorted()
                .toList();
    }

    public List<TrsEvent> changesSince(long sinceOrder) {
        synchronized (changeLog) {
            return changeLog.stream().filter(e -> e.order() > sinceOrder).toList();
        }
    }

    /**
     * Create a new revision of an existing item. The new revision has a brand-new
     * opaque UUID URI (per ADR-0003 / ADR-0018 — treating each revision as its own
     * resource for the walking skeleton) and appears as a {@code trs:Creation}
     * event. Returns the new item or {@code null} if the parent is missing.
     */
    public synchronized Item revise(String parentNativeId, String newRevisionId,
                                    String newDescription) {
        Item parent = items.get(parentNativeId);
        if (parent == null) {
            return null;
        }
        String partNumber = partNumberOf(parentNativeId);
        String newNativeId = partNumber + "/" + newRevisionId;
        if (items.containsKey(newNativeId)) {
            return null;
        }
        String newUri = baseDomain + "/r/tc/" + UUID.randomUUID();
        Item created = new Item(newNativeId, newUri,
                newDescription == null ? parent.description() : newDescription,
                parent.nativeState(), 1);
        items.put(newNativeId, created);
        uriIndex.put(newUri, newNativeId);
        changeLog.add(new TrsEvent(
                orderCounter.incrementAndGet(),
                TrsEvent.Kind.CREATION,
                newUri,
                1,
                OffsetDateTime.now(clock)));
        return created;
    }

    public synchronized Item updateDescription(String nativeId, String newDescription) {
        Item existing = items.get(nativeId);
        if (existing == null) {
            return null;
        }
        Item updated = existing.withNewVersion(newDescription);
        items.put(nativeId, updated);
        changeLog.add(new TrsEvent(
                orderCounter.incrementAndGet(),
                TrsEvent.Kind.MODIFICATION,
                updated.uri(),
                updated.version(),
                OffsetDateTime.now(clock)));
        return updated;
    }

    public synchronized boolean deleteItem(String nativeId) {
        Item existing = items.remove(nativeId);
        if (existing == null) {
            return false;
        }
        uriIndex.remove(existing.uri());
        changeLog.add(new TrsEvent(
                orderCounter.incrementAndGet(),
                TrsEvent.Kind.DELETION,
                existing.uri(),
                existing.version() + 1,
                OffsetDateTime.now(clock)));
        return true;
    }

    private static String partNumberOf(String nativeId) {
        int slash = nativeId.indexOf('/');
        return slash < 0 ? nativeId : nativeId.substring(0, slash);
    }

    private static String stripTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
