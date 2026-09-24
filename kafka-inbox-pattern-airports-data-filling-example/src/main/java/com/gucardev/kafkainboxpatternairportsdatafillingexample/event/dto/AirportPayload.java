package com.gucardev.kafkainboxpatternairportsdatafillingexample.event.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.ZoneId;
import java.util.List;

/** The airport's full state, including its airport-specific details (the runways). */
public record AirportPayload(
        @NotBlank @Pattern(regexp = "[A-Z]{4}", message = "must be a 4-letter ICAO code") String icaoCode,
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 100) String city,
        @NotBlank @Pattern(regexp = "[A-Z]{2}", message = "must be a 2-letter ISO country code") String countryCode,
        @NotBlank @Size(max = 50) String timezone,
        @NotNull List<@NotNull @Valid RunwayPayload> runways) {

    // Rules across fields, still plain Bean Validation: @AssertTrue on a boolean "getter".
    // @JsonIgnore keeps them out of the JSON.

    @JsonIgnore
    @AssertTrue(message = "must be a known time zone, e.g. Europe/Istanbul")
    public boolean isTimezoneKnown() {
        return timezone == null || ZoneId.getAvailableZoneIds().contains(timezone);
    }

    @JsonIgnore
    @AssertTrue(message = "runway designators must be unique")
    public boolean isRunwayDesignatorsUnique() {
        return runways == null || runways.stream().map(RunwayPayload::designator).distinct().count() == runways.size();
    }
}
