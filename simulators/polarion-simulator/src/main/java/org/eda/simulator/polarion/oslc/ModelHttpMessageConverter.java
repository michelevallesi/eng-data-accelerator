package org.eda.simulator.polarion.oslc;

import java.io.IOException;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;

/**
 * Writes a Jena {@link Model} as JSON-LD (default) or Turtle according to the chosen
 * content type. Order of supported media types matters: JSON-LD is listed first so
 * {@code Accept: *&#47;*} resolves to {@code application/ld+json} (FR-A11).
 */
public class ModelHttpMessageConverter extends AbstractHttpMessageConverter<Model> {

    public static final MediaType JSON_LD = MediaType.valueOf("application/ld+json");
    public static final MediaType TURTLE = MediaType.valueOf("text/turtle");

    public ModelHttpMessageConverter() {
        super(JSON_LD, TURTLE);
    }

    @Override
    protected boolean supports(Class<?> clazz) {
        return Model.class.isAssignableFrom(clazz);
    }

    @Override
    protected Model readInternal(Class<? extends Model> clazz, HttpInputMessage inputMessage)
            throws HttpMessageNotReadableException {
        throw new HttpMessageNotReadableException(
                "RDF model deserialisation is not supported by the simulator", inputMessage);
    }

    @Override
    protected void writeInternal(Model model, HttpOutputMessage outputMessage) throws IOException {
        MediaType contentType = outputMessage.getHeaders().getContentType();
        Lang lang = (contentType != null && contentType.isCompatibleWith(TURTLE)) ? Lang.TURTLE : Lang.JSONLD;
        RDFDataMgr.write(outputMessage.getBody(), model, lang);
    }
}
