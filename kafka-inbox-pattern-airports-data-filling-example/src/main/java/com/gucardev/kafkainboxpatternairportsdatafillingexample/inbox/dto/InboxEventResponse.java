package com.gucardev.kafkainboxpatternairportsdatafillingexample.inbox.dto;

import com.gucardev.kafkainboxpatternairportsdatafillingexample.inbox.entity.InboxSource;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.inbox.entity.InboxStatus;
import java.time.Instant;

public record InboxEventResponse(
        Long id,
        String transactionId,
        InboxSource source,
        String airportCode,
        long version,
        InboxStatus status,
        int attempts,
        String lastError,
        String kafkaPosition,
        Instant receivedAt,
        Instant processedAt) {
}
