package com.gucardev.kafkainboxpatternairportsdatafillingexample.airport.controller;

import com.gucardev.kafkainboxpatternairportsdatafillingexample.airport.dto.AcceptedResponse;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.airport.dto.AirportResponse;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.airport.dto.AirportUpdateRequest;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.airport.service.AirportQueryService;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.event.dto.AirportEvent;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.inbox.dto.InboxEventResponse;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.inbox.entity.InboxSource;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.inbox.service.InboxAdminService;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.inbox.service.InboxWriter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/airports")
public class AirportController {

    private final AirportQueryService queryService;
    private final InboxWriter inboxWriter;
    private final InboxAdminService inboxAdminService;

    public AirportController(AirportQueryService queryService, InboxWriter inboxWriter,
                             InboxAdminService inboxAdminService) {
        this.queryService = queryService;
        this.inboxWriter = inboxWriter;
        this.inboxAdminService = inboxAdminService;
    }

    @GetMapping
    public List<AirportResponse> list() {
        return queryService.list();
    }

    @GetMapping("/{code}")
    public AirportResponse get(@PathVariable String code) {
        return queryService.get(code);
    }

    /** Every change received for this airport, from both sources, and what happened to it. */
    @GetMapping("/{code}/changes")
    public List<InboxEventResponse> changes(@PathVariable String code) {
        return inboxAdminService.forAirport(code);
    }

    /**
     * Create or update, through the inbox like a Kafka event. Answers 202 Accepted: the change
     * is safely stored and will be applied by the processor (normally within a second).
     * Sending the same transactionId again is safe: nothing new is stored.
     */
    @PutMapping("/{code}")
    public ResponseEntity<AcceptedResponse> put(
            @PathVariable @Pattern(regexp = "[A-Z]{3}", message = "must be a 3-letter IATA code") String code,
            @Valid @RequestBody AirportUpdateRequest request) {
        AirportEvent event = new AirportEvent(request.transactionId(), code, request.version(),
                request.occurredAt(), request.airport());

        InboxWriter.Received received = inboxWriter.store(event, InboxSource.REST, null);
        String statusUrl = "/api/inbox/" + received.inboxEventId();
        return ResponseEntity.accepted()
                .location(URI.create(statusUrl))
                .body(new AcceptedResponse(received.inboxEventId(), event.transactionId(), received.status(),
                        received.duplicate(), statusUrl));
    }
}
