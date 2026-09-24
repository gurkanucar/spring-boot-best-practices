package com.gucardev.kafkaoutboxpatternflightdataupdatesexample.event.dto;

import java.time.Instant;

/**
 * What exactly changed. The event carries it next to the full flight snapshot, so a consumer can
 * react to the change (e.g. notify passengers of a gate change) without diffing snapshots.
 */
public sealed interface FlightChange {

    FlightEventType eventType();

    record Scheduled(Instant scheduledDeparture, Instant scheduledArrival) implements FlightChange {
        public FlightEventType eventType() {
            return FlightEventType.FLIGHT_SCHEDULED;
        }
    }

    record Delayed(Instant previousEstimatedDeparture, Instant estimatedDeparture, Instant estimatedArrival,
                   long delayMinutes, String delayCode, String reason) implements FlightChange {
        public FlightEventType eventType() {
            return FlightEventType.FLIGHT_DELAYED;
        }
    }

    record GateChanged(String previousTerminal, String previousGate, String terminal, String gate)
            implements FlightChange {
        public FlightEventType eventType() {
            return FlightEventType.GATE_CHANGED;
        }
    }

    record AircraftChanged(String previousRegistration, String previousType, String registration, String type)
            implements FlightChange {
        public FlightEventType eventType() {
            return FlightEventType.AIRCRAFT_CHANGED;
        }
    }

    record BoardingStarted(String terminal, String gate) implements FlightChange {
        public FlightEventType eventType() {
            return FlightEventType.BOARDING_STARTED;
        }
    }

    record Departed(Instant actualDeparture, long delayMinutes) implements FlightChange {
        public FlightEventType eventType() {
            return FlightEventType.FLIGHT_DEPARTED;
        }
    }

    record Arrived(Instant actualArrival, String arrivalAirport) implements FlightChange {
        public FlightEventType eventType() {
            return FlightEventType.FLIGHT_ARRIVED;
        }
    }

    record Cancelled(String reason) implements FlightChange {
        public FlightEventType eventType() {
            return FlightEventType.FLIGHT_CANCELLED;
        }
    }

    record Diverted(String divertedTo, String reason) implements FlightChange {
        public FlightEventType eventType() {
            return FlightEventType.FLIGHT_DIVERTED;
        }
    }
}
