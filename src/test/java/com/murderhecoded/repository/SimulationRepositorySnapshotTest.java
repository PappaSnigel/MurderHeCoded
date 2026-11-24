package com.murderhecoded.repository;

import com.murderhecoded.domain.Agent;
import com.murderhecoded.domain.AgentSnapshot;
import com.murderhecoded.domain.Item;
import com.murderhecoded.domain.ItemSnapshot;
import com.murderhecoded.domain.ItemType;
import com.murderhecoded.domain.Location;
import com.murderhecoded.domain.LocationSnapshot;
import com.murderhecoded.domain.SimulationStateSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("PMD")
class SimulationRepositorySnapshotTest {

    private SimulationRepository repository;

    @BeforeEach
    void setUp() {
        repository = new SimulationRepository();

        Location lounge = new Location();
        lounge.setId("loc_1");
        lounge.setName("Lounge");
        lounge.setDescription("A cozy lounge with a fireplace.");

        Location study = new Location();
        study.setId("loc_2");
        study.setName("Study");
        study.setDescription("A quiet room lined with bookshelves.");

        Agent lordBlackwood = new Agent();
        lordBlackwood.setId("agent_1");
        lordBlackwood.setName("Lord Blackwood");
        lordBlackwood.setCurrentLocationId("loc_1");
        lordBlackwood.setAlive(true);
        lordBlackwood.setHasArrived(true);
        lordBlackwood.setKnowledgeBase(new ArrayList<>());

        Agent ladyScarlet = new Agent();
        ladyScarlet.setId("agent_2");
        ladyScarlet.setName("Lady Scarlet");
        ladyScarlet.setCurrentLocationId("loc_2");
        ladyScarlet.setAlive(true);
        ladyScarlet.setHasArrived(true);
        ladyScarlet.setKnowledgeBase(new ArrayList<>());

        Item dagger = new Item();
        dagger.setId("item_1");
        dagger.setName("Antique Dagger");
        dagger.setCurrentLocationId("loc_1");
        dagger.setType(ItemType.WEAPON);
        dagger.setHidden(false);

        Item vial = new Item();
        vial.setId("item_2");
        vial.setName("Arsenic Vial");
        vial.setCurrentLocationId("agent_2");
        vial.setType(ItemType.CLUE);
        vial.setHidden(true);
        vial.setPoisoned(true);
        vial.setPoisonDetails("Cyanide variant");

        repository.initialize(
                List.of(lordBlackwood, ladyScarlet),
                List.of(lounge, study),
                List.of(dagger, vial),
                "Mystery at Blackwood Manor",
                "A tense evening ends in foul play."
        );
    }

    @Test
    @DisplayName("takeSnapshot and getSnapshotAt capture immutable state across time steps")
    void testSnapshotImmutabilityAcrossSteps() {
        // Step 0 was snapshotted during initialize()

        // Mutate live entities at Step 1
        repository.setCurrentStep(1);
        Agent lordBlackwood = repository.getAgents().get("agent_1");
        lordBlackwood.setCurrentLocationId("loc_2");
        lordBlackwood.setAlive(false);

        Item dagger = repository.getItems().get("item_1");
        dagger.setCurrentLocationId("agent_1");

        repository.takeSnapshot();

        // Verify Step 0 remains isolated and unmodified
        SimulationStateSnapshot snap0 = repository.getSnapshotAt(0);
        assertEquals(0, snap0.timeStep());
        AgentSnapshot agent0 = snap0.agents().get("agent_1");
        assertTrue(agent0.isAlive());
        assertEquals("loc_1", agent0.currentLocationId());

        ItemSnapshot item0 = snap0.items().get("item_1");
        assertEquals("loc_1", item0.currentLocationId());

        // Verify Step 1 reflects the mutations
        SimulationStateSnapshot snap1 = repository.getSnapshotAt(1);
        assertEquals(1, snap1.timeStep());
        AgentSnapshot agent1 = snap1.agents().get("agent_1");
        assertFalse(agent1.isAlive());
        assertEquals("loc_2", agent1.currentLocationId());

        ItemSnapshot item1 = snap1.items().get("item_1");
        assertEquals("agent_1", item1.currentLocationId());

        // Verify snapshot collections are immutable
        assertThrows(UnsupportedOperationException.class, () -> snap0.agents().put("dummy", null));
        assertThrows(UnsupportedOperationException.class, () -> snap0.locations().put("dummy", null));
        assertThrows(UnsupportedOperationException.class, () -> snap0.items().put("dummy", null));

        // Verify places() backward-compatible alias matches locations()
        assertEquals(snap0.locations(), snap0.places());
    }

    @Test
    @DisplayName("ItemSnapshot preserves poisonDetails")
    void testItemSnapshotPreservesPoisonDetails() {
        ItemSnapshot vialSnap = repository.getItemSnapshot("item_2", 0);
        assertNotNull(vialSnap);
        assertEquals("Cyanide variant", vialSnap.poisonDetails());
        assertTrue(vialSnap.hidden());
        assertTrue(vialSnap.poisoned());
    }

    @Test
    @DisplayName("Query methods return expected entities at specific snapshot step")
    void testSnapshotQueries() {
        // Step 1: Lady Scarlet drops vial in Study and moves to Lounge
        repository.setCurrentStep(1);
        repository.getAgents().get("agent_2").setCurrentLocationId("loc_1");
        repository.getItems().get("item_2").setCurrentLocationId("loc_2");
        repository.takeSnapshot();

        // At Step 0:
        // Lounge has Lord Blackwood and Dagger
        List<AgentSnapshot> loungeAgentsStep0 = repository.getPresentAgentsInLocation("loc_1", 0);
        assertEquals(1, loungeAgentsStep0.size());
        assertEquals("agent_1", loungeAgentsStep0.get(0).id());

        List<ItemSnapshot> loungeItemsStep0 = repository.getItemsInLocation("loc_1", 0);
        assertEquals(1, loungeItemsStep0.size());
        assertEquals("item_1", loungeItemsStep0.get(0).id());

        // Agent 2 is carrying item 2 at Step 0
        List<ItemSnapshot> carriedStep0 = repository.getCarriedItems("agent_2", 0);
        assertEquals(1, carriedStep0.size());
        assertEquals("item_2", carriedStep0.get(0).id());

        // At Step 1:
        // Lounge has both Lord Blackwood and Lady Scarlet
        List<AgentSnapshot> loungeAgentsStep1 = repository.getPresentAgentsInLocation("loc_1", 1);
        assertEquals(2, loungeAgentsStep1.size());

        // Study now has item 2
        List<ItemSnapshot> studyItemsStep1 = repository.getItemsInLocation("loc_2", 1);
        assertEquals(1, studyItemsStep1.size());
        assertEquals("item_2", studyItemsStep1.get(0).id());

        // Agent 2 carries no items at Step 1
        List<ItemSnapshot> carriedStep1 = repository.getCarriedItems("agent_2", 1);
        assertTrue(carriedStep1.isEmpty());
    }

    @Test
    @DisplayName("Name resolution methods resolve from snapshots and live fallback")
    void testNameResolution() {
        assertEquals("Lord Blackwood", repository.resolveAgentName("agent_1", 0));
        assertEquals("Lady Scarlet", repository.resolveAgentName("agent_2", 0));
        assertEquals("Lounge", repository.resolveLocationName("loc_1", 0));
        assertEquals("Study", repository.resolveLocationName("loc_2", 0));

        // Unknown / null handling
        assertEquals("Unknown", repository.resolveAgentName(null, 0));
        assertEquals("agent_unknown", repository.resolveAgentName("agent_unknown", 0));
        assertEquals("Unknown", repository.resolveLocationName(null, 0));
        assertEquals("loc_unknown", repository.resolveLocationName("loc_unknown", 0));
    }

    @Test
    @DisplayName("getSnapshotAt falls back to live snapshot when step snapshot is not yet saved")
    void testFallbackToLiveSnapshot() {
        SimulationRepository freshRepo = new SimulationRepository();
        Agent agent = new Agent();
        agent.setId("agent_test");
        agent.setName("Test Agent");
        agent.setKnowledgeBase(new ArrayList<>());
        freshRepo.getAgents().put("agent_test", agent);

        // Do not call takeSnapshot()
        SimulationStateSnapshot liveSnap = freshRepo.getSnapshotAt(0);
        assertNotNull(liveSnap);
        assertEquals(0, liveSnap.timeStep());
        assertEquals(1, liveSnap.agents().size());
        assertEquals("Test Agent", liveSnap.agents().get("agent_test").name());
    }

    @Test
    @DisplayName("TimeLocationEvent serializes without snapshotBefore and snapshotAfter")
    void testJsonIgnoreOnEventSnapshots() throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

        com.murderhecoded.domain.TimeLocationEvent event = com.murderhecoded.domain.TimeLocationEvent.builder()
                .timeStep(1)
                .locationId("loc_1")
                .locationName("Lounge")
                .snapshotBefore(repository.getSnapshotAt(0))
                .snapshotAfter(repository.getSnapshotAt(0))
                .build();

        String json = mapper.writeValueAsString(event);
        assertFalse(json.contains("snapshotBefore"), "snapshotBefore must be ignored by Jackson serialization");
        assertFalse(json.contains("snapshotAfter"), "snapshotAfter must be ignored by Jackson serialization");
        assertTrue(json.contains("\"locationId\":\"loc_1\""));
    }

    @Test
    @DisplayName("Fast indexed lookups by agent, location, and item with maxStep filtering")
    void testEventIndexingAndFiltering() {
        com.murderhecoded.domain.Action moveAction = new com.murderhecoded.domain.Action();
        moveAction.setActorId("agent_1");
        moveAction.setActionType(com.murderhecoded.domain.ActionType.MOVE);
        moveAction.setTargetLocationId("loc_2");

        com.murderhecoded.domain.TimeLocationEvent ev1 = com.murderhecoded.domain.TimeLocationEvent.builder()
                .timeStep(1)
                .sequenceNumber(1)
                .locationId("loc_1")
                .locationName("Lounge")
                .actions(List.of(moveAction))
                .presentAgentNames(List.of("Lord Blackwood"))
                .build();

        com.murderhecoded.domain.Action manipulateAction = new com.murderhecoded.domain.Action();
        manipulateAction.setActorId("agent_2");
        manipulateAction.setActionType(com.murderhecoded.domain.ActionType.MANIPULATE_ITEM);
        manipulateAction.setTargetItemId("item_1");
        manipulateAction.setInteraction("PICK_UP");

        com.murderhecoded.domain.TimeLocationEvent ev2 = com.murderhecoded.domain.TimeLocationEvent.builder()
                .timeStep(2)
                .sequenceNumber(2)
                .locationId("loc_2")
                .locationName("Study")
                .actions(List.of(manipulateAction))
                .presentAgentNames(List.of("Lady Scarlet"))
                .build();

        repository.recordTimelineEvents(1, List.of(ev1));
        repository.recordTimelineEvents(2, List.of(ev2));

        // Test agent_1 events at maxStep 1 and maxStep 2
        assertEquals(1, repository.getEventsForAgent("agent_1", 1).size());
        assertEquals(1, repository.getEventsForAgent("agent_1", 2).size());
        assertEquals(0, repository.getEventsForAgent("agent_1", 0).size());

        // Test agent_2 events at maxStep 1 and maxStep 2
        assertEquals(0, repository.getEventsForAgent("agent_2", 1).size());
        assertEquals(1, repository.getEventsForAgent("agent_2", 2).size());

        // Test location lookups
        assertEquals(1, repository.getEventsForLocation("loc_1", 2).size());
        assertEquals(1, repository.getEventsForLocation("loc_2", 2).size());
        assertEquals(0, repository.getEventsForLocation("loc_2", 1).size());

        // Test item lookups
        assertEquals(1, repository.getEventsForItem("item_1", 2).size());
        assertEquals(0, repository.getEventsForItem("item_1", 1).size());
    }

    @Test
    @DisplayName("toDocument persists only base snapshot and restoreFrom projects intermediate snapshots")
    void testLightweightPersistenceAndProjection() {
        // Step 1: Agent 1 moves to loc_2
        com.murderhecoded.domain.Action move = new com.murderhecoded.domain.Action();
        move.setActorId("agent_1");
        move.setActionType(com.murderhecoded.domain.ActionType.MOVE);
        move.setTargetLocationId("loc_2");

        com.murderhecoded.domain.TimeLocationEvent ev1 = com.murderhecoded.domain.TimeLocationEvent.builder()
                .timeStep(1)
                .sequenceNumber(1)
                .locationId("loc_1")
                .actions(List.of(move))
                .build();

        // Step 2: Agent 2 murders Agent 1
        com.murderhecoded.domain.Action murder = new com.murderhecoded.domain.Action();
        murder.setActorId("agent_2");
        murder.setActionType(com.murderhecoded.domain.ActionType.MURDER);
        murder.setTargetAgentId("agent_1");

        com.murderhecoded.domain.TimeLocationEvent ev2 = com.murderhecoded.domain.TimeLocationEvent.builder()
                .timeStep(2)
                .sequenceNumber(2)
                .locationId("loc_2")
                .actions(List.of(murder))
                .build();

        repository.setCurrentStep(2);
        repository.recordTimelineEvents(1, List.of(ev1));
        repository.recordTimelineEvents(2, List.of(ev2));

        // Create persistence document
        com.murderhecoded.domain.SimulationStateDocument doc = repository.toDocument();

        // Verify disk document contains only 1 snapshot (step 0 base)
        assertEquals(1, doc.snapshots().size(), "Document should only persist base step 0 snapshot");
        assertTrue(doc.snapshots().containsKey(0));

        // Restore into a fresh repository
        SimulationRepository restoredRepo = new SimulationRepository();
        restoredRepo.restoreFrom(doc);

        // Verify that intermediate snapshots (step 1 and step 2) were dynamically projected in memory
        assertEquals(3, restoredRepo.getSnapshots().size(), "Restored repository should project snapshots for steps 0, 1, 2");

        // Verify Step 0 state: Agent 1 alive in loc_1
        SimulationStateSnapshot snap0 = restoredRepo.getSnapshotAt(0);
        assertTrue(snap0.agents().get("agent_1").isAlive());
        assertEquals("loc_1", snap0.agents().get("agent_1").currentLocationId());

        // Verify Step 1 state: Agent 1 alive in loc_2
        SimulationStateSnapshot snap1 = restoredRepo.getSnapshotAt(1);
        assertTrue(snap1.agents().get("agent_1").isAlive());
        assertEquals("loc_2", snap1.agents().get("agent_1").currentLocationId());

        // Verify Step 2 state: Agent 1 dead in loc_2
        SimulationStateSnapshot snap2 = restoredRepo.getSnapshotAt(2);
        assertFalse(snap2.agents().get("agent_1").isAlive(), "Agent 1 should be dead at step 2");

        // Verify indices were rebuilt
        // Agent 1 was actor in ev1 (move) and target in ev2 (murder victim), so involved in 2 events
        assertEquals(2, restoredRepo.getEventsForAgent("agent_1", 2).size());
        assertEquals(1, restoredRepo.getEventsForAgent("agent_2", 2).size());
        assertEquals(1, restoredRepo.getEventsForLocation("loc_1", 2).size());
        assertEquals(1, restoredRepo.getEventsForLocation("loc_2", 2).size());
    }
}
