package com.gucardev.kafkainboxpatternairportsdatafillingexample.airport.dto;

import com.gucardev.kafkainboxpatternairportsdatafillingexample.inbox.entity.InboxStatus;

public record AcceptedResponse(
        long inboxEventId,
        String transactionId,
        InboxStatus status,
        boolean duplicate,
        String statusUrl) {
}
