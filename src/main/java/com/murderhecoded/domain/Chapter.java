package com.murderhecoded.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Chapter(
    @JsonProperty("text") String text, 
    @JsonProperty("isPivotal") boolean isPivotal, 
    @JsonProperty("isFluff") boolean isFluff, 
    @JsonProperty("timeStep") int timeStep, 
    @JsonProperty("sceneNumber") int sceneNumber, 
    @JsonProperty("locationName") String locationName,
    @JsonProperty("importanceScore") double importanceScore,
    @JsonProperty("suspenseScore") double suspenseScore,
    @JsonProperty("category") SceneCategory category,
    @JsonProperty("dramaticFocus") String dramaticFocus,
    @JsonProperty("startStep") int startStep,
    @JsonProperty("endStep") int endStep,
    @JsonProperty("timeSpan") String timeSpan,
    @JsonProperty("isSelected") boolean isSelected,
    @JsonProperty("directorNotes") String directorNotes,
    @JsonProperty("openingHook") String openingHook,
    @JsonProperty("sceneButton") String sceneButton,
    @JsonProperty("subtext") java.util.List<String> subtext
) {
    public Chapter {
        if (startStep == 0 && timeStep > 0) {
            startStep = timeStep;
        }
        if (endStep == 0 && timeStep > 0) {
            endStep = timeStep;
        }
        if (timeSpan == null || timeSpan.isEmpty()) {
            timeSpan = "Step " + (startStep > 0 ? startStep : timeStep);
        }
        if (!isSelected && category != SceneCategory.DISPENSABLE && !isFluff && importanceScore >= 3.0) {
            isSelected = true;
        }
        if (directorNotes == null) {
            directorNotes = "";
        }
        if (openingHook == null) {
            openingHook = "";
        }
        if (sceneButton == null) {
            sceneButton = "";
        }
        if (subtext == null) {
            subtext = java.util.List.of();
        }
    }

    // Backward-compatible constructor (14 args)
    @SuppressWarnings("PMD.ExcessiveParameterList")
    public Chapter(
        String text, 
        boolean isPivotal, 
        boolean isFluff, 
        int timeStep, 
        int sceneNumber, 
        String locationName,
        double importanceScore,
        double suspenseScore,
        SceneCategory category,
        String dramaticFocus,
        int startStep,
        int endStep,
        String timeSpan,
        boolean isSelected
    ) {
        this(
            text, 
            isPivotal, 
            isFluff, 
            timeStep, 
            sceneNumber, 
            locationName,
            importanceScore,
            suspenseScore,
            category,
            dramaticFocus,
            startStep,
            endStep,
            timeSpan,
            isSelected,
            "",
            "",
            "",
            java.util.List.of()
        );
    }

    // Backward-compatible constructor (10 args)
    @SuppressWarnings("PMD.ExcessiveParameterList")
    public Chapter(
        String text, 
        boolean isPivotal, 
        boolean isFluff, 
        int timeStep, 
        int sceneNumber, 
        String locationName,
        double importanceScore,
        double suspenseScore,
        SceneCategory category,
        String dramaticFocus
    ) {
        this(
            text, 
            isPivotal, 
            isFluff, 
            timeStep, 
            sceneNumber, 
            locationName,
            importanceScore,
            suspenseScore,
            category,
            dramaticFocus,
            timeStep,
            timeStep,
            "Step " + timeStep,
            category != SceneCategory.DISPENSABLE && !isFluff
        );
    }

    // Backward-compatible constructor (6 args)
    public Chapter(String text, boolean isPivotal, boolean isFluff, int timeStep, int sceneNumber, String locationName) {
        this(
            text, 
            isPivotal, 
            isFluff, 
            timeStep, 
            sceneNumber, 
            locationName,
            isPivotal ? 9.5 : (isFluff ? 2.0 : 5.5),
            isPivotal ? 8.5 : (isFluff ? 2.0 : 4.5),
            isPivotal ? SceneCategory.ESSENTIAL : (isFluff ? SceneCategory.DISPENSABLE : SceneCategory.SUPPORTING),
            locationName,
            timeStep,
            timeStep,
            "Step " + timeStep,
            !isFluff
        );
    }

    @JsonIgnore
    public boolean hasProse() {
        return text != null && !text.isBlank() && !text.startsWith("[HÄNDELSELOGG");
    }

    @JsonIgnore
    public int wordCount() {
        if (!hasProse() || text.startsWith("[UPPLÖSNINGSKAPITLET")) {
            return 0;
        }
        return text.trim().split("\\s+").length;
    }
}