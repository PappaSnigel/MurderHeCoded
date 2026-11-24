package com.murderhecoded.service;

import com.murderhecoded.domain.Agent;
import com.murderhecoded.domain.DramaturgyProfile;
import com.murderhecoded.domain.GlobalEvent;
import com.murderhecoded.domain.Item;
import com.murderhecoded.domain.ItemType;
import com.murderhecoded.domain.Location;
import com.murderhecoded.domain.SimulationStateSnapshot;
import com.murderhecoded.domain.TimeLocationEvent;
import com.murderhecoded.repository.SimulationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Consumer;

@Service
@SuppressWarnings("PMD")
public class DramaturgyDirectorService {

    private static final Logger log = LoggerFactory.getLogger(DramaturgyDirectorService.class);

    public enum StoryAct {
        ACT_1_EXPOSITION("Act 1: Exposition & Arrival", 1),
        ACT_2_RISING_SUSPENSE("Act 2: Rising Suspense & Pre-Crime Plotting", 2),
        ACT_3_THE_CRIME("Act 3: The Crime & Strike Opportunity", 3),
        ACT_4_DISCOVERY("Act 4: Body Discovery & Initial Probe", 4),
        ACT_5_ESCALATION("Act 5: Escalation & High Stakes", 5),
        ACT_6_CLIMAX("Act 6: Grand Climax & Resolution", 6);

        private final String description;
        private final int actNumber;

        StoryAct(String description, int actNumber) {
            this.description = description;
            this.actNumber = actNumber;
        }

        public String getDescription() {
            return description;
        }

        public int getActNumber() {
            return actNumber;
        }
    }

    public enum TensionTrend {
        RISING,
        PEAK,
        LULL,
        CLIMAX
    }

    public enum MurdererUrgency {
        PREPARATION,
        OPPORTUNITY,
        DESPERATE,
        POST_CRIME
    }

    public enum DetectiveState {
        UNAWARE_GUEST,
        ACTIVE_INVESTIGATION,
        CLIMAX_ACCUSATION
    }

    public enum ActPhase {
        EARLY("Early Phase"),
        MID("Mid Phase"),
        LATE("Late Phase / Climax");

        private final String displayName;

        ActPhase(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private boolean introEventTriggered = false;
    private boolean opportunityEventTriggered = false;
    private boolean bodyDiscovered = false;
    private String discoveredBodyAgentId = null;
    private int deathStep = -1;
    private int discoveryStep = -1;
    private boolean climaxEventTriggered = false;
    private int dynamicEventCounter = 1;
    private final EditorialScorer editorialScorer;
    private DramaturgyProfile profile = DramaturgyProfile.standard();

    @Autowired
    public DramaturgyDirectorService(EditorialScorer editorialScorer) {
        this(editorialScorer, DramaturgyProfile.standard());
    }

    public DramaturgyDirectorService(EditorialScorer editorialScorer, DramaturgyProfile profile) {
        this.editorialScorer = editorialScorer != null ? editorialScorer : new EditorialScorer();
        this.profile = profile != null ? profile : DramaturgyProfile.standard();
    }

    public DramaturgyDirectorService() {
        this(new EditorialScorer(), DramaturgyProfile.standard());
    }

    public DramaturgyProfile getProfile() {
        return profile;
    }

    public void setProfile(DramaturgyProfile profile) {
        this.profile = profile != null ? profile : DramaturgyProfile.standard();
    }

    public DramaturgyProfile resolveProfile(SimulationRepository state) {
        if (state != null && state.getDramaturgyProfile() != null) {
            return state.getDramaturgyProfile();
        }
        return this.profile != null ? this.profile : DramaturgyProfile.standard();
    }

    public void reset() {
        this.introEventTriggered = false;
        this.opportunityEventTriggered = false;
        this.bodyDiscovered = false;
        this.discoveredBodyAgentId = null;
        this.deathStep = -1;
        this.discoveryStep = -1;
        this.climaxEventTriggered = false;
        this.dynamicEventCounter = 1;
        log.info("DramaturgyDirectorService state reset.");
    }

    /**
     * Calculates the theoretical narrative drama score for a given time-step based on the
     * active DramaturgyProfile harmonic curve.
     *
     * @param step Current time step (1-indexed)
     * @param totalSteps Total steps planned for the simulation
     * @return Target drama score from 0.0 to 10.0
     */
    public static double calculateTargetTension(int step, int totalSteps) {
        return DramaturgyProfile.standard().calculateTargetTension(step, totalSteps);
    }

    public static TensionTrend determineTensionTrend(int step, int totalSteps) {
        return DramaturgyProfile.standard().determineTensionTrend(step, totalSteps);
    }

    public double calculateTargetTension(int step, int totalSteps, DramaturgyProfile profile) {
        return (profile != null ? profile : this.profile).calculateTargetTension(step, totalSteps);
    }

    public TensionTrend determineTensionTrend(int step, int totalSteps, DramaturgyProfile profile) {
        return (profile != null ? profile : this.profile).determineTensionTrend(step, totalSteps);
    }

    public StoryAct determineActForStep(SimulationRepository state, int step) {
        int target = Math.max(1, state != null ? state.getTargetSteps() : 1);
        double progress = (double) step / target;
        DramaturgyProfile activeProfile = resolveProfile(state);

        int resolvedDeath = this.deathStep;
        int resolvedDiscovery = this.discoveryStep;

        if (resolvedDeath == -1 && state != null) {
            for (int s = 1; s <= state.getCurrentStep(); s++) {
                List<TimeLocationEvent> evs = state.getEventsForStep(s);
                if (evs != null && evs.stream().anyMatch(TimeLocationEvent::hasMurderAction)) {
                    resolvedDeath = s;
                    break;
                }
            }
        }

        if (resolvedDiscovery == -1 && state != null && state.getGlobalEvents() != null) {
            for (GlobalEvent ge : state.getGlobalEvents().values()) {
                if ("ALARM".equalsIgnoreCase(ge.getImpactType()) ||
                        (ge.getName() != null && ge.getName().toLowerCase(Locale.ROOT).contains("discovery"))) {
                    resolvedDiscovery = ge.getScheduledStep();
                    break;
                }
            }
        }

        boolean hasDead = resolvedDeath >= 0 ? step >= resolvedDeath : (getFirstDeadAgent(state) != null && step >= (state != null ? state.getCurrentStep() : step));
        boolean isDiscovered = resolvedDiscovery >= 0 ? step >= resolvedDiscovery : (this.bodyDiscovered && (state == null || step >= state.getCurrentStep()));

        return activeProfile.determineAct(progress, isDiscovered, hasDead);
    }

    public ActPhase determineActPhaseForStep(SimulationRepository state, int step) {
        StoryAct act = determineActForStep(state, step);
        DramaturgyProfile activeProfile = resolveProfile(state);
        double progress = (double) step / Math.max(1, state != null ? state.getTargetSteps() : 1);
        return activeProfile.determineActPhase(act, progress);
    }

    public double determineActProgressForStep(SimulationRepository state, int step) {
        StoryAct act = determineActForStep(state, step);
        DramaturgyProfile activeProfile = resolveProfile(state);
        double progress = (double) step / Math.max(1, state != null ? state.getTargetSteps() : 1);
        return activeProfile.calculateActProgress(act, progress);
    }

    public StoryAct determineCurrentAct(SimulationRepository state) {
        return determineActForStep(state, state != null ? state.getCurrentStep() : 0);
    }

    public ActPhase determineCurrentActPhase(SimulationRepository state) {
        return determineActPhaseForStep(state, state != null ? state.getCurrentStep() : 0);
    }

    public double determineCurrentActProgress(SimulationRepository state) {
        return determineActProgressForStep(state, state != null ? state.getCurrentStep() : 0);
    }

    public void evaluateAndDirectStep(SimulationRepository state, String language, Consumer<String> eventLogger) {
        int step = state.getCurrentStep();
        int target = Math.max(1, state.getTargetSteps());
        double progress = (double) step / target;
        DramaturgyProfile activeProfile = resolveProfile(state);

        checkVictimDeathAndDiscovery(state, activeProfile, eventLogger);

        StoryAct currentAct = determineCurrentAct(state);
        triggerActEvents(state, currentAct, step, progress, activeProfile, eventLogger);
    }

    private void triggerActEvents(SimulationRepository state, StoryAct act, int step, double progress, DramaturgyProfile activeProfile, Consumer<String> eventLogger) {
        if (act == StoryAct.ACT_1_EXPOSITION && step >= 1 && !introEventTriggered) {
            triggerIntroEvent(state, eventLogger);
        } else if (act == StoryAct.ACT_2_RISING_SUSPENSE && progress >= activeProfile.act2RisingSuspenseThreshold() && !opportunityEventTriggered && !bodyDiscovered) {
            triggerOpportunityEvent(state, eventLogger);
        } else if (act == StoryAct.ACT_6_CLIMAX && !climaxEventTriggered) {
            triggerClimaxEvent(state, eventLogger);
        }
    }

    private void checkVictimDeathAndDiscovery(SimulationRepository state, DramaturgyProfile activeProfile, Consumer<String> eventLogger) {
        Agent dead = getFirstDeadAgent(state);
        if (dead == null) {
            return;
        }
        if (deathStep == -1) {
            deathStep = state.getCurrentStep();
            if (log.isInfoEnabled()) {
                log.info("Dramaturgy Director noted death of victim {} at step {}", dead.getName(), deathStep);
            }
        }

        if (!bodyDiscovered) {
            String deathLocId = dead.getCurrentLocationId();
            boolean witnessInRoom = state.getAgents().values().stream()
                    .anyMatch(a -> a.isAlive() && Objects.equals(a.getCurrentLocationId(), deathLocId));

            if (witnessInRoom) {
                triggerBodyDiscovery(state, dead, eventLogger);
            } else if (state.getCurrentStep() - deathStep >= activeProfile.unwitnessedBodyDiscoveryDelay()) {
                // If body has not been found after configured delay steps, trigger discovery via inspection
                triggerBodyDiscovery(state, dead, eventLogger);
            }
        }
    }

    private void triggerBodyDiscovery(SimulationRepository state, Agent victim, Consumer<String> eventLogger) {
        bodyDiscovered = true;
        discoveredBodyAgentId = victim.getId();
        discoveryStep = state.getCurrentStep();

        String locId = victim.getCurrentLocationId();
        String locName = locId != null && state.getLocations().containsKey(locId)
                ? state.getLocations().get(locId).getName()
                : "the scene";

        String evId = "event_discovery_" + dynamicEventCounter++;
        String evName = "Alarm: Body Discovered in " + locName + "!";
        String evDesc = String.format("A chilling cry rings out! The lifeless body of %s has been discovered in %s. The area is sealed and panic surges.",
                victim.getName(), locName);

        GlobalEvent event = new GlobalEvent(evId, evName, evDesc, state.getCurrentStep(), 3, locId, "ALARM", true, true, false);
        state.putGlobalEvent(evId, event);

        String msg = "[Dramaturgic Event] " + evName + " - " + evDesc;
        log.info(msg);
        if (eventLogger != null) {
            eventLogger.accept(msg);
        }
    }

    private void triggerIntroEvent(SimulationRepository state, Consumer<String> eventLogger) {
        introEventTriggered = true;
        String mainLoc = getMainLocationId(state);
        String locName = (mainLoc != null && state.getLocations().containsKey(mainLoc))
                ? state.getLocations().get(mainLoc).getName()
                : "the main gathering area";
        String evId = "event_intro_" + dynamicEventCounter++;

        GlobalEvent event = new GlobalEvent(evId, "Initial Gathering & Arrival",
                "Everyone convenes in " + locName + " as the story begins, exchanging greetings and observing their surroundings.",
                state.getCurrentStep(), 2, mainLoc, "GATHERING", true, true, false);

        state.putGlobalEvent(evId, event);
        notifyEvent(event, eventLogger);
    }

    private void triggerOpportunityEvent(SimulationRepository state, Consumer<String> eventLogger) {
        opportunityEventTriggered = true;
        String evId = "event_cover_" + dynamicEventCounter++;

        GlobalEvent event = new GlobalEvent(evId, "Sudden Disturbance & Dimming Lights",
                "A sudden disturbance and flickering lights plunge the surroundings into darkness and confusion, creating an opportune cover.",
                state.getCurrentStep(), 2, null, "ENVIRONMENTAL", true, true, false);

        state.putGlobalEvent(evId, event);
        notifyEvent(event, eventLogger);
    }

    private void triggerClimaxEvent(SimulationRepository state, Consumer<String> eventLogger) {
        climaxEventTriggered = true;
        String mainLoc = getMainLocationId(state);
        String locName = (mainLoc != null && state.getLocations().containsKey(mainLoc))
                ? state.getLocations().get(mainLoc).getName()
                : "the gathering area";
        String evId = "event_climax_" + dynamicEventCounter++;

        GlobalEvent event = new GlobalEvent(evId, "The Grand Gathering: Final Summons",
                "All surviving persons are urgently assembled in " + locName + ". The investigator prepares to lay out the evidence and confront the killer.",
                state.getCurrentStep(), 3, mainLoc, "GATHERING", true, true, false);

        state.putGlobalEvent(evId, event);
        notifyEvent(event, eventLogger);
    }

    private void notifyEvent(GlobalEvent event, Consumer<String> eventLogger) {
        String msg = "[Dramaturgic Event] " + event.getName() + " - " + event.getDescription();
        log.info(msg);
        if (eventLogger != null) {
            eventLogger.accept(msg);
        }
    }

    public String buildDramaturgicDirectives(Agent agent, SimulationRepository state) {
        StringBuilder sb = new StringBuilder();
        DramaturgyProfile activeProfile = resolveProfile(state);
        StoryAct act = determineCurrentAct(state);
        ActPhase phase = determineCurrentActPhase(state);
        double actProgress = determineCurrentActProgress(state);
        int currentStep = state.getCurrentStep();
        int targetSteps = Math.max(1, state.getTargetSteps());
        double progress = (double) currentStep / targetSteps;
        double globalDramaScore = activeProfile.calculateTargetTension(currentStep, targetSteps);
        TensionTrend trend = activeProfile.determineTensionTrend(currentStep, targetSteps);

        List<Agent> agentsInRoom = state.getAgents().values().stream()
                .filter(a -> a.isAlive() && a.isHasArrived() && Objects.equals(a.getCurrentLocationId(), agent.getCurrentLocationId()))
                .toList();
        double roomSuspense = editorialScorer.calculateLiveRoomTension(state, agentsInRoom, currentStep + 1);

        if (isEmergencyOverrideActive(state)) {
            String crisisMsg = getActiveCrisisDescription(state);
            sb.append("\n=================================================================\n");
            sb.append("🚨 [WORLD STATE OVERRIDE - ACTIVE CRISIS / EMERGENCY INTERRUPT] 🚨\n");
            sb.append(crisisMsg).append("\n");
            sb.append("ALL ORDINARY ROUTINES AND BACKGROUND GOALS ARE STRICTLY OVERRIDDEN!\n");
            sb.append("Every character is gripped by shock, horror, suspicion, or intense panic.\n");
            sb.append("You MUST NOT pursue idle hobbies or ordinary job tasks. React directly to the crisis:\n");
            sb.append("- Inquire about the discovery, demand explanations, or interrogate others (TALK)\n");
            sb.append("- Rush to the scene, flee from danger, or seek allies (MOVE)\n");
            sb.append("- Search for weapons, inspect doors/locks, or look for clues (SEARCH)\n");
            sb.append("- If you are the killer: feign innocent shock, discard your weapon, or establish an alibi!\n");
            sb.append("=================================================================\n\n");
        }

        sb.append("\n=== DRAMATURGIC DIRECTOR GUIDANCE ===\n");
        sb.append("Current Narrative Stage: [").append(act.getDescription()).append("]\n");
        sb.append(String.format(Locale.ROOT, "Act Progression: Phase [%s] (%.0f%% through Act)\n", phase.getDisplayName(), actProgress * 100));
        sb.append(String.format(Locale.ROOT, "Story Progress: Step %d of %d (%.1f%% of planned mystery)\n", currentStep, targetSteps, progress * 100));
        sb.append(String.format(Locale.ROOT, "Global Drama Score: [%.1f/10.0] (Pacing Trend: %s)\n", globalDramaScore, trend.name()));
        sb.append(String.format(Locale.ROOT, "Current Room Suspense Score: [%.1f/10.0]\n", roomSuspense));
        if (roomSuspense >= activeProfile.criticalRoomSuspenseThreshold() || globalDramaScore >= activeProfile.criticalGlobalDramaThreshold()) {
            sb.append("ATMOSPHERE ALERT: Dramatic suspense is at a critical peak! Tension is palpable and events cannot continue quietly. Senses are heightened and decisive actions are required.\n");
        } else if (trend == TensionTrend.LULL) {
            sb.append("ATMOSPHERE NOTE: An ominous post-crime lull blankets the area. Suspicions simmer under a veneer of shock and concealment.\n");
        }

        Agent murderer = state.getAgents().values().stream().filter(Agent::isMurderer).findFirst().orElse(null);
        Agent victim = murderer != null ? findTargetVictim(murderer, state) : null;
        boolean isVictim = victim != null && agent.getId().equals(victim.getId());

        if (agent.isMurderer()) {
            appendMurdererGuidance(sb, agent, state, act, phase, actProgress, roomSuspense, agentsInRoom, activeProfile);
        } else if (agent.isDetective()) {
            appendDetectiveGuidance(sb, agent, state, act, phase, actProgress, roomSuspense, activeProfile, agentsInRoom);
        } else if (isVictim) {
            appendVictimGuidance(sb, agent, state, act, phase, actProgress, roomSuspense, activeProfile);
        } else {
            appendSuspectGuidance(sb, agent, state, act, phase, actProgress, roomSuspense, activeProfile);
        }

        // Anti-stalling rule
        if (agent.getConsecutiveReflectCount() >= activeProfile.antiStallingTurnThreshold()) {
            boolean hasOthers = agentsInRoom != null && agentsInRoom.stream().anyMatch(a -> a.isAlive() && !a.getId().equals(agent.getId()));
            String actionList = hasOthers ? "MOVE to another room, TALK to someone present, or SEARCH" : "MOVE to another room or SEARCH";
            sb.append("\n[CRITICAL PACING DIRECTIVE - ANTI-STALLING]: You have spent recent turns in quiet contemplation. You MUST choose an active physical action this turn: ").append(actionList).append(".\n");
        }
        if (agent.getTurnsInCurrentLocation() >= 3) {
            sb.append("\n[CRITICAL PACING DIRECTIVE - SPATIAL MOBILITY]: You have remained in this location for ").append(agent.getTurnsInCurrentLocation()).append(" consecutive turns. You MUST MOVE to an adjacent room to explore, seek company, or follow leads!\n");
        }

        appendActiveGlobalEventGuidance(sb, state);

        return sb.toString();
    }

    private void appendMurdererGuidance(StringBuilder sb, Agent agent, SimulationRepository state, StoryAct act, ActPhase phase, double actProgress, double roomSuspense, List<Agent> agentsInRoom, DramaturgyProfile activeProfile) {
        Agent dead = getFirstDeadAgent(state);
        if (dead != null) {
            sb.append("[POST-CRIME PHASE]: The crime has already occurred and ").append(dead.getName()).append(" is DEAD! Blend in completely! Hide or discard any murder weapon, offer plausible alibis, act shocked, and do not draw attention.\n");
            sb.append("STRICT RULE: The crime is accomplished! You are STRICTLY FORBIDDEN from committing another murder, attempting to lure anyone, or interacting with the deceased.\n");
            return;
        }

        Agent victim = findTargetVictim(agent, state);
        String victimName = victim != null ? victim.getName() : "your target victim";
        String victimId = victim != null ? victim.getId() : (agent.getTargetVictimId() != null ? agent.getTargetVictimId() : "target");

        boolean victimInRoom = victim != null && agentsInRoom.stream().anyMatch(a -> a.getId().equals(victim.getId()));
        double progress = (double) state.getCurrentStep() / Math.max(1, state.getTargetSteps());

        List<Item> heldWeapons = state.getItems().values().stream()
                .filter(it -> agent.getId().equals(it.getCurrentLocationId()) && it.getType() == ItemType.WEAPON)
                .toList();
        boolean holdsWeapon = !heldWeapons.isEmpty();

        List<Item> roomWeapons = state.getItems().values().stream()
                .filter(it -> Objects.equals(agent.getCurrentLocationId(), it.getCurrentLocationId()) && !it.isHidden() && it.getType() == ItemType.WEAPON)
                .toList();

        List<Item> otherWeapons = state.getItems().values().stream()
                .filter(it -> it.getType() == ItemType.WEAPON && !it.isHidden() && !Objects.equals(agent.getCurrentLocationId(), it.getCurrentLocationId()) && state.getLocations().containsKey(it.getCurrentLocationId()))
                .toList();

        String curLocId = agent.getCurrentLocationId();
        String curLocName = curLocId != null && state.getLocations().containsKey(curLocId) ? state.getLocations().get(curLocId).getName() : "this area";

        if (victimInRoom) {
            if (isStrikeWindow(state, agent, agentsInRoom)) {
                String phaseTag = (progress >= activeProfile.act3CrimeThreshold()) ? "[URGENCY - DESPERATE PHASE]" : "[URGENCY - STRIKE WINDOW]";
                sb.append(phaseTag).append(": [CRITICAL STRIKE DIRECTIVE - OPTIMAL CONDITIONS MET]!\n");
                sb.append("Your target victim ").append(victimName).append(" (id: '").append(victimId).append("') is IN THIS ROOM RIGHT NOW!\n");
                sb.append("The pacing is right, and conditions (isolation/cover) are met. You MUST execute the MURDER action THIS TURN!\n");
                sb.append("- actionType: 'MURDER'\n");
                sb.append("- targetAgentId: '").append(victimId).append("'\n");
                if (holdsWeapon) {
                    Item w = heldWeapons.get(0);
                    sb.append("- weaponItemId: '").append(w.getId()).append("' (use your carried weapon: ").append(w.getName()).append(")\n");
                } else if (!roomWeapons.isEmpty()) {
                    Item w = roomWeapons.get(0);
                    if (w.isMovable()) {
                        sb.append("- weaponItemId: '").append(w.getId()).append("' (seize the weapon in the room: ").append(w.getName()).append(" or use improvised force)\n");
                    } else {
                        sb.append("- weaponItemId: '").append(w.getId()).append("' (use the stationary machinery/fixture in this room: ").append(w.getName()).append(" - push them into the mechanisms, activate lethal override, or crush them!)\n");
                    }
                } else {
                    sb.append("- weaponItemId: optional (use any held weapon, room item, poison, strangulation, sabotage, or blunt force)\n");
                }
                sb.append("- details: Describe your sudden, lethal attack vividly.\n");
                sb.append("STRICT RULE: You are STRICTLY FORBIDDEN from choosing MOVE, OBSERVE, or REFLECT. Do NOT leave the room. Strike now!\n");
                return;
            } else {
                sb.append("[PACING - STRICTLY NO MURDER]: You are in the room with ").append(victimName)
                  .append(". However, the conditions for murder are NOT met yet (e.g., too early in the story, or too many witnesses present).\n");
                sb.append("STRICT RULE: You are FORBIDDEN from using the MURDER action right now. Play your social role, converse naturally (TALK), scout weapons, or observe their habits (OBSERVE).\n");
                if (!holdsWeapon && !roomWeapons.isEmpty()) {
                    Item targetWeapon = roomWeapons.get(0);
                    if (targetWeapon.isMovable()) {
                        sb.append("[WEAPON ACQUISITION OPPORTUNITY]: A lethal weapon is in this room: ")
                          .append(targetWeapon.getName()).append(" ('").append(targetWeapon.getId()).append("')!\n")
                          .append("You are currently unarmed. Pick it up now while talking or passing by:\n")
                          .append("- actionType: 'MANIPULATE_ITEM'\n")
                          .append("- interaction: 'TAKE'\n")
                          .append("- targetItemId: '").append(targetWeapon.getId()).append("'\n");
                    } else {
                        sb.append("[LETHAL STATIONARY HAZARD IN ROOM]: A deadly stationary machine/fixture is in this room: ")
                          .append(targetWeapon.getName()).append(" ('").append(targetWeapon.getId()).append("')!\n")
                          .append("It cannot be moved, but note its lethal mechanisms—you can push ").append(victimName)
                          .append(" into it or sabotage it when the moment to strike arrives.\n");
                    }
                }
                return;
            }
        }

        String victimLocName = "another area";
        String victimLocId = null;
        if (victim != null && victim.getCurrentLocationId() != null) {
            victimLocId = victim.getCurrentLocationId();
            if (state.getLocations().containsKey(victimLocId)) {
                victimLocName = state.getLocations().get(victimLocId).getName();
            }
        }

        if (progress >= activeProfile.act3CrimeThreshold()) {
            sb.append("[URGENCY - DESPERATE PHASE]: Time is running out! You must corner your target ")
              .append(victimName).append(" who is currently in ").append(victimLocName)
              .append(victimLocId != null ? " ('" + victimLocId + "')" : "")
              .append(". You MUST MOVE towards their location immediately to strike!\n");
        } else if (act == StoryAct.ACT_1_EXPOSITION) {
            sb.append("[PACING - PLANNING PHASE]: It is early in the story. Do NOT commit the murder yet! Establish your innocent public facade, converse naturally, scout the location of weapons, and observe your target.\n");
            sb.append("STRICT RULE: You are FORBIDDEN from using the MURDER action right now.\n");
        } else {
            sb.append("[URGENCY - ESCALATING PHASE]: Move towards ").append(victimLocName)
              .append(" to corner ").append(victimName).append(" and prepare your strike!\n");
            sb.append("STRICT RULE: You are FORBIDDEN from using the MURDER action right now unless you are completely isolated with the victim.\n");
        }

        appendMurdererPreparationDirectives(sb, state, curLocName, victimName, victimId, holdsWeapon, roomWeapons, otherWeapons, act, progress, activeProfile);
    }

    private void appendMurdererPreparationDirectives(StringBuilder sb, SimulationRepository state, String curLocName,
                                                     String victimName, String victimId, boolean holdsWeapon, List<Item> roomWeapons,
                                                     List<Item> otherWeapons, StoryAct act, double progress, DramaturgyProfile activeProfile) {
        if (!holdsWeapon) {
            List<Item> roomPortableWeapons = roomWeapons.stream().filter(Item::isMovable).toList();
            List<Item> roomStationaryWeapons = roomWeapons.stream().filter(i -> !i.isMovable()).toList();

            if (!roomPortableWeapons.isEmpty()) {
                Item targetWeapon = roomPortableWeapons.get(0);
                sb.append("[WEAPON ACQUISITION OPPORTUNITY]: A lethal weapon is right here in ").append(curLocName).append(": ")
                  .append(targetWeapon.getName()).append(" ('").append(targetWeapon.getId()).append("')!\n")
                  .append("You are currently unarmed. Pick up the weapon now to arm yourself for the crime:\n")
                  .append("- actionType: 'MANIPULATE_ITEM'\n")
                  .append("- interaction: 'TAKE'\n")
                  .append("- targetItemId: '").append(targetWeapon.getId()).append("'\n");
            } else if (!roomStationaryWeapons.isEmpty()) {
                Item hazard = roomStationaryWeapons.get(0);
                sb.append("[LETHAL STATIONARY HAZARD IN ROOM]: A deadly stationary machine/fixture is in ").append(curLocName).append(": ")
                  .append(hazard.getName()).append(" ('").append(hazard.getId()).append("')!\n")
                  .append("It cannot be moved, but it is an ideal execution hazard. Lure ").append(victimName)
                  .append(" here to push them into it, crush them, or sabotage the controls when you strike!\n");
            } else if (!otherWeapons.isEmpty() && act == StoryAct.ACT_1_EXPOSITION) {
                Item targetWeapon = otherWeapons.get(0);
                String wLoc = state.getLocations().containsKey(targetWeapon.getCurrentLocationId())
                        ? state.getLocations().get(targetWeapon.getCurrentLocationId()).getName()
                        : targetWeapon.getCurrentLocationId();
                if (targetWeapon.isMovable()) {
                    sb.append("[WEAPON SCOUTING DIRECTIVE]: You are currently unarmed. A lethal weapon (")
                      .append(targetWeapon.getName()).append(") is located in ").append(wLoc)
                      .append(" ('").append(targetWeapon.getCurrentLocationId()).append("'). Scout or move there to equip yourself before striking!\n");
                } else {
                    sb.append("[LETHAL AMBUSH LOCATION DIRECTIVE]: A deadly stationary machine (")
                      .append(targetWeapon.getName()).append(") is located in ").append(wLoc)
                      .append(" ('").append(targetWeapon.getCurrentLocationId()).append("'). You can lure ").append(victimName)
                      .append(" there to stage a lethal industrial accident or sabotage!\n");
                }
            }
        }

        if (progress < activeProfile.act3CrimeThreshold()) {
            sb.append("[TACTICAL LURE STRATEGY - ALTER_ENVIRONMENT]: If you want to lure ").append(victimName)
              .append(" into an ambush or isolated spot, you can leave a forged note or bait in ").append(curLocName).append(":\n")
              .append("- actionType: 'ALTER_ENVIRONMENT'\n")
              .append("- traceDescription: 'A discreet note addressed to ").append(victimName)
              .append(": Meet me urgently in ").append(curLocName).append(" regarding a private matter.'\n")
              .append("When ").append(victimName).append(" discovers traces addressed to them, they will be drawn to investigate!\n");

            sb.append("[COVERT POISON STRATEGY - REMOTE MURDER]: You can kill ").append(victimName)
              .append(" without being in the same room! You can secretly POISON or SABOTAGE food, drink, medicine, or equipment in ").append(curLocName).append(":\n")
              .append("- actionType: 'MANIPULATE_ITEM'\n")
              .append("- interaction: 'POISON'\n")
              .append("- targetItemId: '<id of an item in ").append(curLocName).append(">'\n")
              .append("- targetAgentId: '").append(victimId).append("'\n")
              .append("- details: 'Secretly laced the item with lethal poison intended for ").append(victimName).append("...'\n")
              .append("When ").append(victimName).append(" enters or interacts with it, they will die from the poison—giving you an airtight alibi elsewhere!\n");
        }
    }

    private void appendVictimGuidance(StringBuilder sb, Agent agent, SimulationRepository state, StoryAct act, ActPhase phase, double actProgress, double roomSuspense, DramaturgyProfile activeProfile) {
        if (bodyDiscovered || !agent.isAlive()) return;
        
        double progress = (double) state.getCurrentStep() / Math.max(1, state.getTargetSteps());
        if (act == StoryAct.ACT_3_THE_CRIME) {
            sb.append("Role Directive (The Target - CRITICAL DANGER): An acute sense of dread grips you. You have separated yourself from safety or are pursuing a hazardous secret errand.\n");
            sb.append("DRAMATURGIC VULNERABILITY: If someone enters or confronts you, react with vulnerability, shock, or panic. You are in mortal jeopardy!\n");
        } else if (progress >= activeProfile.act2RisingSuspenseThreshold() && act != StoryAct.ACT_1_EXPOSITION) {
            sb.append("Role Directive (The Target): You feel an inexplicable, overwhelming urge to pursue your secret goal or find a moment of total isolation. The crowd is suffocating, and your secrets feel exposed.\n");
            sb.append("DRAMATURGIC PUSH: You MUST leave the main gathering! MOVE to an isolated, empty, or dangerous room to investigate a hunch, retrieve a hidden item, or simply get away from prying eyes.\n");
        } else {
            sb.append("Role Directive (The Target): Engage naturally with the world and others, but you feel a growing sense of unease. Pursue your personal goals discreetly.\n");
        }
    }

    private void appendDetectiveGuidance(StringBuilder sb, Agent agent, SimulationRepository state, StoryAct act, ActPhase phase, double actProgress, double roomSuspense, DramaturgyProfile activeProfile, List<Agent> agentsInRoom) {
        Agent dead = getFirstDeadAgent(state);
        if (bodyDiscovered && dead != null && agent != null && state != null) {
            String crimeLocId = dead.getCurrentLocationId();
            if (Objects.equals(agent.getCurrentLocationId(), crimeLocId)) {
                agent.setHasInspectedCrimeScene(true);
            }
            if (!agent.hasInspectedCrimeScene() && crimeLocId != null && !Objects.equals(agent.getCurrentLocationId(), crimeLocId)) {
                String crimeLocName = state.resolveLocationName(crimeLocId, state.getCurrentStep());
                sb.append("[CRITICAL CRIME SCENE PRIORITY]: The victim (").append(dead.getName())
                  .append(") lies dead in ").append(crimeLocName).append(" ('").append(crimeLocId).append("')!\n")
                  .append("You are currently in ").append(state.resolveLocationName(agent.getCurrentLocationId(), state.getCurrentStep()))
                  .append(". As the lead investigator, your immediate duty is to reach the crime scene! You MUST choose:\n")
                  .append("- actionType: 'MOVE'\n")
                  .append("- targetLocationId: '").append(crimeLocId).append("'\n")
                  .append("- details: 'Rushes directly to ").append(crimeLocName).append(" to examine the crime scene and inspect the body.'\n");
            } else if (agent.hasInspectedCrimeScene()) {
                sb.append("Role Directive (Investigator - PATROL & SUSPECT INQUIRY): You have already secured and inspected the crime scene. You must now patrol the other rooms, question suspects (TALK) about alibis and suspicious movements, and search for additional clues across the station!\n");
            }
        }
        if (agent != null) {
            List<Agent> presentOthers = agentsInRoom != null ? agentsInRoom.stream()
                    .filter(a -> a.isAlive() && !a.getId().equals(agent.getId()))
                    .toList() : List.of();
            if (presentOthers.isEmpty() || agent.getConsecutiveSearchCount() >= 1 || agent.getTurnsInCurrentLocation() >= 2) {
                sb.append("[INVESTIGATOR MOBILITY MANDATE]: You have already inspected this location or are alone here. Lingering accomplishes nothing! You MUST MOVE to another location to interview witnesses, cross-examine suspects, and uncover clues across the station!\n");
            }
        }
        if (act == StoryAct.ACT_6_CLIMAX) {
            String mainLoc = getMainLocationId(state);
            String mainLocName = state != null ? state.resolveLocationName(mainLoc, state.getCurrentStep()) : "the main hall";
            boolean inMainLoc = agent != null && Objects.equals(agent.getCurrentLocationId(), mainLoc);

            if (!inMainLoc) {
                sb.append("[CLIMAX SUMMONS - ASSEMBLE IN ").append(mainLocName.toUpperCase()).append("]:\n")
                  .append("The climax has arrived! As lead investigator, you must assemble all surviving suspects in ")
                  .append(mainLocName).append(" ('").append(mainLoc).append("') for the final confrontation. ")
                  .append("You MUST MOVE to ").append(mainLocName).append(" now!\n")
                  .append("- actionType: 'MOVE'\n")
                  .append("- targetLocationId: '").append(mainLoc).append("'\n");
            } else if (phase == ActPhase.LATE) {
                sb.append("Role Directive (Investigator - CLIMAX FINAL ACCUSATION): The suspects are assembled in ").append(mainLocName)
                  .append("! Lay out the conclusive evidence, dismantle alibis, and execute the ACCUSE action against the murderer immediately!\n");
            } else {
                sb.append("Role Directive (Investigator - CLIMAX): You are in ").append(mainLocName)
                  .append(". Address the assembled room, outline the clues, assemble the suspects, and execute the ACCUSE action against the murderer!\n");
            }
        } else if (act == StoryAct.ACT_5_ESCALATION) {
            if (phase == ActPhase.LATE) {
                sb.append("Role Directive (Investigator - ESCALATION & CLOSING THE NET): Pressure is mounting! Contradictions in alibis are emerging. Interrogate key suspects vigorously (TALK), confront them with newly found clues, and search secure areas for hidden murder weapons. The net is closing!\n");
            } else {
                sb.append("Role Directive (Investigator - ESCALATION & CROSS-EXAMINATION): Pressure is mounting! Contradictions in alibis are emerging. Interrogate key suspects vigorously (TALK), confront them with newly found clues, and search secure areas for hidden murder weapons.\n");
            }
        } else if (bodyDiscovered || act == StoryAct.ACT_4_DISCOVERY) {
            sb.append("Role Directive (Investigator - ACTIVE INQUIRY): A murder has been discovered! Take charge of the scene. Question suspects (TALK) about their movements and alibis, inspect the crime scene (SEARCH), and analyze motives.\n");
        } else if (roomSuspense >= activeProfile.criticalRoomSuspenseThreshold()) {
            sb.append("Role Directive (Investigator - ALERT OBSERVER): Dramatic tension is thick in this room. Watch for erratic behavior, hidden motives, or sudden movements among the occupants.\n");
        } else {
            sb.append("Role Directive (Investigator - SOCIAL OBSERVER): No crime has been discovered yet. You are an ordinary guest or resident. Converse naturally, observe dynamics, and enjoy your time. You have nothing to investigate yet.\n");
        }
    }

    private void appendSuspectGuidance(StringBuilder sb, Agent agent, SimulationRepository state, StoryAct act, ActPhase phase, double actProgress, double roomSuspense, DramaturgyProfile activeProfile) {
        if (act == StoryAct.ACT_6_CLIMAX) {
            String mainLoc = getMainLocationId(state);
            String mainLocName = state != null ? state.resolveLocationName(mainLoc, state.getCurrentStep()) : "the main hall";
            boolean inMainLoc = agent != null && Objects.equals(agent.getCurrentLocationId(), mainLoc);

            if (!inMainLoc) {
                sb.append("[GRAND CONFRONTATION SUMMONS - PROCEED TO ").append(mainLocName.toUpperCase()).append("]:\n")
                  .append("The lead investigator has summoned all surviving persons to ").append(mainLocName)
                  .append(" ('").append(mainLoc).append("') for the final revelation! You MUST comply and MOVE there immediately:\n")
                  .append("- actionType: 'MOVE'\n")
                  .append("- targetLocationId: '").append(mainLoc).append("'\n")
                  .append("- details: 'Hurries to ").append(mainLocName).append(" in response to the investigator's urgent summons.'\n");
            } else {
                sb.append("Role Directive (Suspect/Witness - CLIMAX): The investigator is assembling everyone for the grand confrontation. Comply with the summons, watch the accusations unfold, and defend yourself if cornered!\n");
            }
        } else if (act == StoryAct.ACT_5_ESCALATION) {
            sb.append("Role Directive (Suspect/Witness - HIGH STAKES & PARANOIA): Whispers and mutual suspicions are tearing the group apart. Cling to your alibi, observe who is acting nervous, and protect yourself. Trust no one.\n");
        } else if (bodyDiscovered || act == StoryAct.ACT_4_DISCOVERY) {
            sb.append("Role Directive (Suspect/Witness): A murder has taken place! You are a potential suspect. Guard your personal secrets, offer your alibi, observe who acts suspiciously, and protect yourself.\n");
        } else if (roomSuspense >= activeProfile.criticalRoomSuspenseThreshold()) {
            sb.append("Role Directive (High Suspense & Paranoia): You feel intensely watched. The atmosphere is suffocating and paranoia sets in. You suspect someone here is hiding a dark secret or plotting against you. Confront someone (TALK), inspect the room cautiously (SEARCH), or watch other occupants closely! Do not trust anyone.\n");
            if (agent.getSecretGoal() != null && !agent.getSecretGoal().isBlank()) {
                sb.append("CRITICAL SUBPLOT: This tension makes you desperate to resolve your own secret: \"").append(agent.getSecretGoal()).append("\". Take risky action to achieve it now while others are distracted!\n");
            }
        } else if (act == StoryAct.ACT_1_EXPOSITION) {
            sb.append("Role Directive (Guest/Resident): This is the opening act. Introduce yourself to others through dialogue and natural actions. Establish your presence, relationships, and distinct personality.\n");
        } else {
            sb.append("Role Directive (Guest/Resident): Subtle tensions are growing. Pursue your personal secret goal discreetly without making unnecessary enemies.\n");
            if (agent.getSecretGoal() != null && !agent.getSecretGoal().isBlank()) {
                sb.append("Remember your secret goal: \"").append(agent.getSecretGoal()).append("\". Find an opportunity to act on it.\n");
            }
        }
    }

    public Agent findTargetVictim(Agent murderer, SimulationRepository state) {
        if (murderer.getTargetVictimId() != null) {
            Agent v = state.getAgents().get(murderer.getTargetVictimId());
            if (v != null) {
                return v;
            }
        }
        String goal = murderer.getSecretGoal() != null ? murderer.getSecretGoal().toLowerCase(Locale.ROOT) : "";
        for (Agent a : state.getAgents().values()) {
            if (!a.getId().equals(murderer.getId()) && !a.isDetective()) {
                if (goal.contains(a.getName().toLowerCase(Locale.ROOT))) {
                    murderer.setTargetVictimId(a.getId());
                    return a;
                }
            }
        }
        for (Agent a : state.getAgents().values()) {
            if (a.isAlive() && !a.getId().equals(murderer.getId()) && !a.isDetective()) {
                murderer.setTargetVictimId(a.getId());
                return a;
            }
        }
        return null;
    }

    public boolean isStrikeWindow(SimulationRepository state, Agent murderer, List<Agent> agentsInRoom) {
        Agent victim = findTargetVictim(murderer, state);
        if (victim == null || !victim.isAlive()) {
            return false;
        }
        boolean victimInRoom = agentsInRoom.stream().anyMatch(a -> a.getId().equals(victim.getId()));
        if (!victimInRoom) {
            return false;
        }

        // The killer will NEVER commit a murder in the presence of a detective
        boolean detectiveInRoom = agentsInRoom.stream().anyMatch(Agent::isDetective);
        if (detectiveInRoom) {
            return false;
        }
        
        DramaturgyProfile activeProfile = resolveProfile(state);
        StoryAct act = determineCurrentAct(state);
        double progress = (double) state.getCurrentStep() / Math.max(1, state.getTargetSteps());
        
        // Prevent murder from happening too early in the game (allow world building in Act 1)
        if (act == StoryAct.ACT_1_EXPOSITION && progress < activeProfile.act2RisingSuspenseThreshold()) {
            return false;
        }

        int agentCount = agentsInRoom.size();
        boolean isIsolated = agentCount <= activeProfile.isolationAgentCount();
        boolean hasEnvironmentalCover = state.getActiveGlobalEvents(state.getCurrentStep()).stream()
                .anyMatch(ge -> "ENVIRONMENTAL".equals(ge.getImpactType()) || "ISOLATION".equals(ge.getImpactType()));
        
        ActPhase phase = activeProfile.determineActPhase(act, progress);
        boolean desperate = progress >= activeProfile.act3CrimeThreshold();
        
        // Under environmental cover (lights out, alarm confusion), strike is possible with at most 1 bystander (<= 3 agents)
        boolean coverOpportunity = hasEnvironmentalCover && agentCount <= 3;

        // Act 3 mid-phase opportunity with at most 1 bystander
        boolean midPhaseOpportunity = act == StoryAct.ACT_3_THE_CRIME && phase == ActPhase.MID && agentCount <= 3;

        // Desperate phase (time running out) allows striking with at most 1 bystander
        boolean desperateOpportunity = desperate && agentCount <= 3;

        return isIsolated || coverOpportunity || desperateOpportunity || midPhaseOpportunity;
    }

    public double calculateLiveRoomTension(SimulationRepository state, List<Agent> agentsInRoom, int currentStep) {
        return editorialScorer.calculateLiveRoomTension(state, agentsInRoom, currentStep);
    }

    public EditorialScorer getEditorialScorer() {
        return editorialScorer;
    }

    private void appendActiveGlobalEventGuidance(StringBuilder sb, SimulationRepository state) {
        List<GlobalEvent> activeEvents = state.getActiveGlobalEvents(state.getCurrentStep());
        if (activeEvents.isEmpty()) {
            return;
        }

        sb.append("\n=== ACTIVE WORLD EVENT & DRAMATIC RESPONSE ===\n");
        for (GlobalEvent ge : activeEvents) {
            sb.append("- Event: \"").append(ge.getName()).append("\" (").append(ge.getImpactType()).append(")\n");
            sb.append("  Context: ").append(ge.getDescription()).append("\n");

            if ("GATHERING".equalsIgnoreCase(ge.getImpactType())) {
                String loc = ge.getTargetLocationId() != null && state.getLocations().containsKey(ge.getTargetLocationId())
                        ? state.getLocations().get(ge.getTargetLocationId()).getName()
                        : "the central gathering area";
                sb.append("  Action Guidance: A public gathering is underway in ").append(loc)
                        .append(". You are strongly expected to MOVE there or interact with people present, unless you have urgent covert reasons to stay hidden.\n");
            } else if ("ENVIRONMENTAL".equalsIgnoreCase(ge.getImpactType()) || "ALARM".equalsIgnoreCase(ge.getImpactType())) {
                sb.append("  Action Guidance: The environment is unsettled (dimmed lighting, loud alarms, or darkness). React to the disturbance or utilize the confusion as cover for discreet actions.\n");
            } else if ("ISOLATION".equalsIgnoreCase(ge.getImpactType())) {
                sb.append("  Action Guidance: Certain passages or doors are sealed. Be cautious of being trapped alone with dangerous company.\n");
            }
        }
    }

    private Agent getFirstDeadAgent(SimulationRepository state) {
        return state.getAgents().values().stream()
                .filter(a -> !a.isAlive())
                .findFirst()
                .orElse(null);
    }

    public String getMainLocationId(SimulationRepository state) {
        if (state == null || state.getLocations() == null || state.getLocations().isEmpty()) {
            return "room_0";
        }
        for (Location loc : state.getLocations().values()) {
            if (loc.getName() != null) {
                String n = loc.getName().toLowerCase();
                if (n.contains("lounge") || n.contains("parlor") || n.contains("hall") || n.contains("dome") || n.contains("salong")) {
                    return loc.getId();
                }
            }
        }
        return state.getLocations().keySet().iterator().next();
    }

    public boolean isBodyDiscovered() {
        return bodyDiscovered;
    }

    public void setBodyDiscovered(boolean bodyDiscovered) {
        this.bodyDiscovered = bodyDiscovered;
    }

    public String getDiscoveredBodyAgentId() {
        return discoveredBodyAgentId;
    }

    public record MurdererPressureStatus(
        Agent murderer,
        Agent victim,
        boolean victimPresent,
        double suspenseScore,
        String urgencyPhase,
        boolean strikeWindowActive,
        String pressureSummary
    ) {}

    public MurdererPressureStatus evaluateMurdererPressure(SimulationRepository state, Agent murderer, List<Agent> agentsInRoom, int currentStep) {
        if (murderer == null) {
            return null;
        }
        Agent victim = findTargetVictim(murderer, state);
        boolean victimPresent = victim != null && agentsInRoom != null && agentsInRoom.stream().anyMatch(a -> a.getId().equals(victim.getId()));
        double roomSuspense = calculateLiveRoomTension(state, agentsInRoom, currentStep);
        Agent dead = getFirstDeadAgent(state);
        boolean isStrike = isStrikeWindow(state, murderer, agentsInRoom != null ? agentsInRoom : Collections.emptyList());
        DramaturgyProfile activeProfile = resolveProfile(state);
        double progress = (double) state.getCurrentStep() / Math.max(1, state.getTargetSteps());
        StoryAct act = determineCurrentAct(state);

        String urgencyPhase;
        String pressureSummary;

        if (dead != null) {
            urgencyPhase = "POST_CRIME";
            pressureSummary = "Post-crime concealment phase. Blend in with guests, discard weapons, establish alibi, and do not raise suspicion.";
        } else if (victimPresent && isStrike) {
            urgencyPhase = progress >= activeProfile.act3CrimeThreshold() ? "DESPERATE_STRIKE_WINDOW" : "STRIKE_WINDOW";
            pressureSummary = String.format(Locale.ROOT,
                "CRITICAL STRIKE WINDOW! Target %s is present. Conditions are optimal (isolated or desperate). Dramaturgist enforces lethal attack now!",
                victim.getName());
        } else if (progress >= activeProfile.act3CrimeThreshold()) {
            urgencyPhase = "DESPERATE";
            pressureSummary = String.format(Locale.ROOT,
                "Desperate pacing pressure (%.0f%% elapsed). Corner target %s immediately to strike before mystery time expires.",
                progress * 100, victim != null ? victim.getName() : "victim");
        } else if (act == StoryAct.ACT_1_EXPOSITION && !victimPresent) {
            urgencyPhase = "PLANNING";
            pressureSummary = String.format(Locale.ROOT,
                "World introduction pacing. Establish social cover, study the layout, do not strike yet.");
        } else {
            urgencyPhase = "ESCALATING";
            pressureSummary = String.format(Locale.ROOT,
                "Escalating tension. Track down %s, prepare weapons or covert poison, and isolate them.",
                victim != null ? victim.getName() : "target");
        }

        return new MurdererPressureStatus(
            murderer,
            victim,
            victimPresent,
            roomSuspense,
            urgencyPhase,
            isStrike,
            pressureSummary
        );
    }

    public List<TimeLocationEvent> ensureOrSynthesizeTimelineEvents(SimulationRepository state) {
        if (state == null) {
            return Collections.emptyList();
        }
        List<TimeLocationEvent> existing = state.getAllRecordedEvents();
        if (!existing.isEmpty()) {
            return existing;
        }
        List<TimeLocationEvent> synthesized = new ArrayList<>();
        for (int s = 1; s <= state.getCurrentStep(); s++) {
            SimulationStateSnapshot snap = state.getSnapshots().get(s);
            SimulationStateSnapshot snapBefore = state.getSnapshots().get(s - 1);
            if (snap == null) continue;

            Map<String, List<String>> agentsByLoc = new HashMap<>();
            snap.agents().values().forEach(a -> {
                if (a.isAlive() && a.hasArrived() && a.currentLocationId() != null) {
                    agentsByLoc.computeIfAbsent(a.currentLocationId(), k -> new ArrayList<>()).add(a.name());
                }
            });

            int seq = 1;
            List<TimeLocationEvent> stepEvents = new ArrayList<>();
            for (Map.Entry<String, List<String>> entry : agentsByLoc.entrySet()) {
                String locId = entry.getKey();
                String locName = (snap.places() != null && snap.places().containsKey(locId)) ? snap.places().get(locId).name() : locId;
                TimeLocationEvent ev = TimeLocationEvent.builder()
                        .timeStep(s)
                        .sequenceNumber(seq++)
                        .locationId(locId)
                        .locationName(locName)
                        .presentAgentNames(entry.getValue())
                        .snapshotBefore(snapBefore)
                        .snapshotAfter(snap)
                        .suspenseScore(2.0)
                        .build();
                stepEvents.add(ev);
            }
            state.recordTimelineEvents(s, stepEvents);
            synthesized.addAll(stepEvents);
        }
        return synthesized;
    }

    public int getDiscoveryStep() {
        return discoveryStep;
    }

    public void setDiscoveryStep(int discoveryStep) {
        this.discoveryStep = discoveryStep;
    }

    public int getDeathStep() {
        return deathStep;
    }

    public void setDeathStep(int deathStep) {
        this.deathStep = deathStep;
    }

    public boolean isEmergencyOverrideActive(SimulationRepository state) {
        if (state == null) return false;
        int current = state.getCurrentStep();
        if (bodyDiscovered && discoveryStep >= 0 && (current - discoveryStep) >= 0 && (current - discoveryStep) <= 4) {
            return true;
        }
        if (state.getGlobalEvents() != null) {
            for (GlobalEvent ge : state.getGlobalEvents().values()) {
                if (ge.isActiveAt(current) && "ALARM".equalsIgnoreCase(ge.getImpactType())) {
                    return true;
                }
            }
        }
        return false;
    }

    public String getActiveCrisisDescription(SimulationRepository state) {
        if (state == null) return null;
        int current = state.getCurrentStep();
        if (state.getGlobalEvents() != null) {
            for (GlobalEvent ge : state.getGlobalEvents().values()) {
                if (ge.isActiveAt(current) && "ALARM".equalsIgnoreCase(ge.getImpactType())) {
                    return ge.getName() + " - " + ge.getDescription();
                }
            }
        }
        if (bodyDiscovered && discoveredBodyAgentId != null && state.getAgents().containsKey(discoveredBodyAgentId)) {
            Agent victim = state.getAgents().get(discoveredBodyAgentId);
            return "Alarm: The lifeless body of " + victim.getName() + " has just been discovered! The entire area is in emergency lockdown.";
        }
        return "Alarm: A major murder crisis has been declared across the entire area.";
    }

}
