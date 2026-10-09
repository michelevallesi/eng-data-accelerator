package org.eda.adapter.polarion.polarion;

public class PolarionUnavailableException extends RuntimeException {

    public PolarionUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public PolarionUnavailableException(String message) {
        super(message);
    }
}
