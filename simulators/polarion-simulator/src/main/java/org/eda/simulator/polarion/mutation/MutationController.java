package org.eda.simulator.polarion.mutation;

import org.eda.simulator.polarion.state.InMemoryPolarionState;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test-only endpoints to drive simulator state transitions from integration tests
 * and the e2e scenario (S11). Placed under {@code /_mutate/*} to signal non-production
 * intent; a {@code @Profile} gate can be added before any shared deployment.
 */
@RestController
@RequestMapping("/_mutate")
public class MutationController {

    private final InMemoryPolarionState state;

    public MutationController(InMemoryPolarionState state) {
        this.state = state;
    }

    @PatchMapping("/req/{id}")
    public ResponseEntity<Void> patchRequirement(@PathVariable("id") String id,
                                                 @RequestBody DescriptionBody body) {
        if (body == null || body.description() == null) {
            return ResponseEntity.badRequest().build();
        }
        return state.updateRequirementDescription(id, body.description()) == null
                ? ResponseEntity.notFound().build()
                : ResponseEntity.noContent().build();
    }

    @DeleteMapping("/req/{id}")
    public ResponseEntity<Void> deleteRequirement(@PathVariable("id") String id) {
        return state.deleteRequirement(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    @PatchMapping("/tc/{id}")
    public ResponseEntity<Void> patchTestCase(@PathVariable("id") String id,
                                              @RequestBody DescriptionBody body) {
        if (body == null || body.description() == null) {
            return ResponseEntity.badRequest().build();
        }
        return state.updateTestCaseDescription(id, body.description()) == null
                ? ResponseEntity.notFound().build()
                : ResponseEntity.noContent().build();
    }

    @DeleteMapping("/tc/{id}")
    public ResponseEntity<Void> deleteTestCase(@PathVariable("id") String id) {
        return state.deleteTestCase(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    public record DescriptionBody(String description) {
    }
}
