package com.gucardev.kafkainboxpatternairportsdatafillingexample.airport.service;

import com.gucardev.kafkainboxpatternairportsdatafillingexample.airport.dto.AirportResponse.RunwayResponse;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.airport.dto.AirportResponse;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.airport.entity.Airport;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.airport.repository.AirportRepository;
import com.gucardev.kafkainboxpatternairportsdatafillingexample.common.error.ResourceNotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AirportQueryService {

    private final AirportRepository repository;

    public AirportResponse get(String code) {
        // runways are fetched together with the airport (entity graph)
        return repository.findWithRunwaysByCode(code).map(AirportQueryService::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Airport " + code + " not found"));
    }

    public List<AirportResponse> list() {
        return repository.findAllByOrderByCode().stream().map(AirportQueryService::toResponse).toList();
    }

    private static AirportResponse toResponse(Airport a) {
        return new AirportResponse(a.getCode(), a.getIcaoCode(), a.getName(), a.getCity(), a.getCountryCode(),
                a.getTimezone(), a.getVersion(), a.getLastTransactionId(), a.getCreatedAt(), a.getUpdatedAt(),
                a.getRunways().stream()
                        .map(r -> new RunwayResponse(r.getId(), r.getDesignator(), r.getLengthMeters(), r.getSurface()))
                        .toList());
    }
}
