package org.eda.resolver.domain;

import java.time.OffsetDateTime;

public record UriRecord(
        String uri,
        String tool,
        String nativeId,
        String deepLink,
        OffsetDateTime createdAt) {
}
