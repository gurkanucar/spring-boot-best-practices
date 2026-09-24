package com.gucardev.kafkainboxpatternairportsdatafillingexample.inbox.controller;

import com.gucardev.kafkainboxpatternairportsdatafillingexample.inbox.dto.InboxEventResponse;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.inbox.entity.InboxStatus;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.inbox.service.InboxAdminService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Operations endpoints. In a real system these belong behind admin authorization. */
@RestController
@RequestMapping("/api/inbox")
public class InboxController {

    private final InboxAdminService service;

    public InboxController(InboxAdminService service) {
        this.service = service;
    }

    /** {@code ?status=FAILED} to see what needs attention. */
    @GetMapping
    public List<InboxEventResponse> list(@RequestParam(required = false) InboxStatus status) {
        return service.list(status);
    }

    @GetMapping("/{id}")
    public InboxEventResponse get(@PathVariable long id) {
        return service.get(id);
    }

    @PostMapping("/{id}/retry")
    public InboxEventResponse retry(@PathVariable long id) {
        return service.retry(id);
    }
}
