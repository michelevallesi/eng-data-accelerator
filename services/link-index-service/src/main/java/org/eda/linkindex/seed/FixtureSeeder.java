package org.eda.linkindex.seed;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;
import org.eda.fixtures.FixtureSet;
import org.eda.fixtures.model.Artefact;
import org.eda.fixtures.model.Link;
import org.eda.linkindex.domain.ArtefactRecord;
import org.eda.linkindex.domain.LinkRecord;
import org.eda.linkindex.domain.LinkState;
import org.eda.linkindex.persistence.ArtefactRepository;
import org.eda.linkindex.persistence.LinkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Seeds the Link Index from a fixture scenario on application startup when the
 * DB is empty. Seeding is NOT a rebuild path (it's not driven by events), but
 * it is deterministic: given the same fixture, the resulting rows are
 * identical. A full prod rebuild would replay the resolver's URI events plus
 * the first assertion events — see docs/open-decisions.md.
 */
@Component
public class FixtureSeeder {

    private static final Logger log = LoggerFactory.getLogger(FixtureSeeder.class);

    private final ArtefactRepository artefactRepo;
    private final LinkRepository linkRepo;
    private final TransactionTemplate txTemplate;
    private final Clock clock;
    private final String scenario;

    public FixtureSeeder(ArtefactRepository artefactRepo,
                         LinkRepository linkRepo,
                         PlatformTransactionManager txManager,
                         Clock clock,
                         @Value("${eda.fixtures.scenario}") String scenario) {
        this.artefactRepo = artefactRepo;
        this.linkRepo = linkRepo;
        this.txTemplate = new TransactionTemplate(txManager);
        this.clock = clock;
        this.scenario = scenario;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seedIfEmpty() {
        if (artefactRepo.count() > 0 || linkRepo.count() > 0) {
            log.info("link index already populated; skipping fixture seed");
            return;
        }
        seed();
    }

    public void seed() {
        FixtureSet fixtures = FixtureSet.load(scenario);
        Map<String, String> uris = fixtures.uris();
        txTemplate.executeWithoutResult(status -> {
            for (Artefact a : fixtures.artefacts()) {
                String uri = uris.get(a.nativeId());
                artefactRepo.upsert(new ArtefactRecord(
                        a.nativeId(), uri, "1", a.tool(), a.nativeState(), null));
            }
            OffsetDateTime now = OffsetDateTime.now(clock);
            for (Link link : fixtures.links()) {
                String sourceUri = uris.get(link.source());
                String targetUri = uris.get(link.target());
                if (sourceUri == null || targetUri == null) {
                    throw new IllegalStateException(
                            "fixture link references unknown artefact: " + link);
                }
                linkRepo.insert(new LinkRecord(
                        link.id(), link.relation(),
                        link.source(), link.target(),
                        sourceUri, targetUri,
                        "1", "1",
                        LinkState.fromWire(link.state()),
                        "fixture-seed", null,
                        now));
            }
            log.info("seeded link index from fixture '{}': {} artefacts, {} links",
                    scenario, fixtures.artefacts().size(), fixtures.links().size());
        });
    }
}
