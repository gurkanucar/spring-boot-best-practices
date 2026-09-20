package com.gucardev.restapidesign.common;

import java.util.List;

/** Slices an already-sorted, already-filtered list the same way for every collection endpoint. */
public final class PageSupport {

    private PageSupport() {
    }

    public static <T> PageResponse<T> paginate(List<T> items, int page, int size) {
        int fromIndex = Math.min(page * size, items.size());
        int toIndex = Math.min(fromIndex + size, items.size());
        return PageResponse.of(items.subList(fromIndex, toIndex), page, size, items.size());
    }
}
