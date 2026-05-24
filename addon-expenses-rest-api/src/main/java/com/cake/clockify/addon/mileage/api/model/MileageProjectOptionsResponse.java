package com.cake.clockify.addon.mileage.api.model;

import com.cake.clockify.addon.mileage.clockify.ClockifyProjectOption;

import java.util.List;

public record MileageProjectOptionsResponse(List<ClockifyProjectOption> projects) {
}
