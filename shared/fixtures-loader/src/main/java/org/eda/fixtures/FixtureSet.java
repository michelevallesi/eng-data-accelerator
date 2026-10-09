package org.eda.fixtures;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.apache.jena.rdf.model.Model;
import org.eda.fixtures.model.Artefact;
import org.eda.fixtures.model.Configuration;
import org.eda.fixtures.model.Link;
import org.eda.fixtures.model.MaturityScale;
import org.eda.fixtures.model.Owners;
import org.eda.fixtures.model.Tool;

public final class FixtureSet {

    private final Path directory;
    private final List<Tool> tools;
    private final List<Artefact> artefacts;
    private final List<Link> links;
    private final List<Configuration> configurations;
    private final Map<String, String> uris;
    private final MaturityScale maturityScale;
    private final Owners owners;
    private final Model rdfModel;

    FixtureSet(Path directory, List<Tool> tools, List<Artefact> artefacts, List<Link> links,
               List<Configuration> configurations, Map<String, String> uris,
               MaturityScale maturityScale, Owners owners, Model rdfModel) {
        this.directory = directory;
        this.tools = List.copyOf(tools);
        this.artefacts = List.copyOf(artefacts);
        this.links = List.copyOf(links);
        this.configurations = List.copyOf(configurations);
        this.uris = Map.copyOf(uris);
        this.maturityScale = maturityScale;
        this.owners = owners;
        this.rdfModel = rdfModel;
    }

    public static FixtureSet load(String name) {
        return new FixtureLoader().loadByName(name);
    }

    public static FixtureSet load(Path dir) {
        return new FixtureLoader().load(dir);
    }

    public Path directory() {
        return directory;
    }

    public List<Tool> tools() {
        return tools;
    }

    public List<Artefact> artefacts() {
        return artefacts;
    }

    public List<Link> links() {
        return links;
    }

    public List<Configuration> configurations() {
        return configurations;
    }

    public Map<String, String> uris() {
        return uris;
    }

    public MaturityScale maturityScale() {
        return maturityScale;
    }

    public Owners owners() {
        return owners;
    }

    public Model rdfModel() {
        return rdfModel;
    }
}
