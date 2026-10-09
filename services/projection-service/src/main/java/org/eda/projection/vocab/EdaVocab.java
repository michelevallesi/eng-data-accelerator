package org.eda.projection.vocab;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;

public final class EdaVocab {

    private EdaVocab() {
    }

    public static final String EDA_NS = "https://eda.example.org/ns#";
    public static final String PROV_NS = "http://www.w3.org/ns/prov#";

    public static final Property EDA_CURRENT = ResourceFactory.createProperty(EDA_NS + "current");
    public static final Property EDA_DATA_AGE = ResourceFactory.createProperty(EDA_NS + "dataAge");
    public static final Property EDA_DELETED_AT = ResourceFactory.createProperty(EDA_NS + "deletedAt");
    public static final Property EDA_VERSION = ResourceFactory.createProperty(EDA_NS + "version");
    public static final Property EDA_TRS_ORDER = ResourceFactory.createProperty(EDA_NS + "trsOrder");
    public static final Property EDA_KIND = ResourceFactory.createProperty(EDA_NS + "kind");

    public static final Resource PROV_ACTIVITY = ResourceFactory.createResource(PROV_NS + "Activity");
    public static final Property PROV_WAS_DERIVED_FROM =
            ResourceFactory.createProperty(PROV_NS + "wasDerivedFrom");
    public static final Property PROV_WAS_GENERATED_BY =
            ResourceFactory.createProperty(PROV_NS + "wasGeneratedBy");
    public static final Property PROV_REVISION_OF =
            ResourceFactory.createProperty(PROV_NS + "wasRevisionOf");
    public static final Property PROV_STARTED_AT_TIME =
            ResourceFactory.createProperty(PROV_NS + "startedAtTime");
    public static final Property PROV_WAS_ATTRIBUTED_TO =
            ResourceFactory.createProperty(PROV_NS + "wasAttributedTo");

    public static String eventIri(String cloudEventId) {
        return "urn:eda:event:" + cloudEventId;
    }

    public static String conceptIri(String subjectUri) {
        return subjectUri + "#concept";
    }

    public static String versionIri(String subjectUri, Object edaversion) {
        if (edaversion == null) {
            return subjectUri;
        }
        return subjectUri + "#v/" + edaversion;
    }
}
