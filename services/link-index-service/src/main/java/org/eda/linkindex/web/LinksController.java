package org.eda.linkindex.web;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.eda.linkindex.domain.LinkRecord;
import org.eda.linkindex.domain.LinkState;
import org.eda.linkindex.persistence.LinkRepository;
import org.eda.linkindex.state.LinkStateMachine;
import org.springframework.beans.factory.annotation.Value;
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
@RequestMapping("/links")
public class LinksController {

    private final LinkRepository links;
    private final LinkStateMachine machine;
    private final String baseDomain;

    public LinksController(LinkRepository links, LinkStateMachine machine,
                           @Value("${eda.base-domain}") String baseDomain) {
        this.links = links;
        this.machine = machine;
        this.baseDomain = baseDomain;
    }

    @GetMapping
    public List<Map<String, Object>> list(@RequestParam(required = false) String source,
                                          @RequestParam(required = false) String target,
                                          @RequestParam(required = false) String state,
                                          @RequestParam(required = false) String relation) {
        LinkState s = state == null ? null : LinkState.fromWire(state);
        return links.filter(source, target, s, relation).stream().map(LinksController::render).toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> one(@PathVariable String id) {
        return links.findById(normaliseId(id))
                .map(l -> ResponseEntity.ok(render(l)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/inverse")
    public ResponseEntity<Map<String, Object>> inverse(@PathVariable String id) {
        return links.findById(normaliseId(id))
                .map(l -> ResponseEntity.ok(renderInverse(l)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> propose(@RequestBody ProposeBody body) {
        if (body == null || body.source() == null || body.target() == null
                || body.relation() == null) {
            return ResponseEntity.badRequest().body(Map.of("error",
                    "source, target and relation are required"));
        }
        String id = baseDomain + "/link/" + UUID.randomUUID();
        LinkRecord record = machine.propose(id, body.relation(), body.source(), body.target());
        return ResponseEntity.created(URI.create(id)).body(render(record));
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<Map<String, Object>> confirm(@PathVariable String id,
                                                       @RequestHeader(value = "X-Eda-User", required = false)
                                                       String actor,
                                                       @RequestBody(required = false) ConfirmBody body) {
        String effectiveActor = (body != null && body.actor() != null && !body.actor().isBlank())
                ? body.actor() : actor;
        if (effectiveActor == null || effectiveActor.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error",
                    "actor is required (X-Eda-User header or body.actor)"));
        }
        try {
            LinkRecord asserted = machine.confirm(normaliseId(id), effectiveActor);
            return ResponseEntity.ok(render(asserted));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        }
    }

    private String normaliseId(String id) {
        return id.startsWith("http") ? id : (baseDomain + "/link/" + id);
    }

    static Map<String, Object> render(LinkRecord l) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", l.id());
        map.put("relation", l.relation());
        map.put("source", l.sourceNativeId());
        map.put("target", l.targetNativeId());
        map.put("sourceUri", l.sourceUri());
        map.put("targetUri", l.targetUri());
        map.put("sourceVersion", l.sourceVersion());
        map.put("targetVersion", l.targetVersion());
        map.put("state", l.state().wire());
        map.put("cause", l.cause());
        map.put("actor", l.actor());
        map.put("updatedAt", l.updatedAt().toString());
        return map;
    }

    static Map<String, Object> renderInverse(LinkRecord l) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", l.id());
        map.put("relation", "inverseOf:" + l.relation());
        map.put("source", l.targetNativeId());
        map.put("target", l.sourceNativeId());
        map.put("sourceUri", l.targetUri());
        map.put("targetUri", l.sourceUri());
        map.put("sourceVersion", l.targetVersion());
        map.put("targetVersion", l.sourceVersion());
        map.put("state", l.state().wire());
        return map;
    }

    public record ProposeBody(String source, String target, String relation) {
    }

    public record ConfirmBody(String actor) {
    }
}
