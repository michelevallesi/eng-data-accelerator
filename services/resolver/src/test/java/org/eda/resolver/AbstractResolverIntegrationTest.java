package org.eda.resolver;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Shared Postgres container for the whole JVM: started once, never torn down, wired
 * into every {@code @SpringBootTest} subclass via {@link DynamicPropertySource}. The
 * singleton pattern avoids per-class container churn, which was breaking Testcontainers
 * when running Maven inside a container through a mounted Podman socket.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"eda.fixtures.seed=skid400"})
@SuppressWarnings("resource")
public abstract class AbstractResolverIntegrationTest {

    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("eda")
                    .withUsername("eda")
                    .withPassword("dev");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
