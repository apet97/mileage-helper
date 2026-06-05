package com.cake.clockify.addon.mileage.api.model;

import com.cake.clockify.addon.mileage.clockify.ClockifyUserOption;

import java.util.List;

public record MileageUserOptionsResponse(List<ClockifyUserOption> users, String warning) {
    public static MileageUserOptionsResponse from(List<ClockifyUserOption> users) {
        return new MileageUserOptionsResponse(users, null);
    }

    public static MileageUserOptionsResponse unavailable(String warning) {
        return new MileageUserOptionsResponse(List.of(), warning);
    }
}
