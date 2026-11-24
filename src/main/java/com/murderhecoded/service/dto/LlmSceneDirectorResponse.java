package com.murderhecoded.service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Structured output response payload from LLM Scene Director.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LlmSceneDirectorResponse(
    @JsonProperty("dramaticFocus") String dramaticFocus,
    @JsonProperty("openingHook") String openingHook,
    @JsonProperty("sceneButton") String sceneButton,
    @JsonProperty("subtextAndSecrets") List<String> subtextAndSecrets,
    @JsonProperty("directorNotes") String directorNotes,
    @JsonProperty("recommendedStartStep") Integer recommendedStartStep,
    @JsonProperty("recommendedEndStep") Integer recommendedEndStep,
    @JsonProperty("narrativeWeight") String narrativeWeight,
    @JsonProperty("dramaticScore") Double dramaticScore
) {
    public LlmSceneDirectorResponse {
        if (subtextAndSecrets == null) {
            subtextAndSecrets = List.of();
        }
        if (dramaticFocus == null) {
            dramaticFocus = "";
        }
        if (openingHook == null) {
            openingHook = "";
        }
        if (sceneButton == null) {
            sceneButton = "";
        }
        if (directorNotes == null) {
            directorNotes = "";
        }
        if (narrativeWeight == null) {
            narrativeWeight = "SUPPORTING";
        }
        if (dramaticScore == null) {
            dramaticScore = 5.0;
        }
    }
}
