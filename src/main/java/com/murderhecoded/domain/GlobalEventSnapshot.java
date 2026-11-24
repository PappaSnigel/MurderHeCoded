package com.murderhecoded.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GlobalEventSnapshot(
    String id,
    String name,
    String description,
    int scheduledStep,
    int durationSteps,
    String targetLocationId,
    String impactType,
    boolean isActive,
    boolean isConcluded
) {}
