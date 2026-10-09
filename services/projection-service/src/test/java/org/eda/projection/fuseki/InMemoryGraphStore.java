package org.eda.projection.fuseki;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.ResultSetFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;

/**
 * Thread-safe in-memory {@link GraphStore} backed by a transactional
 * {@link Dataset}. Tests that cover {@code EventProjector} without the Fuseki
 * container plug this in.
 */
public class InMemoryGraphStore implements GraphStore {

    private final Dataset dataset = DatasetFactory.createTxnMem();

    public Dataset dataset() {
        return dataset;
    }

    @Override
    public void putGraph(String graphIri, Model model) {
        dataset.begin(ReadWrite.WRITE);
        try {
            dataset.replaceNamedModel(graphIri, model);
            dataset.commit();
        } finally {
            dataset.end();
        }
    }

    @Override
    public void loadGraph(String graphIri, Model additions) {
        dataset.begin(ReadWrite.WRITE);
        try {
            Model target = dataset.containsNamedModel(graphIri)
                    ? dataset.getNamedModel(graphIri)
                    : ModelFactory.createDefaultModel();
            target.add(additions);
            dataset.replaceNamedModel(graphIri, target);
            dataset.commit();
        } finally {
            dataset.end();
        }
    }

    @Override
    public Model fetchGraph(String graphIri) {
        dataset.begin(ReadWrite.READ);
        try {
            if (!dataset.containsNamedModel(graphIri)) {
                return ModelFactory.createDefaultModel();
            }
            Model copy = ModelFactory.createDefaultModel();
            copy.add(dataset.getNamedModel(graphIri));
            return copy;
        } finally {
            dataset.end();
        }
    }

    @Override
    public void clearGraph(String graphIri) {
        dataset.begin(ReadWrite.WRITE);
        try {
            dataset.removeNamedModel(graphIri);
            dataset.commit();
        } finally {
            dataset.end();
        }
    }

    @Override
    public void clearAll() {
        dataset.begin(ReadWrite.WRITE);
        try {
            dataset.asDatasetGraph().clear();
            dataset.commit();
        } finally {
            dataset.end();
        }
    }

    @Override
    public boolean ask(String sparql) {
        dataset.begin(ReadWrite.READ);
        try (QueryExecution qe = QueryExecutionFactory.create(sparql, dataset)) {
            return qe.execAsk();
        } finally {
            dataset.end();
        }
    }

    @Override
    public ResultSet select(String sparql) {
        dataset.begin(ReadWrite.READ);
        try (QueryExecution qe = QueryExecutionFactory.create(sparql, dataset)) {
            return ResultSetFactory.copyResults(qe.execSelect());
        } finally {
            dataset.end();
        }
    }
}
