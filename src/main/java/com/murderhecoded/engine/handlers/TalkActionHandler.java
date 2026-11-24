package com.murderhecoded.engine.handlers;

import com.murderhecoded.domain.Action;
import com.murderhecoded.domain.Agent;
import com.murderhecoded.domain.Knowledge;
import com.murderhecoded.repository.SimulationRepository;
import java.util.Objects;
import java.util.function.Consumer;

public class TalkActionHandler implements ActionHandler {
    @Override
    public void handle(Action action, SimulationRepository state, Consumer<String> logAppender) {
        Agent actor = state.getAgents().get(action.getActorId());
        Agent target = state.getAgents().get(action.getTargetAgentId());
        if (target != null && target.isAlive() && actor != null
                && Objects.equals(target.getCurrentLocationId(), actor.getCurrentLocationId())
                && action.getSharedKnowledge() != null && !action.getSharedKnowledge().trim().isEmpty()) {
            String sanitizedKnowledge = stripLeadingVocatives(action.getSharedKnowledge());
            target.addKnowledge(new Knowledge(sanitizedKnowledge, state.getCurrentStep()));
        }
    }

    public static String stripLeadingVocatives(String text) {
        if (text == null || text.isBlank()) return text;
        String cleaned = text.trim().replaceFirst("^(?i)(?:(?:captain|doctor|detective|officer|chief|dr\\.|mr\\.|ms\\.|mrs\\.)(?:\\s+[a-z]+)?,?\\s*|(?:hey|listen|look|hello|hi),?\\s*)+", "");
        if (!cleaned.isBlank()) {
            return Character.toUpperCase(cleaned.charAt(0)) + (cleaned.length() > 1 ? cleaned.substring(1) : "");
        }
        return text.trim();
    }
}