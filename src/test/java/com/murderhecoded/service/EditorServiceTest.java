package com.murderhecoded.service;

import com.murderhecoded.domain.*;
import com.murderhecoded.repository.SimulationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EditorServiceTest {

    private EditorService editorService;
    private SimulationRepository repository;

    @BeforeEach
    void setUp() {
        editorService = new EditorService();
        repository = new SimulationRepository();

        Location library = new Location();
        library.setId("room_1");
        library.setName("The Library");

        Location kitchen = new Location();
        kitchen.setId("room_2");
        kitchen.setName("The Kitchen");

        Agent arthur = new Agent();
        arthur.setId("agent_1");
        arthur.setName("Arthur");
        arthur.setCurrentLocationId("room_1");
        arthur.setAlive(true);
        arthur.setHasArrived(true);

        Agent beatrice = new Agent();
        beatrice.setId("agent_2");
        beatrice.setName("Beatrice");
        beatrice.setCurrentLocationId("room_1");
        beatrice.setAlive(true);
        beatrice.setHasArrived(true);

        repository.initialize(List.of(arthur, beatrice), List.of(library, kitchen), List.of(), "Estate Mystery", "A rainy manor");
        repository.setCurrentStep(5);
    }

    @Test
    void testMeasureDramaScore() {
        Action mundaneAction = new Action();
        mundaneAction.setActorId("agent_1");
        mundaneAction.setActionType(ActionType.OBSERVE);

        TimeLocationEvent mundaneEvent = TimeLocationEvent.builder()
                .timeStep(1)
                .sequenceNumber(1)
                .locationId("room_1")
                .locationName("The Library")
                .actions(List.of(mundaneAction))
                .build();

        Action dramaticAction = new Action();
        dramaticAction.setActorId("agent_1");
        dramaticAction.setActionType(ActionType.MURDER);
        dramaticAction.setTargetAgentId("agent_2");

        TimeLocationEvent dramaticEvent = TimeLocationEvent.builder()
                .timeStep(2)
                .sequenceNumber(2)
                .locationId("room_1")
                .locationName("The Library")
                .actions(List.of(dramaticAction))
                .build();

        double mundaneScore = editorService.measureDrama(mundaneEvent, repository);
        double dramaticScore = editorService.measureDrama(dramaticEvent, repository);

        assertTrue(mundaneScore >= 1.0 && mundaneScore <= 10.0);
        assertTrue(dramaticScore >= 1.0 && dramaticScore <= 10.0);
        assertTrue(dramaticScore > mundaneScore, "Murder action should yield a higher drama score than mundane observation");
        assertEquals(dramaticScore, dramaticEvent.getDramaScore());
    }

    @Test
    void testAssembleSceneFramingFirstAndLastEvent() {
        // Event 1: Idle mundane observation (leading idle lag)
        Action idleAction = new Action();
        idleAction.setActorId("agent_1");
        idleAction.setActionType(ActionType.OBSERVE);

        TimeLocationEvent ev1 = TimeLocationEvent.builder()
                .timeStep(1)
                .sequenceNumber(1)
                .locationId("room_1")
                .locationName("The Library")
                .actions(List.of(idleAction))
                .build();

        // Event 2: Meaningful search action (first meaningful hook)
        Action searchAction = new Action();
        searchAction.setActorId("agent_1");
        searchAction.setActionType(ActionType.SEARCH);

        TimeLocationEvent ev2 = TimeLocationEvent.builder()
                .timeStep(2)
                .sequenceNumber(2)
                .locationId("room_1")
                .locationName("The Library")
                .actions(List.of(searchAction))
                .build();

        // Event 3: Confrontation/Murder action (climax cutoff)
        Action murderAction = new Action();
        murderAction.setActorId("agent_1");
        murderAction.setActionType(ActionType.MURDER);
        murderAction.setTargetAgentId("agent_2");

        TimeLocationEvent ev3 = TimeLocationEvent.builder()
                .timeStep(3)
                .sequenceNumber(3)
                .locationId("room_1")
                .locationName("The Library")
                .actions(List.of(murderAction))
                .build();

        // Event 4: Trailing idle event after climax
        TimeLocationEvent ev4 = TimeLocationEvent.builder()
                .timeStep(4)
                .sequenceNumber(4)
                .locationId("room_1")
                .locationName("The Library")
                .actions(List.of(idleAction))
                .build();

        Scene scene = editorService.assembleScene(1, List.of(ev1, ev2, ev3, ev4), repository);

        assertNotNull(scene);
        assertNotNull(scene.getFirstEvent(), "firstEvent must not be null");
        assertNotNull(scene.getLastEvent(), "lastEvent must not be null");
        assertEquals(ev2.getTimeStep(), scene.getFirstEvent().getTimeStep(), "firstEvent should be the meaningful hook (ev2)");
        assertEquals(ev3.getTimeStep(), scene.getLastEvent().getTimeStep(), "lastEvent should be the climax cutoff (ev3)");
        assertEquals(2, scene.getEvents().size(), "Curated sequence should prune outside events");
        assertTrue(scene.getDramaScore() >= 8.0, "Climactic scene should have high peak drama score");
    }

    @Test
    void testCreateScenesChronologicalSpan() {
        Action searchAction = new Action();
        searchAction.setActorId("agent_1");
        searchAction.setActionType(ActionType.SEARCH);

        // Events in Library at step 1 and 2
        TimeLocationEvent lib1 = TimeLocationEvent.builder()
                .timeStep(1).sequenceNumber(1).locationId("room_1").locationName("The Library")
                .actions(List.of(searchAction)).build();
        TimeLocationEvent lib2 = TimeLocationEvent.builder()
                .timeStep(2).sequenceNumber(2).locationId("room_1").locationName("The Library")
                .actions(List.of(searchAction)).build();

        // Events in Kitchen at step 1 and 2
        TimeLocationEvent kitch1 = TimeLocationEvent.builder()
                .timeStep(1).sequenceNumber(3).locationId("room_2").locationName("The Kitchen")
                .actions(List.of(searchAction)).build();
        TimeLocationEvent kitch2 = TimeLocationEvent.builder()
                .timeStep(2).sequenceNumber(4).locationId("room_2").locationName("The Kitchen")
                .actions(List.of(searchAction)).build();

        List<Scene> scenes = editorService.createScenes(List.of(lib1, lib2, kitch1, kitch2), repository);

        assertNotNull(scenes);
        assertEquals(2, scenes.size(), "Each location must curate into its own separate scene to guarantee location purity");
        Scene sc1 = scenes.get(0);
        assertEquals("room_1", sc1.getPrimaryLocationId());
        assertTrue(sc1.getEvents().stream().allMatch(e -> "room_1".equals(e.getLocationId())),
                "Scene 1 must only contain Library events");
        assertEquals(1, sc1.getStartStep());
        assertEquals(2, sc1.getEndStep());

        Scene sc2 = scenes.get(1);
        assertEquals("room_2", sc2.getPrimaryLocationId());
        assertTrue(sc2.getEvents().stream().allMatch(e -> "room_2".equals(e.getLocationId())),
                "Scene 2 must only contain Kitchen events");
        assertEquals(1, sc2.getStartStep());
        assertEquals(2, sc2.getEndStep());
    }

    @Test
    void testStrictSingleLocationInvariantWithInterleavedEvents() {
        Action talkAction = new Action();
        talkAction.setActionType(ActionType.TALK);

        TimeLocationEvent roomA1 = TimeLocationEvent.builder().timeStep(1).sequenceNumber(1).locationId("room_A").actions(List.of(talkAction)).build();
        TimeLocationEvent roomB1 = TimeLocationEvent.builder().timeStep(1).sequenceNumber(2).locationId("room_B").actions(List.of(talkAction)).build();
        TimeLocationEvent roomA2 = TimeLocationEvent.builder().timeStep(2).sequenceNumber(3).locationId("room_A").actions(List.of(talkAction)).build();
        TimeLocationEvent roomB2 = TimeLocationEvent.builder().timeStep(2).sequenceNumber(4).locationId("room_B").actions(List.of(talkAction)).build();

        List<Scene> scenes = editorService.createScenes(List.of(roomA1, roomB1, roomA2, roomB2), repository);

        assertEquals(2, scenes.size());
        for (Scene scene : scenes) {
            String primaryLoc = scene.getPrimaryLocationId();
            for (TimeLocationEvent ev : scene.getEvents()) {
                assertEquals(primaryLoc, ev.getLocationId(), "No scene may contain events from any other location");
            }
        }
    }

    @Test
    void testMultiStepSceneCohesiveness() {
        Action talkAction = new Action();
        talkAction.setActionType(ActionType.TALK);

        List<TimeLocationEvent> events = new java.util.ArrayList<>();
        for (int step = 1; step <= 6; step++) {
            events.add(TimeLocationEvent.builder()
                    .timeStep(step)
                    .sequenceNumber(step)
                    .locationId("room_1")
                    .locationName("The Library")
                    .actions(List.of(talkAction))
                    .build());
        }

        List<Scene> scenes = editorService.createScenes(events, repository);

        assertEquals(1, scenes.size(), "6 steps of continuous interaction should curate into 1 cohesive scene, not 6 separate scenes");
        Scene scene = scenes.get(0);
        assertEquals(1, scene.getStartStep());
        assertEquals(6, scene.getEndStep());
        assertEquals(6, scene.getEvents().size());
    }

    @Test
    void testPruneMundaneIdleEpisodes() {
        Action observeAction = new Action();
        observeAction.setActionType(ActionType.OBSERVE);

        // Room 2 has purely mundane observing with drama score 1.0 and no knowledge
        TimeLocationEvent mundane1 = TimeLocationEvent.builder()
                .timeStep(1).sequenceNumber(1).locationId("room_2").actions(List.of(observeAction)).dramaScore(1.0).build();
        TimeLocationEvent mundane2 = TimeLocationEvent.builder()
                .timeStep(2).sequenceNumber(2).locationId("room_2").actions(List.of(observeAction)).dramaScore(1.0).build();

        // Room 1 has dramatic search and conversation
        Action talkAction = new Action();
        talkAction.setActionType(ActionType.TALK);
        TimeLocationEvent dramatic1 = TimeLocationEvent.builder()
                .timeStep(1).sequenceNumber(3).locationId("room_1").actions(List.of(talkAction)).dramaScore(5.0).build();
        TimeLocationEvent dramatic2 = TimeLocationEvent.builder()
                .timeStep(2).sequenceNumber(4).locationId("room_1").actions(List.of(talkAction)).dramaScore(5.0).build();

        List<Scene> scenes = editorService.createScenes(List.of(mundane1, mundane2, dramatic1, dramatic2), repository);

        assertEquals(1, scenes.size(), "Mundane room_2 should be pruned, leaving only dramatic room_1");
        assertEquals("room_1", scenes.get(0).getPrimaryLocationId());
    }

    @Test
    void testIsTimeToMakeSceneImmediateClimax() {
        Action murder = new Action();
        murder.setActionType(ActionType.MURDER);

        TimeLocationEvent murderEvent = TimeLocationEvent.builder()
                .timeStep(2)
                .locationId("room_1")
                .actions(List.of(murder))
                .build();

        boolean shouldCut = editorService.isTimeToMakeScene(List.of(murderEvent), 2, 2, repository, false);
        assertTrue(shouldCut, "Immediate scene break should trigger on high-impact action (MURDER)");
    }

    @Test
    void testIsTimeToMakeSceneMaxTemporalLimit() {
        TimeLocationEvent ev = TimeLocationEvent.builder()
                .timeStep(10)
                .locationId("room_1")
                .actions(List.of())
                .build();

        // Span from step 1 to 10 = 10 steps (MAX_SCENE_STEP_SPAN)
        boolean shouldCut = editorService.isTimeToMakeScene(List.of(ev), 10, 1, repository, false);
        assertTrue(shouldCut, "Scene should break when reaching 10 time steps");

        // Span from step 1 to 5 = 5 steps (not max yet)
        boolean shouldNotCut = editorService.isTimeToMakeScene(List.of(ev), 5, 1, repository, false);
        assertFalse(shouldNotCut, "Scene should not break before reaching threshold if mundane");
    }

    @Test
    void testSelectEventsForScenePrunesEmptyMundaneTurns() {
        Action observe = new Action();
        observe.setActionType(ActionType.OBSERVE);

        TimeLocationEvent mundaneSecondary = TimeLocationEvent.builder()
                .timeStep(1)
                .sequenceNumber(1)
                .locationId("room_2")
                .locationName("The Kitchen")
                .actions(List.of(observe))
                .dramaScore(1.0)
                .build();

        Action search = new Action();
        search.setActionType(ActionType.SEARCH);

        TimeLocationEvent dramaticPrimary = TimeLocationEvent.builder()
                .timeStep(1)
                .sequenceNumber(2)
                .locationId("room_1")
                .locationName("The Library")
                .actions(List.of(search))
                .dramaScore(6.5)
                .build();

        List<TimeLocationEvent> selected = editorService.selectEventsForScene(
                List.of(mundaneSecondary, dramaticPrimary), repository
        );

        assertTrue(selected.contains(dramaticPrimary), "Primary dramatic event must be selected");
    }

    @Test
    void testCreateScenesEmptyOrNull() {
        assertTrue(editorService.createScenes(null, repository).isEmpty());
        assertTrue(editorService.createScenes(List.of(), repository).isEmpty());
    }

    @Test
    void testPrunesRedundantSolitaryIdleLoopWithoutClues() {
        Action fruitlessSearch = new Action();
        fruitlessSearch.setActorId("agent_1");
        fruitlessSearch.setActionType(ActionType.SEARCH);

        // 4 steps of searching alone with no findings
        List<TimeLocationEvent> solitaryLoop = List.of(
                TimeLocationEvent.builder().timeStep(1).locationId("room_1").presentAgentNames(List.of("Arthur")).actions(List.of(fruitlessSearch)).build(),
                TimeLocationEvent.builder().timeStep(2).locationId("room_1").presentAgentNames(List.of("Arthur")).actions(List.of(fruitlessSearch)).build(),
                TimeLocationEvent.builder().timeStep(3).locationId("room_1").presentAgentNames(List.of("Arthur")).actions(List.of(fruitlessSearch)).build(),
                TimeLocationEvent.builder().timeStep(4).locationId("room_1").presentAgentNames(List.of("Arthur")).actions(List.of(fruitlessSearch)).build()
        );

        assertFalse(editorService.isEpisodeMeaningful(solitaryLoop, repository),
                "Editor must prune redundant solitary loops where agent searches alone with no findings");

        // If one event produces a clue, it becomes meaningful
        TimeLocationEvent clueEvent = TimeLocationEvent.builder()
                .timeStep(4)
                .locationId("room_1")
                .presentAgentNames(List.of("Arthur"))
                .actions(List.of(fruitlessSearch))
                .knowledgeGained(List.of("Discovered torn blueprint"))
                .build();
        List<TimeLocationEvent> loopWithClue = List.of(solitaryLoop.get(0), solitaryLoop.get(1), clueEvent);

        assertTrue(editorService.isEpisodeMeaningful(loopWithClue, repository),
                "Episode that uncovers a clue must be retained as meaningful");
    }
}
