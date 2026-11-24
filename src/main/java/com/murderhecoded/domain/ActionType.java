package com.murderhecoded.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.HashMap;
import java.util.Map;

public enum ActionType {
    MOVE, TALK, MURDER, SEARCH, ACCUSE, ALTER_ENVIRONMENT, MANIPULATE_ITEM, OBSERVE, REFLECT, CASUAL_INTERACT;

    private static final Map<String, ActionType> LOOKUP = new HashMap<>();

    static {
        for (ActionType type : values()) {
            LOOKUP.put(type.name(), type);
        }
        LOOKUP.put("TAKE", MANIPULATE_ITEM);
        LOOKUP.put("DROP", MANIPULATE_ITEM);
        LOOKUP.put("PICK_UP", MANIPULATE_ITEM);
        LOOKUP.put("PICKUP", MANIPULATE_ITEM);
        LOOKUP.put("HIDE", MANIPULATE_ITEM);
        LOOKUP.put("INSPECT_ITEM", MANIPULATE_ITEM);
        LOOKUP.put("USE_ITEM", MANIPULATE_ITEM);
        LOOKUP.put("POISON", MANIPULATE_ITEM);
        LOOKUP.put("SABOTAGE", MANIPULATE_ITEM);
        LOOKUP.put("LACE", MANIPULATE_ITEM);
        LOOKUP.put("INVESTIGATE", SEARCH);
        LOOKUP.put("EXAMINE", SEARCH);
        LOOKUP.put("LOOK", SEARCH);
        LOOKUP.put("KILL", MURDER);
        LOOKUP.put("ATTACK", MURDER);
        LOOKUP.put("SPEAK", TALK);
        LOOKUP.put("CONVERSE", TALK);
        LOOKUP.put("CHAT", TALK);
        LOOKUP.put("ASK", TALK);
        LOOKUP.put("GO", MOVE);
        LOOKUP.put("TRAVEL", MOVE);
        LOOKUP.put("WALK", MOVE);
        LOOKUP.put("LEAVE", MOVE);
        LOOKUP.put("WAIT", REFLECT);
        LOOKUP.put("IDLE", REFLECT);
        LOOKUP.put("REST", REFLECT);
        LOOKUP.put("THINK", REFLECT);
    }

    @JsonCreator
    public static ActionType fromString(String value) {
        if (value == null || value.isBlank()) {
            return OBSERVE;
        }
        String normalized = value.trim().toUpperCase().replace("-", "_").replace(" ", "_");
        return LOOKUP.getOrDefault(normalized, OBSERVE);
    }
}