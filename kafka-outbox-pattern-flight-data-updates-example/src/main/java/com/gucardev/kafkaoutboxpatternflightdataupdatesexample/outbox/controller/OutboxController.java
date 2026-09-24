package com.gucardev.kafkaoutboxpatternflightdataupdatesexample.outbox.controller;

import com.gucardev.kafkaoutboxpatternflightdataupdatesexample.outbox.dto.OutboxEventResponse;
import com.gucardev.kafkaoutboxpatternflightdataupdatesexample.outbox.entity.OutboxStatus;
import com.gucardev.kafkaoutboxpatternflightdataupdatesexample.outbox.service.OutboxQueryService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/outbox")
@RequiredArgsConstructor
public class OutboxController {

    private final OutboxQueryService service;

    @GetMapping
    public List<OutboxEventResponse> list(@RequestParam(required = false) OutboxStatus status,
                                          @RequestParam(required = false) String aggregateId) {
        return service.list(status, aggregateId);
    }

    @GetMapping("/{id}")
    public OutboxEventResponse get(@PathVariable long id) {
        return service.get(id);
    }
}
