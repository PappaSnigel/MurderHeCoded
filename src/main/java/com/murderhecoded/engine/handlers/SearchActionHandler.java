package com.murderhecoded.engine.handlers;

import com.murderhecoded.domain.Action;
import com.murderhecoded.domain.Agent;
import com.murderhecoded.domain.Item;
import com.murderhecoded.domain.ItemType;
import com.murderhecoded.domain.Knowledge;
import com.murderhecoded.repository.SimulationRepository;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

@SuppressWarnings("PMD.LawOfDemeter")
public class SearchActionHandler implements ActionHandler {
    @Override
    public void handle(Action action, SimulationRepository state, Consumer<String> logAppender) {
        Agent actor = state.getAgents().get(action.getActorId());
        if (actor == null) {
            return;
        }

        boolean foundNotebook = searchNotebooks(actor, state, action.getDetails(), logAppender);
        boolean foundHidden = searchHiddenItems(actor, state, logAppender);
        boolean foundPoisonClue = searchPoisonTraces(actor, state, logAppender);

        if (!foundNotebook && !foundHidden && !foundPoisonClue) {
            logAppender.accept(actor.getName() + " searched the room. Details: " + action.getDetails());
        }
    }

    private boolean searchNotebooks(Agent actor, SimulationRepository state, String details, Consumer<String> logAppender) {
        for (Item item : state.getItems().values()) {
            if (item.getType() == ItemType.NOTEBOOK && Objects.equals(item.getCurrentLocationId(), actor.getCurrentLocationId())) {
                String prefix = "[From " + item.getName() + "]: ";
                List<Knowledge> prefixedKnowledge = item.getNotebookKnowledge().stream()
                        .map(k -> {
                            String info = k.information();
                            if (info != null && !info.startsWith("[From ")) {
                                info = prefix + info;
                            }
                            return new Knowledge(info, state.getCurrentStep());
                        })
                        .toList();
                actor.getKnowledgeBase().addAll(prefixedKnowledge);
                item.setCurrentLocationId(actor.getId());
                logAppender.accept(actor.getName() + " searched and FOUND " + item.getName() + "! (Details: " + details + ")");
                return true;
            }
        }
        return false;
    }

    private boolean searchHiddenItems(Agent actor, SimulationRepository state, Consumer<String> logAppender) {
        boolean found = false;
        for (Item item : state.getItems().values()) {
            if (item.isHidden() && Objects.equals(item.getCurrentLocationId(), actor.getCurrentLocationId())) {
                item.setHidden(false);
                logAppender.accept(actor.getName() + " searched and DISCOVERED the hidden item: " + item.getName() + "!");
                found = true;
            }
        }
        return found;
    }

    private boolean searchPoisonTraces(Agent actor, SimulationRepository state, Consumer<String> logAppender) {
        boolean found = false;
        for (Item item : state.getItems().values()) {
            if ((item.isPoisoned() || item.isPoisonConsumed()) && Objects.equals(item.getCurrentLocationId(), actor.getCurrentLocationId())) {
                String clue = "Found suspicious chemical residue and faint toxin traces on " + item.getName();
                boolean alreadyKnown = actor.getKnowledgeBase().stream()
                        .anyMatch(k -> k.information().contains("chemical residue and faint toxin traces on " + item.getName()));
                if (!alreadyKnown) {
                    actor.getKnowledgeBase().add(new Knowledge(clue, state.getCurrentStep()));
                    logAppender.accept(actor.getName() + " searched and DISCOVERED: " + clue);
                    found = true;
                }
            }
        }
        return found;
    }
}