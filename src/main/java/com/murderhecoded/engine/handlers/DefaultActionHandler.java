package com.murderhecoded.engine.handlers;

import com.murderhecoded.domain.Action;
import com.murderhecoded.domain.Agent;
import com.murderhecoded.repository.SimulationRepository;
import java.util.Locale;
import java.util.function.Consumer;

@SuppressWarnings("PMD.LawOfDemeter")
public class DefaultActionHandler implements ActionHandler {
    @Override
    public void handle(Action action, SimulationRepository state, Consumer<String> logAppender) {
        if (action == null) {
            return;
        }
        Agent actor = state.getAgents().get(action.getActorId());
        String actorName = actor != null ? actor.getName() : "Unknown";
        String actionStr = action.getActionType() != null 
            ? action.getActionType().name().toLowerCase(Locale.ROOT).replace("_", " ") 
            : "acted";
        String details = action.getDetails() != null ? action.getDetails() : "";
        logAppender.accept(actorName + " " + actionStr + (details.isEmpty() ? "" : ": " + details));
    }
}