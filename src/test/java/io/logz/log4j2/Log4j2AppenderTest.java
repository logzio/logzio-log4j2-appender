package io.logz.log4j2;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.apache.logging.log4j.ThreadContext;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static io.logz.test.MockLogzioBulkListener.LogRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@RunWith(Parameterized.class)
public class Log4j2AppenderTest extends BaseLog4jAppenderTest {
    private LogzioAppender.Builder logzioAppenderBuilder;
    private QueueType queueType;

    @Before
    public void setNewLogzioAppenderBuilder() {
        logzioAppenderBuilder = queueType == QueueType.DISK
                ? new LogzioAppender.Builder()
                : new LogzioAppender.Builder().setInMemoryQueue(true);
    }

    @Parameterized.Parameters
    public static Collection<QueueType[]> logzioSenderQueueTypes() {
        Collection<QueueType[]> queueTypes = new ArrayList<>();
        for (QueueType type : QueueType.values()) {
            queueTypes.add(new QueueType[]{type});
        }

        return queueTypes;
    }

    public Log4j2AppenderTest(QueueType queueType) {
        this.queueType = queueType;
    }

    @Test
    public void simpleAppending() {
        String token = "aBcDeFgHiJkLmNoPqRsT";
        String type = random(8);
        String loggerName = "simpleAppending" + random(8);
        int drainTimeout = 1;
        String message1 = "Testing.." + random(5);
        String message2 = "Warning test.." + random(5);

        Logger testLogger = getLogger(logzioAppenderBuilder, loggerName, token, type, drainTimeout);
        testLogger.info(message1);
        testLogger.warn(message2);

        sleepSeconds(drainTimeout * 2);
        mockListener.assertNumberOfReceivedMsgs(2);
        mockListener.assertLogReceivedIs(message1, token, type, loggerName, Level.INFO.name());
        mockListener.assertLogReceivedIs(message2, token, type, loggerName, Level.WARN.name());
    }

    @Test
    public void testReconfigureSimpleAppending() {
        String token = "aBcDeFgHiJkLmNoPqRsT";
        String type = random(8);
        String loggerName = "simpleAppending" + random(8);
        int drainTimeout = 1;
        String message1 = "Testing.." + random(5);
        String message2 = "Warning test.." + random(5);

        Logger testLogger = getLogger(logzioAppenderBuilder, loggerName, token, type, drainTimeout);
        testLogger.info(message1);
        testLogger.warn(message2);
        sleepSeconds(drainTimeout * 2);
        mockListener.assertNumberOfReceivedMsgs(2);

        testLogger = getLogger(logzioAppenderBuilder, loggerName, token, type, drainTimeout);
        testLogger.info(message1);
        testLogger.warn(message2);

        type = random(8);
        testLogger = getLogger(logzioAppenderBuilder, loggerName, token, type, drainTimeout);
        testLogger.info(message1);
        testLogger.info(message2);
        testLogger = getLogger(logzioAppenderBuilder, loggerName, token, type, drainTimeout);
        testLogger.info(message1);
        testLogger.warn(message2);

        sleepSeconds(drainTimeout * 2);
        mockListener.assertNumberOfReceivedMsgs(8);
    }


    @Test
    public void simpleGzipAppending() {
        String token = "aBcDeFgHiJkLmNoPqRsTGzIp";
        String type = random(8);
        String loggerName = "simpleGzipAppending"+ random(8);
        int drainTimeout = 1;
        String message1 = "Testing.." + random(5);
        String message2 = "Warning test.." + random(5);

        logzioAppenderBuilder.setCompressRequests(true);
        Logger testLogger = getLogger(logzioAppenderBuilder, loggerName, token, type, drainTimeout);
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

        String additionalFieldsString = "testing=yes;message=override";
//        additionalFields.put("java_home", System.getenv("JAVA_HOME"));
        additionalFields.put("testing", "yes");


        logzioAppenderBuilder.setAdditionalFields(additionalFieldsString);
        Logger testLogger = getLogger(logzioAppenderBuilder, loggerName, token, type, drainTimeout);
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

        logzioAppenderBuilder.setAddHostname(true);
        Logger testLogger = getLogger(logzioAppenderBuilder, loggerName, token, type, drainTimeout);

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

        Logger testLogger = getLogger(logzioAppenderBuilder, loggerName, token, type, drainTimeout);

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

        Logger testLogger = getLogger(logzioAppenderBuilder, loggerName, token, type, drainTimeout);

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

        Logger testLogger = getLogger(logzioAppenderBuilder, loggerName, token, type, drainTimeout);

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
        token = "fds";
        String type = random(8);
        String loggerName = "testLogger" + random(8);
        int drainTimeout = 1;
        String message1 = "Just a log - " + random(5);

        Logger testLogger = getLogger(logzioAppenderBuilder, loggerName, token, type, drainTimeout);

        testLogger.info(message1);

        sleepSeconds(2 * drainTimeout);

        mockListener.assertNumberOfReceivedMsgs(1);
        LogRequest logRequest = mockListener.assertLogReceivedByMessage(message1);
        mockListener.assertLogReceivedIs(logRequest, token, type, loggerName, Level.INFO.name());
    }

    @Test
    public void testDisabled() {
        String token = "fds";
        String type = random(8);
        String loggerName = "testLogger" + random(8);
        int drainTimeout = 1;
        String message1 = "Just a log - " + random(5);

        LogzioAppender.Builder testBuilder = logzioAppenderBuilder;
        testBuilder = testBuilder.setDisabled( true );
        testBuilder.setLogzioToken(token);
        testBuilder.setLogzioUrl("http://" + mockListener.getHost() + ":" + mockListener.getPort());
        testBuilder.setLogzioType(type);
        testBuilder.setDrainTimeoutSec(drainTimeout);
        Logger testLogger =  LogManager.getLogger(loggerName);
        LogzioAppender appender = testBuilder.build();
        appender.start();
        ((org.apache.logging.log4j.core.Logger) testLogger).addAppender(appender);

        testLogger.info(message1);

        sleepSeconds(2 * drainTimeout);

        mockListener.assertNumberOfReceivedMsgs(0);
    }
}
