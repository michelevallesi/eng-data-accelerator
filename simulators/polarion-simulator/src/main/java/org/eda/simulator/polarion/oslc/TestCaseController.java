package org.eda.simulator.polarion.oslc;

import org.apache.jena.rdf.model.Model;
import org.eda.simulator.polarion.state.InMemoryPolarionState;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class TestCaseController {

    private final InMemoryPolarionState state;
    private final String baseDomain;

    public TestCaseController(InMemoryPolarionState state,
                              @Value("${eda.base-domain}") String baseDomain) {
        this.state = state;
        this.baseDomain = stripTrailingSlash(baseDomain);
    }

    @GetMapping("/qm/testcases/{id}")
    public Model get(@PathVariable("id") String id) {
        return state.findTestCase(id)
                .map(t -> OslcModels.testCase(t, baseDomain))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "test case not found: " + id));
    }

    private static String stripTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
