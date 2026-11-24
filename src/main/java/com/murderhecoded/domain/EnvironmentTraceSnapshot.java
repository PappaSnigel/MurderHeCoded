package com.murderhecoded.domain;

public record EnvironmentTraceSnapshot(
    String id,
    String description,
    String creatorId,
    int timeStepCreated
) {}