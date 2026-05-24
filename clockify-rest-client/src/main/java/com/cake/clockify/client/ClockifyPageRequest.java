package com.cake.clockify.client;

public record ClockifyPageRequest(int page, int pageSize) {
    public static final int MAX_PAGE_SIZE = 5000;

    public ClockifyPageRequest {
        if (page < 1) throw new IllegalArgumentException("page is 1-based and must be >= 1");
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("pageSize must be between 1 and " + MAX_PAGE_SIZE);
        }
    }

    public static ClockifyPageRequest firstPage(int pageSize) {
        return new ClockifyPageRequest(1, pageSize);
    }
}
