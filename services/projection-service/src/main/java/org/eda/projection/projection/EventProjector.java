package org.eda.projection.projection;

import java.io.ByteArrayInputStream;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.RDF;
import org.eda.projection.fuseki.GraphStore;
import org.eda.projection.prov.ProvFactory;
import org.eda.projection.vocab.EdaVocab;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Projects {@code eda.resource.changed} events into Fuseki named graphs. Writes
 * are organised in three graphs per subject:
 *
 * <ul>
 *   <li>Version graph {@code <subject>#v/<edaversion>} — the JSON-LD payload for
 *       that revision (FR-M01). The subject carries
 *       {@code prov:wasGeneratedBy <urn:eda:event:<ce_id>>} so every assertion is
 *       traceable (FR-M02).</li>
 *   <li>Concept graph {@code <subject>#concept} — {@code eda:current} points to
 *       the latest version IRI, {@code eda:dataAge} tracks the source tool's
 *       {@code edasourcetime} (FR-M08), {@code prov:wasRevisionOf} links the
 *       newest version to its predecessor (FR-M05). Every concept rewrite also
 *       stamps {@code prov:wasGeneratedBy} with the event that drove it so the
 *       rewrite history is auditable (FR-M02).</li>
 *   <li>Provenance graph {@code urn:eda:prov} — one {@code prov:Activity} per
 *       processed CloudEvent id, which is also the idempotence key (FR-B04).</li>
 * </ul>
 *
 * <p>DELETION events (Kafka tombstones with a null body — see
 * {@code docs/specs/asyncapi.yaml}) are read from Kafka headers via
 * {@link IncomingEvent#from}. The deletion preserves the version-graph payload
 * (FR-M05 temporal queries) and marks the subject with {@code eda:deletedAt};
 * the concept graph drops {@code eda:current} but retains the lineage.
 */
@Component
public class EventProjector {

    private static final Logger log = LoggerFactory.getLogger(EventProjector.class);

    private final GraphStore fuseki;
    private final String provGraph;

    public EventProjector(GraphStore fuseki,
                          @Value("${eda.projection.prov-graph}") String provGraph) {
        this.fuseki = fuseki;
        this.provGraph = provGraph;
    }

    /** Idempotent entry point: returns {@code true} if the event was projected. */
    public boolean project(IncomingEvent event) {
        if (event.id() == null || event.subject() == null || event.kind() == null) {
            log.warn("skipping malformed event: id={}, subject={}, kind={}",
                    event.id(), event.subject(), event.kind());
            return false;
        }
        if (alreadyProjected(event.id())) {
            log.debug("skipping duplicate event id={}", event.id());
            return false;
        }
        if (event.isDeletion()) {
            projectDeletion(event);
        } else {
            projectChange(event);
        }
        appendActivity(event);
        return true;
    }

    private boolean alreadyProjected(String ceId) {
        String ask = String.format(
                "ASK WHERE { GRAPH <%s> { <%s> a <%s> } }",
                provGraph, EdaVocab.eventIri(ceId), EdaVocab.PROV_ACTIVITY.getURI());
        return fuseki.ask(ask);
    }

    private void projectChange(IncomingEvent event) {
        String versionGraph = EdaVocab.versionIri(event.subject(), event.edaversion());
        String conceptGraph = EdaVocab.conceptIri(event.subject());

        Model version = ModelFactory.createDefaultModel();
        if (event.jsonLdPayload() != null) {
            RDFDataMgr.read(version,
                    new ByteArrayInputStream(event.jsonLdPayload()), Lang.JSONLD);
        }
        Resource subjectRes = version.getResource(event.subject());
        subjectRes.addProperty(EdaVocab.PROV_WAS_GENERATED_BY,
                version.createResource(EdaVocab.eventIri(event.id())));

        fuseki.putGraph(versionGraph, version);
        rewriteConcept(conceptGraph, versionGraph, event);
    }

    private void projectDeletion(IncomingEvent event) {
        String versionGraph = EdaVocab.versionIri(event.subject(), event.edaversion());
        String conceptGraph = EdaVocab.conceptIri(event.subject());

        // FR-M05: preserve the version payload. Mark the subject as deleted so
        // downstream can tell "this version is historical / tombstoned" from
        // "still current".
        Model version = fuseki.fetchGraph(versionGraph);
        Resource subjectRes = version.getResource(event.subject());
        subjectRes.addProperty(EdaVocab.PROV_WAS_GENERATED_BY,
                version.createResource(EdaVocab.eventIri(event.id())));
        String dataAge = event.dataAgeLiteral();
        if (dataAge != null) {
            subjectRes.addProperty(EdaVocab.EDA_DELETED_AT, dataAge);
        }
        fuseki.putGraph(versionGraph, version);

        // Concept graph: drop eda:current (no active version), keep rdf:type
        // and prov:wasRevisionOf chain. Stamp wasGeneratedBy on the concept so
        // the rewrite is traceable to the deletion event (FR-M02).
        Model concept = fuseki.fetchGraph(conceptGraph);
        Resource conceptRes = concept.getResource(conceptGraph);
        if (!concept.contains(conceptRes, RDF.type)) {
            conceptRes.addProperty(RDF.type,
                    ResourceFactory.createResource(EdaVocab.EDA_NS + "Concept"));
        }
        concept.removeAll(conceptRes, EdaVocab.EDA_CURRENT, null);
        concept.removeAll(conceptRes, EdaVocab.EDA_DATA_AGE, null);
        if (dataAge != null) {
            conceptRes.addProperty(EdaVocab.EDA_DATA_AGE, dataAge);
        }
        conceptRes.addProperty(EdaVocab.PROV_WAS_GENERATED_BY,
                concept.createResource(EdaVocab.eventIri(event.id())));
        fuseki.putGraph(conceptGraph, concept);
    }

    private void rewriteConcept(String conceptGraph, String newVersionGraph,
                                IncomingEvent event) {
        Model concept = fuseki.fetchGraph(conceptGraph);
        Resource conceptRes = concept.getResource(conceptGraph);
        if (!concept.contains(conceptRes, RDF.type)) {
            conceptRes.addProperty(RDF.type,
                    ResourceFactory.createResource(EdaVocab.EDA_NS + "Concept"));
        }
        Resource previous = concept.getProperty(conceptRes, EdaVocab.EDA_CURRENT) == null
                ? null
                : concept.getProperty(conceptRes, EdaVocab.EDA_CURRENT).getResource();
        concept.removeAll(conceptRes, EdaVocab.EDA_CURRENT, null);
        concept.removeAll(conceptRes, EdaVocab.EDA_DATA_AGE, null);

        Resource newVersion = concept.createResource(newVersionGraph);
        conceptRes.addProperty(EdaVocab.EDA_CURRENT, newVersion);
        String dataAge = event.dataAgeLiteral();
        if (dataAge != null) {
            conceptRes.addProperty(EdaVocab.EDA_DATA_AGE, dataAge);
        }
        conceptRes.addProperty(EdaVocab.PROV_WAS_GENERATED_BY,
                concept.createResource(EdaVocab.eventIri(event.id())));
        if (previous != null && !previous.getURI().equals(newVersionGraph)) {
            newVersion.addProperty(EdaVocab.PROV_REVISION_OF, previous);
        }
        fuseki.putGraph(conceptGraph, concept);
    }

    private void appendActivity(IncomingEvent event) {
        Model activity = ProvFactory.buildActivity(event);
        fuseki.loadGraph(provGraph, activity);
    }
}
