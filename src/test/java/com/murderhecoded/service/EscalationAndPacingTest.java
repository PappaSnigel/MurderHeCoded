package com.murderhecoded.service;

import com.murderhecoded.agent.AgentService;
import com.murderhecoded.domain.Agent;
import com.murderhecoded.domain.EnvironmentTrace;
import com.murderhecoded.domain.Location;
import com.murderhecoded.llm.LlmExecutor;
import com.murderhecoded.repository.SimulationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("PMD")
class EscalationAndPacingTest {

    private SimulationRepository state;
    private AgentService agentService;

    @BeforeEach
    void setUp() {
        state = new SimulationRepository();
        agentService = new AgentService(null, new LlmExecutor(), new DramaturgyDirectorService());

        Agent murderer = new Agent();
        murderer.setId("agent_1");
        murderer.setName("Lord Blackwood");
        murderer.setAlive(true);
        murderer.setHasMurderIntent(true);
        murderer.setSecretGoal("Murder Lord Arthur because of inheritance");
        murderer.setCurrentLocationId("room_1");
        murderer.setHasArrived(true);

        Agent victim = new Agent();
        victim.setId("agent_2");
        victim.setName("Lord Arthur");
        victim.setAlive(true);
        victim.setCurrentLocationId("room_1");
        victim.setHasArrived(true);

        Location room = new Location();
        room.setId("room_1");
        room.setName("Library");

        state.initialize(List.of(murderer, victim), List.of(room), List.of(), "Mystery Title", "Premise");
    }

    @Test
    void testPlanningPhaseAtStart() {
        state.setTargetSteps(144); // 36 hours
        state.setCurrentStep(5); // Act 1, very early. < 15% so isStrikeWindow is false even if isolated.

        Agent murderer = state.getAgents().get("agent_1");
        String prompt = agentService.buildPrompt(murderer, state);

        // Now uses the strict no murder directive since victim is in room but conditions (time) aren't met
        assertTrue(prompt.contains("[PACING - STRICTLY NO MURDER]"));
        assertFalse(prompt.contains("[URGENCY - DESPERATE PHASE]"));
    }

    @Test
    void testEscalatingPhaseMidWay() {
        state.setTargetSteps(144); // 36 hours
        state.setCurrentStep(25); // ~17% progress. Act 2. 

        Agent murderer = state.getAgents().get("agent_1");
        String prompt = agentService.buildPrompt(murderer, state);

        // Because they are alone (only 2 agents in room), isStrikeWindow is true.
        assertTrue(prompt.contains("[URGENCY - STRIKE WINDOW]"));
    }

    @Test
    void testDesperatePhaseWhenTimeRunningOut() {
        state.setTargetSteps(144); // 36 hours
        state.setCurrentStep(55); // >35% progress with victim still alive

        Agent murderer = state.getAgents().get("agent_1");
        String prompt = agentService.buildPrompt(murderer, state);

        assertTrue(prompt.contains("[URGENCY - DESPERATE PHASE]"));
    }

    @Test
    void testPostCrimePhaseAfterMurder() {
        state.setTargetSteps(144);
        state.setCurrentStep(30);

        // Victim is killed
        state.getAgents().get("agent_2").setAlive(false);

        Agent murderer = state.getAgents().get("agent_1");
        String prompt = agentService.buildPrompt(murderer, state);

        assertTrue(prompt.contains("[POST-CRIME PHASE]"));
        assertFalse(prompt.contains("[URGENCY - DESPERATE PHASE]"));
    }

    @Test
    void testEnvironmentTraceDirectNoticeForAddressedAgent() {
        Location room = state.getLocations().get("room_1");
        EnvironmentTrace trace = new EnvironmentTrace();
        trace.setId("trace_1");
        trace.setDescription("A note addressed to Lord Arthur: Meet me at the cellar.");
        room.getTraces().add(trace);

        Agent victim = state.getAgents().get("agent_2"); // Lord Arthur
        String prompt = agentService.buildPrompt(victim, state);

        assertTrue(prompt.contains("[NOTICE: This trace directly addresses or concerns YOU! Consider investigating or following it!]"));
    }
}
