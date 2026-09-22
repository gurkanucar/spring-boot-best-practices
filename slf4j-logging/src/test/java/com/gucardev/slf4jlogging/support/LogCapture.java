package com.gucardev.slf4jlogging.support;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.slf4j.LoggerFactory;

/** Captures real Logback events and restores logger state after each test. */
public final class LogCapture implements AutoCloseable {

    private final Logger logger;
    private final Level previousLevel;
    private final boolean previousAdditivity;
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>() {
        @Override
        protected void append(ILoggingEvent event) {
            event.prepareForDeferredProcessing();
            super.append(event);
        }
    };

    public LogCapture(Class<?> type, Level level) {
        this(type.getName(), level);
    }

    public LogCapture(String name, Level level) {
        logger = (Logger) LoggerFactory.getLogger(name);
        previousLevel = logger.getLevel();
        previousAdditivity = logger.isAdditive();
        logger.setLevel(level);
        logger.setAdditive(false);
        appender.setContext(logger.getLoggerContext());
        appender.start();
        logger.addAppender(appender);
    }

    public List<ILoggingEvent> events() {
        return List.copyOf(appender.list);
    }

    @Override
    public void close() {
        logger.detachAppender(appender);
        appender.stop();
        logger.setLevel(previousLevel);
        logger.setAdditive(previousAdditivity);
    }
}
