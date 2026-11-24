package com.murderhecoded.service;

import com.murderhecoded.domain.Action;
import com.murderhecoded.domain.ActionType;
import com.murderhecoded.domain.Chapter;
import com.murderhecoded.domain.Scene;
import com.murderhecoded.domain.SceneCategory;
import com.murderhecoded.domain.SimulationStateSnapshot;
import com.murderhecoded.domain.AgentSnapshot;
import com.murderhecoded.domain.TimeLocationEvent;
import com.murderhecoded.domain.Agent;
import com.murderhecoded.domain.Item;
import com.murderhecoded.domain.ItemType;
import com.murderhecoded.domain.Knowledge;
import com.murderhecoded.domain.Location;
import com.murderhecoded.llm.LlmExecutor;
import com.murderhecoded.llm.LlmRole;
import com.murderhecoded.repository.SimulationRepository;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
@SuppressWarnings("PMD")
public class ProseService {
    private static final Logger log = LoggerFactory.getLogger(ProseService.class);
    private final OllamaLlmService ollamaLlmService;
    private final LlmExecutor llmExecutor;
    private final SimulationRepository state;
    private final EditorService editorService;

    @Value("classpath:/prompts/prose/scene-prose.st")
    private Resource sceneProsePromptResource = new ClassPathResource("prompts/prose/scene-prose.st");
    @Value("classpath:/prompts/prose/resolution-prose.st")
    private Resource resolutionProsePromptResource = new ClassPathResource("prompts/prose/resolution-prose.st");

    private final BlockingQueue<TimeLocationEvent> eventQueue = new LinkedBlockingQueue<>();
    private final List<TimeLocationEvent> currentBatch = new ArrayList<>();
    private final List<Chapter> generatedChapters = new ArrayList<>();
    private int sceneSequence = 1;

    private final java.util.concurrent.atomic.AtomicInteger totalScenesEnqueued = new java.util.concurrent.atomic.AtomicInteger(0);
    private final java.util.concurrent.atomic.AtomicInteger totalScenesProcessed = new java.util.concurrent.atomic.AtomicInteger(0);
    private final java.util.concurrent.atomic.AtomicInteger totalProseGenerated = new java.util.concurrent.atomic.AtomicInteger(0);

    private volatile Thread workerThread;
    private volatile boolean running = false;
    private volatile Consumer<Chapter> onChapterGenerated;
    private volatile Consumer<String> onLogEvent;
    private volatile Runnable onCompleteCallback;
    private volatile boolean generateProseText = true;

    private volatile String language = "en";
    private volatile String title = "";
    private volatile String premise = "";
    private volatile String voice = "Default";
    private volatile String sceneLength = "Medium";

    @Autowired
    public ProseService(OllamaLlmService ollamaLlmService, LlmExecutor llmExecutor, SimulationRepository state, EditorService editorService) {
        this.ollamaLlmService = ollamaLlmService;
        this.llmExecutor = llmExecutor != null ? llmExecutor : new LlmExecutor();
        this.state = state;
        this.editorService = editorService;
    }

    public ProseService(OllamaLlmService ollamaLlmService, SimulationRepository state, EditorService editorService) {
        this(ollamaLlmService, new LlmExecutor(), state, editorService);
    }

    public void setSceneProsePromptResource(Resource sceneProsePromptResource) {
        this.sceneProsePromptResource = sceneProsePromptResource;
    }

    public void setOnChapterGenerated(Consumer<Chapter> callback) {
        this.onChapterGenerated = callback;
    }

    public void setOnLogEvent(Consumer<String> callback) {
        this.onLogEvent = callback;
    }

    public void setOnCompleteCallback(Runnable callback) {
        this.onCompleteCallback = callback;
    }

    public void setGenerateProseText(boolean generateProseText) {
        this.generateProseText = generateProseText;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public void setContext(String title, String premise, String voice, String sceneLength) {
        this.title = title;
        this.premise = premise;
        this.voice = voice;
        this.sceneLength = sceneLength;
    }

    public void restoreState(int enqueued, int processed, int nextSceneSequence, List<Chapter> existingChapters) {
        if (!running) {
            this.totalScenesEnqueued.set(enqueued);
            this.totalScenesProcessed.set(processed);
            this.sceneSequence = nextSceneSequence;
            this.generatedChapters.clear();
            if (existingChapters != null) {
                this.generatedChapters.addAll(existingChapters);
            }
        }
    }

    public synchronized void start(boolean isResume) {
        if (running) {
            return;
        }
        running = true;
        if (!isResume) {
            currentBatch.clear();
            generatedChapters.clear();
            eventQueue.clear();
            sceneSequence = 1;
            totalScenesEnqueued.set(0);
            totalScenesProcessed.set(0);
            totalProseGenerated.set(0);
        } else {
            eventQueue.clear();
        }

        workerThread = new Thread(this::processQueue, "ProseGenerator-Worker");
        workerThread.setDaemon(true);
        workerThread.start();
        log.info("Prose generation pipeline started (Prose Text Generation: {}, isResume: {})", generateProseText, isResume);
    }

    public synchronized void start() {
        start(false);
    }

    public void stop() {
        running = false;
        onCompleteCallback = null;
        Thread t = workerThread;
        if (t != null) {
            t.interrupt();
        }
        eventQueue.clear();
        currentBatch.clear();
        log.info("Prose generation pipeline stopped");
    }

    /**
     * Curates recorded raw events into framed scenes via EditorService and generates prose for each scene.
     */
    public synchronized void generateProseForRecordedEvents(List<TimeLocationEvent> events, Runnable onFinished) {
        if (running) {
            log.warn("Prose generation pipeline is already running.");
            return;
        }
        if (events == null || events.isEmpty()) {
            String msg = "[Editor] Not time to create any scenes: recorded events list is empty or null.";
            log.info(msg);
            logScene(msg);
            if (onFinished != null) {
                try {
                    Platform.runLater(onFinished);
                } catch (IllegalStateException e) {
                    onFinished.run();
                }
            }
            return;
        }

        List<Scene> scenes = editorService.createScenes(events, state);
        if (scenes.isEmpty()) {
            String msg = String.format("[Editor] Not time to create scenes: no valid scenes curated from %d events.", events.size());
            log.info(msg);
            logScene(msg);
        } else {
            String msg = String.format("[Editor] Time to create scenes! Curated %d scenes from %d recorded events.", scenes.size(), events.size());
            log.info(msg);
            logScene(msg);
        }
        generateProseForScenes(scenes, onFinished);
    }

    /**
     * Generates prose directly for a single curated Scene using Spring AI ChatClient and LlmExecutor.
     *
     * @param scene The curated Scene with framed firstEvent and lastEvent boundaries
     * @return Generated Chapter object
     */
    public Chapter generateProseForScene(Scene scene) {
        if (scene == null) {
            return null;
        }

        String timeSpan = editorService.formatSceneTimeSpan(scene, state);
        String selStatus = scene.isSelected() ? "SELECTED" : "CUT";

        String assembleLogMsg = String.format(
            "[Scene %d Assembled & Scored] Time: %s | Room: %s | Score: Importance %.1f/10, Suspense %.1f/10 [%s] | Final Cut: %s (isSelected=%b)",
            scene.getSceneNumber(),
            timeSpan,
            scene.getPrimaryLocationName(),
            scene.getNarrativeImportanceScore(),
            scene.getSuspenseScore(),
            scene.getCategory().getDisplayName(),
            selStatus,
            scene.isSelected()
        );
        log.info(assembleLogMsg);
        logScene(assembleLogMsg);

        String chapterText;
        if (generateProseText) {
            Map<String, Object> promptParams = buildProsePromptParams(scene);
            String prompt = buildProsePrompt(scene);
            log.info("--- PROSE ENGINE LLM PROMPT (Scene {} - {} [Model: {}]) ---\n{}",
                scene.getSceneNumber(), scene.getDramaticFocus(), ollamaLlmService.getModel(LlmRole.PROSE), prompt);

            try {
                chapterText = llmExecutor.execute(
                    () -> {
                        if (Thread.currentThread().isInterrupted()) {
                            return "";
                        }
                        var client = ollamaLlmService.getChatClient(LlmRole.PROSE);
                        if (client != null) {
                            return client.prompt()
                                    .user(u -> u.text(sceneProsePromptResource).params(promptParams))
                                    .call()
                                    .content();
                        }
                        return ollamaLlmService.generateText(prompt, LlmRole.PROSE);
                    },
                    text -> text != null && !text.isBlank(),
                    "Generated prose was empty or null",
                    3,
                    3
                );
            } catch (Exception e) {
                log.warn("Spring AI ChatClient execution via LlmExecutor threw exception, attempting fallback: {}", e.getMessage());
                chapterText = ollamaLlmService.generateText(prompt, LlmRole.PROSE);
            }

            if (chapterText == null || chapterText.isBlank()) {
                chapterText = "[Prose generation unavailable]\n\n" + (scene.getSummaryText() != null ? scene.getSummaryText() : "");
            }
        } else {
            chapterText = "[HÄNDELSELOGG (Prosa avstängd)]\n\n" + (scene.getSummaryText() != null ? scene.getSummaryText() : "");
        }
        scene.setProseText(chapterText);

        Chapter chapter = new Chapter(
            chapterText,
            scene.isPivotal(),
            scene.isDispensable(),
            scene.getEndStep(),
            scene.getSceneNumber(),
            scene.getPrimaryLocationName(),
            scene.getNarrativeImportanceScore(),
            scene.getSuspenseScore(),
            scene.getCategory(),
            scene.getDramaticFocus(),
            scene.getStartStep(),
            scene.getEndStep(),
            timeSpan,
            scene.isSelected()
        );

        generatedChapters.add(chapter);
        totalScenesProcessed.addAndGet(scene.getEvents() != null ? scene.getEvents().size() : 1);
        totalProseGenerated.incrementAndGet();

        String proseLogMsg = String.format(
            "[Scene %d Prose Generated] Time: %s | Focus: \"%s\" (%d words)",
            scene.getSceneNumber(),
            timeSpan,
            scene.getDramaticFocus(),
            chapter.wordCount()
        );
        log.info(proseLogMsg);
        logScene(proseLogMsg);

        if (onChapterGenerated != null) {
            try {
                Platform.runLater(() -> onChapterGenerated.accept(chapter));
            } catch (IllegalStateException e) {
                onChapterGenerated.accept(chapter);
            }
        }

        return chapter;
    }

    /**
     * Asynchronously generates prose for a list of pre-curated scenes.
     */
    public synchronized void generateProseForScenes(List<Scene> scenes, Runnable onFinished) {
        if (running) {
            log.warn("Prose generation pipeline is already running.");
            return;
        }
        this.generateProseText = true;
        this.onCompleteCallback = onFinished;
        this.running = true;

        if (scenes == null || scenes.isEmpty()) {
            this.running = false;
            if (onFinished != null) {
                try {
                    Platform.runLater(onFinished);
                } catch (IllegalStateException e) {
                    onFinished.run();
                }
            }
            return;
        }

        totalScenesEnqueued.set(scenes.size());
        totalScenesProcessed.set(0);
        totalProseGenerated.set(0);
        generatedChapters.clear();

        workerThread = new Thread(() -> {
            try {
                int lastSceneNumber = 0;
                int lastEndStep = state != null ? state.getCurrentStep() : 0;
                for (Scene sc : scenes) {
                    if (!running || Thread.currentThread().isInterrupted()) {
                        break;
                    }
                    generateProseForScene(sc);
                    lastSceneNumber = Math.max(lastSceneNumber, sc.getSceneNumber());
                    lastEndStep = Math.max(lastEndStep, sc.getEndStep());
                }

                if (running && !Thread.currentThread().isInterrupted() && shouldGenerateResolution()) {
                    generateResolutionChapter(lastSceneNumber + 1, lastEndStep);
                }
            } catch (Exception e) {
                log.error("Error generating prose for scenes", e);
            } finally {
                running = false;
                if (onCompleteCallback != null) {
                    Runnable cb = onCompleteCallback;
                    onCompleteCallback = null;
                    try {
                        Platform.runLater(cb);
                    } catch (IllegalStateException e) {
                        cb.run();
                    }
                }
            }
        }, "ProseGenerator-Scenes-Worker");
        workerThread.setDaemon(true);
        workerThread.start();
    }

    public Scene chapterToScene(Chapter chapter) {
        if (chapter == null) {
            return null;
        }
        int start = chapter.startStep() > 0 ? chapter.startStep() : chapter.timeStep();
        int end = chapter.endStep() > 0 ? chapter.endStep() : start;

        List<TimeLocationEvent> allEvents = state != null ? state.ensureOrSynthesizeTimelineEvents() : Collections.emptyList();
        List<TimeLocationEvent> sceneEvents = allEvents.stream()
                .filter(e -> e.getTimeStep() >= start && e.getTimeStep() <= end)
                .collect(Collectors.toList());

        Scene scene = new Scene();
        scene.setSceneNumber(chapter.sceneNumber());
        scene.setStartStep(start);
        scene.setEndStep(end);
        scene.setPrimaryLocationName(chapter.locationName());
        scene.setDramaticFocus(chapter.dramaticFocus());
        scene.setDirectorNotes(chapter.directorNotes());
        scene.setOpeningHook(chapter.openingHook());
        scene.setSceneButton(chapter.sceneButton());
        scene.setSubtext(chapter.subtext());
        scene.setCategory(chapter.category() != null ? chapter.category() : SceneCategory.SUPPORTING);
        scene.setNarrativeImportanceScore(chapter.importanceScore());
        scene.setSuspenseScore(chapter.suspenseScore());
        scene.setSelected(chapter.isSelected());
        scene.setEvents(sceneEvents);

        if (!sceneEvents.isEmpty()) {
            scene.setFirstEvent(sceneEvents.get(0));
            scene.setLastEvent(sceneEvents.get(sceneEvents.size() - 1));
            scene.setPrimaryLocationId(sceneEvents.get(0).getLocationId());
        }
        return scene;
    }

    public List<Scene> chaptersToScenes(List<Chapter> chaptersList) {
        if (chaptersList == null) {
            return Collections.emptyList();
        }
        return chaptersList.stream()
                .filter(c -> c.dramaticFocus() == null || !c.dramaticFocus().contains("The Grand Reveal"))
                .map(this::chapterToScene)
                .collect(Collectors.toList());
    }

    public boolean awaitTermination(long timeoutMs) {
        Thread t = workerThread;
        if (t == null) {
            return true;
        }
        try {
            t.join(timeoutMs);
            return !t.isAlive();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public boolean isRunning() {
        return running || (workerThread != null && workerThread.isAlive());
    }

    public void enqueueEvent(TimeLocationEvent event) {
        if (event == null) {
            return;
        }
        totalScenesEnqueued.incrementAndGet();
        if (!eventQueue.offer(event)) {
            log.warn("Failed to enqueue event for time step: {}", event.getTimeStep());
        }
    }

    public void flush() {
        TimeLocationEvent flushTrigger = TimeLocationEvent.builder()
                .timeStep(state != null ? state.getCurrentStep() : 0)
                .locationId(null)
                .locationName("FLUSH")
                .actions(List.of())
                .knowledgeGained(List.of())
                .build();
        if (!eventQueue.offer(flushTrigger)) {
            log.warn("Failed to enqueue flush trigger for prose engine");
        }
    }

    private void processQueue() {
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                TimeLocationEvent event = eventQueue.poll(500, java.util.concurrent.TimeUnit.MILLISECONDS);
                if (event == null) {
                    continue;
                }

                if (event.getLocationId() == null && (event.getActions() == null || event.getActions().isEmpty())) {
                    if (!currentBatch.isEmpty()) {
                        flushBatch();
                    }
                    if (onCompleteCallback != null) {
                        Runnable cb = onCompleteCallback;
                        onCompleteCallback = null;
                        running = false;
                        try {
                            Platform.runLater(cb);
                        } catch (IllegalStateException e) {
                            cb.run();
                        }
                        break;
                    }
                    continue;
                }

                // Ensure strict location purity: if incoming event is from another room, flush currentBatch first
                if (!currentBatch.isEmpty() && event.getLocationId() != null
                        && !Objects.equals(currentBatch.get(0).getLocationId(), event.getLocationId())) {
                    flushBatch();
                }

                boolean shouldBreak = editorService.shouldBreakScene(currentBatch, event, false, state);
                String locName = event.getLocationName() != null ? event.getLocationName() : (event.getLocationId() != null ? event.getLocationId() : "Unknown");
                if (shouldBreak) {
                    String breakMsg = String.format("[Editor] Time to create a scene! Scene boundary triggered with %d events at %s (Step %d).",
                            currentBatch.size(), locName, event.getTimeStep());
                    log.info(breakMsg);
                    logScene(breakMsg);
                } else {
                    String waitMsg = String.format("[Editor] Not time to create a scene yet (accumulating event at %s, Step %d; current batch: %d events).",
                            locName, event.getTimeStep(), currentBatch.size());
                    log.info(waitMsg);
                    logScene(waitMsg);
                }
                currentBatch.add(event);

                if (shouldBreak) {
                    flushBatch();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (RuntimeException e) {
                if (log.isErrorEnabled()) {
                    log.error("Error generating prose", e);
                }
            }
        }
    }

    private void flushBatch() {
        if (!running || Thread.currentThread().isInterrupted() || currentBatch.isEmpty()) {
            return;
        }

        List<TimeLocationEvent> batchToProcess = new ArrayList<>(currentBatch);
        currentBatch.clear();

        Scene scene = editorService.assembleScene(sceneSequence++, batchToProcess, state);
        if (scene == null) {
            String msg = String.format("[Editor] Not time to create a scene yet (assembleScene returned null for %d events).", batchToProcess.size());
            log.info(msg);
            logScene(msg);
            return;
        }

        String createdMsg = String.format("[Editor] Created Scene %d [%s @ %s] with %d events.",
                scene.getSceneNumber(), editorService.formatSceneTimeSpan(scene, state), scene.getPrimaryLocationName(), scene.getEvents().size());
        log.info(createdMsg);
        logScene(createdMsg);

        generateProseForScene(scene);
    }

    public Map<String, Object> buildProsePromptParams(Scene scene) {
        String startTime = state != null ? state.getClockTime(scene.getStartStep()) : "Step " + scene.getStartStep();
        String endTime = state != null ? state.getClockTime(scene.getEndStep()) : "Step " + scene.getEndStep();

        String storyTitleLine = (title != null && !title.isEmpty()) ? "Story Title: \"" + title + "\"\n" : "";
        String premiseLine = (premise != null && !premise.isEmpty())
                ? "Premise/Setting: \"" + premise + "\"\nMake sure the narrative, descriptions, and atmosphere fit this setting perfectly!\n" : "";
        String voiceLine = (voice != null && !voice.trim().isEmpty() && !"Default".equalsIgnoreCase(voice))
                ? "CRITICAL: You MUST write the prose in the exact literary style, tone, and authorial voice of " + voice + ".\n" : "";

        SceneCategory cat = scene.getCategory() != null ? scene.getCategory() : SceneCategory.SUPPORTING;
        String categoryText = switch (cat) {
            case ESSENTIAL -> "- ★ CLIMACTIC SCENE: This is a major turning point of the murder mystery! Focus heavily on dramatic pacing, tension, physical confrontations, and revealing dialogue.";
            case SUPPORTING -> "- INVESTIGATIVE SCENE: Focus on dialogue exchanges, subtle suspicion, characters hiding secrets, and the discovery of clues.";
            case ATMOSPHERIC -> "- ATMOSPHERIC SCENE: Emphasize the environment, psychological mood, weather, and the creeping dread among the guests.";
            default -> "- TRANSITIONAL SCENE: Keep the scene concise and fluid, weaving mundane movements into brisk atmospheric exposition.";
        };

        String lengthText = "";
        if ("Short".equalsIgnoreCase(sceneLength) || "Kort".equalsIgnoreCase(sceneLength)) {
            lengthText = "Keep the scene concise and fast-paced. Target length: ~400-600 words (2-3 paragraphs). Focus on essential actions, key dialogue, and immediate tension.";
        } else if ("Medium".equalsIgnoreCase(sceneLength)) {
            lengthText = "Write a rich, balanced novel scene. Target length: ~800-1,200 words (4-6 paragraphs). Expand on sensory atmosphere, environmental descriptions, character interactions with fully written-out spoken dialogue, and rising suspense.";
        } else if ("Long".equalsIgnoreCase(sceneLength) || "Lång".equalsIgnoreCase(sceneLength) || "Lang".equalsIgnoreCase(sceneLength)) {
            lengthText = "Write an expansive, slow-paced, detailed novelistic chapter. Target length: ~1,500-2,000+ words (7-10+ paragraphs). Delve deeply into the characters' inner monologues, nuanced subtext in spoken dialogue, rich sensory world-building, and escalating psychological dread.";
        }

        StringBuilder framing = new StringBuilder();
        if (scene.getDirectorNotes() != null && !scene.getDirectorNotes().isBlank()) {
            framing.append("=== DIRECTOR'S EDITORIAL DIRECTIVE ===\n")
                    .append("DIRECTOR'S NOTES: ").append(scene.getDirectorNotes()).append("\n");
        }
        if (scene.getOpeningHook() != null && !scene.getOpeningHook().isBlank()) {
            framing.append("OPENING HOOK: ").append(scene.getOpeningHook()).append("\n");
        } else if (scene.getFirstEvent() != null) {
            framing.append("OPENING HOOK: Establish the scene starting at ")
                    .append(scene.getFirstEvent().getLocationName())
                    .append(" (Time Step ").append(scene.getFirstEvent().getTimeStep()).append(")")
                    .append(" with immediate dramatic tension.\n");
        }
        if (scene.getSceneButton() != null && !scene.getSceneButton().isBlank()) {
            framing.append("SCENE CLIMAX / BUTTON: ").append(scene.getSceneButton()).append("\n");
        } else if (scene.getLastEvent() != null && scene.getLastEvent() != scene.getFirstEvent()) {
            framing.append("SCENE CLIMAX / BUTTON: Conclude the scene emphasizing the culmination or unresolved cliffhanger at Time Step ")
                    .append(scene.getLastEvent().getTimeStep()).append(".\n");
        }
        if (scene.getSubtext() != null && !scene.getSubtext().isEmpty()) {
            framing.append("SUBTEXT & CONCEALED SECRETS:\n");
            for (String sub : scene.getSubtext()) {
                framing.append("- ").append(sub).append("\n");
            }
        }

        StringBuilder eventsText = new StringBuilder();
        if (scene.getEvents() != null) {
            for (TimeLocationEvent event : scene.getEvents()) {
                String clock = state != null ? state.getClockTime(event.getTimeStep()) : "Step " + event.getTimeStep();
                eventsText.append("\n[Time Step ").append(event.getTimeStep())
                        .append(" - ").append(clock)
                        .append(" @ ").append(event.getLocationName()).append("]\n");

                if (event.getActions() == null || event.getActions().isEmpty()) {
                    eventsText.append("No active actions taken.\n");
                } else {
                    for (Action a : event.getActions()) {
                        appendSingleAction(eventsText, a, event.getSnapshotBefore());
                    }
                }
            }
        }

        int sceneStartStep = scene.getStartStep() > 0 ? scene.getStartStep()
                : (scene.getFirstEvent() != null ? scene.getFirstEvent().getTimeStep() : 0);
        SimulationStateSnapshot startSnap = (state != null && sceneStartStep > 0)
                ? state.getSnapshotAt(sceneStartStep)
                : null;

        // Check if any event in this scene contains a MURDER action
        Set<String> murderVictimIdsInScene = new HashSet<>();
        if (scene.getEvents() != null) {
            for (TimeLocationEvent ev : scene.getEvents()) {
                if (ev.getActions() != null) {
                    for (Action a : ev.getActions()) {
                        if (a.getActionType() == ActionType.MURDER && a.getTargetAgentId() != null) {
                            murderVictimIdsInScene.add(a.getTargetAgentId());
                        }
                    }
                }
            }
        }

        // Determine which agents are present in this scene's events/rooms
        Set<String> sceneLocationIds = new HashSet<>();
        if (scene.getPrimaryLocationId() != null) {
            sceneLocationIds.add(scene.getPrimaryLocationId());
        }
        if (scene.getEvents() != null) {
            for (TimeLocationEvent ev : scene.getEvents()) {
                if (ev.getLocationId() != null) {
                    sceneLocationIds.add(ev.getLocationId());
                }
            }
        }

        Set<String> presentAgentIds = new LinkedHashSet<>();
        if (scene.getEvents() != null) {
            for (TimeLocationEvent ev : scene.getEvents()) {
                if (ev.getActions() != null) {
                    for (Action a : ev.getActions()) {
                        if (a.getActorId() != null) presentAgentIds.add(a.getActorId());
                        if (a.getTargetAgentId() != null) presentAgentIds.add(a.getTargetAgentId());
                    }
                }
                if (ev.getPresentAgentNames() != null && state != null && state.getAgents() != null) {
                    for (Agent ag : state.getAgents().values()) {
                        if (ev.getPresentAgentNames().contains(ag.getName())) {
                            presentAgentIds.add(ag.getId());
                        }
                    }
                }
            }
        }

        if (!sceneLocationIds.isEmpty() && state != null && state.getAgents() != null) {
            if (startSnap != null && startSnap.agents() != null) {
                for (Map.Entry<String, AgentSnapshot> entry : startSnap.agents().entrySet()) {
                    if (entry.getValue().currentLocationId() != null && sceneLocationIds.contains(entry.getValue().currentLocationId())) {
                        presentAgentIds.add(entry.getKey());
                    }
                }
            } else {
                for (Agent ag : state.getAgents().values()) {
                    if (ag.getCurrentLocationId() != null && sceneLocationIds.contains(ag.getCurrentLocationId())) {
                        presentAgentIds.add(ag.getId());
                    }
                }
            }
        }

        if (presentAgentIds.isEmpty() && state != null && state.getAgents() != null) {
            // Fallback: if no specific presence could be determined, include all registered agents
            presentAgentIds.addAll(state.getAgents().keySet());
        }

        String charactersList = "(No characters registered)";
        if (state != null && state.getAgents() != null && !state.getAgents().isEmpty()) {
            StringBuilder charsSb = new StringBuilder();
            charsSb.append("=== CHARACTERS PRESENT IN THIS SCENE ===\n");
            charsSb.append("(Only characters listed here are physically in this room and permitted to appear, speak, move, or interact):\n");

            List<Agent> presentList = state.getAgents().values().stream()
                    .filter(a -> presentAgentIds.contains(a.getId()))
                    .toList();
            List<Agent> absentList = state.getAgents().values().stream()
                    .filter(a -> !presentAgentIds.contains(a.getId()))
                    .toList();

            if (presentList.isEmpty()) {
                charsSb.append("- (No characters present)\n");
            } else {
                for (Agent a : presentList) {
                    String role = a.getRole();
                    boolean aliveAtSceneStart = isAgentAliveAtStart(a.getId(), startSnap);
                    if (role != null && role.equalsIgnoreCase("Victim") && aliveAtSceneStart) {
                        role = "Target / Key Stakeholder";
                    }

                    String vitality;
                    if (murderVictimIdsInScene.contains(a.getId())) {
                        vitality = "ALIVE AT START OF SCENE -> BECOMES VICTIM / DIES IN THIS SCENE";
                    } else if (aliveAtSceneStart) {
                        vitality = "ALIVE";
                    } else {
                        vitality = "DECEASED";
                    }

                    charsSb.append("- ").append(a.getId()).append(": ").append(a.getName())
                            .append(role != null ? " (" + role + ")" : "")
                            .append(" [").append(vitality).append("]");
                    if (!aliveAtSceneStart) {
                        charsSb.append(" -> Corpse/body located in this room. May only be discovered or examined as a deceased victim.\n");
                    } else {
                        charsSb.append(" -> Physically PRESENT in this scene.\n");
                    }
                }
            }

            if (!absentList.isEmpty()) {
                charsSb.append("\n=== OTHER CHARACTERS (LOCATED ELSEWHERE OR DECEASED - NOT IN THIS SCENE) ===\n");
                charsSb.append("(STRICT ENFORCEMENT: These characters are NOT in this room. You are STRICTLY FORBIDDEN from having them enter, appear, speak, exchange glances, or interact here):\n");
                for (Agent a : absentList) {
                    String role = a.getRole();
                    boolean aliveAtSceneStart = isAgentAliveAtStart(a.getId(), startSnap);
                    if (role != null && role.equalsIgnoreCase("Victim") && aliveAtSceneStart) {
                        role = "Target / Key Stakeholder";
                    }

                    String locId = null;
                    if (startSnap != null && startSnap.agents() != null && startSnap.agents().containsKey(a.getId())) {
                        locId = startSnap.agents().get(a.getId()).currentLocationId();
                    }
                    if (locId == null) {
                        locId = a.getCurrentLocationId();
                    }
                    String locName = (locId != null && state.getLocations() != null && state.getLocations().containsKey(locId))
                            ? state.getLocations().get(locId).getName()
                            : "another location";

                    if (aliveAtSceneStart) {
                        charsSb.append("- ").append(a.getId()).append(": ").append(a.getName())
                                .append(role != null ? " (" + role + ")" : "")
                                .append(" [ALIVE - Currently in ").append(locName).append("]")
                                .append(" -> Physically in another area. CANNOT appear, be seen, or interact in this scene!\n");
                    } else {
                        charsSb.append("- ").append(a.getId()).append(": ").append(a.getName())
                                .append(role != null ? " (" + role + ")" : "")
                                .append(" [DECEASED - Body in ").append(locName).append("]")
                                .append(" -> NOT in this scene. CANNOT appear, speak, or interact here!\n");
                    }
                }
            }
            charactersList = charsSb.toString().trim();
        }

        String locationsList = "(No locations registered)";
        if (state != null && state.getLocations() != null && !state.getLocations().isEmpty()) {
            locationsList = state.getLocations().values().stream()
                    .map(l -> "- " + l.getId() + ": " + l.getName())
                    .collect(Collectors.joining("\n"));
        }

        String itemsList = "(No items registered)";
        if (state != null && state.getItems() != null && !state.getItems().isEmpty()) {
            itemsList = state.getItems().values().stream()
                    .map(i -> "- " + i.getId() + ": " + i.getName() + " (" + i.getType() + ")")
                    .collect(Collectors.joining("\n"));
        }

        Map<String, Object> params = new HashMap<>();
        params.put("startStep", scene.getStartStep());
        params.put("endStep", scene.getEndStep());
        params.put("startTime", startTime);
        params.put("endTime", endTime);
        params.put("dramaticFocus", scene.getDramaticFocus() != null ? scene.getDramaticFocus() : "Scene unfolding");
        params.put("directorNotes", scene.getDirectorNotes() != null ? scene.getDirectorNotes() : "");
        params.put("openingHook", scene.getOpeningHook() != null ? scene.getOpeningHook() : "");
        params.put("sceneButton", scene.getSceneButton() != null ? scene.getSceneButton() : "");
        params.put("subtext", scene.getSubtext() != null && !scene.getSubtext().isEmpty() ? String.join("\n- ", scene.getSubtext()) : "");
        params.put("primarySetting", scene.getPrimaryLocationName() != null ? scene.getPrimaryLocationName() : "Unknown");
        params.put("category", scene.getCategory() != null ? scene.getCategory().getDisplayName() : "Supporting");
        params.put("importanceScore", String.format(Locale.ROOT, "%.1f", scene.getNarrativeImportanceScore()));
        params.put("suspenseScore", String.format(Locale.ROOT, "%.1f", scene.getSuspenseScore()));
        params.put("storyTitle", storyTitleLine);
        params.put("premise", premiseLine);
        params.put("voiceInstruction", voiceLine);
        params.put("categoryInstructions", categoryText);
        params.put("lengthInstructions", lengthText);
        params.put("previousChaptersContext", buildPreviousChaptersContext());
        params.put("framingInstructions", framing.toString());
        params.put("charactersList", charactersList);
        params.put("locationsList", locationsList);
        params.put("itemsList", itemsList);
        params.put("eventsSection", eventsText.toString());

        return params;
    }

    public String buildPreviousChaptersContext() {
        if (generatedChapters.isEmpty()) {
            return "This is the opening scene of the novel. No previous chapters have been written yet.";
        }
        StringBuilder sb = new StringBuilder();
        int total = generatedChapters.size();
        int startIndex = Math.max(0, total - 4);
        sb.append(String.format("There are %d previously written chapters. Recent continuity context:\n", total));
        for (int i = startIndex; i < total; i++) {
            Chapter ch = generatedChapters.get(i);
            sb.append(String.format("• Chapter %d [%s | %s]: Focus was \"%s\".\n",
                    ch.sceneNumber(),
                    ch.locationName() != null ? ch.locationName() : "Unknown",
                    ch.timeSpan() != null ? ch.timeSpan() : "Time Step " + ch.timeStep(),
                    ch.dramaticFocus() != null ? ch.dramaticFocus() : ""));
            String txt = ch.text();
            if (txt != null && !txt.isBlank()) {
                String clean = txt.trim();
                int lastPeriod = clean.lastIndexOf(". ");
                if (lastPeriod > 0 && lastPeriod < clean.length() - 2) {
                    sb.append("  Ending: \"").append(clean.substring(lastPeriod + 2).trim()).append("\"\n");
                }
            }
        }
        return sb.toString();
    }

    public String buildProsePrompt(Scene scene) {
        Map<String, Object> params = buildProsePromptParams(scene);
        PromptTemplate template = new PromptTemplate(sceneProsePromptResource);
        return template.render(params);
    }

    private void appendSingleAction(StringBuilder sb, Action a, SimulationStateSnapshot snapshot) {
        String actorName = a.getActorId();
        if (snapshot != null && snapshot.agents() != null && snapshot.agents().containsKey(a.getActorId())) {
            actorName = snapshot.agents().get(a.getActorId()).name();
        } else if (state != null && state.getAgents() != null && state.getAgents().containsKey(a.getActorId())) {
            actorName = state.getAgents().get(a.getActorId()).getName();
        }
        sb.append("- ").append(actorName).append(" did ").append(a.getActionType());

        if (a.getTargetAgentId() != null) {
            String targetName = a.getTargetAgentId();
            if (snapshot != null && snapshot.agents() != null && snapshot.agents().containsKey(a.getTargetAgentId())) {
                targetName = snapshot.agents().get(a.getTargetAgentId()).name();
            } else if (state != null && state.getAgents() != null && state.getAgents().containsKey(a.getTargetAgentId())) {
                targetName = state.getAgents().get(a.getTargetAgentId()).getName();
            }
            sb.append(" to ").append(targetName);
        }
        if (a.getTargetLocationId() != null) {
            String targetLoc = a.getTargetLocationId();
            if (snapshot != null && snapshot.places() != null && snapshot.places().containsKey(a.getTargetLocationId())) {
                targetLoc = snapshot.places().get(a.getTargetLocationId()).name();
            } else if (state != null && state.getLocations() != null && state.getLocations().containsKey(a.getTargetLocationId())) {
                targetLoc = state.getLocations().get(a.getTargetLocationId()).getName();
            }
            sb.append(" at ").append(targetLoc);
        }
        if (a.getDetails() != null) {
            sb.append(". Details: ").append(a.getDetails());
        }
        sb.append("\n");
    }

    private boolean isAgentAliveAtStart(String agentId, SimulationStateSnapshot startSnap) {
        if (startSnap != null && startSnap.agents() != null && startSnap.agents().containsKey(agentId)) {
            return startSnap.agents().get(agentId).isAlive();
        } else if (state != null && state.getSnapshots() != null && state.getSnapshots().containsKey(0)) {
            SimulationStateSnapshot step0 = state.getSnapshots().get(0);
            if (step0.agents() != null && step0.agents().containsKey(agentId)) {
                return step0.agents().get(agentId).isAlive();
            }
        }
        if (state != null && state.getAgents() != null && state.getAgents().containsKey(agentId)) {
            return state.getAgents().get(agentId).isAlive();
        }
        return true;
    }

    public int getTotalScenesEnqueued() {
        return totalScenesEnqueued.get();
    }

    public int getTotalScenesProcessed() {
        return totalScenesProcessed.get();
    }

    public int getTotalProseGenerated() {
        return totalProseGenerated.get();
    }

    public int getPendingQueueSize() {
        return eventQueue.size();
    }

    public List<Chapter> getGeneratedChapters() {
        return new ArrayList<>(generatedChapters);
    }

    public void addGeneratedChapter(Chapter chapter) {
        if (chapter != null) {
            generatedChapters.add(chapter);
        }
    }

    private void logScene(String message) {
        if (onLogEvent != null) {
            try {
                Platform.runLater(() -> onLogEvent.accept(message));
            } catch (IllegalStateException e) {
                onLogEvent.accept(message);
            }
        }
    }

    public boolean shouldGenerateResolution() {
        if (state == null) {
            return false;
        }
        boolean alreadyExists = generatedChapters.stream()
                .anyMatch(c -> c.dramaticFocus() != null && c.dramaticFocus().contains("The Grand Reveal"));
        if (alreadyExists) {
            return false;
        }
        boolean isFinished = state.isCompleted() || (state.getTargetSteps() > 0 && state.getCurrentStep() >= state.getTargetSteps());
        boolean hasVictim = state.getAgents() != null && state.getAgents().values().stream().anyMatch(a -> !a.isAlive());
        return isFinished || hasVictim;
    }

    public Chapter generateResolutionChapter(int sceneNumber, int endStep) {
        if (state == null) {
            return null;
        }

        Agent detective = null;
        Agent murderer = null;
        List<Agent> victims = new ArrayList<>();
        List<Agent> surviving = new ArrayList<>();

        if (state.getAgents() != null) {
            for (Agent a : state.getAgents().values()) {
                if (a.isDetective() || "agent_0".equals(a.getId()) || (a.getRole() != null && a.getRole().toLowerCase(Locale.ROOT).contains("detective"))) {
                    if (detective == null || a.isDetective()) {
                        detective = a;
                    }
                }
                if (a.isMurderer()) {
                    murderer = a;
                }
                if (!a.isAlive()) {
                    victims.add(a);
                } else {
                    surviving.add(a);
                }
            }
        }

        if (detective == null && !surviving.isEmpty()) {
            detective = surviving.get(0);
        }

        if (murderer == null && state.getAgents() != null) {
            murderer = state.getAgents().values().stream()
                    .filter(a -> a.getSecretGoal() != null && (
                            a.getSecretGoal().toLowerCase(Locale.ROOT).contains("kill")
                            || a.getSecretGoal().toLowerCase(Locale.ROOT).contains("murder")
                            || a.getSecretGoal().toLowerCase(Locale.ROOT).contains("mörda")))
                    .findFirst().orElse(null);
        }

        String detectiveName = detective != null ? detective.getName() : "The Detective";
        String detectiveRole = detective != null && detective.getRole() != null ? detective.getRole() : "Lead Investigator";
        String murdererName = murderer != null ? murderer.getName() : "The Culprit";
        String murdererRole = murderer != null && murderer.getRole() != null ? murderer.getRole() : "Suspect";
        String murdererGoal = murderer != null && murderer.getSecretGoal() != null && !murderer.getSecretGoal().isBlank()
                ? murderer.getSecretGoal()
                : "A clandestine conspiracy and personal vendetta.";

        String victimNames = victims.isEmpty() ? "the victim" : victims.stream().map(Agent::getName).collect(Collectors.joining(", "));

        String settingName = "The Main Gathering Hall";
        if (state.getLocations() != null && !state.getLocations().isEmpty()) {
            Location preferred = state.getLocations().values().stream()
                    .filter(l -> {
                        String lname = l.getName().toLowerCase(Locale.ROOT);
                        return lname.contains("dome") || lname.contains("lounge") || lname.contains("bridge") || lname.contains("hall") || lname.contains("library");
                    })
                    .findFirst()
                    .orElse(state.getLocations().values().iterator().next());
            settingName = preferred.getName();
        }

        TimeLocationEvent actualMurderEvent = null;
        Action actualMurderAction = null;
        if (state != null && state.getAllRecordedEvents() != null) {
            for (TimeLocationEvent ev : state.getAllRecordedEvents()) {
                if (ev.getActions() != null) {
                    for (Action a : ev.getActions()) {
                        if (a.getActionType() == ActionType.MURDER) {
                            actualMurderEvent = ev;
                            actualMurderAction = a;
                            break;
                        }
                    }
                }
                if (actualMurderAction != null) break;
            }
        }

        String crimeLocation = settingName;
        String crimeTime = "the earlier hours of the gathering";
        String actualWeapon = "a lethal weapon";
        String crimeDetails = "The perpetrator struck down the victim in secret";

        if (actualMurderEvent != null && actualMurderAction != null) {
            if (actualMurderEvent.getLocationName() != null && !actualMurderEvent.getLocationName().isBlank()) {
                crimeLocation = actualMurderEvent.getLocationName();
            }
            if (state != null) {
                crimeTime = String.format("Time Step %d (%s)", actualMurderEvent.getTimeStep(), state.getClockTime(actualMurderEvent.getTimeStep()));
            }
            if (actualMurderAction.getTargetItemId() != null && state != null && state.getItems() != null
                    && state.getItems().containsKey(actualMurderAction.getTargetItemId())) {
                actualWeapon = state.getItems().get(actualMurderAction.getTargetItemId()).getName();
            } else if (actualMurderAction.getDetails() != null && actualMurderAction.getDetails().contains("with")) {
                actualWeapon = actualMurderAction.getDetails();
            }
            if (actualMurderAction.getDetails() != null && !actualMurderAction.getDetails().isBlank()) {
                crimeDetails = actualMurderAction.getDetails();
            } else {
                crimeDetails = String.format("%s executed murder against %s in %s using %s", murdererName, victimNames, crimeLocation, actualWeapon);
            }
        }

        List<String> clueNames = new ArrayList<>();
        if (!"a lethal weapon".equals(actualWeapon)) {
            clueNames.add(actualWeapon + " (Murder Weapon)");
        }
        if (state != null && state.getItems() != null) {
            for (Item itm : state.getItems().values()) {
                if (!itm.getName().equalsIgnoreCase(actualWeapon) && (itm.getType() == ItemType.WEAPON || itm.getType() == ItemType.CLUE)) {
                    clueNames.add(itm.getName() + " (" + itm.getType() + ")");
                }
            }
        }
        String keyClues = clueNames.isEmpty() ? "The forensic traces and recovered surveillance data"
                : clueNames.stream().limit(6).collect(Collectors.joining(", "));

        StringBuilder factsBuilder = new StringBuilder();
        if (detective != null && detective.getKnowledgeBase() != null && !detective.getKnowledgeBase().isEmpty()) {
            detective.getKnowledgeBase().stream()
                    .map(Knowledge::information)
                    .filter(info -> info != null && !info.isBlank() && !info.equalsIgnoreCase("None"))
                    .distinct()
                    .limit(10)
                    .forEach(f -> factsBuilder.append("- ").append(f).append("\n"));
        }
        if (factsBuilder.isEmpty()) {
            factsBuilder.append("- Key testimonies and suspicious timeline discrepancies observed across the estate.\n");
        }

        String survivingCharacters = surviving.isEmpty() ? "All remaining guests and station personnel"
                : surviving.stream().map(a -> "- " + a.getName() + " (" + a.getRole() + ")").collect(Collectors.joining("\n"));

        String storyTitle = this.title != null && !this.title.isBlank() ? this.title : (state != null ? state.getTitle() : "The Mystery");
        String storyPremise = this.premise != null && !this.premise.isBlank() ? this.premise : (state != null ? state.getPremise() : "");
        String storyVoice = this.voice != null && !this.voice.isBlank() ? this.voice : "Classic Agatha Christie Mystery";

        Map<String, Object> params = new HashMap<>();
        params.put("storyTitle", storyTitle);
        params.put("premise", storyPremise);
        params.put("voiceInstruction", storyVoice);
        params.put("detectiveName", detectiveName);
        params.put("detectiveRole", detectiveRole);
        params.put("murdererName", murdererName);
        params.put("murdererRole", murdererRole);
        params.put("murdererGoal", murdererGoal);
        params.put("victimNames", victimNames);
        params.put("keyClues", keyClues);
        params.put("crimeLocation", crimeLocation);
        params.put("crimeTime", crimeTime);
        params.put("actualWeapon", actualWeapon);
        params.put("crimeDetails", crimeDetails);
        params.put("settingName", settingName);
        params.put("survivingCharacters", survivingCharacters);
        params.put("detectiveFacts", factsBuilder.toString());

        log.info("[Grand Reveal & Resolution] Generating final denouement chapter in {}...", settingName);
        logScene(String.format("🎬 [Grand Reveal] Påbörjar generering av upplösningskapitlet via LLM [%s] i %s...",
                ollamaLlmService.getModel(LlmRole.PROSE), settingName));

        String chapterText;
        if (generateProseText) {
            String prompt = buildResolutionPrompt(params);
            log.info("--- PROSE ENGINE LLM PROMPT (The Grand Reveal & Resolution [Model: {}]) ---\n{}",
                    ollamaLlmService.getModel(LlmRole.PROSE), prompt);

            try {
                chapterText = llmExecutor.execute(
                    () -> {
                        if (Thread.currentThread().isInterrupted()) {
                            return "";
                        }
                        var client = ollamaLlmService.getChatClient(LlmRole.PROSE);
                        if (client != null) {
                            return client.prompt()
                                    .user(u -> u.text(resolutionProsePromptResource).params(params))
                                    .call()
                                    .content();
                        }
                        return ollamaLlmService.generateText(prompt, LlmRole.PROSE);
                    },
                    text -> text != null && !text.isBlank() && text.trim().length() > 80,
                    "Resolution chapter text was too short or empty",
                    3,
                    3
                );
            } catch (Exception e) {
                log.warn("Failed to generate resolution prose via Spring AI / LlmExecutor, attempting direct WebClient fallback: {}", e.getMessage());
                logScene("[Grand Reveal] Första anropet misslyckades (" + e.getMessage() + "), försöker direkt WebClient fallback...");
                chapterText = ollamaLlmService.generateText(prompt, LlmRole.PROSE);
            }

            if (chapterText == null || chapterText.isBlank() || chapterText.trim().length() <= 80) {
                String warnMsg = "Genereringen av upplösningskapitlet ('The Grand Reveal') misslyckades via LLM.";
                log.warn(warnMsg);
                logScene("❌ [VARNING] " + warnMsg + " Du kan klicka på '🔄 Regenerate Grand Reveal via LLM' i Scener-fliken för att generera det igen.");

                chapterText = String.format(Locale.ROOT,
                        "[UPPLÖSNINGSKAPITLET KUNDE INTE GENERERAS VIA LLM]\n\n" +
                        "Modellen lyckades inte returnera fullständig prosa för finalen i %s.\n\n" +
                        "Fakta från utredningen:\n" +
                        "• Detektiv / Anklagare: %s (%s)\n" +
                        "• Skyldig: %s (%s)\n" +
                        "• Motiv: %s\n" +
                        "• Nyckelbevis: %s\n\n" +
                        "👉 Klicka på knappen '🔄 Regenerate Grand Reveal via LLM' i Scener-fliken för att generera kapitlet med LLM på nytt.",
                        settingName, detectiveName, detectiveRole, murdererName, murdererRole, murdererGoal, keyClues);
            } else {
                int words = chapterText.split("\\s+").length;
                logScene(String.format("✓ [Grand Reveal] Upplösningskapitlet har genererats via LLM! (%,d ord)", words));
            }
        } else {
            chapterText = String.format(Locale.ROOT,
                    "[HÄNDELSELOGG (Prosa avstängd)]\n\nGrand Reveal: %s assembled all suspects in %s and formally proved that %s committed the crime, driven by '%s'.",
                    detectiveName, settingName, murdererName, murdererGoal);
        }

        Chapter resolutionChapter = new Chapter(
            chapterText,
            true,
            false,
            endStep,
            sceneNumber,
            settingName,
            10.0,
            10.0,
            SceneCategory.ESSENTIAL,
            "The Grand Reveal & Resolution",
            endStep,
            endStep,
            "Finale & Denouement",
            true
        );

        generatedChapters.removeIf(c -> c.dramaticFocus() != null
                && (c.dramaticFocus().contains("The Grand Reveal") || c.dramaticFocus().contains("Resolution")));
        generatedChapters.add(resolutionChapter);
        totalProseGenerated.incrementAndGet();

        log.info("[Grand Reveal Chapter Generated] Scene #{}: The Grand Reveal & Resolution in {}", sceneNumber, settingName);

        if (onChapterGenerated != null) {
            try {
                Platform.runLater(() -> onChapterGenerated.accept(resolutionChapter));
            } catch (IllegalStateException e) {
                onChapterGenerated.accept(resolutionChapter);
            }
        }

        return resolutionChapter;
    }

    public String buildResolutionPrompt(Map<String, Object> params) {
        try {
            PromptTemplate template = new PromptTemplate(resolutionProsePromptResource);
            return template.render(params);
        } catch (Exception e) {
            log.warn("PromptTemplate rendering failed for resolution prompt, falling back to direct replacement: {}", e.getMessage());
            try (java.io.InputStream is = resolutionProsePromptResource.getInputStream()) {
                String raw = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                for (Map.Entry<String, Object> entry : params.entrySet()) {
                    raw = raw.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
                }
                return raw;
            } catch (Exception ioEx) {
                log.error("Failed to read resolution prompt resource", ioEx);
                return "Write the dramatic grand finale and resolution chapter for " + params.get("storyTitle");
            }
        }
    }
}
