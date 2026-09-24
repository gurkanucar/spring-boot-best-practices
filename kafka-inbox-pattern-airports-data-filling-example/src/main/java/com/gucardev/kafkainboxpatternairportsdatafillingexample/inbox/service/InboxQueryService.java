package com.gucardev.kafkainboxpatternairportsdatafillingexample.inbox.service;

import com.gucardev.kafkainboxpatternairportsdatafillingexample.common.error.ResourceNotFoundException;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.inbox.dto.InboxEventResponse;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.inbox.entity.InboxEvent;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.inbox.entity.InboxStatus;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.inbox.repository.InboxEventRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InboxQueryService {

    private final InboxEventRepository repository;

    public List<InboxEventResponse> list(InboxStatus status) {
        var page = PageRequest.of(0, 100);
        var events = status == null ? repository.findAllByOrderByIdDesc(page)
                : repository.findByStatusOrderByIdDesc(status, page);
        return events.stream().map(InboxQueryService::toResponse).toList();
    }

    public InboxEventResponse get(long id) {
        return repository.findById(id).map(InboxQueryService::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Inbox event " + id + " not found"));
    }

    private static InboxEventResponse toResponse(InboxEvent e) {
        return new InboxEventResponse(e.getId(), e.getTransactionId(), e.getSource(), e.getAirportCode(), e.getVersion(),
                e.getStatus(), e.getAttempts(), e.getLastError(), e.getKafkaPosition(), e.getReceivedAt(), e.getProcessedAt());
    }
}
