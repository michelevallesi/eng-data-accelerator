package org.eda.simulator.polarion.oslc;

import org.apache.jena.rdf.model.Model;
import org.eda.simulator.polarion.state.InMemoryPolarionState;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/trs")
public class TrsController {

    private final InMemoryPolarionState state;
    private final String baseDomain;

    public TrsController(InMemoryPolarionState state,
                         @Value("${eda.base-domain}") String baseDomain) {
        this.state = state;
        this.baseDomain = stripTrailingSlash(baseDomain);
    }

    @GetMapping
    public Model descriptor() {
        return OslcModels.trsDescriptor(baseDomain);
    }

    @GetMapping("/base")
    public Model base() {
        return OslcModels.trsBase(state.currentUris(), baseDomain);
    }

    @GetMapping("/changes")
    public Model changes(@RequestParam(value = "since", defaultValue = "0") long since) {
        return OslcModels.trsChangeLog(state.changesSince(since), baseDomain);
    }

    private static String stripTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
