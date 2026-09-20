package com.gucardev.caching.examples.dto;

public record StandardShipment(String carrier, int estimatedDays) implements Shipment {
}
