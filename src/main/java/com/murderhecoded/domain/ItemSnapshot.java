package com.murderhecoded.domain;

import java.util.List;

public record ItemSnapshot(
    String id,
    String name,
    ItemType type,
    String currentLocationId,
    boolean hidden,
    boolean movable,
    boolean poisoned,
    boolean poisonConsumed,
    String poisonedByAgentId,
    String poisonTargetAgentId,
    String poisonDetails,
    List<KnowledgeSnapshot> notebookKnowledge
) {
    public ItemSnapshot(String id, String name, ItemType type, String currentLocationId, boolean hidden, boolean movable, boolean poisoned, boolean poisonConsumed, String poisonedByAgentId, String poisonTargetAgentId, List<KnowledgeSnapshot> notebookKnowledge) {
        this(id, name, type, currentLocationId, hidden, movable, poisoned, poisonConsumed, poisonedByAgentId, poisonTargetAgentId, null, notebookKnowledge);
    }

    public ItemSnapshot(String id, String name, ItemType type, String currentLocationId, boolean hidden, boolean movable, List<KnowledgeSnapshot> notebookKnowledge) {
        this(id, name, type, currentLocationId, hidden, movable, false, false, null, null, null, notebookKnowledge);
    }

    public ItemSnapshot(String id, String name, ItemType type, String currentLocationId, boolean hidden, List<KnowledgeSnapshot> notebookKnowledge) {
        this(id, name, type, currentLocationId, hidden, true, false, false, null, null, null, notebookKnowledge);
    }
}