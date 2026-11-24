package com.murderhecoded.service;

import com.murderhecoded.domain.Action;
import com.murderhecoded.domain.ActionType;
import com.murderhecoded.domain.Agent;
import com.murderhecoded.domain.Knowledge;
import com.murderhecoded.domain.Location;
import com.murderhecoded.repository.SimulationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SimulationServiceTest {

    private SimulationRepository repository;
    private SimulationService simulationService;
    private Agent murderer;
    private Agent detective;
    private Agent victim;
    private Location lounge;
    private Location airlock;

    @BeforeEach
    void setUp() {
        repository = new SimulationRepository();
        simulationService = new SimulationService(repository, null, null);

        lounge = new Location();
        lounge.setId("loc_lounge");
        lounge.setName("Stakeholders' Lounge");

        airlock = new Location();
        airlock.setId("loc_airlock");
        airlock.setName("Airlock");

        murderer = new Agent();
        murderer.setId("agent_hale");
        murderer.setName("Victor Hale");
        murderer.setHasMurderIntent(true);
        murderer.setAlive(true);
        murderer.setCurrentLocationId("loc_lounge");

        detective = new Agent();
        detective.setId("agent_0");
        detective.setName("Mara Quinn");
        detective.setRole("Detective");
        detective.setAlive(true);
        detective.setCurrentLocationId("loc_airlock");

        victim = new Agent();
        victim.setId("agent_elena");
        victim.setName("Elena Voss");
        victim.setAlive(true);
        victim.setCurrentLocationId("loc_lounge");

        repository.initialize(List.of(murderer, detective, victim), List.of(lounge, airlock), List.of(), List.of(), "Title", "Premise");
    }

    @Test
    void testMurdererCannotAccuseProgrammatically() {
        repository.setTargetSteps(100);
        repository.setCurrentStep(85);

        // Add dead body knowledge so murder is discovered
        detective.getKnowledgeBase().add(new Knowledge("Dead body of Elena Voss found", 80));
        murderer.getKnowledgeBase().add(new Knowledge("Dead body of Elena Voss found", 80));

        // Murderer amasses 53 facts
        for (int i = 0; i < 53; i++) {
            murderer.getKnowledgeBase().add(new Knowledge("Fact #" + i, 80));
        }

        // Run checkProgrammaticAccusation
        simulationService.checkProgrammaticAccusation();

        // Murderer must NOT complete the simulation with ACCUSATION!
        assertFalse(repository.isCompleted(), "Simulation must not be completed by a murderer accusing");

        // Now give the detective enough facts
        for (int i = 0; i < 6; i++) {
            detective.getKnowledgeBase().add(new Knowledge("Detective Fact #" + i, 80));
        }
        simulationService.checkProgrammaticAccusation();
        assertTrue(repository.isCompleted(), "Detective should be allowed to solve the case and complete the simulation");
    }

    @Test
    void testProcessSingleActionUpdatesConsecutiveSearchAndTurns() {
        Action search = new Action();
        search.setActorId(detective.getId());
        search.setActionType(ActionType.SEARCH);
        search.setDetails("Searching airlock");

        assertEquals(0, detective.getConsecutiveSearchCount());
        assertEquals(0, detective.getTurnsInCurrentLocation());

        simulationService.processSingleAction(search);
        assertEquals(1, detective.getConsecutiveSearchCount());
        assertEquals(1, detective.getTurnsInCurrentLocation());

        simulationService.processSingleAction(search);
        assertEquals(2, detective.getConsecutiveSearchCount());
        assertEquals(2, detective.getTurnsInCurrentLocation());

        // Moving resets turns and search count
        Action move = new Action();
        move.setActorId(detective.getId());
        move.setActionType(ActionType.MOVE);
        move.setTargetLocationId("loc_lounge");
        simulationService.processSingleAction(move);

        assertEquals(0, detective.getConsecutiveSearchCount());
        assertEquals(0, detective.getTurnsInCurrentLocation());
    }

    @Test
    void testProcessSingleActionDeduplicatesKnowledgeBase() {
        Action search1 = new Action();
        search1.setActorId(detective.getId());
        search1.setActionType(ActionType.SEARCH);
        search1.setNewKnowledgeFact("Discovered secret compartment in mahogany desk");

        simulationService.processSingleAction(search1);
        assertEquals(1, detective.getKnowledgeBase().size());

        // Attempting to learn the exact same or slightly location-tagged fact again
        Action search2 = new Action();
        search2.setActorId(detective.getId());
        search2.setActionType(ActionType.SEARCH);
        search2.setNewKnowledgeFact("Discovered secret compartment in mahogany desk (Location: Stakeholders' Lounge)");

        simulationService.processSingleAction(search2);
        assertEquals(1, detective.getKnowledgeBase().size(), "Duplicate discovery fact should be deduplicated");
    }

    @Test
    void testGenerateSafeFallbackAction() {
        // Alone in room -> generates MOVE
        Action fallbackAlone = simulationService.generateSafeFallbackAction(detective, repository, List.of(detective));
        assertEquals(ActionType.MOVE, fallbackAlone.getActionType());
        assertNotNull(fallbackAlone.getTargetLocationId());
        assertNotEquals(detective.getCurrentLocationId(), fallbackAlone.getTargetLocationId());

        // Others in room -> generates TALK
        Action fallbackWithOthers = simulationService.generateSafeFallbackAction(murderer, repository, List.of(murderer, victim));
        assertEquals(ActionType.TALK, fallbackWithOthers.getActionType());
        assertEquals(victim.getId(), fallbackWithOthers.getTargetAgentId());
    }

    @Test
    void testDetectiveFallbackPrioritizesCrimeSceneWhenMurderOccurred() {
        // Victim dies in lounge
        victim.setAlive(false);
        victim.setCurrentLocationId("loc_lounge");
        detective.setCurrentLocationId("loc_airlock");

        Action action = simulationService.generateSafeFallbackAction(detective, repository, List.of(detective));
        assertEquals(ActionType.MOVE, action.getActionType());
        assertEquals("loc_lounge", action.getTargetLocationId(), "Detective must prioritize moving directly to the crime scene");
        assertTrue(action.getDetails().contains("crime scene"));
    }

    @Test
    void testDetectiveFallbackDoesNotPrioritizeCrimeSceneAfterInspecting() {
        // Victim dies in lounge, but detective has already inspected crime scene
        victim.setAlive(false);
        victim.setCurrentLocationId("loc_lounge");
        detective.setCurrentLocationId("loc_airlock");
        detective.setHasInspectedCrimeScene(true);

        // Bystander present in airlock to interview
        Agent bystander = new Agent();
        bystander.setId("agent_bystander");
        bystander.setName("Witness");
        bystander.setAlive(true);
        bystander.setHasArrived(true);
        bystander.setCurrentLocationId("loc_airlock");
        repository.getAgents().put(bystander.getId(), bystander);

        Action action = simulationService.generateSafeFallbackAction(detective, repository, List.of(detective, bystander));
        assertNotEquals("loc_lounge", action.getTargetLocationId(),
                "Detective who has already inspected crime scene must not be forced back to crime scene");
    }

    @Test
    void testAct6ClimaxPrioritizesGrandGatheringArea() {
        repository.setTargetSteps(100);
        repository.setCurrentStep(90); // Act 6 Climax

        // Murderer is in airlock
        murderer.setCurrentLocationId("loc_airlock");

        Action action = simulationService.generateSafeFallbackAction(murderer, repository, List.of(murderer));
        assertEquals(ActionType.MOVE, action.getActionType());
        assertEquals("loc_lounge", action.getTargetLocationId(), "In Act 6 Climax, agent should move to the main gathering lounge");
        assertTrue(action.getDetails().contains("grand gathering"));
    }
}
