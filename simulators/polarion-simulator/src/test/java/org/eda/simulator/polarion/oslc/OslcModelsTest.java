package org.eda.simulator.polarion.oslc;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.OffsetDateTime;
import java.util.List;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.DCTerms;
import org.eda.simulator.polarion.state.Requirement;
import org.eda.simulator.polarion.state.TestCase;
import org.eda.simulator.polarion.state.TrsEvent;
import org.junit.jupiter.api.Test;

class OslcModelsTest {

    private static final String BASE = "https://eda.example.org";

    @Test
    void FR_A11_requirement_round_trips_through_JSON_LD_and_Turtle() {
        Requirement r = new Requirement(
                "REQ-019",
                "https://eda.example.org/r/pol/7d33b7bc-90e1-461c-97c1-8845d01a68d8",
                "max operating pressure 17.2 bar",
                "Released",
                1);
        Model source = OslcModels.requirement(r, BASE);

        Model fromJsonLd = roundTrip(source, Lang.JSONLD);
        Model fromTurtle = roundTrip(source, Lang.TURTLE);

        assertThat(fromJsonLd.isIsomorphicWith(source)).isTrue();
        assertThat(fromTurtle.isIsomorphicWith(source)).isTrue();
        assertThat(fromJsonLd.isIsomorphicWith(fromTurtle)).isTrue();

        assertThat(fromJsonLd.contains(
                ResourceFactory.createResource(r.uri()),
                DCTerms.identifier,
                "REQ-019"))
                .isTrue();
    }

    @Test
    void FR_A01_test_case_round_trips_through_JSON_LD_and_Turtle() {
        TestCase t = new TestCase(
                "TC-204",
                "https://eda.example.org/r/pol/fcf31708-bbb8-470d-a341-3a6e46f7c1bf",
                "flow test",
                "Released",
                1);
        Model source = OslcModels.testCase(t, BASE);

        Model fromJsonLd = roundTrip(source, Lang.JSONLD);
        Model fromTurtle = roundTrip(source, Lang.TURTLE);

        assertThat(fromJsonLd.isIsomorphicWith(source)).isTrue();
        assertThat(fromTurtle.isIsomorphicWith(source)).isTrue();
    }

    @Test
    void FR_A02_catalog_round_trips() {
        Model source = OslcModels.catalog(BASE);

        Model fromJsonLd = roundTrip(source, Lang.JSONLD);
        Model fromTurtle = roundTrip(source, Lang.TURTLE);

        assertThat(fromJsonLd.isIsomorphicWith(source)).isTrue();
        assertThat(fromTurtle.isIsomorphicWith(source)).isTrue();
    }

    @Test
    void FR_A03_trs_base_round_trips_with_empty_and_populated_bases() {
        Model empty = OslcModels.trsBase(List.of(), BASE);
        Model populated = OslcModels.trsBase(
                List.of(
                        "https://eda.example.org/r/pol/a",
                        "https://eda.example.org/r/pol/b"),
                BASE);

        assertThat(roundTrip(empty, Lang.JSONLD).isIsomorphicWith(empty)).isTrue();
        assertThat(roundTrip(empty, Lang.TURTLE).isIsomorphicWith(empty)).isTrue();
        assertThat(roundTrip(populated, Lang.JSONLD).isIsomorphicWith(populated)).isTrue();
        assertThat(roundTrip(populated, Lang.TURTLE).isIsomorphicWith(populated)).isTrue();
    }

    @Test
    void FR_A03_trs_change_log_round_trips_with_mixed_event_kinds() {
        OffsetDateTime t = OffsetDateTime.parse("2026-10-01T12:00:00Z");
        Model source = OslcModels.trsChangeLog(
                List.of(
                        new TrsEvent(1, TrsEvent.Kind.MODIFICATION,
                                "https://eda.example.org/r/pol/a", 2, t),
                        new TrsEvent(2, TrsEvent.Kind.DELETION,
                                "https://eda.example.org/r/pol/b", 3, t)),
                BASE);

        Model fromJsonLd = roundTrip(source, Lang.JSONLD);
        Model fromTurtle = roundTrip(source, Lang.TURTLE);

        assertThat(fromJsonLd.isIsomorphicWith(source)).isTrue();
        assertThat(fromTurtle.isIsomorphicWith(source)).isTrue();
    }

    private static Model roundTrip(Model model, Lang lang) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        RDFDataMgr.write(out, model, lang);
        Model parsed = ModelFactory.createDefaultModel();
        RDFDataMgr.read(parsed, new ByteArrayInputStream(out.toByteArray()), lang);
        return parsed;
    }
}
