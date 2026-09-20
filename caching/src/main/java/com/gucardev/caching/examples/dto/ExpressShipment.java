package com.gucardev.caching.examples.dto;

public record ExpressShipment(String carrier, boolean signatureRequired) implements Shipment {
}
