package com.gucardev.kafkainboxpatternairportsdatafillingexample.kafka.config;

import com.gucardev.kafkainboxpatternairportsdatafillingexample.event.exception.InvalidEventException;
import jakarta.validation.Validator;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.KafkaListenerConfigurer;
import org.springframework.kafka.config.KafkaListenerEndpointRegistrar;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.converter.RecordMessageConverter;
import org.springframework.kafka.support.converter.StringJacksonJsonMessageConverter;
import org.springframework.util.backoff.ExponentialBackOff;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class KafkaConfig implements KafkaListenerConfigurer {

    static final int PARTITIONS = 3;

    private final Validator validator;

    public KafkaConfig(Validator validator) {
        this.validator = validator;
    }

    /**
     * Makes {@code @Valid} work on {@code @KafkaListener} parameters: the same Bean Validation
     * rules as {@code @Valid @RequestBody} on the REST side. A violation throws a
     * MethodArgumentNotValidException, which is never retried and goes to the dead letter topic.
     */
    @Override
    public void configureKafkaListeners(KafkaListenerEndpointRegistrar registrar) {
        registrar.setValidator(new SpringValidatorAdapter(validator));
    }

    /** Created at startup if missing (topic auto-creation is off in docker-compose.yml). */
    @Bean
    NewTopic airportEventsTopic(@Value("${app.kafka.airport-events-topic}") String topic) {
        return TopicBuilder.name(topic).partitions(PARTITIONS).replicas(1).build();
    }

    /**
     * The dead letter topic. Spring Kafka 4 names it {@code <topic>-dlt} by default (3.x used
     * {@code <topic>.DLT}). Same partition count: a failed record goes to the same partition number.
     */
    @Bean
    NewTopic airportEventsDeadLetterTopic(@Value("${app.kafka.airport-events-topic}") String topic) {
        return TopicBuilder.name(topic + "-dlt").partitions(PARTITIONS).replicas(1).build();
    }

    /**
     * Lets listeners take the event type directly ({@code onAirportEvent(AirportEvent event, ...)}).
     * Spring Boot applies this bean to the auto-configured listener container factory.
     *
     * <p>Converting here, not in a JSON value deserializer: the record value stays the original
     * String, so a message that cannot be converted is published to the DLT exactly as it arrived,
     * with the same String serializer. With a JSON deserializer a malformed message would have to
     * be wrapped in an ErrorHandlingDeserializer (otherwise it fails on every poll and blocks the
     * partition) and dead-lettered as raw bytes.
     */
    @Bean
    RecordMessageConverter messageConverter(JsonMapper jsonMapper) {
        return new StringJacksonJsonMessageConverter(jsonMapper);
    }

    /**
     * What happens when the listener throws. Spring Boot applies this bean to the auto-configured
     * listener container factory.
     * <ul>
     *   <li>Messages that can never succeed: not valid JSON (conversion error), constraint
     *       violations ({@code @Valid}) and {@link InvalidEventException} (wrong key). Spring Kafka
     *       does not retry the first two by default; the third is registered below. Straight to
     *       {@code airport-events-dlt}. Retrying cannot fix it, and blocking the partition for it
     *       would stop every airport behind it.</li>
     *   <li>Anything else (typically the database being unreachable while storing into the inbox):
     *       retried with exponential backoff for about a minute, then also sent to the DLT.</li>
     * </ul>
     * The offset is committed after the record was stored or dead-lettered, never before.
     */
    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaOperations<Object, Object> kafkaOperations) {
        ExponentialBackOff backOff = new ExponentialBackOff(1_000, 2.0);
        backOff.setMaxInterval(10_000);
        backOff.setMaxElapsedTime(60_000);

        DefaultErrorHandler handler = new DefaultErrorHandler(new DeadLetterPublishingRecoverer(kafkaOperations), backOff);
        handler.addNotRetryableExceptions(InvalidEventException.class);
        return handler;
    }
}
