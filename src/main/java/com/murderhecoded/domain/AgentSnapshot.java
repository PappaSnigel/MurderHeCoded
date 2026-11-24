package com.murderhecoded.domain;

import java.util.List;

public record AgentSnapshot(
    String id,
    String name,
    String appearance,
    String secretGoal,
    String role,
    String personality,
    String relationship,
    boolean isAlive,
    boolean hasArrived,
    String currentLocationId,
    boolean hasMurderIntent,
    String targetVictimId,
    List<KnowledgeSnapshot> knowledgeBase,
    List<MemorySnapshot> episodicMemories
) {
    public AgentSnapshot {
        knowledgeBase = knowledgeBase != null ? List.copyOf(knowledgeBase) : List.of();
        episodicMemories = episodicMemories != null ? List.copyOf(episodicMemories) : List.of();
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isDetective() {
        return "agent_0".equals(id) || 
               (role != null && (role.toLowerCase().contains("detective") || role.toLowerCase().contains("detektiv"))) ||
               (secretGoal != null && secretGoal.toLowerCase().contains("solve the murder"));
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isMurderer() {
        if (isDetective()) {
            return false;
        }
        if (hasMurderIntent) {
            return true;
        }
        if (role != null) {
            String lowerRole = role.trim().toLowerCase(java.util.Locale.ROOT);
            if (lowerRole.contains("murderer") || lowerRole.contains("mördare") || lowerRole.equals("killer")) {
                return true;
            }
        }
        if (secretGoal != null) {
            String lowerGoal = secretGoal.trim().toLowerCase(java.util.Locale.ROOT);
            if (!lowerGoal.contains("solve the murder") && !lowerGoal.contains("lösa mordet")
                    && !lowerGoal.startsWith("frame ") && !lowerGoal.startsWith("sätta dit ")
                    && !lowerGoal.startsWith("cover up ") && !lowerGoal.startsWith("dölj ")) {
                if (lowerGoal.startsWith("murder ") || lowerGoal.startsWith("mörda ")
                        || lowerGoal.startsWith("kill ") || lowerGoal.startsWith("döda ")
                        || lowerGoal.matches("^(murder|mörda|kill|döda)\\b.*")) {
                    return true;
                }
            }
        }
        return false;
    }
}