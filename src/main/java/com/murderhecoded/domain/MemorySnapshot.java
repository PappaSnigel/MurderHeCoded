package com.murderhecoded.domain;

import java.util.List;

public record MemorySnapshot(
    int timeStep,
    String locationId,
    String locationName,
    List<String> presentAgentIds,
    List<String> presentAgentNames,
    List<String> presentItemIds,
    List<String> presentItemNames,
    List<String> observedEvents
) {
    public MemorySnapshot {
        presentAgentIds = presentAgentIds != null ? List.copyOf(presentAgentIds) : List.of();
        presentAgentNames = presentAgentNames != null ? List.copyOf(presentAgentNames) : List.of();
        presentItemIds = presentItemIds != null ? List.copyOf(presentItemIds) : List.of();
        presentItemNames = presentItemNames != null ? List.copyOf(presentItemNames) : List.of();
        observedEvents = observedEvents != null ? List.copyOf(observedEvents) : List.of();
    }
}
