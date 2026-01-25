package io.logz.e2e;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * E2E test that sends logs to Logz.io for validation.
 * 
 * Required environment variables:
 * - LOGZIO_TOKEN: Logz.io shipping token
 * - ENV_ID: Unique identifier for this test run
 * 
 * The log4j2.xml configuration uses these environment variables
 * to configure the LogzioAppender.
 */
public class E2ETest {
    private static final Logger logger = LogManager.getLogger(E2ETest.class);

    public static void main(String[] args) throws InterruptedException {
        String envId = System.getenv("ENV_ID");
        
        if (envId == null || envId.isEmpty()) {
            System.err.println("ERROR: ENV_ID environment variable is required");
            System.exit(1);
        }

        System.out.println("Starting E2E test with env_id: " + envId);

        logger.info("E2E test started - env_id: {}", envId);
        logger.warn("E2E warning message - env_id: {}", envId);
        logger.error("E2E error message - env_id: {}", envId);

        try {
            throw new RuntimeException("Test exception for E2E validation");
        } catch (Exception e) {
            logger.error("E2E exception test - env_id: {}", envId, e);
        }

        System.out.println("Waiting for logs to be sent...");
        Thread.sleep(15000);

        System.out.println("E2E logs sent successfully");
    }
}

