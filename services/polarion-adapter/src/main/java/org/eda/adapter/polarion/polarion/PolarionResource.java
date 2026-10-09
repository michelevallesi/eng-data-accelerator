package org.eda.adapter.polarion.polarion;

import org.apache.jena.rdf.model.Model;

/**
 * A single Polarion resource fetched from the simulator, carrying both the parsed
 * {@link Model} and the raw JSON-LD payload that will be shipped as the CloudEvent
 * {@code data}.
 */
public record PolarionResource(
        String uri,
        Model model,
        byte[] jsonLdBytes,
        int version) {
}
