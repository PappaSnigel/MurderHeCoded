package com.murderhecoded.service;

import com.murderhecoded.domain.GlobalEvent;
import com.murderhecoded.repository.SimulationRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("PMD")
class GlobalEventTest {

    @Test
    public void testGlobalEventActiveAndConcludedWindow() {
        GlobalEvent event = GlobalEvent.builder()
                .id("event_1")
                .name("Formal Dinner")
                .scheduledStep(10)
                .durationSteps(4) // active during steps 10, 11, 12, 13
                .impactType("GATHERING")
                .build();

        assertFalse(event.isActiveAt(9));
        assertTrue(event.isActiveAt(10));
        assertTrue(event.isActiveAt(12));
        assertTrue(event.isActiveAt(13));
        assertFalse(event.isActiveAt(14));

        assertFalse(event.isConcludedAt(9));
        assertFalse(event.isConcludedAt(13));
        assertTrue(event.isConcludedAt(14));
        assertTrue(event.isConcludedAt(20));
    }

    @Test
    public void testSimulationRepositoryActiveGlobalEvents() {
        SimulationRepository repo = new SimulationRepository();
        GlobalEvent e1 = new GlobalEvent("ev_1", "Tea", "Afternoon tea", 4, 3, "room_1", "GATHERING", false, false, false);
        GlobalEvent e2 = new GlobalEvent("ev_2", "Storm", "Thunderstorm blackout", 20, 4, null, "ENVIRONMENTAL", false, false, false);

        repo.initialize(List.of(), List.of(), List.of(), List.of(e1, e2), "Title", "Premise");

        assertEquals(2, repo.getGlobalEvents().size());
        assertEquals(1, repo.getActiveGlobalEvents(5).size());
        assertEquals("Tea", repo.getActiveGlobalEvents(5).get(0).getName());
        assertTrue(repo.getActiveGlobalEvents(10).isEmpty());
        assertEquals(1, repo.getActiveGlobalEvents(22).size());
        assertEquals("Storm", repo.getActiveGlobalEvents(22).get(0).getName());
    }
}
