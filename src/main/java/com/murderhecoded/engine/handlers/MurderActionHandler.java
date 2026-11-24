package com.murderhecoded.engine.handlers;

import com.murderhecoded.domain.Action;
import com.murderhecoded.domain.Agent;
import com.murderhecoded.domain.Item;
import com.murderhecoded.domain.ItemType;
import com.murderhecoded.repository.SimulationRepository;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

public class MurderActionHandler implements ActionHandler {
    @Override
    public void handle(Action action, SimulationRepository state, Consumer<String> logAppender) {
        Agent actor = state.getAgents().get(action.getActorId());
        Agent victim = state.getAgents().get(action.getTargetAgentId());
        if (victim != null && victim.isAlive() && actor != null && actor.getCurrentLocationId() != null
                && Objects.equals(victim.getCurrentLocationId(), actor.getCurrentLocationId())) {
            victim.setAlive(false);
            actor.setHasMurderIntent(true);
            actor.setTargetVictimId(victim.getId());
            state.resetIncidentTimer();
            logAppender.accept("MURDER OCCURRED! Victim: " + victim.getName());

            if (!victim.getKnowledgeBase().isEmpty()) {
                Item notebook = new Item();
                notebook.setId("notebook_" + UUID.randomUUID().toString().substring(0, 8));
                notebook.setName("Notebook of " + victim.getName());
                notebook.setType(ItemType.NOTEBOOK);
                notebook.setCurrentLocationId(victim.getCurrentLocationId());
                notebook.getNotebookKnowledge().addAll(victim.getKnowledgeBase());
                state.getItems().put(notebook.getId(), notebook);
                logAppender.accept("Notebook dropped at " + victim.getCurrentLocationId());
            }
        }
    }
}