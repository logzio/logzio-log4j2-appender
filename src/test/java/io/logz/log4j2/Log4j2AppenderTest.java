package io.logz.log4j2;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import org.apache.logging.log4j.Level;
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
    /**
     * Create and configure an OpenTelemetry instance.
     * For real usage, you might configure exporters, Resource, etc.
     */
    private OpenTelemetry initOpenTelemetry() {
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .build();

        OpenTelemetrySdk openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .build();

        Runtime.getRuntime().addShutdownHook(new Thread(tracerProvider::close));
        return openTelemetry;
    }

    @Test
    public void openTelemetryContext_Enabled() {
        String token = "otelToken";
        String type = "otelType" + random(8);
        String loggerName = "otelLogger" + random(8);
        int drainTimeout = 1;
        String message = "Test log with OTel context for Log4j2 - " + random(5);

        logzioAppenderBuilder.setAddOpentelemetryContext(true);

        Logger testLogger = getLogger(logzioAppenderBuilder, loggerName, token, type, drainTimeout);

        OpenTelemetry openTelemetry = initOpenTelemetry();
        Tracer tracer = openTelemetry.getTracer("test-tracer");
        Span span = tracer.spanBuilder("testSpan").startSpan();
        try (Scope scope = span.makeCurrent()) {
            testLogger.info(message);

            sleepSeconds(drainTimeout * 2);

            mockListener.assertNumberOfReceivedMsgs(1);
            LogRequest logRequest = mockListener.assertLogReceivedByMessage(message);
            mockListener.assertLogReceivedIs(logRequest, token, type, loggerName, Level.INFO.name());

            String traceId = logRequest.getStringFieldOrNull("trace_id");
            String spanId = logRequest.getStringFieldOrNull("span_id");
            String serviceName = logRequest.getStringFieldOrNull("service_name");

            assertThat(traceId)
                    .as("trace_id should be present when addOpentelemetryContext=true")
                    .isNotNull();
            assertThat(spanId)
                    .as("span_id should be present when addOpentelemetryContext=true")
                    .isNotNull();
            assertThat(serviceName)
                    .as("service_name might be null unless configured, but let's check anyway")
                    .isNotNull();
        } finally {
            span.end();
        }
    }

    @Test
    public void openTelemetryContext_Disabled() {
        String token = "otelToken2";
        String type = "otelTypeDisabled" + random(8);
        String loggerName = "otelLoggerDisabled" + random(8);
        int drainTimeout = 1;
        String message = "Test log with OTel context disabled - " + random(5);

        logzioAppenderBuilder.setAddOpentelemetryContext(false);

        Logger testLogger = getLogger(logzioAppenderBuilder, loggerName, token, type, drainTimeout);

        OpenTelemetry openTelemetry = initOpenTelemetry();
        Tracer tracer = openTelemetry.getTracer("test-tracer");
        Span span = tracer.spanBuilder("testSpan").startSpan();
        try (Scope scope = span.makeCurrent()) {
            testLogger.info(message);

            sleepSeconds(drainTimeout * 2);

            mockListener.assertNumberOfReceivedMsgs(1);
            LogRequest logRequest = mockListener.assertLogReceivedByMessage(message);
            mockListener.assertLogReceivedIs(logRequest, token, type, loggerName, Level.INFO.name());

            String traceId = logRequest.getStringFieldOrNull("trace_id");
            String spanId = logRequest.getStringFieldOrNull("span_id");
            String serviceName = logRequest.getStringFieldOrNull("service_name");

            assertThat(traceId)
                    .as("trace_id should NOT be present when addOpentelemetryContext=false")
                    .isNull();
            assertThat(spanId)
                    .as("span_id should NOT be present when addOpentelemetryContext=false")
                    .isNull();
            assertThat(serviceName)
                    .as("service_name should NOT be present when addOpentelemetryContext=false")
                    .isNull();
        } finally {
            span.end();
        }
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
}
