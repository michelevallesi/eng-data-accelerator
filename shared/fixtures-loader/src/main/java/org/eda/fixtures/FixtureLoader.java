package org.eda.fixtures;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.eda.fixtures.model.Artefact;
import org.eda.fixtures.model.Configuration;
import org.eda.fixtures.model.Link;
import org.eda.fixtures.model.MaturityScale;
import org.eda.fixtures.model.Owners;
import org.eda.fixtures.model.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads a fixture directory into typed POJOs plus a Jena RDF model.
 *
 * <p>Validation is fail-loud: any unknown field, dangling reference, or unknown link
 * state throws {@link InvalidFixtureException} with the fixture directory, the file,
 * and the offending identifier.
 *
 * <p>Story S01. Fixtures are the authoritative input for walking-skeleton tests
 * (ADR-0022). No real tool is ever contacted.
 */
public final class FixtureLoader {

    private static final Logger log = LoggerFactory.getLogger(FixtureLoader.class);

    /** Known link states (PRD chapter 12, ADR-0006, ADR-0010). */
    static final Set<String> KNOWN_LINK_STATES =
            Set.of("proposed", "asserted", "validated", "superseded", "dangling");

    private final ObjectMapper mapper = strictMapper();

    public FixtureSet loadByName(String name) {
        Path root = locateFixturesRoot();
        Path dir = root.resolve(name);
        if (!Files.isDirectory(dir)) {
            throw new InvalidFixtureException(
                    "fixture directory not found: " + dir + " (searched from " + root + ")");
        }
        return load(dir);
    }

    public FixtureSet load(Path dir) {
        if (!Files.isDirectory(dir)) {
            throw new InvalidFixtureException("not a directory: " + dir);
        }
        List<Tool> tools = parseList(dir.resolve("tools.json"), new TypeReference<>() {});
        List<Artefact> artefacts = parseList(dir.resolve("artefacts.json"), new TypeReference<>() {});
        List<Link> links = parseList(dir.resolve("links.json"), new TypeReference<>() {});
        List<Configuration> configurations =
                parseList(dir.resolve("configurations.json"), new TypeReference<>() {});
        Map<String, String> uris = parseMap(dir.resolve("uris.json"));
        MaturityScale maturity = parse(dir.resolve("maturity-scale.json"), MaturityScale.class);
        Owners owners = parse(dir.resolve("owners.json"), Owners.class);

        validate(dir, tools, artefacts, links, uris, owners);

        Model rdfModel = parseTurtle(dir);
        return new FixtureSet(dir, tools, artefacts, links, configurations, uris, maturity, owners, rdfModel);
    }

    private <T> T parse(Path file, Class<T> type) {
        requireFile(file);
        try {
            return mapper.readValue(file.toFile(), type);
        } catch (IOException e) {
            throw new InvalidFixtureException(
                    "failed to parse " + file.getFileName() + " in " + file.getParent() + ": " + e.getMessage(), e);
        }
    }

    private <T> List<T> parseList(Path file, TypeReference<List<T>> type) {
        requireFile(file);
        try {
            return mapper.readValue(file.toFile(), type);
        } catch (IOException e) {
            throw new InvalidFixtureException(
                    "failed to parse " + file.getFileName() + " in " + file.getParent() + ": " + e.getMessage(), e);
        }
    }

    private Map<String, String> parseMap(Path file) {
        requireFile(file);
        try {
            return mapper.readValue(file.toFile(), new TypeReference<Map<String, String>>() {});
        } catch (IOException e) {
            throw new InvalidFixtureException(
                    "failed to parse " + file.getFileName() + " in " + file.getParent() + ": " + e.getMessage(), e);
        }
    }

    private void validate(Path dir, List<Tool> tools, List<Artefact> artefacts, List<Link> links,
                          Map<String, String> uris, Owners owners) {
        Set<String> toolNames = tools.stream().map(Tool::name).collect(Collectors.toSet());
        Set<String> nativeIds = artefacts.stream().map(Artefact::nativeId).collect(Collectors.toSet());

        for (Artefact a : artefacts) {
            if (!toolNames.contains(a.tool())) {
                throw new InvalidFixtureException(
                        "artefacts.json: artefact '" + a.nativeId() + "' references unknown tool '"
                                + a.tool() + "' (fixture " + dir + ")");
            }
            String expectedUri = uris.get(a.nativeId());
            if (expectedUri == null) {
                throw new InvalidFixtureException(
                        "uris.json: no URI mapping for artefact '" + a.nativeId()
                                + "' declared in artefacts.json (fixture " + dir + ")");
            }
            if (!expectedUri.equals(a.uri())) {
                throw new InvalidFixtureException(
                        "uris.json: URI for artefact '" + a.nativeId()
                                + "' does not match artefacts.json (fixture " + dir + ")");
            }
        }

        for (String nativeId : uris.keySet()) {
            if (!nativeIds.contains(nativeId)) {
                throw new InvalidFixtureException(
                        "uris.json: URI declared for unknown native id '" + nativeId
                                + "' (fixture " + dir + ")");
            }
        }

        for (Link link : links) {
            if (!nativeIds.contains(link.source())) {
                throw new InvalidFixtureException(
                        "links.json: dangling source reference '" + link.source()
                                + "' in link " + link.id() + " (fixture " + dir + ")");
            }
            if (!nativeIds.contains(link.target())) {
                throw new InvalidFixtureException(
                        "links.json: dangling target reference '" + link.target()
                                + "' in link " + link.id() + " (fixture " + dir + ")");
            }
            if (!KNOWN_LINK_STATES.contains(link.state())) {
                throw new InvalidFixtureException(
                        "links.json: unknown link state '" + link.state()
                                + "' in link " + link.id()
                                + "; expected one of " + KNOWN_LINK_STATES
                                + " (fixture " + dir + ")");
            }
        }

        if (owners.fallback() == null || owners.fallback().isBlank()) {
            throw new InvalidFixtureException(
                    "owners.json: 'fallback' is required (fixture " + dir + ")");
        }
        // owners.byTool is a notification-routing hint (CR-25); keys do not need to
        // match tool names exactly — fallback owner covers anything unmatched. We
        // emit a warning so operators can spot typos; a hard failure here would
        // over-constrain programme configuration (AERO-7 intentionally uses the
        // short name "CSDB" for the tool "CSDB S1000D").
        for (String toolInOwners : owners.byTool().keySet()) {
            if (!toolNames.contains(toolInOwners)) {
                log.warn(
                        "owners.json byTool key '{}' does not match any tool name in {} "
                                + "(known tools: {}). Notifications for this key will fall "
                                + "back to '{}'.",
                        toolInOwners, dir, toolNames, owners.fallback());
            }
        }
    }

    private Model parseTurtle(Path dir) {
        Path ttl = findTurtleFile(dir);
        if (ttl == null) {
            return ModelFactory.createDefaultModel();
        }
        try {
            Model model = ModelFactory.createDefaultModel();
            RDFParser.source(ttl.toUri().toString()).lang(Lang.TURTLE).parse(model);
            return model;
        } catch (RuntimeException e) {
            throw new InvalidFixtureException(
                    "failed to parse Turtle " + ttl.getFileName() + " in " + dir + ": " + e.getMessage(), e);
        }
    }

    private Path findTurtleFile(Path dir) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.ttl")) {
            for (Path p : stream) {
                return p;
            }
            return null;
        } catch (IOException e) {
            throw new InvalidFixtureException("could not scan " + dir + " for .ttl files", e);
        }
    }

    private void requireFile(Path file) {
        if (!Files.isRegularFile(file)) {
            throw new InvalidFixtureException("required fixture file missing: " + file);
        }
    }

    private static ObjectMapper strictMapper() {
        ObjectMapper m = new ObjectMapper();
        m.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
        m.configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, true);
        return m;
    }

    private static Path locateFixturesRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve("fixtures");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new InvalidFixtureException(
                "could not find a 'fixtures/' directory walking up from "
                        + Path.of("").toAbsolutePath());
    }
}
