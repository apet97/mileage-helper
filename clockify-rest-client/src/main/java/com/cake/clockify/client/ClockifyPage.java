package com.cake.clockify.client;

import java.util.List;

public record ClockifyPage<T>(List<T> items, ClockifyPageRequest request, boolean lastPage) {
    public ClockifyPage {
        items = List.copyOf(items);
    }

    public static boolean parseLastPageHeader(String value) {
        return value != null && Boolean.parseBoolean(value.trim());
    }
}
