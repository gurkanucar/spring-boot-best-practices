package com.gucardev.kafkaoutboxpatternflightdataupdatesexample;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.KafkaListener;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Real Postgres and Kafka in containers. {@code @ServiceConnection} points the datasource and
 * {@code spring.kafka.bootstrap-servers} at them, so no URLs are configured by hand.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer("postgres:17-alpine");
    }

    @Bean
    @ServiceConnection
    KafkaContainer kafka() {
        return new KafkaContainer("apache/kafka:latest");
    }

    @Bean
    FlightUpdatesCollector flightUpdatesCollector() {
        return new FlightUpdatesCollector();
    }

    /** A downstream consumer (think: airport display boards) that records what it received. */
    public static class FlightUpdatesCollector {

        private final List<ConsumerRecord<String, String>> records = new CopyOnWriteArrayList<>();

        @KafkaListener(topics = "${app.kafka.flight-updates-topic}", groupId = "test-flight-updates",
                properties = "auto.offset.reset=earliest")
        void collect(ConsumerRecord<String, String> record) {
            records.add(record);
        }

        /** Records of one flight, in the order they were received. */
        public List<ConsumerRecord<String, String>> recordsFor(String flightId) {
            return records.stream().filter(r -> flightId.equals(r.key())).toList();
        }
    }
}
