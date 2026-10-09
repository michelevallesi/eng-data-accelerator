package org.eda.adapter.polarion.validation;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.apache.jena.graph.Graph;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.shacl.Shapes;
import org.apache.jena.shacl.ValidationReport;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * Validates fetched resources against the SHACL shapes under
 * {@code classpath:shapes/*.ttl}. The one shape shipped in S04 is the Requirement
 * shape (dcterms:identifier required). S08 brings the rest.
 */
@Component("shaclValidator")
public class ShaclValidator {

    private final Shapes shapes;

    public ShaclValidator() {
        this.shapes = loadShapes();
    }

    public ValidationReport validate(Model dataModel) {
        return org.apache.jena.shacl.ShaclValidator.get().validate(shapes, dataModel.getGraph());
    }

    public String firstViolation(ValidationReport report) {
        return report.getEntries().stream()
                .findFirst()
                .map(e -> e.message() + " (focus=" + e.focusNode()
                        + ", path=" + e.resultPath() + ")")
                .orElse("conforming");
    }

    private static Shapes loadShapes() {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] files = resolver.getResources("classpath:shapes/*.ttl");
            if (files.length == 0) {
                throw new IllegalStateException("no SHACL shapes found under classpath:shapes/*.ttl");
            }
            Model aggregated = ModelFactory.createDefaultModel();
            for (Resource f : files) {
                try (InputStream in = f.getInputStream()) {
                    RDFDataMgr.read(aggregated, in, Lang.TURTLE);
                }
            }
            Graph g = aggregated.getGraph();
            return Shapes.parse(g);
        } catch (IOException e) {
            throw new IllegalStateException("failed to load SHACL shapes", e);
        }
    }

    public List<ShapeInfo> loadedShapes() {
        return shapes.getTargetShapes().stream()
                .map(s -> new ShapeInfo(s.getShapeNode().toString()))
                .toList();
    }

    public record ShapeInfo(String shapeNode) {
    }
}
