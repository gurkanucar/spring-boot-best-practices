package com.gucardev.kafkainboxpatternairportsdatafillingexample.kafka.listener;

import com.gucardev.kafkainboxpatternairportsdatafillingexample.event.dto.AirportEvent;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.event.exception.InvalidEventException;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.inbox.entity.InboxSource;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.inbox.service.InboxWriter;
import jakarta.validation.Valid;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AirportEventListener {

    private static final Logger log = LoggerFactory.getLogger(AirportEventListener.class);

    private final InboxWriter inboxWriter;

    @KafkaListener(topics = "${app.kafka.airport-events-topic}")
    public void onAirportEvent(@Payload @Valid AirportEvent event,
                               @Header(KafkaHeaders.RECEIVED_KEY) String key,
                               @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                               @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                               @Header(KafkaHeaders.OFFSET) long offset) {
        if (!Objects.equals(key, event.airportCode())) {
            // The key decides the partition, and the partition decides the ordering. A wrong key
            // would put this airport's events on different partitions.
            throw new InvalidEventException("Message key '%s' does not match airportCode '%s'"
                    .formatted(key, event.airportCode()));
        }

        String position = topic + "-" + partition + "@" + offset;
        InboxWriter.Received received = inboxWriter.store(event, InboxSource.KAFKA, position);
        if (received.duplicate()) {
            log.info("Duplicate event {} at {} ignored (inbox event {})", event.transactionId(), position, received.inboxEventId());
        }
    }
}
