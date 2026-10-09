package org.eda.resolver.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class UriGeneratorTest {

    private static final Pattern OPAQUE_URI = Pattern.compile(
            "https://eda\\.example\\.org/r/[a-z]+/"
                    + "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private final UriGenerator generator = new UriGenerator("https://eda.example.org");

    @Test
    void AD_03_signature_takes_only_tool_namespace_and_UUID() {
        List<Method> publicGenerators = Arrays.stream(UriGenerator.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .filter(m -> m.getName().equals("generate"))
                .toList();

        assertThat(publicGenerators)
                .as("UriGenerator must expose exactly one public 'generate' method")
                .hasSize(1);

        Method m = publicGenerators.get(0);
        assertThat(m.getReturnType()).isEqualTo(URI.class);
        assertThat(m.getParameterTypes())
                .as("generate must take exactly (String toolNamespace, UUID uuid)")
                .containsExactly(String.class, UUID.class);
    }

    @Test
    void AD_03_output_matches_opaque_pattern_for_random_UUIDs() {
        Random rnd = new Random(42);
        for (int i = 0; i < 1000; i++) {
            UUID uuid = new UUID(rnd.nextLong(), rnd.nextLong());
            URI uri = generator.generate("/r/pol/", uuid);
            assertThat(uri.toString()).matches(OPAQUE_URI);
        }
    }

    @Test
    void AD_03_namespace_normalisation_is_idempotent() {
        UUID uuid = UUID.randomUUID();
        URI withSlashes = generator.generate("/r/pol/", uuid);
        URI withoutLeading = generator.generate("r/pol/", uuid);
        URI withoutTrailing = generator.generate("/r/pol", uuid);
        URI without = generator.generate("r/pol", uuid);

        assertThat(withSlashes).isEqualTo(withoutLeading);
        assertThat(withSlashes).isEqualTo(withoutTrailing);
        assertThat(withSlashes).isEqualTo(without);
    }

    @Test
    void rejects_null_or_blank_inputs() {
        UUID uuid = UUID.randomUUID();

        assertThatThrownBy(() -> generator.generate(null, uuid))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> generator.generate("", uuid))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> generator.generate("/r/pol/", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_null_or_blank_base_domain() {
        assertThatThrownBy(() -> new UriGenerator(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UriGenerator(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
