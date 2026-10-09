package org.eda.resolver.domain;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.eda.fixtures.FixtureSet;
import org.eda.fixtures.model.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Loads tools from {@code fixtures/<name>/tools.json} and exposes namespace/deep-link
 * lookups. For S02 this stands in for the production tool registration surface
 * (CR-01, future story).
 */
@Component
public class FixturesToolRegistry implements ToolRegistry {

    private final Map<String, Tool> byName = new HashMap<>();
    private final Map<String, Tool> byNamespace = new HashMap<>();

    public FixturesToolRegistry(@Value("${eda.fixtures.seed:}") String fixtureName) {
        if (fixtureName != null && !fixtureName.isBlank()) {
            FixtureSet f = FixtureSet.load(fixtureName);
            for (Tool t : f.tools()) {
                byName.put(t.name(), t);
                byNamespace.put(normalise(t.uriNamespace()), t);
            }
        }
    }

    @Override
    public Optional<String> namespaceOf(String toolName) {
        Tool t = byName.get(toolName);
        return Optional.ofNullable(t).map(Tool::uriNamespace);
    }

    @Override
    public Optional<String> toolOfNamespace(String namespace) {
        Tool t = byNamespace.get(normalise(namespace));
        return Optional.ofNullable(t).map(Tool::name);
    }

    @Override
    public String deepLinkFor(String tool, String nativeId) {
        Tool t = byName.get(tool);
        if (t == null) {
            throw new ResolverExceptions.UnknownToolException(tool);
        }
        return t.uriNamespace() + "native/" + nativeId;
    }

    public boolean isEmpty() {
        return byName.isEmpty();
    }

    private static String normalise(String ns) {
        if (ns == null) {
            return "";
        }
        String n = ns.startsWith("/") ? ns : "/" + ns;
        return n.endsWith("/") ? n : n + "/";
    }
}
