package org.eda.projection.prov;

import java.time.format.DateTimeFormatter;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.RDF;
import org.eda.projection.projection.IncomingEvent;
import org.eda.projection.vocab.EdaVocab;

/**
 * Builds PROV-O activity triples for a projected CloudEvent. Every triple that
 * lands in the dataset traces back to a {@code prov:Activity} whose IRI is
 * {@code urn:eda:event:<ce_id>} (FR-M02).
 */
public final class ProvFactory {

    private ProvFactory() {
    }

    public static Model buildActivity(IncomingEvent event) {
        Model m = ModelFactory.createDefaultModel();
        m.setNsPrefix("eda", EdaVocab.EDA_NS);
        m.setNsPrefix("prov", EdaVocab.PROV_NS);

        Resource activity = m.createResource(EdaVocab.eventIri(event.id()));
        activity.addProperty(RDF.type, EdaVocab.PROV_ACTIVITY);
        activity.addProperty(EdaVocab.PROV_WAS_ATTRIBUTED_TO,
                m.createResource(event.source()));
        if (event.time() != null) {
            activity.addProperty(EdaVocab.PROV_STARTED_AT_TIME,
                    ResourceFactory.createTypedLiteral(
                            DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(event.time()),
                            XSDDatatype.XSDdateTime));
        }
        if (event.trsOrder() != null) {
            activity.addLiteral(EdaVocab.EDA_TRS_ORDER, event.trsOrder());
        }
        if (event.kind() != null) {
            activity.addProperty(EdaVocab.EDA_KIND, event.kind());
        }
        if (event.edaversion() != null) {
            activity.addProperty(EdaVocab.EDA_VERSION, event.edaversion());
        }
        return m;
    }
}
