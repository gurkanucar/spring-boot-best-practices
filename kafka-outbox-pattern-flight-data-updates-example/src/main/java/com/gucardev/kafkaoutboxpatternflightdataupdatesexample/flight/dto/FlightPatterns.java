package com.gucardev.kafkaoutboxpatternflightdataupdatesexample.flight.dto;

final class FlightPatterns {

    static final String GATE = "[A-Z0-9]{1,6}";
    static final String GATE_MESSAGE = "must be 1 to 6 letters or digits";

    static final String REGISTRATION = "[A-Z0-9]{1,2}-?[A-Z0-9]{1,5}";
    static final String REGISTRATION_MESSAGE = "must be an aircraft registration, e.g. TC-LGA";

    static final String AIRCRAFT_TYPE = "[A-Z][A-Z0-9]{1,3}";
    static final String AIRCRAFT_TYPE_MESSAGE = "must be an ICAO aircraft type designator, e.g. A21N";

    private FlightPatterns() {
    }
}
