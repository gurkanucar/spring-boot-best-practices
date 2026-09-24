package com.gucardev.kafkainboxpatternairportsdatafillingexample.airport.controller;

import com.gucardev.kafkainboxpatternairportsdatafillingexample.airport.dto.AcceptedResponse;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.airport.dto.AirportResponse;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.airport.dto.AirportUpdateRequest;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.airport.service.AirportQueryService;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.event.dto.AirportEvent;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.inbox.entity.InboxSource;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.inbox.service.InboxWriter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/airports")
@RequiredArgsConstructor
public class AirportController {

    private final AirportQueryService queryService;
    private final InboxWriter inboxWriter;

    @GetMapping
    public List<AirportResponse> list() {
        return queryService.list();
    }

    @GetMapping("/{code}")
    public AirportResponse get(@PathVariable String code) {
        return queryService.get(code);
    }

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
