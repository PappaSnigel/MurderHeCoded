package com.murderhecoded.domain;

import java.util.ArrayList;

public record AgentRecord(
    String id,
    String name,
    String role,
    String appearance,
    String personality,
    String relationship,
    String secretGoal,
    String currentLocationId,
    boolean hasArrived
) {
    public Agent toAgent() {
        Agent agent = new Agent();
        agent.setId(id);
        agent.setName(name);
        agent.setRole(role);
        agent.setAppearance(appearance);
        agent.setPersonality(personality);
        agent.setRelationship(relationship);
        agent.setSecretGoal(secretGoal);
        agent.setCurrentLocationId(currentLocationId);
        agent.setHasArrived(hasArrived);
        agent.setAlive(true);
        agent.setKnowledgeBase(new ArrayList<>());
        return agent;
    }

    public static AgentRecord from(Agent agent) {
        if (agent == null) {
            return null;
        }
        return new AgentRecord(
            agent.getId(),
            agent.getName(),
            agent.getRole(),
            agent.getAppearance(),
            agent.getPersonality(),
            agent.getRelationship(),
            agent.getSecretGoal(),
            agent.getCurrentLocationId(),
            agent.isHasArrived()
        );
    }
}
