package org.eda.simulator.polarion.state;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import org.eda.fixtures.FixtureSet;
import org.eda.fixtures.model.Artefact;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * In-memory Polarion state seeded from {@code fixtures/<scenario>}. Thread-safe.
 */
@Component
public class InMemoryPolarionState {

    private final Map<String, Requirement> requirements = new ConcurrentHashMap<>();
    private final Map<String, TestCase> testCases = new ConcurrentHashMap<>();
    /** URI → native id (across requirements and test cases) for O(1) dereference. */
    private final Map<String, String> uriToRequirement = new ConcurrentHashMap<>();
    private final Map<String, String> uriToTestCase = new ConcurrentHashMap<>();
    private final List<TrsEvent> changeLog = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
    private final AtomicLong orderCounter = new AtomicLong();
    private final Clock clock;
    private final String scenario;

    public InMemoryPolarionState(@Value("${eda.fixtures.scenario}") String scenario, Clock clock) {
        this.clock = clock;
        this.scenario = scenario;
        reset();
    }

    /** Re-seed from the fixture. Test-only. */
    public synchronized void reset() {
        requirements.clear();
        testCases.clear();
        uriToRequirement.clear();
        uriToTestCase.clear();
        changeLog.clear();
        orderCounter.set(0);
        FixtureSet fixtures = FixtureSet.load(scenario);
        for (Artefact a : fixtures.artefacts()) {
            if (!"Polarion".equals(a.tool())) {
                continue;
            }
            switch (a.type()) {
                case "requirement" -> {
                    requirements.put(a.nativeId(),
                            new Requirement(a.nativeId(), a.uri(), a.description(), a.nativeState(), 1));
                    uriToRequirement.put(a.uri(), a.nativeId());
                }
                case "test case" -> {
                    testCases.put(a.nativeId(),
                            new TestCase(a.nativeId(), a.uri(), a.description(), a.nativeState(), 1));
                    uriToTestCase.put(a.uri(), a.nativeId());
                }
                default -> throw new IllegalStateException(
                        "Polarion fixture has unexpected artefact type '" + a.type()
                                + "' for native id " + a.nativeId());
            }
        }
    }

    public Optional<Requirement> findRequirement(String nativeId) {
        return Optional.ofNullable(requirements.get(nativeId));
    }

    public Optional<TestCase> findTestCase(String nativeId) {
        return Optional.ofNullable(testCases.get(nativeId));
    }

    public Optional<Requirement> findRequirementByUri(String uri) {
        String nativeId = uriToRequirement.get(uri);
        return nativeId == null ? Optional.empty() : findRequirement(nativeId);
    }

    public Optional<TestCase> findTestCaseByUri(String uri) {
        String nativeId = uriToTestCase.get(uri);
        return nativeId == null ? Optional.empty() : findTestCase(nativeId);
    }

    public List<String> currentUris() {
        return Stream.concat(
                        requirements.values().stream().map(Requirement::uri),
                        testCases.values().stream().map(TestCase::uri))
                .sorted()
                .toList();
    }

    public List<Requirement> allRequirements() {
        return List.copyOf(requirements.values());
    }

    public List<TestCase> allTestCases() {
        return List.copyOf(testCases.values());
    }

    public List<TrsEvent> changesSince(long sinceOrder) {
        synchronized (changeLog) {
            return changeLog.stream().filter(e -> e.order() > sinceOrder).toList();
        }
    }

    public synchronized Requirement updateRequirementDescription(String nativeId, String newDescription) {
        Requirement existing = requirements.get(nativeId);
        if (existing == null) {
            return null;
        }
        Requirement updated = existing.withDescription(newDescription);
        requirements.put(nativeId, updated);
        changeLog.add(new TrsEvent(
                orderCounter.incrementAndGet(),
                TrsEvent.Kind.MODIFICATION,
                updated.uri(),
                updated.version(),
                OffsetDateTime.now(clock)));
        return updated;
    }

    public synchronized TestCase updateTestCaseDescription(String nativeId, String newDescription) {
        TestCase existing = testCases.get(nativeId);
        if (existing == null) {
            return null;
        }
        TestCase updated = existing.withDescription(newDescription);
        testCases.put(nativeId, updated);
        changeLog.add(new TrsEvent(
                orderCounter.incrementAndGet(),
                TrsEvent.Kind.MODIFICATION,
                updated.uri(),
                updated.version(),
                OffsetDateTime.now(clock)));
        return updated;
    }

    public synchronized boolean deleteRequirement(String nativeId) {
        Requirement existing = requirements.remove(nativeId);
        if (existing == null) {
            return false;
        }
        uriToRequirement.remove(existing.uri());
        changeLog.add(new TrsEvent(
                orderCounter.incrementAndGet(),
                TrsEvent.Kind.DELETION,
                existing.uri(),
                existing.version() + 1,
                OffsetDateTime.now(clock)));
        return true;
    }

    public synchronized boolean deleteTestCase(String nativeId) {
        TestCase existing = testCases.remove(nativeId);
        if (existing == null) {
            return false;
        }
        uriToTestCase.remove(existing.uri());
        changeLog.add(new TrsEvent(
                orderCounter.incrementAndGet(),
                TrsEvent.Kind.DELETION,
                existing.uri(),
                existing.version() + 1,
                OffsetDateTime.now(clock)));
        return true;
    }
}
