package com.gucardev.validation.constraint.group;

/**
 * Marker interfaces so the same DTO can be validated with different
 * rule sets depending on the scenario (create vs. update).
 */
public final class ValidationGroups {

    private ValidationGroups() {
    }

    /** Create scenario. */
    public interface OnCreate {
    }

    /** Update scenario. */
    public interface OnUpdate {
    }
}
