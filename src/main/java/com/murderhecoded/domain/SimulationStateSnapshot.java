package com.murderhecoded.domain;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SimulationStateSnapshot(
        int timeStep,
        Map<String, AgentSnapshot> agents,
        @JsonProperty("locations") @JsonAlias("places")
        Map<String, LocationSnapshot> locations,
        Map<String, ItemSnapshot> items,
        List<GlobalEventSnapshot> globalEvents
) {
    public SimulationStateSnapshot {
        agents = agents != null ? Map.copyOf(agents) : Map.of();
        locations = locations != null ? Map.copyOf(locations) : Map.of();
        items = items != null ? Map.copyOf(items) : Map.of();
        globalEvents = globalEvents != null ? List.copyOf(globalEvents) : List.of();
    }

    public SimulationStateSnapshot(int timeStep, Map<String, AgentSnapshot> agents, Map<String, LocationSnapshot> locations, Map<String, ItemSnapshot> items) {
        this(timeStep, agents, locations, items, List.of());
    }

    /**
     * Backward-compatible alias for locations().
     */
    public Map<String, LocationSnapshot> places() {
        return locations;
    }
}