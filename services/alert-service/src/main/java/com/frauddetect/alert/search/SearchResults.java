package com.frauddetect.alert.search;

import java.util.List;

/**
 * A page of search hits plus the total match count (independent of page size), used by the search API.
 */
public record SearchResults<T>(List<T> items, long total, int page, int size) {

    public static <T> SearchResults<T> of(List<T> items, long total, int page, int size) {
        return new SearchResults<>(List.copyOf(items), total, page, size);
    }

    public static <T> SearchResults<T> empty(int page, int size) {
        return new SearchResults<>(List.of(), 0L, page, size);
    }
}
