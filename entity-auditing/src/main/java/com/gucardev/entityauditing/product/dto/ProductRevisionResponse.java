package com.gucardev.entityauditing.product.dto;

import java.time.Instant;
import java.util.Set;

/**
 * One entry of a product's history.
 *
 * @param type          CREATED, UPDATED or DELETED
 * @param changedFields properties whose value changed in this revision (from the modified flags);
 *                      empty for CREATED and DELETED, where the whole entity changed
 * @param state         the full state after the change (for DELETED: the last state before it)
 */
public record ProductRevisionResponse(
        Long revision,
        String type,
        Instant at,
        String changedBy,
        Set<String> changedFields,
        ProductSnapshot state) {
}
