package com.murderhecoded.engine.handlers;

import com.murderhecoded.domain.Action;
import com.murderhecoded.repository.SimulationRepository;
import java.util.function.Consumer;

@FunctionalInterface
public interface ActionHandler {
    void handle(Action action, SimulationRepository state, Consumer<String> logAppender);
}