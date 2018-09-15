package io.logz.log4j2;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static org.assertj.core.api.Assertions.assertThat;

public class OnDiskBuffer extends Log4j2AppenderTest {
    private final static Logger logger = LogManager.getLogger(Log4j2AppenderTest.class);

    @Override
    Logger createLogger(String token, String type, String loggerName, Integer drainTimeout, boolean addHostname, String additionalFields, boolean compressRequests) {
        logger.info("Creating logger {}. token={}, type={}, drainTimeout={}, addHostname={}, additionalFields={}",
                loggerName, token, type, drainTimeout, addHostname, additionalFields);
        Logger log4j2Logger =  LogManager.getLogger(loggerName);
        LogzioAppender.Builder logzioLog4j2AppenderBuilder = LogzioAppender.newBuilder();
        logzioLog4j2AppenderBuilder.setLogzioToken(token);
        logzioLog4j2AppenderBuilder.setLogzioType(type);
        logzioLog4j2AppenderBuilder.setDebug(false);
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
}
