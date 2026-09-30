package com.gucardev.jwtauthrefreshtokenroles.common.cleanup;

import java.time.Instant;

/**
 * Something that stores expiring rows. ExpiredEntryCleanupJob calls every bean of this type, so a
 * store gets cleaned up just by implementing it — without widening the store's own interface.
 */
public interface ExpiredEntryCleaner {

    /** Human-readable name for log lines, e.g. "refresh tokens". */
    String name();

    /** Deletes rows that expired before {@code now}; returns how many were deleted. */
    int deleteExpired(Instant now);
}
