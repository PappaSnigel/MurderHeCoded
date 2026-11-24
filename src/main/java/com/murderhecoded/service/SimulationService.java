package com.murderhecoded.service;

import com.murderhecoded.agent.AgentService;
import com.murderhecoded.domain.*;
import com.murderhecoded.engine.handlers.TalkActionHandler;
import com.murderhecoded.repository.SimulationRepository;
import javafx.application.Platform;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

@Service
@SuppressWarnings("PMD")
public class SimulationService {

    private static final org.slf4j.Logger logApp = org.slf4j.LoggerFactory.getLogger(SimulationService.class);

    private final SimulationRepository state;
    private final OllamaLlmService ollamaLlmService;
    private final ProseService proseService;
    private final DramaturgyDirectorService dramaturgyDirectorService;
    private final AgentService agentService;

    private volatile Thread workerThread;
    private final java.util.concurrent.atomic.AtomicInteger totalScenesGenerated = new java.util.concurrent.atomic.AtomicInteger(0);
    private volatile boolean running = false;
    private volatile boolean enableProse = false;
    private String language = "en";
    private int targetSteps = 10;
    
    private Consumer<String> onLogEvent;
    private Runnable onStateUpdated;

    @Autowired
    public SimulationService(SimulationRepository state, OllamaLlmService ollamaLlmService, ProseService proseService, DramaturgyDirectorService dramaturgyDirectorService, @Autowired(required = false) AgentService agentService) {
        this.state = state;
        this.ollamaLlmService = ollamaLlmService;
        this.proseService = proseService;
        this.dramaturgyDirectorService = dramaturgyDirectorService != null ? dramaturgyDirectorService : new DramaturgyDirectorService();
        this.agentService = agentService != null ? agentService : new AgentService(ollamaLlmService, new com.murderhecoded.llm.LlmExecutor(), this.dramaturgyDirectorService);
        initializeHandlers();
    }

    public SimulationService(SimulationRepository state, OllamaLlmService ollamaLlmService, ProseService proseService, DramaturgyDirectorService dramaturgyDirectorService) {
        this(state, ollamaLlmService, proseService, dramaturgyDirectorService, null);
    }

    public SimulationService(SimulationRepository state, OllamaLlmService ollamaLlmService, ProseService proseService) {
        this(state, ollamaLlmService, proseService, new DramaturgyDirectorService(), null);
    }

    public void setCallbacks(Consumer<String> onLogEvent, Runnable onStateUpdated) {
        this.onLogEvent = onLogEvent;
        this.onStateUpdated = onStateUpdated;
    }

    public void startSimulation(String language, int targetSteps, boolean enableProse, boolean isResume) {
        if (running) return;
        if (!isResume) {
            totalScenesGenerated.set(0);
            dramaturgyDirectorService.reset();
        }
        this.language = language;
        this.targetSteps = targetSteps;
        state.setTargetSteps(targetSteps);
        this.running = true;
        this.enableProse = enableProse;
        
        if (enableProse && proseService != null) {
            proseService.setGenerateProseText(true);
            proseService.start(isResume);
        }

        workerThread = Thread.ofVirtual()
            .name("Simulation-Thread", 0)
            .start(this::simulationLoop);
    }

    public int getTotalScenesGenerated() {
        return totalScenesGenerated.get();
    }
    
    public boolean isRunning() {
        return running;
    }

    public DramaturgyDirectorService getDramaturgyDirectorService() {
        return dramaturgyDirectorService;
    }
    
    public void stopSimulation() {
        running = false;
        Thread t = workerThread;
        if (t != null) {
            t.interrupt();
        }
        if (enableProse && proseService != null && proseService.isRunning()) {
            proseService.stop();
        }
        log("Simulation stopped by user.");
    }

    public boolean awaitTermination(long timeoutMs) {
        Thread t = workerThread;
        if (t == null) {
            return true;
        }
        try {
            t.join(timeoutMs);
            return !t.isAlive();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void simulationLoop() {
        log("Generated persons: " + state.getAgents().size());
        log("Generated places: " + state.getLocations().size());
        log("Generated things: " + state.getItems().size());
        log("Starting Decentralized Multi-Agent Simulation Loop");
        
        int emptyActionRetries = 0;
        while (running && !state.isCompleted() && state.getCurrentStep() < state.getTargetSteps()) {
            try {
                SimulationStateSnapshot snapBefore = state.getSnapshots().get(state.getCurrentStep());
                java.util.List<Action> stepActions = new java.util.ArrayList<>();
                
                int nextStep = state.getCurrentStep() + 1;
                log("");
                log("[Time Step: " + nextStep + " - " + state.formatInterval(state.getCurrentStep(), nextStep) + "]");
                
                dramaturgyDirectorService.evaluateAndDirectStep(state, language, this::log);
                
                java.util.List<Agent> activeAgents = new java.util.ArrayList<>(state.getAgents().values());
                java.util.Collections.shuffle(activeAgents);
                
                java.util.Map<String, java.util.List<Agent>> agentsByLoc = new java.util.HashMap<>();
                for (Agent a : activeAgents) {
                    if (a.isAlive() && a.isHasArrived()) {
                        String loc = a.getCurrentLocationId();
                        if (loc == null || !state.getLocations().containsKey(loc)) {
                            loc = state.getLocations().isEmpty() ? "room_0" : state.getLocations().keySet().iterator().next();
                            a.setCurrentLocationId(loc);
                        }
                        agentsByLoc.computeIfAbsent(loc, k -> new java.util.ArrayList<>()).add(a);
                    }
                }
                
                int eventCounter = 1;
                java.util.List<TimeLocationEvent> eventsThisStep = new java.util.ArrayList<>();
                
                for (java.util.Map.Entry<String, java.util.List<Agent>> entry : agentsByLoc.entrySet()) {
                    if (!running || state.isCompleted()) break;
                    
                    String locId = entry.getKey();
                    String locName = state.getLocations().containsKey(locId) ? state.getLocations().get(locId).getName() : locId;
                    String sceneStr = nextStep + "." + eventCounter;
                    
                    java.util.List<Action> sceneActions = new java.util.ArrayList<>();
                    java.util.List<String> sceneKnowledge = new java.util.ArrayList<>();
                    
                    // Sort agents by initiative: Murderer always has first initiative (0), followed by others (1)
                    java.util.List<Agent> roomAgents = new java.util.ArrayList<>(entry.getValue());
                    roomAgents.sort((a1, a2) -> {
                        int p1 = a1.isMurderer() ? 0 : 1;
                        int p2 = a2.isMurderer() ? 0 : 1;
                        return Integer.compare(p1, p2);
                    });

                    double roomTension = dramaturgyDirectorService.calculateLiveRoomTension(state, roomAgents, nextStep);
                    log("");
                    log("[" + sceneStr + ", " + locName + " | Suspense: " + String.format(java.util.Locale.ROOT, "%.1f", roomTension) + "/10]");

                    Agent killerInRoom = roomAgents.stream().filter(Agent::isMurderer).findFirst().orElse(null);
                    DramaturgyDirectorService.MurdererPressureStatus killerPressure = null;
                    if (killerInRoom != null && killerInRoom.isAlive()) {
                        killerPressure = dramaturgyDirectorService.evaluateMurdererPressure(state, killerInRoom, roomAgents, nextStep);
                        String targetDesc = killerPressure.victim() != null ? killerPressure.victim().getName() : "None";
                        String targetPresence = killerPressure.victimPresent() ? "IN ROOM" : "Elsewhere";
                        log(String.format(java.util.Locale.ROOT,
                                "  >>> [MURDERER PRESSURE] Killer: %s | Suspense: %.1f/10.0 | Urgency: %s | Target: %s (%s) | %s",
                                killerInRoom.getName(),
                                killerPressure.suspenseScore(),
                                killerPressure.urgencyPhase(),
                                targetDesc,
                                targetPresence,
                                killerPressure.pressureSummary()));
                    }

                    // Snapshot room state at start of event
                    java.util.List<String> presentAgentIds = roomAgents.stream().map(Agent::getId).toList();
                    java.util.List<String> presentAgentNames = roomAgents.stream().map(Agent::getName).toList();
                    java.util.List<Item> initialRoomItems = state.getItems().values().stream()
                            .filter(i -> java.util.Objects.equals(i.getCurrentLocationId(), locId) && !i.isHidden())
                            .toList();
                    java.util.List<String> presentItemIds = initialRoomItems.stream().map(Item::getId).toList();
                    java.util.List<String> presentItemNames = initialRoomItems.stream().map(Item::getName).toList();
                    java.util.List<String> observedEvents = new java.util.ArrayList<>();

                    for (Agent agent : roomAgents) {
                        if (!running || state.isCompleted()) break;
                        if (!agent.isAlive() || !agent.isHasArrived()) continue; // could have died this step
                        
                        Action action = null;
                        if (agentService != null) {
                            try {
                                AgentActionRecord record = agentService.decideAction(agent, state);
                                if (record != null) {
                                    action = record.toAction();
                                }
                            } catch (Exception ex) {
                                logApp.warn("AgentService failed for {}: {}. Attempting validated fallback.", agent.getName(), ex.getMessage());
                                try {
                                    String prompt = agentService.buildPrompt(agent, state);
                                    Action rawAction = ollamaLlmService.generateSingleAction(prompt);
                                    if (rawAction != null) {
                                        AgentActionRecord rec = AgentActionRecord.from(rawAction);
                                        java.util.List<Agent> presentOthers = roomAgents.stream()
                                                .filter(a -> a.isAlive() && a.isHasArrived() && !a.getId().equals(agent.getId()))
                                                .toList();
                                        if (agentService.validateAction(rec, agent, state, presentOthers)) {
                                            action = rawAction;
                                        }
                                    }
                                } catch (Exception ex2) {
                                    logApp.error("Fallback prompt-based action generation failed for {}: {}", agent.getName(), ex2.getMessage());
                                }
                                if (action == null) {
                                    action = generateSafeFallbackAction(agent, state, roomAgents);
                                }
                            }
                        }
                        if (!running || Thread.currentThread().isInterrupted()) {
                            break;
                        }
                        if (action != null) {
                            action.setActorId(agent.getId());

                            // Dramaturgic strike enforcement if murderer hesitated in strike window
                            DramaturgyDirectorService.StoryAct currentAct = dramaturgyDirectorService.determineCurrentAct(state);
                            DramaturgyDirectorService.ActPhase currentActPhase = dramaturgyDirectorService.determineCurrentActPhase(state);
                            boolean anyCrimeCommitted = state.getAgents().values().stream().anyMatch(a -> !a.isAlive());

                            if (agent.isMurderer() && !anyCrimeCommitted && dramaturgyDirectorService.isStrikeWindow(state, agent, roomAgents)) {
                                Agent targetVictim = dramaturgyDirectorService.findTargetVictim(agent, state);
                                if (targetVictim != null && targetVictim.isAlive() && action.getActionType() != com.murderhecoded.domain.ActionType.MURDER) {
                                    log("[DRAMATURGIC ENFORCEMENT]: Murderer " + agent.getName() + " hesitated in the presence of victim " 
                                        + targetVictim.getName() + " at peak suspense (" + String.format(java.util.Locale.ROOT, "%.1f", roomTension) 
                                        + "/10). Enforcing MURDER action!");
                                    action.setActionType(com.murderhecoded.domain.ActionType.MURDER);
                                    action.setTargetAgentId(targetVictim.getId());
                                    action.setDetails(agent.getName() + " seizes the decisive moment under peak dramatic tension to lethally strike " + targetVictim.getName() + "!");
                                    action.setNewKnowledgeFact("Assassinated " + targetVictim.getName());
                                }
                            } else if (agent.isMurderer() && !anyCrimeCommitted && currentAct == DramaturgyDirectorService.StoryAct.ACT_3_THE_CRIME) {
                                Agent targetVictim = dramaturgyDirectorService.findTargetVictim(agent, state);
                                if (targetVictim != null && targetVictim.isAlive() && roomAgents.stream().noneMatch(a -> a.getId().equals(targetVictim.getId()))) {
                                    if (action.getActionType() == com.murderhecoded.domain.ActionType.OBSERVE 
                                            || action.getActionType() == com.murderhecoded.domain.ActionType.REFLECT
                                            || action.getActionType() == com.murderhecoded.domain.ActionType.CASUAL_INTERACT) {
                                        String victimLoc = targetVictim.getCurrentLocationId();
                                        if (victimLoc != null) {
                                            String destName = state.getLocations().containsKey(victimLoc) ? state.getLocations().get(victimLoc).getName() : victimLoc;
                                            log("[DRAMATURGIC STEERING]: Murderer " + agent.getName() + " was idling with " 
                                                + action.getActionType() + " in Act 3 while victim is in " + destName + ". Steering action to MOVE towards victim!");
                                            action.setActionType(com.murderhecoded.domain.ActionType.MOVE);
                                            action.setTargetLocationId(victimLoc);
                                            action.setDetails(agent.getName() + " moves purposefully toward " + destName + " to corner " + targetVictim.getName() + ".");
                                        }
                                    }
                                }
                            }

                            // Dramaturgic steering for detective towards crime scene (prior to Climax)
                            if (agent.isDetective() && anyCrimeCommitted && currentAct != DramaturgyDirectorService.StoryAct.ACT_6_CLIMAX) {
                                Agent deadVictim = state.getAgents().values().stream()
                                        .filter(a -> !a.isAlive() && a.getCurrentLocationId() != null)
                                        .findFirst()
                                        .orElse(null);
                                if (deadVictim != null && java.util.Objects.equals(agent.getCurrentLocationId(), deadVictim.getCurrentLocationId())) {
                                    agent.setHasInspectedCrimeScene(true);
                                }
                                if (deadVictim != null && !agent.hasInspectedCrimeScene() && !java.util.Objects.equals(agent.getCurrentLocationId(), deadVictim.getCurrentLocationId())) {
                                    if (action.getActionType() == com.murderhecoded.domain.ActionType.OBSERVE 
                                            || action.getActionType() == com.murderhecoded.domain.ActionType.REFLECT
                                            || action.getActionType() == com.murderhecoded.domain.ActionType.SEARCH) {
                                        String crimeLoc = deadVictim.getCurrentLocationId();
                                        String destName = state.getLocations().containsKey(crimeLoc) ? state.getLocations().get(crimeLoc).getName() : crimeLoc;
                                        log("[DRAMATURGIC STEERING]: Detective " + agent.getName() + " was idling with " 
                                            + action.getActionType() + " while victim " + deadVictim.getName() + "'s body lies in " + destName + ". Steering detective to MOVE to the crime scene!");
                                        action.setActionType(com.murderhecoded.domain.ActionType.MOVE);
                                        action.setTargetLocationId(crimeLoc);
                                        action.setDetails(agent.getName() + " marches purposefully toward " + destName + " to inspect the body of " + deadVictim.getName() + " and secure the crime scene.");
                                    }
                                } else if (agent.getTurnsInCurrentLocation() >= 2 && state.getLocations().size() > 1) {
                                    // Detective mobility: after securing crime scene or investigating a room for 2+ turns, patrol to interview suspects
                                    String bestTargetLoc = null;
                                    int maxSuspects = 0;
                                    for (Location loc : state.getLocations().values()) {
                                        if (java.util.Objects.equals(loc.getId(), agent.getCurrentLocationId())) continue;
                                        int suspectsHere = (int) state.getAgents().values().stream()
                                                .filter(a -> a.isAlive() && !a.isDetective() && java.util.Objects.equals(a.getCurrentLocationId(), loc.getId()))
                                                .count();
                                        if (suspectsHere > maxSuspects) {
                                            maxSuspects = suspectsHere;
                                            bestTargetLoc = loc.getId();
                                        }
                                    }
                                    if (bestTargetLoc == null) {
                                        bestTargetLoc = state.getLocations().values().stream()
                                                .filter(l -> !java.util.Objects.equals(l.getId(), agent.getCurrentLocationId()))
                                                .map(Location::getId)
                                                .findFirst()
                                                .orElse(null);
                                    }
                                    if (bestTargetLoc != null && action.getActionType() != com.murderhecoded.domain.ActionType.MOVE) {
                                        String destName = state.resolveLocationName(bestTargetLoc, state.getCurrentStep());
                                        log("[DETECTIVE PATROL]: Detective " + agent.getName() + " has finished inspecting " 
                                                + locName + " (" + agent.getTurnsInCurrentLocation() + " turns). Moving to " + destName + " to interview suspects!");
                                        action.setActionType(com.murderhecoded.domain.ActionType.MOVE);
                                        action.setTargetLocationId(bestTargetLoc);
                                        action.setDetails(agent.getName() + " proceeds to " + destName + " to interview suspects and search for new evidence.");
                                    }
                                }
                            }

                            // Dramaturgic convergence in Act 6 Climax: assemble all surviving agents in the main gathering location
                            if (currentAct == DramaturgyDirectorService.StoryAct.ACT_6_CLIMAX && dramaturgyDirectorService != null) {
                                String mainLoc = dramaturgyDirectorService.getMainLocationId(state);
                                if (mainLoc != null && !java.util.Objects.equals(agent.getCurrentLocationId(), mainLoc)) {
                                    if (action.getActionType() != com.murderhecoded.domain.ActionType.ACCUSE) {
                                        String destName = state.getLocations().containsKey(mainLoc) ? state.getLocations().get(mainLoc).getName() : mainLoc;
                                        log("[DRAMATURGIC CONVERGENCE]: In Act 6 Climax, " + agent.getName() 
                                            + " was in " + state.resolveLocationName(agent.getCurrentLocationId(), state.getCurrentStep())
                                            + ". Steering towards the grand gathering in " + destName + "!");
                                        action.setActionType(com.murderhecoded.domain.ActionType.MOVE);
                                        action.setTargetLocationId(mainLoc);
                                        action.setDetails(agent.getName() + " proceeds urgently to " + destName + " for the grand confrontation.");
                                    }
                                }
                            }

                            // Dramaturgic enforcement for detective in Act 6 Late Climax
                            if (agent.isDetective() && currentAct == DramaturgyDirectorService.StoryAct.ACT_6_CLIMAX && currentActPhase == DramaturgyDirectorService.ActPhase.LATE) {
                                Agent theMurderer = state.getAgents().values().stream().filter(Agent::isMurderer).findFirst().orElse(null);
                                if (theMurderer != null && action.getActionType() != com.murderhecoded.domain.ActionType.ACCUSE) {
                                    log("[DRAMATURGIC ENFORCEMENT]: In Act 6 Late Climax, detective " + agent.getName() 
                                        + " hesitated. Enforcing grand confrontation and ACCUSE action against " + theMurderer.getName() + "!");
                                    action.setActionType(com.murderhecoded.domain.ActionType.ACCUSE);
                                    action.setTargetAgentId(theMurderer.getId());
                                    action.setDetails(agent.getName() + " gathers everyone, presents conclusive evidence, and publicly accuses " + theMurderer.getName() + " of murder!");
                                    action.setNewKnowledgeFact("Publicly accused " + theMurderer.getName() + " of the murder");
                                }
                            }
                            
                            // Contextualize and deduplicate knowledge fact
                            if (action.getNewKnowledgeFact() != null && !action.getNewKnowledgeFact().trim().isEmpty() 
                                    && !action.getNewKnowledgeFact().trim().equalsIgnoreCase("None")) {
                                String rawFact = TalkActionHandler.stripLeadingVocatives(action.getNewKnowledgeFact().trim());
                                String baseFact = stripLocationTag(rawFact).toLowerCase(java.util.Locale.ROOT);

                                boolean alreadyKnown = agent.getKnowledgeBase().stream().anyMatch(k -> {
                                    String existingBase = stripLocationTag(k.information()).trim().toLowerCase(java.util.Locale.ROOT);
                                    return existingBase.equals(baseFact) || (baseFact.length() > 20 && (existingBase.contains(baseFact) || baseFact.contains(existingBase)));
                                });

                                // Check if SEARCH invented an item that does not exist in this room
                                boolean isSearchHallucination = false;
                                if (action.getActionType() == com.murderhecoded.domain.ActionType.SEARCH) {
                                    if (baseFact.contains("found ") || baseFact.contains("discovered ") || baseFact.contains("hittade ") || baseFact.contains("upptäckte ")) {
                                        java.util.List<Item> roomItems = state.getItems().values().stream()
                                                .filter(i -> java.util.Objects.equals(i.getCurrentLocationId(), locId))
                                                .toList();
                                        boolean matchesActualRoomItem = roomItems.stream()
                                                .anyMatch(i -> i.getName() != null && baseFact.contains(i.getName().toLowerCase(java.util.Locale.ROOT)));
                                        if (!matchesActualRoomItem && !baseFact.contains("nothing") && !baseFact.contains("inget")) {
                                            isSearchHallucination = true;
                                        }
                                    }
                                }

                                // Check if OBSERVE references an absent character
                                boolean isObserveAbsentCharacter = false;
                                if (action.getActionType() == com.murderhecoded.domain.ActionType.OBSERVE) {
                                    java.util.Set<String> presentNames = roomAgents.stream()
                                            .map(a -> a.getName().toLowerCase(java.util.Locale.ROOT))
                                            .collect(java.util.stream.Collectors.toSet());
                                    for (Agent other : state.getAgents().values()) {
                                        String otherNameLower = other.getName().toLowerCase(java.util.Locale.ROOT);
                                        if (!presentNames.contains(otherNameLower) && baseFact.contains(otherNameLower)) {
                                            isObserveAbsentCharacter = true;
                                            break;
                                        }
                                    }
                                }

                                if (alreadyKnown || isSearchHallucination || isObserveAbsentCharacter) {
                                    action.setNewKnowledgeFact(null);
                                } else {
                                    String fact = rawFact;
                                    if (!fact.contains(locName)) {
                                        fact = fact + " (Location: " + locName + ")";
                                    }
                                    action.setNewKnowledgeFact(fact);
                                    sceneKnowledge.add(agent.getName() + " learned: " + fact);
                                }
                            }
                            
                            String eventDesc = describeActionForMemory(action, state, locName);
                            if (eventDesc != null && !eventDesc.isBlank()) {
                                observedEvents.add(eventDesc);
                            }

                            stepActions.add(action);
                            sceneActions.add(action);
                            processSingleAction(action);
                            broadcastWitnessKnowledge(action, agent, roomAgents, locName, nextStep);
                        } else {
                            log(agent.getName() + " failed to act.");
                        }
                    }

                    checkAndTriggerPoisonEffects(locId, locName, roomAgents, nextStep, observedEvents, stepActions, sceneActions, sceneKnowledge);
                    
                    for (String k : sceneKnowledge) {
                        log("[Memory] " + k);
                    }
                    
                    if (observedEvents.isEmpty()) {
                        observedEvents.add("The scene in " + locName + " remained quiet.");
                    }
                    Memory sceneMemory = new Memory(
                        nextStep,
                        locId,
                        locName,
                        presentAgentIds,
                        presentAgentNames,
                        presentItemIds,
                        presentItemNames,
                        observedEvents
                    );
                    for (Agent agent : roomAgents) {
                        agent.getEpisodicMemories().add(sceneMemory);
                    }
                    
                    log("[" + sceneStr + " completed]");
                    
                    TimeLocationEvent locEvent = TimeLocationEvent.builder()
                            .timeStep(state.getCurrentStep())
                            .sequenceNumber(eventCounter)
                            .locationId(locId)
                            .locationName(locName)
                            .presentAgentNames(presentAgentNames)
                            .actions(sceneActions)
                            .knowledgeGained(sceneKnowledge)
                            .snapshotBefore(snapBefore)
                            .suspenseScore(roomTension)
                            .murdererPresent(killerInRoom != null)
                            .murdererPressureInfo(killerPressure != null ? killerPressure.pressureSummary() : null)
                            .build();
                    eventsThisStep.add(locEvent);
                    
                    eventCounter++;
                    if (!running || Thread.currentThread().isInterrupted()) {
                        break;
                    }
                }
                
                if (!running || Thread.currentThread().isInterrupted()) {
                    break;
                }
                
                if (stepActions.isEmpty()) {
                    if (emptyActionRetries < 2) {
                        emptyActionRetries++;
                        log("No actions generated this turn, retrying (" + emptyActionRetries + "/2)...");
                        Thread.sleep(1000);
                        continue;
                    } else {
                        log("Characters remained observant and quiet during this interval.");
                        emptyActionRetries = 0;
                    }
                } else {
                    emptyActionRetries = 0;
                }
                
                state.incrementStep();
                
                updateObservations();
                spreadGossip();
                checkProgrammaticAccusation();
                
                state.takeSnapshot();
                SimulationStateSnapshot snapAfter = state.getSnapshotAt(state.getCurrentStep());
                
                for (TimeLocationEvent ev : eventsThisStep) {
                    ev.setTimeStep(state.getCurrentStep()); // Set correctly after incrementing
                    ev.setSnapshotAfter(snapAfter);
                    if (enableProse && proseService != null) {
                        proseService.enqueueEvent(ev);
                    }
                }
                state.recordTimelineEvents(state.getCurrentStep(), eventsThisStep);
                totalScenesGenerated.addAndGet(eventsThisStep.size());
                
                if (onStateUpdated != null) {
                    Platform.runLater(onStateUpdated);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log("Simulation interrupted.");
                break;
            } catch (Exception e) {
                if (!running || Thread.currentThread().isInterrupted()) {
                    log("Simulation stopped.");
                    break;
                }
                log("Error during simulation: " + e.getMessage());
                e.printStackTrace();
            }
        }
        log(running ? "Simulation Complete." : "Simulation Stopped.");
        running = false;
        if (enableProse && proseService != null && proseService.isRunning()) {
            proseService.stop();
        }
        if (onStateUpdated != null) {
            Platform.runLater(onStateUpdated);
        }
    }

    private final java.util.Map<com.murderhecoded.domain.ActionType, com.murderhecoded.engine.handlers.ActionHandler> actionHandlers = new java.util.EnumMap<>(com.murderhecoded.domain.ActionType.class);

    private void initializeHandlers() {
        actionHandlers.put(com.murderhecoded.domain.ActionType.MOVE, new com.murderhecoded.engine.handlers.MoveActionHandler());
        actionHandlers.put(com.murderhecoded.domain.ActionType.TALK, new com.murderhecoded.engine.handlers.TalkActionHandler());
        actionHandlers.put(com.murderhecoded.domain.ActionType.MURDER, new com.murderhecoded.engine.handlers.MurderActionHandler());
        actionHandlers.put(com.murderhecoded.domain.ActionType.SEARCH, new com.murderhecoded.engine.handlers.SearchActionHandler());
        actionHandlers.put(com.murderhecoded.domain.ActionType.ALTER_ENVIRONMENT, new com.murderhecoded.engine.handlers.AlterEnvironmentActionHandler());
        actionHandlers.put(com.murderhecoded.domain.ActionType.MANIPULATE_ITEM, new com.murderhecoded.engine.handlers.ManipulateItemActionHandler());
        
        com.murderhecoded.engine.handlers.ActionHandler defaultHandler = new com.murderhecoded.engine.handlers.DefaultActionHandler();
        actionHandlers.put(com.murderhecoded.domain.ActionType.OBSERVE, defaultHandler);
        actionHandlers.put(com.murderhecoded.domain.ActionType.REFLECT, defaultHandler);
        actionHandlers.put(com.murderhecoded.domain.ActionType.CASUAL_INTERACT, defaultHandler);
        actionHandlers.put(com.murderhecoded.domain.ActionType.ACCUSE, defaultHandler);
    }

    void processSingleAction(Action action) {
        Agent actor = state.getAgents().get(action.getActorId());
        if (actor == null || !actor.isAlive()) return;

        log(actor.getName() + " executed " + action.getActionType());
        
        if (action.getActionType() == com.murderhecoded.domain.ActionType.REFLECT) {
            actor.setConsecutiveReflectCount(actor.getConsecutiveReflectCount() + 1);
        } else {
            actor.setConsecutiveReflectCount(0);
        }

        if (action.getActionType() == com.murderhecoded.domain.ActionType.OBSERVE) {
            actor.setConsecutiveObserveCount(actor.getConsecutiveObserveCount() + 1);
            actor.setLastObservedLocationId(actor.getCurrentLocationId());
        } else {
            actor.setConsecutiveObserveCount(0);
            actor.setLastObservedLocationId(null);
        }

        if (action.getActionType() == com.murderhecoded.domain.ActionType.SEARCH) {
            actor.setConsecutiveSearchCount(actor.getConsecutiveSearchCount() + 1);
        } else {
            actor.setConsecutiveSearchCount(0);
        }

        if (action.getActionType() == com.murderhecoded.domain.ActionType.MOVE) {
            actor.setTurnsInCurrentLocation(0);
        } else {
            actor.setTurnsInCurrentLocation(actor.getTurnsInCurrentLocation() + 1);
        }

        String locName = state.resolveLocationName(actor.getCurrentLocationId(), state.getCurrentStep());
        String actionSummary = action.getActionType().name() + " in " + locName;
        if (action.getDetails() != null && !action.getDetails().isBlank()) {
            actionSummary += ": " + action.getDetails();
        }
        actor.recordAction(state.getCurrentStep(), actionSummary);
        
        if (action.getNewKnowledgeFact() != null && !action.getNewKnowledgeFact().trim().isEmpty() 
                && !action.getNewKnowledgeFact().trim().equalsIgnoreCase("None")) {
            addKnowledgeIfAbsent(actor, action.getNewKnowledgeFact().trim(), state.getCurrentStep());
        }

        com.murderhecoded.engine.handlers.ActionHandler handler = actionHandlers.get(action.getActionType());
        if (handler != null) {
            handler.handle(action, state, this::log);
        } else {
            log("Unhandled action type: " + action.getActionType());
        }
    }

    private void broadcastWitnessKnowledge(Action action, Agent actor, java.util.List<Agent> roomAgents, String locName, int timeStep) {
        if (action == null || actor == null) return;
        
        for (Agent witness : roomAgents) {
            if (witness.getId().equals(actor.getId()) || !witness.isAlive() || !witness.isHasArrived()) {
                continue;
            }
            if (action.getActionType() == com.murderhecoded.domain.ActionType.MURDER) {
                Agent victim = state.getAgents().get(action.getTargetAgentId());
                String vicName = victim != null ? victim.getName() : "someone";
                boolean hasEnvironmentalCover = state.getActiveGlobalEvents(timeStep).stream()
                        .anyMatch(ge -> "ENVIRONMENTAL".equals(ge.getImpactType()) || "ISOLATION".equals(ge.getImpactType()));
                if (hasEnvironmentalCover) {
                    addKnowledgeIfAbsent(witness, "Heard a sudden disturbance and saw " + vicName + " collapse dead in the dim lighting of " + locName + " (could not clearly identify the attacker in the chaos)", timeStep);
                } else {
                    addKnowledgeIfAbsent(witness, "Saw " + actor.getName() + " murder " + vicName + " in " + locName, timeStep);
                }
            } else if (action.getActionType() == com.murderhecoded.domain.ActionType.MANIPULATE_ITEM) {
                Item itm = state.getItems().get(action.getTargetItemId());
                String itmName = itm != null ? itm.getName() : "an item";
                String interaction = action.getInteraction() != null ? action.getInteraction().toUpperCase() : "";
                if ("TAKE".equals(interaction)) {
                    boolean notable = itm != null && (itm.getType() == com.murderhecoded.domain.ItemType.WEAPON || itm.getType() == com.murderhecoded.domain.ItemType.CLUE || itm.getType() == com.murderhecoded.domain.ItemType.NOTEBOOK);
                    String fact = "Saw " + actor.getName() + " take " + (notable ? "suspicious item " : "") + "'" + itmName + "' in " + locName;
                    addKnowledgeIfAbsent(witness, fact, timeStep);
                } else if ("HIDE".equals(interaction)) {
                    addKnowledgeIfAbsent(witness, "Saw " + actor.getName() + " secretly HIDE '" + itmName + "' in " + locName, timeStep);
                } else if ("POISON".equals(interaction) || "SABOTAGE".equals(interaction) || "LACE".equals(interaction)) {
                    addKnowledgeIfAbsent(witness, "Saw " + actor.getName() + " suspiciously tamper with or lace '" + itmName + "' in " + locName, timeStep);
                }
            } else if (action.getActionType() == com.murderhecoded.domain.ActionType.ALTER_ENVIRONMENT) {
                addKnowledgeIfAbsent(witness, "Saw " + actor.getName() + " tamper with the environment in " + locName, timeStep);
            }
        }
    }

    void checkAndTriggerPoisonEffects(String locId, String locName, java.util.List<Agent> roomAgents, int nextStep,
                                      java.util.List<String> observedEvents, java.util.List<Action> stepActions,
                                      java.util.List<Action> sceneActions, java.util.List<String> sceneKnowledge) {
        java.util.List<Item> poisonedItems = state.getItems().values().stream()
                .filter(i -> i.isPoisoned() && (java.util.Objects.equals(i.getCurrentLocationId(), locId) ||
                        roomAgents.stream().anyMatch(a -> a.getId().equals(i.getCurrentLocationId()))))
                .toList();

        if (poisonedItems.isEmpty()) {
            return;
        }

        for (Item item : poisonedItems) {
            for (Agent victim : roomAgents) {
                if (!victim.isAlive() || !victim.isHasArrived()) {
                    continue;
                }
                if (java.util.Objects.equals(item.getPoisonedByAgentId(), victim.getId())) {
                    continue;
                }
                boolean isTarget = item.getPoisonTargetAgentId() == null
                        || item.getPoisonTargetAgentId().isBlank()
                        || item.getPoisonTargetAgentId().equalsIgnoreCase(victim.getId())
                        || item.getPoisonTargetAgentId().toLowerCase(java.util.Locale.ROOT).contains(victim.getName().toLowerCase(java.util.Locale.ROOT));

                if (isTarget) {
                    victim.setAlive(false);
                    item.setPoisoned(false);
                    item.setPoisonConsumed(true);
                    state.resetIncidentTimer();

                    Agent killer = item.getPoisonedByAgentId() != null ? state.getAgents().get(item.getPoisonedByAgentId()) : null;
                    if (killer != null) {
                        killer.setHasMurderIntent(true);
                        killer.setTargetVictimId(victim.getId());
                    }

                    String desc = victim.getName() + " suddenly gasped in agony, clutched their throat, and collapsed lifeless to the floor! The poisoned " + item.getName() + " lies nearby.";
                    observedEvents.add(desc);
                    log("[REMOTE POISONING] " + desc);

                    for (Agent witness : roomAgents) {
                        if (witness.getId().equals(victim.getId()) || !witness.isAlive() || !witness.isHasArrived()) {
                            continue;
                        }
                        String fact = "Saw " + victim.getName() + " collapse and die from poisoning in " + locName;
                        addKnowledgeIfAbsent(witness, fact, nextStep);
                        sceneKnowledge.add(witness.getName() + " learned: " + fact);
                    }

                    Action murderAction = new Action();
                    murderAction.setActorId(killer != null ? killer.getId() : "poison");
                    murderAction.setActionType(com.murderhecoded.domain.ActionType.MURDER);
                    murderAction.setTargetAgentId(victim.getId());
                    murderAction.setWeaponItemId(item.getId());
                    murderAction.setDetails(desc);
                    stepActions.add(murderAction);
                    sceneActions.add(murderAction);

                    if (!victim.getKnowledgeBase().isEmpty()) {
                        Item notebook = new Item();
                        notebook.setId("notebook_" + java.util.UUID.randomUUID().toString().substring(0, 8));
                        notebook.setName("Notebook of " + victim.getName());
                        notebook.setType(com.murderhecoded.domain.ItemType.NOTEBOOK);
                        notebook.setCurrentLocationId(locId);
                        notebook.getNotebookKnowledge().addAll(victim.getKnowledgeBase());
                        state.getItems().put(notebook.getId(), notebook);
                    }
                    break;
                }
            }
        }
    }

    Action generateSafeFallbackAction(Agent agent, SimulationRepository state, java.util.List<Agent> roomAgents) {
        Action action = new Action();
        action.setActorId(agent.getId());
        java.util.List<Agent> presentOthers = roomAgents != null ? roomAgents.stream()
                .filter(a -> a.isAlive() && a.isHasArrived() && !a.getId().equals(agent.getId()))
                .toList() : java.util.List.of();

        boolean shouldMove = agent.getConsecutiveSearchCount() >= 1 
                || agent.getTurnsInCurrentLocation() >= 2 
                || presentOthers.isEmpty();

        // In Act 6 Climax, all surviving agents prioritize assembling in the grand gathering area
        if (dramaturgyDirectorService != null && state != null && dramaturgyDirectorService.determineCurrentAct(state) == DramaturgyDirectorService.StoryAct.ACT_6_CLIMAX) {
            String mainLoc = dramaturgyDirectorService.getMainLocationId(state);
            if (mainLoc != null && !java.util.Objects.equals(agent.getCurrentLocationId(), mainLoc)) {
                String destName = state.resolveLocationName(mainLoc, state.getCurrentStep());
                action.setActionType(com.murderhecoded.domain.ActionType.MOVE);
                action.setTargetLocationId(mainLoc);
                action.setDetails(agent.getName() + " proceeds to " + destName + " for the grand gathering.");
                return action;
            }
        }

        // If detective and a murder has occurred (prior to climax), prioritize moving to the crime scene!
        if (agent.isDetective() && state != null && state.getAgents() != null) {
            Agent deadVictim = state.getAgents().values().stream()
                    .filter(a -> !a.isAlive() && a.getCurrentLocationId() != null)
                    .findFirst()
                    .orElse(null);
            if (deadVictim != null && !agent.hasInspectedCrimeScene() && !java.util.Objects.equals(agent.getCurrentLocationId(), deadVictim.getCurrentLocationId())) {
                String crimeLoc = deadVictim.getCurrentLocationId();
                String destName = state.resolveLocationName(crimeLoc, state.getCurrentStep());
                action.setActionType(com.murderhecoded.domain.ActionType.MOVE);
                action.setTargetLocationId(crimeLoc);
                action.setDetails(agent.getName() + " proceeds urgently to " + destName + " to inspect the body of " + deadVictim.getName() + " and secure the crime scene.");
                return action;
            }
        }

        if (shouldMove && state.getLocations() != null && state.getLocations().size() > 1) {
            com.murderhecoded.domain.Location nextLoc = state.getLocations().values().stream()
                    .filter(l -> !java.util.Objects.equals(l.getId(), agent.getCurrentLocationId()))
                    .findFirst()
                    .orElse(null);
            if (nextLoc != null) {
                action.setActionType(com.murderhecoded.domain.ActionType.MOVE);
                action.setTargetLocationId(nextLoc.getId());
                action.setDetails(agent.getName() + " walks purposefully to " + nextLoc.getName() + ".");
                return action;
            }
        }

        if (!presentOthers.isEmpty()) {
            Agent other = presentOthers.get(0);
            action.setActionType(com.murderhecoded.domain.ActionType.TALK);
            action.setTargetAgentId(other.getId());
            action.setSharedKnowledge("Discusses recent events and surroundings.");
            action.setDetails(agent.getName() + " speaks quietly with " + other.getName() + ".");
            return action;
        }

        action.setActionType(com.murderhecoded.domain.ActionType.SEARCH);
        action.setDetails(agent.getName() + " looks around the room carefully.");
        return action;
    }

    private String stripLocationTag(String fact) {
        if (fact == null) return "";
        return fact.replaceAll("\\s*\\(Location:\\s*[^)]+\\)\\s*$", "").trim();
    }

    private void addKnowledgeIfAbsent(Agent agent, String fact, int timeStep) {
        if (fact == null || fact.isBlank() || agent == null) return;
        boolean added = agent.addKnowledge(new Knowledge(fact.trim(), timeStep));
        if (added) {
            logApp.debug("Agent {} gained knowledge: {}", agent.getName(), fact);
            log("[Memory] " + agent.getName() + " learned: " + fact.trim());
        }
    }

    private String describeActionForMemory(Action action, SimulationRepository state, String locName) {
        if (action == null || action.getActorId() == null) {
            return null;
        }
        Agent actor = state.getAgents().get(action.getActorId());
        String actorName = actor != null ? actor.getName() : action.getActorId();
        ActionType type = action.getActionType();
        if (type == null) {
            return actorName + " acted.";
        }
        switch (type) {
            case MANIPULATE_ITEM -> {
                Item itm = state.getItems().get(action.getTargetItemId());
                String itmName = itm != null ? itm.getName() : (action.getTargetItemId() != null ? action.getTargetItemId() : "an item");
                String inter = action.getInteraction() != null ? action.getInteraction().toUpperCase() : "INTERACTED WITH";
                return switch (inter) {
                    case "TAKE" -> actorName + " took '" + itmName + "'.";
                    case "DROP" -> actorName + " dropped '" + itmName + "'.";
                    case "HIDE" -> actorName + " hid '" + itmName + "'.";
                    case "POISON", "SABOTAGE", "LACE" -> actorName + " suspiciously tampered with '" + itmName + "'.";
                    default -> actorName + " interacted with '" + itmName + "'.";
                };
            }
            case MOVE -> {
                Location dest = state.getLocations().get(action.getTargetLocationId());
                String destName = dest != null ? dest.getName() : action.getTargetLocationId();
                return actorName + " left " + locName + " for " + (destName != null ? destName : "another room") + ".";
            }
            case MURDER -> {
                Agent vic = state.getAgents().get(action.getTargetAgentId());
                String vicName = vic != null ? vic.getName() : action.getTargetAgentId();
                return actorName + " attacked and murdered " + (vicName != null ? vicName : "someone") + "!";
            }
            case TALK -> {
                Agent other = state.getAgents().get(action.getTargetAgentId());
                String otherName = other != null ? other.getName() : "someone";
                String topic = action.getDetails() != null && !action.getDetails().isBlank() ? action.getDetails() : action.getSharedKnowledge();
                if (topic != null && !topic.isBlank()) {
                    String cleanTopic = TalkActionHandler.stripLeadingVocatives(topic);
                    return actorName + " conversed with " + otherName + " about: " + cleanTopic;
                }
                return actorName + " conversed with " + otherName + ".";
            }
            case SEARCH -> {
                return actorName + " searched " + locName + " carefully.";
            }
            case ALTER_ENVIRONMENT -> {
                String desc = action.getTraceDescription() != null ? action.getTraceDescription() : action.getDetails();
                return actorName + " altered the environment" + (desc != null ? " (" + desc + ")" : "") + ".";
            }
            case ACCUSE -> {
                Agent acc = state.getAgents().get(action.getTargetAgentId());
                String accName = acc != null ? acc.getName() : "someone";
                return actorName + " openly accused " + accName + " of murder!";
            }
            case OBSERVE -> {
                return actorName + " observed the room and occupants quietly.";
            }
            case REFLECT -> {
                return actorName + " paused and reflected on the situation.";
            }
            case CASUAL_INTERACT -> {
                return actorName + " engaged in casual activities in " + locName + ".";
            }
            default -> {
                return actorName + " spent time in " + locName + ".";
            }
        }
    }

    private void updateObservations() {
        boolean murderNewlyDiscovered = false;
        for (Agent agent : state.getAgents().values()) {
            if (!agent.isAlive() || !agent.isHasArrived()) continue;
            String loc = agent.getCurrentLocationId();
            Location p = state.getLocations().get(loc);
            
            for (Agent other : state.getAgents().values()) {
                if (!other.isAlive() && java.util.Objects.equals(other.getCurrentLocationId(), loc) && loc != null && !other.getId().equals(agent.getId())) {
                    String fact = "Dead body of " + other.getName() + " seen in " + p.getName();
                    if (agent.getKnowledgeBase().stream().noneMatch(k -> k.information().equals(fact))) {
                        agent.getKnowledgeBase().add(new Knowledge(fact, state.getCurrentStep()));
                        boolean firstDiscoveryGlobally = state.getAgents().values().stream()
                                .noneMatch(a -> !a.getId().equals(agent.getId()) && a.getKnowledgeBase().stream().anyMatch(k -> k.information().startsWith("Dead body of " + other.getName())));
                        if (firstDiscoveryGlobally) {
                            log("" + agent.getName() + " DISCOVERED the dead body of " + other.getName() + "!");
                            murderNewlyDiscovered = true;
                        } else {
                            log("" + agent.getName() + " entered the area and observed the dead body of " + other.getName() + ".");
                        }
                    }
                }
            }
        }
        
        // If a murder was just discovered, the detective(s) arrive!
        if (murderNewlyDiscovered) {
            state.recordIncident();
            String arrivalRoom = state.getLocations().isEmpty() ? "room_0" : state.getLocations().keySet().iterator().next();
            for (Agent agent : state.getAgents().values()) {
                if (!agent.isHasArrived()) {
                    agent.setHasArrived(true);
                    if (agent.getCurrentLocationId() == null || !state.getLocations().containsKey(agent.getCurrentLocationId())) {
                        agent.setCurrentLocationId(arrivalRoom);
                    }
                    log("THE DETECTIVE ARRIVES! " + agent.getName() + " has entered the investigation.");
                }
            }
        }
    }
    
    private void spreadGossip() {
        java.util.List<Agent> agents = new java.util.ArrayList<>(state.getAgents().values());
        for (int i = 0; i < agents.size(); i++) {
            Agent a1 = agents.get(i);
            if (!a1.isAlive()) continue;
            for (int j = i + 1; j < agents.size(); j++) {
                Agent a2 = agents.get(j);
                if (!a2.isAlive()) continue;
                if (java.util.Objects.equals(a1.getCurrentLocationId(), a2.getCurrentLocationId()) && a1.getCurrentLocationId() != null) {
                    for (Knowledge k : new java.util.ArrayList<>(a1.getKnowledgeBase())) {
                        if (k.information().startsWith("Dead body")) {
                            if (a2.getKnowledgeBase().stream().noneMatch(existing -> existing.information().equals(k.information()))) {
                                a2.getKnowledgeBase().add(new Knowledge(k.information(), state.getCurrentStep()));
                                log(a1.getName() + " told " + a2.getName() + " about the murder!");
                            }
                        }
                    }
                    for (Knowledge k : new java.util.ArrayList<>(a2.getKnowledgeBase())) {
                        if (k.information().startsWith("Dead body")) {
                            if (a1.getKnowledgeBase().stream().noneMatch(existing -> existing.information().equals(k.information()))) {
                                a1.getKnowledgeBase().add(new Knowledge(k.information(), state.getCurrentStep()));
                                log(a2.getName() + " told " + a1.getName() + " about the murder!");
                            }
                        }
                    }
                }
            }
        }
    }

    void checkProgrammaticAccusation() {
        boolean murderDiscovered = state.getAgents().values().stream()
            .flatMap(a -> a.getKnowledgeBase().stream())
            .anyMatch(k -> k.information().startsWith("Dead body"));
            
        if (!murderDiscovered) return;

        int totalTargetSteps = state.getTargetSteps() > 0 ? state.getTargetSteps() : 144;
        com.murderhecoded.domain.DramaturgyProfile profile = dramaturgyDirectorService.resolveProfile(state);
        double progress = (double) state.getCurrentStep() / totalTargetSteps;
        
        for (Agent agent : state.getAgents().values()) {
            if (!agent.isAlive() || agent.isMurderer()) continue;
            
            long totalFacts = agent.getKnowledgeBase().stream()
                    .map(Knowledge::information)
                    .map(Agent::normalizeKnowledge)
                    .filter(s -> !s.isBlank())
                    .distinct()
                    .count();
            boolean knowsMurder = agent.getKnowledgeBase().stream().anyMatch(k -> k.information().startsWith("Dead body"));
            boolean isDetective = agent.isDetective() || agent.getId().equals("agent_0") || 
                (agent.getRole() != null && agent.getRole().toLowerCase().contains("detective"));
            
            boolean canAccuse = !agent.isMurderer() && knowsMurder && (
                (isDetective && totalFacts >= 3 && progress >= profile.act6ClimaxThreshold()) ||
                (isDetective && totalFacts >= 5 && progress >= profile.act5EscalationThreshold()) ||
                (totalFacts >= 7 && progress >= profile.act5EscalationThreshold()) ||
                (isDetective && progress >= 0.95)
            );
            
            if (canAccuse) {
                log("ACCUSATION! " + agent.getName() + " has assembled the crucial clues (" + totalFacts + " facts) and solves the case!");
                state.recordIncident();
                state.setCompleted(true);
                return;
            }
        }
    }

    private void log(String message) {
        final String timedMessage;
        if (!message.startsWith("[")) {
            timedMessage = "[" + state.getClockTime(state.getCurrentStep()) + "] " + message;
        } else {
            timedMessage = message;
        }
        state.getEventHistory().add(timedMessage);
        if (onLogEvent != null) {
            Platform.runLater(() -> onLogEvent.accept(timedMessage));
        }
    }
}

