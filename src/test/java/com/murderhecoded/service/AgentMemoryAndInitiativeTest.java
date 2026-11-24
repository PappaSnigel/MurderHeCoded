package com.murderhecoded.service;

import com.murderhecoded.agent.AgentService;
import com.murderhecoded.domain.*;
import com.murderhecoded.domain.Location;
import com.murderhecoded.llm.LlmExecutor;
import com.murderhecoded.repository.SimulationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("PMD")
class AgentMemoryAndInitiativeTest {

    private SimulationRepository repository;
    private AgentService agentService;

    @BeforeEach
    void setUp() {
        repository = new SimulationRepository();
        agentService = new AgentService(null, new LlmExecutor());
    }

    @Test
    void testMemorySnapshotConversion() {
        Memory mem = new Memory(
            1,
            "room_library",
            "Library",
            List.of("agent_1", "agent_2"),
            List.of("Alice", "Bob"),
            List.of("item_book"),
            List.of("Ancient Book"),
            List.of("Bob took 'Ancient Book'.")
        );

        MemorySnapshot snap = mem.snapshot();
        assertEquals(1, snap.timeStep());
        assertEquals("room_library", snap.locationId());
        assertEquals("Library", snap.locationName());
        assertEquals(2, snap.presentAgentNames().size());
        assertEquals(1, snap.observedEvents().size());
        assertEquals("Bob took 'Ancient Book'.", snap.observedEvents().get(0));
    }

    @Test
    void testAgentSnapshotIncludesMemories() {
        Agent agent = new Agent();
        agent.setId("agent_1");
        agent.setName("Alice");
        agent.setAlive(true);

        Memory mem = new Memory(
            2,
            "room_salon",
            "Salon",
            List.of("agent_1"),
            List.of("Alice"),
            List.of(),
            List.of(),
            List.of("Alice observed the painting.")
        );
        agent.getEpisodicMemories().add(mem);

        AgentSnapshot snap = agent.snapshot();
        assertNotNull(snap.episodicMemories());
        assertEquals(1, snap.episodicMemories().size());
        assertEquals("Salon", snap.episodicMemories().get(0).locationName());
    }

    @Test
    void testInitiativeSortingPlacesMurdererFirst() {
        Agent innocent = new Agent();
        innocent.setId("agent_innocent");
        innocent.setName("Innocent Person");
        innocent.setRole("Guest");
        innocent.setHasMurderIntent(false);

        Agent murderer = new Agent();
        murderer.setId("agent_killer");
        murderer.setName("The Killer");
        murderer.setRole("Murderer");
        murderer.setHasMurderIntent(true);

        List<Agent> room = new ArrayList<>(List.of(innocent, murderer));
        room.sort((a1, a2) -> {
            int p1 = a1.isMurderer() ? 0 : 1;
            int p2 = a2.isMurderer() ? 0 : 1;
            return Integer.compare(p1, p2);
        });

        assertEquals("The Killer", room.get(0).getName(), "Murderer must be sorted first for initiative");
        assertEquals("Innocent Person", room.get(1).getName());
    }

    @Test
    void testAgentServiceIncludesMemoriesAndCarriedItems() {
        Location room = new Location();
        room.setId("room_1");
        room.setName("Library");

        Agent anna = new Agent();
        anna.setId("agent_anna");
        anna.setName("Anna");
        anna.setCurrentLocationId("room_1");
        anna.setAlive(true);
        anna.setHasArrived(true);

        Agent bertil = new Agent();
        bertil.setId("agent_bertil");
        bertil.setName("Bertil");
        bertil.setCurrentLocationId("room_1");
        bertil.setAlive(true);
        bertil.setHasArrived(true);

        Item book = new Item();
        book.setId("item_book");
        book.setName("Ancient Diary");
        book.setType(ItemType.CLUE);
        book.setCurrentLocationId("agent_bertil"); // Bertil is carrying it!

        repository.initialize(List.of(anna, bertil), List.of(room), List.of(book), List.of(), "Title", "Premise");

        anna.getEpisodicMemories().add(new Memory(
            1, "room_1", "Library",
            List.of("agent_anna", "agent_bertil"), List.of("Anna", "Bertil"),
            List.of("item_book"), List.of("Ancient Diary"),
            List.of("Bertil took 'Ancient Diary'.")
        ));

        String prompt = agentService.buildPrompt(anna, repository);
        assertTrue(prompt.contains("Recent Experiences & Memories"), "Prompt must include episodic memories");
        assertTrue(prompt.contains("Bertil took 'Ancient Diary'"), "Prompt must include observed event");
        assertTrue(prompt.contains("Carrying: Ancient Diary"), "Prompt must show that Bertil is carrying the diary");
    }

    @Test
    void testCannotTakeImmovableItem() {
        Location room = new Location();
        room.setId("room_1");
        room.setName("Lab");

        Agent anna = new Agent();
        anna.setId("agent_anna");
        anna.setName("Anna");
        anna.setCurrentLocationId("room_1");
        anna.setAlive(true);

        Item centrifuge = new Item();
        centrifuge.setId("item_centrifuge");
        centrifuge.setName("Large Centrifuge");
        centrifuge.setType(ItemType.WEAPON);
        centrifuge.setMovable(false);
        centrifuge.setCurrentLocationId("room_1");

        repository.initialize(List.of(anna), List.of(room), List.of(centrifuge), List.of(), "Title", "Premise");

        com.murderhecoded.engine.handlers.ManipulateItemActionHandler handler = new com.murderhecoded.engine.handlers.ManipulateItemActionHandler();
        Action action = new Action();
        action.setActorId("agent_anna");
        action.setActionType(ActionType.MANIPULATE_ITEM);
        action.setInteraction("TAKE");
        action.setTargetItemId("item_centrifuge");

        List<String> logs = new ArrayList<>();
        handler.handle(action, repository, logs::add);

        assertEquals("room_1", centrifuge.getCurrentLocationId(), "Centrifuge must remain in room_1");
        assertNotEquals("agent_anna", centrifuge.getCurrentLocationId());
        assertTrue(logs.stream().anyMatch(l -> l.contains("cannot be moved")));
    }

    @Test
    void testPoisonItemAndSearchResidue() {
        Location room = new Location();
        room.setId("room_1");
        room.setName("Dining Room");

        Agent murderer = new Agent();
        murderer.setId("agent_murderer");
        murderer.setName("Dr. Malice");
        murderer.setCurrentLocationId("room_1");
        murderer.setAlive(true);

        Agent detective = new Agent();
        detective.setId("agent_0");
        detective.setName("Vance");
        detective.setCurrentLocationId("room_1");
        detective.setAlive(true);

        Item wine = new Item();
        wine.setId("item_wine");
        wine.setName("Decanter of Wine");
        wine.setType(ItemType.VALUABLE);
        wine.setCurrentLocationId("room_1");

        repository.initialize(List.of(murderer, detective), List.of(room), List.of(wine), List.of(), "Title", "Premise");

        com.murderhecoded.engine.handlers.ManipulateItemActionHandler manipHandler = new com.murderhecoded.engine.handlers.ManipulateItemActionHandler();
        Action poisonAction = new Action();
        poisonAction.setActorId("agent_murderer");
        poisonAction.setActionType(ActionType.MANIPULATE_ITEM);
        poisonAction.setInteraction("POISON");
        poisonAction.setTargetItemId("item_wine");
        poisonAction.setTargetAgentId("agent_victim");
        poisonAction.setDetails("Laced with cyanide");

        List<String> logs = new ArrayList<>();
        manipHandler.handle(poisonAction, repository, logs::add);

        assertTrue(wine.isPoisoned(), "Wine should be marked as poisoned");
        assertEquals("agent_murderer", wine.getPoisonedByAgentId());
        assertEquals("agent_victim", wine.getPoisonTargetAgentId());
        assertTrue(murderer.isHasMurderIntent());

        // Detective searches the room
        com.murderhecoded.engine.handlers.SearchActionHandler searchHandler = new com.murderhecoded.engine.handlers.SearchActionHandler();
        Action searchAction = new Action();
        searchAction.setActorId("agent_0");
        searchAction.setActionType(ActionType.SEARCH);
        searchHandler.handle(searchAction, repository, logs::add);

        boolean foundClue = detective.getKnowledgeBase().stream()
                .anyMatch(k -> k.information().contains("suspicious chemical residue") && k.information().contains("Decanter of Wine"));
        assertTrue(foundClue, "Detective should discover poison residue on the wine decanter");
    }

    @Test
    void testRemotePoisoningExecutionWithAlibi() {
        Location kitchen = new Location();
        kitchen.setId("room_kitchen");
        kitchen.setName("Kitchen");

        Location library = new Location();
        library.setId("room_library");
        library.setName("Library");

        Agent murderer = new Agent();
        murderer.setId("agent_murderer");
        murderer.setName("Dr. Malice");
        murderer.setCurrentLocationId("room_library"); // Murderer is in the library!
        murderer.setAlive(true);
        murderer.setHasArrived(true);

        Agent victim = new Agent();
        victim.setId("agent_victim");
        victim.setName("Lord Arthur");
        victim.setCurrentLocationId("room_kitchen"); // Victim is in the kitchen!
        victim.setAlive(true);
        victim.setHasArrived(true);

        Agent witness = new Agent();
        witness.setId("agent_witness");
        witness.setName("Chef Pierre");
        witness.setCurrentLocationId("room_kitchen"); // Witness is in the kitchen with victim
        witness.setAlive(true);
        witness.setHasArrived(true);

        Item tea = new Item();
        tea.setId("item_tea");
        tea.setName("Earl Grey Tea");
        tea.setType(ItemType.VALUABLE);
        tea.setCurrentLocationId("room_kitchen");
        tea.setPoisoned(true);
        tea.setPoisonedByAgentId("agent_murderer");
        tea.setPoisonTargetAgentId("agent_victim");

        repository.initialize(List.of(murderer, victim, witness), List.of(kitchen, library), List.of(tea), List.of(), "Title", "Premise");

        SimulationService gm = new SimulationService(repository, null, null);

        List<String> observedEvents = new ArrayList<>();
        List<Action> stepActions = new ArrayList<>();
        List<Action> sceneActions = new ArrayList<>();
        List<String> sceneKnowledge = new ArrayList<>();

        gm.checkAndTriggerPoisonEffects("room_kitchen", "Kitchen", List.of(victim, witness), 2, observedEvents, stepActions, sceneActions, sceneKnowledge);

        // Victim must be dead!
        assertFalse(victim.isAlive(), "Victim should have succumbed to the poison");
        // Murderer is alive in the library (airtight alibi!)
        assertTrue(murderer.isAlive());
        assertEquals("room_library", murderer.getCurrentLocationId());
        assertTrue(murderer.isHasMurderIntent());
        assertEquals("agent_victim", murderer.getTargetVictimId());

        // Witness observed the poisoning death
        boolean witnessLearned = witness.getKnowledgeBase().stream()
                .anyMatch(k -> k.information().contains("Lord Arthur") && k.information().contains("poisoning"));
        assertTrue(witnessLearned, "Witness in kitchen should know Lord Arthur died from poisoning");

        // Poison is consumed
        assertFalse(tea.isPoisoned());
        assertTrue(tea.isPoisonConsumed());

        // A murder action was created
        boolean hasMurderAction = sceneActions.stream().anyMatch(a -> a.getActionType() == ActionType.MURDER && "agent_victim".equals(a.getTargetAgentId()));
        assertTrue(hasMurderAction, "Scene actions should record a MURDER action for the poison kill");
    }

    @Test
    void testSearchNotebookPrefixesKnowledgeWithNotebookName() {
        Agent actor = new Agent();
        actor.setId("agent_finder");
        actor.setName("Rook Tanaka");
        actor.setCurrentLocationId("room_core");

        Item notebook = new Item();
        notebook.setId("item_nb");
        notebook.setName("Notebook of Dr. Selene Ortiz");
        notebook.setType(ItemType.NOTEBOOK);
        notebook.setCurrentLocationId("room_core");
        notebook.getNotebookKnowledge().add(new Knowledge("Unauthorized gene therapy experiments I've been conducting", 10));

        Location coreLoc = new Location();
        coreLoc.setId("room_core");
        coreLoc.setName("Engineering Core");
        coreLoc.setDescription("Core room");
        repository.initialize(List.of(actor), List.of(coreLoc), List.of(notebook), List.of(), "Title", "Premise");

        com.murderhecoded.engine.handlers.SearchActionHandler searchHandler = new com.murderhecoded.engine.handlers.SearchActionHandler();
        Action searchAct = new Action();
        searchAct.setActorId(actor.getId());
        searchAct.setActionType(ActionType.SEARCH);

        searchHandler.handle(searchAct, repository, log -> {});

        assertEquals(1, actor.getKnowledgeBase().size(), "Actor must acquire the knowledge from the notebook");
        String gainedFact = actor.getKnowledgeBase().get(0).information();
        assertTrue(gainedFact.startsWith("[From Notebook of Dr. Selene Ortiz]: "),
                "Acquired knowledge must be prefixed with the notebook origin to avoid identity contagion");
        assertTrue(gainedFact.contains("Unauthorized gene therapy experiments"),
                "Original information must be preserved after prefix");
    }

    @Test
    void testTalkActionHandlerStripsLeadingVocatives() {
        Agent speaker = new Agent();
        speaker.setId("agent_speaker");
        speaker.setName("Elise Marceau");
        speaker.setCurrentLocationId("room_lounge");
        speaker.setAlive(true);

        Agent listener = new Agent();
        listener.setId("agent_listener");
        listener.setName("Captain Joren Voss");
        listener.setCurrentLocationId("room_lounge");
        listener.setAlive(true);

        Location loungeLoc = new Location();
        loungeLoc.setId("room_lounge");
        loungeLoc.setName("Lounge");
        loungeLoc.setDescription("Lounge room");
        repository.initialize(List.of(speaker, listener), List.of(loungeLoc), List.of(), List.of(), "Title", "Premise");

        com.murderhecoded.engine.handlers.TalkActionHandler talkHandler = new com.murderhecoded.engine.handlers.TalkActionHandler();
        Action talkAct = new Action();
        talkAct.setActorId(speaker.getId());
        talkAct.setTargetAgentId(listener.getId());
        talkAct.setActionType(ActionType.TALK);
        talkAct.setSharedKnowledge("Captain, I noticed some discrepancies in the rotation system reports.");

        talkHandler.handle(talkAct, repository, log -> {});

        assertEquals(1, listener.getKnowledgeBase().size());
        String learned = listener.getKnowledgeBase().get(0).information();
        assertEquals("I noticed some discrepancies in the rotation system reports.", learned,
                "Leading vocative 'Captain,' must be stripped so facts don't propagate honorifics inappropriately");

        assertEquals("We need to act now.", com.murderhecoded.engine.handlers.TalkActionHandler.stripLeadingVocatives("Captain Voss, we need to act now."));
        assertEquals("The leak is growing.", com.murderhecoded.engine.handlers.TalkActionHandler.stripLeadingVocatives("Doctor, the leak is growing."));
    }

    @Test
    void testKnowledgeDeduplicationPreventsRepetition() {
        Agent agent = new Agent();
        agent.setId("agent_detective");
        agent.setName("Inspector Callum Wren");

        boolean first = agent.addKnowledge(new Knowledge("Noticed strange pressure fluctuations near the airlock", 1));
        assertTrue(first, "First occurrence of new knowledge must be accepted");
        assertEquals(1, agent.getKnowledgeBase().size());

        // Identical fact with different location tag
        boolean duplicate1 = agent.addKnowledge(new Knowledge("Noticed strange pressure fluctuations near the airlock (Location: The Central Rotunda)", 2));
        assertFalse(duplicate1, "Duplicate knowledge with location tag must be rejected");
        assertEquals(1, agent.getKnowledgeBase().size());

        // Fact with vocative prefix and different location tag
        boolean duplicate2 = agent.addKnowledge(new Knowledge("Captain, noticed strange pressure fluctuations near the airlock (Location: The Observation Dome)", 3));
        assertFalse(duplicate2, "Duplicate knowledge with vocative and location must be rejected");
        assertEquals(1, agent.getKnowledgeBase().size());

        // Completely new fact
        boolean second = agent.addKnowledge(new Knowledge("Found suspicious chemical residue on the sample case", 4));
        assertTrue(second, "Genuine new fact must be accepted");
        assertEquals(2, agent.getKnowledgeBase().size());
    }
}
