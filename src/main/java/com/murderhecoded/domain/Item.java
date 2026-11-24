package com.murderhecoded.domain;

import lombok.Data;
import java.util.List;
import java.util.ArrayList;

@Data
public class Item {
    private String id;
    private String name;
    private ItemType type;
    private String currentLocationId;
    private boolean hidden = false;
    private boolean movable = true;
    private boolean poisoned = false;
    private boolean poisonConsumed = false;
    private String poisonedByAgentId;
    private String poisonTargetAgentId;
    private String poisonDetails;
    private int poisonTimeStep = -1;
    private List<Knowledge> notebookKnowledge = new ArrayList<>(); // Only used if type is NOTEBOOK

    public ItemSnapshot snapshot() {
        List<KnowledgeSnapshot> notebookKnowledgeSnapshots = null;
        if (notebookKnowledge != null) {
             notebookKnowledgeSnapshots = notebookKnowledge.stream().map(Knowledge::snapshot).toList();
        }
        return new ItemSnapshot(id, name, type, currentLocationId, hidden, movable, poisoned, poisonConsumed, poisonedByAgentId, poisonTargetAgentId, poisonDetails, notebookKnowledgeSnapshots);
    }

    public ItemRecord toRecord() {
        return ItemRecord.from(this);
    }

    public static Item fromRecord(ItemRecord record) {
        return record != null ? record.toItem() : null;
    }
}