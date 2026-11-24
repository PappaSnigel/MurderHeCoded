package com.murderhecoded.domain;

public record Knowledge(String information, int timeStep) {
    public KnowledgeSnapshot snapshot() {
        return new KnowledgeSnapshot(information, timeStep);
    }
}