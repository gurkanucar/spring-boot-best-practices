package com.gucardev.kafkainboxpatternairportsdatafillingexample.airport.dto;

import com.gucardev.kafkainboxpatternairportsdatafillingexample.inbox.entity.InboxStatus;

/**
 * Answer to a REST write: the change is stored, not necessarily applied yet.
 *
 * @param duplicate true when this transaction id had been received before (nothing new stored)
 * @param statusUrl where to follow what happens to the change
 */
public record AcceptedResponse(
        long inboxEventId,
        String transactionId,
        InboxStatus status,
        boolean duplicate,
        String statusUrl) {
}
