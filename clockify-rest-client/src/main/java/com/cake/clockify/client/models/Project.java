package com.cake.clockify.client.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Project(String id, String name, Boolean archived) {
}
