package org.eda.adapter.teamcenter.teamcenter;

public class TeamcenterUnavailableException extends RuntimeException {

    public TeamcenterUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public TeamcenterUnavailableException(String message) {
        super(message);
    }
}
