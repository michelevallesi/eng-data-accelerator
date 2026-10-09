package org.eda.resolver.domain;

public final class ResolverExceptions {

    private ResolverExceptions() {
    }

    public static final class UnknownToolException extends RuntimeException {
        public UnknownToolException(String tool) {
            super("unknown tool: " + tool);
        }
    }

    public static final class UnknownUriException extends RuntimeException {
        public UnknownUriException(String uri) {
            super("unknown URI: " + uri);
        }
    }

    public static final class TombstonedUriException extends RuntimeException {
        private final String reason;
        private final java.time.OffsetDateTime tombstonedAt;

        public TombstonedUriException(String uri, String reason, java.time.OffsetDateTime tombstonedAt) {
            super("tombstoned URI: " + uri);
            this.reason = reason;
            this.tombstonedAt = tombstonedAt;
        }

        public String reason() {
            return reason;
        }

        public java.time.OffsetDateTime tombstonedAt() {
            return tombstonedAt;
        }
    }

    public static final class AliasConflictException extends RuntimeException {
        public AliasConflictException(String fromUri) {
            super("URI already aliased: " + fromUri);
        }
    }
}
