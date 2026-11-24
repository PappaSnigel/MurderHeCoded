package com.murderhecoded.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GlobalEvent {
    private String id;
    private String name;
    private String description;
    private int scheduledStep;       // When it starts
    private int durationSteps;       // Duration in steps (default 2-4)
    private String targetLocationId; // Optional location where people are called to gather
    private String impactType;       // GATHERING, ENVIRONMENTAL, ISOLATION, ALARM, DEPARTURE
    private boolean triggered;
    private boolean active;
    private boolean concluded;

    public boolean isActiveAt(int step) {
        int duration = Math.max(1, durationSteps);
        return step >= scheduledStep && step < (scheduledStep + duration);
    }

    public boolean isConcludedAt(int step) {
        int duration = Math.max(1, durationSteps);
        return step >= (scheduledStep + duration);
    }

    public GlobalEventSnapshot snapshot(int currentStep) {
        return new GlobalEventSnapshot(
            id,
            name,
            description,
            scheduledStep,
            durationSteps,
            targetLocationId,
            impactType != null ? impactType : "GATHERING",
            isActiveAt(currentStep),
            isConcludedAt(currentStep)
        );
    }
}
