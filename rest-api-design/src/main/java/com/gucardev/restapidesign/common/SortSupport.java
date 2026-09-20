package com.gucardev.restapidesign.common;

import java.util.Comparator;
import java.util.Map;

/**
 * Resolves ?sortBy=field&amp;sortDir=asc|desc into a Comparator. Deliberately NOT a
 * single "field,dir" query value: Spring's default converter splits a comma-containing
 * request param into a List by comma, which silently breaks that format.
 */
public final class SortSupport {

    private SortSupport() {
    }

    public static <T> Comparator<T> resolve(String sortBy, String sortDir, Map<String, Comparator<T>> availableSorts) {
        Comparator<T> comparator = availableSorts.get(sortBy);
        if (comparator == null) {
            throw new IllegalArgumentException("Unsupported sort field: " + sortBy);
        }
        return "desc".equalsIgnoreCase(sortDir) ? comparator.reversed() : comparator;
    }
}
