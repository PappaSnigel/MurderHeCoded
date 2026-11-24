package com.murderhecoded.domain;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum ItemType {
    WEAPON, VALUABLE, CLUE, NOTEBOOK;

    @JsonCreator
    public static ItemType fromString(String value) {
        if (value == null || value.isBlank()) {
            return CLUE;
        }
        String clean = value.trim().toUpperCase(java.util.Locale.ROOT).replace("-", "_").replace(" ", "_");
        for (ItemType t : values()) {
            if (t.name().equals(clean)) {
                return t;
            }
        }
        if (clean.contains("WEAPON") || clean.contains("GUN") || clean.contains("KNIFE") || clean.contains("POISON") || clean.contains("VAPEN") || clean.contains("PISTOL") || clean.contains("DAGGER") || clean.contains("BLADE")) {
            return WEAPON;
        }
        if (clean.contains("VALUABLE") || clean.contains("MONEY") || clean.contains("JEWEL") || clean.contains("GOLD") || clean.contains("VÄRDEFULL") || clean.contains("CASH") || clean.contains("KEY") || clean.contains("RING")) {
            return VALUABLE;
        }
        if (clean.contains("NOTE") || clean.contains("DIARY") || clean.contains("BOOK") || clean.contains("ANTECKNING") || clean.contains("LETTER") || clean.contains("DOCUMENT")) {
            return NOTEBOOK;
        }
        return CLUE;
    }
}