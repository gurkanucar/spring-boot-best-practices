package com.gucardev.kafkaoutboxpatternflightdataupdatesexample.flight.controller;

import com.gucardev.kafkaoutboxpatternflightdataupdatesexample.event.dto.FlightSnapshot;
import com.gucardev.kafkaoutboxpatternflightdataupdatesexample.flight.dto.AircraftChangeRequest;
import com.gucardev.kafkaoutboxpatternflightdataupdatesexample.flight.dto.CancellationRequest;
import com.gucardev.kafkaoutboxpatternflightdataupdatesexample.flight.dto.DelayRequest;
import com.gucardev.kafkaoutboxpatternflightdataupdatesexample.flight.dto.DiversionRequest;
import com.gucardev.kafkaoutboxpatternflightdataupdatesexample.flight.dto.GateChangeRequest;
import com.gucardev.kafkaoutboxpatternflightdataupdatesexample.flight.dto.MovementRequest;
import com.gucardev.kafkaoutboxpatternflightdataupdatesexample.flight.dto.ScheduleFlightRequest;
import com.gucardev.kafkaoutboxpatternflightdataupdatesexample.flight.service.FlightCommandService;
import com.gucardev.kafkaoutboxpatternflightdataupdatesexample.flight.service.FlightQueryService;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The operations-control API. Every successful command returns the new flight state right away;
 * the matching Kafka event follows asynchronously through the outbox.
 */
@RestController
@RequestMapping("/api/flights")
@RequiredArgsConstructor
public class FlightController {

    private final FlightCommandService commands;
    private final FlightQueryService queries;

    @PostMapping
    public ResponseEntity<FlightSnapshot> schedule(@Valid @RequestBody ScheduleFlightRequest request) {
        FlightSnapshot flight = commands.schedule(request);
        return ResponseEntity.created(URI.create("/api/flights/" + flight.flightId())).body(flight);
    }

    @GetMapping
    public List<FlightSnapshot> list(@RequestParam(required = false)
                                     @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return queries.list(date);
    }

    @GetMapping("/{id}")
    public FlightSnapshot get(@PathVariable String id) {
        return queries.get(id);
    }

    @PostMapping("/{id}/delay")
    public FlightSnapshot delay(@PathVariable String id, @Valid @RequestBody DelayRequest request) {
        return commands.delay(id, request);
    }

    @PutMapping("/{id}/gate")
    public FlightSnapshot changeGate(@PathVariable String id, @Valid @RequestBody GateChangeRequest request) {
        return commands.changeGate(id, request);
    }

    @PutMapping("/{id}/aircraft")
    public FlightSnapshot changeAircraft(@PathVariable String id, @Valid @RequestBody AircraftChangeRequest request) {
        return commands.changeAircraft(id, request);
    }

    @PostMapping("/{id}/boarding")
    public FlightSnapshot startBoarding(@PathVariable String id) {
        return commands.startBoarding(id);
    }

    @PostMapping("/{id}/departure")
    public FlightSnapshot depart(@PathVariable String id, @RequestBody(required = false) MovementRequest request) {
        return commands.depart(id, request);
    }

    @PostMapping("/{id}/arrival")
    public FlightSnapshot arrive(@PathVariable String id, @RequestBody(required = false) MovementRequest request) {
        return commands.arrive(id, request);
    }

    @PostMapping("/{id}/cancellation")
    public FlightSnapshot cancel(@PathVariable String id, @Valid @RequestBody CancellationRequest request) {
        return commands.cancel(id, request);
    }

    @PostMapping("/{id}/diversion")
    public FlightSnapshot divert(@PathVariable String id, @Valid @RequestBody DiversionRequest request) {
        return commands.divert(id, request);
    }
}
