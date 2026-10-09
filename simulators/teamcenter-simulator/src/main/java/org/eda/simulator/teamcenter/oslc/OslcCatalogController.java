package org.eda.simulator.teamcenter.oslc;

import org.apache.jena.rdf.model.Model;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OslcCatalogController {

    private final String baseDomain;

    public OslcCatalogController(@Value("${eda.base-domain}") String baseDomain) {
        this.baseDomain = stripTrailingSlash(baseDomain);
    }

    @GetMapping("/catalog")
    public Model catalog() {
        return OslcModels.catalog(baseDomain);
    }

    private static String stripTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
