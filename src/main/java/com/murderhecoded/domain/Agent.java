package com.murderhecoded.domain;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Data
@SuppressWarnings("PMD.TooManyFields")
public class Agent {
    private String id;
    private String name;
    private String appearance;
    private String secretGoal;
    private String role;
    private String personality;
    private String relationship;
    private boolean isAlive;
    private boolean hasArrived = true;
    private String currentLocationId;
    private boolean hasMurderIntent = false;
    private String targetVictimId;
    private List<Knowledge> knowledgeBase = new ArrayList<>();
    private List<Memory> episodicMemories = new ArrayList<>();
    private int consecutiveReflectCount = 0;
    private int consecutiveObserveCount = 0;
    private int consecutiveSearchCount = 0;
    private int turnsInCurrentLocation = 0;
    private String lastObservedLocationId;
    private boolean hasInspectedCrimeScene = false;
    private List<String> recentActions = new ArrayList<>();

    public void recordAction(int step, String actionSummary) {
        if (recentActions == null) {
            recentActions = new ArrayList<>();
        }
        recentActions.add(String.format("Step %d: %s", step, actionSummary));
        while (recentActions.size() > 5) {
            recentActions.remove(0);
        }
    }

    public boolean hasInspectedCrimeScene() {
        return hasInspectedCrimeScene;
    }

    public boolean addKnowledge(Knowledge knowledge) {
        if (knowledge == null || knowledge.information() == null || knowledge.information().isBlank()) {
            return false;
        }
        if (this.knowledgeBase == null) {
            this.knowledgeBase = new ArrayList<>();
        }
        String newNorm = normalizeKnowledge(knowledge.information());
        if (newNorm.isBlank()) {
            return false;
        }
        boolean exists = this.knowledgeBase.stream()
                .anyMatch(existing -> isSimilarKnowledge(normalizeKnowledge(existing.information()), newNorm));
        if (!exists) {
            this.knowledgeBase.add(knowledge);
            return true;
        }
        return false;
    }

    public static String normalizeKnowledge(String info) {
        if (info == null) return "";
        return info.replaceAll("\\s*\\(Location:\\s*[^)]+\\)\\s*$", "")
                   .replaceAll("^\\s*\\[From[^\\]]+\\]:\\s*", "")
                   .replaceAll("^(?i)(?:(?:captain|doctor|detective|officer|chief|dr\\.|mr\\.|ms\\.|mrs\\.)(?:\\s+[a-z]+)?,?\\s*|(?:hey|listen|look|hello|hi),?\\s*)+", "")
                   .trim()
                   .toLowerCase(java.util.Locale.ROOT);
    }

    public static boolean isSimilarKnowledge(String a, String b) {
        if (a == null || b == null) return false;
        if (a.equals(b)) return true;
        if (a.length() > 20 && (a.contains(b) || b.contains(a))) return true;

        String[] tokensA = a.split("\\W+");
        String[] tokensB = b.split("\\W+");
        java.util.Set<String> setA = java.util.Arrays.stream(tokensA).filter(s -> s.length() > 2).collect(Collectors.toSet());
        java.util.Set<String> setB = java.util.Arrays.stream(tokensB).filter(s -> s.length() > 2).collect(Collectors.toSet());
        if (setA.isEmpty() || setB.isEmpty()) return false;
        long intersection = setA.stream().filter(setB::contains).count();
        double jaccard = (double) intersection / (setA.size() + setB.size() - intersection);
        return jaccard >= 0.70;
    }

    public AgentSnapshot snapshot() {
        return new AgentSnapshot(
            id, name, appearance, secretGoal, role, personality, relationship, isAlive, hasArrived, currentLocationId,
            hasMurderIntent, targetVictimId,
            knowledgeBase.stream().map(Knowledge::snapshot).collect(Collectors.toList()),
            episodicMemories.stream().map(Memory::snapshot).collect(Collectors.toList())
        );
    }

    public boolean isDetective() {
        return "agent_0".equals(id) || 
               (role != null && (role.toLowerCase().contains("detective") || role.toLowerCase().contains("detektiv"))) ||
               (secretGoal != null && secretGoal.toLowerCase().contains("solve the murder"));
    }

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

    public AgentRecord toRecord() {
        return AgentRecord.from(this);
    }

    public static Agent fromRecord(AgentRecord record) {
        return record != null ? record.toAgent() : null;
    }
}