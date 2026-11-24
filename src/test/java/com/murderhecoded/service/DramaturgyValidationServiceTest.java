package com.murderhecoded.service;

import com.murderhecoded.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("PMD")
class DramaturgyValidationServiceTest {

    private DramaturgyValidationService service;

    @BeforeEach
    public void setUp() {
        service = new DramaturgyValidationService();
    }

    @Test
    public void testDetectiveNeverMurdererAndIsolated() {
        List<Agent> agents = new ArrayList<>();
        Agent a0 = new Agent();
        a0.setId("agent_0");
        a0.setName("Sherlock");
        a0.setRole("Detective");
        a0.setSecretGoal("Solve the murder");
        agents.add(a0);

        Agent a1 = new Agent();
        a1.setId("agent_1");
        a1.setName("Moriarty");
        a1.setRole("Guest");
        a1.setSecretGoal("Acquire control");
        agents.add(a1);

        Agent a2 = new Agent();
        a2.setId("agent_2");
        a2.setName("Watson");
        a2.setRole("Guest");
        a2.setSecretGoal("Help the investigation");
        agents.add(a2);

        List<Location> locations = new ArrayList<>();
        Location p0 = new Location();
        p0.setId("room_0");
        p0.setName("Lounge");
        locations.add(p0);

        List<Item> items = new ArrayList<>();

        DramaturgyValidationService.DramaturgyReport report = service.validateAndEnforceDramaturgy(
            agents, locations, items, "Mystery in London", "Foggy night in 1890"
        );

        assertTrue(report.success());
        assertEquals("agent_0", report.detectiveId());
        assertEquals("agent_1", report.murdererId());
        assertEquals("agent_2", report.victimId());

        // Verify Detective properties
        assertTrue(a0.isDetective());
        assertFalse(a0.isMurderer());
        assertFalse(a0.isHasMurderIntent());
        assertFalse(a0.isHasArrived());
        assertNull(a0.getTargetVictimId());

        // Verify Murderer properties
        assertFalse(a1.isDetective());
        assertTrue(a1.isMurderer());
        assertTrue(a1.isHasMurderIntent());
        assertEquals("agent_2", a1.getTargetVictimId());
        assertTrue(a1.getSecretGoal().toLowerCase().startsWith("murder watson"), "Murderer goal must be explicit");
    }

    @Test
    public void testMissingGoalItemsInjected() {
        List<Agent> agents = new ArrayList<>();
        Agent a0 = new Agent();
        a0.setId("agent_0");
        a0.setName("Detective Dupin");
        agents.add(a0);

        Agent a1 = new Agent();
        a1.setId("agent_1");
        a1.setName("Lady Beatrice");
        a1.setSecretGoal("Steal the Encrypted Data Slate (item_6) before departure.");
        agents.add(a1);

        Agent a2 = new Agent();
        a2.setId("agent_2");
        a2.setName("Lord Blackwood");
        a2.setSecretGoal("Find the Martian Keycard from the vault.");
        agents.add(a2);

        List<Location> locations = new ArrayList<>();
        Location p0 = new Location();
        p0.setId("room_0");
        p0.setName("Bridge");
        locations.add(p0);
        Location p1 = new Location();
        p1.setId("room_1");
        p1.setName("Lab");
        locations.add(p1);

        List<Item> items = new ArrayList<>();
        Item existingItem = new Item();
        existingItem.setId("item_0");
        existingItem.setName("Common Cup");
        existingItem.setType(ItemType.VALUABLE);
        items.add(existingItem);

        DramaturgyValidationService.DramaturgyReport report = service.validateAndEnforceDramaturgy(
            agents, locations, items,  "Murder Over Mars", "Space station mystery"
        );

        assertTrue(report.success());

        // Check that item_6 was created
        boolean hasItem6 = items.stream().anyMatch(i -> "item_6".equalsIgnoreCase(i.getId()));
        assertTrue(hasItem6, "Item item_6 must exist after validation");

        // Check that Martian Keycard was created
        boolean hasKeycard = items.stream().anyMatch(i -> i.getName().toLowerCase().contains("keycard"));
        assertTrue(hasKeycard, "Item Martian Keycard must exist after validation");

        // Check that a weapon was guaranteed
        boolean hasWeapon = items.stream().anyMatch(i -> i.getType() == ItemType.WEAPON);
        assertTrue(hasWeapon, "A weapon must be present in the world");
    }



    @Test
    public void testStationaryMachineryItemsAreImmovable() {
        List<Agent> agents = new ArrayList<>();
        Agent a0 = new Agent();
        a0.setId("agent_0");
        a0.setName("Vance");
        agents.add(a0);

        Agent a1 = new Agent();
        a1.setId("agent_1");
        a1.setName("Dr. Reed");
        a1.setSecretGoal("Murder Arthur");
        agents.add(a1);

        Agent a2 = new Agent();
        a2.setId("agent_2");
        a2.setName("Arthur");
        agents.add(a2);

        List<Location> locations = new ArrayList<>();
        Location p0 = new Location();
        p0.setId("room_0");
        p0.setName("Engineering Bay");
        locations.add(p0);

        List<Item> items = new ArrayList<>();
        Item centrifuge = new Item();
        centrifuge.setId("item_0");
        centrifuge.setName("Industrial Centrifuge");
        centrifuge.setType(ItemType.WEAPON);
        centrifuge.setCurrentLocationId("agent_1");
        items.add(centrifuge);

        Item wrench = new Item();
        wrench.setId("item_1");
        wrench.setName("Hydro-Spanner");
        wrench.setType(ItemType.WEAPON);
        wrench.setCurrentLocationId("room_0");
        items.add(wrench);

        service.validateAndEnforceDramaturgy(agents, locations, items, "Space Station", "Sci fi");

        assertFalse(centrifuge.isMovable(), "Centrifuge should be marked as not movable");
        assertEquals("room_0", centrifuge.getCurrentLocationId(), "Stationary item should be moved from agent to room");
        assertTrue(wrench.isMovable(), "Hydro-spanner should remain movable");
    }

    @Test
    public void testMurdererAndVictimStartInDistinctRoomsWhenMultipleRoomsExist() {
        List<Agent> agents = new ArrayList<>();
        Agent a0 = new Agent();
        a0.setId("agent_0");
        a0.setName("Mara Quinn");
        a0.setCurrentLocationId("room_0");
        agents.add(a0);

        Agent a1 = new Agent();
        a1.setId("agent_1");
        a1.setName("Victor Hale");
        a1.setSecretGoal("Murder Elena Voss");
        a1.setCurrentLocationId("room_0");
        agents.add(a1);

        Agent a2 = new Agent();
        a2.setId("agent_2");
        a2.setName("Elena Voss");
        a2.setCurrentLocationId("room_0");
        agents.add(a2);

        List<Location> locations = new ArrayList<>();
        Location p0 = new Location();
        p0.setId("room_0");
        p0.setName("Stakeholders Lounge");
        locations.add(p0);

        Location p1 = new Location();
        p1.setId("room_1");
        p1.setName("Grand Observation Dome");
        locations.add(p1);

        DramaturgyValidationService.DramaturgyReport report = service.validateAndEnforceDramaturgy(
                agents, locations, new ArrayList<>(), "Murder on Mars", "Colony station"
        );

        assertTrue(report.success());
        assertEquals("agent_1", report.murdererId());
        assertEquals("agent_2", report.victimId());

        assertNotEquals(a1.getCurrentLocationId(), a2.getCurrentLocationId(),
                "Murderer and victim must not start in the same room when multiple rooms exist");
        assertEquals("room_1", a1.getCurrentLocationId(), "Murderer should be relocated to alternative room");
        assertEquals("room_0", a2.getCurrentLocationId(), "Victim remains in original room");
    }

    @Test
    public void testPhantomVictimReferencesSanitized() {
        List<Agent> agents = new ArrayList<>();
        Agent a0 = new Agent();
        a0.setId("agent_0");
        a0.setName("Mara Quinn");
        agents.add(a0);

        Agent a1 = new Agent();
        a1.setId("agent_1");
        a1.setName("Victor Hale");
        a1.setSecretGoal("Murder Lian Park because of embezzlement");
        agents.add(a1);

        Agent a2 = new Agent();
        a2.setId("agent_2");
        a2.setName("Dr. Selene Ortiz");
        a2.setRelationship("Former lover of victim Lian Park, now professionally distant");
        agents.add(a2);

        Agent a3 = new Agent();
        a3.setId("agent_3");
        a3.setName("Mira Chen");
        a3.setRelationship("Close friend of victim Lian Park, shares hydroponics data with him");
        agents.add(a3);

        List<Location> locations = new ArrayList<>();
        Location p0 = new Location();
        p0.setId("room_0");
        p0.setName("Lounge");
        locations.add(p0);

        DramaturgyValidationService.DramaturgyReport report = service.validateAndEnforceDramaturgy(
                agents, locations, new ArrayList<>(), "Murder on Mars", "Station"
        );

        assertTrue(report.success());
        assertEquals("agent_2", report.victimId());

        assertFalse(a3.getRelationship().contains("Lian Park"), "Mira Chen relationship must not reference phantom character Lian Park");
        assertTrue(a3.getRelationship().contains("Dr. Selene Ortiz"), "Mira Chen relationship must reference confirmed victim Dr. Selene Ortiz");

        assertFalse(a2.getRelationship().contains("Lian Park"), "Victim relationship must not reference phantom character Lian Park");
        assertFalse(a2.getRelationship().contains("victim Dr. Selene Ortiz"), "Victim relationship should not self-reference as lover of victim");
    }

    @Test
    public void testPhantomDirectorVossAndNonPhantomPreservation() {
        List<Agent> agents = new ArrayList<>();
        Agent a0 = new Agent();
        a0.setId("agent_0");
        a0.setName("Detective Marcus Hale");
        a0.setRole("Detective");
        a0.setRelationship("Hired by the station's oversight board to investigate the death of Director Elara Voss");
        agents.add(a0);

        Agent a1 = new Agent();
        a1.setId("agent_1");
        a1.setName("Yuki Tanaka");
        a1.setRole("Chief Engineer");
        a1.setSecretGoal("Murder Director Elara Voss because she defunded life support");
        a1.setRelationship("Views Director Elara Voss as a negligent authority");
        agents.add(a1);

        Agent a2 = new Agent();
        a2.setId("agent_2");
        a2.setName("Reginald Ashworth");
        a2.setRole("Lead Stakeholder");
        a2.setRelationship("Business rival to Director Elara Voss; stands to inherit a controlling share");
        agents.add(a2);

        Agent a3 = new Agent();
        a3.setId("agent_3");
        a3.setName("Dr. Amara Osei");
        a3.setRole("Medical Officer");
        a3.setSecretGoal("Hide evidence of Director Elara Voss's terminal neurological diagnosis");
        a3.setRelationship("Treated Director Elara Voss in private and discovered a medical secret the victim desperately wanted kept from the board");
        agents.add(a3);

        Agent a4 = new Agent();
        a4.setId("agent_4");
        a4.setName("Viktor Drakov");
        a4.setRole("Mining Manager");
        a4.setRelationship("Contracts were set to be terminated by Director Elara Voss within the month");
        agents.add(a4);

        Agent a5 = new Agent();
        a5.setId("agent_5");
        a5.setName("Lady Catherine Blackwood");
        a5.setRole("Legal Counsel");
        a5.setSecretGoal("Expose Director Elara Voss's embezzlement of colony funds");
        a5.setRelationship("Director Elara Voss was actively blocking her acquisition");
        agents.add(a5);

        List<Location> locations = new ArrayList<>();
        Location p0 = new Location();
        p0.setId("room_0");
        p0.setName("Dining Hall");
        locations.add(p0);

        DramaturgyValidationService.DramaturgyReport report = service.validateAndEnforceDramaturgy(
                agents, locations, new ArrayList<>(), "Murder Over Mars", "Station"
        );

        assertTrue(report.success());
        assertEquals("agent_2", report.victimId(), "Confirmed victim must be Reginald Ashworth");

        // 1. Check non-phantom text preservation: "desperately wanted kept from the board" must NOT be corrupted!
        assertTrue(a3.getRelationship().contains("desperately wanted kept from the board"),
                "Phrase 'desperately wanted kept from the board' must NOT be replaced or corrupted");
        assertTrue(a3.getRelationship().contains("Reginald Ashworth"),
                "Dr. Amara Osei's relationship must replace Director Elara Voss with Reginald Ashworth");
        assertTrue(a3.getSecretGoal().contains("Reginald Ashworth"),
                "Dr. Amara Osei's goal must replace Director Elara Voss with Reginald Ashworth");

        // 2. Check all other agents no longer contain "Director Elara Voss"
        for (Agent a : agents) {
            if (a.getRelationship() != null) {
                assertFalse(a.getRelationship().contains("Director Elara Voss"),
                        "Relationship of " + a.getName() + " must not contain phantom 'Director Elara Voss'");
                assertFalse(a.getRelationship().contains("Elara Voss"),
                        "Relationship of " + a.getName() + " must not contain phantom 'Elara Voss'");
            }
            if (a.getSecretGoal() != null) {
                assertFalse(a.getSecretGoal().contains("Director Elara Voss"),
                        "SecretGoal of " + a.getName() + " must not contain phantom 'Director Elara Voss'");
                assertFalse(a.getSecretGoal().contains("Elara Voss"),
                        "SecretGoal of " + a.getName() + " must not contain phantom 'Elara Voss'");
            }
        }

        // 3. Check victim does not talk about himself as his own rival
        assertFalse(a2.getRelationship().contains("Business rival to Reginald Ashworth"),
                "Confirmed victim must not refer to himself as rival to himself");
    }
}
