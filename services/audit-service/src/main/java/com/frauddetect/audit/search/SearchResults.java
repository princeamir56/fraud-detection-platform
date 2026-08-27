package com.frauddetect.audit.search;

import java.util.List;

/**
 * A page of search hits plus the total number of matches, echoed back with the requested paging so
 * clients can render pagination without a second round-trip.
 */
public record SearchResults<T>(List<T> items, long total, int page, int size) {

    public static <T> SearchResults<T> of(List<T> items, long total, int page, int size) {
        return new SearchResults<>(items, total, page, size);
    }

    public static <T> SearchResults<T> empty(int page, int size) {
        return new SearchResults<>(List.of(), 0L, page, size);
    }
}
