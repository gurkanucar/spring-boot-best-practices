package com.gucardev.caching.examples.dto;

/**
 * Deliberately polymorphic: a field declared as this interface can hold either
 * implementation below. Only Jackson's default-typing {@code @class} hint lets the
 * Redis round trip tell them apart on the way back — a plain JSON shape can't.
 */
public interface Shipment {

    String carrier();
}
