package org.eda.projection;

import java.time.Duration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@SuppressWarnings("resource")
public abstract class AbstractProjectionIntegrationTest {

    protected static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.0"));

    protected static final GenericContainer<?> FUSEKI =
            new GenericContainer<>(DockerImageName.parse("stain/jena-fuseki:5.1.0"))
                    .withEnv("ADMIN_PASSWORD", "dev")
                    .withEnv("FUSEKI_DATASET_1", "eda")
                    .withExposedPorts(3030)
                    .waitingFor(Wait.forHttp("/$/ping")
                            .forStatusCode(200)
                            .withStartupTimeout(Duration.ofMinutes(2)));

    static {
        KAFKA.start();
        FUSEKI.start();
    }

    @DynamicPropertySource
    static void sharedProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("eda.projection.fuseki.base-url",
                () -> "http://" + FUSEKI.getHost() + ":" + FUSEKI.getMappedPort(3030));
        registry.add("eda.projection.fuseki.dataset", () -> "eda");
    }
}
