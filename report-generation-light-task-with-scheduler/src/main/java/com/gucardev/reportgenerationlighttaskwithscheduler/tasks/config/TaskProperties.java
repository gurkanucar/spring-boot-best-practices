package com.gucardev.reportgenerationlighttaskwithscheduler.tasks.config;

import com.gucardev.reportgenerationlighttaskwithscheduler.tasks.entity.TaskType;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("tasks")
public record TaskProperties(
        @DefaultValue("2s") Duration pollInterval,
        @DefaultValue("16") int workers,
        Map<TaskType, Integer> concurrency,
        @DefaultValue("20m") Duration stuckAfter,
        @DefaultValue("60s") Duration shutdownWait,
        String instanceId) {

    public TaskProperties {
        concurrency = concurrency == null ? Map.of() : Map.copyOf(concurrency);
        if (instanceId == null || instanceId.isBlank()) {
            instanceId = hostName() + "-" + UUID.randomUUID().toString().substring(0, 8);
        }
    }

    /** Types without a configured limit may use every worker. */
    public int concurrencyOf(TaskType type) {
        return concurrency.getOrDefault(type, workers);
    }

    private static String hostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "unknown-host";
        }
    }
}
