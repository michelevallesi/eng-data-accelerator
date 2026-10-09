package org.eda.adapter.polarion.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.shacl.ValidationReport;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.Test;

class ShaclValidatorTest {

    private static final String REQUIREMENT_CLASS = "http://open-services.net/ns/rm#Requirement";
    private static final String REQ_019_URI =
            "https://eda.example.org/r/pol/7d33b7bc-90e1-461c-97c1-8845d01a68d8";

    private final ShaclValidator validator = new ShaclValidator();

    @Test
    void FR_A08_conformant_requirement_passes() {
        Model m = ModelFactory.createDefaultModel();
        Resource r = m.createResource(REQ_019_URI);
        r.addProperty(RDF.type, m.createResource(REQUIREMENT_CLASS));
        r.addProperty(DCTerms.identifier, "REQ-019");

        ValidationReport report = validator.validate(m);
        assertThat(report.conforms()).isTrue();
    }

    @Test
    void FR_A08_requirement_without_identifier_fails_and_reports_the_missing_property() {
        Model m = ModelFactory.createDefaultModel();
        Resource r = m.createResource(REQ_019_URI);
        r.addProperty(RDF.type, m.createResource(REQUIREMENT_CLASS));
        // intentionally no dcterms:identifier

        ValidationReport report = validator.validate(m);
        assertThat(report.conforms()).isFalse();

        String violation = validator.firstViolation(report);
        assertThat(violation).contains("dcterms:identifier").describedAs(violation);
    }

    @Test
    void non_requirement_resources_are_not_targeted_by_the_shape() {
        Model m = ModelFactory.createDefaultModel();
        Resource r = m.createResource("https://eda.example.org/r/pol/some-test-case");
        r.addProperty(RDF.type, m.createResource("http://open-services.net/ns/qm#TestCase"));
        // test case without dcterms:identifier -- not covered by this shape yet
        assertThat(validator.validate(m).conforms()).isTrue();
    }
}
