package com.gucardev.kafkaoutboxpatternflightdataupdatesexample.event.dto;

public enum FlightEventType {
    FLIGHT_SCHEDULED,
    FLIGHT_DELAYED,
    GATE_CHANGED,
    AIRCRAFT_CHANGED,
    BOARDING_STARTED,
    FLIGHT_DEPARTED,
    FLIGHT_ARRIVED,
    FLIGHT_CANCELLED,
    FLIGHT_DIVERTED
}
