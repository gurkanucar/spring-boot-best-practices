package com.gucardev.slf4jlogging.async;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

/** MDC is thread-local: capture on submission, install on the worker, restore on exit. */
public class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable task) {
        var callerContext = MDC.getCopyOfContextMap();
        return () -> {
            var workerContext = MDC.getCopyOfContextMap();
            try {
                if (callerContext == null) {
                    MDC.clear();
                } else {
                    MDC.setContextMap(callerContext);
                }
                task.run();
            } finally {
                if (workerContext == null) {
                    MDC.clear();
                } else {
                    MDC.setContextMap(workerContext);
                }
            }
        };
    }
}
