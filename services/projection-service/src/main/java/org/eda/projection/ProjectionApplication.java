package org.eda.projection;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

@SpringBootApplication
@EnableKafka
public class ProjectionApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProjectionApplication.class, args);
    }
}
