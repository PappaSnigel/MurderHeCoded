package com.murderhecoded.service;

import com.murderhecoded.domain.Action;
import com.murderhecoded.domain.ActionType;
import com.murderhecoded.domain.GlobalEvent;
import com.murderhecoded.domain.Scene;
import com.murderhecoded.domain.SceneCategory;
import com.murderhecoded.domain.TimeLocationEvent;
import com.murderhecoded.repository.SimulationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Editorial curation service responsible for measuring drama across TimeLocationEvents,
 * deciding which events belong together in a Scene, framing the opening (firstEvent)
 * and climax/cut (lastEvent), and pruning mundane idle lag.
 */
@Service
@Primary
@Slf4j
@SuppressWarnings("PMD.TooManyMethods")
public class EditorService {
    public static final int MIN_SCENE_STEP_SPAN = 3;
    public static final int MAX_SCENE_STEP_SPAN = 8;

    private final EditorialScorer scorer;
    private LlmSceneDirectorService directorService;

    public EditorService() {
        this(new EditorialScorer(), null);
    }

    public EditorService(EditorialScorer scorer) {
        this(scorer, null);
    }

    @Autowired
    public EditorService(EditorialScorer scorer, @Autowired(required = false) LlmSceneDirectorService directorService) {
        this.scorer = scorer != null ? scorer : new EditorialScorer();
        this.directorService = directorService;
    }

    public void setDirectorService(LlmSceneDirectorService directorService) {
        this.directorService = directorService;
    }

    public LlmSceneDirectorService getDirectorService() {
        return directorService;
    }

    public EditorialScorer getScorer() {
        return scorer;
    }

    public double evaluateImportance(List<TimeLocationEvent> events, SimulationRepository state) {
        return scorer.evaluateImportance(events, state);
    }

    public double evaluateSuspense(List<TimeLocationEvent> events, SimulationRepository state) {
        return scorer.evaluateSuspense(events, state);
    }

    public SceneCategory determineCategory(double totalScore, boolean hasMurderOrAccusal) {
        return scorer.determineCategory(totalScore, hasMurderOrAccusal);
    }

    public boolean determineSelection(SceneCategory category, double totalScore, double importanceScore, boolean hasMurderOrAccuse) {
        return scorer.determineSelection(category, totalScore, importanceScore, hasMurderOrAccuse);
    }

    /**
     * Measures the multi-factor drama score for an individual TimeLocationEvent (0.0 - 10.0),
     * combining action severity, role tension, and revelation impact.
     */
    public double measureDrama(TimeLocationEvent event, SimulationRepository state) {
        if (event == null) {
            return 1.0;
        }
        double importance = scorer.evaluateImportance(List.of(event), state);
        double suspense = scorer.evaluateSuspense(List.of(event), state);

        double composite = (importance * 0.6) + (suspense * 0.4);
        double rounded = Math.round(Math.min(10.0, Math.max(1.0, composite)) * 10.0) / 10.0;
        event.setDramaScore(rounded);
        return rounded;
    }

    /**
     * Curates recorded TimeLocationEvents into ordered, location-pure, cohesive Scene objects.
     * Each scene strictly contains events from only ONE location and typically spans multiple
     * time steps (3-8 steps). Scenes across different locations are interleaved chronologically.
     */
    public List<Scene> createScenes(List<TimeLocationEvent> rawEvents, SimulationRepository state) {
        if (rawEvents == null || rawEvents.isEmpty()) {
            log.info("Editor: Not time to create any scenes (raw event list is empty or null).");
            return Collections.emptyList();
        }

        log.info("Editor: Evaluating {} raw events across locations to curate cohesive single-location scenes...", rawEvents.size());

        // 1. Measure drama on all events
        for (TimeLocationEvent ev : rawEvents) {
            measureDrama(ev, state);
        }

        // 2. Group events strictly by locationId
        Map<String, List<TimeLocationEvent>> byLocation = new LinkedHashMap<>();
        for (TimeLocationEvent ev : rawEvents) {
            if (ev.getLocationId() != null && !ev.getLocationId().isBlank()) {
                byLocation.computeIfAbsent(ev.getLocationId(), k -> new ArrayList<>()).add(ev);
            }
        }

        if (byLocation.isEmpty()) {
            log.info("Editor: Not time to create any scenes (no events with valid location IDs found out of {} raw events).", rawEvents.size());
            return Collections.emptyList();
        }

        List<Scene> allCuratedScenes = new ArrayList<>();

        // 3. For each location, segment its events into cohesive single-location scenes
        for (Map.Entry<String, List<TimeLocationEvent>> entry : byLocation.entrySet()) {
            String locationId = entry.getKey();
            List<TimeLocationEvent> locationEvents = entry.getValue();

            // Sort chronologically within the location
            locationEvents.sort(Comparator.comparingInt(TimeLocationEvent::getTimeStep)
                    .thenComparingInt(TimeLocationEvent::getSequenceNumber));

            List<Scene> locationScenes = curateLocationEpisodes(locationId, locationEvents, state);
            allCuratedScenes.addAll(locationScenes);
        }

        // Fallback: If all episodes were deemed mundane but events exist, curate at least the most dramatic location
        if (allCuratedScenes.isEmpty()) {
            String fallbackLocId = byLocation.keySet().iterator().next();
            List<TimeLocationEvent> fallbackEvents = byLocation.get(fallbackLocId);
            Scene fallbackScene = assembleScene(1, fallbackEvents, state);
            if (fallbackScene != null) {
                allCuratedScenes.add(fallbackScene);
            }
        }

        // 4. Chronologically interleave scenes across all locations
        allCuratedScenes.sort(Comparator
                .comparingInt(Scene::getStartStep)
                .thenComparingInt(Scene::getEndStep)
                .thenComparing(Scene::getPrimaryLocationId));

        // 5. Renumber consecutively 1, 2, 3...
        for (int i = 0; i < allCuratedScenes.size(); i++) {
            allCuratedScenes.get(i).setSceneNumber(i + 1);
        }

        log.info("Editor: Finished creating scenes. Total cohesive scenes curated: {}.", allCuratedScenes.size());
        return allCuratedScenes;
    }

    /**
     * Curates scenes using the hybrid architecture:
     * 1. Deterministic spatial & temporal event clustering (Phase 3A)
     * 2. LLM Scene Director enrichment (Phase 3B) if enabled
     */
    public List<Scene> createScenes(
            List<TimeLocationEvent> rawEvents,
            SimulationRepository state,
            boolean useLlmDirector,
            String authorVoice,
            String title,
            String premise,
            Consumer<String> logConsumer,
            Consumer<Double> progressConsumer
    ) {
        List<Scene> candidateScenes = createScenes(rawEvents, state);
        if (candidateScenes.isEmpty()) {
            return candidateScenes;
        }

        if (useLlmDirector) {
            if (directorService != null) {
                if (logConsumer != null) {
                    logConsumer.accept(String.format("🎬 [Phase 3B] Startar LLM Scene Director för %d scener...", candidateScenes.size()));
                }
                return directorService.enrichScenes(candidateScenes, state, authorVoice, title, premise, logConsumer, progressConsumer);
            } else {
                String warn = "⚠️ [Phase 3] LLM Scene Director är inte tillgänglig (directorService är null). Använder algoritmisk kurering.";
                if (logConsumer != null) {
                    logConsumer.accept(warn);
                }
                log.warn("EditorService: useLlmDirector was true but directorService is null!");
            }
        }

        return candidateScenes;
    }

    /**
     * Curates events for a specific location into multi-step episodes (scenes),
     * respecting natural breaks (climax, vacancy, agent exit, or max step duration).
     */
    private List<Scene> curateLocationEpisodes(String locationId, List<TimeLocationEvent> events, SimulationRepository state) {
        if (events == null || events.isEmpty()) {
            return Collections.emptyList();
        }

        List<Scene> scenes = new ArrayList<>();
        List<TimeLocationEvent> currentEpisode = new ArrayList<>();
        int episodeStartStep = -1;

        for (int i = 0; i < events.size(); i++) {
            TimeLocationEvent ev = events.get(i);
            int step = ev.getTimeStep();
            boolean isLast = (i == events.size() - 1);

            if (currentEpisode.isEmpty()) {
                episodeStartStep = step;
            }
            currentEpisode.add(ev);

            TimeLocationEvent nextEv = isLast ? null : events.get(i + 1);
            boolean shouldBreak = shouldBreakLocationEpisode(currentEpisode, ev, nextEv, episodeStartStep, isLast, state);

            if (shouldBreak) {
                if (isEpisodeMeaningful(currentEpisode, state)) {
                    Scene scene = assembleScene(0, currentEpisode, state);
                    if (scene != null) {
                        scenes.add(scene);
                        log.info("Editor: Curated Episode Scene [Steps {}-{} @ '{}'] with {} events (Drama: {}, Category: {}).",
                                scene.getStartStep(), scene.getEndStep(), scene.getPrimaryLocationName(),
                                scene.getEvents().size(), scene.getDramaScore(), scene.getCategory());
                    }
                } else {
                    log.debug("Editor: Pruned mundane/idle episode at '{}' [Steps {}-{}] (Peak Drama: {}).",
                            locationId, episodeStartStep, step,
                            currentEpisode.stream().mapToDouble(TimeLocationEvent::getDramaScore).max().orElse(1.0));
                }
                currentEpisode = new ArrayList<>();
                episodeStartStep = -1;
            }
        }

        return scenes;
    }

    /**
     * Determines whether an episode in a specific location should conclude.
     */
    private boolean shouldBreakLocationEpisode(
            List<TimeLocationEvent> episode,
            TimeLocationEvent currentEv,
            TimeLocationEvent nextEv,
            int episodeStartStep,
            boolean isLast,
            SimulationRepository state
    ) {
        if (isLast) {
            return true;
        }

        int currentStep = currentEv.getTimeStep();
        int stepSpan = currentStep - episodeStartStep + 1;

        // 1. Climax cutoff: Murder, Accusation, or Dead body discovered terminates the scene
        boolean hasClimax = currentEv.hasMurderAction() || currentEv.hasAccuseAction()
                || (currentEv.getKnowledgeGained() != null && currentEv.getKnowledgeGained().stream()
                        .anyMatch(k -> k.toLowerCase().contains("corpse") || k.toLowerCase().contains("dead body")));
        if (hasClimax) {
            log.info("Editor: Location episode at {} breaks on climax action at step {} (span: {} steps).",
                    currentEv.getLocationId(), currentStep, stepSpan);
            return true;
        }

        // 2. Room vacancy / temporal gap between events in this location (> 1 step gap)
        if (nextEv != null && nextEv.getTimeStep() - currentStep > 1) {
            log.info("Editor: Location episode at {} breaks due to vacancy/time gap (step {} -> {}).",
                    currentEv.getLocationId(), currentStep, nextEv.getTimeStep());
            return true;
        }

        // 3. Room became empty: only if previous events in this episode tracked agents and now it is explicitly empty with no actions
        boolean wasPopulated = episode.stream().anyMatch(e -> e.getPresentAgentNames() != null && !e.getPresentAgentNames().isEmpty());
        boolean nowEmpty = currentEv.getPresentAgentNames() != null && currentEv.getPresentAgentNames().isEmpty()
                && (currentEv.getActions() == null || currentEv.getActions().isEmpty());
        if (wasPopulated && nowEmpty) {
            log.info("Editor: Location episode at {} breaks as room became empty at step {}.",
                    currentEv.getLocationId(), currentStep);
            return true;
        }

        // 4. Maximum temporal threshold reached (8 steps / 2 hours)
        if (stepSpan >= MAX_SCENE_STEP_SPAN) {
            log.info("Editor: Location episode at {} reached max step span ({} steps [steps {}-{}]).",
                    currentEv.getLocationId(), stepSpan, episodeStartStep, currentStep);
            return true;
        }

        // 5. Global event boundary (e.g. storm breaks, alarm sounds) after minimum accumulation
        if (isGlobalEventBoundaryAtStep(currentStep, state) && stepSpan >= MIN_SCENE_STEP_SPAN) {
            log.info("Editor: Location episode at {} breaks on global event boundary at step {}.",
                    currentEv.getLocationId(), currentStep);
            return true;
        }

        // 6. Dramatic tension resolution: peak >= 6.0 drops to mundane <= 3.0 after at least MIN_SCENE_STEP_SPAN steps
        if (stepSpan >= MIN_SCENE_STEP_SPAN) {
            double peakDrama = episode.stream().mapToDouble(TimeLocationEvent::getDramaScore).max().orElse(1.0);
            if (peakDrama >= 6.0 && currentEv.getDramaScore() <= 3.0) {
                log.info("Editor: Location episode at {} breaks on dramatic tension resolution at step {}.",
                        currentEv.getLocationId(), currentStep);
                return true;
            }
        }

        return false;
    }

    /**
     * Evaluates whether an episode has sufficient narrative weight, clues, or interaction
     * to warrant becoming a scene in the novel, pruning empty idle turns.
     */
    public boolean isEpisodeMeaningful(List<TimeLocationEvent> episode, SimulationRepository state) {
        if (episode == null || episode.isEmpty()) {
            return false;
        }

        // Rule A: Any murder, accusation, or corpse discovery is essential
        boolean hasClimax = episode.stream().anyMatch(e ->
                e.hasMurderAction() || e.hasAccuseAction() ||
                (e.getKnowledgeGained() != null && e.getKnowledgeGained().stream()
                        .anyMatch(k -> k.toLowerCase().contains("corpse") || k.toLowerCase().contains("dead body")))
        );
        if (hasClimax) {
            return true;
        }

        // Rule B: Any knowledge gained (clue, secret, testimony)
        boolean hasKnowledge = episode.stream().anyMatch(e ->
                e.getKnowledgeGained() != null && !e.getKnowledgeGained().isEmpty()
        );
        if (hasKnowledge) {
            return true;
        }

        // Prune redundant solitary idle loops: agent alone repeatedly searching/observing with zero clues, no dialogue, no items
        boolean anyDialogueOrItem = episode.stream().flatMap(e -> e.getActions().stream())
                .anyMatch(a -> a.getActionType() == ActionType.TALK 
                        || a.getActionType() == ActionType.MANIPULATE_ITEM 
                        || a.getActionType() == ActionType.ALTER_ENVIRONMENT);
        boolean anyFinding = episode.stream().flatMap(e -> e.getActions().stream())
                .anyMatch(a -> a.getNewKnowledgeFact() != null && !a.getNewKnowledgeFact().trim().isEmpty() && !"None".equalsIgnoreCase(a.getNewKnowledgeFact()));
        boolean multipleOccupants = episode.stream().anyMatch(e -> e.getPresentAgentNames() != null && e.getPresentAgentNames().size() > 1);

        if (!anyDialogueOrItem && !anyFinding && !multipleOccupants && episode.size() > 2) {
            log.info("Editor: Pruned redundant solitary idle loop at {} ({} steps of empty searching/observing alone with zero findings).",
                    episode.get(0).getLocationId(), episode.size());
            return false;
        }

        // Rule C: Active dramatic interactions (TALK, SEARCH, MANIPULATE_ITEM, ALTER_ENVIRONMENT)
        boolean hasActiveActions = episode.stream().flatMap(e -> e.getActions().stream()).anyMatch(a -> {
            ActionType t = a.getActionType();
            return t == ActionType.TALK || t == ActionType.SEARCH
                    || t == ActionType.MANIPULATE_ITEM || t == ActionType.ALTER_ENVIRONMENT;
        });
        if (hasActiveActions) {
            return true;
        }

        // Rule D: Peak drama score threshold (>= 3.5 indicates notable suspense/tension)
        double peakDrama = episode.stream().mapToDouble(TimeLocationEvent::getDramaScore).max().orElse(1.0);
        return peakDrama >= 3.5;
    }

    /**
     * Chooses which events belonging to the primary location should be included in the curated Scene.
     * Strictly enforces that events from other locations are NEVER included.
     */
    public List<TimeLocationEvent> selectEventsForScene(List<TimeLocationEvent> candidateEvents, SimulationRepository state) {
        if (candidateEvents == null || candidateEvents.isEmpty()) {
            return Collections.emptyList();
        }

        for (TimeLocationEvent ev : candidateEvents) {
            if (ev.getDramaScore() <= 0.0) {
                measureDrama(ev, state);
            }
        }

        String primaryLocId = findPrimaryLocationId(candidateEvents);

        List<TimeLocationEvent> selected = new ArrayList<>();
        for (TimeLocationEvent ev : candidateEvents) {
            boolean isPrimaryRoom = Objects.equals(ev.getLocationId(), primaryLocId);
            if (!isPrimaryRoom) {
                // Invariant: A scene can strictly only contain events from ONE single location.
                continue;
            }

            boolean isMeaningful = !ev.isOnlyMundaneObserving()
                    || ev.hasMurderAction()
                    || ev.hasAccuseAction()
                    || ev.getDramaScore() >= 3.5
                    || (ev.getKnowledgeGained() != null && !ev.getKnowledgeGained().isEmpty());

            if (isMeaningful) {
                selected.add(ev);
            }
        }

        if (selected.isEmpty()) {
            selected = candidateEvents.stream()
                    .filter(e -> Objects.equals(e.getLocationId(), primaryLocId))
                    .collect(Collectors.toList());
            if (selected.isEmpty()) {
                selected = new ArrayList<>(candidateEvents);
            }
        }

        selected.sort(Comparator.comparingInt(TimeLocationEvent::getTimeStep)
                .thenComparingInt(TimeLocationEvent::getSequenceNumber));

        return selected;
    }

    /**
     * Assembles a single cohesive Scene from a candidate list of events,
     * framing the firstEvent (hook/entrance) and lastEvent (climax/cut),
     * and pruning mundane idle turns outside the dramatic boundary.
     */
    public Scene assembleScene(int sceneNumber, List<TimeLocationEvent> candidateEvents, SimulationRepository state) {
        if (candidateEvents == null || candidateEvents.isEmpty()) {
            log.info("Editor: Not time to create scene {} (candidate events list is empty or null).", sceneNumber);
            return null;
        }

        List<TimeLocationEvent> selectedEvents = selectEventsForScene(candidateEvents, state);
        if (selectedEvents.isEmpty()) {
            selectedEvents = candidateEvents;
        }

        // Determine firstEvent: the first meaningful action or arrival (prune leading empty idle lag)
        TimeLocationEvent firstEvent = findFirstEvent(selectedEvents);
        // Determine lastEvent: the climax or decisive turning point (prune trailing empty idle lag)
        TimeLocationEvent lastEvent = findLastEvent(selectedEvents, firstEvent);

        int firstStep = firstEvent.getTimeStep();
        int lastStep = lastEvent.getTimeStep();

        // Prune events outside dramatic framing [firstStep, lastStep]
        List<TimeLocationEvent> curatedEvents = selectedEvents.stream()
                .filter(e -> e.getTimeStep() >= firstStep && e.getTimeStep() <= lastStep)
                .sorted(Comparator.comparingInt(TimeLocationEvent::getTimeStep).thenComparingInt(TimeLocationEvent::getSequenceNumber))
                .toList();
        if (curatedEvents.isEmpty()) {
            curatedEvents = selectedEvents;
        }

        // Location identification
        String primaryLocId = findPrimaryLocationId(curatedEvents);
        String primaryLocName = state != null && state.getLocations().containsKey(primaryLocId)
                ? state.getLocations().get(primaryLocId).getName() : primaryLocId;

        // Editorial scoring
        double importance = scorer.evaluateImportance(curatedEvents, state);
        double suspense = scorer.evaluateSuspense(curatedEvents, state);
        double peakDrama = curatedEvents.stream().mapToDouble(TimeLocationEvent::getDramaScore).max().orElse(1.0);
        double totalScore = Math.round(((importance * 0.6) + (suspense * 0.4)) * 10.0) / 10.0;

        boolean hasMurderOrAccuse = curatedEvents.stream().flatMap(e -> e.getActions().stream()).anyMatch(a ->
                a.getActionType() == ActionType.MURDER || a.getActionType() == ActionType.ACCUSE
        );

        SceneCategory category = scorer.determineCategory(totalScore, hasMurderOrAccuse);
        boolean isSelected = scorer.determineSelection(category, totalScore, importance, hasMurderOrAccuse);
        String dramaticFocus = scorer.determineDramaticFocus(curatedEvents, state, primaryLocName);
        String summaryText = createEditorialSummary(state, primaryLocName, firstStep, lastStep);

        log.info("Editor: Assembled Scene {} [Steps {}-{} @ '{}'] with {} curated events (Peak Drama: {}/10, Category: {}). Hook: Step {}, Climax: Step {}.",
                sceneNumber, firstStep, lastStep, primaryLocName, curatedEvents.size(), peakDrama, category, firstStep, lastStep);

        return Scene.builder()
                .sceneNumber(sceneNumber)
                .startStep(firstStep)
                .endStep(lastStep)
                .primaryLocationId(primaryLocId)
                .primaryLocationName(primaryLocName)
                .dramaticFocus(dramaticFocus)
                .firstEvent(firstEvent)
                .lastEvent(lastEvent)
                .events(new ArrayList<>(curatedEvents))
                .dramaScore(peakDrama)
                .narrativeImportanceScore(importance)
                .suspenseScore(suspense)
                .totalEditorialScore(totalScore)
                .category(category)
                .summaryText(summaryText)
                .isSelected(isSelected)
                .build();
    }

    private TimeLocationEvent findFirstEvent(List<TimeLocationEvent> events) {
        // Find the first event with an active action or revelation
        for (TimeLocationEvent ev : events) {
            if (!ev.isOnlyMundaneObserving() || ev.hasHighImpactAction()) {
                return ev;
            }
        }
        // Default to the first chronological event if all are mundane
        return events.get(0);
    }

    private TimeLocationEvent findLastEvent(List<TimeLocationEvent> events, TimeLocationEvent firstEvent) {
        // Climax priority: if a murder or accuse occurred, that is the definitive scene cutoff
        for (int i = events.size() - 1; i >= 0; i--) {
            TimeLocationEvent ev = events.get(i);
            if (ev.hasMurderAction() || ev.hasAccuseAction()) {
                return ev;
            }
        }

        // Next priority: find the last event with high drama or active action
        for (int i = events.size() - 1; i >= 0; i--) {
            TimeLocationEvent ev = events.get(i);
            if (ev.getTimeStep() >= firstEvent.getTimeStep() && (!ev.isOnlyMundaneObserving() || ev.getDramaScore() >= 5.0)) {
                return ev;
            }
        }

        // Fallback: the last chronological event
        return events.get(events.size() - 1);
    }

    /**
     * Determines whether it is time to create a scene based on events accumulated across
     * all locations since the last scene ended.
     * A scene can span anywhere from 1 up to 10 time steps.
     */
    public boolean isTimeToMakeScene(
            List<TimeLocationEvent> accumulatedEvents,
            int currentStep,
            int windowStartStep,
            SimulationRepository state,
            boolean isLastStep
    ) {
        if (accumulatedEvents == null || accumulatedEvents.isEmpty()) {
            return false;
        }

        if (isLastStep) {
            log.info("Editor: Time to create scene! Final step of event stream reached at step {}.", currentStep);
            return true;
        }

        int stepSpan = currentStep - windowStartStep + 1;

        // Rule 1: Immediate break on high-impact climax (Murder, Accusation, Dead body)
        // Must be checked before MIN_SCENE_STEP_SPAN because climaxes must never be delayed!
        boolean hasClimaxAtCurrentStep = accumulatedEvents.stream()
                .filter(e -> e.getTimeStep() == currentStep)
                .anyMatch(e -> e.hasMurderAction() || e.hasAccuseAction()
                        || (e.getKnowledgeGained() != null && e.getKnowledgeGained().stream()
                                .anyMatch(k -> k.toLowerCase().contains("corpse") || k.toLowerCase().contains("dead body"))));
        if (hasClimaxAtCurrentStep) {
            log.info("Editor: Time to create scene! Climax action (Murder/Accuse) occurred at step {} (span: {} steps).",
                    currentStep, stepSpan);
            return true;
        }

        if (stepSpan < MIN_SCENE_STEP_SPAN) {
            return false;
        }

        // Rule 2: Forced boundary when maximum temporal threshold of 10 steps is reached
        if (stepSpan >= MAX_SCENE_STEP_SPAN) {
            log.info("Editor: Time to create scene! Maximum temporal threshold reached ({} steps [steps {}-{}]).",
                    stepSpan, windowStartStep, currentStep);
            return true;
        }

        // Rule 3: Global event boundary between steps
        if (isGlobalEventBoundaryAtStep(currentStep, state) && stepSpan >= 2) {
            log.info("Editor: Time to create scene! Global event boundary encountered at step {} (span: {} steps).",
                    currentStep, stepSpan);
            return true;
        }

        // Rule 4: Dramatic climax resolution after reasonable accumulation (>= 3 steps)
        if (stepSpan >= 3) {
            double peakDramaInWindow = accumulatedEvents.stream()
                    .mapToDouble(TimeLocationEvent::getDramaScore)
                    .max().orElse(1.0);
            double currentStepMaxDrama = accumulatedEvents.stream()
                    .filter(e -> e.getTimeStep() == currentStep)
                    .mapToDouble(TimeLocationEvent::getDramaScore)
                    .max().orElse(1.0);
            if (peakDramaInWindow >= 7.0 && currentStepMaxDrama <= 3.5) {
                log.info("Editor: Time to create scene! Dramatic peak ({}/10) resolved at step {} (span: {} steps).",
                        peakDramaInWindow, currentStep, stepSpan);
                return true;
            }
        }

        return false;
    }

    private boolean isGlobalEventBoundaryAtStep(int currentStep, SimulationRepository state) {
        if (state == null || state.getGlobalEvents() == null || state.getGlobalEvents().isEmpty()) {
            return false;
        }
        return state.getGlobalEvents().values().stream().anyMatch(ge ->
                ge.getScheduledStep() == currentStep ||
                        (ge.getScheduledStep() + Math.max(1, ge.getDurationSteps())) == currentStep
        );
    }

    public boolean shouldBreakScene(List<TimeLocationEvent> currentBatch, TimeLocationEvent incoming, boolean queueEmpty) {
        return shouldBreakScene(currentBatch, incoming, queueEmpty, null);
    }

    public boolean shouldBreakScene(List<TimeLocationEvent> currentBatch, TimeLocationEvent incoming, boolean queueEmpty, SimulationRepository state) {
        if (currentBatch == null || currentBatch.isEmpty()) {
            return false;
        }

        // Strict location purity invariant: If incoming event is in a different room, break!
        String batchLocId = currentBatch.get(0).getLocationId();
        if (incoming != null && incoming.getLocationId() != null && !Objects.equals(batchLocId, incoming.getLocationId())) {
            log.info("Editor: Location changed from '{}' to '{}'. Breaking scene to maintain location purity.",
                    batchLocId, incoming.getLocationId());
            return true;
        }

        if (incoming != null && (incoming.hasMurderAction() || incoming.hasAccuseAction()
                || (incoming.getKnowledgeGained() != null && incoming.getKnowledgeGained().stream()
                        .anyMatch(k -> k.toLowerCase().contains("corpse") || k.toLowerCase().contains("dead body"))))) {
            log.info("Editor: Time to create scene! Incoming event at step {} has climax action.", incoming.getTimeStep());
            return true;
        }

        int minStep = currentBatch.stream().mapToInt(TimeLocationEvent::getTimeStep).min().orElse(incoming != null ? incoming.getTimeStep() : 1);
        int maxStep = Math.max(currentBatch.stream().mapToInt(TimeLocationEvent::getTimeStep).max().orElse(1), incoming != null ? incoming.getTimeStep() : 1);

        return isTimeToMakeScene(currentBatch, maxStep, minStep, state, queueEmpty);
    }

    public String formatSceneTimeSpan(Scene scene, SimulationRepository state) {
        if (scene == null) {
            return "Unknown";
        }
        int start = scene.getStartStep();
        int end = scene.getEndStep();
        if (state == null) {
            return start == end ? "Step " + start : "Steps " + start + "-" + end;
        }
        String clockInterval = state.formatInterval(start, end);
        if (start == end) {
            return String.format("Step %d (%s)", start, state.getClockTime(start));
        }
        return String.format("Steps %d-%d (%s)", start, end, clockInterval);
    }

    private String findPrimaryLocationId(List<TimeLocationEvent> events) {
        for (TimeLocationEvent e : events) {
            if (e.hasHighImpactAction()) {
                return e.getLocationId();
            }
        }
        Map<String, Long> freq = events.stream()
                .collect(Collectors.groupingBy(TimeLocationEvent::getLocationId, Collectors.counting()));
        return freq.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(events.get(0).getLocationId());
    }

    private String createEditorialSummary(SimulationRepository state, String locName, int startStep, int endStep) {
        if (state == null) {
            return "Dramatic events unfolded in " + locName;
        }
        String startTime = state.getClockTime(startStep);
        String endTime = state.getClockTime(endStep);
        return String.format("Between %s and %s, quiet murmurs and watchful eyes drifted through the %s as the guests moved uneasily across the estate.",
                startTime, endTime, locName);
    }
}
