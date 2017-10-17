# Logzio Log4j 2 Appender 
Log4j 2 Appender that ships logs using HTTPS bulk
[![Build Status](https://travis-ci.org/logzio/logzio-log4j2-appender.svg?branch=master)](https://travis-ci.org/logzio/logzio-log4j2-appender)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.logz.log4j2/logzio-log4j2-appender/badge.svg)](http://mvnrepository.com/artifact/io.logz.log4j2/logzio-log4j2-appender)

This appender sends logs to your [Logz.io](http://logz.io) account, using non-blocking threading, bulks, and HTTPS encryption. Please note that this appender requires log4j version 2.7 and up, and java 8 and up.

### Technical Information
This appender uses  [LogzioSender](https://github.com/logzio/logzio-java-sender) implementation. Once you send a log, it will be enqueued in the buffer and 100% non-blocking. There is a background task that will handle the log shipment for you. This jar is an "Uber-Jar" that shades both LogzioSender, BigQueue, Gson and Guava to avoid "dependency hell".

### Installation from maven
```xml
    <dependency>
        <groupId>io.logz.log4j2</groupId>
        <artifactId>logzio-log4j2-appender</artifactId>
        <version>1.0.5</version>
    </dependency>
```

### Log4 2 Example Configuration
```xml
    <Appenders>
        <LogzioAppender name="Logzio">
            <logzioToken>your-logzio-personal-token-from-settings</logzioToken>
            <logzioType>myAwesomeType</logzioType>
        </LogzioAppender>
       
    </Appenders>
    <Loggers>
        <Root level="info">
            <AppenderRef ref="Logzio"/>
        </Root>
    </Loggers>
```

### Parameters
| Parameter          | Default                              | Explained  |
| ------------------ | ------------------------------------ | ----- |
| **logzioToken**              | *None*                                 | Your Logz.io token, which can be found under "settings" in your account, If the value begins with `$` then the appender looks for an environment variable with the name specified. For example: `$LOGZIO_TOKEN` will look for environment variable named `LOGZIO_TOKEN` |
| **logzioType**               | *java*                                 | The [log type](https://support.logz.io/hc/en-us/articles/209486049-What-is-Type-) for that appender |
| **drainTimeoutSec**       | *5*                                    | How often the appender should drain the buffer (in seconds) |
| **fileSystemFullPercentThreshold** | *98*                                   | The percent of used file system space at which the appender will stop buffering. When we will reach that percentage, the file system in which the buffer is stored will drop all new logs until the percentage of used space drops below that threshold. Set to -1 to never stop processing new logs |
| **bufferDir**          | *System.getProperty("java.io.tmpdir")* | Where the appender should store the buffer |
| **socketTimeoutMs**       | *10 * 1000*                                    | The socket timeout during log shipment |
| **connectTimeoutMs**       | *10 * 1000*                                    | The connection timeout during log shipment |
| **addHostname**       | *false*                                    | Optional. If true, then a field named 'hostname' will be added holding the host name of the machine. If from some reason there's no defined hostname, this field won't be added |
| **additionalFields**       | *None*                                    | Optional. Allows to add additional fields to the JSON message sent. The format is "fieldName1=fieldValue1;fieldName2=fieldValue2". You can optionally inject an environment variable value using the following format: "fieldName1=fieldValue1;fieldName2=$ENV_VAR_NAME". In that case, the environment variable should be the only value. In case the environment variable can't be resolved, the field will be omitted. |
| **debug**       | *false*                                    | Print some debug messages to stdout to help to diagnose issues |


### Code Example
```java
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class LogzioLog4j2Example {

    public static void main(String[] args) {
        Logger logger = LogManager.getLogger(LogzioLog4j2Example.class);
        
        logger.info("Testing logz.io!");
        logger.warn("Winter is coming");
    }
}
```

### MDC
Each key value you will add to MDC will be added to each log line as long as the thread alive. No further configuration needed.
```java
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;

public class LogzioLog4j2Example {

    public static void main(String[] args) {
        Logger logger = LogManager.getLogger(LogzioLog4j2Example.class);

        ThreadContext.put("Key", "Value");
        logger.info("This log will hold the MDC data as well");
    }
}
```

Will send a log to Logz.io that looks like this:
```
{
    "message": "This log will hold the MDC data as well",
    "Key": "Value",
    ... (all other fields you used to get)
}
```

### Marker
Markers are named objects used to enrich log statements, so each log line will be enriched with its own. No further configuration needed.
```java
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

public class LogzioLog4j2Example {

    public static void main(String[] args) {
        Logger logger = LogManager.getLogger(LogzioLog4j2Example.class);

        Marker marker = MarkerManager.getMarker("Fatal");
        logger.error(marker, "This line has a fatal error");
    }
}
```

Will send a log to Logz.io that looks like this:
```
{
    "message": "This line has a fatal error",
    "Marker": "Fatal",
    ... (all other fields you used to get)
}
```

### Release notes
 - 1.0.5 
   - fix an issue: [Log4j2 version 2.8.2 throws exception - backward compatibility problem ](https://github.com/logzio/logzio-log4j2-appender/issues)
 - 1.0.3 - 1.0.4
   - add error message about reason of 400(BAD REQUEST)
 - 1.0.2
   - Fix problem where logzioToken and logzioUrl could not be environment variables
 - 1.0.1
   - Fixed an issue: [LogzioAppender does not let the JVM exit](https://github.com/logzio/logzio-log4j2-appender/issues/2)  
 - 1.0.0
   - Initial releases
   

### Contribution
 - Fork
 - Code
 - ```mvn test```
 - Issue a PR :)
