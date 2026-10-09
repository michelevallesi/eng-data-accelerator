package org.eda.projection.fuseki;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdfconnection.RDFConnection;
import org.apache.jena.rdfconnection.RDFConnectionRemote;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Thin wrapper over Jena's {@link RDFConnection} to Fuseki. Isolated so tests can
 * swap in an in-memory dataset for unit coverage of {@code EventProjector}.
 */
@Component
public class FusekiClient implements GraphStore {

    private final String datasetUrl;
    private final String pingUrl;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();

    public FusekiClient(@Value("${eda.projection.fuseki.base-url}") String baseUrl,
                        @Value("${eda.projection.fuseki.dataset}") String dataset) {
        String base = stripTrailingSlash(baseUrl);
        this.datasetUrl = base + "/" + dataset;
        this.pingUrl = base + "/$/ping";
    }

    public RDFConnection open() {
        return RDFConnectionRemote.service(datasetUrl).build();
    }

    @Override
    public void putGraph(String graphIri, Model model) {
        try (RDFConnection c = open()) {
            c.put(graphIri, model);
        }
    }

    @Override
    public void loadGraph(String graphIri, Model additions) {
        try (RDFConnection c = open()) {
            c.load(graphIri, additions);
        }
    }

    @Override
    public Model fetchGraph(String graphIri) {
        try (RDFConnection c = open()) {
            Model m = c.fetch(graphIri);
            return m == null ? ModelFactory.createDefaultModel() : m;
        } catch (org.apache.jena.atlas.web.HttpException e) {
            // Fuseki returns 404 for a never-created named graph; treat it as empty
            // so EventProjector can seed the concept graph on first use.
            if (e.getStatusCode() == 404) {
                return ModelFactory.createDefaultModel();
            }
            throw e;
        }
    }

    @Override
    public void clearGraph(String graphIri) {
        try (RDFConnection c = open()) {
            c.update("DROP SILENT GRAPH <" + graphIri + ">");
        }
    }

    @Override
    public void clearAll() {
        try (RDFConnection c = open()) {
            c.update("DROP ALL");
        }
    }

    @Override
    public boolean ask(String sparql) {
        try (RDFConnection c = open();
             QueryExecution qe = c.query(sparql)) {
            return qe.execAsk();
        }
    }

    @Override
    public ResultSet select(String sparql) {
        try (RDFConnection c = open();
             QueryExecution qe = c.query(sparql)) {
            return org.apache.jena.query.ResultSetFactory.copyResults(qe.execSelect());
        }
    }

    public Dataset fetchDataset() {
        try (RDFConnection c = open()) {
            Dataset remote = c.fetchDataset();
            Dataset copy = DatasetFactory.createTxnMem();
            copy.begin(org.apache.jena.query.ReadWrite.WRITE);
            try {
                copy.asDatasetGraph().addAll(remote.asDatasetGraph());
                copy.commit();
            } finally {
                copy.end();
            }
            return copy;
        }
    }

    public boolean ping() {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(pingUrl))
                    .timeout(Duration.ofSeconds(2)).GET().build();
            HttpResponse<Void> resp = httpClient.send(req, HttpResponse.BodyHandlers.discarding());
            return resp.statusCode() >= 200 && resp.statusCode() < 300;
        } catch (Exception e) {
            return false;
        }
    }

    public String datasetUrl() {
        return datasetUrl;
    }

    public String nextSolutionAsString(ResultSet rs, String var) {
        if (!rs.hasNext()) {
            return null;
        }
        QuerySolution sol = rs.nextSolution();
        return sol.get(var) == null ? null : sol.get(var).toString();
    }

    private static String stripTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
