package com.murderhecoded.domain;

import java.util.List;

public record WorldSetupResponse(
    List<LocationRecord> locations,
    List<ItemRecord> items,
    List<AgentRecord> agents
) {
    public SetupResponse toSetupResponse() {
        SetupResponse response = new SetupResponse();
        response.setLocations(locations != null ? locations.stream().map(LocationRecord::toLocation).toList() : List.of());
        response.setItems(items != null ? items.stream().map(ItemRecord::toItem).toList() : List.of());
        response.setAgents(agents != null ? agents.stream().map(AgentRecord::toAgent).toList() : List.of());
        return response;
    }
}
