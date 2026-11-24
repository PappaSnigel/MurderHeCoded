package com.murderhecoded.engine.handlers;

import com.murderhecoded.domain.Action;
import com.murderhecoded.domain.Agent;
import com.murderhecoded.domain.EnvironmentTrace;
import com.murderhecoded.domain.Location;
import com.murderhecoded.repository.SimulationRepository;
import java.util.UUID;
import java.util.function.Consumer;

public class AlterEnvironmentActionHandler implements ActionHandler {
    @Override
    public void handle(Action action, SimulationRepository state, Consumer<String> logAppender) {
        Agent actor = state.getAgents().get(action.getActorId());
        if (action.getTraceDescription() != null && !action.getTraceDescription().isEmpty()) {
            Location location = state.getLocations().get(actor.getCurrentLocationId());
            if (location != null) {
                EnvironmentTrace trace = new EnvironmentTrace();
                trace.setId("trace_" + UUID.randomUUID().toString().substring(0, 8));
                trace.setDescription(action.getTraceDescription());
                trace.setCreatorId(actor.getId());
                trace.setTimeStepCreated(state.getCurrentStep());
                location.getTraces().add(trace);
                logAppender.accept("" + actor.getName() + " altered the environment. Added trace: " + action.getTraceDescription());
            }
        }
    }
}