package com.cake.clockify.addon.mileage.api.model;

import com.cake.clockify.addon.mileage.clockify.ClockifyTaskOption;

import java.util.List;

public record MileageTaskOptionsResponse(List<ClockifyTaskOption> tasks) {
}
