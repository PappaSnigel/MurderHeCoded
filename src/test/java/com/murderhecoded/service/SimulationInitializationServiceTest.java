package com.murderhecoded.service;

import com.murderhecoded.agent.WorldBuilderService;
import com.murderhecoded.domain.*;
import com.murderhecoded.repository.SimulationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

class SimulationInitializationServiceTest {

    private WorldBuilderService worldBuilderService;
    private DramaturgyValidationService dramaturgyService;
    private SimulationInitializationService initService;
    private SimulationRepository repository;

    @BeforeEach
    void setUp() {
        worldBuilderService = Mockito.mock(WorldBuilderService.class);
        dramaturgyService = new DramaturgyValidationService();
        initService = new SimulationInitializationService(worldBuilderService, dramaturgyService);
        repository = new SimulationRepository();
    }

    @Test
    @DisplayName("Should initialize world state even when WorldBuilderService returns immutable lists")
    void testInitializeStateWithImmutableLists() {
        // Given: WorldSetupResponse constructed with List.of (unmodifiable lists)
        LocationRecord loc1 = new LocationRecord("room_0", "Command Deck", "High tech control center");
        LocationRecord loc2 = new LocationRecord("room_1", "Engineering Bay", "Humming reactor core");

        ItemRecord item1 = new ItemRecord("item_0", "Security Keycard", ItemType.CLUE, "room_0", true, false);
        // Note: No weapons in the initial list, forcing DramaturgyValidationService to call items.add(weapon)

        AgentRecord agent0 = new AgentRecord("agent_0", "Detective Miller", "Detective", "Sharp", "Calculated", "Neutral", "Solve the murder", "room_0", false);
        AgentRecord agent1 = new AgentRecord("agent_1", "Dr. Aris", "Chief Engineer", "Intense", "Vindictive", "Hostile to Vance", "Revenge against Captain Vance", "room_0", true);
        AgentRecord agent2 = new AgentRecord("agent_2", "Captain Vance", "Station Commander", "Stern", "Authoritative", "Suspicious of Aris", "Maintain order", "room_0", true);

        WorldSetupResponse worldSetup = new WorldSetupResponse(
                List.of(loc1, loc2),
                List.of(item1),
                List.of(agent0, agent1, agent2)
        );

        when(worldBuilderService.buildWorld(anyString(), anyString(), anyInt(), anyInt(), anyInt(), anyString(), anyString()))
                .thenReturn(worldSetup);

        List<String> logs = new ArrayList<>();
        initService.setOnLogEvent(logs::add);

        // When
        assertDoesNotThrow(() -> initService.initializeState(
                repository,
                "en",
                3,
                2,
                1,
                "Murder Over Mars 2",
                "A tense space murder mystery",
                "atmospheric"
        ));

        // Then
        assertTrue(repository.isWorldGenerated(), "World should be marked as generated");
        assertEquals(3, repository.getAgents().size());
        assertEquals(2, repository.getLocations().size());
        // At least 2 weapons guaranteed by DramaturgyValidationService + 1 initial item
        assertTrue(repository.getItems().size() >= 3, "Items list should include added weapons");

        Agent murderer = repository.getAgents().get("agent_1");
        assertNotNull(murderer);
        assertTrue(murderer.isHasMurderIntent());
        assertEquals("agent_2", murderer.getTargetVictimId());

        // Verify log event emitted
        assertTrue(logs.stream().anyMatch(l -> l.contains("Spelvärlden har framgångsrikt initialiserats")));
    }

    @Test
    @DisplayName("Should capture exception details cleanly when buildWorld fails without a message")
    void testExceptionHandlingWithoutMessage() {
        when(worldBuilderService.buildWorld(anyString(), anyString(), anyInt(), anyInt(), anyInt(), anyString(), anyString()))
                .thenThrow(new NullPointerException()); // Message is null

        List<String> errorLogs = new ArrayList<>();
        initService.setOnLogEvent(errorLogs::add);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> initService.initializeState(
                repository,
                "en",
                3,
                2,
                1,
                "Title",
                "Premise",
                "voice"
        ));

        assertFalse(repository.isWorldGenerated());
        assertTrue(ex.getMessage().contains("NullPointerException"), "Error message should include exception class name instead of 'null'");
        assertTrue(errorLogs.stream().anyMatch(l -> l.contains("[FEL] Världsgenerering misslyckades: NullPointerException")));
    }
}
