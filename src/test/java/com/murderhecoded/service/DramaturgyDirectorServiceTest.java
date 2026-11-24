package com.murderhecoded.service;

import com.murderhecoded.domain.*;
import com.murderhecoded.domain.Location;
import com.murderhecoded.repository.SimulationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("PMD")
class DramaturgyDirectorServiceTest {

    private DramaturgyDirectorService director;
    private SimulationRepository repository;
    private Agent murderer;
    private Agent victim;
    private Agent detective;
    private Location grandHall;

    @BeforeEach
    void setUp() {
        director = new DramaturgyDirectorService();
        repository = new SimulationRepository();

        grandHall = new Location();
        grandHall.setId("room_0");
        grandHall.setName("Grand Hall");

        Location lab = new Location();
        lab.setId("room_1");
        lab.setName("Research Lab");

        detective = new Agent();
        detective.setId("agent_0");
        detective.setName("Inspector Vance");
        detective.setRole("Detective");
        detective.setAlive(true);
        detective.setHasArrived(true);
        detective.setCurrentLocationId("room_0");

        murderer = new Agent();
        murderer.setId("agent_1");
        murderer.setName("Dr. Malice");
        murderer.setRole("Chemist");
        murderer.setAlive(true);
        murderer.setHasMurderIntent(true);
        murderer.setSecretGoal("Murder Lady Agatha to conceal the theft");
        murderer.setCurrentLocationId("room_1");
        murderer.setHasArrived(true);

        victim = new Agent();
        victim.setId("agent_2");
        victim.setName("Lady Agatha");
        victim.setRole("Aristocrat");
        victim.setAlive(true);
        victim.setCurrentLocationId("room_1");
        victim.setHasArrived(true);

        repository.initialize(List.of(detective, murderer, victim), List.of(grandHall, lab), List.of(), "Murder at the Manor", "Classic country estate");
        repository.setTargetSteps(100);
    }

    @Test
    void testActProgression() {
        repository.setCurrentStep(5);
        assertEquals(DramaturgyDirectorService.StoryAct.ACT_1_EXPOSITION, director.determineCurrentAct(repository));

        repository.setCurrentStep(20);
        assertEquals(DramaturgyDirectorService.StoryAct.ACT_2_RISING_SUSPENSE, director.determineCurrentAct(repository));

        repository.setCurrentStep(40);
        assertEquals(DramaturgyDirectorService.StoryAct.ACT_3_THE_CRIME, director.determineCurrentAct(repository));

        // Kill victim and trigger discovery
        victim.setAlive(false);
        director.evaluateAndDirectStep(repository, "en", null);
        assertEquals(DramaturgyDirectorService.StoryAct.ACT_4_DISCOVERY, director.determineCurrentAct(repository));

        repository.setCurrentStep(70);
        assertEquals(DramaturgyDirectorService.StoryAct.ACT_5_ESCALATION, director.determineCurrentAct(repository));

        repository.setCurrentStep(90);
        assertEquals(DramaturgyDirectorService.StoryAct.ACT_6_CLIMAX, director.determineCurrentAct(repository));
    }

    @Test
    void testCalculateTargetTensionCurve() {
        int total = 100;
        // Total <= 0 fallback
        assertEquals(5.0, DramaturgyDirectorService.calculateTargetTension(10, 0));

        // Act 1 Exposition / Start: p=0
        double tStart = DramaturgyDirectorService.calculateTargetTension(0, total);
        assertEquals(2.5, tStart, 0.1);

        // Peak 1: Strike window around p=0.40 (step 40)
        double tPeak1 = DramaturgyDirectorService.calculateTargetTension(40, total);
        assertEquals(8.2, tPeak1, 0.1);

        // Lull: Post-crime around p=0.52 (step 52)
        double tLull = DramaturgyDirectorService.calculateTargetTension(52, total);
        assertEquals(4.8, tLull, 0.1);

        // Peak 2: Climax around p=0.92 (step 92)
        double tPeak2 = DramaturgyDirectorService.calculateTargetTension(92, total);
        assertEquals(9.8, tPeak2, 0.1);

        // Denouement / resolution at p=1.00 (step 100)
        double tEnd = DramaturgyDirectorService.calculateTargetTension(100, total);
        assertEquals(6.0, tEnd, 0.1);

        // Verify bounds across all steps
        for (int s = 1; s <= total; s++) {
            double score = DramaturgyDirectorService.calculateTargetTension(s, total);
            assertTrue(score >= 0.0 && score <= 10.0, "Score at step " + s + " out of bounds: " + score);
        }
    }

    @Test
    void testDetermineTensionTrend() {
        int total = 100;
        assertEquals(DramaturgyDirectorService.TensionTrend.RISING, DramaturgyDirectorService.determineTensionTrend(5, total));
        assertEquals(DramaturgyDirectorService.TensionTrend.PEAK, DramaturgyDirectorService.determineTensionTrend(40, total));
        assertEquals(DramaturgyDirectorService.TensionTrend.LULL, DramaturgyDirectorService.determineTensionTrend(50, total));
        assertEquals(DramaturgyDirectorService.TensionTrend.RISING, DramaturgyDirectorService.determineTensionTrend(70, total));
        assertEquals(DramaturgyDirectorService.TensionTrend.CLIMAX, DramaturgyDirectorService.determineTensionTrend(90, total));
    }

    @Test
    void testDramaturgicDirectivesIncludesGlobalDramaScoreAndTrend() {
        repository.setCurrentStep(40);
        String directives = director.buildDramaturgicDirectives(murderer, repository);
        assertTrue(directives.contains("Current Narrative Stage: [Act 3: The Crime & Strike Opportunity]"));
        assertTrue(directives.contains("Global Drama Score: [8.2/10.0]"));
        assertTrue(directives.contains("Pacing Trend: PEAK"));
        assertTrue(directives.contains("Current Room Suspense Score:"));
    }

    @Test
    void testMurdererUrgencyDirectives() {
        victim.setCurrentLocationId("room_0"); // victim in different room to test tracking/pacing phases
        // Act 1
        repository.setCurrentStep(5);
        String promptAct1 = director.buildDramaturgicDirectives(murderer, repository);
        assertTrue(promptAct1.contains("[PACING - PLANNING PHASE]"));

        // Act 2 early
        repository.setCurrentStep(18);
        String promptAct2 = director.buildDramaturgicDirectives(murderer, repository);
        assertTrue(promptAct2.contains("[URGENCY - ESCALATING PHASE]"));

        // Act 2 late (>30%)
        repository.setCurrentStep(40);
        String promptAct2Late = director.buildDramaturgicDirectives(murderer, repository);
        assertTrue(promptAct2Late.contains("[URGENCY - DESPERATE PHASE]"));

        // Post crime
        victim.setAlive(false);
        String promptPostCrime = director.buildDramaturgicDirectives(murderer, repository);
        assertTrue(promptPostCrime.contains("[POST-CRIME PHASE]"));
    }

    @Test
    void testMurdererStrikeDirectiveWhenVictimInRoom() {
        repository.setCurrentStep(20);
        murderer.setCurrentLocationId("room_1");
        victim.setCurrentLocationId("room_1");
        String prompt = director.buildDramaturgicDirectives(murderer, repository);
        assertTrue(prompt.contains("[CRITICAL STRIKE DIRECTIVE"));
        assertTrue(prompt.contains("Lady Agatha"));
        assertTrue(prompt.contains("agent_2"));
        assertTrue(prompt.contains("STRICT RULE: You are STRICTLY FORBIDDEN from choosing MOVE"));
        assertTrue(prompt.contains("Current Room Suspense Score:"));
        assertTrue(director.isStrikeWindow(repository, murderer, List.of(murderer, victim)));
    }

    @Test
    void testDetectiveStateDirectives() {
        // Act 1
        repository.setCurrentStep(5);
        String promptAct1 = director.buildDramaturgicDirectives(detective, repository);
        assertTrue(promptAct1.contains("SOCIAL OBSERVER"));
        assertTrue(promptAct1.contains("No crime has been discovered yet"));

        // After body discovery
        victim.setAlive(false);
        director.evaluateAndDirectStep(repository, "en", null);
        String promptAct3 = director.buildDramaturgicDirectives(detective, repository);
        assertTrue(promptAct3.contains("ACTIVE INQUIRY"));
        assertTrue(promptAct3.contains("Take charge of the scene"));

        // Climax
        repository.setCurrentStep(85);
        String promptAct4 = director.buildDramaturgicDirectives(detective, repository);
        assertTrue(promptAct4.contains("CLIMAX"));
        assertTrue(promptAct4.contains("ACCUSE action"));
    }

    @Test
    void testDynamicEventTriggering() {
        // Step 1: Director dynamically triggers Intro Gathering
        repository.setCurrentStep(1);
        List<String> logs = new ArrayList<>();
        director.evaluateAndDirectStep(repository, "en", logs::add);

        assertEquals(1, repository.getGlobalEvents().size());
        GlobalEvent introEvent = repository.getGlobalEvents().values().iterator().next();
        assertEquals("GATHERING", introEvent.getImpactType());
        assertTrue(introEvent.isActiveAt(1));
        assertTrue(introEvent.getDescription().contains("Grand Hall"));

        // Step 25: Director dynamically triggers Opportunity Cover event
        repository.setCurrentStep(25);
        director.evaluateAndDirectStep(repository, "en", logs::add);

        assertEquals(2, repository.getGlobalEvents().size());
        boolean hasEnvironmental = repository.getGlobalEvents().values().stream()
                .anyMatch(e -> "ENVIRONMENTAL".equals(e.getImpactType()));
        assertTrue(hasEnvironmental);

        // Step 90: Act 6 Climax event uses resolved location name
        repository.setCurrentStep(90);
        director.evaluateAndDirectStep(repository, "en", logs::add);
        GlobalEvent climaxEvent = repository.getGlobalEvents().values().stream()
                .filter(e -> e.getId().startsWith("event_climax_"))
                .findFirst()
                .orElse(null);
        assertNotNull(climaxEvent);
        assertTrue(climaxEvent.getDescription().contains("Grand Hall"));
    }

    @Test
    void testWeaponAcquisitionGuidanceWhenUnarmedAndWeaponInRoom() {
        Item weapon = new Item();
        weapon.setId("item_w1");
        weapon.setName("Heavy Pipe");
        weapon.setType(ItemType.WEAPON);
        weapon.setCurrentLocationId("room_1");
        repository.getItems().put("item_w1", weapon);

        victim.setCurrentLocationId("room_0");
        repository.setCurrentStep(5);

        String prompt = director.buildDramaturgicDirectives(murderer, repository);
        assertTrue(prompt.contains("[WEAPON ACQUISITION OPPORTUNITY]"));
        assertTrue(prompt.contains("item_w1"));
        assertTrue(prompt.contains("MANIPULATE_ITEM"));
    }

    @Test
    void testStrikeDirectiveWithCarriedWeapon() {
        Item weapon = new Item();
        weapon.setId("item_w1");
        weapon.setName("Silver Dagger");
        weapon.setType(ItemType.WEAPON);
        weapon.setCurrentLocationId("agent_1"); // carried by murderer
        repository.getItems().put("item_w1", weapon);

        murderer.setCurrentLocationId("room_1");
        victim.setCurrentLocationId("room_1");
        repository.setCurrentStep(20);

        String prompt = director.buildDramaturgicDirectives(murderer, repository);
        assertTrue(prompt.contains("[CRITICAL STRIKE DIRECTIVE"));
        assertTrue(prompt.contains("weaponItemId: 'item_w1'"));
        assertTrue(prompt.contains("use your carried weapon: Silver Dagger"));
    }

    @Test
    void testAntiStallingDirective() {
        murderer.setConsecutiveReflectCount(0);
        String p1 = director.buildDramaturgicDirectives(murderer, repository);
        assertFalse(p1.contains("ANTI-STALLING"));

        murderer.setConsecutiveReflectCount(2);
        String p2 = director.buildDramaturgicDirectives(murderer, repository);
        assertTrue(p2.contains("[CRITICAL PACING DIRECTIVE - ANTI-STALLING]"));
        assertTrue(p2.contains("You MUST choose an active physical action"));
    }

    @Test
    void testTacticalLureGuidanceWhenVictimElsewhere() {
        victim.setCurrentLocationId("room_0");
        repository.setCurrentStep(10);

        String prompt = director.buildDramaturgicDirectives(murderer, repository);
        assertTrue(prompt.contains("[TACTICAL LURE STRATEGY - ALTER_ENVIRONMENT]"));
        assertTrue(prompt.contains("Lady Agatha"));
        assertTrue(prompt.contains("ALTER_ENVIRONMENT"));
    }

    @Test
    void testStationaryMachineryHazardGuidance() {
        Item centrifuge = new Item();
        centrifuge.setId("item_centrifuge");
        centrifuge.setName("Industrial Centrifuge");
        centrifuge.setType(ItemType.WEAPON);
        centrifuge.setMovable(false);
        centrifuge.setCurrentLocationId("room_1");
        repository.getItems().put("item_centrifuge", centrifuge);

        // When victim is elsewhere (planning/escalating)
        victim.setCurrentLocationId("room_0");
        repository.setCurrentStep(10);
        String promptElsewhere = director.buildDramaturgicDirectives(murderer, repository);
        assertTrue(promptElsewhere.contains("[LETHAL STATIONARY HAZARD IN ROOM]"));
        assertTrue(promptElsewhere.contains("Industrial Centrifuge"));
        assertFalse(promptElsewhere.contains("interaction: 'TAKE'"));

        // When victim is in room at strike window
        victim.setCurrentLocationId("room_1");
        repository.setCurrentStep(20);
        String promptStrike = director.buildDramaturgicDirectives(murderer, repository);
        assertTrue(promptStrike.contains("[CRITICAL STRIKE DIRECTIVE"));
        assertTrue(promptStrike.contains("weaponItemId: 'item_centrifuge'"));
        assertTrue(promptStrike.contains("use the stationary machinery/fixture in this room: Industrial Centrifuge"));
    }

    @Test
    void testCovertPoisonStrategyGuidance() {
        victim.setCurrentLocationId("room_0");
        repository.setCurrentStep(5);

        String prompt = director.buildDramaturgicDirectives(murderer, repository);
        assertTrue(prompt.contains("[COVERT POISON STRATEGY - REMOTE MURDER]"));
        assertTrue(prompt.contains("Lady Agatha"));
        assertTrue(prompt.contains("POISON"));
    }

    @Test
    void testEvaluateMurdererPressurePlanningPhase() {
        victim.setCurrentLocationId("room_0");
        repository.setCurrentStep(3);

        List<Agent> roomAgents = List.of(murderer);
        DramaturgyDirectorService.MurdererPressureStatus status = director.evaluateMurdererPressure(repository, murderer, roomAgents, 4);

        assertNotNull(status);
        assertFalse(status.victimPresent());
        assertEquals("PLANNING", status.urgencyPhase());
        assertTrue(status.pressureSummary().contains("World introduction pacing"));
    }

    @Test
    void testEvaluateMurdererPressurePostCrimePhase() {
        victim.setAlive(false);
        repository.setCurrentStep(25);

        List<Agent> roomAgents = List.of(murderer, detective);
        DramaturgyDirectorService.MurdererPressureStatus status = director.evaluateMurdererPressure(repository, murderer, roomAgents, 26);

        assertNotNull(status);
        assertEquals("POST_CRIME", status.urgencyPhase());
        assertTrue(status.pressureSummary().contains("Post-crime concealment"));
    }

    @Test
    void testEvaluateMurdererPressureDesperatePhase() {
        victim.setCurrentLocationId("room_0");
        repository.setCurrentStep(40); // 40% of 100

        List<Agent> roomAgents = List.of(murderer);
        DramaturgyDirectorService.MurdererPressureStatus status = director.evaluateMurdererPressure(repository, murderer, roomAgents, 41);

        assertNotNull(status);
        assertEquals("DESPERATE", status.urgencyPhase());
        assertTrue(status.pressureSummary().contains("Desperate pacing pressure"));
    }

    @Test
    void testTimelineEventsRecordingAndMetadata() {
        com.murderhecoded.domain.Action murderAction = new com.murderhecoded.domain.Action();
        murderAction.setActionType(com.murderhecoded.domain.ActionType.MURDER);
        murderAction.setActorId("agent_1");
        murderAction.setTargetAgentId("agent_2");
        murderAction.setDetails("Strikes with lethal force");

        com.murderhecoded.domain.TimeLocationEvent event = com.murderhecoded.domain.TimeLocationEvent.builder()
                .timeStep(5)
                .sequenceNumber(1)
                .locationId("room_1")
                .locationName("Research Lab")
                .presentAgentNames(List.of("Dr. Malice", "Lady Agatha"))
                .suspenseScore(8.5)
                .murdererPresent(true)
                .murdererPressureInfo("CRITICAL STRIKE WINDOW!")
                .actions(List.of(murderAction))
                .build();

        repository.recordTimelineEvents(5, List.of(event));

        List<com.murderhecoded.domain.TimeLocationEvent> step5Events = repository.getEventsForStep(5);
        assertEquals(1, step5Events.size());
        com.murderhecoded.domain.TimeLocationEvent recorded = step5Events.get(0);

        assertEquals(8.5, recorded.getSuspenseScore());
        assertTrue(recorded.isMurdererPresent());
        assertEquals("CRITICAL STRIKE WINDOW!", recorded.getMurdererPressureInfo());
        assertTrue(recorded.hasMurderAction());
        assertFalse(recorded.hasAccuseAction());
        assertEquals(2, recorded.getPresentAgentNames().size());

        // Test step without events returns empty list
        assertTrue(repository.getEventsForStep(99).isEmpty());
    }

    @Test
    void testProfileOverrideViaSimulationRepository() {
        // With standard profile, step 26 of 100 is in Act 2 (Rising Suspense, threshold 0.15 - 0.35)
        repository.setCurrentStep(26);
        assertEquals(DramaturgyDirectorService.StoryAct.ACT_2_RISING_SUSPENSE, director.determineCurrentAct(repository));

        // When overridden with Fast-Paced Thriller profile (Act 3 threshold is 0.25)
        repository.setDramaturgyProfile(DramaturgyProfile.fastPacedThriller());
        assertEquals(DramaturgyDirectorService.StoryAct.ACT_3_THE_CRIME, director.determineCurrentAct(repository));

        // Murderer pressure at step 26 is now DESPERATE under Fast-Paced Thriller
        victim.setCurrentLocationId("room_0");
        List<Agent> roomAgents = List.of(murderer);
        DramaturgyDirectorService.MurdererPressureStatus status = director.evaluateMurdererPressure(repository, murderer, roomAgents, 26);
        assertNotNull(status);
        assertEquals("DESPERATE", status.urgencyPhase());
    }

    @Test
    void testCustomProfileInDirectorConstructor() {
        DramaturgyDirectorService slowDirector = new DramaturgyDirectorService(null, DramaturgyProfile.slowBurn());
        assertEquals("Slow-Burn Atmosphere", slowDirector.getProfile().name());

        // Under slow burn, at step 35 (35%), still Act 2 (threshold is 0.50)
        SimulationRepository repo = new SimulationRepository();
        repo.setTargetSteps(100);
        repo.setCurrentStep(35);
        repo.setDramaturgyProfile(DramaturgyProfile.slowBurn());
        assertEquals(DramaturgyDirectorService.StoryAct.ACT_2_RISING_SUSPENSE, slowDirector.determineCurrentAct(repo));
    }

    @Test
    void testMathematicalActProgressionAndPhases() {
        // Standard profile: Act 1 is 0.0 - 0.15 (steps 0 to 15 out of 100)
        // Step 3 = 0.03 (20% through Act 1 -> EARLY)
        repository.setCurrentStep(3);
        assertEquals(DramaturgyDirectorService.StoryAct.ACT_1_EXPOSITION, director.determineCurrentAct(repository));
        assertEquals(DramaturgyDirectorService.ActPhase.EARLY, director.determineCurrentActPhase(repository));

        // Step 8 = 0.08 (~53% through Act 1 -> MID)
        repository.setCurrentStep(8);
        assertEquals(DramaturgyDirectorService.ActPhase.MID, director.determineCurrentActPhase(repository));

        // Step 13 = 0.13 (~86% through Act 1 -> LATE)
        repository.setCurrentStep(13);
        assertEquals(DramaturgyDirectorService.ActPhase.LATE, director.determineCurrentActPhase(repository));

        // Act 3 Crime is 0.35 - 0.50 (span 0.15)
        // Step 36 = 0.36 (1/15 ~ 6.7% through Act 3 -> EARLY)
        repository.setCurrentStep(36);
        assertEquals(DramaturgyDirectorService.StoryAct.ACT_3_THE_CRIME, director.determineCurrentAct(repository));
        assertEquals(DramaturgyDirectorService.ActPhase.EARLY, director.determineCurrentActPhase(repository));

        // Step 43 = 0.43 (8/15 ~ 53% through Act 3 -> MID)
        repository.setCurrentStep(43);
        assertEquals(DramaturgyDirectorService.ActPhase.MID, director.determineCurrentActPhase(repository));

        // Step 48 = 0.48 (13/15 ~ 86% through Act 3 -> LATE)
        repository.setCurrentStep(48);
        assertEquals(DramaturgyDirectorService.ActPhase.LATE, director.determineCurrentActPhase(repository));

        // Directives header verification
        String directives = director.buildDramaturgicDirectives(murderer, repository);
        assertTrue(directives.contains("Current Narrative Stage: [Act 3: The Crime & Strike Opportunity]"));
        assertTrue(directives.contains("Act Progression: Phase [Late Phase / Climax]"));
        assertTrue(directives.contains("Story Progress: Step 48 of 100 (48.0% of planned mystery)"));
    }

    @Test
    void testVictimCriticalDangerGuidanceInAct3() {
        repository.setCurrentStep(40); // Act 3
        String victimPrompt = director.buildDramaturgicDirectives(victim, repository);
        assertTrue(victimPrompt.contains("Role Directive (The Target - CRITICAL DANGER)"));
        assertTrue(victimPrompt.contains("DRAMATURGIC VULNERABILITY"));
    }

    @Test
    void testDetectiveSubphaseGuidanceInAct6() {
        repository.setCurrentStep(98); // Act 6 Late (span 0.85 - 1.0; 0.98 is ~87% through Act 6)
        String detectivePrompt = director.buildDramaturgicDirectives(detective, repository);
        assertTrue(detectivePrompt.contains("CLIMAX FINAL ACCUSATION"));
        assertTrue(detectivePrompt.contains("ACCUSE action"));
    }

    @Test
    void testStrikeWindowInAct3MidPhaseAllowsSmallGroup() {
        repository.setCurrentStep(43); // Act 3 Mid Phase
        murderer.setCurrentLocationId("room_1");
        victim.setCurrentLocationId("room_1");
        Agent witness = new Agent();
        witness.setId("agent_3");
        witness.setName("Bystander");
        witness.setAlive(true);
        witness.setCurrentLocationId("room_1");

        // 3 agents in room (murderer, victim, witness)
        List<Agent> agents = List.of(murderer, victim, witness);
        assertTrue(director.isStrikeWindow(repository, murderer, agents));
    }
    @Test
    void testEmergencyOverrideActiveAndGuidance() {
        assertFalse(director.isEmergencyOverrideActive(repository));

        // Trigger discovery at step 10
        repository.setCurrentStep(10);
        director.setDiscoveryStep(10);
        GlobalEvent alarm = new GlobalEvent(
                "event_alarm", "Alarm: Body Discovered!", "Body in airlock", 10, 3, "room_1", "ALARM", true, true, false
        );
        repository.putGlobalEvent("event_alarm", alarm);

        assertTrue(director.isEmergencyOverrideActive(repository));
        String desc = director.getActiveCrisisDescription(repository);
        assertTrue(desc.contains("Alarm: Body Discovered!"));

        String directives = director.buildDramaturgicDirectives(detective, repository);
        assertTrue(directives.contains("WORLD STATE OVERRIDE - ACTIVE CRISIS"));
        assertTrue(directives.contains("ALL ORDINARY ROUTINES AND BACKGROUND GOALS ARE STRICTLY OVERRIDDEN"));

        // At step 16 (after 4+ steps), override concludes if no active alarm event
        repository.setCurrentStep(16);
        assertFalse(director.isEmergencyOverrideActive(repository));
    }

    @Test
    void testStrikeWindowForbiddenWhenDetectiveInRoom() {
        repository.setCurrentStep(43); // Act 3 Mid Phase
        murderer.setCurrentLocationId("room_1");
        victim.setCurrentLocationId("room_1");
        detective.setCurrentLocationId("room_1");

        List<Agent> agents = List.of(murderer, victim, detective);
        assertFalse(director.isStrikeWindow(repository, murderer, agents),
                "Murderer must never strike in the presence of the detective");
    }

    @Test
    void testStrikeWindowForbiddenInCrowdedRoomEvenWithEnvironmentalCover() {
        repository.setCurrentStep(20);
        murderer.setCurrentLocationId("room_1");
        victim.setCurrentLocationId("room_1");

        GlobalEvent coverEvent = new GlobalEvent(
                "event_cover_1", "Dimming Lights", "Lights flicker", 20, 2, "room_1", "ENVIRONMENTAL", true, true, false
        );
        repository.putGlobalEvent("event_cover_1", coverEvent);

        List<Agent> crowdedRoom = new ArrayList<>();
        crowdedRoom.add(murderer);
        crowdedRoom.add(victim);
        for (int i = 2; i <= 6; i++) {
            Agent bystander = new Agent();
            bystander.setId("agent_" + i);
            bystander.setName("Guest " + i);
            bystander.setAlive(true);
            crowdedRoom.add(bystander);
        }

        // 7 people in the room!
        assertEquals(7, crowdedRoom.size());
        assertFalse(director.isStrikeWindow(repository, murderer, crowdedRoom),
                "Murderer must not strike in front of a crowd of 5 bystanders even with environmental cover");
    }

    @Test
    void testSuspectWithWordMurderInGoalIsNotMurderer() {
        Agent suspectFrame = new Agent();
        suspectFrame.setId("agent_suspect1");
        suspectFrame.setName("Elena Voss");
        suspectFrame.setSecretGoal("Frame the engineer for murder to seize control of the station");
        assertFalse(suspectFrame.isMurderer(), "Suspect wanting to frame someone for murder is not a murderer");

        Agent suspectCover = new Agent();
        suspectCover.setId("agent_suspect2");
        suspectCover.setName("Sofia Reyes");
        suspectCover.setSecretGoal("Cover up the transmission leak that revealed the murder before it happened");
        assertFalse(suspectCover.isMurderer(), "Suspect wanting to cover up a leak is not a murderer");

        Agent realMurderer = new Agent();
        realMurderer.setId("agent_killer");
        realMurderer.setName("Viktor Hale");
        realMurderer.setSecretGoal("Murder Captain Mara Quinn because she ordered the shutdown");
        assertTrue(realMurderer.isMurderer(), "Agent whose secret goal starts with 'Murder' is the murderer");
    }

    @Test
    void testDetectiveGuidanceDoesNotForceMoveToCrimeSceneAfterInspection() {
        // Victim dies in room_1
        victim.setAlive(false);
        victim.setCurrentLocationId("room_1");
        director.setBodyDiscovered(true);

        // Detective is in room_0 (Grand Hall) and has already inspected crime scene
        detective.setCurrentLocationId("room_0");
        detective.setHasInspectedCrimeScene(true);

        String directives = director.buildDramaturgicDirectives(detective, repository);
        assertFalse(directives.contains("[CRITICAL CRIME SCENE PRIORITY]"),
                "Directives must NOT force MOVE to crime scene after detective has already inspected it");
        assertTrue(directives.contains("PATROL"),
                "Directives should instruct detective to patrol and interview suspects after inspecting crime scene");
    }
}
