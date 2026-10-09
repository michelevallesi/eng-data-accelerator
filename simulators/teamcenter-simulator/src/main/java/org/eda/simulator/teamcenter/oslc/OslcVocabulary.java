package org.eda.simulator.teamcenter.oslc;

public final class OslcVocabulary {

    private OslcVocabulary() {
    }

    public static final String OSLC_CORE = "http://open-services.net/ns/core#";
    public static final String OSLC_CM = "http://open-services.net/ns/cm#";
    public static final String TRS = "http://open-services.net/ns/core/trs#";
    public static final String DCTERMS = "http://purl.org/dc/terms/";
    public static final String LDP = "http://www.w3.org/ns/ldp#";
    public static final String EDA = "https://eda.example.org/ns#";

    public static final String ITEM_CLASS = EDA + "Item";
    public static final String CHANGE_REQUEST_CLASS = OSLC_CM + "ChangeRequest";
    public static final String SERVICE_PROVIDER_CATALOG = OSLC_CORE + "ServiceProviderCatalog";
    public static final String SERVICE_PROVIDER = OSLC_CORE + "ServiceProvider";
    public static final String TRACKED_RESOURCE_SET = TRS + "TrackedResourceSet";
    public static final String BASE_CLASS = TRS + "Base";
    public static final String CHANGE_LOG_CLASS = TRS + "ChangeLog";
    public static final String CREATION = TRS + "Creation";
    public static final String MODIFICATION = TRS + "Modification";
    public static final String DELETION = TRS + "Deletion";

    public static final String DCTERMS_IDENTIFIER = DCTERMS + "identifier";
    public static final String DCTERMS_TITLE = DCTERMS + "title";
    public static final String DCTERMS_DESCRIPTION = DCTERMS + "description";
    public static final String DCTERMS_SERVICE_PROVIDER = DCTERMS + "serviceProvider";

    public static final String EDA_NATIVE_STATE = EDA + "nativeState";
    public static final String EDA_VERSION = EDA + "version";
    public static final String EDA_TOOL = EDA + "tool";
    public static final String EDA_PART_NUMBER = EDA + "partNumber";
    public static final String EDA_REVISION = EDA + "revision";

    public static final String OSLC_DOMAIN = OSLC_CORE + "domain";
    public static final String OSLC_SERVICE_PROVIDER = OSLC_CORE + "serviceProvider";

    public static final String TRS_BASE = TRS + "base";
    public static final String TRS_CHANGE_LOG = TRS + "changeLog";
    public static final String TRS_CHANGE = TRS + "change";
    public static final String TRS_CHANGED = TRS + "changed";
    public static final String TRS_ORDER = TRS + "order";

    public static final String LDP_CONTAINS = LDP + "contains";
}
