package com.cake.clockify.addon.mileage.api.model;

import com.cake.clockify.addon.mileage.clockify.ClockifyUserOption;

import java.util.List;

public record MileageUserOptionsResponse(List<ClockifyUserOption> users) {
}
