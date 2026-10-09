package org.eda.simulator.polarion.oslc;

import java.util.List;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.RDF;
import org.eda.simulator.polarion.state.Requirement;
import org.eda.simulator.polarion.state.TestCase;
import org.eda.simulator.polarion.state.TrsEvent;

public final class OslcModels {

    private OslcModels() {
    }

    public static Model requirement(Requirement r, String baseDomain) {
        Model m = newModel();
        Resource res = m.createResource(r.uri());
        res.addProperty(RDF.type, m.createResource(OslcVocabulary.REQUIREMENT_CLASS));
        res.addProperty(DCTerms.identifier, r.nativeId());
        res.addProperty(DCTerms.description, r.description());
        res.addLiteral(prop(m, OslcVocabulary.EDA_NATIVE_STATE), r.nativeState());
        res.addLiteral(prop(m, OslcVocabulary.EDA_VERSION), r.version());
        res.addLiteral(prop(m, OslcVocabulary.EDA_TOOL), "Polarion");
        res.addProperty(prop(m, OslcVocabulary.DCTERMS_SERVICE_PROVIDER),
                m.createResource(baseDomain + "/catalog/polarion-rm"));
        return m;
    }

    public static Model testCase(TestCase t, String baseDomain) {
        Model m = newModel();
        Resource res = m.createResource(t.uri());
        res.addProperty(RDF.type, m.createResource(OslcVocabulary.TEST_CASE_CLASS));
        res.addProperty(DCTerms.identifier, t.nativeId());
        res.addProperty(DCTerms.description, t.description());
        res.addLiteral(prop(m, OslcVocabulary.EDA_NATIVE_STATE), t.nativeState());
        res.addLiteral(prop(m, OslcVocabulary.EDA_VERSION), t.version());
        res.addLiteral(prop(m, OslcVocabulary.EDA_TOOL), "Polarion");
        res.addProperty(prop(m, OslcVocabulary.DCTERMS_SERVICE_PROVIDER),
                m.createResource(baseDomain + "/catalog/polarion-qm"));
        return m;
    }

    public static Model catalog(String baseDomain) {
        Model m = newModel();
        Resource catalog = m.createResource(baseDomain + "/catalog");
        catalog.addProperty(RDF.type, m.createResource(OslcVocabulary.SERVICE_PROVIDER_CATALOG));
        catalog.addProperty(DCTerms.title, "Polarion simulator catalog");

        Resource rmProvider = m.createResource(baseDomain + "/catalog/polarion-rm");
        rmProvider.addProperty(RDF.type, m.createResource(OslcVocabulary.SERVICE_PROVIDER));
        rmProvider.addProperty(DCTerms.title, "Polarion RM");
        rmProvider.addProperty(prop(m, OslcVocabulary.OSLC_DOMAIN),
                m.createResource(OslcVocabulary.OSLC_RM));

        Resource qmProvider = m.createResource(baseDomain + "/catalog/polarion-qm");
        qmProvider.addProperty(RDF.type, m.createResource(OslcVocabulary.SERVICE_PROVIDER));
        qmProvider.addProperty(DCTerms.title, "Polarion QM");
        qmProvider.addProperty(prop(m, OslcVocabulary.OSLC_DOMAIN),
                m.createResource(OslcVocabulary.OSLC_QM));

        catalog.addProperty(prop(m, OslcVocabulary.OSLC_SERVICE_PROVIDER), rmProvider);
        catalog.addProperty(prop(m, OslcVocabulary.OSLC_SERVICE_PROVIDER), qmProvider);
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
        m.setNsPrefix("oslc_rm", OslcVocabulary.OSLC_RM);
        m.setNsPrefix("oslc_qm", OslcVocabulary.OSLC_QM);
        m.setNsPrefix("trs", OslcVocabulary.TRS);
        m.setNsPrefix("dcterms", OslcVocabulary.DCTERMS);
        m.setNsPrefix("ldp", OslcVocabulary.LDP);
        m.setNsPrefix("eda", OslcVocabulary.EDA);
        return m;
    }

    private static Property prop(Model m, String uri) {
        return m.createProperty(uri);
    }
}
