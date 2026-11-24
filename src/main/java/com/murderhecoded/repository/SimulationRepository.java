package com.murderhecoded.repository;

import com.murderhecoded.domain.*;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.stream.Collectors;

@Repository
@Slf4j
@Getter
@SuppressWarnings("PMD")
public class SimulationRepository {
    private final Map<String, Agent> agents = new HashMap<>();
    private final Map<String, Location> locations = new HashMap<>();
    private final Map<String, Item> items = new HashMap<>();

    private final Map<String, GlobalEvent> globalEvents = new LinkedHashMap<>();
    private final Map<Integer, List<TimeLocationEvent>> timelineEvents = new java.util.concurrent.ConcurrentHashMap<>();
    @Setter private int currentStep = 0;
    private int stepsSinceLastIncident = 0;
    @Setter private int totalHours;
    private int stepMinutes;

    @Setter private int targetSteps;
    @Setter private boolean isWorldGenerated;
    @Setter private DramaturgyProfile dramaturgyProfile = DramaturgyProfile.standard();

    public DramaturgyProfile getDramaturgyProfile() {
        return dramaturgyProfile != null ? dramaturgyProfile : DramaturgyProfile.standard();
    }

    private Map<Integer, SimulationStateSnapshot> snapshots = new HashMap<>();
    private List<String> eventHistory = new java.util.concurrent.CopyOnWriteArrayList<>();

    @com.fasterxml.jackson.annotation.JsonIgnore
    private final Map<String, List<TimeLocationEvent>> eventsByAgent = new java.util.concurrent.ConcurrentHashMap<>();
    @com.fasterxml.jackson.annotation.JsonIgnore
    private final Map<String, List<TimeLocationEvent>> eventsByLocation = new java.util.concurrent.ConcurrentHashMap<>();
    @com.fasterxml.jackson.annotation.JsonIgnore
    private final Map<String, List<TimeLocationEvent>> eventsByItem = new java.util.concurrent.ConcurrentHashMap<>();

    @Setter private boolean completed = false;
    @Setter private String title;
    @Setter private String premise;

    public void initialize(List<Agent> initialAgents, List<Location> initialLocations, List<Item> initialItems, String title, String premise) {
        initialize(initialAgents, initialLocations, initialItems, Collections.emptyList(), title, premise);
    }

    public void initialize(List<Agent> initialAgents, List<Location> initialLocations, List<Item> initialItems, List<GlobalEvent> initialGlobalEvents, String title, String premise) {
        this.title = title;
        this.premise = premise;
        this.agents.clear();
        this.locations.clear();
        this.items.clear();
        this.globalEvents.clear();
        this.timelineEvents.clear();
        this.snapshots.clear();
        this.eventHistory.clear();
        this.eventsByAgent.clear();
        this.eventsByLocation.clear();
        this.eventsByItem.clear();
        this.currentStep = 0;
        this.stepsSinceLastIncident = 0;
        this.completed = false;
        
        if (initialAgents != null) initialAgents.forEach(a -> agents.put(a.getId(), a));

        if (initialLocations != null) initialLocations.forEach(l -> locations.put(l.id(), l));



        if (initialItems != null) initialItems.forEach(i -> items.put(i.getId(), i));
        if (initialGlobalEvents != null) initialGlobalEvents.forEach(e -> globalEvents.put(e.getId(), e));
        
        takeSnapshot();
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    private final com.murderhecoded.service.TimeService timeService = new com.murderhecoded.service.TimeService();

    public void setStepMinutes(int stepMinutes) {
        this.stepMinutes = stepMinutes > 0 ? stepMinutes : com.murderhecoded.service.TimeService.DEFAULT_MINUTES_PER_STEP;
        this.timeService.setMinutesPerStep(this.stepMinutes);
    }

    public int getStepMinutes() {
        return this.stepMinutes;
    }

    public String getClockTime(int step) {
        return timeService.formatForUI(step);
    }

    public String formatInterval(int fromStep, int toStep) {
        return timeService.formatInterval(fromStep, toStep);
    }

    public void recordIncident() {
        this.stepsSinceLastIncident = 0;
    }

    public void incrementStep() {
        this.currentStep++;
        this.stepsSinceLastIncident++;
    }

    public void resetIncidentTimer() {
        this.stepsSinceLastIncident = 0;
    }

    public List<GlobalEvent> getActiveGlobalEvents(int step) {
        return globalEvents.values().stream()
                .filter(e -> e.isActiveAt(step))
                .collect(Collectors.toList());
    }

    public List<GlobalEvent> getUpcomingGlobalEvents(int step) {
        return globalEvents.values().stream()
                .filter(e -> e.getScheduledStep() > step)
                .sorted(Comparator.comparingInt(GlobalEvent::getScheduledStep))
                .collect(Collectors.toList());
    }

    public void recordTimelineEvents(int step, List<TimeLocationEvent> events) {
        if (events != null && !events.isEmpty()) {
            this.timelineEvents.computeIfAbsent(step, k -> new java.util.concurrent.CopyOnWriteArrayList<>()).addAll(events);
            for (TimeLocationEvent ev : events) {
                indexEvent(ev);
            }
        }
    }

    private void indexEvent(TimeLocationEvent ev) {
        if (ev == null) return;
        if (ev.getLocationId() != null) {
            eventsByLocation.computeIfAbsent(ev.getLocationId(), k -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(ev);
        }

        Set<String> involvedAgents = new HashSet<>();
        if (ev.getPresentAgentNames() != null) {
            for (String pName : ev.getPresentAgentNames()) {
                if (agents.containsKey(pName)) {
                    involvedAgents.add(pName);
                } else {
                    for (Agent a : agents.values()) {
                        if (a.getName() != null && a.getName().equalsIgnoreCase(pName)) {
                            involvedAgents.add(a.getId());
                        }
                    }
                }
            }
        }

        if (ev.getActions() != null) {
            for (Action a : ev.getActions()) {
                if (a.getActorId() != null) involvedAgents.add(a.getActorId());
                if (a.getTargetAgentId() != null) involvedAgents.add(a.getTargetAgentId());

                String it1 = a.getTargetItemId();
                if (it1 != null) {
                    eventsByItem.computeIfAbsent(it1, k -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(ev);
                }
                String it2 = a.getWeaponItemId();
                if (it2 != null && !it2.equals(it1)) {
                    eventsByItem.computeIfAbsent(it2, k -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(ev);
                }
            }
        }

        for (String agentId : involvedAgents) {
            eventsByAgent.computeIfAbsent(agentId, k -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(ev);
        }
    }

    public List<TimeLocationEvent> getEventsForAgent(String agentId, int maxStep) {
        if (agentId == null) return Collections.emptyList();
        List<TimeLocationEvent> list = eventsByAgent.get(agentId);
        if (list == null) return Collections.emptyList();
        return list.stream()
                .filter(e -> e.getTimeStep() <= maxStep)
                .distinct()
                .sorted(Comparator.comparingInt(TimeLocationEvent::getTimeStep).thenComparingInt(TimeLocationEvent::getSequenceNumber))
                .toList();
    }

    public List<TimeLocationEvent> getEventsForLocation(String locationId, int maxStep) {
        if (locationId == null) return Collections.emptyList();
        List<TimeLocationEvent> list = eventsByLocation.get(locationId);
        if (list == null) return Collections.emptyList();
        return list.stream()
                .filter(e -> e.getTimeStep() <= maxStep)
                .distinct()
                .sorted(Comparator.comparingInt(TimeLocationEvent::getTimeStep).thenComparingInt(TimeLocationEvent::getSequenceNumber))
                .toList();
    }

    public List<TimeLocationEvent> getEventsForItem(String itemId, int maxStep) {
        if (itemId == null) return Collections.emptyList();
        List<TimeLocationEvent> list = eventsByItem.get(itemId);
        if (list == null) return Collections.emptyList();
        return list.stream()
                .filter(e -> e.getTimeStep() <= maxStep)
                .distinct()
                .sorted(Comparator.comparingInt(TimeLocationEvent::getTimeStep).thenComparingInt(TimeLocationEvent::getSequenceNumber))
                .toList();
    }

    public List<TimeLocationEvent> getEventsForStep(int step) {
        return this.timelineEvents.getOrDefault(step, Collections.emptyList());
    }

    public List<TimeLocationEvent> getAllRecordedEvents() {
        List<TimeLocationEvent> all = new ArrayList<>();
        for (int s = 1; s <= currentStep; s++) {
            List<TimeLocationEvent> stepEvents = timelineEvents.get(s);
            if (stepEvents != null) {
                all.addAll(stepEvents);
            }
        }
        return all;
    }

    public List<TimeLocationEvent> ensureOrSynthesizeTimelineEvents() {
        return new com.murderhecoded.service.DramaturgyDirectorService().ensureOrSynthesizeTimelineEvents(this);
    }

    public synchronized SimulationStateDocument toDocument() {
        // Event sourcing persistence optimization:
        // Do not serialize hundreds of redundant world snapshots to disk.
        // We persist the base step 0 snapshot; on load, snapshots are dynamically projected in memory.
        Map<Integer, SimulationStateSnapshot> diskSnapshots = new HashMap<>();
        if (snapshots.containsKey(0)) {
            diskSnapshots.put(0, snapshots.get(0));
        } else if (!snapshots.isEmpty()) {
            int minStep = snapshots.keySet().stream().min(Integer::compareTo).orElse(0);
            diskSnapshots.put(minStep, snapshots.get(minStep));
        }
        return new SimulationStateDocument(
                this.title,
                this.premise,
                this.currentStep,
                this.targetSteps,
                this.totalHours,
                this.stepMinutes,
                this.completed,
                this.isWorldGenerated,
                this.agents,
                this.locations,
                this.items,
                this.globalEvents,
                this.timelineEvents,
                diskSnapshots
        );
    }

    public synchronized void restoreFrom(SimulationStateDocument doc) {
        if (doc == null) {
            return;
        }
        this.agents.clear();
        this.locations.clear();
        this.items.clear();
        this.globalEvents.clear();
        this.timelineEvents.clear();
        this.snapshots.clear();
        this.eventHistory.clear();
        this.eventsByAgent.clear();
        this.eventsByLocation.clear();
        this.eventsByItem.clear();

        if (doc.agents() != null) {
            this.agents.putAll(doc.agents());
        }
        if (doc.locations() != null) {
            this.locations.putAll(doc.locations());
        }
        if (doc.items() != null) {
            this.items.putAll(doc.items());
        }
        if (doc.globalEvents() != null) {
            this.globalEvents.putAll(doc.globalEvents());
        }
        if (doc.timelineEvents() != null) {
            this.timelineEvents.putAll(doc.timelineEvents());
        }
        if (doc.snapshots() != null) {
            this.snapshots.putAll(doc.snapshots());
        }

        this.title = doc.title();
        this.premise = doc.premise();
        this.currentStep = doc.currentStep();
        this.targetSteps = doc.targetSteps();
        this.totalHours = doc.totalHours();
        this.completed = doc.completed();
        this.isWorldGenerated = doc.worldGenerated();
        setStepMinutes(doc.stepMinutes() > 0 ? doc.stepMinutes() : 15);

        rebuildSnapshotsAndIndices();
    }

    public synchronized void rebuildSnapshotsAndIndices() {
        eventsByAgent.clear();
        eventsByLocation.clear();
        eventsByItem.clear();

        List<Integer> sortedSteps = timelineEvents.keySet().stream().sorted().toList();
        for (int s : sortedSteps) {
            List<TimeLocationEvent> evList = timelineEvents.get(s);
            if (evList != null) {
                for (TimeLocationEvent ev : evList) {
                    indexEvent(ev);
                }
            }
        }

        if (!snapshots.containsKey(0)) {
            snapshots.put(0, createLiveSnapshot(0));
        }

        SimulationStateSnapshot step0Snap = snapshots.get(0);
        Map<String, String> agentLocations = new HashMap<>();
        Map<String, Boolean> agentAlive = new HashMap<>();
        Map<String, Boolean> agentArrived = new HashMap<>();
        Map<String, String> itemLocations = new HashMap<>();
        Map<String, Boolean> itemHidden = new HashMap<>();
        Map<String, Boolean> itemPoisoned = new HashMap<>();
        Map<String, Boolean> itemPoisonConsumed = new HashMap<>();

        if (step0Snap != null) {
            if (step0Snap.agents() != null) {
                step0Snap.agents().values().forEach(a -> {
                    agentLocations.put(a.id(), a.currentLocationId());
                    agentAlive.put(a.id(), a.isAlive());
                    agentArrived.put(a.id(), a.hasArrived());
                });
            }
            if (step0Snap.items() != null) {
                step0Snap.items().values().forEach(i -> {
                    itemLocations.put(i.id(), i.currentLocationId());
                    itemHidden.put(i.id(), i.hidden());
                    itemPoisoned.put(i.id(), i.poisoned());
                    itemPoisonConsumed.put(i.id(), i.poisonConsumed());
                });
            }
        }

        int maxStep = Math.max(currentStep, sortedSteps.isEmpty() ? 0 : sortedSteps.get(sortedSteps.size() - 1));

        for (int step = 1; step <= maxStep; step++) {
            final int currentProjectionStep = step;
            List<TimeLocationEvent> evList = timelineEvents.get(step);
            if (evList != null) {
                for (TimeLocationEvent ev : evList) {
                    if (ev.getSnapshotBefore() == null) {
                        ev.setSnapshotBefore(snapshots.get(step - 1));
                    }
                    if (ev.getActions() != null) {
                        for (Action a : ev.getActions()) {
                            String actorId = a.getActorId();
                            if (a.getActionType() == ActionType.MOVE && a.getTargetLocationId() != null) {
                                agentLocations.put(actorId, a.getTargetLocationId());
                            } else if (a.getActionType() == ActionType.MURDER && a.getTargetAgentId() != null) {
                                agentAlive.put(a.getTargetAgentId(), false);
                            } else if (a.getActionType() == ActionType.MANIPULATE_ITEM) {
                                String itId = a.getTargetItemId() != null ? a.getTargetItemId() : a.getWeaponItemId();
                                if (itId != null) {
                                    if ("TAKE".equalsIgnoreCase(a.getInteraction()) || "PICK_UP".equalsIgnoreCase(a.getInteraction())) {
                                        itemLocations.put(itId, actorId);
                                    } else if ("DROP".equalsIgnoreCase(a.getInteraction())) {
                                        itemLocations.put(itId, ev.getLocationId());
                                    } else if ("HIDE".equalsIgnoreCase(a.getInteraction())) {
                                        itemHidden.put(itId, true);
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (!snapshots.containsKey(step)) {
                Map<String, AgentSnapshot> newAgentSnaps = new HashMap<>();
                for (Agent a : agents.values()) {
                    String loc = agentLocations.getOrDefault(a.getId(), a.getCurrentLocationId());
                    boolean alive = agentAlive.getOrDefault(a.getId(), a.isAlive());
                    boolean arrived = agentArrived.getOrDefault(a.getId(), a.isHasArrived());
                    List<KnowledgeSnapshot> kbSnaps = a.getKnowledgeBase() != null
                            ? a.getKnowledgeBase().stream().filter(k -> k.timeStep() <= currentProjectionStep).map(Knowledge::snapshot).toList()
                            : List.of();
                    List<MemorySnapshot> memSnaps = a.getEpisodicMemories() != null
                            ? a.getEpisodicMemories().stream().filter(m -> m.timeStep() <= currentProjectionStep).map(Memory::snapshot).toList()
                            : List.of();
                    newAgentSnaps.put(a.getId(), new AgentSnapshot(
                            a.getId(), a.getName(), a.getAppearance(), a.getSecretGoal(), a.getRole(),
                            a.getPersonality(), a.getRelationship(), alive, arrived, loc,
                            a.isHasMurderIntent(), a.getTargetVictimId(),
                            kbSnaps, memSnaps
                    ));
                }

                Map<String, LocationSnapshot> newLocSnaps = locations.entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().snapshot()));

                Map<String, ItemSnapshot> newItemSnaps = new HashMap<>();
                for (Item i : items.values()) {
                    String loc = itemLocations.getOrDefault(i.getId(), i.getCurrentLocationId());
                    boolean hidden = itemHidden.getOrDefault(i.getId(), i.isHidden());
                    boolean poisoned = itemPoisoned.getOrDefault(i.getId(), i.isPoisoned());
                    boolean consumed = itemPoisonConsumed.getOrDefault(i.getId(), i.isPoisonConsumed());
                    List<KnowledgeSnapshot> nbKb = i.getNotebookKnowledge() != null
                            ? i.getNotebookKnowledge().stream().map(Knowledge::snapshot).toList()
                            : null;
                    newItemSnaps.put(i.getId(), new ItemSnapshot(
                            i.getId(), i.getName(), i.getType(), loc, hidden, i.isMovable(),
                            poisoned, consumed, i.getPoisonedByAgentId(), i.getPoisonTargetAgentId(),
                            i.getPoisonDetails(), nbKb
                    ));
                }

                List<GlobalEventSnapshot> evSnaps = globalEvents.values().stream()
                        .map(e -> e.snapshot(currentProjectionStep))
                        .toList();

                snapshots.put(step, new SimulationStateSnapshot(step, newAgentSnaps, newLocSnaps, newItemSnaps, evSnaps));
            }

            if (evList != null) {
                SimulationStateSnapshot snapNow = snapshots.get(step);
                for (TimeLocationEvent ev : evList) {
                    if (ev.getSnapshotAfter() == null) {
                        ev.setSnapshotAfter(snapNow);
                    }
                }
            }
        }
    }

    public synchronized void reset() {
        this.agents.clear();
        this.locations.clear();
        this.items.clear();
        this.globalEvents.clear();
        this.timelineEvents.clear();
        this.snapshots.clear();
        this.eventHistory.clear();
        this.eventsByAgent.clear();
        this.eventsByLocation.clear();
        this.eventsByItem.clear();
        this.currentStep = 0;
        this.stepsSinceLastIncident = 0;
        this.completed = false;
        this.title = null;
        this.premise = null;
        this.targetSteps = 0;
        this.totalHours = 0;
        this.isWorldGenerated = false;
    }

    public void takeSnapshot() {
        snapshots.put(currentStep, createLiveSnapshot(currentStep));
    }

    public SimulationStateSnapshot getSnapshotAt(int step) {
        SimulationStateSnapshot snap = snapshots.get(step);
        if (snap != null) {
            return snap;
        }
        return createLiveSnapshot(step);
    }

    private SimulationStateSnapshot createLiveSnapshot(int step) {
        Map<String, AgentSnapshot> agentSnaps = agents.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().snapshot()));

        Map<String, LocationSnapshot> placeSnaps = locations.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().snapshot()));

        Map<String, ItemSnapshot> itemSnaps = items.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().snapshot()));
        List<GlobalEventSnapshot> eventSnaps = globalEvents.values().stream()
                .map(e -> e.snapshot(step))
                .collect(Collectors.toList());

        return new SimulationStateSnapshot(step, agentSnaps, placeSnaps, itemSnaps, eventSnaps);
    }

    public AgentSnapshot getAgentSnapshot(String agentId, int step) {
        if (agentId == null) return null;
        return getSnapshotAt(step).agents().get(agentId);
    }

    public LocationSnapshot getLocationSnapshot(String locationId, int step) {
        if (locationId == null) return null;
        return getSnapshotAt(step).locations().get(locationId);
    }

    public ItemSnapshot getItemSnapshot(String itemId, int step) {
        if (itemId == null) return null;
        return getSnapshotAt(step).items().get(itemId);
    }

    public List<AgentSnapshot> getPresentAgentsInLocation(String locationId, int step) {
        if (locationId == null) return List.of();
        SimulationStateSnapshot snap = getSnapshotAt(step);
        return snap.agents().values().stream()
                .filter(a -> locationId.equals(a.currentLocationId()))
                .toList();
    }

    public List<ItemSnapshot> getItemsInLocation(String locationId, int step) {
        if (locationId == null) return List.of();
        SimulationStateSnapshot snap = getSnapshotAt(step);
        return snap.items().values().stream()
                .filter(i -> locationId.equals(i.currentLocationId()))
                .toList();
    }

    public List<ItemSnapshot> getCarriedItems(String agentId, int step) {
        if (agentId == null) return List.of();
        SimulationStateSnapshot snap = getSnapshotAt(step);
        return snap.items().values().stream()
                .filter(i -> agentId.equals(i.currentLocationId()))
                .toList();
    }

    public String resolveAgentName(String agentId, int step) {
        if (agentId == null) return "Unknown";
        AgentSnapshot snap = getAgentSnapshot(agentId, step);
        if (snap != null) return snap.name();
        Agent live = agents.get(agentId);
        return live != null ? live.getName() : agentId;
    }

    public String resolveLocationName(String locationId, int step) {
        if (locationId == null) return "Unknown";
        LocationSnapshot snap = getLocationSnapshot(locationId, step);
        if (snap != null) return snap.name();
        Location live = locations.get(locationId);
        return live != null ? live.getName() : locationId;
    }

    public String getLocationName(String locationId) {
        Optional<Map.Entry<String, Location>> location = locations.entrySet().stream()
                .filter(e-> e.getKey().equals(locationId)).findFirst();

        if (location.isPresent()) {
            return location.get().getValue().getName();
        } else {
            log.error("Location with id " + locationId + " not found");
            return "N/A";
        }
    }

    public void putGlobalEvent(String evId, GlobalEvent event) {
        globalEvents.put(evId, event);
        log.info("GlobalEvent: {}", event.getDescription());
    }
}