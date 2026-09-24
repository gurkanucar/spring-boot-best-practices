package com.gucardev.kafkainboxpatternairportsdatafillingexample.kafka.controller;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dev/kafka")
public class DevPublishController {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topic;

    public DevPublishController(KafkaTemplate<String, String> kafkaTemplate,
                                @Value("${app.kafka.airport-events-topic}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    @PostMapping("/airport-events/{key}")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Object> publish(@PathVariable String key, @RequestBody String body) throws Exception {
        SendResult<String, String> result = kafkaTemplate.send(topic, key, body).get();
        var metadata = result.getRecordMetadata();
        return Map.of("topic", metadata.topic(), "partition", metadata.partition(), "offset", metadata.offset());
    }
}
