package com.gucardev.kafkaoutboxpatternflightdataupdatesexample.flight.service;

import com.gucardev.kafkaoutboxpatternflightdataupdatesexample.common.error.ResourceNotFoundException;
import com.gucardev.kafkaoutboxpatternflightdataupdatesexample.event.dto.FlightSnapshot;
import com.gucardev.kafkaoutboxpatternflightdataupdatesexample.flight.repository.FlightRepository;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class FlightQueryService {

    private final FlightRepository repository;

    public FlightSnapshot get(String id) {
        return repository.findById(id).map(FlightSnapshot::from)
                .orElseThrow(() -> new ResourceNotFoundException("Flight " + id + " not found"));
    }

    public List<FlightSnapshot> list(LocalDate departureDate) {
        var flights = departureDate == null ? repository.findAllByOrderByScheduledDepartureAscIdAsc()
                : repository.findByDepartureDateOrderByScheduledDepartureAscIdAsc(departureDate);
        return flights.stream().map(FlightSnapshot::from).toList();
    }
}
