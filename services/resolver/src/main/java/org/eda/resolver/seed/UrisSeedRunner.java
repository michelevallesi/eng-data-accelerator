package org.eda.resolver.seed;

import java.util.Map;
import org.eda.fixtures.FixtureSet;
import org.eda.fixtures.model.Artefact;
import org.eda.resolver.domain.ResolverService;
import org.eda.resolver.persistence.UriRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * On startup, if {@code eda.fixtures.seed} is set and the Resolver DB is empty,
 * imports every entry in {@code fixtures/<name>/uris.json} using the pre-minted URIs.
 * Idempotent: re-starting against a populated DB is a no-op.
 */
@Component
public class UrisSeedRunner implements ApplicationRunner {

    private final String fixtureName;
    private final ResolverService service;
    private final UriRepository uriRepository;

    public UrisSeedRunner(@Value("${eda.fixtures.seed:}") String fixtureName,
                          ResolverService service,
                          UriRepository uriRepository) {
        this.fixtureName = fixtureName;
        this.service = service;
        this.uriRepository = uriRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (fixtureName == null || fixtureName.isBlank()) {
            return;
        }
        if (uriRepository.count() > 0) {
            return;
        }
        FixtureSet f = FixtureSet.load(fixtureName);
        Map<String, Artefact> byNativeId = new java.util.HashMap<>();
        for (Artefact a : f.artefacts()) {
            byNativeId.put(a.nativeId(), a);
        }
        for (Map.Entry<String, String> e : f.uris().entrySet()) {
            Artefact a = byNativeId.get(e.getKey());
            if (a == null) {
                throw new IllegalStateException(
                        "fixture " + fixtureName + ": uris.json entry '" + e.getKey()
                                + "' has no matching artefact");
            }
            service.importSeeded(a.tool(), a.nativeId(), e.getValue());
        }
    }
}
