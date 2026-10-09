package org.eda.adapter.teamcenter.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.shacl.ValidationReport;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.Test;

class ShaclValidatorTest {

    private static final String ITEM_CLASS = "https://eda.example.org/ns#Item";
    private static final String ITEM_URI =
            "https://eda.example.org/r/tc/53c7253e-fec0-44b3-8a7d-88629ebb0a07";

    private final ShaclValidator validator = new ShaclValidator();

    @Test
    void FR_A08_conformant_item_passes() {
        Model m = ModelFactory.createDefaultModel();
        Resource r = m.createResource(ITEM_URI);
        r.addProperty(RDF.type, m.createResource(ITEM_CLASS));
        r.addProperty(DCTerms.identifier, "400-1105/C");

        ValidationReport report = validator.validate(m);
        assertThat(report.conforms()).isTrue();
    }

    @Test
    void FR_A08_item_without_identifier_fails_and_reports_the_missing_property() {
        Model m = ModelFactory.createDefaultModel();
        Resource r = m.createResource(ITEM_URI);
        r.addProperty(RDF.type, m.createResource(ITEM_CLASS));

        ValidationReport report = validator.validate(m);
        assertThat(report.conforms()).isFalse();

        String violation = validator.firstViolation(report);
        assertThat(violation).contains("dcterms:identifier").describedAs(violation);
    }
}
