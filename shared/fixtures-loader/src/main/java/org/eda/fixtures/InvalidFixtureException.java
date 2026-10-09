package org.eda.fixtures;

public final class InvalidFixtureException extends RuntimeException {

    public InvalidFixtureException(String message) {
        super(message);
    }

    public InvalidFixtureException(String message, Throwable cause) {
        super(message, cause);
    }
}
