package com.murderhecoded.engine.handlers;

import com.murderhecoded.domain.Action;
import com.murderhecoded.domain.Agent;
import com.murderhecoded.repository.SimulationRepository;
import java.util.function.Consumer;

public class MoveActionHandler implements ActionHandler {
    @Override
    public void handle(Action action, SimulationRepository state, Consumer<String> logAppender) {
        Agent actor = state.getAgents().get(action.getActorId());
        if (action.getTargetLocationId() != null && state.getLocations().containsKey(action.getTargetLocationId())) {
            actor.setCurrentLocationId(action.getTargetLocationId());
        }
    }
}