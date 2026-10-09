package org.eda.simulator.teamcenter.oslc;

import org.apache.jena.rdf.model.Model;
import org.eda.simulator.teamcenter.state.InMemoryTeamcenterState;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Serves items at their opaque URI path (OSLC-correct dereferencing). The adapter
 * only knows the URI from TRS events and uses this endpoint to fetch the resource.
 */
@RestController
public class UriResourceController {

    private final InMemoryTeamcenterState state;
    private final String baseDomain;

    public UriResourceController(InMemoryTeamcenterState state,
                                 @Value("${eda.base-domain}") String baseDomain) {
        this.state = state;
        this.baseDomain = stripTrailingSlash(baseDomain);
    }

    @GetMapping("/r/tc/{uuid}")
    public Model getByUri(@PathVariable("uuid") String uuid) {
        String uri = baseDomain + "/r/tc/" + uuid;
        return state.findItemByUri(uri)
                .map(it -> OslcModels.item(it, baseDomain))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "resource not found: " + uri));
    }

    private static String stripTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
