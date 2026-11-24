package com.murderhecoded.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimeLocationEvent {
    private int timeStep;
    private int sequenceNumber;
    private String locationId;
    private String locationName;
    
    @Builder.Default
    private List<Action> actions = new ArrayList<>();
    
    @Builder.Default
    private List<String> knowledgeGained = new ArrayList<>();
    
    @Builder.Default
    private List<String> presentAgentNames = new ArrayList<>();

    private double suspenseScore;
    private double dramaScore;
    private boolean murdererPresent;
    private String murdererPressureInfo;

    @com.fasterxml.jackson.annotation.JsonIgnore
    private SimulationStateSnapshot snapshotBefore;
    @com.fasterxml.jackson.annotation.JsonIgnore
    private SimulationStateSnapshot snapshotAfter;

    public boolean hasMurderAction() {
        if (actions == null || actions.isEmpty()) return false;
        return actions.stream().anyMatch(a -> a.getActionType() == ActionType.MURDER);
    }

    public boolean hasAccuseAction() {
        if (actions == null || actions.isEmpty()) return false;
        return actions.stream().anyMatch(a -> a.getActionType() == ActionType.ACCUSE);
    }

    public boolean hasHighImpactAction() {
        if (actions == null || actions.isEmpty()) return false;
        return actions.stream().anyMatch(a -> 
            a.getActionType() == ActionType.MURDER || 
            a.getActionType() == ActionType.ACCUSE ||
            a.getActionType() == ActionType.SEARCH
        );
    }

    public boolean isOnlyMundaneObserving() {
        if (actions == null || actions.isEmpty()) return true;
        return actions.stream().allMatch(a -> 
            a.getActionType() == ActionType.OBSERVE || 
            a.getActionType() == ActionType.MOVE || 
            a.getActionType() == ActionType.CASUAL_INTERACT
        );
    }
}
