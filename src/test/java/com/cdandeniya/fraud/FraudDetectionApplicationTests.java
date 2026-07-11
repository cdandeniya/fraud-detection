package com.cdandeniya.fraud;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Smoke test - makes sure the whole Spring context wires up and starts.
 * Uses the in-memory H2 database from src/test/resources so no Postgres needed.
 */
@SpringBootTest
class FraudDetectionApplicationTests {

    @Test
    void contextLoads() {
    }
}
