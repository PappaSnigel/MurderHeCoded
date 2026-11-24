package com.murderhecoded.service;

import com.murderhecoded.domain.*;
import com.murderhecoded.domain.Location;
import com.murderhecoded.repository.SimulationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SuppressWarnings("PMD")
class ProseServiceTest {

    private OllamaLlmService mockLlmService;
    private SimulationRepository repository;
    private NarrativeEditorService editorService;
    private ProseService proseService;

    @BeforeEach
    void setUp() {
        mockLlmService = Mockito.mock(OllamaLlmService.class);
        when(mockLlmService.generateText(anyString())).thenReturn("A suspenseful chapter unfolds in the dim corridors of the estate.");
        when(mockLlmService.generateText(anyString(), any())).thenReturn("A suspenseful chapter unfolds in the dim corridors of the estate.");

        repository = new SimulationRepository();
        editorService = new NarrativeEditorService();
        proseService = new ProseService(mockLlmService, repository, editorService);

        Location library = new Location();
        library.setId("room_1");
        library.setName("The Library");

        Agent arthur = new Agent();
        arthur.setId("agent_1");
        arthur.setName("Arthur");
        arthur.setCurrentLocationId("room_1");
        arthur.setAlive(true);
        arthur.setHasArrived(true);

        repository.initialize(List.of(arthur), List.of(library), List.of(), "Mystery at Manor", "A classic mystery");
        repository.setCurrentStep(2);
    }

    @Test
    void testGenerateProseForRecordedEvents() throws InterruptedException {
        List<Chapter> chapters = new ArrayList<>();
        proseService.setOnChapterGenerated(chapters::add);

        Action action = new Action();
        action.setActorId("agent_1");
        action.setActionType(ActionType.SEARCH);
        action.setDetails("Searched the bookshelves for a hidden letter");

        TimeLocationEvent event1 = TimeLocationEvent.builder()
                .timeStep(1)
                .sequenceNumber(1)
                .locationId("room_1")
                .locationName("The Library")
                .actions(List.of(action))
                .knowledgeGained(List.of("Found a coded telegram"))
                .suspenseScore(5.0)
                .build();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean completed = new AtomicBoolean(false);

        proseService.generateProseForRecordedEvents(List.of(event1), () -> {
            completed.set(true);
            latch.countDown();
        });

        boolean finishedInTime = latch.await(5, TimeUnit.SECONDS);
        assertTrue(finishedInTime, "Prose generation should complete within timeout");
        assertTrue(completed.get(), "Completion callback should be invoked");
        assertFalse(chapters.isEmpty(), "At least one chapter should be generated");
        assertTrue(chapters.get(0).text().contains("suspenseful chapter"));
    }

    @Test
    void testGenerateProseForEmptyEvents() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean completed = new AtomicBoolean(false);

        proseService.generateProseForRecordedEvents(List.of(), () -> {
            completed.set(true);
            latch.countDown();
        });

        boolean finishedInTime = latch.await(3, TimeUnit.SECONDS);
        assertTrue(finishedInTime, "Empty events should complete immediately");
        assertTrue(completed.get());
    }

    @Test
    void testGenerateProseForSceneDirectly() {
        Action action = new Action();
        action.setActorId("agent_1");
        action.setActionType(ActionType.SEARCH);

        TimeLocationEvent event1 = TimeLocationEvent.builder()
                .timeStep(1)
                .sequenceNumber(1)
                .locationId("room_1")
                .locationName("The Library")
                .actions(List.of(action))
                .dramaScore(4.0)
                .build();

        Scene scene = Scene.builder()
                .sceneNumber(1)
                .startStep(1)
                .endStep(1)
                .primaryLocationId("room_1")
                .primaryLocationName("The Library")
                .dramaticFocus("Searching for clues in the library")
                .firstEvent(event1)
                .lastEvent(event1)
                .events(List.of(event1))
                .category(SceneCategory.SUPPORTING)
                .narrativeImportanceScore(5.0)
                .suspenseScore(4.0)
                .totalEditorialScore(4.6)
                .isSelected(true)
                .build();

        Chapter chapter = proseService.generateProseForScene(scene);

        assertNotNull(chapter);
        assertEquals(1, chapter.sceneNumber());
        assertEquals("The Library", chapter.locationName());
        assertTrue(chapter.text().contains("suspenseful chapter"));
        assertEquals(chapter.text(), scene.getProseText());
    }

    @Test
    void testGenerateProseForScenesBatch() throws InterruptedException {
        Action action = new Action();
        action.setActorId("agent_1");
        action.setActionType(ActionType.ACCUSE);

        TimeLocationEvent event1 = TimeLocationEvent.builder()
                .timeStep(2)
                .sequenceNumber(1)
                .locationId("room_1")
                .locationName("The Library")
                .actions(List.of(action))
                .dramaScore(8.0)
                .build();

        Scene scene = Scene.builder()
                .sceneNumber(1)
                .startStep(2)
                .endStep(2)
                .primaryLocationId("room_1")
                .primaryLocationName("The Library")
                .dramaticFocus("A dramatic confrontation unfolds")
                .firstEvent(event1)
                .lastEvent(event1)
                .events(List.of(event1))
                .category(SceneCategory.ESSENTIAL)
                .narrativeImportanceScore(8.0)
                .suspenseScore(7.0)
                .totalEditorialScore(7.6)
                .isSelected(true)
                .build();

        List<Chapter> batchChapters = new ArrayList<>();
        proseService.setOnChapterGenerated(batchChapters::add);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean completed = new AtomicBoolean(false);

        proseService.generateProseForScenes(List.of(scene), () -> {
            completed.set(true);
            latch.countDown();
        });

        boolean finishedInTime = latch.await(5, TimeUnit.SECONDS);
        assertTrue(finishedInTime);
        assertTrue(completed.get());
        assertEquals(1, batchChapters.size());
        assertEquals(1, batchChapters.get(0).sceneNumber());
    }

    @Test
    void testBuildProsePromptFromTemplate() {
        Action action = new Action();
        action.setActorId("agent_1");
        action.setActionType(ActionType.SEARCH);
        action.setDetails("Searching through old archives");

        TimeLocationEvent event1 = TimeLocationEvent.builder()
                .timeStep(1)
                .sequenceNumber(1)
                .locationId("room_1")
                .locationName("The Library")
                .actions(List.of(action))
                .dramaScore(5.0)
                .build();

        Scene scene = Scene.builder()
                .sceneNumber(3)
                .startStep(1)
                .endStep(1)
                .primaryLocationId("room_1")
                .primaryLocationName("The Library")
                .dramaticFocus("Discovery of a classified letter")
                .firstEvent(event1)
                .lastEvent(event1)
                .events(List.of(event1))
                .category(SceneCategory.SUPPORTING)
                .narrativeImportanceScore(6.0)
                .suspenseScore(5.5)
                .totalEditorialScore(5.8)
                .isSelected(true)
                .build();

        proseService.setContext("Murder at Midnight", "A stormy evening", "Agatha Christie", "Medium");
        String prompt = proseService.buildProsePrompt(scene);

        assertNotNull(prompt);
        assertTrue(prompt.contains("DRAMATIC FOCUS: \"Discovery of a classified letter\""));
        assertTrue(prompt.contains("PRIMARY SETTING: \"The Library\""));
        assertTrue(prompt.contains("Story Title: \"Murder at Midnight\""));
        assertTrue(prompt.contains("Agatha Christie"));
        assertTrue(prompt.contains("OPENING HOOK: Establish the scene starting at The Library"));
        assertTrue(prompt.contains("Searching through old archives"));
        assertTrue(prompt.contains("=== STORY REFERENCE DIRECTORY (ID LOOKUP) ==="));
        assertTrue(prompt.contains("agent_1: Arthur"));
        assertTrue(prompt.contains("room_1: The Library"));
        assertTrue(prompt.contains("=== CHARACTERS PRESENT IN THIS SCENE ==="));
        assertTrue(prompt.contains("STRICT CLOSED-CAST ENSEMBLE"));
    }

    @Test
    void testProsePromptDistinguishesPresentFromAbsentCharacters() {
        Agent arthur = repository.getAgents().get("agent_1");
        arthur.setCurrentLocationId("room_1");

        Agent elena = new Agent();
        elena.setId("agent_2");
        elena.setName("Elena");
        elena.setRole("Victim");
        elena.setCurrentLocationId("room_2");
        elena.setAlive(true);

        Location lounge = new Location();
        lounge.setId("room_2");
        lounge.setName("The Lounge");

        repository.getAgents().put(elena.getId(), elena);
        repository.getLocations().put(lounge.getId(), lounge);

        Action action = new Action();
        action.setActorId("agent_1");
        action.setActionType(ActionType.SEARCH);

        TimeLocationEvent event1 = TimeLocationEvent.builder()
                .timeStep(1)
                .sequenceNumber(1)
                .locationId("room_1")
                .locationName("The Library")
                .actions(List.of(action))
                .build();

        Scene scene = Scene.builder()
                .sceneNumber(1)
                .startStep(1)
                .endStep(1)
                .primaryLocationId("room_1")
                .primaryLocationName("The Library")
                .dramaticFocus("Arthur searches the library")
                .firstEvent(event1)
                .lastEvent(event1)
                .events(List.of(event1))
                .build();

        String prompt = proseService.buildProsePrompt(scene);

        assertNotNull(prompt);
        assertTrue(prompt.contains("=== CHARACTERS PRESENT IN THIS SCENE ==="));
        assertTrue(prompt.contains("agent_1: Arthur"));
        assertTrue(prompt.contains("Physically PRESENT in this scene"));

        assertTrue(prompt.contains("=== OTHER CHARACTERS (LOCATED ELSEWHERE OR DECEASED - NOT IN THIS SCENE) ==="));
        assertTrue(prompt.contains("agent_2: Elena"));
        assertTrue(prompt.contains("Currently in The Lounge"));
        assertTrue(prompt.contains("Physically in another area. CANNOT appear, be seen, or interact in this scene!"));
    }

    @Test
    void testEnsureOrSynthesizeTimelineEvents() {
        // Setup repository with snapshots but no timeline events
        repository.setCurrentStep(2);
        repository.takeSnapshot();

        assertTrue(repository.getAllRecordedEvents().isEmpty());

        List<TimeLocationEvent> synthesized = repository.ensureOrSynthesizeTimelineEvents();
        assertFalse(synthesized.isEmpty());
        assertEquals(synthesized.size(), repository.getAllRecordedEvents().size());
        assertEquals("The Library", synthesized.get(0).getLocationName());
    }

    @Test
    void testShouldGenerateResolution() {
        assertFalse(proseService.shouldGenerateResolution());

        repository.setCompleted(true);
        assertTrue(proseService.shouldGenerateResolution());

        repository.setCompleted(false);
        Agent victim = new Agent();
        victim.setId("agent_victim");
        victim.setName("Victim Person");
        victim.setAlive(false);
        repository.getAgents().put("agent_victim", victim);
        assertTrue(proseService.shouldGenerateResolution());
    }

    @Test
    void testGenerateResolutionChapter() {
        Agent detective = new Agent();
        detective.setId("agent_0");
        detective.setName("Inspector Poirot");
        detective.setRole("Detective");
        // role Detective sets isDetective
        detective.setAlive(true);
        repository.getAgents().put("agent_0", detective);

        Agent killer = new Agent();
        killer.setId("agent_killer");
        killer.setName("Col. Mustard");
        killer.setRole("Guest");
        killer.setHasMurderIntent(true);
        killer.setSecretGoal("Kill the host and steal inheritance");
        killer.setAlive(true);
        repository.getAgents().put("agent_killer", killer);

        Item wrench = new Item();
        wrench.setId("item_wrench");
        wrench.setName("Heavy Wrench");
        wrench.setType(ItemType.WEAPON);
        repository.getItems().put("item_wrench", wrench);

        Chapter resolution = proseService.generateResolutionChapter(10, 50);

        assertNotNull(resolution);
        assertEquals(10, resolution.sceneNumber());
        assertEquals(50, resolution.timeStep());
        assertEquals(SceneCategory.ESSENTIAL, resolution.category());
        assertTrue(resolution.isPivotal());
        assertTrue(resolution.isSelected());
        assertEquals("The Grand Reveal & Resolution", resolution.dramaticFocus());
        assertEquals(10.0, resolution.importanceScore());
        assertEquals(10.0, resolution.suspenseScore());
        assertNotNull(resolution.text());
        assertFalse(resolution.text().isBlank());
    }

    @Test
    void testGenerateProseForScenesAppendsResolutionWhenCompleted() throws InterruptedException {
        repository.setCompleted(true);

        Agent detective = new Agent();
        detective.setId("agent_0");
        detective.setName("Inspector");
        detective.setAlive(true);
        repository.getAgents().put("agent_0", detective);

        Action action = new Action();
        action.setActorId("agent_0");
        action.setActionType(ActionType.SEARCH);

        TimeLocationEvent event1 = TimeLocationEvent.builder()
                .timeStep(1)
                .sequenceNumber(1)
                .locationId("room_1")
                .locationName("The Library")
                .actions(List.of(action))
                .build();

        Scene scene = Scene.builder()
                .sceneNumber(1)
                .startStep(1)
                .endStep(1)
                .primaryLocationId("room_1")
                .primaryLocationName("The Library")
                .dramaticFocus("Investigation starts")
                .firstEvent(event1)
                .lastEvent(event1)
                .events(List.of(event1))
                .category(SceneCategory.SUPPORTING)
                .narrativeImportanceScore(5.0)
                .suspenseScore(5.0)
                .totalEditorialScore(5.0)
                .isSelected(true)
                .build();

        List<Chapter> chapters = new ArrayList<>();
        proseService.setOnChapterGenerated(chapters::add);

        CountDownLatch latch = new CountDownLatch(1);
        proseService.generateProseForScenes(List.of(scene), latch::countDown);

        boolean finished = latch.await(5, TimeUnit.SECONDS);
        assertTrue(finished);
        assertEquals(2, chapters.size(), "Should have the scene chapter plus the resolution chapter");
        assertEquals("Investigation starts", chapters.get(0).dramaticFocus());
        assertEquals("The Grand Reveal & Resolution", chapters.get(1).dramaticFocus());
    }

    @Test
    void testChapterToSceneAndChaptersToScenes() {
        Chapter c1 = new Chapter(
                null,
                true,
                false,
                2,
                1,
                "The Library",
                9.0,
                8.0,
                SceneCategory.ESSENTIAL,
                "Discovery of Body",
                1,
                2,
                "Steps 1-2",
                true
        );
        Chapter c2 = new Chapter(
                "Some prose",
                false,
                false,
                4,
                2,
                "The Hall",
                5.0,
                4.0,
                SceneCategory.SUPPORTING,
                "Questioning",
                3,
                4,
                "Steps 3-4",
                true
        );

        Scene s1 = proseService.chapterToScene(c1);
        assertNotNull(s1);
        assertEquals(1, s1.getSceneNumber());
        assertEquals(1, s1.getStartStep());
        assertEquals(2, s1.getEndStep());
        assertEquals("The Library", s1.getPrimaryLocationName());
        assertEquals("Discovery of Body", s1.getDramaticFocus());
        assertEquals(SceneCategory.ESSENTIAL, s1.getCategory());
        assertTrue(s1.isSelected());

        List<Scene> scenes = proseService.chaptersToScenes(List.of(c1, c2));
        assertEquals(2, scenes.size());
        assertEquals("Discovery of Body", scenes.get(0).getDramaticFocus());
        assertEquals("Questioning", scenes.get(1).getDramaticFocus());
    }

    @Test
    void testRegenerateProseCountersReset() throws InterruptedException {
        Scene scene = Scene.builder()
                .sceneNumber(1)
                .startStep(1)
                .endStep(1)
                .primaryLocationId("room_1")
                .primaryLocationName("The Library")
                .dramaticFocus("Scene 1")
                .category(SceneCategory.SUPPORTING)
                .narrativeImportanceScore(5.0)
                .suspenseScore(5.0)
                .isSelected(true)
                .build();

        CountDownLatch latch1 = new CountDownLatch(1);
        proseService.generateProseForScenes(List.of(scene), latch1::countDown);
        assertTrue(latch1.await(5, TimeUnit.SECONDS));

        // Now regenerate prose for the scene
        CountDownLatch latch2 = new CountDownLatch(1);
        proseService.generateProseForScenes(List.of(scene), latch2::countDown);
        assertTrue(latch2.await(5, TimeUnit.SECONDS));

        // After regeneration, queue metrics should reflect the second run, not accumulate
        assertEquals(1, proseService.getTotalScenesEnqueued());
    }

    @Test
    void testSnapshotBasedVitalityInProsePromptParams() {
        Agent victim = repository.getAgents().get("agent_1");
        assertNotNull(victim);

        // Snapshot at step 5: victim is alive
        victim.setAlive(true);
        AgentSnapshot snapAlive = victim.snapshot();
        SimulationStateSnapshot stateSnapStep5 = new SimulationStateSnapshot(5, Map.of(victim.getId(), snapAlive), Map.of(), Map.of(), List.of());
        repository.getSnapshots().put(5, stateSnapStep5);

        // Snapshot at step 20: victim is deceased
        victim.setAlive(false);
        AgentSnapshot snapDead = victim.snapshot();
        SimulationStateSnapshot stateSnapStep20 = new SimulationStateSnapshot(20, Map.of(victim.getId(), snapDead), Map.of(), Map.of(), List.of());
        repository.getSnapshots().put(20, stateSnapStep20);

        // Even if live state is currently dead:
        victim.setAlive(false);

        // Scene at step 5 should report victim as ALIVE because they were alive at step 5
        Scene earlyScene = Scene.builder()
                .sceneNumber(1)
                .startStep(5)
                .endStep(7)
                .primaryLocationId("room_1")
                .primaryLocationName("The Library")
                .events(List.of())
                .build();
        var earlyParams = proseService.buildProsePromptParams(earlyScene);
        String earlyChars = (String) earlyParams.get("charactersList");
        assertTrue(earlyChars.contains(victim.getName()));
        assertTrue(earlyChars.contains("[ALIVE]"));
        assertFalse(earlyChars.contains("[DECEASED]"));

        // Scene where murder occurs in the events list
        Action murderAction = new Action();
        murderAction.setActorId("agent_0");
        murderAction.setActionType(ActionType.MURDER);
        murderAction.setTargetAgentId(victim.getId());

        TimeLocationEvent murderEvent = TimeLocationEvent.builder()
                .timeStep(10)
                .locationId("room_1")
                .locationName("The Library")
                .actions(List.of(murderAction))
                .build();

        Scene murderScene = Scene.builder()
                .sceneNumber(2)
                .startStep(5)
                .endStep(10)
                .primaryLocationId("room_1")
                .primaryLocationName("The Library")
                .events(List.of(murderEvent))
                .build();
        var murderParams = proseService.buildProsePromptParams(murderScene);
        String murderChars = (String) murderParams.get("charactersList");
        assertTrue(murderChars.contains("ALIVE AT START OF SCENE -> BECOMES VICTIM / DIES IN THIS SCENE"));

        // Scene at step 20 should report victim as DECEASED
        Scene lateScene = Scene.builder()
                .sceneNumber(3)
                .startStep(20)
                .endStep(25)
                .primaryLocationId("room_1")
                .primaryLocationName("The Library")
                .events(List.of())
                .build();
        var lateParams = proseService.buildProsePromptParams(lateScene);
        String lateChars = (String) lateParams.get("charactersList");
        assertTrue(lateChars.contains("[DECEASED]"));
    }

    @Test
    void testPreviousChaptersContextInProsePromptParams() {
        Chapter ch1 = new Chapter(
                "Viktor examined the mahogany desk. Silence fell over the salon.",
                true, false, 1, 1, "Dining Salon", 5.0, 5.0, SceneCategory.ATMOSPHERIC, "Pacing", 1, 1, "00:00 - 00:15", true
        );
        proseService.addGeneratedChapter(ch1);

        String context = proseService.buildPreviousChaptersContext();
        assertNotNull(context);
        assertTrue(context.contains("Chapter 1 [Dining Salon | 00:00 - 00:15]: Focus was \"Pacing\""));
        assertTrue(context.contains("Ending: \"Silence fell over the salon.\""));

        Scene scene = Scene.builder()
                .sceneNumber(2)
                .startStep(2)
                .endStep(3)
                .primaryLocationId("room_1")
                .primaryLocationName("The Library")
                .events(List.of())
                .build();
        var params = proseService.buildProsePromptParams(scene);
        assertTrue(params.containsKey("previousChaptersContext"));
        assertTrue(((String) params.get("previousChaptersContext")).contains("Chapter 1"));
    }

    @Test
    void testResolutionChapterForensicCrimeDetails() {
        Agent killer = new Agent();
        killer.setId("agent_killer");
        killer.setName("Dr. Thorne");
        killer.setRole("Chief Medical Officer");
        killer.setSecretGoal("Murder Mira Solis because of illegal organ harvesting");
        killer.setAlive(true);
        killer.setHasMurderIntent(true);
        repository.getAgents().put(killer.getId(), killer);

        Agent victim = new Agent();
        victim.setId("agent_victim");
        victim.setName("Mira Solis");
        victim.setRole("Hydroponics Lead");
        victim.setAlive(false);
        repository.getAgents().put(victim.getId(), victim);

        Item dagger = new Item();
        dagger.setId("item_dagger");
        dagger.setName("Ceremonial Dagger");
        dagger.setType(ItemType.WEAPON);
        repository.getItems().put(dagger.getId(), dagger);

        Action murder = new Action();
        murder.setActorId(killer.getId());
        murder.setActionType(ActionType.MURDER);
        murder.setTargetAgentId(victim.getId());
        murder.setTargetItemId(dagger.getId());
        murder.setDetails("Stabbed Mira Solis with the Ceremonial Dagger in the Engineering Ring");

        TimeLocationEvent murderEvent = TimeLocationEvent.builder()
                .timeStep(37)
                .locationId("room_3")
                .locationName("The Engineering Ring")
                .actions(List.of(murder))
                .build();
        repository.getAllRecordedEvents().add(murderEvent);

        Chapter resolution = proseService.generateResolutionChapter(15, 60);
        assertNotNull(resolution);
        assertEquals("The Grand Reveal & Resolution", resolution.dramaticFocus());
    }
}