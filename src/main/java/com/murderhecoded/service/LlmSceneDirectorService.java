package com.murderhecoded.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.murderhecoded.domain.Action;
import com.murderhecoded.domain.Agent;
import com.murderhecoded.domain.Scene;
import com.murderhecoded.domain.SceneCategory;
import com.murderhecoded.domain.TimeLocationEvent;
import com.murderhecoded.llm.LlmExecutor;
import com.murderhecoded.llm.LlmRole;
import com.murderhecoded.repository.SimulationRepository;
import com.murderhecoded.service.dto.LlmSceneDirectorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;

/**
 * Editorial service using an LLM to elevate algorithmically clustered scene candidates
 * into rich, directed literary scenes with gripping dramatic focus, opening hooks,
 * climax buttons, character subtext, and directorial guidance for prose generation.
 */
@Service
@Slf4j
public class LlmSceneDirectorService {

    private final OllamaLlmService ollamaLlmService;
    private final LlmExecutor llmExecutor;
    private final ObjectMapper objectMapper;

    @Value("classpath:prompts/editor/scene-director.st")
    private Resource promptResource;

    private String cachedPromptTemplate;

    @Autowired
    public LlmSceneDirectorService(OllamaLlmService ollamaLlmService,
                                   LlmExecutor llmExecutor,
                                   ObjectMapper objectMapper) {
        this.ollamaLlmService = ollamaLlmService;
        this.llmExecutor = llmExecutor;
        this.objectMapper = objectMapper;
    }

    public synchronized String getPromptTemplate() {
        if (cachedPromptTemplate == null) {
            try (InputStream is = promptResource.getInputStream()) {
                cachedPromptTemplate = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            } catch (Exception e) {
                log.error("Failed to load scene-director.st prompt resource", e);
                cachedPromptTemplate = "You are the Scene Director for {title}. Analyze scene #{sceneNumber} in {locationName}.\n" +
                        "Characters: {charactersSummary}\nEvents: {eventsSummary}\nReturn JSON with dramaticFocus, openingHook, sceneButton, directorNotes, subtextAndSecrets.";
            }
        }
        return cachedPromptTemplate;
    }

    /**
     * Enriches a single candidate scene using the LLM Director.
     */
    public Scene enrichScene(Scene scene,
                             SimulationRepository state,
                             String authorVoice,
                             String title,
                             String premise) {
        if (scene == null) {
            return null;
        }

        String safeTitle = (title != null && !title.isBlank()) ? title : "A Classic Mystery";
        String safeVoice = (authorVoice != null && !authorVoice.isBlank()) ? authorVoice : "Classic Detective Fiction";
        String safePremise = (premise != null && !premise.isBlank()) ? premise : "A murder mystery in an isolated setting.";

        String charactersSummary = formatCharactersSummary(scene, state);
        String eventsSummary = formatEventsSummary(scene, state);
        String actName = determineActName(scene.getStartStep(), state);

        String startTime = state != null ? state.getClockTime(scene.getStartStep()) : "Step " + scene.getStartStep();
        String endTime = state != null ? state.getClockTime(scene.getEndStep()) : "Step " + scene.getEndStep();

        String prompt = getPromptTemplate()
                .replace("{title}", safeTitle)
                .replace("{voice}", safeVoice)
                .replace("{premise}", safePremise)
                .replace("{sceneNumber}", String.valueOf(scene.getSceneNumber()))
                .replace("{locationName}", scene.getPrimaryLocationName() != null ? scene.getPrimaryLocationName() : "Unknown Setting")
                .replace("{startStep}", String.valueOf(scene.getStartStep()))
                .replace("{endStep}", String.valueOf(scene.getEndStep()))
                .replace("{startTime}", startTime)
                .replace("{endTime}", endTime)
                .replace("{actName}", actName)
                .replace("{charactersSummary}", charactersSummary)
                .replace("{eventsSummary}", eventsSummary);

        try {
            LlmSceneDirectorResponse response = llmExecutor.execute(
                    () -> {
                        String raw = ollamaLlmService.generateText(prompt, LlmRole.DIRECTOR);
                        String cleanedJson = extractJson(raw);
                        try {
                            return objectMapper.readValue(cleanedJson, LlmSceneDirectorResponse.class);
                        } catch (Exception parseEx) {
                            log.warn("Failed to parse LLM Scene Director JSON: {}. Raw: {}", parseEx.getMessage(), raw);
                            return null;
                        }
                    },
                    res -> res != null && res.dramaticFocus() != null && !res.dramaticFocus().isBlank(),
                    "Scene Director response missing dramatic focus",
                    2,
                    2
            );

            if (response != null) {
                applyDirectorResponse(scene, response);
                log.info("Scene Director successfully enriched Scene #{} with Focus: '{}'",
                        scene.getSceneNumber(), scene.getDramaticFocus());
            } else {
                log.warn("Scene Director returned null response for Scene #{}; keeping algorithmic values.", scene.getSceneNumber());
            }
        } catch (Exception ex) {
            log.warn("LLM Scene Director encountered an error for Scene #{}: {}. Preserving algorithmic defaults.",
                    scene.getSceneNumber(), ex.getMessage());
        }

        return scene;
    }

    /**
     * Enriches a batch of candidate scenes with progress updates and live logging.
     */
    public List<Scene> enrichScenes(List<Scene> scenes,
                                    SimulationRepository state,
                                    String authorVoice,
                                    String title,
                                    String premise,
                                    Consumer<String> logConsumer,
                                    Consumer<Double> progressConsumer) {
        if (scenes == null || scenes.isEmpty()) {
            return scenes != null ? scenes : Collections.emptyList();
        }

        int total = scenes.size();
        for (int i = 0; i < total; i++) {
            if (Thread.currentThread().isInterrupted()) {
                log.info("LlmSceneDirectorService interrupted, stopping enrichment.");
                if (logConsumer != null) {
                    logConsumer.accept("⏹️ [LLM Scene Director] Avbruten av användaren.");
                }
                break;
            }
            Scene sc = scenes.get(i);
            String infoMsg = String.format("🎬 [LLM Scene Director] Analyserar och regisserar Scen %d/%d ('%s', steg %d-%d)...",
                    sc.getSceneNumber(), total, sc.getPrimaryLocationName(), sc.getStartStep(), sc.getEndStep());
            if (logConsumer != null) {
                logConsumer.accept(infoMsg);
            }
            log.info(infoMsg);

            enrichScene(sc, state, authorVoice, title, premise);

            if (progressConsumer != null) {
                progressConsumer.accept((double) (i + 1) / total);
            }
        }

        if (logConsumer != null) {
            logConsumer.accept(String.format("✓ [LLM Scene Director] Samtliga %d scener har regisserats med framgång!", total));
        }
        return scenes;
    }

    private void applyDirectorResponse(Scene scene, LlmSceneDirectorResponse res) {
        if (res.dramaticFocus() != null && !res.dramaticFocus().isBlank()) {
            scene.setDramaticFocus(res.dramaticFocus().trim());
        }
        if (res.directorNotes() != null && !res.directorNotes().isBlank()) {
            scene.setDirectorNotes(res.directorNotes().trim());
        }
        if (res.openingHook() != null && !res.openingHook().isBlank()) {
            scene.setOpeningHook(res.openingHook().trim());
        }
        if (res.sceneButton() != null && !res.sceneButton().isBlank()) {
            scene.setSceneButton(res.sceneButton().trim());
        }
        if (res.subtextAndSecrets() != null && !res.subtextAndSecrets().isEmpty()) {
            scene.setSubtext(new ArrayList<>(res.subtextAndSecrets()));
        }

        if (res.narrativeWeight() != null && !res.narrativeWeight().isBlank()) {
            try {
                SceneCategory cat = SceneCategory.valueOf(res.narrativeWeight().trim().toUpperCase(Locale.ROOT));
                scene.setCategory(cat);
                if (cat == SceneCategory.DISPENSABLE) {
                    scene.setSelected(false);
                } else if (cat == SceneCategory.ESSENTIAL) {
                    scene.setSelected(true);
                }
            } catch (IllegalArgumentException ignored) {
                // keep existing category
            }
        }

        if (res.dramaticScore() != null && res.dramaticScore() >= 1.0 && res.dramaticScore() <= 10.0) {
            scene.setDramaScore(Math.round(res.dramaticScore() * 10.0) / 10.0);
        }
    }

    private String formatCharactersSummary(Scene scene, SimulationRepository state) {
        if (state == null || state.getAgents() == null || state.getAgents().isEmpty()) {
            return "(No character details available)";
        }

        Set<String> presentAgentIds = new HashSet<>();
        if (scene.getEvents() != null) {
            for (TimeLocationEvent ev : scene.getEvents()) {
                if (ev.getActions() != null) {
                    for (Action a : ev.getActions()) {
                        if (a.getActorId() != null) presentAgentIds.add(a.getActorId());
                        if (a.getTargetAgentId() != null) presentAgentIds.add(a.getTargetAgentId());
                    }
                }
                if (ev.getPresentAgentNames() != null) {
                    for (Agent ag : state.getAgents().values()) {
                        if (ev.getPresentAgentNames().contains(ag.getName())) {
                            presentAgentIds.add(ag.getId());
                        }
                    }
                }
            }
        }

        com.murderhecoded.domain.SimulationStateSnapshot startSnap = (state != null && scene.getStartStep() > 0)
                ? state.getSnapshotAt(scene.getStartStep())
                : null;

        if (scene.getPrimaryLocationId() != null) {
            String roomLoc = scene.getPrimaryLocationId();
            if (startSnap != null && startSnap.agents() != null) {
                for (var entry : startSnap.agents().entrySet()) {
                    if (roomLoc.equals(entry.getValue().currentLocationId())) {
                        presentAgentIds.add(entry.getKey());
                    }
                }
            } else if (state != null && state.getAgents() != null) {
                for (Agent ag : state.getAgents().values()) {
                    if (roomLoc.equals(ag.getCurrentLocationId())) {
                        presentAgentIds.add(ag.getId());
                    }
                }
            }
        }

        if (presentAgentIds.isEmpty()) {
            // Default to all agents if none filtered
            presentAgentIds = state.getAgents().keySet();
        }

        StringBuilder sb = new StringBuilder();
        sb.append("PRESENT IN THIS SCENE:\n");
        for (String id : presentAgentIds) {
            Agent a = state.getAgents().get(id);
            if (a == null) continue;
            boolean aliveAtStart = true;
            if (startSnap != null && startSnap.agents() != null && startSnap.agents().containsKey(id)) {
                aliveAtStart = startSnap.agents().get(id).isAlive();
            } else {
                aliveAtStart = a.isAlive();
            }
            String status = aliveAtStart ? "[ALIVE]" : "[DECEASED]";
            sb.append("- ").append(a.getName()).append(" ").append(status)
                    .append(" (Role: ").append(a.getRole() != null ? a.getRole() : "Guest");
            if (a.getPersonality() != null && !a.getPersonality().isBlank()) {
                sb.append(", Traits: ").append(a.getPersonality());
            }
            if (a.getSecretGoal() != null && !a.getSecretGoal().isBlank()) {
                sb.append(", Secret Agenda: ").append(a.getSecretGoal());
            }
            sb.append(")\n");
        }

        Set<String> absentAgentIds = new HashSet<>(state.getAgents().keySet());
        absentAgentIds.removeAll(presentAgentIds);
        if (!absentAgentIds.isEmpty()) {
            sb.append("\nOTHER CHARACTERS (NOT IN THIS LOCATION - DO NOT INCLUDE AS ACTIVE PARTICIPANTS):\n");
            for (String id : absentAgentIds) {
                Agent a = state.getAgents().get(id);
                if (a == null) continue;
                boolean alive = true;
                if (startSnap != null && startSnap.agents() != null && startSnap.agents().containsKey(id)) {
                    alive = startSnap.agents().get(id).isAlive();
                } else {
                    alive = a.isAlive();
                }
                String status = alive ? "[ALIVE - in another location]" : "[DECEASED]";
                sb.append("- ").append(a.getName()).append(" ").append(status).append("\n");
            }
        }
        return sb.length() > 0 ? sb.toString() : "(No characters identified)";
    }

    private String formatEventsSummary(Scene scene, SimulationRepository state) {
        if (scene.getEvents() == null || scene.getEvents().isEmpty()) {
            return "(No actions recorded)";
        }

        StringBuilder sb = new StringBuilder();
        for (TimeLocationEvent ev : scene.getEvents()) {
            String clock = state != null ? state.getClockTime(ev.getTimeStep()) : "Step " + ev.getTimeStep();
            sb.append("\n[Step ").append(ev.getTimeStep()).append(" (").append(clock).append(")]\n");

            if (ev.getActions() == null || ev.getActions().isEmpty()) {
                sb.append("  - Idle observations; room silent.\n");
            } else {
                for (Action a : ev.getActions()) {
                    String actor = state != null ? state.resolveAgentName(a.getActorId(), ev.getTimeStep()) : a.getActorId();
                    sb.append("  - ").append(actor).append(": ").append(a.getActionType());
                    if (a.getTargetAgentId() != null) {
                        String target = state != null ? state.resolveAgentName(a.getTargetAgentId(), ev.getTimeStep()) : a.getTargetAgentId();
                        sb.append(" with ").append(target);
                    }
                    if (a.getDetails() != null && !a.getDetails().isBlank()) {
                        sb.append(" -> Details: \"").append(a.getDetails()).append("\"");
                    }
                    if (a.getSharedKnowledge() != null && !a.getSharedKnowledge().isBlank()) {
                        sb.append(" [Shared/Thought: ").append(a.getSharedKnowledge()).append("]");
                    }
                    sb.append("\n");
                }
            }

            if (ev.getKnowledgeGained() != null && !ev.getKnowledgeGained().isEmpty()) {
                for (String k : ev.getKnowledgeGained()) {
                    sb.append("    * Revelation / Clue discovered: ").append(k).append("\n");
                }
            }
        }
        return sb.toString();
    }

    private String determineActName(int step, SimulationRepository state) {
        int target = state != null && state.getTargetSteps() > 0 ? state.getTargetSteps() : 100;
        double pct = (double) step / target;
        if (pct < 0.15) return "Act 1: Exposition & Gathering of Guests";
        if (pct < 0.35) return "Act 2: Rising Suspense & Secret Schemes";
        if (pct < 0.55) return "Act 3: The Crime & Strike Opportunity";
        if (pct < 0.75) return "Act 4: Body Discovery & Panic";
        if (pct < 0.90) return "Act 5: Escalation & Conflicting Alibis";
        return "Act 6: Grand Climax & Resolution";
    }

    private String extractJson(String text) {
        if (text == null) return "{}";
        String trimmed = text.trim();
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring(7);
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3);
        }
        int endCode = trimmed.lastIndexOf("```");
        if (endCode != -1) {
            trimmed = trimmed.substring(0, endCode);
        }
        int firstBrace = trimmed.indexOf('{');
        int lastBrace = trimmed.lastIndexOf('}');
        if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            trimmed = trimmed.substring(firstBrace, lastBrace + 1);
        }
        return trimmed.trim();
    }
}
