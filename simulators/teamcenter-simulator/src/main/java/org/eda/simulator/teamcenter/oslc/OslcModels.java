package org.eda.simulator.teamcenter.oslc;

import java.util.List;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.RDF;
import org.eda.simulator.teamcenter.state.Item;
import org.eda.simulator.teamcenter.state.TrsEvent;

public final class OslcModels {

    private OslcModels() {
    }

    public static Model item(Item it, String baseDomain) {
        Model m = newModel();
        Resource res = m.createResource(it.uri());
        res.addProperty(RDF.type, m.createResource(OslcVocabulary.ITEM_CLASS));
        res.addProperty(DCTerms.identifier, it.nativeId());
        res.addProperty(DCTerms.description, it.description());
        res.addLiteral(prop(m, OslcVocabulary.EDA_NATIVE_STATE), it.nativeState());
        res.addLiteral(prop(m, OslcVocabulary.EDA_VERSION), it.version());
        res.addLiteral(prop(m, OslcVocabulary.EDA_TOOL), "Teamcenter");

        String[] parts = splitNativeId(it.nativeId());
        if (parts != null) {
            res.addLiteral(prop(m, OslcVocabulary.EDA_PART_NUMBER), parts[0]);
            res.addLiteral(prop(m, OslcVocabulary.EDA_REVISION), parts[1]);
        }

        res.addProperty(prop(m, OslcVocabulary.DCTERMS_SERVICE_PROVIDER),
                m.createResource(baseDomain + "/catalog/teamcenter-cm"));
        return m;
    }

    public static Model catalog(String baseDomain) {
        Model m = newModel();
        Resource catalog = m.createResource(baseDomain + "/catalog");
        catalog.addProperty(RDF.type, m.createResource(OslcVocabulary.SERVICE_PROVIDER_CATALOG));
        catalog.addProperty(DCTerms.title, "Teamcenter simulator catalog");

        Resource cmProvider = m.createResource(baseDomain + "/catalog/teamcenter-cm");
        cmProvider.addProperty(RDF.type, m.createResource(OslcVocabulary.SERVICE_PROVIDER));
        cmProvider.addProperty(DCTerms.title, "Teamcenter CM");
        cmProvider.addProperty(prop(m, OslcVocabulary.OSLC_DOMAIN),
                m.createResource(OslcVocabulary.OSLC_CM));

        catalog.addProperty(prop(m, OslcVocabulary.OSLC_SERVICE_PROVIDER), cmProvider);
        return m;
    }

    public static Model trsDescriptor(String baseDomain) {
        Model m = newModel();
        Resource trs = m.createResource(baseDomain + "/trs");
        trs.addProperty(RDF.type, m.createResource(OslcVocabulary.TRACKED_RESOURCE_SET));
        trs.addProperty(prop(m, OslcVocabulary.TRS_BASE), m.createResource(baseDomain + "/trs/base"));
        trs.addProperty(prop(m, OslcVocabulary.TRS_CHANGE_LOG),
                m.createResource(baseDomain + "/trs/changes"));
        return m;
    }

    public static Model trsBase(List<String> uris, String baseDomain) {
        Model m = newModel();
        Resource base = m.createResource(baseDomain + "/trs/base");
        base.addProperty(RDF.type, m.createResource(OslcVocabulary.BASE_CLASS));
        Property contains = prop(m, OslcVocabulary.LDP_CONTAINS);
        for (String uri : uris) {
            base.addProperty(contains, m.createResource(uri));
        }
        return m;
    }

    public static Model trsChangeLog(List<TrsEvent> events, String baseDomain) {
        Model m = newModel();
        Resource log = m.createResource(baseDomain + "/trs/changes");
        log.addProperty(RDF.type, m.createResource(OslcVocabulary.CHANGE_LOG_CLASS));
        Property change = prop(m, OslcVocabulary.TRS_CHANGE);
        Property changed = prop(m, OslcVocabulary.TRS_CHANGED);
        Property order = prop(m, OslcVocabulary.TRS_ORDER);
        for (TrsEvent e : events) {
            Resource ev = m.createResource(baseDomain + "/trs/changes#" + e.order());
            ev.addProperty(RDF.type, m.createResource(kindClass(e.kind())));
            ev.addProperty(changed, m.createResource(e.resourceUri()));
            ev.addLiteral(order, e.order());
            log.addProperty(change, ev);
        }
        return m;
    }

    private static String kindClass(TrsEvent.Kind kind) {
        return switch (kind) {
            case CREATION -> OslcVocabulary.CREATION;
            case MODIFICATION -> OslcVocabulary.MODIFICATION;
            case DELETION -> OslcVocabulary.DELETION;
        };
    }

    private static Model newModel() {
        Model m = ModelFactory.createDefaultModel();
        m.setNsPrefix("oslc", OslcVocabulary.OSLC_CORE);
        m.setNsPrefix("oslc_cm", OslcVocabulary.OSLC_CM);
        m.setNsPrefix("trs", OslcVocabulary.TRS);
        m.setNsPrefix("dcterms", OslcVocabulary.DCTERMS);
        m.setNsPrefix("ldp", OslcVocabulary.LDP);
        m.setNsPrefix("eda", OslcVocabulary.EDA);
        return m;
    }

    private static Property prop(Model m, String uri) {
        return m.createProperty(uri);
    }

    private static String[] splitNativeId(String nativeId) {
        int slash = nativeId.indexOf('/');
        if (slash < 0) {
            return null;
        }
        return new String[] { nativeId.substring(0, slash), nativeId.substring(slash + 1) };
    }
}
