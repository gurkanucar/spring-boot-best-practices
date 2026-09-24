package com.gucardev.kafkainboxpatternairportsdatafillingexample.inbox.controller;

import com.gucardev.kafkainboxpatternairportsdatafillingexample.inbox.dto.InboxEventResponse;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.inbox.entity.InboxStatus;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.inbox.service.InboxQueryService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inbox")
@RequiredArgsConstructor
public class InboxController {

    private final InboxQueryService service;

    @GetMapping
    public List<InboxEventResponse> list(@RequestParam(required = false) InboxStatus status) {
        return service.list(status);
    }

    @GetMapping("/{id}")
    public InboxEventResponse get(@PathVariable long id) {
        return service.get(id);
    }
}
