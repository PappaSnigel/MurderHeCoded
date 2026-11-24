package com.murderhecoded.service;

import com.murderhecoded.domain.Action;
import com.murderhecoded.domain.ActionType;
import com.murderhecoded.domain.Agent;
import com.murderhecoded.domain.AgentSnapshot;
import com.murderhecoded.domain.GlobalEvent;
import com.murderhecoded.domain.SceneCategory;
import com.murderhecoded.domain.SimulationStateSnapshot;
import com.murderhecoded.domain.TimeLocationEvent;
import com.murderhecoded.repository.SimulationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@SuppressWarnings("PMD")
public class EditorialScorer {

    @Value("${scorer.action.murder:10.0}") private double actionMurder = 10.0;
    @Value("${scorer.action.accuse:9.5}") private double actionAccuse = 9.5;
    @Value("${scorer.action.search:6.5}") private double actionSearch = 6.5;
    @Value("${scorer.action.search-fruitless:3.5}") private double actionSearchFruitless = 3.5;
    @Value("${scorer.action.talk-new-fact:5.5}") private double actionTalkNewFact = 5.5;
    @Value("${scorer.action.talk-no-fact:3.5}") private double actionTalkNoFact = 3.5;
    @Value("${scorer.action.manipulate:5.0}") private double actionManipulate = 5.0;
    @Value("${scorer.action.alter-env:4.5}") private double actionAlterEnv = 4.5;
    @Value("${scorer.action.reflect:2.5}") private double actionReflect = 2.5;
    @Value("${scorer.action.default:1.0}") private double actionDefault = 1.0;

    @Value("${scorer.suspense.max:10.0}") private double suspenseMax = 10.0;
    @Value("${scorer.suspense.detective-present:7.5}") private double suspenseDetective = 7.5;
    @Value("${scorer.suspense.victim-isolated:9.0}") private double suspenseVictimIsolated = 9.0;
    @Value("${scorer.suspense.victim-present:7.5}") private double suspenseVictimPresent = 7.5;
    @Value("${scorer.suspense.search-multiple:6.5}") private double suspenseSearchMultiple = 6.5;
    @Value("${scorer.suspense.hide-item:7.0}") private double suspenseHideItem = 7.0;
    @Value("${scorer.suspense.time-bonus:1.0}") private double suspenseTimeBonus = 1.0;
    @Value("${scorer.suspense.event-alarm:1.5}") private double suspenseEventAlarm = 1.5;
    @Value("${scorer.suspense.event-isolation:1.0}") private double suspenseEventIsolation = 1.0;

    @Value("${scorer.category.essential:7.5}") private double categoryEssential = 7.5;
    @Value("${scorer.category.supporting:5.5}") private double categorySupporting = 5.5;
    @Value("${scorer.category.atmospheric:3.5}") private double categoryAtmospheric = 3.5;

    @Value("${scorer.select.supporting:4.0}") private double selectSupporting = 4.0;
    @Value("${scorer.select.atmospheric:4.5}") private double selectAtmospheric = 4.5;

    public double evaluateImportance(List<TimeLocationEvent> events, SimulationRepository state) {
        if (events == null || events.isEmpty()) {
            return actionDefault;
        }
        double score = actionDefault;
        for (TimeLocationEvent e : events) {
            score = Math.max(score, evaluateEventImportance(e));
            if (score >= actionMurder) {
                return actionMurder;
            }
        }
        return Math.min(actionMurder, Math.round(score * 10.0) / 10.0);
    }

    private double evaluateEventImportance(TimeLocationEvent e) {
        double score = actionDefault;
        for (Action a : e.getActions()) {
            double actionScore = evaluateActionImportance(a, e);
            score = Math.max(score, actionScore);
        }
        
        for (String fact : e.getKnowledgeGained()) {
            if (fact.toLowerCase().contains("dead body") || fact.contains(" MURDER ")) {
                score = Math.max(score, actionAccuse);
            } else if (fact.contains("saw") || fact.contains("suspicious")) {
                score = Math.max(score, actionSearch);
            }
        }
        return score;
    }

    private double evaluateActionImportance(Action a) {
        return evaluateActionImportance(a, null);
    }

    private double evaluateActionImportance(Action a, TimeLocationEvent event) {
        if (a == null || a.getActionType() == null) {
            return actionDefault;
        }
        return switch (a.getActionType()) {
            case MURDER -> actionMurder;
            case ACCUSE -> actionAccuse;
            case MANIPULATE_ITEM -> actionManipulate;
            case SEARCH -> {
                boolean hasFinding = (a.getNewKnowledgeFact() != null 
                        && !a.getNewKnowledgeFact().trim().isEmpty() 
                        && !"None".equalsIgnoreCase(a.getNewKnowledgeFact()))
                        || (a.getTargetItemId() != null && !a.getTargetItemId().isBlank())
                        || (event != null && event.getKnowledgeGained() != null && !event.getKnowledgeGained().isEmpty());
                yield hasFinding ? actionSearch : actionSearchFruitless;
            }
            case TALK -> (a.getNewKnowledgeFact() != null && !"None".equalsIgnoreCase(a.getNewKnowledgeFact())) ? actionTalkNewFact : actionTalkNoFact;
            case ALTER_ENVIRONMENT -> actionAlterEnv;
            case REFLECT -> actionReflect;
            default -> actionDefault;
        };
    }

    public double evaluateSuspense(List<TimeLocationEvent> events, SimulationRepository state) {
        if (events == null || events.isEmpty()) {
            return actionDefault;
        }
        double score = actionDefault;
        for (TimeLocationEvent e : events) {
            score = evaluateEventSuspense(e, state, score);
            if (score >= suspenseMax) {
                return suspenseMax;
            }
        }
        return Math.min(suspenseMax, Math.round(score * 10.0) / 10.0);
    }

    private double evaluateEventSuspense(TimeLocationEvent e, SimulationRepository state, double currentScore) {
        double score = currentScore;
        SimulationStateSnapshot snap = e.getSnapshotBefore();
        if (snap != null && snap.agents() != null) {
            List<AgentSnapshot> agentsInRoom = snap.agents().values().stream()
                    .filter(a -> a.isAlive() && a.hasArrived() && e.getLocationId() != null && e.getLocationId().equals(a.currentLocationId()))
                    .toList();

            boolean hasMurderer = false;
            boolean hasDetective = false;
            String victimId = null;
            boolean hasVictim = false;

            for (AgentSnapshot a : agentsInRoom) {
                if (a.isMurderer()) {
                    hasMurderer = true;
                    victimId = a.targetVictimId();
                }
                if (a.isDetective()) {
                    hasDetective = true;
                }
            }
            if (hasMurderer && victimId != null) {
                String finalVictimId = victimId;
                hasVictim = agentsInRoom.stream().anyMatch(a -> a.id().equals(finalVictimId));
            }

            boolean crimeOccurred = state != null && state.getAgents().values().stream().anyMatch(a -> !a.isAlive());
            int targetSteps = state != null && state.getTargetSteps() > 0 ? state.getTargetSteps() : 144;
            double progress = (double) e.getTimeStep() / targetSteps;

            score = Math.max(score, calculateRoleBasedTension(hasMurderer, hasDetective, hasVictim, agentsInRoom.size(), crimeOccurred, progress));
        }

        if (e.getActions() != null) {
            for (Action a : e.getActions()) {
                if (a.getActionType() == ActionType.MURDER) {
                    return suspenseMax;
                }
                if (a.getActionType() == ActionType.SEARCH && (snap == null || (snap.agents() != null && snap.agents().size() > 1))) {
                    score = Math.max(score, suspenseSearchMultiple);
                }
                if (a.getActionType() == ActionType.MANIPULATE_ITEM && "HIDE".equalsIgnoreCase(a.getInteraction())) {
                    score = Math.max(score, suspenseHideItem);
                }
            }
        }

        if (state != null) {
            score = applyStateTensionModifiers(state, e.getTimeStep(), score);
        }
        return score;
    }

    private double calculateRoleBasedTension(boolean hasMurderer, boolean hasDetective, boolean hasVictim, int roomSize) {
        return calculateRoleBasedTension(hasMurderer, hasDetective, hasVictim, roomSize, false, 0.0);
    }

    private double calculateRoleBasedTension(boolean hasMurderer, boolean hasDetective, boolean hasVictim, int roomSize, boolean crimeOccurred, double progress) {
        if (!hasMurderer) {
            return actionDefault;
        }
        double tension = actionDefault;

        if (hasVictim) {
            if (roomSize <= 2) {
                tension = Math.max(tension, suspenseVictimIsolated);
            } else {
                if (progress < 0.15) {
                    tension = Math.max(tension, 3.5);
                } else if (progress < 0.35) {
                    tension = Math.max(tension, 5.5);
                } else {
                    tension = Math.max(tension, suspenseVictimPresent);
                }
            }
        }

        if (hasDetective) {
            if (crimeOccurred) {
                tension = Math.max(tension, roomSize <= 3 ? 8.5 : suspenseDetective);
            } else {
                tension = Math.max(tension, 3.0);
            }
        }

        return tension;
    }

    private double applyStateTensionModifiers(SimulationRepository state, int eventStep, double currentScore) {
        double score = currentScore;
        int totalTarget = state.getTargetSteps() > 0 ? state.getTargetSteps() : 144;
        if (state.getCurrentStep() >= (totalTarget * 0.30)) {
            score += suspenseTimeBonus;
        }
        for (GlobalEvent ge : state.getActiveGlobalEvents(eventStep)) {
            if ("ALARM".equalsIgnoreCase(ge.getImpactType()) || "ENVIRONMENTAL".equalsIgnoreCase(ge.getImpactType())) {
                score += suspenseEventAlarm;
            } else if ("ISOLATION".equalsIgnoreCase(ge.getImpactType())) {
                score += suspenseEventIsolation;
            }
        }
        return score;
    }

    public double calculateLiveRoomTension(SimulationRepository state, List<Agent> agentsInRoom, int currentStep) {
        if (agentsInRoom == null || agentsInRoom.isEmpty()) {
            return actionDefault;
        }
        
        boolean hasMurderer = false;
        boolean hasDetective = false;
        String victimId = null;
        boolean hasVictim = false;
        
        for (Agent a : agentsInRoom) {
            if (a.isMurderer()) {
                hasMurderer = true;
                victimId = a.getTargetVictimId();
            }
            if (a.isDetective()) {
                hasDetective = true;
            }
        }
        if (hasMurderer && victimId != null) {
            String finalVictimId = victimId;
            hasVictim = agentsInRoom.stream().anyMatch(a -> a.getId().equals(finalVictimId));
        }

        boolean crimeOccurred = state != null && state.getAgents().values().stream().anyMatch(a -> !a.isAlive());
        int targetSteps = state != null && state.getTargetSteps() > 0 ? state.getTargetSteps() : 144;
        double progress = (double) currentStep / targetSteps;

        double tension = calculateRoleBasedTension(hasMurderer, hasDetective, hasVictim, agentsInRoom.size(), crimeOccurred, progress);
        
        if (state != null) {
            tension = applyStateTensionModifiers(state, currentStep, tension);
        }
        return Math.min(suspenseMax, Math.round(tension * 10.0) / 10.0);
    }

    public SceneCategory determineCategory(double totalScore, boolean hasMurderOrAccusal) {
        if (hasMurderOrAccusal || totalScore >= categoryEssential) {
            return SceneCategory.ESSENTIAL;
        } else if (totalScore >= categorySupporting) {
            return SceneCategory.SUPPORTING;
        } else if (totalScore >= categoryAtmospheric) {
            return SceneCategory.ATMOSPHERIC;
        } else {
            return SceneCategory.DISPENSABLE;
        }
    }

    public boolean determineSelection(SceneCategory category, double totalScore, double importanceScore, boolean hasMurderOrAccuse) {
        return hasMurderOrAccuse
                || category == SceneCategory.ESSENTIAL
                || (category == SceneCategory.SUPPORTING && totalScore >= selectSupporting)
                || (category == SceneCategory.ATMOSPHERIC && totalScore >= selectAtmospheric);
    }

    public String determineDramaticFocus(List<TimeLocationEvent> events, SimulationRepository state, String locName) {
        String highImpactFocus = findHighImpactFocus(events, state, locName);
        if (highImpactFocus != null) {
            return highImpactFocus;
        }

        String searchFocus = findInvestigationFocus(events, locName);
        if (searchFocus != null) {
            return searchFocus;
        }

        if (state != null && !events.isEmpty()) {
            int step = events.get(0).getTimeStep();
            List<GlobalEvent> activeEvents = state.getActiveGlobalEvents(step);
            if (!activeEvents.isEmpty()) {
                return activeEvents.get(0).getName() + " in " + locName;
            }
        }

        return "Atmospheric Tension in " + locName;
    }

    private String findHighImpactFocus(List<TimeLocationEvent> events, SimulationRepository state, String locName) {
        for (TimeLocationEvent e : events) {
            for (Action a : e.getActions()) {
                if (a.getActionType() == ActionType.MURDER) {
                    String victim = a.getTargetAgentId();
                    if (state != null && state.getAgents().containsKey(victim)) {
                        victim = state.getAgents().get(victim).getName();
                    }
                    return "The Murder of " + victim + " in " + locName;
                }
                if (a.getActionType() == ActionType.ACCUSE) {
                    return "The Dramatic Accusation in " + locName;
                }
            }
            for (String k : e.getKnowledgeGained()) {
                if (k.toLowerCase().contains("dead body")) {
                    return "Discovery of the Corpse in " + locName;
                }
            }
        }
        return null;
    }

    private String findInvestigationFocus(List<TimeLocationEvent> events, String locName) {
        for (TimeLocationEvent e : events) {
            for (Action a : e.getActions()) {
                if (a.getActionType() == ActionType.TALK) {
                    return "Tense Exchange in " + locName;
                }
                if (a.getActionType() == ActionType.ALTER_ENVIRONMENT) {
                    return "Environmental Tampering in " + locName;
                }
                if (a.getActionType() == ActionType.SEARCH) {
                    return "Searching for Clues in " + locName;
                }
                if (a.getActionType() == ActionType.MANIPULATE_ITEM) {
                    return "Subtle Movements in " + locName;
                }
            }
        }
        return null;
    }
}
