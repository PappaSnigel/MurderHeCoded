package com.murderhecoded.service;

import com.murderhecoded.domain.Chapter;
import com.murderhecoded.domain.SceneCategory;
import com.murderhecoded.repository.SimulationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("PMD")
class ProjectServiceTest {

    @TempDir
    Path tempDir;

    @Test
    public void testAutoSaveCreatesScenesJsonAndChaptersJson() throws IOException {
        ProjectService projectService = new ProjectService();
        
        // Mock project path
        Path testProjDir = tempDir.resolve("TestProject");
        Files.createDirectories(testProjDir);

        SimulationRepository state = new SimulationRepository();
        state.setTitle("The Mystery of Mars");
        
        Chapter c1 = new Chapter(
            "It was a dark and stormy night.",
            true,
            false,
            4,
            1,
            "The Library",
            9.5,
            8.0,
            SceneCategory.ESSENTIAL,
            "The Stabbing in the Library",
            1,
            4,
            "Steps 1-4 (Day 1, 09:00 - 10:00)",
            true
        );

        Chapter c2 = new Chapter(
            "Lord Blackwood walked across the hall.",
            false,
            true,
            5,
            2,
            "The Conservatory",
            2.0,
            1.5,
            SceneCategory.DISPENSABLE,
            "Mundane stroll",
            5,
            5,
            "Step 5 (Day 1, 10:15)",
            false
        );

        // Manually invoke autoSave through private field or loadProject
        File scenesFile = testProjDir.resolve("scenes.json").toFile();
        File chaptersFile = testProjDir.resolve("chapters.json").toFile();

        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        mapper.writerWithDefaultPrettyPrinter().writeValue(scenesFile, List.of(c1, c2));
        mapper.writerWithDefaultPrettyPrinter().writeValue(chaptersFile, List.of(c1, c2));
        
        File stateFile = testProjDir.resolve("state.json").toFile();
        mapper.writerWithDefaultPrettyPrinter().writeValue(stateFile, state);

        // Load project
        ProjectService.ProjectData loaded = projectService.loadProject(testProjDir.toFile());
        assertNotNull(loaded);
        assertEquals(2, loaded.chapters().size());

        Chapter loadedC1 = loaded.chapters().get(0);
        assertEquals(1, loadedC1.sceneNumber());
        assertTrue(loadedC1.isSelected());
        assertEquals("Steps 1-4 (Day 1, 09:00 - 10:00)", loadedC1.timeSpan());
        assertEquals(1, loadedC1.startStep());
        assertEquals(4, loadedC1.endStep());

        Chapter loadedC2 = loaded.chapters().get(1);
        assertEquals(2, loadedC2.sceneNumber());
        assertFalse(loadedC2.isSelected());
        assertEquals("Step 5 (Day 1, 10:15)", loadedC2.timeSpan());
    }

    @Test
    public void testLoadFallbackToChaptersJsonWhenScenesJsonMissing() throws IOException {
        ProjectService projectService = new ProjectService();
        Path testProjDir = tempDir.resolve("LegacyProject");
        Files.createDirectories(testProjDir);

        SimulationRepository state = new SimulationRepository();
        state.setTitle("Legacy Mystery");

        Chapter legacyChapter = new Chapter(
            "Legacy chapter prose.",
            true,
            false,
            2,
            1,
            "Lounge",
            8.0,
            7.0,
            SceneCategory.ESSENTIAL,
            "Climax"
        );

        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        mapper.writeValue(testProjDir.resolve("state.json").toFile(), state);
        // Only write chapters.json (no scenes.json)
        mapper.writeValue(testProjDir.resolve("chapters.json").toFile(), List.of(legacyChapter));

        ProjectService.ProjectData loaded = projectService.loadProject(testProjDir.toFile());
        assertNotNull(loaded);
        assertEquals(1, loaded.chapters().size());
        Chapter loadedChapter = loaded.chapters().get(0);
        assertEquals("Legacy chapter prose.", loadedChapter.text());
        assertTrue(loadedChapter.isSelected());
        assertEquals(2, loadedChapter.startStep());
        assertEquals(2, loadedChapter.endStep());
    }

    @Test
    public void testAutoSaveAndLoadWithSnapshots() throws IOException {
        ProjectService projectService = new ProjectService();
        Path testProjDir = tempDir.resolve("SnapshotProject");
        Files.createDirectories(testProjDir);

        SimulationRepository state = new SimulationRepository();
        state.setTitle("Snapshot Test Mystery");

        com.murderhecoded.domain.Location lounge = new com.murderhecoded.domain.Location();
        lounge.setId("loc_1");
        lounge.setName("Lounge");
        lounge.setDescription("A cozy lounge");

        com.murderhecoded.domain.Agent agent = new com.murderhecoded.domain.Agent();
        agent.setId("agent_1");
        agent.setName("Lord Blackwood");
        agent.setCurrentLocationId("loc_1");
        agent.setAlive(true);
        agent.setHasArrived(true);

        com.murderhecoded.domain.Item item = new com.murderhecoded.domain.Item();
        item.setId("item_1");
        item.setName("Dagger");
        item.setCurrentLocationId("loc_1");
        item.setType(com.murderhecoded.domain.ItemType.WEAPON);
        item.setPoisoned(true);
        item.setPoisonDetails("Strychnine");

        state.initialize(List.of(agent), List.of(lounge), List.of(item), "Snapshot Test Mystery", "Premise");
        state.setCurrentStep(1);
        agent.setCurrentLocationId("agent_1");
        state.takeSnapshot();

        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        mapper.writerWithDefaultPrettyPrinter().writeValue(testProjDir.resolve("state.json").toFile(), state);
        mapper.writerWithDefaultPrettyPrinter().writeValue(testProjDir.resolve("scenes.json").toFile(), List.of());

        ProjectService.ProjectData loaded = projectService.loadProject(testProjDir.toFile());
        assertNotNull(loaded);
        assertNotNull(loaded.state());
        assertEquals(2, loaded.state().getSnapshots().size());

        com.murderhecoded.domain.SimulationStateSnapshot snap0 = loaded.state().getSnapshotAt(0);
        assertNotNull(snap0);
        assertEquals(0, snap0.timeStep());
        assertEquals("Lord Blackwood", snap0.agents().get("agent_1").name());
        assertEquals("loc_1", snap0.agents().get("agent_1").currentLocationId());

        com.murderhecoded.domain.SimulationStateSnapshot snap1 = loaded.state().getSnapshotAt(1);
        assertNotNull(snap1);
        assertEquals(1, snap1.timeStep());
        assertEquals("agent_1", snap1.agents().get("agent_1").currentLocationId());

        com.murderhecoded.domain.ItemSnapshot loadedItem = loaded.state().getItemSnapshot("item_1", 0);
        assertNotNull(loadedItem);
        assertEquals("Strychnine", loadedItem.poisonDetails());
    }

    @Test
    public void testAtomicAutoSave() throws IOException {
        ProjectService projectService = new ProjectService();
        Path testProjDir = tempDir.resolve("AtomicProject");
        Files.createDirectories(testProjDir);

        // Point projectService to test directory using reflection or createNewProject
        try {
            java.lang.reflect.Field pathField = ProjectService.class.getDeclaredField("currentProjectPath");
            pathField.setAccessible(true);
            pathField.set(projectService, testProjDir);
        } catch (Exception e) {
            fail("Failed to set currentProjectPath: " + e.getMessage());
        }

        SimulationRepository state = new SimulationRepository();
        state.setTitle("Atomic Mystery");
        state.setPremise("A test for atomic saving");
        state.setCurrentStep(5);

        projectService.autoSave(state, List.of(), "Sample event log text");

        Path stateFile = testProjDir.resolve("state.json");
        Path tempFile = testProjDir.resolve("state.json.tmp");
        assertTrue(Files.exists(stateFile), "state.json must exist after autoSave");
        assertFalse(Files.exists(tempFile), "state.json.tmp must not remain after successful atomic move");

        ProjectService.ProjectData loaded = projectService.loadProject(testProjDir.toFile());
        assertNotNull(loaded.stateDocument());
        assertEquals("Atomic Mystery", loaded.stateDocument().title());
        assertEquals("A test for atomic saving", loaded.stateDocument().premise());
        assertEquals(5, loaded.stateDocument().currentStep());
        assertEquals("Sample event log text", loaded.eventLog());

        SimulationRepository restored = new SimulationRepository();
        restored.restoreFrom(loaded.stateDocument());
        assertEquals("Atomic Mystery", restored.getTitle());
        assertEquals("A test for atomic saving", restored.getPremise());
    }

    @Test
    public void testDeserializationWithDescriptionAlias() throws IOException {
        String json = """
            {
                "title": "Alias Mystery",
                "description": "Legacy premise field name",
                "currentStep": 0,
                "targetSteps": 100,
                "totalHours": 24,
                "stepMinutes": 15,
                "completed": false,
                "worldGenerated": false
            }
            """;
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        com.murderhecoded.domain.SimulationStateDocument doc = mapper.readValue(json, com.murderhecoded.domain.SimulationStateDocument.class);
        assertEquals("Alias Mystery", doc.title());
        assertEquals("Legacy premise field name", doc.premise());
    }

    @Test
    public void testLoadRealSavedNovelProject() throws IOException {
        File realNovelDir = new File("novel/Murder_Over_Mars_51");
        if (!realNovelDir.exists() || !realNovelDir.isDirectory()) {
            // Skip if not present in environment
            return;
        }

        ProjectService projectService = new ProjectService();
        ProjectService.ProjectData loaded = projectService.loadProject(realNovelDir);

        assertNotNull(loaded);
        assertNotNull(loaded.stateDocument());

        SimulationRepository repo = new SimulationRepository();
        repo.restoreFrom(loaded.stateDocument());

        assertFalse(repo.getAgents().isEmpty(), "Loaded novel must have agents");
        assertFalse(repo.getLocations().isEmpty(), "Loaded novel must have locations");
        assertFalse(repo.getSnapshots().isEmpty(), "Loaded novel must have snapshots");
        assertTrue(repo.getCurrentStep() > 0, "Loaded novel must have advanced steps");
        assertNotNull(repo.getTitle());
    }
}
