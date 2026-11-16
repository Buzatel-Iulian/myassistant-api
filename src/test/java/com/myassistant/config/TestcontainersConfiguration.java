package com.myassistant.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.mongodb.MongoDBContainer;

/**
 * A real MongoDB in Docker for the tests. The field is static, so one container serves every test class of the run,
 * and {@code @ServiceConnection} points Spring's MongoDB client at it.
 */
@TestConfiguration
@ImportTestcontainers
public class TestcontainersConfiguration {

    // MongoDB 8.0 refuses to start on Linux kernel 6.19 and newer (SERVER-121912), as on GitHub's newer runners;
    // this setting lets it start there. Without it, the build fails in CI only.
    @ServiceConnection
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:8.0")
            .withEnv("GLIBC_TUNABLES", "glibc.pthread.rseq=1");
}
