package org.eda.fixtures;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.apache.jena.rdf.model.ResourceFactory;
import org.eda.fixtures.model.Artefact;
import org.eda.fixtures.model.Link;
import org.eda.fixtures.model.Tool;
import org.junit.jupiter.api.Test;

/**
 * S01 acceptance: loads SKID-400 and AERO-7 from the real fixtures, fails loudly on
 * unknown fields or dangling references (negative paths live in
 * {@link FixtureValidationTest}).
 */
class FixtureLoaderTest {

    private static final Pattern OPAQUE_URI = Pattern.compile(
            "https://eda\\.example\\.org/r/[a-z]+/"
                    + "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    @Test
    void loads_skid400_fixture_and_counts_match_json_view() {
        FixtureSet f = FixtureSet.load("skid400");

        assertThat(f.tools()).hasSize(5);
        assertThat(f.artefacts()).hasSize(27);
        assertThat(f.links()).hasSize(18);
        assertThat(f.configurations()).hasSize(3);
        assertThat(f.uris()).hasSize(27);
        assertThat(f.owners().byTool()).hasSize(5);
    }

    @Test
    void loads_aero7_fixture() {
        FixtureSet f = FixtureSet.load("aero7");

        assertThat(f.tools()).hasSize(5);
        assertThat(f.artefacts()).hasSize(16);
        assertThat(f.uris()).hasSize(16);
        // owners.byTool uses the short name "CSDB" for the CSDB S1000D tool —
        // the loader tolerates the mismatch (CR-25 is a routing hint, not a
        // strict cross-reference).
        assertThat(f.owners().byTool()).containsKeys(
                "Polarion", "Capella", "Teamcenter", "Ansys", "CSDB");
    }

    @Test
    void AD_03_every_artefact_has_an_opaque_uri_from_its_tool_namespace() {
        FixtureSet f = FixtureSet.load("skid400");

        for (Artefact a : f.artefacts()) {
            assertThat(a.uri())
                    .as("artefact %s", a.nativeId())
                    .matches(OPAQUE_URI);
            Tool tool = f.tools().stream()
                    .filter(t -> t.name().equals(a.tool()))
                    .findFirst()
                    .orElseThrow();
            assertThat(a.uri())
                    .as("artefact %s URI must live under tool %s namespace %s",
                            a.nativeId(), tool.name(), tool.uriNamespace())
                    .contains(tool.uriNamespace());
        }
    }

    @Test
    void FR_F02_uris_json_covers_every_artefact_native_id() {
        FixtureSet f = FixtureSet.load("skid400");

        for (Artefact a : f.artefacts()) {
            assertThat(f.uris()).containsEntry(a.nativeId(), a.uri());
        }
    }

    @Test
    void FR_S05_every_link_declares_a_valid_state() {
        FixtureSet f = FixtureSet.load("skid400");

        Set<String> known = FixtureLoader.KNOWN_LINK_STATES;
        for (Link link : f.links()) {
            assertThat(known)
                    .as("link %s state", link.id())
                    .contains(link.state());
        }
    }

    @Test
    void CR_25_owners_json_declares_notification_routing_and_a_fallback() {
        // owners.byTool is a notification-routing hint (CR-25). Keys do not have to
        // match tool names exactly — the fallback owner covers anything unmatched.
        FixtureSet skid = FixtureSet.load("skid400");
        FixtureSet aero = FixtureSet.load("aero7");

        for (FixtureSet f : List.of(skid, aero)) {
            assertThat(f.owners().byTool()).isNotEmpty();
            assertThat(f.owners().fallback()).isNotBlank();
        }
    }

    @Test
    void turtle_view_link_count_matches_json_link_count() {
        FixtureSet f = FixtureSet.load("skid400");

        long rdfLinkCount = f.rdfModel()
                .listResourcesWithProperty(
                        org.apache.jena.vocabulary.RDF.type,
                        ResourceFactory.createResource("https://eda.example.org/ns#Link"))
                .toList()
                .size();

        assertThat(rdfLinkCount).isEqualTo(f.links().size());
    }
}
