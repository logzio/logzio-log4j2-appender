package io.logz.log4j2;

import com.google.common.base.Splitter;
import io.logz.sender.LogzioSender;
import io.logz.sender.SenderStatusReporter;
import io.logz.sender.com.google.gson.JsonObject;
import io.logz.sender.exceptions.LogzioParameterErrorException;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderFactory;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.impl.ThrowableProxy;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.apache.logging.log4j.core.util.Log4jThreadFactory;
import org.apache.logging.log4j.status.StatusLogger;
import org.apache.logging.log4j.util.ReadOnlyStringMap;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;

/**
 * @author MarinaRazumovsky
 */
@Plugin(name = "LogzioAppender", category = "Core", elementType = Appender.ELEMENT_TYPE, printObject = true)
public class LogzioLog4j2Appender extends AbstractAppender {

    private static final String TIMESTAMP = "@timestamp";
    private static final String LOGLEVEL = "loglevel";
    private static final String MARKER = "marker";
    private static final String MESSAGE = "message";
    private static final String LOGGER = "logger";
    private static final String THREAD = "thread";
    private static final String EXCEPTION = "exception";

    private static final Set<String> reservedFields =  new HashSet<>(Arrays.asList(new String[] {TIMESTAMP,LOGLEVEL, MARKER, MESSAGE,LOGGER,THREAD,EXCEPTION}));

    private static Logger statusLogger = StatusLogger.getLogger();

    private LogzioSender logzioSender;

    private String logzioToken;
    private String logzioType = "java";
    private int drainTimeoutSec = 5;
    private int fileSystemFullPercentThreshold = 98;
    private String bufferDir;
    private String logzioUrl = "https://listener.logz.io:8071";
    private int connectTimeout = 10 * 1000;
    private int socketTimeout = 10 * 1000;
    private boolean debug = false;
    private boolean addHostname = false;
    private int gcPersistedQueueFilesIntervalSeconds = 30;

    private Map<String, String> additionalFieldsMap = new HashMap<>();

    protected LogzioLog4j2Appender(String name, Filter filter, Layout<? extends Serializable> layout, final boolean ignoreExceptions, String url,
                                   String token, String type, int drainTimeoutSec,int fileSystemFullPercentThreshold,
                                   String bufferDir, int socketTimeout, int connectTimeout, boolean addHostname,
                                   String additionalFields, boolean debug,int gcPersistedQueueFilesIntervalSeconds) {
        super(name, filter, layout, ignoreExceptions);
        this.logzioToken = token;
        this.logzioUrl = url;
        this.logzioType = type;
        this.drainTimeoutSec = drainTimeoutSec;
        this.fileSystemFullPercentThreshold = fileSystemFullPercentThreshold;
        this.bufferDir = bufferDir;
        this.socketTimeout = socketTimeout;
        this.connectTimeout = connectTimeout;
        this.debug = debug;
        this.addHostname = addHostname;
        this.gcPersistedQueueFilesIntervalSeconds = gcPersistedQueueFilesIntervalSeconds;
        if (additionalFields != null) {
            Splitter.on(';').omitEmptyStrings().withKeyValueSeparator('=').split(additionalFields).forEach((k, v) -> {
                if (reservedFields.contains(k)) {
                    statusLogger.warn("The field name '" + k + "' defined in additionalFields configuration can't be used since it's a reserved field name. This field will not be added to the outgoing log messages");
                }
                else {
                    String value = getValueFromSystemEnvironmentIfNeeded(v);
                    if (value != null) {
                        additionalFieldsMap.put(k, value);
                    }
                }
            });
            statusLogger.info("The additional fields that would be added: " + additionalFieldsMap.toString());
        }
    }

    @PluginBuilderFactory
    public static Builder newBuilder() {
        return new Builder();
    }

    public static class Builder implements org.apache.logging.log4j.core.util.Builder<LogzioLog4j2Appender> {

        @PluginElement("Filter")
        private Filter filter;

        @PluginElement("Layout")
        Layout<? extends Serializable> layout;

        @PluginBuilderAttribute
        String name = "LogzioAppender";

        @PluginBuilderAttribute("logzioUrl")
        String logzioUrl="https://listener.logz.io:8071";

        @PluginBuilderAttribute
        String logzioToken;

        @PluginBuilderAttribute
        String logzioType = "java";

        @PluginBuilderAttribute
        int drainTimeoutSec = 5;

        @PluginBuilderAttribute
        int fileSystemFullPercentThreshold = 98;

        @PluginBuilderAttribute
        String bufferDir;

        @PluginBuilderAttribute
        int socketTimeout = 10*1000;

        @PluginBuilderAttribute
        int connectTimeout = 10*1000;;

        @PluginBuilderAttribute
        boolean addHostname = false;

        @PluginBuilderAttribute
        String additionalFields;

        @PluginBuilderAttribute
        boolean debug=false;

        @PluginBuilderAttribute
        int gcPersistedQueueFilesIntervalSeconds = 30;

        @PluginBuilderAttribute
        private boolean ignoreExceptions = true;

        @Override
        public LogzioLog4j2Appender build() {
            return new LogzioLog4j2Appender(name,filter, layout, ignoreExceptions, logzioUrl, logzioToken, logzioType, drainTimeoutSec, fileSystemFullPercentThreshold,
                    bufferDir,socketTimeout,connectTimeout,addHostname,additionalFields,debug,gcPersistedQueueFilesIntervalSeconds);
        }

        public Builder setFilter(Filter filter) {
            this.filter = filter;
            return this;
        }

        public Builder setLayout(Layout<? extends Serializable> layout) {
            if (this.layout == null) {
                layout = PatternLayout.createDefaultLayout();
            }
            this.layout = layout;
            return this;
        }

        public Builder setIgnoreExceptions(final boolean ignoreExceptions) {
            this.ignoreExceptions = ignoreExceptions;
            return this;
        }

        public Builder setName(String name) {
            if (name == null) {
                statusLogger.warn("No name provided for LogzioLog4j2Appender");
                name = "LogzioAppender";
            }
            this.name = name;
            return this;
        }

        public Builder setLogzioUrl(String logzioUrl) {
            if ( logzioUrl != null ) {
                this.logzioUrl = getValueFromSystemEnvironmentIfNeeded(logzioUrl);
            }
            return this;
        }

        public Builder setLogzioToken(String logzioToken) {
            this.logzioToken = getValueFromSystemEnvironmentIfNeeded(logzioToken);
            return this;
        }

        public Builder setLogzioType(String logzioType) {
            this.logzioType = logzioType;
            return this;
        }

        public Builder setDrainTimeoutSec(int drainTimeoutSec) {
            this.drainTimeoutSec = drainTimeoutSec;
            return this;
        }

        public Builder setFileSystemFullPercentThreshold(int fileSystemFullPercentThreshold) {
            this.fileSystemFullPercentThreshold = fileSystemFullPercentThreshold;
            return this;
        }

        public Builder setBufferDir(String bufferDir) {
            this.bufferDir = bufferDir;
            return this;
        }

        public Builder setSocketTimeout(int socketTimeout) {
            this.socketTimeout = socketTimeout;
            return this;
        }

        public Builder setConnectTimeout(int connectTimeout) {
            this.connectTimeout = connectTimeout;
            return this;
        }

        public Builder setAddHostname(boolean addHostname) {
            this.addHostname = addHostname;
            return this;
        }

        public Builder setAdditionalFields(String additionalFields) {
            this.additionalFields = additionalFields;
            return this;
        }

        public Builder setDebug(boolean debug) {
            this.debug = debug;
            return this;
        }

        public Builder setGcPersistedQueueFilesIntervalSeconds(int gcPersistedQueueFilesIntervalSeconds) {
            this.gcPersistedQueueFilesIntervalSeconds = gcPersistedQueueFilesIntervalSeconds;
            return this;
        }
    }


    public void start() {
        if (logzioToken == null) {
            statusLogger.error("Logz.io Token is missing! Bailing out..");
            return;
        }
        if (!(fileSystemFullPercentThreshold >= 1 && fileSystemFullPercentThreshold <= 100)) {
            if (fileSystemFullPercentThreshold != -1) {
                statusLogger.error("fileSystemFullPercentThreshold should be a number between 1 and 100, or -1");
                return;
            }
        }
        try {
            if (addHostname) {
                String hostname = InetAddress.getLocalHost().getHostName();
                additionalFieldsMap.put("hostname", hostname);
            }
        } catch (UnknownHostException e) {
            statusLogger.warn("The configuration addHostName was specified but the host could not be resolved, thus the field 'hostname' will not be added", e);
        }
        if (bufferDir != null) {
            bufferDir += File.separator + logzioType;
            File bufferFile = new File(bufferDir);
            if (bufferFile.exists()) {
                if (!bufferFile.canWrite()) {
                    statusLogger.error("We cant write to your bufferDir location: "+bufferFile.getAbsolutePath());
                    return;
                }
            } else {
                if (!bufferFile.mkdirs()) {
                    statusLogger.error("We cant create your bufferDir location: "+bufferFile.getAbsolutePath());
                    return;
                }
            }
        }
        else {
            bufferDir = System.getProperty("java.io.tmpdir") + File.separator+"logzio-log4j2-buffer"+File.separator + logzioType;
        }
        File bufferDirFile = new File(bufferDir,"logzio-log4j2-appender");

        try {
           logzioSender = LogzioSender.getOrCreateSenderByType(logzioToken, logzioType, drainTimeoutSec, fileSystemFullPercentThreshold,
                    bufferDirFile, logzioUrl, socketTimeout, connectTimeout, debug,
                    new StatusReporter(), Executors.newScheduledThreadPool (2,Log4jThreadFactory.createThreadFactory(this.getClass().getSimpleName())), gcPersistedQueueFilesIntervalSeconds);
            logzioSender.start();
        } catch(IOException e) {
            statusLogger.error("Can't start Logzio data sender. Problem to create buffer directory: ",e);
            return;
        } catch (LogzioParameterErrorException e) {
            statusLogger.error("Some of the configuration parameters of logz.io is wrong: "+e.getMessage());
            statusLogger.error("Exception: " + e.getMessage(), e);
            return;
        }
        super.start();
    }


    @Override
    public void stop() {
        if (logzioSender != null) logzioSender.stop();
        super.stop();
    }


    @Override
    public void append(LogEvent loggingEvent) {
        if (!loggingEvent.getLoggerName().contains("io.logz.sender")) {
            logzioSender.send(formatMessageAsJson(loggingEvent));
        }
    }


    private JsonObject formatMessageAsJson(LogEvent loggingEvent) {
        JsonObject logMessage = new JsonObject();

        // Adding MDC first, as I dont want it to collide with any one of the following fields
        ReadOnlyStringMap mdcProperties =loggingEvent.getContextData();
        if (mdcProperties != null) {
            mdcProperties.toMap().forEach(logMessage::addProperty);
        }

        logMessage.addProperty(TIMESTAMP, new Date(loggingEvent.getTimeMillis()).toInstant().toString());
        logMessage.addProperty(LOGLEVEL, loggingEvent.getLevel().toString());

        Marker marker = loggingEvent.getMarker();
        if (marker != null) {
            logMessage.addProperty(MARKER, marker.toString());
        }
        logMessage.addProperty(MESSAGE, loggingEvent.getMessage().getFormattedMessage());
        logMessage.addProperty(LOGGER, loggingEvent.getLoggerName());
        logMessage.addProperty(THREAD, loggingEvent.getThreadName());
        ThrowableProxy throwableProxy = loggingEvent.getThrownProxy();
        if (throwableProxy != null ) {
            logMessage.addProperty(EXCEPTION, throwableProxy.getCauseStackTraceAsString() );
        }

        if (additionalFieldsMap != null) {
            additionalFieldsMap.forEach(logMessage::addProperty);
        }
        return logMessage;
    }

    private static String getValueFromSystemEnvironmentIfNeeded(String value) {
        if (value == null)
            return value;
        if (value.startsWith("$")) {
            return System.getenv(value.replace("$", ""));
        }
        return value;
    }


    private class StatusReporter implements SenderStatusReporter {

        @Override
        public void error(String msg) {
            statusLogger.error(msg);
        }

        @Override
        public void error(String msg, Throwable e) {
            statusLogger.error(msg,e);
        }

        @Override
        public void warning(String msg) {
            statusLogger.warn(msg);
        }

        @Override
        public void warning(String msg, Throwable e) {
            statusLogger.warn(msg,e);
        }

        @Override
        public void info(String msg) {
            statusLogger.info(msg);
        }

        @Override
        public void info(String msg, Throwable e) {
            statusLogger.info(msg,e);
        }
    }
}
