package org.eda.adapter.teamcenter.teamcenter;

import org.apache.jena.rdf.model.Model;

public record TeamcenterResource(
        String uri,
        Model model,
        byte[] jsonLdBytes,
        int version) {
}
