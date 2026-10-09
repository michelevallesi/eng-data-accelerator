package org.eda.simulator.polarion;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.RDF;
import org.eda.simulator.polarion.oslc.OslcVocabulary;
import org.eda.simulator.polarion.state.InMemoryPolarionState;
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
class PolarionSimulatorIntegrationTest {

    private static final String REQ_019_URI =
            "https://eda.example.org/r/pol/7d33b7bc-90e1-461c-97c1-8845d01a68d8";
    private static final String TC_204_URI =
            "https://eda.example.org/r/pol/fcf31708-bbb8-470d-a341-3a6e46f7c1bf";
    private static final String TC_211_URI =
            "https://eda.example.org/r/pol/b872f211-d6fb-41e0-bb78-65e5e03b168a";

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_SO =
            new ParameterizedTypeReference<>() {};

    @Autowired TestRestTemplate rest;
    @Autowired InMemoryPolarionState state;
    @Autowired ObjectMapper mapper;
    @org.springframework.boot.test.web.server.LocalServerPort int port;

    private final HttpClient jdkClient = HttpClient.newHttpClient();

    @org.junit.jupiter.api.BeforeEach
    void resetState() {
        state.reset();
    }

    private HttpResponse<String> patch(String path, Map<String, Object> body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method("PATCH",
                        HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
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
    void FR_A01_FR_A11_requirement_resource_responds_in_JSON_LD_by_default() {
        ResponseEntity<String> response = get("/rm/requirements/REQ-019", MediaType.ALL);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType().toString()).contains("application/ld+json");

        Model m = parse(response, Lang.JSONLD);
        assertThat(m.contains(
                ResourceFactory.createResource(REQ_019_URI),
                DCTerms.identifier,
                "REQ-019")).isTrue();
        assertThat(m.contains(
                ResourceFactory.createResource(REQ_019_URI),
                DCTerms.description,
                "max operating pressure 17.2 bar")).isTrue();
    }

    @Test
    void FR_A11_requirement_resource_responds_in_Turtle_on_content_negotiation() {
        ResponseEntity<String> jsonLd = get("/rm/requirements/REQ-019",
                MediaType.valueOf("application/ld+json"));
        ResponseEntity<String> turtle = get("/rm/requirements/REQ-019",
                MediaType.valueOf("text/turtle"));

        assertThat(turtle.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(turtle.getHeaders().getContentType().toString()).contains("text/turtle");

        Model jsonLdModel = parse(jsonLd, Lang.JSONLD);
        Model turtleModel = parse(turtle, Lang.TURTLE);
        assertThat(turtleModel.isIsomorphicWith(jsonLdModel)).isTrue();
    }

    @Test
    void FR_A01_test_case_resource_follows_OSLC_QM_shape() {
        ResponseEntity<String> response = get("/qm/testcases/TC-204", MediaType.ALL);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Model m = parse(response, Lang.JSONLD);
        assertThat(m.contains(
                ResourceFactory.createResource(TC_204_URI),
                RDF.type,
                ResourceFactory.createResource(OslcVocabulary.TEST_CASE_CLASS))).isTrue();
    }

    @Test
    void FR_A02_service_provider_catalog_lists_an_RM_and_a_QM_provider() {
        ResponseEntity<String> response = get("/catalog", MediaType.ALL);

        Model m = parse(response, Lang.JSONLD);
        long providers = m.listStatements(null, RDF.type,
                ResourceFactory.createResource(OslcVocabulary.SERVICE_PROVIDER))
                .toList().size();
        assertThat(providers).isEqualTo(2);

        long catalogs = m.listStatements(null, RDF.type,
                ResourceFactory.createResource(OslcVocabulary.SERVICE_PROVIDER_CATALOG))
                .toList().size();
        assertThat(catalogs).as("catalog resource must be typed as oslc:ServiceProviderCatalog")
                .isEqualTo(1);
    }

    @Test
    void FR_A03_TRS_base_lists_every_Polarion_artefact_URI_exactly_once() {
        ResponseEntity<String> response = get("/trs/base", MediaType.ALL);

        Model m = parse(response, Lang.JSONLD);
        long contained = m.listObjectsOfProperty(
                m.createProperty(OslcVocabulary.LDP_CONTAINS)).toList().size();
        assertThat(contained).isEqualTo(state.currentUris().size());
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
    void S03_PATCH_appends_Modification_event_and_bumps_version() throws Exception {
        HttpResponse<String> patch = patch(
                "/_mutate/req/REQ-019",
                Map.of("description", "max operating pressure 21 bar"));
        assertThat(patch.statusCode()).isEqualTo(HttpStatus.NO_CONTENT.value());

        ResponseEntity<String> getRes = get("/rm/requirements/REQ-019", MediaType.ALL);
        Model m = parse(getRes, Lang.JSONLD);
        assertThat(m.contains(
                ResourceFactory.createResource(REQ_019_URI),
                DCTerms.description,
                "max operating pressure 21 bar")).isTrue();

        ResponseEntity<String> changes = get("/trs/changes", MediaType.ALL);
        Model log = parse(changes, Lang.JSONLD);
        long mods = log.listStatements(null, RDF.type,
                ResourceFactory.createResource(OslcVocabulary.MODIFICATION))
                .toList().size();
        assertThat(mods).isEqualTo(1);
    }

    @Test
    void FR_A09_DELETE_appends_Deletion_event_and_removes_resource() {
        ResponseEntity<Void> delete = rest.exchange(
                "/_mutate/tc/TC-211", HttpMethod.DELETE, null, Void.class);
        assertThat(delete.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<String> fetch = get("/qm/testcases/TC-211", MediaType.ALL);
        assertThat(fetch.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<String> changes = get("/trs/changes", MediaType.ALL);
        Model log = parse(changes, Lang.JSONLD);
        long dels = log.listStatements(null, RDF.type,
                ResourceFactory.createResource(OslcVocabulary.DELETION))
                .toList().size();
        assertThat(dels).isEqualTo(1);
        boolean refersToTc211 = log.listObjectsOfProperty(
                log.createProperty(OslcVocabulary.TRS_CHANGED))
                .toList().stream()
                .anyMatch(n -> n.isURIResource() && n.asResource().getURI().equals(TC_211_URI));
        assertThat(refersToTc211).isTrue();
    }

    @Test
    void FR_A07_X_Eda_User_header_is_captured_but_not_enforced() {
        HttpHeaders anon = new HttpHeaders();
        anon.setAccept(List.of(MediaType.APPLICATION_JSON));
        ResponseEntity<Map<String, Object>> anonResp = rest.exchange(
                "/whoami", HttpMethod.GET, new HttpEntity<>(anon), MAP_SO);
        assertThat(anonResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(anonResp.getBody()).containsEntry("user", "anonymous");

        HttpHeaders withUser = new HttpHeaders();
        withUser.setAccept(List.of(MediaType.APPLICATION_JSON));
        withUser.add("X-Eda-User", "alice");
        ResponseEntity<Map<String, Object>> aliceResp = rest.exchange(
                "/whoami", HttpMethod.GET, new HttpEntity<>(withUser), MAP_SO);
        assertThat(aliceResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(aliceResp.getBody()).containsEntry("user", "alice");
    }

    // ---- Added by review follow-up ----

    @Test
    void S03_404_on_unknown_requirement() {
        ResponseEntity<String> response = get("/rm/requirements/REQ-DOES-NOT-EXIST", MediaType.ALL);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void S03_404_on_unknown_test_case() {
        ResponseEntity<String> response = get("/qm/testcases/TC-DOES-NOT-EXIST", MediaType.ALL);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void S03_404_on_PATCH_unknown_requirement() throws Exception {
        HttpResponse<String> response = patch(
                "/_mutate/req/REQ-DOES-NOT-EXIST",
                Map.of("description", "whatever"));
        assertThat(response.statusCode()).isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    @Test
    void S03_404_on_DELETE_unknown_test_case() {
        ResponseEntity<Void> response = rest.exchange(
                "/_mutate/tc/TC-DOES-NOT-EXIST", HttpMethod.DELETE, null, Void.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void FR_A03_TRS_since_filter_returns_only_newer_events() throws Exception {
        // First mutation -> order=1
        HttpResponse<String> first = patch(
                "/_mutate/req/REQ-019",
                Map.of("description", "revised once"));
        assertThat(first.statusCode()).isEqualTo(HttpStatus.NO_CONTENT.value());
        // Second mutation -> order=2
        HttpResponse<String> second = patch(
                "/_mutate/req/REQ-024",
                Map.of("description", "revised too"));
        assertThat(second.statusCode()).isEqualTo(HttpStatus.NO_CONTENT.value());

        ResponseEntity<String> all = get("/trs/changes", MediaType.ALL);
        Model allLog = parse(all, Lang.JSONLD);
        long allEvents = allLog.listObjectsOfProperty(
                allLog.createProperty(OslcVocabulary.TRS_CHANGE)).toList().size();
        assertThat(allEvents).isEqualTo(2);

        ResponseEntity<String> onlyLatest = get("/trs/changes?since=1", MediaType.ALL);
        Model latestLog = parse(onlyLatest, Lang.JSONLD);
        long latestEvents = latestLog.listObjectsOfProperty(
                latestLog.createProperty(OslcVocabulary.TRS_CHANGE)).toList().size();
        assertThat(latestEvents).isEqualTo(1);

        ResponseEntity<String> none = get("/trs/changes?since=99", MediaType.ALL);
        Model noneLog = parse(none, Lang.JSONLD);
        long noneEvents = noneLog.listObjectsOfProperty(
                noneLog.createProperty(OslcVocabulary.TRS_CHANGE)).toList().size();
        assertThat(noneEvents).isZero();
    }

    @Test
    void FR_A03_TRS_descriptor_declares_base_and_change_log() {
        ResponseEntity<String> response = get("/trs", MediaType.ALL);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        Model m = parse(response, Lang.JSONLD);
        long typed = m.listStatements(null, RDF.type,
                ResourceFactory.createResource(OslcVocabulary.TRACKED_RESOURCE_SET))
                .toList().size();
        assertThat(typed).isEqualTo(1);

        long baseLinks = m.listObjectsOfProperty(
                m.createProperty(OslcVocabulary.TRS_BASE)).toList().size();
        long changeLogLinks = m.listObjectsOfProperty(
                m.createProperty(OslcVocabulary.TRS_CHANGE_LOG)).toList().size();
        assertThat(baseLinks).isEqualTo(1);
        assertThat(changeLogLinks).isEqualTo(1);
    }

    @Test
    void FR_A09_DELETE_requirement_appends_Deletion_and_removes_resource() {
        String reqUri = REQ_019_URI;

        ResponseEntity<Void> delete = rest.exchange(
                "/_mutate/req/REQ-019", HttpMethod.DELETE, null, Void.class);
        assertThat(delete.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<String> fetch = get("/rm/requirements/REQ-019", MediaType.ALL);
        assertThat(fetch.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<String> changes = get("/trs/changes", MediaType.ALL);
        Model log = parse(changes, Lang.JSONLD);
        long dels = log.listStatements(null, RDF.type,
                ResourceFactory.createResource(OslcVocabulary.DELETION))
                .toList().size();
        assertThat(dels).isEqualTo(1);
        boolean refersToReq019 = log.listObjectsOfProperty(
                log.createProperty(OslcVocabulary.TRS_CHANGED))
                .toList().stream()
                .anyMatch(n -> n.isURIResource() && n.asResource().getURI().equals(reqUri));
        assertThat(refersToReq019).isTrue();
    }

    @Test
    void S03_repeated_PATCH_appends_two_Modifications_and_bumps_version_twice() throws Exception {
        patch("/_mutate/req/REQ-019", Map.of("description", "revision 2"));
        patch("/_mutate/req/REQ-019", Map.of("description", "revision 3"));

        ResponseEntity<String> changes = get("/trs/changes", MediaType.ALL);
        Model log = parse(changes, Lang.JSONLD);
        long mods = log.listStatements(null, RDF.type,
                ResourceFactory.createResource(OslcVocabulary.MODIFICATION))
                .toList().size();
        assertThat(mods).isEqualTo(2);

        assertThat(state.findRequirement("REQ-019").orElseThrow().version())
                .as("two PATCHes must bump version from 1 to 3")
                .isEqualTo(3);
    }

    @Test
    void FR_A11_wildcard_subtype_accept_resolves_to_JSON_LD() {
        ResponseEntity<String> response = get("/rm/requirements/REQ-019",
                MediaType.valueOf("application/*"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType().toString()).contains("application/ld+json");
    }

    @Test
    void FR_A11_unsupported_accept_returns_406_Not_Acceptable() {
        ResponseEntity<String> response = get("/rm/requirements/REQ-019", MediaType.APPLICATION_XML);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_ACCEPTABLE);
    }

    @Test
    void S03_non_Polarion_native_ids_return_404() {
        // LC-11 is a Capella native id present in fixtures/skid400 but the simulator
        // only exposes Polarion-tool artefacts.
        ResponseEntity<String> fromRm = get("/rm/requirements/LC-11", MediaType.ALL);
        ResponseEntity<String> fromQm = get("/qm/testcases/LC-11", MediaType.ALL);

        assertThat(fromRm.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(fromQm.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ---- Added by review follow-up (S04 risk #12) ----

    @Test
    void S04_UriResourceController_resolves_requirement_by_URI_path() {
        ResponseEntity<String> response = get(
                "/r/pol/7d33b7bc-90e1-461c-97c1-8845d01a68d8", MediaType.ALL);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Model m = parse(response, Lang.JSONLD);
        assertThat(m.contains(
                ResourceFactory.createResource(REQ_019_URI),
                RDF.type,
                ResourceFactory.createResource(OslcVocabulary.REQUIREMENT_CLASS))).isTrue();
        assertThat(m.contains(
                ResourceFactory.createResource(REQ_019_URI),
                DCTerms.identifier,
                "REQ-019")).isTrue();
    }

    @Test
    void S04_UriResourceController_resolves_test_case_by_URI_path() {
        ResponseEntity<String> response = get(
                "/r/pol/fcf31708-bbb8-470d-a341-3a6e46f7c1bf", MediaType.ALL);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Model m = parse(response, Lang.JSONLD);
        assertThat(m.contains(
                ResourceFactory.createResource(TC_204_URI),
                RDF.type,
                ResourceFactory.createResource(OslcVocabulary.TEST_CASE_CLASS))).isTrue();
    }

    @Test
    void S04_UriResourceController_404_on_unknown_URI() {
        ResponseEntity<String> response = get(
                "/r/pol/00000000-0000-0000-0000-000000000000", MediaType.ALL);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
