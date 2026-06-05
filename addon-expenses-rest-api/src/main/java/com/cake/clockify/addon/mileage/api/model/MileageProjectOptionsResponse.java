package com.cake.clockify.addon.mileage.api.model;

import com.cake.clockify.addon.mileage.clockify.ClockifyProjectOption;

import java.util.List;

public record MileageProjectOptionsResponse(List<ClockifyProjectOption> projects, String warning) {
    public static MileageProjectOptionsResponse from(List<ClockifyProjectOption> projects) {
        return new MileageProjectOptionsResponse(projects, null);
    }

    public static MileageProjectOptionsResponse unavailable(String warning) {
        return new MileageProjectOptionsResponse(List.of(), warning);
    }
}
