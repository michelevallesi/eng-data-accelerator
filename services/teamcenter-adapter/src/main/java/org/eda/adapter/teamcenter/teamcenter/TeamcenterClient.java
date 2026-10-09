package org.eda.adapter.teamcenter.teamcenter;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.RDF;
import org.eda.adapter.teamcenter.availability.TeamcenterAvailability;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Thin HTTP client over the Teamcenter simulator (S05). Mirrors the shape of the
 * Polarion adapter's client; failures mark {@link TeamcenterAvailability} DOWN so
 * the readiness probe reflects reality and the poller aborts the batch without
 * emitting partial data (FR-A10).
 */
@Component
public class TeamcenterClient {

    private static final String TRS_NS = "http://open-services.net/ns/core/trs#";
    private static final String EDA_NS = "https://eda.example.org/ns#";

    private static final String TRS_CHANGE = TRS_NS + "change";
    private static final String TRS_CHANGED = TRS_NS + "changed";
    private static final String TRS_ORDER = TRS_NS + "order";
    private static final String CREATION = TRS_NS + "Creation";
    private static final String MODIFICATION = TRS_NS + "Modification";
    private static final String DELETION = TRS_NS + "Deletion";
    private static final String EDA_VERSION = EDA_NS + "version";

    private final RestClient http;
    private final TeamcenterAvailability availability;

    public TeamcenterClient(@Value("${eda.teamcenter.base-url}") String baseUrl,
                            TeamcenterAvailability availability) {
        this.http = RestClient.builder().baseUrl(baseUrl).build();
        this.availability = availability;
    }

    public List<TeamcenterTrsEvent> fetchChangesSince(long sinceOrder) {
        byte[] body = getJsonLd("/trs/changes?since=" + sinceOrder);
        Model m = parse(body, Lang.JSONLD);

        Property changeP = m.createProperty(TRS_CHANGE);
        Property changedP = m.createProperty(TRS_CHANGED);
        Property orderP = m.createProperty(TRS_ORDER);

        return m.listObjectsOfProperty(changeP)
                .toList().stream()
                .filter(n -> n.isResource())
                .map(n -> n.asResource())
                .map(ev -> toEvent(ev, changedP, orderP))
                .sorted((a, b) -> Long.compare(a.order(), b.order()))
                .collect(Collectors.toList());
    }

    public TeamcenterResource fetchByUri(String uri) {
        String path = URI.create(uri).getPath();
        byte[] body = getJsonLd(path);
        Model m = parse(body, Lang.JSONLD);
        int version = readVersion(m, uri);
        return new TeamcenterResource(uri, m, body, version);
    }

    public void ping() {
        getJsonLd("/trs");
    }

    private byte[] getJsonLd(String path) {
        try {
            byte[] body = http.get()
                    .uri(path)
                    .header("Accept", "application/ld+json")
                    .retrieve()
                    .body(byte[].class);
            availability.markUp();
            if (body == null) {
                throw new TeamcenterUnavailableException("empty response from " + path);
            }
            return body;
        } catch (ResourceAccessException e) {
            availability.markDown("I/O error on " + path + ": " + e.getMessage());
            throw new TeamcenterUnavailableException("I/O error on " + path, e);
        } catch (RestClientResponseException e) {
            availability.markDown("HTTP " + e.getStatusCode() + " on " + path);
            throw new TeamcenterUnavailableException(
                    "HTTP " + e.getStatusCode() + " on " + path, e);
        } catch (RuntimeException e) {
            availability.markDown("unexpected error on " + path + ": " + e.getMessage());
            throw new TeamcenterUnavailableException("unexpected error on " + path, e);
        }
    }

    private static TeamcenterTrsEvent toEvent(Resource ev, Property changedP, Property orderP) {
        Statement changedStmt = ev.getProperty(changedP);
        if (changedStmt == null) {
            throw new IllegalStateException("TRS event without trs:changed: " + ev);
        }
        String uri = changedStmt.getObject().asResource().getURI();
        long order = ev.getProperty(orderP).getObject().asLiteral().getLong();
        String type = ev.getProperty(RDF.type).getObject().asResource().getURI();
        TeamcenterTrsEvent.Kind kind = switch (type) {
            case CREATION -> TeamcenterTrsEvent.Kind.CREATION;
            case MODIFICATION -> TeamcenterTrsEvent.Kind.MODIFICATION;
            case DELETION -> TeamcenterTrsEvent.Kind.DELETION;
            default -> throw new IllegalStateException("unknown TRS event type " + type);
        };
        return new TeamcenterTrsEvent(order, kind, uri);
    }

    private static int readVersion(Model m, String uri) {
        Resource r = m.getResource(uri);
        Statement s = r.getProperty(m.createProperty(EDA_VERSION));
        return s == null ? 0 : s.getObject().asLiteral().getInt();
    }

    private static Model parse(byte[] body, Lang lang) {
        Model m = ModelFactory.createDefaultModel();
        RDFDataMgr.read(m, new ByteArrayInputStream(body), lang);
        return m;
    }
}
