package org.eda.resolver.domain;

import java.util.Optional;

public interface ToolRegistry {

    Optional<String> namespaceOf(String toolName);

    Optional<String> toolOfNamespace(String namespace);

    String deepLinkFor(String tool, String nativeId);
}
