package com.murderhecoded.agent;

import com.murderhecoded.domain.AgentRecord;
import com.murderhecoded.llm.LlmExecutor;
import com.murderhecoded.service.OllamaLlmService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class WorldBuilderServiceTest {

    private WorldBuilderService worldBuilderService;

    @BeforeEach
    void setUp() {
        OllamaLlmService ollamaLlmService = Mockito.mock(OllamaLlmService.class);
        LlmExecutor llmExecutor = new LlmExecutor();
        worldBuilderService = new WorldBuilderService(ollamaLlmService, llmExecutor);
    }

    @Test
    void testSanitizeAndEnforceAgentsReconcilesPhantomVictim() {
        AgentRecord detective = new AgentRecord("agent_0", "Mara Kade", "Detective", "Sharp", "Calculated", "None", "Solve the murder", "room_0", false);
        AgentRecord killer = new AgentRecord("agent_1", "Dr. Thorne", "Chief Medical Officer", "Pale", "Nervous", "None", "Murder Captain Ilya Rostov because he discovered my secret", "room_1", true);
        AgentRecord bystander = new AgentRecord("agent_2", "Mira Solis", "Botanist", "Quiet", "Helpful", "None", "Steal seeds", "room_2", true);

        List<AgentRecord> result = worldBuilderService.sanitizeAndEnforceAgents(
                List.of(detective, killer, bystander),
                3,
                Set.of("room_0", "room_1", "room_2"),
                Map.of()
        );

        assertEquals(3, result.size());
        AgentRecord reconciledKiller = result.get(1);
        assertEquals("Dr. Thorne", reconciledKiller.name());
        // Since Captain Ilya Rostov does not exist, it must be reconciled to target an existing agent (Mira Solis)
        assertTrue(reconciledKiller.secretGoal().contains("Mira Solis"), "Killer goal should be reconciled to target existing agent");
        assertTrue(reconciledKiller.secretGoal().contains("because he discovered my secret"), "Original motive/reason should be preserved");
    }
}
