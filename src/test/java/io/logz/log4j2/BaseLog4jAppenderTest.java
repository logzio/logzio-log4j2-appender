package io.logz.log4j2;

import io.logz.log4j2.LogzioAppender.Builder;
import io.logz.test.MockLogzioBulkListener;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.After;
import org.junit.Before;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class BaseLog4jAppenderTest {
    protected final static Logger logger = LogManager.getLogger(Log4j2AppenderTest.class);
    protected MockLogzioBulkListener mockListener;
    protected enum QueueType { DISK, MEMORY }

    @Before
    public void startMockListener() throws Exception {
        mockListener = new io.logz.test.MockLogzioBulkListener();
        mockListener.start();
    }

    @After
    public void stopMockListener() {
        mockListener.stop();
    }

    protected void assertAdditionalFields(MockLogzioBulkListener.LogRequest logRequest, Map<String, String> additionalFields) {
        additionalFields.forEach((field, value) -> {
            String fieldValueInLog = logRequest.getStringFieldOrNull(field);
            assertThat(fieldValueInLog)
                    .describedAs("Field '{}' in Log [{}]", field, logRequest.getJsonObject().toString())
                    .isNotNull()
                    .isEqualTo(value);
        });
    }

    protected void sleepSeconds(int seconds) {
        logger.info("Sleeping {} [sec]...", seconds);
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    protected Logger getLogger(Builder logzioAppenderBuilder, String loggerName, String token, String type, int drainTimeout) {
        logzioAppenderBuilder.setLogzioToken(token);
        logzioAppenderBuilder.setLogzioUrl("http://" + mockListener.getHost() + ":" + mockListener.getPort());
        logzioAppenderBuilder.setLogzioType(type);
        logzioAppenderBuilder.setDrainTimeoutSec(drainTimeout);
        Logger log4j2Logger =  LogManager.getLogger(loggerName);
        LogzioAppender appender = logzioAppenderBuilder.build();
        appender.start();
        assertThat(appender.isStarted()).isTrue();
        ((org.apache.logging.log4j.core.Logger) log4j2Logger).addAppender(appender);
        ((org.apache.logging.log4j.core.Logger) log4j2Logger).setAdditive(false);
        return log4j2Logger;
    }

    protected String random(int numberOfChars) {
        return UUID.randomUUID().toString().substring(0, numberOfChars-1);
    }
    protected MockLogzioBulkListener.LogRequest waitForMessage(String message, int maxWaitSeconds) {
        long startTime = System.currentTimeMillis();
        long maxWaitMillis = maxWaitSeconds * 1000L;
        
        while (System.currentTimeMillis() - startTime < maxWaitMillis) {
            try {
                return mockListener.assertLogReceivedByMessage(message);
            } catch (AssertionError e) {
                try {
                    Thread.sleep(100); /
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(ie);
                }
            }
        }
        return null; 
    }
}
