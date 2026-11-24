package com.murderhecoded.service;

import com.murderhecoded.domain.*;
import com.murderhecoded.repository.SimulationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("PMD")
class NarrativeEditorServiceTest {

    private NarrativeEditorService editorService;
    private SimulationRepository repository;

    @BeforeEach
    public void setup() {
        repository = new SimulationRepository();
        editorService = new NarrativeEditorService();

        Location location1 = new Location();
        location1.setId("room_1");
        location1.setName("The Library");
        location1.setDescription("A quiet dusty library.");

        Location location2 = new Location();
        location2.setId("room_2");
        location2.setName("The Conservatory");
        location2.setDescription("A glass conservatory.");

        Agent murderer = new Agent();
        murderer.setId("agent_1");
        murderer.setName("Arthur Pendelton");
        murderer.setAlive(true);
        murderer.setHasArrived(true);
        murderer.setCurrentLocationId("room_1");
        murderer.setHasMurderIntent(true);
        murderer.setTargetVictimId("agent_2");

        Agent victim = new Agent();
        victim.setId("agent_2");
        victim.setName("Lord Blackwood");
        victim.setAlive(true);
        victim.setHasArrived(true);
        victim.setCurrentLocationId("room_1");

        Agent detective = new Agent();
        detective.setId("agent_0");
        detective.setName("Inspector Vance");
        detective.setRole("Detective");
        detective.setAlive(true);
        detective.setHasArrived(false);
        detective.setCurrentLocationId("room_2");

        Map<String, Agent> agents = new HashMap<>();
        agents.put(murderer.getId(), murderer);
        agents.put(victim.getId(), victim);
        agents.put(detective.getId(), detective);

        Map<String, Location> places = new HashMap<>();
        places.put(location1.getId(), location1);
        places.put(location2.getId(), location2);

        repository.initialize(new ArrayList<>(agents.values()), new ArrayList<>(places.values()), new ArrayList<>(), "Mystery Title", "Premise");
    }




    @Test
    public void testSceneBreakOnHighImpactEvent() {
        Action observe = new Action();
        observe.setActionType(ActionType.OBSERVE);

        TimeLocationEvent batchEvent = TimeLocationEvent.builder()
                .timeStep(1)
                .locationId("room_1")
                .actions(List.of(observe))
                .build();

        Action murder = new Action();
        murder.setActionType(ActionType.MURDER);

        TimeLocationEvent incomingHighImpact = TimeLocationEvent.builder()
                .timeStep(2)
                .locationId("room_1")
                .actions(List.of(murder))
                .build();

        boolean shouldBreak = editorService.shouldBreakScene(List.of(batchEvent), incomingHighImpact, false);
        assertTrue(shouldBreak, "Editor should trigger immediate scene break on high-impact action (MURDER)");
    }

    @Test
    public void testFormatSceneTimeSpan() {
        Scene singleStepScene = Scene.builder()
                .sceneNumber(1)
                .startStep(2)
                .endStep(2)
                .build();
        String singleFormatted = editorService.formatSceneTimeSpan(singleStepScene, repository);
        assertTrue(singleFormatted.contains("Step 2"));

        Scene multiStepScene = Scene.builder()
                .sceneNumber(2)
                .startStep(1)
                .endStep(3)
                .build();
        String multiFormatted = editorService.formatSceneTimeSpan(multiStepScene, repository);
        assertTrue(multiFormatted.contains("Steps 1-3"));
    }

    @Test
    public void testEditorialSelectionCriteria() {
        // Essential category must always be selected
        assertTrue(editorService.determineSelection(SceneCategory.ESSENTIAL, 8.0, 9.0, false));
        // Murder action must always be selected
        assertTrue(editorService.determineSelection(SceneCategory.DISPENSABLE, 2.0, 1.0, true));
        // Supporting with good score is selected
        assertTrue(editorService.determineSelection(SceneCategory.SUPPORTING, 5.5, 6.0, false));
        // Dispensable fluff without murder is cut
        assertFalse(editorService.determineSelection(SceneCategory.DISPENSABLE, 2.0, 1.5, false));
    }

    @Test
    public void testAssembleScene_withNullActionType_doesNotThrowNpe() {
        Action actionWithNullType = new Action();
        actionWithNullType.setActorId("agent_1");
        actionWithNullType.setActionType(null);

        TimeLocationEvent event = TimeLocationEvent.builder()
                .timeStep(1)
                .locationId("room_1")
                .locationName("The Library")
                .actions(List.of(actionWithNullType))
                .knowledgeGained(List.of())
                .snapshotBefore(repository.getSnapshots().get(0))
                .snapshotAfter(repository.getSnapshots().get(0))
                .build();

        assertDoesNotThrow(() -> {
            Scene scene = editorService.assembleScene(1, List.of(event), repository);
            assertNotNull(scene);
        });
    }

    @Test
    public void testActionTypeJsonCreator_handlesUnknownAndAliases() {
        assertEquals(ActionType.TALK, ActionType.fromString("talk"));
        assertEquals(ActionType.MANIPULATE_ITEM, ActionType.fromString("TAKE"));
        assertEquals(ActionType.MANIPULATE_ITEM, ActionType.fromString("hide"));
        assertEquals(ActionType.MURDER, ActionType.fromString("kill"));
        assertEquals(ActionType.SEARCH, ActionType.fromString("investigate"));
        assertEquals(ActionType.OBSERVE, ActionType.fromString("unknown_custom_action"));
        assertEquals(ActionType.OBSERVE, ActionType.fromString(null));
    }
}
