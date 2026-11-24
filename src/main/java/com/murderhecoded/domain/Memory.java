package com.murderhecoded.domain;

import java.util.List;

public record Memory(
    int timeStep,
    String locationId,
    String locationName,
    List<String> presentAgentIds,
    List<String> presentAgentNames,
    List<String> presentItemIds,
    List<String> presentItemNames,
    List<String> observedEvents
) {
    public Memory {
        presentAgentIds = presentAgentIds != null ? List.copyOf(presentAgentIds) : List.of();
        presentAgentNames = presentAgentNames != null ? List.copyOf(presentAgentNames) : List.of();
        presentItemIds = presentItemIds != null ? List.copyOf(presentItemIds) : List.of();
        presentItemNames = presentItemNames != null ? List.copyOf(presentItemNames) : List.of();
        observedEvents = observedEvents != null ? List.copyOf(observedEvents) : List.of();
    }

    public MemorySnapshot snapshot() {
        return new MemorySnapshot(
            timeStep,
            locationId,
            locationName,
            presentAgentIds,
            presentAgentNames,
            presentItemIds,
            presentItemNames,
            observedEvents
        );
    }
}
