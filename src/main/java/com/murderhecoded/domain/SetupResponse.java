package com.murderhecoded.domain;

import lombok.Data;
import java.util.List;

@Data
public class SetupResponse {
    private List<Location> locations;
    private List<Item> items;
    private List<Agent> agents;

    public WorldSetupResponse toGameSetup() {
        return new WorldSetupResponse(
            locations != null ? locations.stream().map(Location::toRecord).toList() : List.of(),
            items != null ? items.stream().map(Item::toRecord).toList() : List.of(),
            agents != null ? agents.stream().map(Agent::toRecord).toList() : List.of()
        );
    }
}