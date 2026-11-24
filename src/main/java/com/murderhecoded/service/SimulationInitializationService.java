package com.murderhecoded.service;

import com.murderhecoded.agent.WorldBuilderService;
import com.murderhecoded.domain.*;
import com.murderhecoded.repository.SimulationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class SimulationInitializationService {

    private final WorldBuilderService worldBuilderService;
    private final DramaturgyValidationService dramaturgyService;

    private Consumer<String> onLogEvent;

    public void setOnLogEvent(Consumer<String> onLogEvent) {
        this.onLogEvent = onLogEvent;
        if (this.worldBuilderService != null) {
            this.worldBuilderService.setOnLogEvent(onLogEvent);
        }
    }

    private void emitLog(String message) {
        log.info(message);
        if (onLogEvent != null) {
            try {
                if (javafx.application.Platform.isFxApplicationThread()) {
                    onLogEvent.accept(message);
                } else {
                    try {
                        javafx.application.Platform.runLater(() -> onLogEvent.accept(message));
                    } catch (IllegalStateException e) {
                        onLogEvent.accept(message);
                    }
                }
            } catch (Exception e) {
                log.debug("Error delivering log event: {}", e.getMessage());
            }
        }
    }

    public void initializeState(
            SimulationRepository state,
            String language,
            int numAgents,
            int numPlaces,
            int numItems,
            String title,
            String description,
            String voice
    ) {
        emitLog("Påbörjar världsgenerering via WorldBuilderService (3-stegs pipeline)...");
        state.setWorldGenerated(false);

        try {
            // 1. Kör den sekventiella pipelinen (Locations ➔ Items ➔ Agents)
            // LlmExecutor sköter retries internt. Kastar exception om det inte går.
            WorldSetupResponse worldSetup = worldBuilderService.buildWorld(
                    title,
                    description,
                    numPlaces,
                    numItems,
                    numAgents,
                    voice,
                    language
            );

            // 2. Konvertera Records (DTOs) till de levande domänobjekten i muterbara listor
            List<Location> locations = worldSetup.locations() != null
                    ? worldSetup.locations().stream().map(Location::new).collect(Collectors.toCollection(ArrayList::new))
                    : new ArrayList<>();

            List<Item> items = worldSetup.items() != null
                    ? worldSetup.items().stream().map(Item::fromRecord).collect(Collectors.toCollection(ArrayList::new))
                    : new ArrayList<>();

            List<Agent> agents = worldSetup.agents() != null
                    ? worldSetup.agents().stream().map(Agent::fromRecord).collect(Collectors.toCollection(ArrayList::new))
                    : new ArrayList<>();

            // Säkra grundtillstånd på agenterna och säkerställ exakt en mördare
            Agent designatedKiller = null;
            for (Agent agent : agents) {
                agent.setAlive(true);
                if (agent.isDetective()) {
                    agent.setHasArrived(false);
                    agent.setHasMurderIntent(false);
                } else {
                    agent.setHasArrived(true);
                    if (agent.isMurderer()) {
                        if (designatedKiller == null) {
                            designatedKiller = agent;
                            agent.setHasMurderIntent(true);
                        } else {
                            agent.setHasMurderIntent(false);
                        }
                    } else {
                        agent.setHasMurderIntent(false);
                    }
                }
            }

            if (designatedKiller != null && designatedKiller.getTargetVictimId() == null) {
                String goal = designatedKiller.getSecretGoal() != null ? designatedKiller.getSecretGoal().toLowerCase(java.util.Locale.ROOT) : "";
                for (Agent potentialVictim : agents) {
                    if (potentialVictim.getId() != null && !Objects.equals(potentialVictim.getId(), designatedKiller.getId()) && !potentialVictim.isDetective()) {
                        if (potentialVictim.getName() != null && goal.contains(potentialVictim.getName().toLowerCase(java.util.Locale.ROOT))) {
                            designatedKiller.setTargetVictimId(potentialVictim.getId());
                            break;
                        }
                    }
                }
                if (designatedKiller.getTargetVictimId() == null) {
                    for (Agent potentialVictim : agents) {
                        if (potentialVictim.getId() != null && !Objects.equals(potentialVictim.getId(), designatedKiller.getId()) && !potentialVictim.isDetective()) {
                            designatedKiller.setTargetVictimId(potentialVictim.getId());
                            break;
                        }
                    }
                }
            }

            emitLog(String.format("Genererat från LLM: %d karaktärer, %d platser, %d föremål",
                    agents.size(), locations.size(), items.size()));

            // 3. Kör dramaturgikontroll och berika världsobjekt (t.ex. säkerställ vapen och målartiklar)
            DramaturgyValidationService.DramaturgyReport report = dramaturgyService.validateAndEnforceDramaturgy(
                    agents,
                    locations,
                    items,
                    title,
                    description
            );
            report.logs().forEach(this::emitLog);

            // 4. Ladda repositoriet och markera världen som genererad
            state.initialize(agents, locations, items, Collections.emptyList(), title, description);
            state.setWorldGenerated(true);

            emitLog("Spelvärlden har framgångsrikt initialiserats!");

        } catch (Exception ex) {
            String detail = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
            String errMsg = "Världsgenerering misslyckades: " + detail;
            if (ex.getCause() != null && ex.getCause().getMessage() != null) {
                errMsg += " (Orsak: " + ex.getCause().getMessage() + ")";
            }
            log.error("Världsgenerering misslyckades: {}", errMsg, ex);
            emitLog("[FEL] " + errMsg);
            state.setWorldGenerated(false);
            throw new RuntimeException(errMsg, ex);
        }
    }
}