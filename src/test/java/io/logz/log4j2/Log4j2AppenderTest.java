package io.logz.log4j2;

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

import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static io.logz.test.MockLogzioBulkListener.LogRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * @author MarinaRazumovsky
 */
public class Log4j2AppenderTest {

    private final static Logger logger = LogManager.getLogger(Log4j2AppenderTest.class);
    protected MockLogzioBulkListener mockListener;

    @Before
    public void startMockListener() throws Exception {
        mockListener = new io.logz.test.MockLogzioBulkListener();
        mockListener.start();
    }

    @After
    public void stopMockListener() {
        mockListener.stop();
    }

    @Test
    public void simpleAppending() throws Exception {
        String token = "aBcDeFgHiJkLmNoPqRsT";
        String type = "awesomeType";
        String loggerName = "simpleAppending";
        int drainTimeout = 1;
        String message1 = "Testing.." + random(5);
        String message2 = "Warning test.." + random(5);

        Logger testLogger = createLogger(token, type, loggerName, drainTimeout, false, null);
        testLogger.info(message1);
        testLogger.warn(message2);

        sleepSeconds(drainTimeout * 2);
        mockListener.assertNumberOfReceivedMsgs(2);
        mockListener.assertLogReceivedIs(message1, token, type, loggerName, Level.INFO.name());
        mockListener.assertLogReceivedIs(message2, token, type, loggerName, Level.WARN.name());
    }

    @Test
    public void simpleGzipAppending() throws Exception {
        String token = "aBcDeFgHiJkLmNoPqRsTGzIp";
        String type = "awesomeGzipType";
        String loggerName = "simpleGzipAppending";
        int drainTimeout = 1;
        String message1 = "Testing.." + random(5);
        String message2 = "Warning test.." + random(5);

        Logger testLogger = createLogger(token, type, loggerName, drainTimeout, false, null, true);
        testLogger.info(message1);
        testLogger.warn(message2);

        sleepSeconds(drainTimeout * 2);
        mockListener.assertNumberOfReceivedMsgs(2);
        mockListener.assertLogReceivedIs(message1, token, type, loggerName, Level.INFO.name());
        mockListener.assertLogReceivedIs(message2, token, type, loggerName, Level.WARN.name());
    }


    @Test
    public void validateAdditionalFields() throws Exception {
        String token = "validatingAdditionalFields";
        String type = "willTryWithOrWithoutEnvironmentVariables";
        String loggerName = "additionalLogger";
        int drainTimeout = 1;
        String message1 = "Just a log - " + random(5);
        Map<String,String > additionalFields = new HashMap<>();
        String additionalFieldsString = "java_home=$JAVA_HOME;testing=yes;message=override";
        additionalFields.put("java_home", System.getenv("JAVA_HOME"));
        additionalFields.put("testing", "yes");

        Logger testLogger = createLogger(token, type, loggerName, drainTimeout, false, additionalFieldsString);
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
        String type = "withOrWithoutHostnamr";
        String loggerName = "runningOutOfIdeasHere";
        int drainTimeout = 1;
        String message1 = "Hostname log - " +  random(5);

        Logger testLogger = createLogger(token, type, loggerName, drainTimeout, true, null);
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
    public void sendException() throws Exception {
        String token = "checkingExceptions";
        String type = "badType";
        String loggerName = "exceptionProducer";
        int drainTimeout = 1;
        Throwable exception = null;
        String message1 = "This is not an int..";

        Logger testLogger = createLogger(token, type, loggerName, drainTimeout, false, null);
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
    public void testMDC() throws Exception {
        String token = "mdcTokensAreTheBest";
        String type = "mdcType";
        String loggerName = "mdcTesting";
        int drainTimeout = 1;
        String message1 = "Simple log line - "+random(5);
        String mdcKey = "mdc-key";
        String mdcValue = "mdc-value";

        ThreadContext.put(mdcKey,mdcValue);
        Logger testLogger = createLogger(token, type, loggerName, drainTimeout, false, null);
        testLogger.info(message1);

        sleepSeconds(2 * drainTimeout);

        mockListener.assertNumberOfReceivedMsgs(1);
        LogRequest logRequest = mockListener.assertLogReceivedByMessage(message1);
        mockListener.assertLogReceivedIs(logRequest, token, type, loggerName, Level.INFO.name());
        assertThat(logRequest.getStringFieldOrNull(mdcKey)).isEqualTo(mdcValue);
    }

    @Test
    public void testMarker() throws Exception {
        String token = "markerToken";
        String type = "markerType";
        String loggerName = "markerTesting";
        String markerKey = "marker";
        String markerTestValue = "MyMarker";
        int drainTimeout = 1;
        String message1 = "Simple log line - "+random(5);
        Marker marker = MarkerManager.getMarker(markerTestValue);

        Logger testLogger = createLogger(token, type, loggerName, drainTimeout, false, null);
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
        String type = "testType";
        String loggerName = "testLogger";
        int drainTimeout = 1;
        String message1 = "Just a log - " + random(5);

        Logger testLogger = createLogger("$JAVA_HOME", type, loggerName, drainTimeout, false, null);
        testLogger.info(message1);

        sleepSeconds(2 * drainTimeout);

        mockListener.assertNumberOfReceivedMsgs(1);
        LogRequest logRequest = mockListener.assertLogReceivedByMessage(message1);
        mockListener.assertLogReceivedIs(logRequest, token, type, loggerName, Level.INFO.name());
    }

    private Logger createLogger(String token, String type, String loggerName, Integer drainTimeout,
                                  boolean addHostname, String additionalFields, boolean compressRequests) {
        logger.info("Creating logger {}. token={}, type={}, drainTimeout={}, addHostname={}, additionalFields={}",
                loggerName, token, type, drainTimeout, addHostname, additionalFields);
        Logger log4j2Logger =  LogManager.getLogger(loggerName);
        LogzioAppender.Builder logzioLog4j2AppenderBuilder = LogzioAppender.newBuilder();
        logzioLog4j2AppenderBuilder.setLogzioToken(token);
        logzioLog4j2AppenderBuilder.setLogzioType(type);
        logzioLog4j2AppenderBuilder.setDebug(true);
        logzioLog4j2AppenderBuilder.setLogzioUrl("http://" + mockListener.getHost() + ":" + mockListener.getPort());
        logzioLog4j2AppenderBuilder.setAddHostname(addHostname);
        logzioLog4j2AppenderBuilder.setCompressRequests(compressRequests);

        if (drainTimeout != null) {
            logzioLog4j2AppenderBuilder.setDrainTimeoutSec(drainTimeout);
        }

        if (additionalFields != null) {
            logzioLog4j2AppenderBuilder.setAdditionalFields(additionalFields);
        }

        LogzioAppender appender = logzioLog4j2AppenderBuilder.build();
        appender.start();
        assertThat(appender.isStarted()).isTrue();
        ((org.apache.logging.log4j.core.Logger) log4j2Logger).addAppender(appender);
        ((org.apache.logging.log4j.core.Logger) log4j2Logger).setAdditive(false);

        return log4j2Logger;
    }

    private Logger createLogger(String token, String type, String loggerName, Integer drainTimeout,
                                boolean addHostname, String additionalFields) {
        return createLogger(token, type, loggerName, drainTimeout, addHostname, additionalFields, false);
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

    private String random(int numberOfChars) {
            return UUID.randomUUID().toString().substring(0, numberOfChars-1);
        }
}
