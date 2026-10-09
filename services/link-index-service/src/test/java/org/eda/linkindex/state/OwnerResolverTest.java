package org.eda.linkindex.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.eda.fixtures.model.Owners;
import org.junit.jupiter.api.Test;

/**
 * Unit coverage for CR-25 owner resolution. Unknown-tool keys fall back
 * (AERO-7 "CSDB" vs "CSDB S1000D" is the canonical mismatch).
 */
class OwnerResolverTest {

    @Test
    void CR_25_known_tool_returns_mapped_owner() {
        OwnerResolver r = new OwnerResolver(new Owners(
                Map.of("Polarion", "requirements-engineer"), "area-lead", null));
        assertThat(r.resolveForTool("Polarion")).isEqualTo("requirements-engineer");
    }

    @Test
    void CR_25_unknown_tool_falls_back() {
        OwnerResolver r = new OwnerResolver(new Owners(
                Map.of("Polarion", "requirements-engineer"), "area-lead", null));
        assertThat(r.resolveForTool("Capella")).isEqualTo("area-lead");
    }

    @Test
    void CR_25_null_tool_falls_back() {
        OwnerResolver r = new OwnerResolver(new Owners(
                Map.of(), "area-lead", null));
        assertThat(r.resolveForTool(null)).isEqualTo("area-lead");
    }
}
