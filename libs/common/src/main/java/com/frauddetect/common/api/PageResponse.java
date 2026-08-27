package com.frauddetect.common.api;

import java.util.List;

/**
 * Transport-neutral pagination envelope so REST responses don't leak Spring Data's
 * {@code Page} type (which is not a stable serialization contract).
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last) {

    public static <T> PageResponse<T> of(List<T> content, int page, int size, long totalElements) {
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        boolean last = page >= totalPages - 1;
        return new PageResponse<>(content, page, size, totalElements, totalPages, last);
    }
}
