package org.eda.simulator.teamcenter.oslc;

import org.apache.jena.rdf.model.Model;
import org.eda.simulator.teamcenter.state.InMemoryTeamcenterState;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class ItemController {

    private final InMemoryTeamcenterState state;
    private final String baseDomain;

    public ItemController(InMemoryTeamcenterState state,
                          @Value("${eda.base-domain}") String baseDomain) {
        this.state = state;
        this.baseDomain = stripTrailingSlash(baseDomain);
    }

    @GetMapping("/items/{part}/{revision}")
    public Model get(@PathVariable("part") String part,
                     @PathVariable("revision") String revision) {
        String nativeId = part + "/" + revision;
        return state.findItem(nativeId)
                .map(it -> OslcModels.item(it, baseDomain))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "item not found: " + nativeId));
    }

    private static String stripTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
