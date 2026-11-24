package com.murderhecoded.domain;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable persistence document representing the serialized state.json file.
 * Decouples in-memory Spring beans and runtime services from disk serialization.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@SuppressWarnings("PMD")
public record SimulationStateDocument(
        String title,
        @JsonProperty("premise") @JsonAlias({"description", "desc"})
        String premise,
        int currentStep,
        int targetSteps,
        int totalHours,
        int stepMinutes,
        boolean completed,
        @JsonProperty("worldGenerated") @JsonAlias("isWorldGenerated")
        boolean worldGenerated,
        Map<String, Agent> agents,
        @JsonProperty("locations") @JsonAlias("places")
        Map<String, Location> locations,
        Map<String, Item> items,
        Map<String, GlobalEvent> globalEvents,
        Map<Integer, List<TimeLocationEvent>> timelineEvents,
        Map<Integer, SimulationStateSnapshot> snapshots
) {
    public SimulationStateDocument {
        agents = agents != null ? Collections.unmodifiableMap(new LinkedHashMap<>(agents)) : Map.of();
        locations = locations != null ? Collections.unmodifiableMap(new LinkedHashMap<>(locations)) : Map.of();
        items = items != null ? Collections.unmodifiableMap(new LinkedHashMap<>(items)) : Map.of();
        globalEvents = globalEvents != null ? Collections.unmodifiableMap(new LinkedHashMap<>(globalEvents)) : Map.of();
        timelineEvents = timelineEvents != null ? Collections.unmodifiableMap(new LinkedHashMap<>(timelineEvents)) : Map.of();
        snapshots = snapshots != null ? Collections.unmodifiableMap(new LinkedHashMap<>(snapshots)) : Map.of();
    }

    public Map<String, Location> places() {
        return locations;
    }

    public static SimulationStateDocument empty() {
        return new SimulationStateDocument(
                "", "", 0, 0, 0, 15, false, false,
                Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of()
        );
    }
}
