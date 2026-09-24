package com.gucardev.kafkaoutboxpatternflightdataupdatesexample.flight.entity;

/**
 * SCHEDULED -> BOARDING -> DEPARTED -> ARRIVED, with CANCELLED before departure and DIVERTED after it.
 * A diverted flight arrives at the diversion airport.
 */
public enum FlightStatus {
    SCHEDULED,
    BOARDING,
    DEPARTED,
    ARRIVED,
    CANCELLED,
    DIVERTED
}
