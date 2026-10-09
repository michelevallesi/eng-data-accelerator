package org.eda.projection.web;

import java.util.Map;
import org.eda.projection.fuseki.FusekiClient;
import org.eda.projection.rebuild.RebuildService;
import org.eda.projection.vocab.EdaVocab;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
public class AdminController {

    private final RebuildService rebuild;
    private final FusekiClient fuseki;

    public AdminController(RebuildService rebuild, FusekiClient fuseki) {
        this.rebuild = rebuild;
        this.fuseki = fuseki;
    }

    @PostMapping("/rebuild")
    public ResponseEntity<Map<String, Object>> rebuild(
            @RequestParam(value = "confirm", defaultValue = "false") boolean confirm) {
        if (!confirm) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "pass ?confirm=true to acknowledge a destructive rebuild"));
        }
        RebuildService.RebuildResult result = rebuild.rebuild();
        return ResponseEntity.ok(Map.of(
                "partitionsRewound", result.partitionsRewound(),
                "totalEventsReplayed", result.totalEventsReplayed()));
    }

    @GetMapping("/projection/age")
    public ResponseEntity<Map<String, Object>> age(@RequestParam("concept") String conceptSubject) {
        String query = String.format("""
                PREFIX eda: <%s>
                SELECT ?age WHERE { GRAPH <%s> { <%s> eda:dataAge ?age } }
                """,
                EdaVocab.EDA_NS, EdaVocab.conceptIri(conceptSubject),
                EdaVocab.conceptIri(conceptSubject));
        String age = fuseki.nextSolutionAsString(fuseki.select(query), "age");
        if (age == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of(
                "concept", EdaVocab.conceptIri(conceptSubject),
                "dataAge", age));
    }
}
