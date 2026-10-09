package org.eda.simulator.teamcenter;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.RDF;
import org.eda.simulator.teamcenter.oslc.OslcVocabulary;
import org.eda.simulator.teamcenter.state.InMemoryTeamcenterState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TeamcenterSimulatorIntegrationTest {

    private static final String UUID_1105_C = "53c7253e-fec0-44b3-8a7d-88629ebb0a07";
    private static final String URI_1105_C = "https://eda.example.org/r/tc/" + UUID_1105_C;

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_SO =
            new ParameterizedTypeReference<>() {};

    @Autowired TestRestTemplate rest;
    @Autowired InMemoryTeamcenterState state;
    @Autowired ObjectMapper mapper;
    @org.springframework.boot.test.web.server.LocalServerPort int port;

    private final HttpClient jdkClient = HttpClient.newHttpClient();

    @BeforeEach
    void resetState() {
        state.reset();
    }

    private HttpResponse<String> postJson(String path, Object body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();
        return jdkClient.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private ResponseEntity<String> get(String path, MediaType accept) {
        HttpHeaders headers = new HttpHeaders();
        if (accept != null) {
            headers.setAccept(List.of(accept));
        }
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private Model parse(ResponseEntity<String> response, Lang lang) {
        Model m = ModelFactory.createDefaultModel();
        RDFDataMgr.read(m, new ByteArrayInputStream(response.getBody().getBytes()), lang);
        return m;
    }

    @Test
    void FR_A01_item_resource_responds_in_JSON_LD_and_declares_dcterms_identifier() {
        String path = "/items/" + URLEncoder.encode("400-1105", StandardCharsets.UTF_8) + "/C";
        ResponseEntity<String> response = get(path, MediaType.ALL);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType().toString()).contains("application/ld+json");

        Model m = parse(response, Lang.JSONLD);
        assertThat(m.contains(
                ResourceFactory.createResource(URI_1105_C),
                DCTerms.identifier,
                "400-1105/C")).isTrue();
        assertThat(m.contains(
                ResourceFactory.createResource(URI_1105_C),
                RDF.type,
                ResourceFactory.createResource(OslcVocabulary.ITEM_CLASS))).isTrue();
    }

    @Test
    void FR_A11_item_responds_in_Turtle_on_content_negotiation() {
        String path = "/items/400-1105/C";
        ResponseEntity<String> jsonLd = get(path, MediaType.valueOf("application/ld+json"));
        ResponseEntity<String> turtle = get(path, MediaType.valueOf("text/turtle"));

        assertThat(turtle.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(turtle.getHeaders().getContentType().toString()).contains("text/turtle");
        assertThat(parse(turtle, Lang.TURTLE).isIsomorphicWith(parse(jsonLd, Lang.JSONLD))).isTrue();
    }

    @Test
    void FR_A02_catalog_lists_a_CM_service_provider() {
        ResponseEntity<String> response = get("/catalog", MediaType.ALL);
        Model m = parse(response, Lang.JSONLD);

        long catalogs = m.listStatements(null, RDF.type,
                ResourceFactory.createResource(OslcVocabulary.SERVICE_PROVIDER_CATALOG))
                .toList().size();
        long providers = m.listStatements(null, RDF.type,
                ResourceFactory.createResource(OslcVocabulary.SERVICE_PROVIDER))
                .toList().size();
        assertThat(catalogs).isEqualTo(1);
        assertThat(providers).isEqualTo(1);
    }

    @Test
    void FR_A03_TRS_base_lists_every_Teamcenter_item_URI() {
        ResponseEntity<String> response = get("/trs/base", MediaType.ALL);
        Model m = parse(response, Lang.JSONLD);
        long contained = m.listObjectsOfProperty(
                m.createProperty(OslcVocabulary.LDP_CONTAINS)).toList().size();
        assertThat(contained).isEqualTo(state.allItems().size());
    }

    @Test
    void FR_A03_TRS_change_log_is_initially_empty() {
        ResponseEntity<String> response = get("/trs/changes", MediaType.ALL);
        Model m = parse(response, Lang.JSONLD);
        long changes = m.listObjectsOfProperty(
                m.createProperty(OslcVocabulary.TRS_CHANGE)).toList().size();
        assertThat(changes).isZero();
    }

    @Test
    void S05_revising_an_item_mints_a_new_URI_and_appends_a_Creation_event() throws Exception {
        HttpResponse<String> response = postJson(
                "/_mutate/revise/400-1105/C",
                Map.of("newRevision", "D", "description", "centrifugal pump body (D-rev)"));
        assertThat(response.statusCode()).isEqualTo(201);

        Map<String, Object> body = mapper.readValue(response.body(),
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        String newNativeId = (String) body.get("nativeId");
        String newUri = (String) body.get("uri");
        assertThat(newNativeId).isEqualTo("400-1105/D");
        assertThat(newUri).matches("https://eda\\.example\\.org/r/tc/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

        // TRS change log now has one Creation event with the new URI.
        ResponseEntity<String> changes = get("/trs/changes", MediaType.ALL);
        Model log = parse(changes, Lang.JSONLD);
        long creations = log.listStatements(null, RDF.type,
                ResourceFactory.createResource(OslcVocabulary.CREATION))
                .toList().size();
        assertThat(creations).isEqualTo(1);
        boolean refersToNewUri = log.listObjectsOfProperty(
                log.createProperty(OslcVocabulary.TRS_CHANGED))
                .toList().stream()
                .anyMatch(n -> n.isURIResource() && n.asResource().getURI().equals(newUri));
        assertThat(refersToNewUri).isTrue();

        // The new URI dereferences to the Item at that URL path.
        ResponseEntity<String> fetch = get(URI.create(newUri).getPath(), MediaType.ALL);
        assertThat(fetch.getStatusCode()).isEqualTo(HttpStatus.OK);
        Model item = parse(fetch, Lang.JSONLD);
        assertThat(item.contains(
                ResourceFactory.createResource(newUri),
                DCTerms.identifier,
                "400-1105/D")).isTrue();
    }

    @Test
    void FR_A09_DELETE_removes_item_and_appends_Deletion_event() {
        ResponseEntity<Void> delete = rest.exchange(
                "/_mutate/item/400-1180/A", HttpMethod.DELETE, null, Void.class);
        assertThat(delete.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<String> fetch = get("/items/400-1180/A", MediaType.ALL);
        assertThat(fetch.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<String> changes = get("/trs/changes", MediaType.ALL);
        Model log = parse(changes, Lang.JSONLD);
        long dels = log.listStatements(null, RDF.type,
                ResourceFactory.createResource(OslcVocabulary.DELETION))
                .toList().size();
        assertThat(dels).isEqualTo(1);
    }

    @Test
    void UriResourceController_resolves_an_item_by_URI_path() {
        ResponseEntity<String> response = get("/r/tc/" + UUID_1105_C, MediaType.ALL);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Model m = parse(response, Lang.JSONLD);
        assertThat(m.contains(
                ResourceFactory.createResource(URI_1105_C),
                DCTerms.identifier,
                "400-1105/C")).isTrue();
    }

    @Test
    void UriResourceController_404_on_unknown_URI() {
        ResponseEntity<String> response = get(
                "/r/tc/00000000-0000-0000-0000-000000000000", MediaType.ALL);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void FR_A07_X_Eda_User_header_is_captured_but_not_enforced() {
        HttpHeaders anon = new HttpHeaders();
        anon.setAccept(List.of(MediaType.APPLICATION_JSON));
        ResponseEntity<Map<String, Object>> anonResp = rest.exchange(
                "/whoami", HttpMethod.GET, new HttpEntity<>(anon), MAP_SO);
        assertThat(anonResp.getBody()).containsEntry("user", "anonymous");

        HttpHeaders withUser = new HttpHeaders();
        withUser.setAccept(List.of(MediaType.APPLICATION_JSON));
        withUser.add("X-Eda-User", "bob");
        ResponseEntity<Map<String, Object>> bobResp = rest.exchange(
                "/whoami", HttpMethod.GET, new HttpEntity<>(withUser), MAP_SO);
        assertThat(bobResp.getBody()).containsEntry("user", "bob");
    }

    @Test
    void S05_404_on_unknown_item() {
        ResponseEntity<String> response = get("/items/9999-ZZZZ/A", MediaType.ALL);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void S05_PATCH_description_bumps_version_and_appends_Modification_event() throws Exception {
        HttpRequest patch = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/_mutate/item/400-1105/C"))
                .header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(
                        mapper.writeValueAsString(Map.of("description", "pump body rev-bump"))))
                .build();
        HttpResponse<String> patchResponse = jdkClient.send(patch, HttpResponse.BodyHandlers.ofString());
        assertThat(patchResponse.statusCode()).isEqualTo(204);

        ResponseEntity<String> fetch = get("/items/400-1105/C", MediaType.ALL);
        Model item = parse(fetch, Lang.JSONLD);
        assertThat(item.contains(
                ResourceFactory.createResource(URI_1105_C),
                DCTerms.description,
                "pump body rev-bump")).isTrue();
        assertThat(item.getResource(URI_1105_C)
                .getProperty(item.createProperty(OslcVocabulary.EDA_VERSION))
                .getLong())
                .as("PATCH must bump edaversion")
                .isEqualTo(2L);

        ResponseEntity<String> changes = get("/trs/changes", MediaType.ALL);
        Model log = parse(changes, Lang.JSONLD);
        long mods = log.listStatements(null, RDF.type,
                        ResourceFactory.createResource(OslcVocabulary.MODIFICATION))
                .toList().size();
        assertThat(mods).isEqualTo(1);
    }

    @Test
    void FR_A11_wildcard_accept_defaults_to_JSON_LD() {
        ResponseEntity<String> response = get("/items/400-1105/C", MediaType.ALL);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType().toString()).contains("application/ld+json");
    }

    @Test
    void FR_A11_unsupported_accept_returns_406() {
        ResponseEntity<String> response = get("/items/400-1105/C", MediaType.APPLICATION_XML);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_ACCEPTABLE);
    }

    @Test
    void FR_A03_TRS_change_log_since_filters_out_earlier_events() {
        state.updateDescription("400-1105/C", "first bump");
        state.updateDescription("400-1180/A", "second bump");

        ResponseEntity<String> all = get("/trs/changes", MediaType.ALL);
        ResponseEntity<String> afterOne = get("/trs/changes?since=1", MediaType.ALL);

        Model allLog = parse(all, Lang.JSONLD);
        Model filteredLog = parse(afterOne, Lang.JSONLD);
        long allCount = allLog.listObjectsOfProperty(
                allLog.createProperty(OslcVocabulary.TRS_CHANGE)).toList().size();
        long filteredCount = filteredLog.listObjectsOfProperty(
                filteredLog.createProperty(OslcVocabulary.TRS_CHANGE)).toList().size();
        assertThat(allCount).isEqualTo(2);
        assertThat(filteredCount)
                .as("?since=1 must exclude order=1 events")
                .isEqualTo(1);
    }

    @Test
    void FR_A03_TRS_descriptor_at_slash_trs_links_base_and_change_log() {
        ResponseEntity<String> response = get("/trs", MediaType.ALL);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Model m = parse(response, Lang.JSONLD);

        long trackedSets = m.listStatements(null, RDF.type,
                        ResourceFactory.createResource(OslcVocabulary.TRACKED_RESOURCE_SET))
                .toList().size();
        assertThat(trackedSets).isEqualTo(1);

        boolean linksBase = m.listObjectsOfProperty(
                        m.createProperty(OslcVocabulary.TRS_BASE)).toList().stream()
                .anyMatch(n -> n.isURIResource() && n.asResource().getURI().endsWith("/trs/base"));
        boolean linksChangeLog = m.listObjectsOfProperty(
                        m.createProperty(OslcVocabulary.TRS_CHANGE_LOG)).toList().stream()
                .anyMatch(n -> n.isURIResource() && n.asResource().getURI().endsWith("/trs/changes"));
        assertThat(linksBase).isTrue();
        assertThat(linksChangeLog).isTrue();
    }

    @Test
    void S05_non_Teamcenter_URIs_are_not_served_by_this_simulator() {
        // Routing: /r/{tool}/{uuid} is scoped to /r/tc only. A /r/pol path has
        // no handler in the Teamcenter simulator and must 404 rather than cross-
        // dereference to another tool's namespace.
        ResponseEntity<String> response = get(
                "/r/pol/" + UUID_1105_C, MediaType.ALL);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
