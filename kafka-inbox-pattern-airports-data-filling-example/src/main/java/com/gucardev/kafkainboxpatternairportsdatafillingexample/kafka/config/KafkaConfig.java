package com.gucardev.kafkainboxpatternairportsdatafillingexample.kafka.config;

import com.gucardev.kafkainboxpatternairportsdatafillingexample.event.exception.InvalidEventException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class KafkaConfig implements KafkaListenerConfigurer {

    static final int PARTITIONS = 3;

    private final Validator validator;

    @Override
    public void configureKafkaListeners(KafkaListenerEndpointRegistrar registrar) {
        registrar.setValidator(new SpringValidatorAdapter(validator));
    }

    @Bean
    NewTopic airportEventsTopic(@Value("${app.kafka.airport-events-topic}") String topic) {
        return TopicBuilder.name(topic).partitions(PARTITIONS).replicas(1).build();
    }

    @Bean
    NewTopic airportEventsDeadLetterTopic(@Value("${app.kafka.airport-events-topic}") String topic) {
        return TopicBuilder.name(topic + "-dlt").partitions(PARTITIONS).replicas(1).build();
    }

    @Bean
    RecordMessageConverter messageConverter(JsonMapper jsonMapper) {
        return new StringJacksonJsonMessageConverter(jsonMapper);
    }

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
