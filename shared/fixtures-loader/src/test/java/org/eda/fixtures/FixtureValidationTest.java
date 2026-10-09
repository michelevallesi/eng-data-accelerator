package org.eda.fixtures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

/**
 * S01 acceptance: fails loudly on unknown fields or dangling references. Each case
 * loads a minimal broken fixture from {@code src/test/resources/broken-fixtures/<case>}.
 */
class FixtureValidationTest {

    private Path brokenCase(String name) {
        URL url = getClass().getClassLoader().getResource("broken-fixtures/" + name);
        if (url == null) {
            throw new IllegalStateException("missing test resource broken-fixtures/" + name);
        }
        try {
            return Paths.get(url.toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void fails_loudly_on_unknown_field_in_artefacts_json() {
        Path dir = brokenCase("unknown-field");

        assertThatThrownBy(() -> FixtureSet.load(dir))
                .isInstanceOf(InvalidFixtureException.class)
                .hasMessageContaining("artefacts.json")
                .hasMessageContaining("zzz_unknown_field");
    }

    @Test
    void FR_F06_fails_loudly_on_dangling_link_source() {
        Path dir = brokenCase("dangling-link-source");

        assertThatThrownBy(() -> FixtureSet.load(dir))
                .isInstanceOf(InvalidFixtureException.class)
                .hasMessageContaining("links.json")
                .hasMessageContaining("DOES_NOT_EXIST")
                .hasMessageContaining("source");
    }

    @Test
    void FR_F06_fails_loudly_on_dangling_link_target() {
        Path dir = brokenCase("dangling-link-target");

        assertThatThrownBy(() -> FixtureSet.load(dir))
                .isInstanceOf(InvalidFixtureException.class)
                .hasMessageContaining("links.json")
                .hasMessageContaining("DOES_NOT_EXIST")
                .hasMessageContaining("target");
    }

    @Test
    void FR_F02_fails_loudly_on_artefact_without_uri_mapping() {
        Path dir = brokenCase("missing-uri");

        assertThatThrownBy(() -> FixtureSet.load(dir))
                .isInstanceOf(InvalidFixtureException.class)
                .hasMessageContaining("uris.json")
                .hasMessageContaining("A1");
    }

    @Test
    void FR_S05_fails_loudly_on_unknown_link_state() {
        Path dir = brokenCase("unknown-link-state");

        assertThatThrownBy(() -> FixtureSet.load(dir))
                .isInstanceOf(InvalidFixtureException.class)
                .hasMessageContaining("links.json")
                .hasMessageContaining("bogus");
    }

    @Test
    void CR_25_owners_byTool_unknown_tool_is_tolerated_as_a_routing_hint() {
        // The AERO-7 fixture reversion establishes that owners.byTool is a
        // notification-routing hint (CR-25), not a strict foreign key against
        // tools.json. The loader must accept unknown-tool keys and let the
        // fallback owner handle them. The loader emits an SLF4J warning when it
        // sees one — not asserted here to avoid coupling to a logger spy.
        Path dir = brokenCase("owners-unknown-tool");

        org.eda.fixtures.FixtureSet f = FixtureSet.load(dir);
        assertThat(f.owners().byTool()).containsKey("Z-UNKNOWN");
        assertThat(f.owners().fallback()).isEqualTo("area-lead");
    }
}
