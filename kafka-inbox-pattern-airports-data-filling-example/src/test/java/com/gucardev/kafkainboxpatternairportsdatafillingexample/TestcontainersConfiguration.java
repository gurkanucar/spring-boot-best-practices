package com.gucardev.kafkainboxpatternairportsdatafillingexample;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
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
    DeadLetterCollector deadLetterCollector() {
        return new DeadLetterCollector();
    }

    /** Collects everything that lands on the dead letter topic, so tests can assert on it. */
    public static class DeadLetterCollector {

        private final BlockingQueue<ConsumerRecord<String, String>> records = new LinkedBlockingQueue<>();

        @KafkaListener(topics = "${app.kafka.airport-events-topic}-dlt", groupId = "test-dead-letters")
        void collect(ConsumerRecord<String, String> record) {
            records.add(record);
        }

        public BlockingQueue<ConsumerRecord<String, String>> records() {
            return records;
        }
    }
}
