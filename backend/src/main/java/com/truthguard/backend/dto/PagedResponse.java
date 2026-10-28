package com.truthguard.backend.dto;

import java.util.List;

public record PagedResponse<T>(
        List<T> items,
        long total,
        int page,
        int size
) {
    public static <T> PagedResponse<T> of(List<T> items, long total, int page, int size) {
        return new PagedResponse<>(items, total, page, size);
    }
}
