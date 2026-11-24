package com.murderhecoded.llm;

public enum LlmRole {
    WORLD("model.world", 0.3, 4096, "World Building"),
    AGENT("model.agent", 0.2, 4096, "Agent Decisions"),
    PROSE("model.prose", 0.8, 8192, "Novel Prose"),
    DIRECTOR("model.director", 0.35, 8192, "Scene Director");

    private final String prefKey;
    private final double defaultTemperature;
    private final int defaultNumCtx;
    private final String displayName;

    LlmRole(String prefKey, double defaultTemperature, int defaultNumCtx, String displayName) {
        this.prefKey = prefKey;
        this.defaultTemperature = defaultTemperature;
        this.defaultNumCtx = defaultNumCtx;
        this.displayName = displayName;
    }

    public String getPrefKey() {
        return prefKey;
    }

    public double getDefaultTemperature() {
        return defaultTemperature;
    }

    public int getDefaultNumCtx() {
        return defaultNumCtx;
    }

    public String getDisplayName() {
        return displayName;
    }
}
