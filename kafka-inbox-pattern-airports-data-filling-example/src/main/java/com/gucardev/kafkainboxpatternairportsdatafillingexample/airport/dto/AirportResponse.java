package com.gucardev.kafkainboxpatternairportsdatafillingexample.airport.dto;

import com.gucardev.kafkainboxpatternairportsdatafillingexample.airport.entity.RunwaySurface;
import java.time.Instant;
import java.util.List;

public record AirportResponse(
        String code,
        String icaoCode,
        String name,
        String city,
        String countryCode,
        String timezone,
        long version,
        String lastTransactionId,
        Instant createdAt,
        Instant updatedAt,
        List<RunwayResponse> runways) {

    public record RunwayResponse(Long id, String designator, int lengthMeters, RunwaySurface surface) {
    }
}
