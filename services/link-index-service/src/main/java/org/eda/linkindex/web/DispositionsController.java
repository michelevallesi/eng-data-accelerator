package org.eda.linkindex.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.eda.linkindex.domain.DispositionOutcome;
import org.eda.linkindex.domain.DispositionRecord;
import org.eda.linkindex.persistence.DispositionRepository;
import org.eda.linkindex.state.LinkStateMachine;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dispositions")
public class DispositionsController {

    private final DispositionRepository dispositions;
    private final LinkStateMachine machine;

    public DispositionsController(DispositionRepository dispositions, LinkStateMachine machine) {
        this.dispositions = dispositions;
        this.machine = machine;
    }

    @GetMapping
    public List<Map<String, Object>> list(@RequestParam(required = false) String owner,
                                          @RequestParam(required = false) Boolean open) {
        return dispositions.filter(owner, open).stream()
                .map(DispositionsController::render)
                .toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> one(@PathVariable UUID id) {
        return dispositions.findById(id)
                .map(d -> ResponseEntity.ok(render(d)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/resolve")
    public ResponseEntity<Map<String, Object>> resolve(
            @PathVariable UUID id,
            @RequestParam("outcome") String outcome,
            @RequestHeader(value = "X-Eda-User", required = false) String actor,
            @RequestBody(required = false) ResolveBody body) {
        String effectiveActor = (body != null && body.actor() != null && !body.actor().isBlank())
                ? body.actor() : actor;
        if (effectiveActor == null || effectiveActor.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error",
                    "actor is required (X-Eda-User header or body.actor)"));
        }
        DispositionOutcome o;
        try {
            o = DispositionOutcome.fromWire(outcome);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
        try {
            DispositionRecord resolved = machine.resolveDisposition(id, o, effectiveActor);
            return ResponseEntity.ok(render(resolved));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        }
    }

    static Map<String, Object> render(DispositionRecord d) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", d.id().toString());
        map.put("linkId", d.linkId());
        map.put("owner", d.owner());
        map.put("previousVersion", d.previousVersion());
        map.put("newVersion", d.newVersion());
        map.put("causeEventId", d.causeEventId());
        map.put("openedAt", d.openedAt().toString());
        map.put("resolvedAt", d.resolvedAt() == null ? null : d.resolvedAt().toString());
        map.put("outcome", d.outcome() == null ? null : d.outcome().wire());
        map.put("actor", d.actor());
        return map;
    }

    public record ResolveBody(String actor) {
    }
}
