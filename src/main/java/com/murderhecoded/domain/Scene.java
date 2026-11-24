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
public class Scene {
    private int sceneNumber;
    private int startStep;
    private int endStep;
    private String primaryLocationId;
    private String primaryLocationName;
    private String dramaticFocus;

    // Dramatic boundary framing
    private TimeLocationEvent firstEvent;
    private TimeLocationEvent lastEvent;
    
    @Builder.Default
    private List<TimeLocationEvent> events = new ArrayList<>();
    
    private double dramaScore;               // Composite 0.0 - 10.0
    private double narrativeImportanceScore; // 0.0 - 10.0
    private double suspenseScore;            // 0.0 - 10.0
    private double totalEditorialScore;      // 0.0 - 10.0
    private SceneCategory category;
    
    private String proseText;
    private String summaryText;

    private String directorNotes;
    private String openingHook;
    private String sceneButton;

    @Builder.Default
    private List<String> subtext = new ArrayList<>();

    @Builder.Default
    private boolean isSelected = true;

    public TimeLocationEvent getFirstEvent() {
        return firstEvent != null ? firstEvent : (events != null && !events.isEmpty() ? events.get(0) : null);
    }

    public TimeLocationEvent getLastEvent() {
        return lastEvent != null ? lastEvent : (events != null && !events.isEmpty() ? events.get(events.size() - 1) : null);
    }

    public boolean isPivotal() {
        return category == SceneCategory.ESSENTIAL || narrativeImportanceScore >= 8.0 || dramaScore >= 8.0;
    }

    public boolean isDispensable() {
        return category == SceneCategory.DISPENSABLE;
    }
}
