package com.gucardev.kafkaoutboxpatternflightdataupdatesexample.outbox.service;

import com.gucardev.kafkaoutboxpatternflightdataupdatesexample.outbox.config.OutboxProperties;
import com.gucardev.kafkaoutboxpatternflightdataupdatesexample.outbox.entity.OutboxEvent;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OutboxKafkaSender {

    public static final String EVENT_ID_HEADER = "eventId";
    public static final String EVENT_TYPE_HEADER = "eventType";
    public static final String AGGREGATE_TYPE_HEADER = "aggregateType";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OutboxProperties properties;

    /**
     * Sends one event and waits for the broker's acknowledgement.
     *
     * @return the record's position, {@code topic-partition@offset}
     */
    public String send(OutboxEvent event) {
        // The aggregate id is the key: all events of a flight go to the same partition, in order.
        var record = new ProducerRecord<>(event.getTopic(), null, event.getAggregateId(), event.getPayload());
        header(record, EVENT_ID_HEADER, event.getEventId().toString());
        header(record, EVENT_TYPE_HEADER, event.getEventType());
        header(record, AGGREGATE_TYPE_HEADER, event.getAggregateType());
        try {
            RecordMetadata metadata = kafkaTemplate.send(record)
                    .get(properties.sendTimeout().toMillis(), TimeUnit.MILLISECONDS)
                    .getRecordMetadata();
            return metadata.topic() + "-" + metadata.partition() + "@" + metadata.offset();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new KafkaException("Interrupted while sending event " + event.getEventId(), e);
        } catch (ExecutionException e) {
            throw new KafkaException("Kafka did not accept event " + event.getEventId(), e.getCause());
        } catch (TimeoutException e) {
            throw new KafkaException("No acknowledgement for event " + event.getEventId()
                    + " within " + properties.sendTimeout(), e);
        }
    }

    private static void header(ProducerRecord<String, String> record, String name, String value) {
        record.headers().add(name, value.getBytes(StandardCharsets.UTF_8));
    }
}
