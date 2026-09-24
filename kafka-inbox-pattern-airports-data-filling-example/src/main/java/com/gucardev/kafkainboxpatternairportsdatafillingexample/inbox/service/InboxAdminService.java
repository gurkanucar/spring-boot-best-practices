package com.gucardev.kafkainboxpatternairportsdatafillingexample.inbox.service;

import com.gucardev.kafkainboxpatternairportsdatafillingexample.common.error.ConflictException;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.common.error.ResourceNotFoundException;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.inbox.dto.InboxEventResponse;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.inbox.entity.InboxEvent;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.inbox.entity.InboxStatus;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.inbox.repository.InboxEventRepository;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Operations view of the inbox: what arrived, what happened to it, and a manual retry. */
@Service
@Transactional(readOnly = true)
public class InboxAdminService {

    private static final int LIST_LIMIT = 100;

    private final InboxEventRepository repository;

    public InboxAdminService(InboxEventRepository repository) {
        this.repository = repository;
    }

    public List<InboxEventResponse> list(InboxStatus status) {
        var page = PageRequest.of(0, LIST_LIMIT);
        var events = status == null ? repository.findAllByOrderByIdDesc(page) : repository.findByStatusOrderByIdDesc(status, page);
        return events.stream().map(InboxAdminService::toResponse).toList();
    }

    public InboxEventResponse get(long id) {
        return toResponse(find(id));
    }

    public List<InboxEventResponse> forAirport(String code) {
        return repository.findByAirportCodeOrderById(code).stream().map(InboxAdminService::toResponse).toList();
    }

    /** Only FAILED events can be retried; the processor picks the event up on its next poll. */
    @Transactional
    public InboxEventResponse retry(long id) {
        InboxEvent event = find(id);
        if (event.getStatus() != InboxStatus.FAILED) {
            throw new ConflictException("Only FAILED events can be retried, event " + id + " is " + event.getStatus());
        }
        event.resetForRetry();
        return toResponse(event);
    }

    private InboxEvent find(long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Inbox event " + id + " not found"));
    }

    static InboxEventResponse toResponse(InboxEvent e) {
        return new InboxEventResponse(e.getId(), e.getTransactionId(), e.getSource(), e.getAirportCode(), e.getVersion(),
                e.getStatus(), e.getAttempts(), e.getLastError(), e.getKafkaPosition(), e.getReceivedAt(), e.getProcessedAt());
    }
}
