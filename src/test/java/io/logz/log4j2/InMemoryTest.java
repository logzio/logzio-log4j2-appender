package io.logz.log4j2;

import io.logz.log4j2.LogzioAppender.Builder;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

public class InMemoryTest extends BaseLog4jAppenderTest {
    private Builder logzioAppenderBuilder;

    @Before
    public void setNewLogzioAppenderBuilder() {
        logzioAppenderBuilder = new LogzioAppender.Builder().setInMemoryQueue(true);
    }

    @Test
    public void validateQueueCapacityLimit() {
        String token = "verifyCapacityInBytesToken";
        String type = random(8);
        String loggerName = "verifyCapacityInBytes" + random(8);
        int drainTimeout = 2;
        String message1 = "Testing.." + random(5);
        String message2 = "Don't get here test! " + random(5);

        logzioAppenderBuilder.setInMemoryQueueCapacityBytes(message1.getBytes(StandardCharsets.UTF_8).length);
        Logger testLogger = getLogger(logzioAppenderBuilder, loggerName, token, type, drainTimeout);

        testLogger.info(message1);
        testLogger.warn(message2);

        sleepSeconds(drainTimeout * 2);
        mockListener.assertNumberOfReceivedMsgs(1);
        mockListener.assertLogReceivedIs(message1, token, type, loggerName, Level.INFO.name());
    }
}
