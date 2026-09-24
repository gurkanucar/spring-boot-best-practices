package com.gucardev.kafkainboxpatternairportsdatafillingexample.event.dto;

import com.gucardev.kafkainboxpatternairportsdatafillingexample.event.validation.ValidAirport;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

@ValidAirport
public record AirportPayload(
        @NotBlank @Pattern(regexp = "[A-Z]{4}", message = "must be a 4-letter ICAO code") String icaoCode,
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 100) String city,
        @NotBlank @Pattern(regexp = "[A-Z]{2}", message = "must be a 2-letter ISO country code") String countryCode,
        @NotBlank @Size(max = 50) String timezone,
        @NotNull List<@NotNull @Valid RunwayPayload> runways) {
}
