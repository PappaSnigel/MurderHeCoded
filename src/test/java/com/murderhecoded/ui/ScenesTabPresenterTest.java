package com.murderhecoded.ui;

import com.murderhecoded.domain.Action;
import com.murderhecoded.domain.ActionType;
import com.murderhecoded.domain.Chapter;
import com.murderhecoded.domain.SceneCategory;
import com.murderhecoded.domain.TimeLocationEvent;
import com.murderhecoded.repository.SimulationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScenesTabPresenterTest {

    @Test
    @DisplayName("Filter chapters by search term across focus, location, and text")
    void testFilterChaptersBySearchTerm() {
        Chapter c1 = new Chapter("Prose in the library", true, false, 3, 1, "Library", 8.5, 7.0, SceneCategory.ESSENTIAL, "Murder Investigation");
        Chapter c2 = new Chapter("Tea in the garden", false, true, 6, 2, "Garden", 2.0, 1.5, SceneCategory.DISPENSABLE, "Casual Gossip");
        Chapter c3 = new Chapter("Secret letter found in Study", false, false, 9, 3, "Study", 6.0, 5.0, SceneCategory.SUPPORTING, "Clue Search");

        List<Chapter> chapters = List.of(c1, c2, c3);

        List<Chapter> searchGarden = ScenesTabPresenter.filterChapters(chapters, "garden", "All Categories", "All Scenes");
        assertEquals(1, searchGarden.size());
        assertEquals("Garden", searchGarden.get(0).locationName());

        List<Chapter> searchClue = ScenesTabPresenter.filterChapters(chapters, "Clue", "All Categories", "All Scenes");
        assertEquals(1, searchClue.size());
        assertEquals("Study", searchClue.get(0).locationName());

        List<Chapter> searchProse = ScenesTabPresenter.filterChapters(chapters, "library", "All Categories", "All Scenes");
        assertEquals(1, searchProse.size());
        assertEquals(1, searchProse.get(0).sceneNumber());
    }

    @Test
    @DisplayName("Filter chapters by category and final cut status")
    void testFilterChaptersByCategoryAndCut() {
        Chapter c1 = new Chapter("Essential prose", true, false, 3, 1, "Library", 9.0, 8.0, SceneCategory.ESSENTIAL, "Climax Focus", 1, 3, "Steps 1-3", true);
        Chapter c2 = new Chapter("Supporting prose", false, false, 6, 2, "Kitchen", 5.5, 4.0, SceneCategory.SUPPORTING, "Questioning", 4, 6, "Steps 4-6", true);
        Chapter c3 = new Chapter("Dispensable fluff", false, true, 9, 3, "Garden", 1.5, 1.0, SceneCategory.DISPENSABLE, "Weather talk", 7, 9, "Steps 7-9", false);

        List<Chapter> chapters = List.of(c1, c2, c3);

        // Category filter
        List<Chapter> essentialOnly = ScenesTabPresenter.filterChapters(chapters, "", "ESSENTIAL", "All Scenes");
        assertEquals(1, essentialOnly.size());
        assertEquals(SceneCategory.ESSENTIAL, essentialOnly.get(0).category());

        // Cut filter: Final Cut Only
        List<Chapter> finalCut = ScenesTabPresenter.filterChapters(chapters, "", "All Categories", "Final Cut Only");
        assertEquals(2, finalCut.size());
        assertTrue(finalCut.stream().allMatch(Chapter::isSelected));

        // Cut filter: Cut / Fluff Only
        List<Chapter> cutOnly = ScenesTabPresenter.filterChapters(chapters, "", "All Categories", "Cut / Fluff Only");
        assertEquals(1, cutOnly.size());
        assertFalse(cutOnly.get(0).isSelected());
        assertEquals("Garden", cutOnly.get(0).locationName());
    }

    @Test
    @DisplayName("getOrSynthesizeChapters returns existing chapters if present")
    void testGetOrSynthesizeChaptersWithExisting() {
        Chapter c1 = new Chapter("Text", true, false, 1, 1, "Hall", 8.0, 7.0, SceneCategory.ESSENTIAL, "Opening");
        SimulationRepository state = new SimulationRepository();

        List<Chapter> result = ScenesTabPresenter.getOrSynthesizeChapters(List.of(c1), state);
        assertEquals(1, result.size());
        assertEquals("Hall", result.get(0).locationName());
    }

    @Test
    @DisplayName("getOrSynthesizeChapters synthesizes scenes from timeline events when chapters is empty")
    void testGetOrSynthesizeChaptersSynthesizesFromEvents() {
        SimulationRepository state = new SimulationRepository();
        state.setCurrentStep(5);

        com.murderhecoded.domain.Location loc = new com.murderhecoded.domain.Location();
        loc.setId("room_ballroom");
        loc.setName("Ballroom");
        state.getLocations().put("room_ballroom", loc);

        Action murder = new Action();
        murder.setActorId("agent_murderer");
        murder.setActionType(ActionType.MURDER);
        murder.setDetails("Poisoned drink");

        TimeLocationEvent ev1 = TimeLocationEvent.builder()
                .timeStep(1)
                .locationId("room_ballroom")
                .locationName("Ballroom")
                .actions(new ArrayList<>(List.of(murder)))
                .build();

        TimeLocationEvent ev2 = TimeLocationEvent.builder()
                .timeStep(2)
                .locationId("room_ballroom")
                .locationName("Ballroom")
                .actions(new ArrayList<>())
                .build();

        state.recordTimelineEvents(1, List.of(ev1));
        state.recordTimelineEvents(2, List.of(ev2));

        List<Chapter> synthesized = ScenesTabPresenter.getOrSynthesizeChapters(Collections.emptyList(), state);
        assertNotNull(synthesized);
        assertFalse(synthesized.isEmpty());
        Chapter scene = synthesized.get(0);
        assertEquals("Ballroom", scene.locationName());
        assertTrue(scene.importanceScore() > 0);
    }
}
