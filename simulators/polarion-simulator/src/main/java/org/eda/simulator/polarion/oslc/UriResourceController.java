package org.eda.simulator.polarion.oslc;

import java.util.Optional;
import org.apache.jena.rdf.model.Model;
import org.eda.simulator.polarion.state.InMemoryPolarionState;
import org.eda.simulator.polarion.state.Requirement;
import org.eda.simulator.polarion.state.TestCase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Serves resources at their opaque URI path (OSLC-correct dereferencing). The
 * simulator keeps the same per-nativeId endpoints for human readability and adds
 * this URI-based route for OSLC clients that only hold the URI.
 */
@RestController
public class UriResourceController {

    private final InMemoryPolarionState state;
    private final String baseDomain;
    private final String namespace;

    public UriResourceController(InMemoryPolarionState state,
                                 @Value("${eda.base-domain}") String baseDomain) {
        this.state = state;
        this.baseDomain = stripTrailingSlash(baseDomain);
        this.namespace = "/r/pol/";
    }

    @GetMapping("/r/pol/{uuid}")
    public Model getByUri(@PathVariable("uuid") String uuid) {
        String uri = baseDomain + namespace + uuid;

        Optional<Requirement> req = state.findRequirementByUri(uri);
        if (req.isPresent()) {
            return OslcModels.requirement(req.get(), baseDomain);
        }
        Optional<TestCase> tc = state.findTestCaseByUri(uri);
        if (tc.isPresent()) {
            return OslcModels.testCase(tc.get(), baseDomain);
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "resource not found: " + uri);
    }

    private static String stripTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
