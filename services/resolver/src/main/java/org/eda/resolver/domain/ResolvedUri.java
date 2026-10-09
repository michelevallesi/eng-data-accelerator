package org.eda.resolver.domain;

public record ResolvedUri(
        String uri,
        String tool,
        String nativeId,
        String deepLink,
        String canonicalUri) {

    public boolean isAliased() {
        return canonicalUri != null && !canonicalUri.equals(uri);
    }
}
