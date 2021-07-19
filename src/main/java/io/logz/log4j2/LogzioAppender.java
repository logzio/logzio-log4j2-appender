package io.logz.log4j2;

import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderFactory;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.validation.constraints.Required;
import org.apache.logging.log4j.core.util.Log4jThreadFactory;
import org.apache.logging.log4j.status.StatusLogger;
import org.apache.logging.log4j.util.ReadOnlyStringMap;

import com.google.common.base.Splitter;
import io.logz.sender.HttpsRequestConfiguration;
import io.logz.sender.LogzioSender;
import io.logz.sender.SenderStatusReporter;
import io.logz.sender.com.google.common.base.Throwables;
import io.logz.sender.com.google.gson.JsonObject;
import io.logz.sender.exceptions.LogzioParameterErrorException;

@Plugin(name = "LogzioAppender", category = "Core", elementType = Appender.ELEMENT_TYPE, printObject = true)
public class LogzioAppender extends AbstractAppender {

    private static final String TIMESTAMP = "@timestamp";
    private static final String LOGLEVEL = "loglevel";
    private static final String MARKER = "marker";
    private static final String MESSAGE = "message";
    private static final String LOGGER = "logger";
    private static final String THREAD = "thread";
    private static final String EXCEPTION = "exception";

    private static final Set<String> reservedFields =  new HashSet<>(Arrays.asList(TIMESTAMP,LOGLEVEL, MARKER, MESSAGE,LOGGER,THREAD,EXCEPTION));

    private static Logger statusLogger = StatusLogger.getLogger();

    @PluginBuilderFactory
    public static Builder newBuilder() {
        return new Builder();
    }

    public static class Builder implements org.apache.logging.log4j.core.util.Builder<LogzioAppender> {

        @PluginElement("Filter")
        private Filter filter;

        @PluginBuilderAttribute
        String name = "LogzioAppender";

        @PluginBuilderAttribute
        String logzioUrl;

        @PluginBuilderAttribute(sensitive = true)
        @Required
        String logzioToken;

        @PluginBuilderAttribute()
        String logzioType = "java";

        @PluginBuilderAttribute
        int drainTimeoutSec = 5;

        @PluginBuilderAttribute
        int fileSystemFullPercentThreshold = 98;

        /**
         * @deprecated use {@link #queueDir}
         */
        @Deprecated
        @PluginBuilderAttribute
        String bufferDir;

        @PluginBuilderAttribute
        String queueDir;

        @PluginBuilderAttribute
        int socketTimeoutMs = 10*1000;

        @PluginBuilderAttribute
        int connectTimeoutMs = 10*1000;

        @PluginBuilderAttribute
        boolean addHostname = false;

        @PluginBuilderAttribute
        String additionalFields;

        @PluginBuilderAttribute
        boolean debug = false;

        @PluginBuilderAttribute
        int gcPersistedQueueFilesIntervalSeconds = 30;

        @PluginBuilderAttribute
        private boolean ignoreExceptions = true;

        @PluginBuilderAttribute
        boolean compressRequests = false;

        @PluginBuilderAttribute
        boolean inMemoryQueue = false;

        @PluginBuilderAttribute
        long inMemoryQueueCapacityBytes  = 100 * 1024 *1024;

        @PluginBuilderAttribute
        long inMemoryLogsCountCapacity  = DONT_LIMIT_CAPACITY;

        @PluginBuilderAttribute
        String disabled = "false";

        @Override
        public LogzioAppender build() {
            return new LogzioAppender(name, filter, ignoreExceptions, logzioUrl, logzioToken, logzioType,
                    drainTimeoutSec, fileSystemFullPercentThreshold, queueDir == null ? bufferDir : queueDir, socketTimeoutMs, connectTimeoutMs,
                    addHostname, additionalFields, debug, gcPersistedQueueFilesIntervalSeconds, compressRequests,
                    inMemoryQueue, inMemoryQueueCapacityBytes, inMemoryLogsCountCapacity, disabled);
        }

        public Builder setFilter(Filter filter) {
            this.filter = filter;
            return this;
        }

        public Builder setIgnoreExceptions(final boolean ignoreExceptions) {
            this.ignoreExceptions = ignoreExceptions;
            return this;
        }

        public Builder setName(String name) {
            this.name = name;
            return this;
        }

        public Builder setLogzioUrl(String logzioUrl) {
            this.logzioUrl = logzioUrl;
            return this;
        }

        public Builder setLogzioToken(String logzioToken) {
            this.logzioToken = logzioToken;
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

        /**
         * @param queueDir: queue file path
         * @deprecated use {@link #setQueueDir(String)}
         */
        @Deprecated
        public Builder setBufferDir(String queueDir) {
            this.queueDir = queueDir;
            return this;
        }

        public Builder setQueueDir(String queueDir) {
            this.queueDir = queueDir;
            return this;
        }

        public Builder setSocketTimeoutMs(int socketTimeoutMs) {
            this.socketTimeoutMs = socketTimeoutMs;
            return this;
        }

        public Builder setConnectTimeoutMs(int connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
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

        public Builder setCompressRequests(boolean compressRequests) {
            this.compressRequests = compressRequests;
            return this;
        }

        public Builder setInMemoryQueue(boolean inMemoryQueue) {
            this.inMemoryQueue = inMemoryQueue;
            return this;
        }

        public Builder setInMemoryQueueCapacityBytes(long inMemoryQueueCapacityBytes) {
            this.inMemoryQueueCapacityBytes = inMemoryQueueCapacityBytes;
            return this;
        }

        public Builder setInMemoryLogsCountCapacity(long inMemoryLogsCountCapacity) {
            this.inMemoryLogsCountCapacity = inMemoryLogsCountCapacity;
            return this;
        }

        public Builder setDisabled(boolean disabled) {
            this.disabled = String.valueOf(disabled);
            return this;
        }

    }
    private static final int DONT_LIMIT_CAPACITY = -1;
    private static final int LOWER_PERCENTAGE_FS_SPACE = 1;
    private static final int UPPER_PERCENTAGE_FS_SPACE = 100;
    private LogzioSender logzioSender;
    private final String logzioToken;
    private final String logzioType ;
    private final int drainTimeoutSec;
    private final int fileSystemFullPercentThreshold;
    private final String queueDir;
    private final String logzioUrl;
    private final int connectTimeout;
    private final int socketTimeout;
    private final boolean debug;
    private final boolean addHostname;
    private final int gcPersistedQueueFilesIntervalSeconds;
    private final boolean compressRequests;
    private final boolean inMemoryQueue;
    private final long inMemoryQueueCapacityBytes;
    private final long inMemoryLogsCountCapacity;
    private final Map<String, String> additionalFieldsMap = new HashMap<>();
    private final boolean disabled;

    // need to keep static instances of ScheduledExecutorService per LogzioAppender as
    // the LogzioSender.Builder keep static instances per the given token and type
    private static final Map<String, ScheduledExecutorService> tasksExecutors = new HashMap<>();

    private static final Map<LogzioAppender, LogzioSender> appenderToLogzioSender = new HashMap<>();

    private LogzioAppender(String name, Filter filter, final boolean ignoreExceptions, String url,
                           String token, String type, int drainTimeoutSec, int fileSystemFullPercentThreshold,
                           String queueDir, int socketTimeout, int connectTimeout, boolean addHostname,
                           String additionalFields, boolean debug, int gcPersistedQueueFilesIntervalSeconds,
                           boolean compressRequests, boolean inMemoryQueue,
                           long inMemoryQueueCapacityBytes, long inMemoryLogsCountCapacity, String disabled) {
        super(name, filter, null, ignoreExceptions);
        this.logzioToken = getValueFromSystemEnvironmentIfNeeded(token);
        this.logzioUrl = getValueFromSystemEnvironmentIfNeeded(url);
        this.logzioType = getValueFromSystemEnvironmentIfNeeded(type);
        this.drainTimeoutSec = drainTimeoutSec;
        this.fileSystemFullPercentThreshold = fileSystemFullPercentThreshold;
        this.queueDir = queueDir;
        this.socketTimeout = socketTimeout;
        this.connectTimeout = connectTimeout;
        this.debug = debug;
        this.addHostname = addHostname;
        this.gcPersistedQueueFilesIntervalSeconds = gcPersistedQueueFilesIntervalSeconds;
        this.compressRequests = compressRequests;
        this.inMemoryQueue = inMemoryQueue;
        this.inMemoryQueueCapacityBytes = inMemoryQueueCapacityBytes;
        this.inMemoryLogsCountCapacity = inMemoryLogsCountCapacity;

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
        disabled = getValueFromSystemEnvironmentIfNeeded( disabled );
        this.disabled = disabled != null && ( disabled.trim().equalsIgnoreCase( "true" ));
    }

    public void start() {
        if (disabled) {
            return;
        }
        safeStopLogzioSender();
        HttpsRequestConfiguration conf;
        try {
            conf = getHttpsRequestConfiguration();
        } catch (LogzioParameterErrorException e) {
            statusLogger.error("Some of the configuration parameters of logz.io is wrong: " + e.getMessage(), e);
            return;
        }
        setHostname();
        LogzioSender.Builder logzioSenderBuilder = new LogzioSender
                .Builder()
                .setDebug(debug)
                .setDrainTimeoutSec(drainTimeoutSec)
                .setReporter(new StatusReporter())
                .setHttpsRequestConfiguration(conf);

        if (inMemoryQueue) {
            if (!validateQueueCapacity()) {
                return;
            }

            final ScheduledExecutorService tasksExecutor = safeExecutorCreate(() ->
                    Executors.newScheduledThreadPool(1, Log4jThreadFactory.createDaemonThreadFactory(this.getClass().getSimpleName())));

            logzioSenderBuilder
                    .setTasksExecutor(tasksExecutor)
                    .withInMemoryQueue()
                    .setCapacityInBytes(inMemoryQueueCapacityBytes)
                    .setLogsCountLimit(inMemoryLogsCountCapacity)
                    .endInMemoryQueue();
        } else {
            if (!validateFSFullPercentThreshold()) {
                return;
            }

            File queueDirFile = getQueueDirFile();
            if (queueDirFile == null) {
                return;
            }
            final ScheduledExecutorService tasksExecutor = safeExecutorCreate(() ->
                    Executors.newScheduledThreadPool(3, Log4jThreadFactory.createDaemonThreadFactory(this.getClass().getSimpleName())));

            logzioSenderBuilder
                    .setTasksExecutor(tasksExecutor)
                    .withDiskQueue()
                    .setQueueDir(queueDirFile)
                    .setFsPercentThreshold(fileSystemFullPercentThreshold)
                    .setGcPersistedQueueFilesIntervalSeconds(gcPersistedQueueFilesIntervalSeconds)
                    .endDiskQueue();
        }
        try {
            logzioSender = logzioSenderBuilder.build();
        } catch (LogzioParameterErrorException e) {
            statusLogger.error("Couldn't build logzio sender: " + e.getMessage(), e);
            return;
        }

        synchronized (appenderToLogzioSender) {
            appenderToLogzioSender.put(this, logzioSender);
        }

        logzioSender.start();

        super.start();
    }

    private void setHostname() {
        try {
            if (addHostname) {
                String hostname = InetAddress.getLocalHost().getHostName();
                additionalFieldsMap.put("hostname", hostname);
            }
        } catch (UnknownHostException e) {
            statusLogger.warn("The configuration addHostName was specified but the host could not be resolved, thus the field 'hostname' will not be added", e);
        }
    }

    private HttpsRequestConfiguration getHttpsRequestConfiguration() throws LogzioParameterErrorException {
        return HttpsRequestConfiguration
                .builder()
                .setLogzioListenerUrl(logzioUrl)
                .setSocketTimeout(socketTimeout)
                .setLogzioType(logzioType)
                .setLogzioToken(logzioToken)
                .setConnectTimeout(connectTimeout)
                .setCompressRequests(compressRequests)
                .build();
    }

    private boolean validateQueueCapacity() {
        if (inMemoryQueueCapacityBytes <= 0 && inMemoryQueueCapacityBytes != DONT_LIMIT_CAPACITY) {
            statusLogger.error("inMemoryQueueCapacityBytes should be a non zero integer or " + DONT_LIMIT_CAPACITY);
            return false;
        }
        if (inMemoryLogsCountCapacity <= 0 && inMemoryLogsCountCapacity != DONT_LIMIT_CAPACITY) {
            statusLogger.error("inMemoryLogsCountCapacity should be a non zero integer or " + DONT_LIMIT_CAPACITY);
            return false;
        }
        return true;
    }

    private File getQueueDirFile() {
        String queueDirPath;
        if (queueDir != null) {
            queueDirPath = queueDir;
            File queueFile = new File(queueDirPath);
            if (queueFile.exists()) {
                if (!queueFile.canWrite()) {
                    statusLogger.error("We cant write to your queueDir location: " + queueFile.getAbsolutePath());
                    return null;
                }
            } else {
                if (!queueFile.mkdirs()) {
                    statusLogger.error("We cant create your queueDir location: " + queueFile.getAbsolutePath());
                    return null;
                }
            }
        }
        else {
            queueDirPath = System.getProperty("java.io.tmpdir") + File.separator + "logzio-log4j2-buffer";
        }
        return new File(queueDirPath, logzioType);
    }

    private boolean validateFSFullPercentThreshold() {
        if (!(fileSystemFullPercentThreshold >= LOWER_PERCENTAGE_FS_SPACE && fileSystemFullPercentThreshold <= UPPER_PERCENTAGE_FS_SPACE)) {
            if (fileSystemFullPercentThreshold != DONT_LIMIT_CAPACITY) {
                statusLogger.error("fileSystemFullPercentThreshold should be a number between 1 and 100, or " + DONT_LIMIT_CAPACITY);
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean stop(final long timeout, final TimeUnit timeUnit) {
        if (disabled) {
            return true;
        }

        setStopping();

        boolean stopped = super.stop(timeout, timeUnit, false);

        safeStopLogzioSender();

        setStopped();

        return stopped;
    }

    private void safeStopLogzioSender() {
        if (logzioSender == null) {
            return;
        }

        boolean doStop = false;
        synchronized (appenderToLogzioSender) {
            appenderToLogzioSender.remove(this);

            if (!appenderToLogzioSender.containsValue(logzioSender)) {
                doStop = true;
            }
        }

        if (doStop) {
            statusLogger.info("Stop {}", logzioSender);

            logzioSender.stop();

            safeExecutorTerminate();
        } else {
            statusLogger.info("Stop skipped for reused {}", logzioSender);
        }
    }

    @Override
    public void append(LogEvent logEvent) {
        if (disabled) {
            return;
        }

        if (!logEvent.getLoggerName().contains("io.logz.sender")) {
            logzioSender.send(formatMessageAsJson(logEvent));
        }
    }

    private ScheduledExecutorService safeExecutorCreate(Supplier<ScheduledExecutorService> doCreate) {
        final ScheduledExecutorService tasksExecutor  = doCreate.get();

        synchronized (tasksExecutors) {
            final String key = getExecutorKey();

            safeExecutorTerminate(key);

            statusLogger.info("Created new tasksExecutor: {} for key.length: {}",
                    tasksExecutor, key.length());

            tasksExecutors.put(key, tasksExecutor);
        }

        return tasksExecutor;
    }

    private void safeExecutorTerminate() {
        synchronized (tasksExecutors) {
            safeExecutorTerminate(getExecutorKey());
        }
    }

    private void safeExecutorTerminate(String key) {
        final ScheduledExecutorService tasksExecutor = tasksExecutors.remove(key);

        if (tasksExecutor != null) {
            statusLogger.info("Terminating old tasksExecutor: {} for key.length: {}",
                    tasksExecutor, key.length());

            try {
                tasksExecutor.shutdownNow();

                while (!tasksExecutor.isTerminated()) {
                    Thread.sleep(500);
                }
            } catch (Exception e) {
                statusLogger.error("Failed to stop old executor", e);
            }
        } else {
            statusLogger.info("Skip terminating no tasksExecutor for key.length: {}", key.length());
        }
    }

    private String getExecutorKey() {
        return "" + logzioToken + logzioType;
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
        Throwable throwable = loggingEvent.getThrown();
        if (throwable != null) {
            logMessage.addProperty(EXCEPTION, Throwables.getStackTraceAsString(throwable));
        }

        if (additionalFieldsMap != null) {
            additionalFieldsMap.forEach(logMessage::addProperty);
        }
        return logMessage;
    }

    private static String getValueFromSystemEnvironmentIfNeeded(String value) {
        if (value == null)
            return null;
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
