package com.myassistant;

import com.myassistant.config.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class MyAssistantApplicationTest {

    @Autowired
    private HealthEndpoint healthEndpoint;

    @Test
    void contextLoads() {}

    @Test
    void healthShowsMongoDbUp() {
        assertEquals(Status.UP, healthEndpoint.healthForPath("mongo").getStatus());
        assertEquals(Status.UP, healthEndpoint.health().getStatus());
    }
}
