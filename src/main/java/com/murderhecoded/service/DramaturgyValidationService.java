package com.murderhecoded.service;

import com.murderhecoded.domain.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
@SuppressWarnings("PMD")
public class DramaturgyValidationService {

    public record DramaturgyReport(
        boolean success,
        String detectiveId,
        String murdererId,
        String victimId,
        List<String> itemsAdded,
        List<String> logs
    ) {
        public DramaturgyReport(boolean success, List<String> logs) {
            this(success, null, null, null, Collections.emptyList(), logs);
        }
    }

    private static final Pattern ITEM_ID_PATTERN = Pattern.compile("(?i)\\bitem_([a-zA-Z0-9_]+)\\b");
    
    private static final Pattern ACTION_ITEM_EN_PATTERN = Pattern.compile(
        "(?i)\\b(?:steal|find|hide|acquire|retrieve|recover|destroy|take|secure|plant|get)\\s+(?:the\\s+|a\\s+|an\\s+)?([a-zA-Z0-9\\s]{3,35}?)(?=\\s+(?:from|in|to|at|before|and|\\(|,|$)|$|\\.)"
    );

    private static final Set<String> ABSTRACT_EXCLUSIONS = Set.of(
        "evidence", "truth", "secret", "secrets", "love", "trust", "identity", "information",
        "power", "revenge", "justice", "control", "survival", "respect", "honor", "peace"
    );

    public DramaturgyReport validateAndEnforceDramaturgy(
        List<Agent> agents,
        List<Location> locations,
        List<Item> items,
        String title,
        String premise
    ) {
        List<String> logs = new ArrayList<>();
        List<String> itemsAdded = new ArrayList<>();

        logs.add("=== [Dramaturgy Validation & Consistency Check] ===");

        if (agents == null || agents.isEmpty()) {
            logs.add("ERROR: No agents available to validate.");
            return new DramaturgyReport(false, null, null, null, itemsAdded, logs);
        }
        if (locations == null || locations.isEmpty()) {
            logs.add("ERROR: No places available to validate.");
            return new DramaturgyReport(false, null, null, null, itemsAdded, logs);
        }
        if (items == null) {
            items = new ArrayList<>();
        }

        Map<String, String> originalGoals = new HashMap<>();
        for (Agent a : agents) {
            if (a.getSecretGoal() != null) originalGoals.put(a.getId(), a.getSecretGoal());
        }

        // Guarantee all agents and items have valid non-null location IDs
        String defaultPlaceId = locations.get(0).getId() != null ? locations.get(0).getId() : "room_0";
        Set<String> validPlaceIds = locations.stream().map(Location::getId).filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        for (Agent a : agents) {
            if (a.getCurrentLocationId() == null || a.getCurrentLocationId().trim().isEmpty() || !validPlaceIds.contains(a.getCurrentLocationId())) {
                a.setCurrentLocationId(defaultPlaceId);
                logs.add(String.format("Assigned location %s to agent %s (%s)", defaultPlaceId, a.getName(), a.getId()));
            }
        }
        for (Item itm : items) {
            if (itm.getCurrentLocationId() == null || itm.getCurrentLocationId().trim().isEmpty()) {
                itm.setCurrentLocationId(defaultPlaceId);
            }
        }

        // ==========================================
        // 1. Enforce Detective (agent_0)
        // ==========================================
        Agent detective = null;
        for (Agent a : agents) {
            if ("agent_0".equals(a.getId()) || (a.getRole() != null && a.getRole().toLowerCase().contains("detective"))) {
                detective = a;
                break;
            }
        }
        if (detective == null) {
            detective = agents.get(0);
        }

        detective.setRole("Detective");
        detective.setSecretGoal("Solve the murder and identify the killer.");
        detective.setHasArrived(false); // Arrives after first discovery
        detective.setHasMurderIntent(false);
        detective.setTargetVictimId(null);
        if (detective.getKnowledgeBase() == null) detective.setKnowledgeBase(new ArrayList<>());
        logs.add(String.format("Detective verified: %s (%s) [hasArrived=false, hasMurderIntent=false]", detective.getName(), detective.getId()));

        // Ensure no other agent claims to be Detective
        for (Agent a : agents) {
            if (!a.getId().equals(detective.getId())) {
                if (a.getRole() != null && (a.getRole().toLowerCase().contains("detective") || a.getRole().toLowerCase().contains("detektiv"))) {
                    a.setRole("Investigative Guest");
                }
                if (a.getSecretGoal() != null && (a.getSecretGoal().toLowerCase().contains("solve the murder") || a.getSecretGoal().toLowerCase().contains("lösa mordet"))) {
                    a.setSecretGoal("Observe the suspects and survive the night.");
                }
            }
        }

        // ==========================================
        // 2. Identify and Enforce Murderer (NOT Detective)
        // ==========================================
        Agent murderer = null;

        // Check if an existing non-detective agent already has murder intent
        for (Agent a : agents) {
            if (a.getId().equals(detective.getId())) continue;
            if (a.isHasMurderIntent()) {
                murderer = a;
                break;
            }
        }

        // If not, check if an agent has a goal or role mentioning murder/kill
        if (murderer == null) {
            for (Agent a : agents) {
                if (a.getId().equals(detective.getId())) continue;
                String g = a.getSecretGoal() != null ? a.getSecretGoal().toLowerCase() : "";
                String r = a.getRole() != null ? a.getRole().toLowerCase() : "";
                if (g.contains("murder") || g.contains("mörda") || g.contains("kill") || r.contains("murderer") || r.contains("mördare")) {
                    murderer = a;
                    break;
                }
            }
        }

        // Fallback: Pick the second agent (or any non-detective)
        if (murderer == null) {
            for (Agent a : agents) {
                if (!a.getId().equals(detective.getId())) {
                    murderer = a;
                    break;
                }
            }
        }

        // Set Murderer flags
        if (murderer != null) {
            murderer.setHasMurderIntent(true);
            // Clear murder intent from everyone else
            for (Agent a : agents) {
                if (!a.getId().equals(murderer.getId())) {
                    a.setHasMurderIntent(false);
                    a.setTargetVictimId(null);
                }
            }
            logs.add(String.format("Murderer identified: %s (%s)", murderer.getName(), murderer.getId()));
        } else {
            logs.add("CRITICAL: Failed to allocate murderer among agents.");
            return new DramaturgyReport(false, detective.getId(), null, null, itemsAdded, logs);
        }

        // ==========================================
        // 3. Identify and Enforce Target Victim
        // ==========================================
        Agent victim = null;

        // Check if murderer already has targetVictimId pointing to an existing non-detective agent
        if (murderer.getTargetVictimId() != null) {
            for (Agent a : agents) {
                if (a.getId().equals(murderer.getTargetVictimId()) && !a.getId().equals(detective.getId()) && !a.getId().equals(murderer.getId())) {
                    victim = a;
                    break;
                }
            }
        }

        // Check if murderer's secret goal mentions a specific agent's name
        if (victim == null && murderer.getSecretGoal() != null) {
            String goalLower = murderer.getSecretGoal().toLowerCase();
            for (Agent a : agents) {
                if (!a.getId().equals(detective.getId()) && !a.getId().equals(murderer.getId())) {
                    if (goalLower.contains(a.getName().toLowerCase())) {
                        victim = a;
                        break;
                    }
                }
            }
        }

        // Fallback: Pick first eligible non-detective, non-murderer agent
        if (victim == null) {
            for (Agent a : agents) {
                if (!a.getId().equals(detective.getId()) && !a.getId().equals(murderer.getId())) {
                    victim = a;
                    break;
                }
            }
        }

        if (victim != null) {
            murderer.setTargetVictimId(victim.getId());
            logs.add(String.format("Target victim confirmed: %s (%s)", victim.getName(), victim.getId()));

            // Sanitize phantom victim references across all agents' relationships and secret goals
            Set<String> castNames = agents.stream().map(Agent::getName).filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet());
            Set<String> validFirstLast = new HashSet<>();
            for (String cn : castNames) {
                for (String part : cn.split("\\s+")) {
                    if (part.length() > 2) validFirstLast.add(part.toLowerCase(Locale.ROOT));
                }
            }

            Set<String> phantomNames = new HashSet<>();

            // 0. Killer's goal target if not in cast
            if (murderer.getSecretGoal() != null) {
                Matcher goalTargetMatcher = Pattern.compile(
                        "(?i:\\b(?:murder|kill|assassinate|mörda|döda)\\s+(?:the\\s+)?(?:victim\\s+)?)(.*?)(?:\\s+(?:because|to\\b|för\\s+att|due\\s+to|in\\s+order|so\\s+as)|$)"
                ).matcher(murderer.getSecretGoal());
                if (goalTargetMatcher.find()) {
                    String cand = goalTargetMatcher.group(1).trim();
                    if (!cand.isBlank() && castNames.stream().noneMatch(cn -> cn.toLowerCase(Locale.ROOT).contains(cand.toLowerCase(Locale.ROOT)))) {
                        phantomNames.add(cand);
                        String stripped = cand.replaceFirst("^(?i)(?:director|captain|chief\\s+engineer|dr\\.?|doctor|lord|lady|inspector|detective|officer)\\s+", "").trim();
                        if (!stripped.isBlank() && !stripped.equalsIgnoreCase(cand)) {
                            phantomNames.add(stripped);
                        }
                    }
                }
            }

            // 1. Scan for explicit "victim <Name>" without (?i) on the name part
            Pattern explicitVictimPattern = Pattern.compile("(?i:\\b(?:the\\s+)?(?:victim|offret|den\\s+mördade)\\s+)([A-Z][a-z]+(?:\\s+[A-Z][a-z]+)*)");
            for (Agent a : agents) {
                for (String text : List.of(a.getRelationship() != null ? a.getRelationship() : "", a.getSecretGoal() != null ? a.getSecretGoal() : "")) {
                    Matcher m = explicitVictimPattern.matcher(text);
                    while (m.find()) {
                        String matched = m.group(1).trim();
                        boolean isReal = castNames.stream().anyMatch(cn -> cn.toLowerCase(Locale.ROOT).contains(matched.toLowerCase(Locale.ROOT)));
                        if (!isReal && matched.length() > 2) {
                            phantomNames.add(matched);
                        }
                    }
                }
            }

            // 2. Scan for titled names like "Director Elara Voss", "Captain Ilya Rostov"
            Pattern titledPattern = Pattern.compile("\\b(Director|Chief\\s+Engineer|Captain|Dr\\.?|Doctor|Lord|Lady|Inspector|Detective|Officer|Commander|Professor|Prof\\.?|Minister|President|CEO|Sir|Madam|Mr\\.?|Mrs\\.?|Ms\\.?)\\s+([A-Z][a-z]+(?:\\s+[A-Z][a-z]+)*)\\b");
            for (Agent a : agents) {
                for (String text : List.of(a.getRelationship() != null ? a.getRelationship() : "", a.getSecretGoal() != null ? a.getSecretGoal() : "")) {
                    Matcher m = titledPattern.matcher(text);
                    while (m.find()) {
                        String fullTitled = m.group(0).trim();
                        String nameOnly = m.group(2).trim();
                        boolean isReal = castNames.stream().anyMatch(cn -> cn.toLowerCase(Locale.ROOT).contains(nameOnly.toLowerCase(Locale.ROOT)));
                        if (!isReal) {
                            phantomNames.add(fullTitled);
                            phantomNames.add(nameOnly);
                            String titleOnly = m.group(1).trim();
                            String[] nameParts = nameOnly.split("\\s+");
                            if (nameParts.length > 1) {
                                String lastName = nameParts[nameParts.length - 1];
                                phantomNames.add(titleOnly + " " + lastName);
                                if (!validFirstLast.contains(lastName.toLowerCase(Locale.ROOT))) {
                                    phantomNames.add(lastName);
                                }
                            }
                        }
                    }
                }
            }

            if (!phantomNames.isEmpty()) {
                List<String> sortedPhantoms = phantomNames.stream()
                        .sorted((a, b) -> Integer.compare(b.length(), a.length()))
                        .toList();

                for (String phantom : sortedPhantoms) {
                    logs.add(String.format("Sanitized phantom character '%s' in agent relationships and goals, replacing with confirmed victim '%s'", phantom, victim.getName()));
                }

                for (Agent a : agents) {
                    String rel = a.getRelationship();
                    String goal = a.getSecretGoal();

                    if (a.getId().equals(victim.getId())) {
                        boolean hasPhantomInRel = rel != null && sortedPhantoms.stream().anyMatch(p -> Pattern.compile("\\b" + Pattern.quote(p) + "\\b", Pattern.CASE_INSENSITIVE).matcher(a.getRelationship()).find());
                        boolean hasPhantomInGoal = goal != null && sortedPhantoms.stream().anyMatch(p -> Pattern.compile("\\b" + Pattern.quote(p) + "\\b", Pattern.CASE_INSENSITIVE).matcher(a.getSecretGoal()).find());
                        if (hasPhantomInRel) {
                            a.setRelationship("Holds critical authority and influence over the station, making them the prime target of conflicting corporate and crew interests.");
                        }
                        if (hasPhantomInGoal) {
                            a.setSecretGoal("Consolidate control over station assets and safeguard personal financial interests.");
                        }
                    } else {
                        for (String phantom : sortedPhantoms) {
                            Pattern p = Pattern.compile("\\b" + Pattern.quote(phantom) + "\\b", Pattern.CASE_INSENSITIVE);
                            if (rel != null) {
                                a.setRelationship(p.matcher(a.getRelationship()).replaceAll(Matcher.quoteReplacement(victim.getName())));
                            }
                            if (goal != null) {
                                a.setSecretGoal(p.matcher(a.getSecretGoal()).replaceAll(Matcher.quoteReplacement(victim.getName())));
                            }
                        }
                    }
                }
            }

            // Ensure distinct starting locations for murderer and victim to avoid opening loop
            if (locations != null && locations.size() > 1) {
                String murdererLoc = murderer.getCurrentLocationId();
                String victimLoc = victim.getCurrentLocationId();
                if (murdererLoc != null && Objects.equals(murdererLoc, victimLoc)) {
                    Location alternativeLoc = locations.stream()
                            .filter(l -> l.getId() != null && !Objects.equals(l.getId(), victimLoc))
                            .findFirst()
                            .orElse(null);
                    if (alternativeLoc != null) {
                        murderer.setCurrentLocationId(alternativeLoc.getId());
                        logs.add(String.format("Separated murderer %s from victim %s at start (step 0): relocated murderer to %s (%s)",
                                murderer.getName(), victim.getName(), alternativeLoc.getName(), alternativeLoc.getId()));
                    }
                }
            }
        } else {
            logs.add("WARNING: No valid victim could be assigned for the murderer.");
        }

        // ==========================================
        // 4. Enforce Murderer's Secret Goal Explicitly
        // ==========================================
        String currentGoal = murderer.getSecretGoal();
        String victimName = victim != null ? victim.getName() : "the target victim";
        
        boolean goalHasMurder = currentGoal != null && (
            currentGoal.toLowerCase().startsWith("murder " + victimName.toLowerCase()) ||
            currentGoal.toLowerCase().startsWith("mörda " + victimName.toLowerCase()) ||
            (currentGoal.toLowerCase().contains("murder") && currentGoal.toLowerCase().contains(victimName.toLowerCase())) ||
            (currentGoal.toLowerCase().contains("mörda") && currentGoal.toLowerCase().contains(victimName.toLowerCase()))
        );

        if (!goalHasMurder) {
            String motive;
            if (currentGoal != null && !currentGoal.isBlank() && !currentGoal.equalsIgnoreCase("None")) {
                boolean isTheft = currentGoal.toLowerCase().contains("steal") || currentGoal.toLowerCase().contains("stjäl") || currentGoal.toLowerCase().contains("stjäla");
                if (isTheft) {
                    String clean = currentGoal.replaceFirst("(?i)^(to\\s+|steal\\s+|acquire\\s+|take\\s+)", "");
                    motive = "to steal " + clean.replaceFirst("\\.$", "") + " and permanently silence the main witness.";
                } else {
                    motive = "to accomplish their agenda to " + currentGoal.replaceFirst("\\.$", "") + " and eliminate the obstacle.";
                }
            } else {
                motive = "because they discovered the secret plot and threaten total exposure.";
            }

            String updatedGoal = String.format("Murder %s %s", victimName, motive);
            murderer.setSecretGoal(updatedGoal);
            logs.add("Updated murderer's secret goal to explicit murder objective: \"" + updatedGoal + "\"");
        }

        // ==========================================
        // 5. Item Consistency Check for All Agent Goals
        // ==========================================
        Map<String, Item> itemsById = new HashMap<>();
        for (Item i : items) {
            if (i.getId() != null) itemsById.put(i.getId().toLowerCase(), i);
        }

        for (Agent a : agents) {
            List<String> goalsToScan = new ArrayList<>();
            if (a.getSecretGoal() != null && !a.getSecretGoal().isBlank()) {
                goalsToScan.add(a.getSecretGoal());
            }
            if (originalGoals.containsKey(a.getId()) && !goalsToScan.contains(originalGoals.get(a.getId()))) {
                goalsToScan.add(originalGoals.get(a.getId()));
            }

            for (String goal : goalsToScan) {
                // 5a. Check for explicit item_X ID references in goal
                Matcher idMatcher = ITEM_ID_PATTERN.matcher(goal);
                while (idMatcher.find()) {
                    String fullId = "item_" + idMatcher.group(1);
                    if (!itemsById.containsKey(fullId.toLowerCase())) {
                        String itemName = extractNameBeforeId(goal, fullId);
                        if (itemName == null || itemName.isBlank()) {
                            itemName = "Valuable Artefact (" + fullId + ")";
                        }
                        
                        Item newItem = new Item();
                        newItem.setId(fullId);
                        newItem.setName(capitalize(itemName));
                        newItem.setType(determineItemType(itemName));
                        newItem.setCurrentLocationId(choosePlacement(locations, a));
                        newItem.setHidden(false);
                        
                        items.add(newItem);
                        itemsById.put(fullId.toLowerCase(), newItem);
                        itemsAdded.add(newItem.getName() + " (" + fullId + ")");
                        logs.add(String.format("Added missing item referenced by %s's goal: %s [%s] in %s", 
                            a.getName(), newItem.getName(), newItem.getId(), newItem.getCurrentLocationId()));
                    }
                }

                // 5b. Check for action verbs targeting named objects without ID
                Matcher actionMatcher = ACTION_ITEM_EN_PATTERN.matcher(goal);
                if (actionMatcher.find()) {
                    String candidate = actionMatcher.group(1).trim();
                    if (candidate.length() >= 3 && !ABSTRACT_EXCLUSIONS.contains(candidate.toLowerCase()) && !candidate.toLowerCase().contains("victim")) {
                        boolean exists = false;
                        for (Item itm : items) {
                            if (itm.getName() != null && (
                                itm.getName().toLowerCase().contains(candidate.toLowerCase()) ||
                                candidate.toLowerCase().contains(itm.getName().toLowerCase())
                            )) {
                                exists = true;
                                break;
                            }
                        }

                        if (!exists) {
                            String newId = "item_" + nextAvailableItemIndex(items);
                            Item newItem = new Item();
                            newItem.setId(newId);
                            newItem.setName(capitalize(candidate));
                            newItem.setType(determineItemType(candidate));
                            newItem.setCurrentLocationId(choosePlacement(locations, a));
                            newItem.setHidden(false);
                            
                            items.add(newItem);
                            itemsById.put(newId.toLowerCase(), newItem);
                            itemsAdded.add(newItem.getName() + " (" + newId + ")");
                            
                            a.setSecretGoal(goal + " (" + newId + ")");
                            logs.add(String.format("Created item for %s's goal object: %s [%s] in %s",
                                a.getName(), newItem.getName(), newId, newItem.getCurrentLocationId()));
                        }
                    }
                }
            }
        }

        // ==========================================
        // 6. Guarantee Weapon Availability
        // ==========================================
        long weaponCount = items.stream().filter(i -> i.getType() == ItemType.WEAPON).count();
        while (weaponCount < 2) {
            String weaponName = chooseSettingWeapon(title, premise);
            String weaponId = "item_" + nextAvailableItemIndex(items);
            
            Item weapon = new Item();
            weapon.setId(weaponId);
            weapon.setName(weaponName);
            weapon.setType(ItemType.WEAPON);
            int pIndex = (int) (weaponCount % Math.max(1, locations.size()));
            String weaponLoc = (!locations.isEmpty() && locations.get(pIndex) != null && locations.get(pIndex).getId() != null)
                    ? locations.get(pIndex).getId()
                    : defaultPlaceId;
            weapon.setCurrentLocationId(weaponLoc);
            weapon.setHidden(false);
            
            items.add(weapon);
            itemsById.put(weaponId.toLowerCase(), weapon);
            itemsAdded.add(weapon.getName() + " (" + weaponId + ")");
            logs.add("Guaranteed weapon in setting: " + weapon.getName() + " [" + weaponId + "] in " + weapon.getCurrentLocationId());
            weaponCount++;
        }

        // ==========================================
        // 7. Enforce Movability of Stationary Machinery / Fixtures
        // ==========================================
        for (Item itm : items) {
            if (isStationaryItem(itm.getName())) {
                itm.setMovable(false);
                if (itm.getCurrentLocationId() != null && itm.getCurrentLocationId().startsWith("agent_")) {
                    String fallbackPlace = locations.isEmpty() ? "room_0" : locations.get(0).getId();
                    itm.setCurrentLocationId(fallbackPlace);
                    logs.add("Relocated stationary machine " + itm.getName() + " from agent inventory to " + fallbackPlace);
                }
            }
        }

        logs.add(String.format("Dramaturgy validation complete. Agents: %d, Places: %d, Items: %d (New items added: %d)",
            agents.size(), locations.size(), items.size(), itemsAdded.size()));

        return new DramaturgyReport(
            true,
            detective.getId(),
            murderer.getId(),
            victim != null ? victim.getId() : null,
            itemsAdded,
            logs
        );
    }

    private String extractNameBeforeId(String text, String itemId) {
        int idx = text.indexOf(itemId);
        if (idx <= 0) return null;
        String before = text.substring(0, idx).trim();
        if (before.endsWith("(")) {
            before = before.substring(0, before.length() - 1).trim();
        }
        String[] tokens = before.split("(?i)\\b(?:steal|find|hide|acquire|retrieve|recover|the|a|an|stjäl|hitta|göm|den|det|en|ett)\\s+");
        if (tokens.length > 1) {
            String candidate = tokens[tokens.length - 1].trim();
            if (!candidate.isBlank() && candidate.length() < 30) {
                return candidate;
            }
        }
        return null;
    }

    public static boolean isStationaryItem(String name) {
        if (name == null || name.isBlank()) return false;
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.contains("machine") || lower.contains("machinery") || lower.contains("console") ||
               lower.contains("terminal") || lower.contains("furnace") || lower.contains("generator") ||
               lower.contains("centrifuge") || lower.contains("press") || lower.contains("reactor") ||
               lower.contains("panel") || lower.contains("airlock") || lower.contains("transformer") ||
               lower.contains("boiler") || lower.contains("vat") || lower.contains("incinerator") ||
               lower.contains("turbine") || lower.contains("pump") || lower.contains("compressor") ||
               lower.contains("engine") || lower.contains("drill") || lower.contains("maskin") ||
               lower.contains("reaktor") || lower.contains("ugn") || lower.contains("panna");
    }

    private ItemType determineItemType(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.contains("gun") || lower.contains("knife") || lower.contains("dagger") || lower.contains("poison") ||
            lower.contains("weapon") || lower.contains("blade") || lower.contains("cutter") || lower.contains("wrench") ||
            lower.contains("spanner") || lower.contains("scalpel") || lower.contains("torch") || lower.contains("hammer") ||
            lower.contains("incinerator") || lower.contains("centrifuge") || lower.contains("press") ||
            lower.contains("vapen") || lower.contains("kniv") || lower.contains("gift") || lower.contains("pistol")) {
            return ItemType.WEAPON;
        }
        if (lower.contains("data") || lower.contains("report") || lower.contains("slate") || lower.contains("file") ||
            lower.contains("letter") || lower.contains("note") || lower.contains("diary") || lower.contains("record") ||
            lower.contains("bevis") || lower.contains("rapport") || lower.contains("brev") || lower.contains("anteckning")) {
            return ItemType.CLUE;
        }
        return ItemType.VALUABLE;
    }

    private String choosePlacement(List<Location> locations, Agent agent) {
        if (locations == null || locations.isEmpty()) return "room_0";
        for (Location p : locations) {
            if (p != null && p.getId() != null && (agent == null || !Objects.equals(p.getId(), agent.getCurrentLocationId()))) {
                return p.getId();
            }
        }
        return locations.get(0) != null && locations.get(0).getId() != null ? locations.get(0).getId() : "room_0";
    }

    private int nextAvailableItemIndex(List<Item> items) {
        int max = 0;
        for (Item i : items) {
            if (i.getId() != null && i.getId().startsWith("item_")) {
                try {
                    int val = Integer.parseInt(i.getId().substring(5));
                    if (val > max) max = val;
                } catch (NumberFormatException ignored) {}
            }
        }
        return max + 1;
    }

    private String chooseSettingWeapon(String title, String premise) {
        String combined = ((title != null ? title : "") + " " + (premise != null ? premise : "")).toLowerCase(Locale.ROOT);
        if (combined.contains("space") || combined.contains("mars") || combined.contains("station") || combined.contains("orbit")) {
            return "Modified Oxygen Cutoff Valve";
        } else if (combined.contains("ship") || combined.contains("boat") || combined.contains("sea") || combined.contains("båt")) {
            return "Heavy Steel Flare Gun";
        } else if (combined.contains("train") || combined.contains("express") || combined.contains("tåg")) {
            return "Heavy Brass Fire Poker";
        }
        return "Ornate Hunting Dagger";
    }

    private String capitalize(String str) {
        if (str == null || str.isBlank()) return "Item";
        String[] words = str.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.length() > 0) {
                sb.append(Character.toUpperCase(w.charAt(0)));
                if (w.length() > 1) {
                    sb.append(w.substring(1).toLowerCase());
                }
                sb.append(" ");
            }
        }
        return sb.toString().trim();
    }
}
