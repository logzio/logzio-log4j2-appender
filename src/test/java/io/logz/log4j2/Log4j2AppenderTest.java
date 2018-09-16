package io.logz.log4j2;

import io.logz.log4j2.LogzioAppender.Builder;
import io.logz.test.MockLogzioBulkListener;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.apache.logging.log4j.ThreadContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.logz.test.MockLogzioBulkListener.LogRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * @author MarinaRazumovsky
 */

@RunWith(Parameterized.class)
public class Log4j2AppenderTest {

    private final static Logger logger = LogManager.getLogger(Log4j2AppenderTest.class);
    protected MockLogzioBulkListener mockListener;
    private LogzioAppender.Builder logzioAppenderBuilder;

    @Before
    public void startMockListener() throws Exception {
        mockListener = new io.logz.test.MockLogzioBulkListener();
        mockListener.start();
    }

    @After
    public void stopMockListener() {
        mockListener.stop();
    }

    @Parameterized.Parameters
    public static Collection<Object[]> logzioSenderBuilders() {
        List<Builder> builders = new ArrayList<>();
        builders.add(new Builder());
        builders.add(new Builder().setInMemoryBuffer(true));

        Collection<Object[]> result = new ArrayList<>();
        for (Builder builder : builders) {
            result.add(new Object[]{builder});
        }
        return result;
    }

    public Log4j2AppenderTest(LogzioAppender.Builder logzioAppenderBuilder) {
        this.logzioAppenderBuilder = logzioAppenderBuilder;
    }

    @Test
    public void simpleAppending() {
        String token = "aBcDeFgHiJkLmNoPqRsT";
        String type = random(8);
        String loggerName = "simpleAppending" + random(8);
        int drainTimeout = 1;
        String message1 = "Testing.." + random(5);
        String message2 = "Warning test.." + random(5);

        logzioAppenderBuilder.setLogzioToken(token);
        logzioAppenderBuilder.setLogzioUrl("http://" + mockListener.getHost() + ":" + mockListener.getPort());
        logzioAppenderBuilder.setLogzioType(type);
        logzioAppenderBuilder.setDrainTimeoutSec(drainTimeout);

        Logger testLogger = getLogger(loggerName);
        testLogger.info(message1);
        testLogger.warn(message2);

        sleepSeconds(drainTimeout * 2);
        mockListener.assertNumberOfReceivedMsgs(2);
        mockListener.assertLogReceivedIs(message1, token, type, loggerName, Level.INFO.name());
        mockListener.assertLogReceivedIs(message2, token, type, loggerName, Level.WARN.name());
    }

    @Test
    public void simpleGzipAppending() {
        String token = "aBcDeFgHiJkLmNoPqRsTGzIp";
        String type = random(8);
        String loggerName = "simpleGzipAppending"+ random(8);
        int drainTimeout = 1;
        String message1 = "Testing.." + random(5);
        String message2 = "Warning test.." + random(5);

        logzioAppenderBuilder.setLogzioToken(token);
        logzioAppenderBuilder.setLogzioUrl("http://" + mockListener.getHost() + ":" + mockListener.getPort());
        logzioAppenderBuilder.setLogzioType(type);
        logzioAppenderBuilder.setDrainTimeoutSec(drainTimeout);
        logzioAppenderBuilder.setCompressRequests(true);

        Logger testLogger = getLogger(loggerName);
        testLogger.info(message1);
        testLogger.warn(message2);

        sleepSeconds(drainTimeout * 2);
        mockListener.assertNumberOfReceivedMsgs(2);
        mockListener.assertLogReceivedIs(message1, token, type, loggerName, Level.INFO.name());
        mockListener.assertLogReceivedIs(message2, token, type, loggerName, Level.WARN.name());
    }

    @Test
    public void validateAdditionalFields() {
        String token = "validatingAdditionalFields";
        String type = random(8);
        String loggerName = "additionalLogger" + random(8);
        int drainTimeout = 1;
        String message1 = "Just a log - " + random(5);
        Map<String,String > additionalFields = new HashMap<>();

        String additionalFieldsString = "java_home=$JAVA_HOME;testing=yes;message=override";
        additionalFields.put("java_home", System.getenv("JAVA_HOME"));
        additionalFields.put("testing", "yes");

        logzioAppenderBuilder.setLogzioToken(token);
        logzioAppenderBuilder.setLogzioUrl("http://" + mockListener.getHost() + ":" + mockListener.getPort());
        logzioAppenderBuilder.setLogzioType(type);
        logzioAppenderBuilder.setDrainTimeoutSec(drainTimeout);
        logzioAppenderBuilder.setAdditionalFields(additionalFieldsString);

        Logger testLogger = getLogger(loggerName);
        testLogger.info(message1);

        sleepSeconds(2 * drainTimeout);

        mockListener.assertNumberOfReceivedMsgs(1);
        LogRequest logRequest = mockListener.assertLogReceivedByMessage(message1);
        mockListener.assertLogReceivedIs(logRequest, token, type, loggerName, Level.INFO.name());
        assertAdditionalFields(logRequest, additionalFields);
    }

    @Test
    public void existingHostname() throws Exception {
        String token = "checkingHostname";
        String type = random(8);
        String loggerName = "runningOutOfIdeasHere" + random(8);
        int drainTimeout = 1;
        String message1 = "Hostname log - " +  random(5);

        logzioAppenderBuilder.setLogzioToken(token);
        logzioAppenderBuilder.setLogzioUrl("http://" + mockListener.getHost() + ":" + mockListener.getPort());
        logzioAppenderBuilder.setLogzioType(type);
        logzioAppenderBuilder.setDrainTimeoutSec(drainTimeout);
        logzioAppenderBuilder.setAddHostname(true);

        Logger testLogger = getLogger(loggerName);
        testLogger.info(message1);

        // Sleep double time the drain timeout
        sleepSeconds(2 * drainTimeout);

        mockListener.assertNumberOfReceivedMsgs(1);
        LogRequest logRequest = mockListener.assertLogReceivedByMessage(message1);
        mockListener.assertLogReceivedIs(logRequest, token, type, loggerName, Level.INFO.name());

        String hostname = InetAddress.getLocalHost().getHostName();
        assertThat(logRequest.getHost()).isEqualTo(hostname);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void sendException() {
        String token = "checkingExceptions";
        String type = random(8);
        String loggerName = "exceptionProducer" + random(8);
        int drainTimeout = 1;
        Throwable exception = null;
        String message1 = "This is not an int..";

        logzioAppenderBuilder.setLogzioToken(token);
        logzioAppenderBuilder.setLogzioUrl("http://" + mockListener.getHost() + ":" + mockListener.getPort());
        logzioAppenderBuilder.setLogzioType(type);
        logzioAppenderBuilder.setDrainTimeoutSec(drainTimeout);
        Logger testLogger = getLogger(loggerName);

        try {
            Integer.parseInt(message1);
        } catch (Exception e) {
            exception = e;
            testLogger.info(message1, e);
        }
        assertThat(exception).isNotNull();
        sleepSeconds(2 * drainTimeout);

        mockListener.assertNumberOfReceivedMsgs(1);
        LogRequest logRequest = mockListener.assertLogReceivedByMessage(message1);
        mockListener.assertLogReceivedIs(logRequest, token, type, loggerName, Level.INFO.name());

        String exceptionField = logRequest.getStringFieldOrNull("exception");
        if (exceptionField == null) fail("Exception field does not exists");
        assertThat(exceptionField.replace("\\", "")).contains(exception.getMessage());
    }

    @Test
    public void testMDC() {
        String token = "mdcTokensAreTheBest";
        String type = random(8);
        String loggerName = "mdcTesting" + random(8);
        int drainTimeout = 1;
        String message1 = "Simple log line - " + random(5);
        String mdcKey = "mdc-key";
        String mdcValue = "mdc-value";

        ThreadContext.put(mdcKey,mdcValue);

        logzioAppenderBuilder.setLogzioToken(token);
        logzioAppenderBuilder.setLogzioUrl("http://" + mockListener.getHost() + ":" + mockListener.getPort());
        logzioAppenderBuilder.setLogzioType(type);
        logzioAppenderBuilder.setDrainTimeoutSec(drainTimeout);
        Logger testLogger = getLogger(loggerName);

        testLogger.info(message1);

        sleepSeconds(2 * drainTimeout);

        mockListener.assertNumberOfReceivedMsgs(1);
        LogRequest logRequest = mockListener.assertLogReceivedByMessage(message1);
        mockListener.assertLogReceivedIs(logRequest, token, type, loggerName, Level.INFO.name());
        assertThat(logRequest.getStringFieldOrNull(mdcKey)).isEqualTo(mdcValue);
    }

    @Test
    public void testMarker() {
        String token = "markerToken";
        String type = random(8);
        String loggerName = "markerTesting" + random(8);
        String markerKey = "marker";
        String markerTestValue = "MyMarker";
        int drainTimeout = 1;
        String message1 = "Simple log line - " + random(5);
        Marker marker = MarkerManager.getMarker(markerTestValue);

        logzioAppenderBuilder.setLogzioToken(token);
        logzioAppenderBuilder.setLogzioUrl("http://" + mockListener.getHost() + ":" + mockListener.getPort());
        logzioAppenderBuilder.setLogzioType(type);
        logzioAppenderBuilder.setDrainTimeoutSec(drainTimeout);
        Logger testLogger = getLogger(loggerName);

        testLogger.info(marker, message1);

        sleepSeconds(2 * drainTimeout);

        mockListener.assertNumberOfReceivedMsgs(1);
        LogRequest logRequest = mockListener.assertLogReceivedByMessage(message1);
        mockListener.assertLogReceivedIs(logRequest, token, type, loggerName, Level.INFO.name());
        assertThat(logRequest.getStringFieldOrNull(markerKey)).isEqualTo(markerTestValue);
    }

    @Test
    public void testTokenAndLogzioUrlFromSystemEnvironment() {
        String token = System.getenv("JAVA_HOME");
        String type = random(8);
        String loggerName = "testLogger" + random(8);
        int drainTimeout = 1;
        String message1 = "Just a log - " + random(5);

        logzioAppenderBuilder.setLogzioToken(token);
        logzioAppenderBuilder.setLogzioUrl("http://" + mockListener.getHost() + ":" + mockListener.getPort());
        logzioAppenderBuilder.setLogzioType(type);
        logzioAppenderBuilder.setDrainTimeoutSec(drainTimeout);
        Logger testLogger = getLogger(loggerName);

        testLogger.info(message1);

        sleepSeconds(2 * drainTimeout);

        mockListener.assertNumberOfReceivedMsgs(1);
        LogRequest logRequest = mockListener.assertLogReceivedByMessage(message1);
        mockListener.assertLogReceivedIs(logRequest, token, type, loggerName, Level.INFO.name());
    }

    private void assertAdditionalFields(LogRequest logRequest, Map<String, String> additionalFields) {
        additionalFields.forEach((field, value) -> {
            String fieldValueInLog = logRequest.getStringFieldOrNull(field);
            assertThat(fieldValueInLog)
                    .describedAs("Field '{}' in Log [{}]", field, logRequest.getJsonObject().toString())
                    .isNotNull()
                    .isEqualTo(value);
        });
    }

    private void sleepSeconds(int seconds) {
        logger.info("Sleeping {} [sec]...", seconds);
        try {
            Thread.sleep(seconds * 1000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private Logger getLogger(String loggerName) {
        Logger log4j2Logger =  LogManager.getLogger(loggerName);
        LogzioAppender appender = logzioAppenderBuilder.build();
        appender.start();
        assertThat(appender.isStarted()).isTrue();
        ((org.apache.logging.log4j.core.Logger) log4j2Logger).addAppender(appender);
        ((org.apache.logging.log4j.core.Logger) log4j2Logger).setAdditive(false);

        return log4j2Logger;
    }

    private String random(int numberOfChars) {
            return UUID.randomUUID().toString().substring(0, numberOfChars-1);
        }
}
