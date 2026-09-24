package com.gucardev.kafkaoutboxpatternflightdataupdatesexample.outbox.service;

import com.gucardev.kafkaoutboxpatternflightdataupdatesexample.common.error.ResourceNotFoundException;
import com.gucardev.kafkaoutboxpatternflightdataupdatesexample.outbox.dto.OutboxEventResponse;
import com.gucardev.kafkaoutboxpatternflightdataupdatesexample.outbox.entity.OutboxEvent;
import com.gucardev.kafkaoutboxpatternflightdataupdatesexample.outbox.entity.OutboxStatus;
import com.gucardev.kafkaoutboxpatternflightdataupdatesexample.outbox.repository.OutboxEventRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OutboxQueryService {

    private final OutboxEventRepository repository;

    /** All events of one aggregate (oldest first), or the latest 100 events, optionally by status. */
    public List<OutboxEventResponse> list(OutboxStatus status, String aggregateId) {
        List<OutboxEvent> events;
        if (aggregateId != null) {
            events = status == null ? repository.findByAggregateIdOrderById(aggregateId)
                    : repository.findByAggregateIdAndStatusOrderById(aggregateId, status);
        } else {
            var page = PageRequest.of(0, 100);
            events = status == null ? repository.findAllByOrderByIdDesc(page)
                    : repository.findByStatusOrderByIdDesc(status, page);
        }
        return events.stream().map(OutboxQueryService::toResponse).toList();
    }

    public OutboxEventResponse get(long id) {
        return repository.findById(id).map(OutboxQueryService::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Outbox event " + id + " not found"));
    }

    private static OutboxEventResponse toResponse(OutboxEvent e) {
        return new OutboxEventResponse(e.getId(), e.getEventId(), e.getAggregateType(), e.getAggregateId(),
                e.getEventType(), e.getTopic(), e.getStatus(), e.getAttempts(), e.getLastError(), e.getKafkaPosition(),
                e.getCreatedAt(), e.getNextAttemptAt(), e.getSentAt());
    }
}
