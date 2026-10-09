package org.eda.projection.fuseki;

import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;

/**
 * Minimal RDF named-graph store surface the projector needs. Isolating this
 * from the Fuseki HTTP client lets {@code EventProjectorUnitTest} swap in an
 * in-memory Jena {@code Dataset} without starting a container.
 */
public interface GraphStore {

    void putGraph(String graphIri, Model model);

    void loadGraph(String graphIri, Model additions);

    Model fetchGraph(String graphIri);

    void clearGraph(String graphIri);

    void clearAll();

    boolean ask(String sparql);

    ResultSet select(String sparql);
}
