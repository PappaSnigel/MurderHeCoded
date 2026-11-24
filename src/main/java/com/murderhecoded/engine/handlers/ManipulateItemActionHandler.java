package com.murderhecoded.engine.handlers;

import com.murderhecoded.domain.Action;
import com.murderhecoded.domain.Agent;
import com.murderhecoded.domain.Item;
import com.murderhecoded.repository.SimulationRepository;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

public class ManipulateItemActionHandler implements ActionHandler {
    @Override
    public void handle(Action action, SimulationRepository state, Consumer<String> logAppender) {
        if (action.getTargetItemId() == null) {
            return;
        }
        Agent actor = state.getAgents().get(action.getActorId());
        Item item = state.getItems().get(action.getTargetItemId());
        if (actor == null || item == null) {
            return;
        }
        dispatchInteraction(action, actor, item, state, logAppender);
    }

    private void dispatchInteraction(Action action, Agent actor, Item item, SimulationRepository state, Consumer<String> logAppender) {
        String inter = normalizeInteraction(action.getInteraction());
        switch (inter) {
            case "TAKE" -> handleTake(actor, item, logAppender);
            case "DROP" -> handleDrop(actor, item, logAppender);
            case "HIDE" -> handleHide(actor, item, logAppender);
            case "POISON" -> handlePoison(actor, item, action, state, logAppender);
            default -> { /* ignore unrecognized interaction */ }
        }
    }

    private String normalizeInteraction(String raw) {
        if (raw == null || raw.isBlank()) {
            return "POISON";
        }
        String up = raw.toUpperCase(Locale.ROOT);
        if ("SABOTAGE".equals(up) || "LACE".equals(up)) {
            return "POISON";
        }
        return up;
    }

    private void handleTake(Agent actor, Item item, Consumer<String> logAppender) {
        if (Objects.equals(item.getCurrentLocationId(), actor.getCurrentLocationId()) && !item.isHidden()) {
            if (!item.isMovable()) {
                logAppender.accept(actor.getName() + " attempted to take " + item.getName() + ", but it is a stationary fixture/machinery and cannot be moved.");
                return;
            }
            item.setCurrentLocationId(actor.getId());
            logAppender.accept(actor.getName() + " took item: " + item.getName());
        }
    }

    private void handleDrop(Agent actor, Item item, Consumer<String> logAppender) {
        if (Objects.equals(item.getCurrentLocationId(), actor.getId())) {
            item.setCurrentLocationId(actor.getCurrentLocationId());
            logAppender.accept(actor.getName() + " dropped item: " + item.getName());
        }
    }

    private void handleHide(Agent actor, Item item, Consumer<String> logAppender) {
        if (!item.isMovable()) {
            logAppender.accept(actor.getName() + " attempted to hide " + item.getName() + ", but it is a stationary fixture and cannot be moved.");
            return;
        }
        if (Objects.equals(item.getCurrentLocationId(), actor.getId()) || Objects.equals(item.getCurrentLocationId(), actor.getCurrentLocationId())) {
            item.setCurrentLocationId(actor.getCurrentLocationId());
            item.setHidden(true);
            logAppender.accept(actor.getName() + " HID item: " + item.getName());
        }
    }

    private void handlePoison(Agent actor, Item item, Action action, SimulationRepository state, Consumer<String> logAppender) {
        if (Objects.equals(item.getCurrentLocationId(), actor.getCurrentLocationId()) || Objects.equals(item.getCurrentLocationId(), actor.getId())) {
            item.setPoisoned(true);
            item.setPoisonedByAgentId(actor.getId());
            item.setPoisonTargetAgentId(action.getTargetAgentId());
            item.setPoisonDetails(action.getDetails() != null ? action.getDetails() : "Poisoned secretly");
            item.setPoisonTimeStep(state.getCurrentStep());
            actor.setHasMurderIntent(true);
            if (action.getTargetAgentId() != null) {
                actor.setTargetVictimId(action.getTargetAgentId());
            }
            logAppender.accept(actor.getName() + " secretly poisoned/sabotaged item: " + item.getName() +
                    (action.getTargetAgentId() != null ? " (Target: " + action.getTargetAgentId() + ")" : ""));
        }
    }
}