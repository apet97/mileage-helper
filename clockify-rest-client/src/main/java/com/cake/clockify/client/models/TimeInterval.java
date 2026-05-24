package com.cake.clockify.client.models;

import java.time.Instant;

public record TimeInterval(
        Instant start,
        Instant end,
        String duration
) {}
