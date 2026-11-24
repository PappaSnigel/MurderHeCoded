package com.murderhecoded.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.murderhecoded.domain.*;
import com.murderhecoded.llm.LlmExecutor;
import com.murderhecoded.llm.LlmRole;
import com.murderhecoded.repository.SimulationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class LlmSceneDirectorServiceTest {

    private OllamaLlmService mockOllama;
    private LlmExecutor llmExecutor;
    private ObjectMapper objectMapper;
    private LlmSceneDirectorService directorService;
    private SimulationRepository repository;

    @BeforeEach
    void setUp() {
        mockOllama = Mockito.mock(OllamaLlmService.class);
        llmExecutor = new LlmExecutor();
        objectMapper = new ObjectMapper();
        directorService = new LlmSceneDirectorService(mockOllama, llmExecutor, objectMapper);

        String template = "Title: {title}\nVoice: {voice}\nScene #{sceneNumber} in {locationName} [{startTime} - {endTime}]\n" +
                "Characters:\n{charactersSummary}\nEvents:\n{eventsSummary}";
        ReflectionTestUtils.setField(directorService, "promptResource", new ByteArrayResource(template.getBytes()));

        repository = new SimulationRepository();
        Agent a1 = new Agent();
        a1.setId("agent_1");
        a1.setName("Arthur");
        a1.setRole("Doctor");
        a1.setAlive(true);

        Location loc = new Location();
        loc.setId("loc_1");
        loc.setName("The Study");

        repository.initialize(List.of(a1), List.of(loc), List.of(), "The Study Mystery", "A stormy night");
    }

    @Test
    void testEnrichSceneWithValidDirectorJson() {
        String mockJsonResponse = """
            {
              "dramaticFocus": "Arthur searches the mahogany desk for the missing vial of strychnine while thunder rumbles outside.",
              "openingHook": "Start with Arthur's trembling hands rattling the bronze desk key.",
              "sceneButton": "End with the creak of floorboards in the corridor behind him.",
              "subtextAndSecrets": [
                "Arthur is concealing the antidote in his waistcoat pocket"
              ],
              "directorNotes": "Slow, tense pacing with heavy atmospheric rain effects.",
              "recommendedStartStep": 1,
              "recommendedEndStep": 3,
              "narrativeWeight": "ESSENTIAL",
              "dramaticScore": 9.2
            }
            """;

        when(mockOllama.generateText(any(), eq(LlmRole.DIRECTOR))).thenReturn(mockJsonResponse);

        Scene scene = Scene.builder()
                .sceneNumber(1)
                .startStep(1)
                .endStep(3)
                .primaryLocationName("The Study")
                .dramaticFocus("Generic Action")
                .dramaScore(4.0)
                .category(SceneCategory.SUPPORTING)
                .events(new ArrayList<>())
                .build();

        directorService.enrichScene(scene, repository, "Agatha Christie", "The Study Mystery", "A stormy night");

        assertEquals("Arthur searches the mahogany desk for the missing vial of strychnine while thunder rumbles outside.", scene.getDramaticFocus());
        assertEquals("Start with Arthur's trembling hands rattling the bronze desk key.", scene.getOpeningHook());
        assertEquals("End with the creak of floorboards in the corridor behind him.", scene.getSceneButton());
        assertEquals("Slow, tense pacing with heavy atmospheric rain effects.", scene.getDirectorNotes());
        assertEquals(SceneCategory.ESSENTIAL, scene.getCategory());
        assertEquals(9.2, scene.getDramaScore(), 0.01);
        assertEquals(1, scene.getSubtext().size());
        assertEquals("Arthur is concealing the antidote in his waistcoat pocket", scene.getSubtext().get(0));
    }

    @Test
    void testEnrichSceneGracefullyHandlesMalformedJson() {
        when(mockOllama.generateText(any(), eq(LlmRole.DIRECTOR))).thenReturn("Sorry, I could not generate JSON.");

        Scene scene = Scene.builder()
                .sceneNumber(2)
                .startStep(4)
                .endStep(6)
                .primaryLocationName("The Kitchen")
                .dramaticFocus("Algorithmic Tense Exchange")
                .dramaScore(5.5)
                .category(SceneCategory.SUPPORTING)
                .events(new ArrayList<>())
                .build();

        directorService.enrichScene(scene, repository, "Default", "The Study Mystery", "A stormy night");

        // Should retain algorithmic defaults without throwing
        assertEquals("Algorithmic Tense Exchange", scene.getDramaticFocus());
        assertEquals(5.5, scene.getDramaScore(), 0.01);
        assertEquals(SceneCategory.SUPPORTING, scene.getCategory());
    }

    @Test
    void testEditorServiceHybridDelegation() {
        EditorService editorService = new EditorService();
        editorService.setDirectorService(directorService);

        String mockJsonResponse = """
            ```json
            {
              "dramaticFocus": "Elevated Director Focus",
              "openingHook": "Opening line",
              "sceneButton": "Closing line",
              "directorNotes": "Keep it fast",
              "subtextAndSecrets": ["Hidden note"],
              "narrativeWeight": "ESSENTIAL",
              "dramaticScore": 8.5
            }
            ```
            """;
        when(mockOllama.generateText(any(), eq(LlmRole.DIRECTOR))).thenReturn(mockJsonResponse);

        TimeLocationEvent ev = new TimeLocationEvent();
        ev.setTimeStep(1);
        ev.setLocationId("loc_1");
        ev.setLocationName("The Study");
        Action a = new Action();
        a.setActionType(ActionType.SEARCH);
        a.setActorId("agent_1");
        ev.setActions(List.of(a));

        List<Scene> result = editorService.createScenes(
                List.of(ev),
                repository,
                true,
                "Conan Doyle",
                "Study Mystery",
                "Stormy night",
                null,
                null
        );

        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertEquals("Elevated Director Focus", result.get(0).getDramaticFocus());
        assertEquals("Keep it fast", result.get(0).getDirectorNotes());
    }
}
