package org.eda.adapter.polarion;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.eda.simulator.polarion.PolarionSimulatorApplication;
import org.eda.simulator.polarion.state.InMemoryPolarionState;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Lazily starts the Polarion simulator in its own Spring context on a random port.
 * Shared across all adapter integration tests via a JVM-wide singleton; the context
 * is torn down at JVM exit via a shutdown hook.
 */
public final class SimulatorHarness {

    private static volatile ConfigurableApplicationContext context;
    private static volatile int port;

    private SimulatorHarness() {
    }

    public static synchronized int start() {
        if (context == null) {
            context = new SpringApplicationBuilder(PolarionSimulatorApplication.class)
                    .properties(
                            "server.port=0",
                            "spring.main.allow-bean-definition-overriding=true",
                            // The simulator inherits the adapter test's classpath, which
                            // includes spring-boot-starter-jdbc + Flyway + Kafka. Disable
                            // auto-config for all of them so the simulator stays an
                            // HTTP-only stand-in.
                            "spring.autoconfigure.exclude="
                                    + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                                    + "org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration,"
                                    + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,"
                                    + "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration",
                            "eda.fixtures.scenario=skid400",
                            "eda.base-domain=https://eda.example.org",
                            "spring.application.name=sim-under-test")
                    .run();
            port = Integer.parseInt(context.getEnvironment().getProperty("local.server.port"));
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (context != null) {
                    context.close();
                }
            }));
        }
        return port;
    }

    public static String baseUrl() {
        return "http://localhost:" + port;
    }

    public static InMemoryPolarionState state() {
        return context.getBean(InMemoryPolarionState.class);
    }

    public static int patchRequirement(String nativeId, String newDescription) {
        try {
            HttpRequest req = HttpRequest.newBuilder(
                            URI.create(baseUrl() + "/_mutate/req/" + nativeId))
                    .header("Content-Type", "application/json")
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(
                            "{\"description\":\"" + newDescription + "\"}"))
                    .build();
            HttpResponse<String> response =
                    HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
            return response.statusCode();
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("PATCH failed", e);
        }
    }
}
