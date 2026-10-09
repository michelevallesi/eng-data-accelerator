package org.eda.simulator.teamcenter.oslc;

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
import org.eda.simulator.teamcenter.state.Item;
import org.eda.simulator.teamcenter.state.TrsEvent;
import org.junit.jupiter.api.Test;

class OslcModelsTest {

    private static final String BASE = "https://eda.example.org";

    @Test
    void FR_A11_item_round_trips_through_JSON_LD_and_Turtle() {
        Item it = new Item(
                "400-1105/C",
                "https://eda.example.org/r/tc/53c7253e-fec0-44b3-8a7d-88629ebb0a07",
                "centrifugal pump body",
                "Released",
                1);
        Model source = OslcModels.item(it, BASE);

        Model fromJsonLd = roundTrip(source, Lang.JSONLD);
        Model fromTurtle = roundTrip(source, Lang.TURTLE);

        assertThat(fromJsonLd.isIsomorphicWith(source)).isTrue();
        assertThat(fromTurtle.isIsomorphicWith(source)).isTrue();
        assertThat(fromJsonLd.contains(
                ResourceFactory.createResource(it.uri()),
                DCTerms.identifier,
                "400-1105/C")).isTrue();
    }

    @Test
    void FR_A02_catalog_round_trips() {
        Model source = OslcModels.catalog(BASE);
        assertThat(roundTrip(source, Lang.JSONLD).isIsomorphicWith(source)).isTrue();
        assertThat(roundTrip(source, Lang.TURTLE).isIsomorphicWith(source)).isTrue();
    }

    @Test
    void FR_A03_trs_base_round_trips_through_JSON_LD_and_Turtle() {
        Model source = OslcModels.trsBase(
                List.of(
                        "https://eda.example.org/r/tc/53c7253e-fec0-44b3-8a7d-88629ebb0a07",
                        "https://eda.example.org/r/tc/aa8c9b2d-1b2c-4e5f-9a0b-123456789abc"),
                BASE);
        assertThat(roundTrip(source, Lang.JSONLD).isIsomorphicWith(source)).isTrue();
        assertThat(roundTrip(source, Lang.TURTLE).isIsomorphicWith(source)).isTrue();
    }

    @Test
    void FR_A03_trs_change_log_round_trips_with_mixed_event_kinds() {
        OffsetDateTime t = OffsetDateTime.parse("2026-10-07T12:00:00Z");
        Model source = OslcModels.trsChangeLog(
                List.of(
                        new TrsEvent(1, TrsEvent.Kind.CREATION,
                                "https://eda.example.org/r/tc/a", 1, t),
                        new TrsEvent(2, TrsEvent.Kind.DELETION,
                                "https://eda.example.org/r/tc/b", 2, t)),
                BASE);
        assertThat(roundTrip(source, Lang.JSONLD).isIsomorphicWith(source)).isTrue();
        assertThat(roundTrip(source, Lang.TURTLE).isIsomorphicWith(source)).isTrue();
    }

    private static Model roundTrip(Model model, Lang lang) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        RDFDataMgr.write(out, model, lang);
        Model parsed = ModelFactory.createDefaultModel();
        RDFDataMgr.read(parsed, new ByteArrayInputStream(out.toByteArray()), lang);
        return parsed;
    }
}
