package com.gucardev.eronetomany.common;

import java.util.List;

/** One page of a keyset (cursor) paginated result; {@code nextCursor} is null on the last page. */
public record KeysetPage<T>(List<T> items, Long nextCursor) {
}
