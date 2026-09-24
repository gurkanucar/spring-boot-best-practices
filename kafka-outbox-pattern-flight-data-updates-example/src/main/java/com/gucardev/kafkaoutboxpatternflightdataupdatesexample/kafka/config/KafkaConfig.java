package com.gucardev.kafkaoutboxpatternflightdataupdatesexample.kafka.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    static final int PARTITIONS = 3;

    // Keyed by flight id: ordering holds per flight, and flights are spread across partitions.
    @Bean
    NewTopic flightUpdatesTopic(@Value("${app.kafka.flight-updates-topic}") String topic) {
        return TopicBuilder.name(topic).partitions(PARTITIONS).replicas(1).build();
    }
}
