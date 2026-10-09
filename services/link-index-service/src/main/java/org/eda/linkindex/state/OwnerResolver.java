package org.eda.linkindex.state;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.eda.fixtures.FixtureSet;
import org.eda.fixtures.model.Owners;
import org.eda.fixtures.model.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * CR-25 resolver: {@code owners.byTool} is a notification-routing hint, not a
 * strict foreign key against {@code tools.json}. Unknown-tool keys fall back to
 * {@code fallback}; the AERO-7 "CSDB" vs "CSDB S1000D" mismatch is the canonical
 * example the fixture loader already warns about.
 */
@Component
public class OwnerResolver {

    private static final Logger log = LoggerFactory.getLogger(OwnerResolver.class);

    private final Map<String, String> byTool;
    private final String fallback;

    @Autowired
    public OwnerResolver(@Value("${eda.fixtures.scenario}") String scenario) {
        FixtureSet fixtures = FixtureSet.load(scenario);
        Owners owners = fixtures.owners();
        this.byTool = owners.byTool();
        this.fallback = owners.fallback();
        Set<String> toolNames = fixtures.tools().stream()
                .map(Tool::name).collect(Collectors.toSet());
        for (String key : byTool.keySet()) {
            if (!toolNames.contains(key)) {
                log.warn("owners.byTool key '{}' does not match any tool in scenario '{}'; "
                        + "notifications for this tool will fall back to '{}'", key, scenario, fallback);
            }
        }
    }

    public OwnerResolver(Owners owners) {
        this.byTool = owners.byTool();
        this.fallback = owners.fallback();
    }

    public String resolveForTool(String tool) {
        if (tool == null) {
            return fallback;
        }
        return byTool.getOrDefault(tool, fallback);
    }

    public String fallback() {
        return fallback;
    }
}
