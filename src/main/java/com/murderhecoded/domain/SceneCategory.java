package com.murderhecoded.domain;

public enum SceneCategory {
    ESSENTIAL("Essential Climax", "#c0392b"),       // 8.0 - 10.0: Murder, body discovery, accusation, climax (never cut)
    SUPPORTING("Supporting Mystery", "#d35400"),    // 6.0 - 7.9: Clues, searches, key confrontations, weapon handling
    ATMOSPHERIC("Atmospheric World", "#2980b9"),    // 3.5 - 5.9: World-building, character depth, quiet tension
    DISPENSABLE("Mundane / Transition", "#7f8c8d"); // 0.0 - 3.4: Mundane roaming/observation, cut or summarized

    private final String displayName;
    private final String badgeColor;

    SceneCategory(String displayName, String badgeColor) {
        this.displayName = displayName;
        this.badgeColor = badgeColor;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getBadgeColor() {
        return badgeColor;
    }
}
