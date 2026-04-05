package com.employee.loan_system.lacr.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
public class ProductionKafkaGuard implements InitializingBean {

    private final Environment environment;

    public ProductionKafkaGuard(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        String publisherMode = environment.getProperty("lacr.outbox.publisher.mode");
        boolean consumerEnabled = Boolean.parseBoolean(
                environment.getProperty("lacr.outbox.kafka.consumer.enabled", "false"));
        String bootstrapServers = environment.getProperty("spring.kafka.bootstrap-servers");

        if (!"kafka".equalsIgnoreCase(publisherMode)) {
            throw new IllegalStateException("LACR prod profile requires lacr.outbox.publisher.mode=kafka");
        }
        if (!consumerEnabled) {
            throw new IllegalStateException("LACR prod profile requires lacr.outbox.kafka.consumer.enabled=true");
        }
        if (bootstrapServers == null || bootstrapServers.isBlank()) {
            throw new IllegalStateException("LACR prod profile requires spring.kafka.bootstrap-servers");
        }
        if (bootstrapServers.contains("localhost")) {
            throw new IllegalStateException("LACR prod profile cannot use localhost Kafka bootstrap servers");
        }
    }
}
