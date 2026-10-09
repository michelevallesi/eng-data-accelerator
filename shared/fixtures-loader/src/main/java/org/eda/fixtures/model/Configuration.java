package org.eda.fixtures.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

public record Configuration(
        @JsonProperty("Global configuration") @JsonAlias("Configuration") String name,
        @JsonProperty("Content") String content,
        @JsonProperty("State") String state) {
}
