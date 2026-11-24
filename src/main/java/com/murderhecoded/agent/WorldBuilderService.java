package com.murderhecoded.agent;

import com.murderhecoded.domain.AgentRecord;
import com.murderhecoded.domain.ItemRecord;
import com.murderhecoded.domain.ItemType;
import com.murderhecoded.domain.LocationRecord;
import com.murderhecoded.domain.WorldSetupResponse;
import com.murderhecoded.llm.LlmExecutor;
import com.murderhecoded.service.OllamaLlmService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.murderhecoded.llm.LlmRole;

@Service
@Slf4j
public class WorldBuilderService {
    private static final int MAX_SEMANTIC_ATTEMPTS = 3;
    private static final int MAX_TECHNICAL_RETRIES = 3;

    private final LlmExecutor llmExecutor;
    private final OllamaLlmService ollamaLlmService;

    private Consumer<String> onLogEvent;

    @Value("classpath:/prompts/world/locations-step.st")
    private Resource locationsPromptResource;

    @Value("classpath:/prompts/world/items-step.st")
    private Resource itemsPromptResource;

    @Value("classpath:/prompts/world/agents-step.st")
    private Resource agentsPromptResource;

    public WorldBuilderService(OllamaLlmService ollamaLlmService, LlmExecutor llmExecutor) {
        this.ollamaLlmService = ollamaLlmService;
        this.llmExecutor = llmExecutor;
    }

    public void setOnLogEvent(Consumer<String> onLogEvent) {
        this.onLogEvent = onLogEvent;
        if (this.llmExecutor != null) {
            this.llmExecutor.setOnLogEvent(onLogEvent);
        }
    }

    private void emitLog(String message) {
        log.info(message);
        if (onLogEvent != null) {
            try {
                if (javafx.application.Platform.isFxApplicationThread()) {
                    onLogEvent.accept(message);
                } else {
                    try {
                        javafx.application.Platform.runLater(() -> onLogEvent.accept(message));
                    } catch (IllegalStateException e) {
                        onLogEvent.accept(message);
                    }
                }
            } catch (Exception e) {
                log.debug("Error delivering log event: {}", e.getMessage());
            }
        }
    }

    /**
     * Kör en sekventiell 3-stegs pipeline för att bygga spelvärlden:
     * 1. Generera rum och kartgraf.
     * 2. Placera ut föremål i de faktiskt skapade rummen.
     * 3. Skapa karaktärer med roller, motiv och startpositioner.
     */
    public WorldSetupResponse buildWorld(
            String title,
            String premise,
            int numPlaces,
            int numItemsPerPlace,
            int numAgents,
            String voice,
            String languageCode
    ) {
        ChatClient chatClient = ollamaLlmService.getChatClient(LlmRole.WORLD).mutate()
                .defaultSystem("You are an expert mystery writer and worldbuilder. Follow the requested schemas strictly.")
                .build();

        int totalExpectedItems = numPlaces * numItemsPerPlace;
        String lang = (languageCode != null && languageCode.equalsIgnoreCase("sv")) ? "SWEDISH" : "ENGLISH";
        String resolvedVoice = (voice != null && !voice.isBlank()) ? voice : "Classic Agatha Christie Mystery";

        emitLog(String.format("[WorldBuilder] Startar världsbygge för '%s' [Modell: %s]: %d rum, %d items (%d per rum), %d agenter",
                title, ollamaLlmService.getModel(LlmRole.WORLD), numPlaces, totalExpectedItems, numItemsPerPlace, numAgents));

        // STEG 1: Skapa alla platser/rum
        emitLog(String.format("[WorldBuilder Steg 1/3] Genererar %d platser...", numPlaces));
        List<LocationRecord> locations = generateLocations(chatClient, title, premise, numPlaces, resolvedVoice, lang);
        emitLog(String.format("[WorldBuilder Steg 1/3 Klar] Skapade %d platser: %s",
                locations.size(), locations.stream().map(l -> l.id() + " (" + l.name() + ")").collect(Collectors.joining(", "))));

        // STEG 2: Skapa föremål och placera i rummen från steg 1
        emitLog(String.format("[WorldBuilder Steg 2/3] Genererar %d föremål fördelade på rummen...", totalExpectedItems));
        List<ItemRecord> items = generateItems(chatClient, title, premise, locations, numItemsPerPlace, totalExpectedItems, resolvedVoice, lang);
        emitLog(String.format("[WorldBuilder Steg 2/3 Klar] Skapade %d föremål.", items.size()));

        // STEG 3: Skapa agenter med startpositioner i rummen från steg 1
        emitLog(String.format("[WorldBuilder Steg 3/3] Genererar %d karaktärer...", numAgents));
        List<AgentRecord> agents = generateAgents(chatClient, title, premise, locations, numAgents, resolvedVoice, lang);
        emitLog(String.format("[WorldBuilder Steg 3/3 Klar] Skapade %d karaktärer.", agents.size()));

        emitLog("[WorldBuilder] Världsbygge slutfört utan valideringsfel!");
        return new WorldSetupResponse(locations, items, agents);
    }

    // =========================================================================
    // STEG 1: LOCATIONS
    // =========================================================================
    private List<LocationRecord> generateLocations(
            ChatClient chatClient,
            String title,
            String premise,
            int numPlaces,
            String voice,
            String lang
    ) {
        LocationsPayload payload = llmExecutor.execute(
                () -> chatClient.prompt()
                        .user(u -> u.text(locationsPromptResource)
                                .param("title", title)
                                .param("premise", premise)
                                .param("voice", voice)
                                .param("language", lang)
                                .param("numPlaces", numPlaces)
                                .param("lastRoomIndex", numPlaces - 1))
                        .call()
                        .entity(LocationsPayload.class),

                result -> {
                    if (result == null || result.locations() == null || result.locations().isEmpty()) {
                        emitLog("[WorldBuilder Varning] Steg 1: Resultat eller 'locations'-listan var null/tom.");
                        return false;
                    }
                    int count = result.locations().size();
                    if (count < numPlaces) {
                        emitLog(String.format("[WorldBuilder Varning] Steg 1: För få platser genererade (%d av %d).", count, numPlaces));
                        return false;
                    }
                    return true;
                },
                "Kunde inte generera minst " + numPlaces + " giltiga rum",
                MAX_SEMANTIC_ATTEMPTS,
                MAX_TECHNICAL_RETRIES
        );

        List<LocationRecord> list = new ArrayList<>(payload.locations());
        if (list.size() > numPlaces) {
            emitLog(String.format("[WorldBuilder Info] Beskar %d genererade rum till begärda %d rum.", list.size(), numPlaces));
            list = list.subList(0, numPlaces);
        }
        List<LocationRecord> normalized = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            LocationRecord loc = list.get(i);
            String locId = (loc.id() != null && !loc.id().isBlank()) ? loc.id().trim() : ("room_" + i);
            String locName = (loc.name() != null && !loc.name().isBlank()) ? loc.name().trim() : ("Room " + (i + 1));
            String locDesc = loc.description() != null ? loc.description() : "";
            normalized.add(new LocationRecord(locId, locName, locDesc));
        }
        return normalized;
    }

    // =========================================================================
    // STEG 2: ITEMS
    // =========================================================================
    private List<ItemRecord> generateItems(
            ChatClient chatClient,
            String title,
            String premise,
            List<LocationRecord> locations,
            int itemsPerPlace,
            int totalItems,
            String voice,
            String lang
    ) {
        String availableRoomsSummary = locations.stream()
                .map(l -> String.format("- %s (%s): %s", l.id(), l.name(), l.description()))
                .collect(Collectors.joining("\n"));

        Set<String> validLocationIds = locations.stream()
                .map(LocationRecord::id)
                .collect(Collectors.toSet());

        Map<String, String> roomNameToId = new HashMap<>();
        for (LocationRecord l : locations) {
            if (l.name() != null) {
                roomNameToId.put(l.name().trim().toLowerCase(java.util.Locale.ROOT), l.id());
            }
        }

        ItemsPayload payload = llmExecutor.execute(
                () -> chatClient.prompt()
                        .user(u -> u.text(itemsPromptResource)
                                .param("title", title)
                                .param("premise", premise)
                                .param("voice", voice)
                                .param("language", lang)
                                .param("totalItems", totalItems)
                                .param("lastItem", totalItems - 1)
                                .param("itemsPerPlace", itemsPerPlace)
                                .param("availableRoomsSummary", availableRoomsSummary))
                        .call()
                        .entity(ItemsPayload.class),

                result -> {
                    if (result == null || result.items() == null || result.items().isEmpty()) {
                        emitLog("[WorldBuilder Varning] Steg 2: Inga föremål genererades (null eller tom lista).");
                        return false;
                    }
                    int count = result.items().size();
                    if (count < Math.max(1, totalItems - 2)) {
                        emitLog(String.format("[WorldBuilder Varning] Steg 2: För få föremål genererade (%d av %d).", count, totalItems));
                        return false;
                    }
                    return true;
                },
                "Kunde inte generera tillräckligt med föremål för rummen",
                MAX_SEMANTIC_ATTEMPTS,
                MAX_TECHNICAL_RETRIES
        );

        List<ItemRecord> rawItems = new ArrayList<>(payload.items());
        if (rawItems.size() > totalItems) {
            emitLog(String.format("[WorldBuilder Info] Beskar %d genererade föremål till begärda %d föremål.", rawItems.size(), totalItems));
            rawItems = rawItems.subList(0, totalItems);
        }

        List<ItemRecord> sanitized = new ArrayList<>();
        List<String> validList = new ArrayList<>(validLocationIds);
        for (int i = 0; i < rawItems.size(); i++) {
            ItemRecord it = rawItems.get(i);
            String locId = it.currentLocationId();
            if (locId == null || !validLocationIds.contains(locId)) {
                String resolved = (locId != null) ? roomNameToId.get(locId.trim().toLowerCase(java.util.Locale.ROOT)) : null;
                if (resolved != null) {
                    emitLog(String.format("[WorldBuilder Info] Länkade föremål '%s' från rumsnamn '%s' till rums-ID '%s'.", it.name(), locId, resolved));
                    locId = resolved;
                } else {
                    String fallback = validList.get(i % validList.size());
                    emitLog(String.format("[WorldBuilder Varning] Föremål '%s' hade okänt rums-ID '%s'. Placerades i '%s'.", it.name(), locId, fallback));
                    locId = fallback;
                }
            }

            // Natural room alignment: If item name explicitly references a specific room (e.g. "Shard of Observation Glass" -> "The Observation Gallery"), place it there!
            if (it.name() != null) {
                String itNameLower = it.name().toLowerCase(java.util.Locale.ROOT);
                for (LocationRecord lr : locations) {
                    String cleanRoom = lr.name().toLowerCase(java.util.Locale.ROOT).replace("the ", "").trim();
                    for (String word : cleanRoom.split("\\s+")) {
                        if (word.length() >= 5 && itNameLower.contains(word)) {
                            if (!Objects.equals(locId, lr.id())) {
                                emitLog(String.format("[WorldBuilder Info] Flyttade föremål '%s' till dess naturliga rum '%s' (%s).", it.name(), lr.name(), lr.id()));
                                locId = lr.id();
                            }
                            break;
                        }
                    }
                }
            }
            String itemId = (it.id() != null && !it.id().isBlank()) ? it.id().trim() : ("item_" + i);
            String itemName = (it.name() != null && !it.name().isBlank()) ? it.name().trim() : ("Item " + (i + 1));
            ItemType itemType = it.type() != null ? it.type() : ItemType.CLUE;
            sanitized.add(new ItemRecord(itemId, itemName, itemType, locId, it.movable(), it.hidden()));
        }

        return sanitized;
    }

    // =========================================================================
    // STEG 3: AGENTS
    // =========================================================================
    private List<AgentRecord> generateAgents(
            ChatClient chatClient,
            String title,
            String premise,
            List<LocationRecord> locations,
            int numAgents,
            String voice,
            String lang
    ) {
        String availableRoomsSummary = locations.stream()
                .map(l -> String.format("- %s (%s)", l.id(), l.name()))
                .collect(Collectors.joining("\n"));

        Set<String> validLocationIds = locations.stream()
                .map(LocationRecord::id)
                .collect(Collectors.toSet());

        Map<String, String> roomNameToId = new HashMap<>();
        for (LocationRecord l : locations) {
            if (l.name() != null) {
                roomNameToId.put(l.name().trim().toLowerCase(java.util.Locale.ROOT), l.id());
            }
        }

        AgentsPayload payload = llmExecutor.execute(
                () -> chatClient.prompt()
                        .user(u -> u.text(agentsPromptResource)
                                .param("title", title)
                                .param("premise", premise)
                                .param("voice", voice)
                                .param("language", lang)
                                .param("numAgents", numAgents)
                                .param("lastAgentIndex", numAgents - 1)
                                .param("availableRoomsSummary", availableRoomsSummary))
                        .call()
                        .entity(AgentsPayload.class),

                result -> validateAgentsPayload(result, numAgents),
                "Kunde inte generera giltiga agenter med detektiv och korrekta rums-ID:n",
                MAX_SEMANTIC_ATTEMPTS,
                MAX_TECHNICAL_RETRIES
        );

        return sanitizeAndEnforceAgents(payload.agents(), numAgents, validLocationIds, roomNameToId);
    }

    private boolean validateAgentsPayload(AgentsPayload payload, int expectedCount) {
        if (payload == null || payload.agents() == null || payload.agents().isEmpty()) {
            emitLog("[WorldBuilder Varning] Steg 3: Inga agenter genererades (null eller tom lista).");
            return false;
        }
        List<AgentRecord> agents = payload.agents();
        if (agents.size() < expectedCount) {
            emitLog(String.format("[WorldBuilder Varning] Steg 3: För få agenter genererade (%d av %d).", agents.size(), expectedCount));
            return false;
        }

        emitLog(String.format("[WorldBuilder Info] Granskar %d genererade agenter:", agents.size()));
        for (AgentRecord a : agents) {
            emitLog(String.format("   • [%s] %s (%s) | Startrum: %s | Hemligt mål: '%s'",
                    a.id(), a.name(), a.role(), a.currentLocationId(), a.secretGoal()));
        }

        return true;
    }

    List<AgentRecord> sanitizeAndEnforceAgents(
            List<AgentRecord> rawAgents,
            int numAgents,
            Set<String> validRoomIds,
            Map<String, String> roomNameToId
    ) {
        List<AgentRecord> agents = new ArrayList<>(rawAgents);
        if (agents.size() > numAgents) {
            emitLog(String.format("[WorldBuilder Info] Beskar %d genererade agenter till begärda %d agenter.", agents.size(), numAgents));
            agents = agents.subList(0, numAgents);
        }

        List<String> validList = new ArrayList<>(validRoomIds);

        // 1. Ensure room IDs are valid
        List<AgentRecord> roomSanitized = new ArrayList<>();
        for (int i = 0; i < agents.size(); i++) {
            AgentRecord a = agents.get(i);
            String locId = a.currentLocationId();
            if (locId == null || !validRoomIds.contains(locId)) {
                String resolved = (locId != null) ? roomNameToId.get(locId.trim().toLowerCase(java.util.Locale.ROOT)) : null;
                if (resolved != null) {
                    emitLog(String.format("[WorldBuilder Info] Länkade agent '%s' från rumsnamn '%s' till rums-ID '%s'.", a.name(), locId, resolved));
                    locId = resolved;
                } else {
                    String fallback = validList.get(i % validList.size());
                    emitLog(String.format("[WorldBuilder Varning] Agent '%s' hade okänt rums-ID '%s'. Placerades i '%s'.", a.name(), locId, fallback));
                    locId = fallback;
                }
            }
            roomSanitized.add(new AgentRecord(a.id(), a.name(), a.role(), a.appearance(), a.personality(), a.relationship(), a.secretGoal(), locId, a.hasArrived()));
        }

        // 2. Ensure detective agent_0 exists
        int detectiveIndex = -1;
        for (int i = 0; i < roomSanitized.size(); i++) {
            AgentRecord a = roomSanitized.get(i);
            if ("agent_0".equals(a.id()) || isDetectiveRole(a.role(), a.secretGoal())) {
                detectiveIndex = i;
                break;
            }
        }
        if (detectiveIndex == -1) {
            detectiveIndex = 0;
            emitLog("[WorldBuilder Info] Utser agent '" + roomSanitized.get(0).name() + "' till utredare (agent_0).");
        }

        // Swap detective to index 0 if not already there
        if (detectiveIndex != 0) {
            AgentRecord det = roomSanitized.remove(detectiveIndex);
            roomSanitized.add(0, det);
        }

        // Re-index all agents sequentially agent_0 .. agent_{N-1}
        List<AgentRecord> indexed = new ArrayList<>();
        for (int i = 0; i < roomSanitized.size(); i++) {
            AgentRecord a = roomSanitized.get(i);
            String agentId = "agent_" + i;
            String secretGoal = a.secretGoal();
            boolean hasArrived;
            if (i == 0) {
                secretGoal = "Solve the murder and identify the killer";
                hasArrived = false;
            } else {
                hasArrived = true;
            }
            indexed.add(new AgentRecord(agentId, a.name(), a.role(), a.appearance(), a.personality(), a.relationship(), secretGoal, a.currentLocationId(), hasArrived));
        }

        // 3. Ensure exactly ONE killer among non-detective suspects
        int killerIndex = -1;
        for (int i = 1; i < indexed.size(); i++) {
            AgentRecord a = indexed.get(i);
            if (isKillerGoal(a.secretGoal())) {
                if (killerIndex == -1) {
                    killerIndex = i;
                } else {
                    emitLog(String.format("[WorldBuilder Info] Neutraliserar extra mördare '%s' till misstänkt.", a.name()));
                    indexed.set(i, new AgentRecord(a.id(), a.name(), a.role(), a.appearance(), a.personality(), a.relationship(),
                            "Expose the truth and clear my name", a.currentLocationId(), a.hasArrived()));
                }
            }
        }

        if (killerIndex == -1) {
            AgentRecord killer = indexed.get(1);
            String victimName = (indexed.size() > 2) ? indexed.get(2).name() : "the victim";
            String newGoal = "Murder " + victimName + " because of a long-standing personal vendetta";
            emitLog(String.format("[WorldBuilder Info] Inget explicit mordmål hittades i LLM-svaret. Utser '%s' till mördare mot '%s'.", killer.name(), victimName));
            indexed.set(1, new AgentRecord(killer.id(), killer.name(), killer.role(), killer.appearance(), killer.personality(), killer.relationship(),
                    newGoal, killer.currentLocationId(), killer.hasArrived()));
        } else {
            AgentRecord killer = indexed.get(killerIndex);
            String goalLower = killer.secretGoal() != null ? killer.secretGoal().toLowerCase(java.util.Locale.ROOT) : "";
            boolean hasTarget = false;
            for (int j = 1; j < indexed.size(); j++) {
                if (j != killerIndex) {
                    if (goalLower.contains(indexed.get(j).name().toLowerCase(java.util.Locale.ROOT))) {
                        hasTarget = true;
                        break;
                    }
                }
            }

            String phantomFromGoal = null;
            if (!hasTarget) {
                // Killer's goal references a phantom character (e.g. Captain Ilya Rostov) not in the cast!
                // Reconcile by selecting a real non-detective suspect from indexed as the intended victim.
                int victimIndex = (killerIndex == 1 && indexed.size() > 2) ? 2 : 1;
                AgentRecord realVictim = indexed.get(victimIndex);
                String oldGoal = killer.secretGoal() != null ? killer.secretGoal() : "Murder";

                java.util.regex.Matcher goalTargetMatcher = java.util.regex.Pattern.compile(
                        "(?i:\\b(?:murder|kill|assassinate|mörda|döda)\\s+(?:the\\s+)?(?:victim\\s+)?)(.*?)(?:\\s+(?:because|to\\b|för\\s+att|due\\s+to|in\\s+order|so\\s+as)|$)"
                ).matcher(oldGoal);
                if (goalTargetMatcher.find()) {
                    String cand = goalTargetMatcher.group(1).trim();
                    if (!cand.isBlank()) {
                        phantomFromGoal = cand;
                    }
                }

                String reason;
                String lower = oldGoal.toLowerCase(java.util.Locale.ROOT);
                if (lower.contains(" because ")) {
                    reason = " because " + oldGoal.substring(lower.indexOf(" because ") + 9);
                } else if (lower.contains(" för att ")) {
                    reason = " för att " + oldGoal.substring(lower.indexOf(" för att ") + 10);
                } else if (lower.contains(" to cover ")) {
                    reason = " to cover " + oldGoal.substring(lower.indexOf(" to cover ") + 10);
                } else {
                    reason = " to protect critical secrets and settle a personal score";
                }

                String updatedGoal = "Murder " + realVictim.name() + reason;
                emitLog(String.format("[WorldBuilder Info] Mördarens mål refererade till en icke-existerande karaktär. Uppdaterar målet till att rikta sig mot '%s': '%s'",
                        realVictim.name(), updatedGoal));
                indexed.set(killerIndex, new AgentRecord(killer.id(), killer.name(), killer.role(), killer.appearance(), killer.personality(), killer.relationship(),
                        updatedGoal, killer.currentLocationId(), killer.hasArrived()));
            }

            // Sanitize phantom character references across all relationships and secret goals
            int targetVictimIdx = (killerIndex == 1 && indexed.size() > 2) ? 2 : 1;
            String confirmedVictimName = indexed.get(targetVictimIdx).name();
            Set<String> validNames = indexed.stream().map(AgentRecord::name).collect(Collectors.toSet());
            Set<String> validFirstLast = new HashSet<>();
            for (String vn : validNames) {
                for (String part : vn.split("\\s+")) {
                    if (part.length() > 2) validFirstLast.add(part.toLowerCase(Locale.ROOT));
                }
            }

            Set<String> phantomVictims = new HashSet<>();
            if (phantomFromGoal != null) {
                phantomVictims.add(phantomFromGoal);
                String stripped = phantomFromGoal.replaceFirst("^(?i)(?:director|captain|chief\\s+engineer|dr\\.?|doctor|lord|lady|inspector|detective|officer)\\s+", "").trim();
                if (!stripped.isBlank() && !stripped.equalsIgnoreCase(phantomFromGoal)) {
                    phantomVictims.add(stripped);
                }
            }

            // 1. Scan for explicit "victim <Name>" without (?i) on the name part
            Pattern explicitVictimPattern = Pattern.compile("(?i:\\b(?:the\\s+)?(?:victim|offret|den\\s+mördade)\\s+)([A-Z][a-z]+(?:\\s+[A-Z][a-z]+)*)");
            for (AgentRecord ar : indexed) {
                for (String text : List.of(ar.relationship() != null ? ar.relationship() : "", ar.secretGoal() != null ? ar.secretGoal() : "")) {
                    Matcher m = explicitVictimPattern.matcher(text);
                    while (m.find()) {
                        String matchedName = m.group(1).trim();
                        boolean isReal = validNames.stream().anyMatch(vn -> vn.toLowerCase(Locale.ROOT).contains(matchedName.toLowerCase(Locale.ROOT)));
                        if (!isReal && matchedName.length() > 2) {
                            phantomVictims.add(matchedName);
                        }
                    }
                }
            }

            // 2. Scan for titled names like "Director Elara Voss", "Captain Ilya Rostov"
            Pattern titledPattern = Pattern.compile("\\b(Director|Chief\\s+Engineer|Captain|Dr\\.?|Doctor|Lord|Lady|Inspector|Detective|Officer|Commander|Professor|Prof\\.?|Minister|President|CEO|Sir|Madam|Mr\\.?|Mrs\\.?|Ms\\.?)\\s+([A-Z][a-z]+(?:\\s+[A-Z][a-z]+)*)\\b");
            for (AgentRecord ar : indexed) {
                for (String text : List.of(ar.relationship() != null ? ar.relationship() : "", ar.secretGoal() != null ? ar.secretGoal() : "")) {
                    Matcher m = titledPattern.matcher(text);
                    while (m.find()) {
                        String fullTitled = m.group(0).trim();
                        String nameOnly = m.group(2).trim();
                        boolean isReal = validNames.stream().anyMatch(vn -> vn.toLowerCase(Locale.ROOT).contains(nameOnly.toLowerCase(Locale.ROOT)));
                        if (!isReal) {
                            phantomVictims.add(fullTitled);
                            phantomVictims.add(nameOnly);
                            String titleOnly = m.group(1).trim();
                            String[] nameParts = nameOnly.split("\\s+");
                            if (nameParts.length > 1) {
                                String lastName = nameParts[nameParts.length - 1];
                                phantomVictims.add(titleOnly + " " + lastName);
                                if (!validFirstLast.contains(lastName.toLowerCase(Locale.ROOT))) {
                                    phantomVictims.add(lastName);
                                }
                            }
                        }
                    }
                }
            }

            if (!phantomVictims.isEmpty()) {
                List<String> sortedPhantoms = phantomVictims.stream()
                        .sorted((a, b) -> Integer.compare(b.length(), a.length()))
                        .toList();

                for (String phantom : sortedPhantoms) {
                    emitLog(String.format("[WorldBuilder Info] Rensar spökkaraktär '%s' och ersätter med måloffret '%s'", phantom, confirmedVictimName));
                }

                for (int i = 0; i < indexed.size(); i++) {
                    AgentRecord ar = indexed.get(i);
                    String rel = ar.relationship();
                    String goal = ar.secretGoal();

                    if (ar.name().equalsIgnoreCase(confirmedVictimName)) {
                        boolean hasPhantomInRel = rel != null && sortedPhantoms.stream().anyMatch(p -> Pattern.compile("\\b" + Pattern.quote(p) + "\\b", Pattern.CASE_INSENSITIVE).matcher(ar.relationship()).find());
                        boolean hasPhantomInGoal = goal != null && sortedPhantoms.stream().anyMatch(p -> Pattern.compile("\\b" + Pattern.quote(p) + "\\b", Pattern.CASE_INSENSITIVE).matcher(ar.secretGoal()).find());
                        if (hasPhantomInRel) {
                            rel = "Holds critical authority and influence over the station, making them the prime target of conflicting corporate and crew interests.";
                        }
                        if (hasPhantomInGoal) {
                            goal = "Consolidate control over station assets and safeguard personal financial interests.";
                        }
                    } else {
                        for (String phantom : sortedPhantoms) {
                            Pattern p = Pattern.compile("\\b" + Pattern.quote(phantom) + "\\b", Pattern.CASE_INSENSITIVE);
                            if (rel != null) {
                                rel = p.matcher(rel).replaceAll(Matcher.quoteReplacement(confirmedVictimName));
                            }
                            if (goal != null) {
                                goal = p.matcher(goal).replaceAll(Matcher.quoteReplacement(confirmedVictimName));
                            }
                        }
                    }

                    indexed.set(i, new AgentRecord(ar.id(), ar.name(), ar.role(), ar.appearance(), ar.personality(), rel, goal, ar.currentLocationId(), ar.hasArrived()));
                }
            }

            emitLog(String.format("[WorldBuilder Info] Bekräftad mördare: '%s' (%s) med mål '%s'.",
                    indexed.get(killerIndex).name(), indexed.get(killerIndex).id(), indexed.get(killerIndex).secretGoal()));
        }

        return indexed;
    }

    private boolean isDetectiveRole(String role, String goal) {
        String r = (role != null ? role.toLowerCase(java.util.Locale.ROOT) : "");
        String g = (goal != null ? goal.toLowerCase(java.util.Locale.ROOT) : "");
        return r.contains("detective") || r.contains("investigator") || r.contains("utredare") || r.contains("polis")
                || g.contains("solve the murder") || g.contains("lösa mordet");
    }

    private boolean isKillerGoal(String goal) {
        if (goal == null || goal.isBlank()) return false;
        String g = goal.trim().toLowerCase(java.util.Locale.ROOT);
        if (g.contains("solve the murder") || g.contains("lösa mordet")
                || g.startsWith("frame ") || g.startsWith("sätta dit ")
                || g.startsWith("cover up ") || g.startsWith("dölj ")) {
            return false;
        }
        return g.startsWith("murder ") || g.startsWith("mörda ")
                || g.startsWith("kill ") || g.startsWith("döda ")
                || g.contains("murder") || g.contains("mörda")
                || g.contains("kill") || g.contains("döda")
                || g.contains("assassinate") || g.contains("poison")
                || g.contains("eliminate") || g.contains("slay");
    }

    // DTO-wrappers för rotnycklar i Spring AI JSON-deserialisering
    public record LocationsPayload(List<LocationRecord> locations) {}
    public record ItemsPayload(List<ItemRecord> items) {}
    public record AgentsPayload(List<AgentRecord> agents) {}
}
