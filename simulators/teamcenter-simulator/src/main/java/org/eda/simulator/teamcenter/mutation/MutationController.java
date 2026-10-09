package org.eda.simulator.teamcenter.mutation;

import org.eda.simulator.teamcenter.state.InMemoryTeamcenterState;
import org.eda.simulator.teamcenter.state.Item;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test-only endpoints to drive simulator state transitions. {@code /revise} is the
 * S05 AC: creates a brand-new revision resource with a fresh URI.
 */
@RestController
@RequestMapping("/_mutate")
public class MutationController {

    private final InMemoryTeamcenterState state;

    public MutationController(InMemoryTeamcenterState state) {
        this.state = state;
    }

    @PostMapping("/revise/{part}/{revision}")
    public ResponseEntity<ReviseResponse> revise(@PathVariable("part") String part,
                                                 @PathVariable("revision") String revision,
                                                 @RequestBody(required = false) ReviseBody body) {
        String parentNativeId = part + "/" + revision;
        String newRevisionId = body != null && body.newRevision() != null
                ? body.newRevision()
                : nextRevisionLetter(revision);
        String newDescription = body == null ? null : body.description();
        Item created = state.revise(parentNativeId, newRevisionId, newDescription);
        if (created == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.status(201)
                .body(new ReviseResponse(created.nativeId(), created.uri()));
    }

    @PatchMapping("/item/{part}/{revision}")
    public ResponseEntity<Void> patch(@PathVariable("part") String part,
                                      @PathVariable("revision") String revision,
                                      @RequestBody DescriptionBody body) {
        if (body == null || body.description() == null) {
            return ResponseEntity.badRequest().build();
        }
        String nativeId = part + "/" + revision;
        return state.updateDescription(nativeId, body.description()) == null
                ? ResponseEntity.notFound().build()
                : ResponseEntity.noContent().build();
    }

    @DeleteMapping("/item/{part}/{revision}")
    public ResponseEntity<Void> delete(@PathVariable("part") String part,
                                       @PathVariable("revision") String revision) {
        String nativeId = part + "/" + revision;
        return state.deleteItem(nativeId)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    private static String nextRevisionLetter(String current) {
        if (current == null || current.isEmpty()) {
            return "B";
        }
        char last = current.charAt(current.length() - 1);
        char next = (char) (last + 1);
        if (next > 'Z') {
            next = 'Z';
        }
        return current.substring(0, current.length() - 1) + next;
    }

    public record ReviseBody(String newRevision, String description) {
    }

    public record ReviseResponse(String nativeId, String uri) {
    }

    public record DescriptionBody(String description) {
    }
}
