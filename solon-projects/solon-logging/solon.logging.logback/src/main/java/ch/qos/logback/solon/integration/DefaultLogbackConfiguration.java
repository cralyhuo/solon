package ch.qos.logback.solon.integration;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.filter.ThresholdFilter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy;
import ch.qos.logback.core.spi.ScanException;
import ch.qos.logback.core.util.FileSize;
import ch.qos.logback.core.util.OptionHelper;
import ch.qos.logback.solon.SolonAppender;
import ch.qos.logback.solon.SolonTagsConverter;
import org.noear.solon.Solon;

import java.nio.charset.Charset;

/**
 * 默认配置（兼容原生编译）
 *
 * @author noear
 * @since 2.5
 */
public class DefaultLogbackConfiguration {
    void apply(LogbackConfigurator config) {
        synchronized (config.getConfigurationLock()) {
            prepare(config);

            Appender<ILoggingEvent> consoleAppender = consoleAppender(config);
            Appender<ILoggingEvent> solonAppender = solonAppender(config);

            boolean fileEnable = Solon.cfg().getBool("solon.logging.appender.file.enable", true);

            if (fileEnable) {
                Appender<ILoggingEvent> fileAppender = fileAppender(config);
                config.root(Level.TRACE, consoleAppender, fileAppender, solonAppender);
            } else {
                config.root(Level.TRACE, consoleAppender, solonAppender);
            }
        }
    }

    private void prepare(LogbackConfigurator config) {
        config.conversionRule("tags", SolonTagsConverter.class);


        putProperty(config, "APP_NAME", "solon.app.name",
                "solon");

        putProperty(config, "CONSOLE_LOG_PATTERN", "solon.logging.appender.console.pattern",
                "%highlight(%-5level %d{yyyy-MM-dd HH:mm:ss.SSS} [-%t][*%X{traceId}]%tags[%logger{20}]:) %n%msg%n");
        putProperty(config, "CONSOLE_LOG_LEVEL", "solon.logging.appender.console.level",
                "TRACE");

        putProperty(config, "FILE_LOG_EXTENSION", "solon.logging.appender.file.extension",
                ".log");
        putProperty(config, "FILE_LOG_NAME", "solon.logging.appender.file.name",
                "logs/${APP_NAME}");
        putProperty(config, "FILE_LOG_ROLLING", "solon.logging.appender.file.rolling",
                "${FILE_LOG_NAME}_%d{yyyy-MM-dd}_%i${FILE_LOG_EXTENSION}");
        putProperty(config, "FILE_LOG_PATTERN", "solon.logging.appender.file.pattern",
                "%-5level %d{yyyy-MM-dd HH:mm:ss.SSS} [-%t][*%X{traceId}]%tags[%logger{20}]: %n%msg%n");
        putProperty(config, "FILE_LOG_LEVEL", "solon.logging.appender.file.level",
                "INFO");
        putProperty(config, "FILE_LOG_MAX_HISTORY", "solon.logging.appender.file.maxHistory",
                "7");
        putProperty(config, "FILE_LOG_MAX_FILE_SIZE", "solon.logging.appender.file.maxFileSize",
                "10 MB");

        putProperty(config, "FILE_LOG_TOTAL_SIZE_CAP", "solon.logging.appender.file.totalSizeCap",
                "0");


        putProperty(config, "LOGGER_ROOT_LEVEL", "solon.logging.logger.root.level",
                "TRACE");
    }

    private void putProperty(LogbackConfigurator config, String name, String source, String defaultValue) {
        config.getContext()
                .putProperty(name, resolveSolonProp(config, source, defaultValue));
    }

    private Appender<ILoggingEvent> solonAppender(LogbackConfigurator config) {
        SolonAppender appender = new SolonAppender();
        ThresholdFilter filter = new ThresholdFilter();
        filter.setLevel(resolve(config, "TRACE"));
        filter.start();
        appender.addFilter(filter);
        config.appender("SOLON", appender);
        return appender;
    }

    private Appender<ILoggingEvent> consoleAppender(LogbackConfigurator config) {
        ConsoleAppender<ILoggingEvent> appender = new ConsoleAppender<>();
        ThresholdFilter filter = new ThresholdFilter();
        filter.setLevel(resolve(config, "${CONSOLE_LOG_LEVEL}"));
        filter.start();
        appender.addFilter(filter);

        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setPattern(resolve(config, "${CONSOLE_LOG_PATTERN}"));
        config.start(encoder);
        appender.setEncoder(encoder);

        config.appender("CONSOLE", appender);
        return appender;
    }

    private Appender<ILoggingEvent> fileAppender(LogbackConfigurator config) {
        RollingFileAppender<ILoggingEvent> appender = new RollingFileAppender<>();

        ThresholdFilter filter = new ThresholdFilter();
        filter.setLevel(resolve(config, "${FILE_LOG_LEVEL}"));
        filter.start();
        appender.addFilter(filter);

        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setPattern(resolve(config, "${FILE_LOG_PATTERN}"));
        encoder.setCharset(resolveCharset(config, "UTF-8"));
        appender.setEncoder(encoder);
        config.start(encoder);

        appender.setFile(resolve(config, "${FILE_LOG_NAME}.log"));
        appender.setAppend(true);

        setRollingPolicy(appender, config);
        config.appender("FILE", appender);
        return appender;
    }

    private void setRollingPolicy(RollingFileAppender<ILoggingEvent> appender, LogbackConfigurator config) {
        SizeAndTimeBasedRollingPolicy<ILoggingEvent> rollingPolicy = new SizeAndTimeBasedRollingPolicy<>();
        rollingPolicy.setContext(config.getContext());
        rollingPolicy.setFileNamePattern(resolve(config, "${FILE_LOG_ROLLING}"));
        rollingPolicy.setMaxHistory(resolveInt(config, "${FILE_LOG_MAX_HISTORY}}"));
        rollingPolicy.setMaxFileSize(resolveFileSize(config, "${FILE_LOG_MAX_FILE_SIZE}"));

        //rollingPolicy.setCleanHistoryOnStart(resolveBoolean(config, "${LOGBACK_ROLLINGPOLICY_CLEAN_HISTORY_ON_START:-false}"));
        rollingPolicy.setTotalSizeCap(resolveFileSize(config, "${FILE_LOG_TOTAL_SIZE_CAP}"));

        appender.setRollingPolicy(rollingPolicy);
        rollingPolicy.setParent(appender);
        rollingPolicy.setContext(config.getContext());
        rollingPolicy.start();
    }

    private boolean resolveBoolean(LogbackConfigurator config, String val) {
        return Boolean.parseBoolean(resolve(config, val));
    }

    private int resolveInt(LogbackConfigurator config, String val) {
        return Integer.parseInt(resolve(config, val));
    }

    private FileSize resolveFileSize(LogbackConfigurator config, String val) {
        return FileSize.valueOf(resolve(config, val));
    }

    private Charset resolveCharset(LogbackConfigurator config, String val) {
        return Charset.forName(resolve(config, val));
    }

    private String resolveSolonProp(LogbackConfigurator config, String source, String defaultValue) {
        String val = Solon.cfg().getProperty(source);
        if (val == null) {
            val = defaultValue;
        }

        return resolve(config, val);
    }

    private String resolve(LogbackConfigurator config, String val) {
        try {
            return OptionHelper.substVars(val, config.getContext());
        } catch (ScanException ex) {
            throw new RuntimeException(ex);
        }
    }
}
