package com.murderhecoded.agent;

import com.murderhecoded.domain.ActionType;
import com.murderhecoded.domain.Agent;
import com.murderhecoded.domain.AgentActionRecord;
import com.murderhecoded.domain.Item;
import com.murderhecoded.domain.Knowledge;
import com.murderhecoded.domain.Location;
import com.murderhecoded.llm.LlmExecutor;
import com.murderhecoded.repository.SimulationRepository;
import com.murderhecoded.service.DramaturgyDirectorService;
import com.murderhecoded.service.OllamaLlmService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.murderhecoded.llm.LlmRole;

@Service
@Slf4j
@SuppressWarnings("PMD")
public class AgentService {
    private static final int MAX_SEMANTIC_ATTEMPTS = 3;
    private static final int MAX_TECHNICAL_RETRIES = 3;

    private final OllamaLlmService ollamaLlmService;
    private final LlmExecutor llmExecutor;
    private final DramaturgyDirectorService dramaturgyDirectorService;

    @Value("classpath:/prompts/agent/agent-decision.st")
    private Resource agentDecisionPromptResource = new ClassPathResource("prompts/agent/agent-decision.st");

    @Autowired
    public AgentService(OllamaLlmService ollamaLlmService, LlmExecutor llmExecutor, DramaturgyDirectorService dramaturgyDirectorService) {
        this.ollamaLlmService = ollamaLlmService;
        this.llmExecutor = llmExecutor;
        this.dramaturgyDirectorService = dramaturgyDirectorService != null ? dramaturgyDirectorService : new DramaturgyDirectorService();
    }

    public AgentService(OllamaLlmService ollamaLlmService, LlmExecutor llmExecutor) {
        this(ollamaLlmService, llmExecutor, new DramaturgyDirectorService());
    }

    public void setAgentDecisionPromptResource(Resource resource) {
        this.agentDecisionPromptResource = resource;
    }

    /**
     * Builds the complete parameter dictionary for the agent-decision.st template.
     */
    public Map<String, Object> buildPromptParams(Agent agent, SimulationRepository state) {
        Objects.requireNonNull(agent, "agent must not be null");
        Objects.requireNonNull(state, "state must not be null");

        String curLocId = agent.getCurrentLocationId();
        String curLocName = state.resolveLocationName(curLocId, state.getCurrentStep());
        Location curLoc = curLocId != null ? state.getLocations().get(curLocId) : null;
        String curLocDesc = curLoc != null && curLoc.getDescription() != null ? curLoc.getDescription() : "No location description available.";

        List<Agent> otherAgentsInRoom = state.getAgents().values().stream()
                .filter(a -> a.isAlive() && a.isHasArrived() && !a.getId().equals(agent.getId())
                        && Objects.equals(a.getCurrentLocationId(), curLocId))
                .toList();

        List<Agent> deadBodiesInRoom = state.getAgents().values().stream()
                .filter(a -> !a.isAlive() && Objects.equals(a.getCurrentLocationId(), curLocId))
                .toList();

        String roomCharactersSummary = otherAgentsInRoom.isEmpty()
                ? "No other characters present."
                : otherAgentsInRoom.stream()
                .map(a -> {
                    StringBuilder sb = new StringBuilder();
                    sb.append("- ").append(a.getId()).append(" (").append(a.getName());
                    sb.append(a.isAlive() ? "" : " (DEAD)");
                    if (a.getRole() != null) {
                        sb.append(" - Role: ").append(a.getRole());
                    }
                    if (a.getAppearance() != null && !a.getAppearance().isEmpty()) {
                        sb.append(" [Looks: ").append(a.getAppearance()).append("]");
                    }
                    List<String> carried = state.getItems().values().stream()
                            .filter(it -> a.getId().equals(it.getCurrentLocationId()))
                            .map(Item::getName)
                            .toList();
                    if (!carried.isEmpty()) {
                        sb.append(" [Carrying: ").append(String.join(", ", carried)).append("]");
                    }
                    sb.append(")");
                    return sb.toString();
                })
                .collect(Collectors.joining("\n"));

        String roomCorpsesSummary = deadBodiesInRoom.isEmpty()
                ? "No deceased bodies present."
                : deadBodiesInRoom.stream()
                .map(d -> "• DECEASED BODY: " + d.getName() + " (" + (d.getRole() != null ? d.getRole() : "Victim") + ") lies motionless on the floor here. (Target of the murder). Note: ALL characters listed above under CHARACTERS PRESENT are ALIVE.")
                .collect(Collectors.joining("\n"));

        List<Item> visibleRoomItems = state.getItems().values().stream()
                .filter(i -> Objects.equals(i.getCurrentLocationId(), curLocId) && !i.isHidden())
                .toList();

        String roomItemsSummary = visibleRoomItems.isEmpty()
                ? "No visible items in the room."
                : visibleRoomItems.stream()
                .map(i -> {
                    String mobility = i.isMovable() ? "Portable/Movable" : "Stationary Machine/Fixture - Fixed in place";
                    return String.format("- %s (%s, Type: %s, %s)", i.getId(), i.getName(), i.getType(), mobility);
                })
                .collect(Collectors.joining("\n"));

        List<Item> carriedItems = state.getItems().values().stream()
                .filter(i -> Objects.equals(i.getCurrentLocationId(), agent.getId()))
                .toList();

        String carriedItemsSummary = carriedItems.isEmpty()
                ? "None."
                : carriedItems.stream()
                .map(i -> String.format("- %s (%s, Type: %s)", i.getId(), i.getName(), i.getType()))
                .collect(Collectors.joining("\n"));

        Agent deadVictim = state.getAgents().values().stream()
                .filter(a -> !a.isAlive() && a.getCurrentLocationId() != null)
                .findFirst()
                .orElse(null);
        String crimeLocId = deadVictim != null ? deadVictim.getCurrentLocationId() : null;

        String availableLocationsSummary = state.getLocations().values().stream()
                .filter(l -> !Objects.equals(l.getId(), curLocId))
                .map(l -> {
                    String base = String.format("- %s (%s)", l.getId(), l.getName());
                    if (Objects.equals(l.getId(), crimeLocId)) {
                        base += " [CRIME SCENE - Body of " + deadVictim.getName() + " located here!]";
                    }
                    return base;
                })
                .collect(Collectors.joining("\n"));

        String tracesSummary = "None.";
        if (curLoc != null && curLoc.getTraces() != null && !curLoc.getTraces().isEmpty()) {
            tracesSummary = curLoc.getTraces().stream()
                    .map(t -> {
                        String desc = "- " + t.getDescription();
                        String descLower = t.getDescription().toLowerCase(Locale.ROOT);
                        if (descLower.contains(agent.getName().toLowerCase(Locale.ROOT)) || descLower.contains("you")) {
                            desc += " [NOTICE: This trace directly addresses or concerns YOU! Consider investigating or following it!]";
                        }
                        return desc;
                    })
                    .collect(Collectors.joining("\n"));
        }

        String recentMemoriesSummary = (agent.getEpisodicMemories() != null && !agent.getEpisodicMemories().isEmpty())
                ? agent.getEpisodicMemories().stream()
                .skip(Math.max(0, agent.getEpisodicMemories().size() - 4))
                .map(m -> String.format("- Step %d (%s): %s", m.timeStep(), m.locationName(), String.join("; ", m.observedEvents())))
                .collect(Collectors.joining("\n"))
                : "No previous memories.";

        String knowledgeSummary = (agent.getKnowledgeBase() != null && !agent.getKnowledgeBase().isEmpty())
                ? agent.getKnowledgeBase().stream()
                .map(Knowledge::information)
                .collect(Collectors.joining("\n"))
                : "No specific knowledge recorded.";

        String directives = dramaturgyDirectorService.buildDramaturgicDirectives(agent, state);

        boolean searchOnCooldown = agent.getConsecutiveSearchCount() >= 1;
        boolean observeOnCooldown = agent.getConsecutiveObserveCount() >= 1 &&
                Objects.equals(agent.getCurrentLocationId(), agent.getLastObservedLocationId());
        boolean reflectOnCooldown = agent.getConsecutiveReflectCount() >= 1;
        boolean hasOtherAgents = otherAgentsInRoom != null && !otherAgentsInRoom.isEmpty();
        boolean crimeAlreadyCommitted = state.getAgents().values().stream().anyMatch(a -> !a.isAlive());

        List<String> allowedActionList = new ArrayList<>(List.of(
                "MOVE", "ALTER_ENVIRONMENT", "MANIPULATE_ITEM"
        ));
        if (hasOtherAgents) {
            allowedActionList.add("TALK");
            allowedActionList.add("CASUAL_INTERACT");
        }
        if (!searchOnCooldown) {
            allowedActionList.add("SEARCH");
        }
        if (!observeOnCooldown) {
            allowedActionList.add("OBSERVE");
        }
        if (!reflectOnCooldown) {
            allowedActionList.add("REFLECT");
        }
        long aliveWitnessCount = otherAgentsInRoom != null ? otherAgentsInRoom.stream()
                .filter(a -> a.isAlive() && !Objects.equals(a.getId(), agent.getTargetVictimId()))
                .count() : 0;
        boolean hasEnvironmentalCover = state.getActiveGlobalEvents(state.getCurrentStep()).stream()
                .anyMatch(ge -> "ENVIRONMENTAL".equals(ge.getImpactType()) || "ISOLATION".equals(ge.getImpactType()));
        boolean isMurderPacingSatisfied = state.getCurrentStep() >= 12;
        boolean canMurderNow = agent.isMurderer() && !crimeAlreadyCommitted && hasOtherAgents
                && isMurderPacingSatisfied && (aliveWitnessCount == 0 || hasEnvironmentalCover);
        if (canMurderNow) {
            allowedActionList.add("MURDER");
        }
        if (agent.isDetective() && !agent.isMurderer()) {
            allowedActionList.add("ACCUSE");
        }
        String allowedActionsSummary = String.join(", ", allowedActionList);

        if (agent.isMurderer() && !crimeAlreadyCommitted) {
            if (!isMurderPacingSatisfied) {
                directives += "\n\n[EARLY PACING DIRECTIVE]: The mystery has just begun (Step " + state.getCurrentStep() + " < 12). " +
                        "Direct MURDER is locked during early introductions. Build social cover, study room layouts, and locate weapons.";
            } else if (hasOtherAgents && aliveWitnessCount > 0 && !hasEnvironmentalCover) {
                directives += "\n\n[WITNESS DANGER DIRECTIVE]: There are " + aliveWitnessCount + " witnesses present in this room! " +
                        "Direct MURDER is FORBIDDEN in front of witnesses. Either choose MOVE to follow your victim to an isolated room, or use covert poison/sabotage.";
            }
        }

        boolean detectiveInRoom = otherAgentsInRoom != null && otherAgentsInRoom.stream().anyMatch(Agent::isDetective);
        boolean hasWitnessKnowledge = agent.getKnowledgeBase().stream()
                .anyMatch(k -> {
                    String norm = k.information().toLowerCase(java.util.Locale.ROOT);
                    return norm.contains("saw ") || norm.contains("murder") || norm.contains("collapse dead");
                });
        if (detectiveInRoom && hasWitnessKnowledge && !agent.isMurderer()) {
            directives += "\n\n[URGENT WITNESS DIRECTIVE]: The Detective is in this room! You possess critical eyewitness information about the crime. You should prioritize speaking with the Detective (TALK) and share what you witnessed!";
        }

        if (!hasOtherAgents) {
            directives += "\n\n[SOLITARY LOCATION MANDATE]: You are completely ALONE in " + curLocName + ". " +
                    "TALK and CASUAL_INTERACT are STRICTLY FORBIDDEN. If you choose OBSERVE or SEARCH, you can ONLY observe the physical room, machinery, or ambience. You CANNOT see, hear, or observe absent characters!";
        }
        if (searchOnCooldown) {
            String activeOptions = !hasOtherAgents ? "MOVE to another room" : "MOVE to another room or TALK to someone present";
            directives += "\n\n[ACTION COOLDOWN - ANTI-LOOPING]: You have already thoroughly searched " + curLocName + ". " +
                    "SEARCH is on COOLDOWN and FORBIDDEN this turn. You MUST choose: " + activeOptions + ".";
        }
        if (observeOnCooldown) {
            String activeOptions = !hasOtherAgents ? "MOVE to another room" : "MOVE to another room, TALK to someone present, or SEARCH";
            directives += "\n\n[ACTION COOLDOWN - ANTI-STALLING]: You have already observed " + curLocName + " in your recent turn. " +
                    "OBSERVE is on COOLDOWN and FORBIDDEN this turn. You MUST choose an active action: " + activeOptions + ".";
        }
        if (reflectOnCooldown) {
            String activeOptions = !hasOtherAgents ? "MOVE to another room" : "MOVE, TALK to someone present, or SEARCH";
            directives += "\n\n[ACTION COOLDOWN - ANTI-STALLING]: You have already spent turns in quiet contemplation. " +
                    "REFLECT is on COOLDOWN this turn. You MUST choose an active action: " + activeOptions + ".";
        }
        if (agent.getTurnsInCurrentLocation() >= 3) {
            directives += "\n\n[MOBILITY MANDATE]: You have lingered in " + curLocName + " for " + agent.getTurnsInCurrentLocation() + " turns. " +
                    "You should strongly consider choosing MOVE to explore other rooms!";
        }

        int nextStep = state.getCurrentStep() + 1;
        String clockTime = state.getClockTime(state.getCurrentStep());

        Map<String, Object> params = new HashMap<>();
        params.put("storyTitle", state.getTitle() != null ? state.getTitle() : "Mystery");
        params.put("premise", state.getPremise() != null ? state.getPremise() : "A murder mystery unfolds.");
        params.put("agentName", agent.getName());
        params.put("agentRole", agent.getRole() != null ? agent.getRole() : "Guest");
        params.put("agentAppearance", agent.getAppearance() != null ? agent.getAppearance() : "Ordinary appearance");
        params.put("agentPersonality", agent.getPersonality() != null ? agent.getPersonality() : "Reserved");
        params.put("agentRelationship", agent.getRelationship() != null ? agent.getRelationship() : "Acquaintance");
        boolean emergencyOverride = dramaturgyDirectorService.isEmergencyOverrideActive(state);
        String secretGoal = agent.getSecretGoal() != null ? agent.getSecretGoal() : "None";
        if (crimeAlreadyCommitted && agent.isMurderer()) {
            secretGoal = "[POST-CRIME PHASE - COVER YOUR TRACKS]: The victim is dead! Your mission is accomplished. " +
                    "Under NO circumstances attempt another murder. Act normal, hide any weapons or traces, blend in, and never reveal your guilt.";
        } else if (emergencyOverride && !agent.isMurderer()) {
            String crisisDesc = dramaturgyDirectorService.getActiveCrisisDescription(state);
            secretGoal = String.format(
                "[EMERGENCY OVERRIDE - ACTIVE CRISIS]: Normal routines and background goals are strictly suspended! " +
                "%s Your immediate priorities are shock, self-preservation, securing an airtight alibi, checking on allies, and finding out who the killer is!",
                crisisDesc != null ? crisisDesc : "A murder has just been discovered!"
            );
        } else if (emergencyOverride && agent.isMurderer()) {
            secretGoal = "[EMERGENCY OVERRIDE - COVER YOUR TRACKS]: The alarm has been sounded and the body discovered! " +
                "Blend in immediately, pretend to be horrified by the discovery, discard any weapons, and establish a bulletproof alibi!";
        }
        params.put("agentSecretGoal", secretGoal);

        String recentActionsSummary = (agent.getRecentActions() != null && !agent.getRecentActions().isEmpty())
                ? String.join("\n", agent.getRecentActions())
                : "No recent actions recorded.";
        params.put("recentActions", recentActionsSummary);
        params.put("currentTimeStep", nextStep);
        params.put("clockTime", clockTime);
        params.put("currentLocationId", curLocId != null ? curLocId : "room_0");
        params.put("currentLocationName", curLocName);
        params.put("currentLocationDescription", curLocDesc);
        params.put("roomCharacters", roomCharactersSummary);
        params.put("roomCorpses", roomCorpsesSummary);
        params.put("roomItems", roomItemsSummary);
        params.put("carriedItems", carriedItemsSummary);
        params.put("availableLocations", availableLocationsSummary.isEmpty() ? "No adjacent locations" : availableLocationsSummary);
        params.put("environmentTraces", tracesSummary);
        params.put("recentMemories", recentMemoriesSummary);
        params.put("knowledgeBase", knowledgeSummary);
        params.put("dramaturgicDirectives", directives != null ? directives : "None");
        params.put("allowedActions", allowedActionsSummary);

        return params;
    }

    /**
     * Renders the agent prompt string using Spring AI PromptTemplate.
     */
    public String buildPrompt(Agent agent, SimulationRepository state) {
        Map<String, Object> params = buildPromptParams(agent, state);
        PromptTemplate template = new PromptTemplate(agentDecisionPromptResource);
        return template.render(params);
    }

    /**
     * Executes the cognitive decision-making step for an agent using Spring AI ChatClient
     * and LlmExecutor with strict semantic validation.
     */
    public AgentActionRecord decideAction(Agent agent, SimulationRepository state) {
        Objects.requireNonNull(agent, "agent must not be null");
        Objects.requireNonNull(state, "state must not be null");

        ChatClient chatClient = ollamaLlmService.getChatClient(LlmRole.AGENT).mutate()
                .defaultSystem("You are an expert roleplayer and murder mystery character agent. Make decisions strictly in-character, prioritizing survival, secrecy, and goals. Output valid structured JSON matching the requested schema.")
                .build();

        String curLocId = agent.getCurrentLocationId();
        String curLocName = state.resolveLocationName(curLocId, state.getCurrentStep());
        int nextStep = state.getCurrentStep() + 1;

        List<Agent> otherAgentsInRoom = state.getAgents().values().stream()
                .filter(a -> a.isAlive() && a.isHasArrived() && !a.getId().equals(agent.getId())
                        && Objects.equals(a.getCurrentLocationId(), curLocId))
                .toList();

        Map<String, Object> params = buildPromptParams(agent, state);

        log.info("AgentService: Deciding action for agent {} ({}) in {} at step {} [Model: {}]",
                agent.getName(), agent.getId(), curLocName, nextStep, ollamaLlmService.getModel(LlmRole.AGENT));

        AgentActionRecord decision = llmExecutor.execute(
                () -> chatClient.prompt()
                        .user(u -> u.text(agentDecisionPromptResource).params(params))
                        .call()
                        .entity(AgentActionRecord.class),
                record -> validateAction(record, agent, state, otherAgentsInRoom),
                "Invalid action generated for agent " + agent.getName(),
                MAX_SEMANTIC_ATTEMPTS,
                MAX_TECHNICAL_RETRIES
        );

        ActionType finalType = decision.actionType() != null ? decision.actionType() : ActionType.SEARCH;
        boolean observeCooldown = agent.getConsecutiveObserveCount() >= 1 &&
                Objects.equals(agent.getCurrentLocationId(), agent.getLastObservedLocationId());
        boolean hasPresentOthers = otherAgentsInRoom != null && !otherAgentsInRoom.isEmpty();
        boolean crimeAlreadyCommitted = state.getAgents().values().stream().anyMatch(a -> !a.isAlive());

        if (finalType == ActionType.SEARCH && agent.getConsecutiveSearchCount() >= 1) {
            finalType = hasPresentOthers ? ActionType.TALK : ActionType.MOVE;
        }
        if (finalType == ActionType.OBSERVE && observeCooldown) {
            finalType = hasPresentOthers ? ActionType.TALK : ActionType.MOVE;
        }
        if (finalType == ActionType.REFLECT && agent.getConsecutiveReflectCount() >= 1) {
            finalType = hasPresentOthers ? ActionType.TALK : ActionType.MOVE;
        }
        if ((finalType == ActionType.TALK || finalType == ActionType.CASUAL_INTERACT) && !hasPresentOthers) {
            finalType = ActionType.MOVE;
        }
        if (finalType == ActionType.MURDER && (!agent.isMurderer() || crimeAlreadyCommitted || !hasPresentOthers)) {
            finalType = hasPresentOthers ? ActionType.TALK : ActionType.MOVE;
        }
        if (finalType == ActionType.ACCUSE && (!agent.isDetective() || agent.isMurderer())) {
            finalType = hasPresentOthers ? ActionType.TALK : ActionType.MOVE;
        }

        String targetLocId = decision.targetLocationId();
        final String rawTargetAgentId = decision.targetAgentId();
        String targetAgentId = rawTargetAgentId;
        String details = decision.details();

        if (finalType == ActionType.MOVE) {
            if (targetLocId == null || !state.getLocations().containsKey(targetLocId) || Objects.equals(targetLocId, curLocId)) {
                targetLocId = state.getLocations().values().stream()
                        .filter(l -> !Objects.equals(l.getId(), curLocId))
                        .map(Location::getId)
                        .findFirst()
                        .orElse(curLocId);
            }
            if (details == null || details.isBlank() || decision.actionType() != ActionType.MOVE) {
                String destName = state.resolveLocationName(targetLocId, state.getCurrentStep());
                details = agent.getName() + " walks to " + destName + ".";
            }
        } else if (finalType == ActionType.TALK) {
            if (hasPresentOthers) {
                if (rawTargetAgentId == null || otherAgentsInRoom.stream().noneMatch(a -> a.getId().equals(rawTargetAgentId))) {
                    targetAgentId = otherAgentsInRoom.get(0).getId();
                }
                if (details == null || details.isBlank() || decision.actionType() != ActionType.TALK) {
                    Agent partner = state.getAgents().get(targetAgentId);
                    details = agent.getName() + " speaks with " + (partner != null ? partner.getName() : "someone") + ".";
                }
            }
        }

        return new AgentActionRecord(
                agent.getId(),
                finalType,
                targetLocId,
                targetAgentId,
                decision.sharedKnowledge(),
                decision.weaponItemId(),
                decision.traceDescription(),
                decision.targetItemId(),
                decision.interaction(),
                details != null ? details : "Observes the surroundings quietly.",
                decision.newKnowledgeFact()
        );
    }

    /**
     * Strict semantic validation of the returned AgentActionRecord.
     */
    public boolean validateAction(AgentActionRecord record, Agent agent, SimulationRepository state, List<Agent> otherAgentsInRoom) {
        if (record == null || record.actionType() == null) {
            log.warn("Action validation rejected: record or actionType is null | Action: {}", formatActionSummary(record));
            return false;
        }

        ActionType type = record.actionType();

        if (type == ActionType.OBSERVE) {
            if (agent.getConsecutiveObserveCount() >= 1 &&
                    Objects.equals(agent.getCurrentLocationId(), agent.getLastObservedLocationId())) {
                log.warn("Action validation rejected: Agent {} attempted OBSERVE but is on cooldown in {} (already observed {} consecutive times) | Action: {}",
                        agent.getName(), state.resolveLocationName(agent.getCurrentLocationId(), state.getCurrentStep()),
                        agent.getConsecutiveObserveCount(), formatActionSummary(record));
                return false;
            }

            // Presence check for OBSERVE: Cannot observe characters who are not in the room
            Set<String> presentNames = otherAgentsInRoom != null ? otherAgentsInRoom.stream()
                    .map(a -> a.getName().toLowerCase(java.util.Locale.ROOT))
                    .collect(Collectors.toSet()) : new HashSet<>();
            presentNames.add(agent.getName().toLowerCase(java.util.Locale.ROOT));

            for (Agent other : state.getAgents().values()) {
                String otherNameLower = other.getName().toLowerCase(java.util.Locale.ROOT);
                if (!presentNames.contains(otherNameLower)) {
                    boolean detailsMentions = record.details() != null && record.details().toLowerCase(java.util.Locale.ROOT).contains(otherNameLower);
                    boolean factMentions = record.newKnowledgeFact() != null && record.newKnowledgeFact().toLowerCase(java.util.Locale.ROOT).contains(otherNameLower);
                    if (detailsMentions || factMentions) {
                        log.warn("Action validation rejected: Agent {} attempted OBSERVE referencing absent character '{}' (present in room: {}) | Action: {}",
                                agent.getName(), other.getName(), presentNames, formatActionSummary(record));
                        return false;
                    }
                }
            }
        }

        // Physical presence check for deceased bodies / corpses
        boolean hasCorpseInRoom = state.getAgents().values().stream()
                .anyMatch(a -> !a.isAlive() && Objects.equals(a.getCurrentLocationId(), agent.getCurrentLocationId()));
        if (!hasCorpseInRoom) {
            java.util.regex.Pattern corpsePattern = java.util.regex.Pattern.compile(
                    "(?i)\\b(?:the\\s+body|a\\s+body|the\\s+corpse|a\\s+corpse|liket?|död\\s+kropp|victim's\\s+body|lifeless\\s+form|cause\\s+of\\s+death|embedded\\s+in\\s+the\\s+back\\s+of\\s+the\\s+neck)\\b"
            );
            boolean detailsHasCorpse = record.details() != null && corpsePattern.matcher(record.details()).find();
            boolean factHasCorpse = record.newKnowledgeFact() != null && corpsePattern.matcher(record.newKnowledgeFact()).find();
            if (detailsHasCorpse || factHasCorpse) {
                log.warn("Action validation rejected: Agent {} attempted to describe or observe a deceased body in '{}' where no dead body exists! | Action: {}",
                        agent.getName(), state.resolveLocationName(agent.getCurrentLocationId(), state.getCurrentStep()), formatActionSummary(record));
                return false;
            }
        }
        if (type == ActionType.ALTER_ENVIRONMENT && record.traceDescription() != null && !record.traceDescription().isBlank()) {
            String normTrace = record.traceDescription().trim().toLowerCase(java.util.Locale.ROOT);
            boolean repeatTrace = agent.getRecentActions().stream()
                    .anyMatch(ra -> ra != null && ra.toLowerCase(java.util.Locale.ROOT).contains(normTrace));
            if (repeatTrace) {
                log.warn("Action validation rejected: Agent {} attempted repeat ALTER_ENVIRONMENT with existing trace '{}' | Action: {}",
                        agent.getName(), record.traceDescription(), formatActionSummary(record));
                return false;
            }
        }
        if (type == ActionType.REFLECT && agent.getConsecutiveReflectCount() >= 1) {
            log.warn("Action validation rejected: Agent {} attempted REFLECT but is on cooldown (already reflected {} consecutive times) | Action: {}",
                    agent.getName(), agent.getConsecutiveReflectCount(), formatActionSummary(record));
            return false;
        }
        if (type == ActionType.SEARCH && agent.getConsecutiveSearchCount() >= 1) {
            log.warn("Action validation rejected: Agent {} attempted SEARCH but is on cooldown (already searched {} consecutive times) | Action: {}",
                    agent.getName(), agent.getConsecutiveSearchCount(), formatActionSummary(record));
            return false;
        }

        if (type == ActionType.MURDER) {
            if (!agent.isMurderer()) {
                log.warn("Action validation rejected: Agent {} attempted MURDER but is not the murderer | Action: {}",
                        agent.getName(), formatActionSummary(record));
                return false;
            }
            boolean crimeAlreadyCommitted = state.getAgents().values().stream().anyMatch(a -> !a.isAlive());
            if (crimeAlreadyCommitted) {
                log.warn("Action validation rejected: Agent {} attempted MURDER but a murder has already taken place | Action: {}",
                        agent.getName(), formatActionSummary(record));
                return false;
            }
            String targetAgentId = record.targetAgentId();
            Agent victim = targetAgentId != null ? state.getAgents().get(targetAgentId) : null;
            if (victim == null || !victim.isAlive()) {
                log.warn("Action validation rejected: Murder target is null or already dead: {} | Action: {}",
                        targetAgentId, formatActionSummary(record));
                return false;
            }
            boolean victimInRoom = otherAgentsInRoom != null && otherAgentsInRoom.stream().anyMatch(a -> a.getId().equals(targetAgentId));
            if (!victimInRoom) {
                log.warn("Action validation rejected: Murder victim '{}' is not in room with murderer {} | Action: {}",
                        targetAgentId, agent.getName(), formatActionSummary(record));
                return false;
            }
            if (state.getCurrentStep() < 12) {
                log.warn("Action validation rejected: Agent {} attempted MURDER too early (step {} < 12). Tension must build first | Action: {}",
                        agent.getName(), state.getCurrentStep(), formatActionSummary(record));
                return false;
            }
            long aliveWitnessCount = otherAgentsInRoom.stream()
                    .filter(a -> a.isAlive() && !a.getId().equals(targetAgentId))
                    .count();
            boolean hasCover = state.getActiveGlobalEvents(state.getCurrentStep()).stream()
                    .anyMatch(ge -> "ENVIRONMENTAL".equals(ge.getImpactType()) || "ISOLATION".equals(ge.getImpactType()));
            if (aliveWitnessCount > 0 && !hasCover) {
                log.warn("Action validation rejected: Agent {} attempted direct MURDER in front of {} witnesses without environmental cover | Action: {}",
                        agent.getName(), aliveWitnessCount, formatActionSummary(record));
                return false;
            }
        }

        if (type == ActionType.ACCUSE) {
            if (!agent.isDetective() || agent.isMurderer()) {
                log.warn("Action validation rejected: Agent {} attempted ACCUSE but is not an eligible detective | Action: {}",
                        agent.getName(), formatActionSummary(record));
                return false;
            }
            String targetAgentId = record.targetAgentId();
            if (targetAgentId == null || !state.getAgents().containsKey(targetAgentId)) {
                log.warn("Action validation rejected: targetAgentId '{}' invalid for ACCUSE | Action: {}",
                        targetAgentId, formatActionSummary(record));
                return false;
            }
        }

        if (type == ActionType.MOVE) {
            String targetLocId = record.targetLocationId();
            if (targetLocId == null || !state.getLocations().containsKey(targetLocId)) {
                List<String> validLocs = state.getLocations().values().stream()
                        .filter(l -> !Objects.equals(l.getId(), agent.getCurrentLocationId()))
                        .map(l -> l.getId() + " (" + l.getName() + ")")
                        .toList();
                log.warn("Action validation rejected: Target location '{}' invalid for MOVE (available locations: {}) | Action: {}",
                        targetLocId, validLocs.isEmpty() ? "none" : validLocs, formatActionSummary(record));
                return false;
            }
            if (Objects.equals(targetLocId, agent.getCurrentLocationId())) {
                log.warn("Action validation rejected: Agent tried to MOVE to current location '{}' | Action: {}",
                        targetLocId, formatActionSummary(record));
                return false;
            }
        }

        if (type == ActionType.TALK || type == ActionType.CASUAL_INTERACT) {
            if (otherAgentsInRoom == null || otherAgentsInRoom.isEmpty()) {
                log.warn("Action validation rejected: Agent {} attempted {} while alone in room | Action: {}",
                        agent.getName(), type, formatActionSummary(record));
                return false;
            }
            if (type == ActionType.TALK) {
                String targetAgentId = record.targetAgentId();
                if (targetAgentId == null) {
                    log.warn("Action validation rejected: targetAgentId missing for TALK | Action: {}", formatActionSummary(record));
                    return false;
                }
                boolean targetInRoom = otherAgentsInRoom.stream().anyMatch(a -> a.getId().equals(targetAgentId));
                if (!targetInRoom) {
                    List<String> presentOthers = otherAgentsInRoom.stream().map(a -> a.getId() + " (" + a.getName() + ")").toList();
                    log.warn("Action validation rejected: targetAgent '{}' is not in room with {} (present in room: {}) | Action: {}",
                            targetAgentId, agent.getName(), presentOthers.isEmpty() ? "none" : presentOthers, formatActionSummary(record));
                    return false;
                }
                // Anti-Blurt check: do not blurt out own secret goal verbatim
                if (agent.getSecretGoal() != null && !agent.getSecretGoal().isBlank()) {
                    String cleanGoal = agent.getSecretGoal().trim().toLowerCase(java.util.Locale.ROOT);
                    if (cleanGoal.length() > 15) {
                        String shared = record.sharedKnowledge() != null ? record.sharedKnowledge().toLowerCase(java.util.Locale.ROOT) : "";
                        String details = record.details() != null ? record.details().toLowerCase(java.util.Locale.ROOT) : "";
                        if (shared.contains(cleanGoal) || details.contains(cleanGoal)) {
                            log.warn("Action validation rejected: Agent {} attempted to blurt out their own secret goal verbatim | Action: {}",
                                    agent.getName(), formatActionSummary(record));
                            return false;
                        }
                    }
                }

                // Dialogue repetition check: cannot repeat identical or near-identical dialogue from recent actions
                if (isDuplicateDialogue(record, agent, otherAgentsInRoom)) {
                    log.warn("Action validation rejected: Agent {} attempted duplicate/repetitive dialogue already spoken in recent actions | Action: {}",
                            agent.getName(), formatActionSummary(record));
                    return false;
                }
            } else if (record.targetAgentId() != null) {
                boolean targetInRoom = otherAgentsInRoom.stream().anyMatch(a -> a.getId().equals(record.targetAgentId()));
                if (!targetInRoom) {
                    log.warn("Action validation rejected: CASUAL_INTERACT target '{}' is not in room | Action: {}",
                            record.targetAgentId(), formatActionSummary(record));
                    return false;
                }
            }
        }

        // Ghost check: details referencing dead agents
        if (record.details() != null && (type == ActionType.TALK || type == ActionType.CASUAL_INTERACT)) {
            List<String> deadNames = state.getAgents().values().stream()
                    .filter(a -> !a.isAlive())
                    .map(Agent::getName)
                    .toList();
            for (String deadName : deadNames) {
                if (record.details().toLowerCase(Locale.ROOT).contains(deadName.toLowerCase(Locale.ROOT))) {
                    log.warn("Action validation rejected: Agent {} attempted social interaction referencing dead character '{}' | Action: {}",
                            agent.getName(), deadName, formatActionSummary(record));
                    return false;
                }
            }
        }

        if (type == ActionType.MANIPULATE_ITEM) {
            String targetItemId = record.targetItemId();
            if (targetItemId == null || !state.getItems().containsKey(targetItemId)) {
                List<String> roomItems = state.getItems().values().stream()
                        .filter(i -> Objects.equals(i.getCurrentLocationId(), agent.getCurrentLocationId()))
                        .map(i -> i.getId() + " (" + i.getName() + ")")
                        .toList();
                log.warn("Action validation rejected: targetItemId '{}' is invalid (items in room: {}) | Action: {}",
                        targetItemId, roomItems.isEmpty() ? "none" : roomItems, formatActionSummary(record));
                return false;
            }
            Item item = state.getItems().get(targetItemId);
            if (!item.isMovable() && "TAKE".equalsIgnoreCase(record.interaction())) {
                log.warn("Action validation rejected: Agent {} tried to TAKE immovable item '{}' | Action: {}",
                        agent.getName(), item.getName(), formatActionSummary(record));
                return false;
            }
        }

        return true;
    }

    boolean isDuplicateDialogue(AgentActionRecord record, Agent agent, List<Agent> otherAgentsInRoom) {
        String details = record.details() != null ? record.details().trim() : "";
        String shared = record.sharedKnowledge() != null ? record.sharedKnowledge().trim() : "";

        // Collect recent action summaries to check against: agent's own recent actions + others in room
        List<String> historyToCheck = new ArrayList<>();
        if (agent.getRecentActions() != null) {
            historyToCheck.addAll(agent.getRecentActions());
        }
        if (otherAgentsInRoom != null) {
            for (Agent other : otherAgentsInRoom) {
                if (other.getRecentActions() != null) {
                    historyToCheck.addAll(other.getRecentActions());
                }
            }
        }

        if (historyToCheck.isEmpty()) {
            return false;
        }

        // 1. Check exact quoted dialogue in details (e.g. text inside single or double quotes of length >= 12)
        Pattern quotePattern = Pattern.compile("['\"]([^'\"]{12,})['\"]");
        Matcher quoteMatcher = quotePattern.matcher(details);
        while (quoteMatcher.find()) {
            String quote = quoteMatcher.group(1).trim().toLowerCase(Locale.ROOT);
            for (String past : historyToCheck) {
                if (past != null && past.toLowerCase(Locale.ROOT).contains(quote)) {
                    return true;
                }
            }
        }

        // 2. Check 6-word phrase n-gram matching in details (e.g. "blue residue I found on the walkway")
        if (details.length() >= 30) {
            String cleanDetails = details.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\s]", " ").replaceAll("\\s+", " ").trim();
            String[] words = cleanDetails.split(" ");
            if (words.length >= 6) {
                for (int i = 0; i <= words.length - 6; i++) {
                    String phrase = String.join(" ", java.util.Arrays.copyOfRange(words, i, i + 6));
                    if (phrase.length() >= 20) {
                        for (String past : historyToCheck) {
                            if (past != null) {
                                String cleanPast = past.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\s]", " ").replaceAll("\\s+", " ").trim();
                                if (cleanPast.contains(phrase)) {
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Check sharedKnowledge repetition
        if (shared.length() >= 20) {
            String cleanShared = shared.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\s]", " ").replaceAll("\\s+", " ").trim();
            for (String past : historyToCheck) {
                if (past != null) {
                    String cleanPast = past.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\s]", " ").replaceAll("\\s+", " ").trim();
                    if (cleanPast.contains(cleanShared)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private String formatActionSummary(AgentActionRecord record) {
        return record != null ? record.toSummaryString() : "null";
    }
}
