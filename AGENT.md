# 🤖 Agent Architecture & Simulation Engine — "Murder, He Coded"

This document provides a detailed technical specification of the autonomous agent architecture, domain models, and LLM-driven simulation engine that powers **Murder, He Coded**.

---

## 🎯 1. Core Domain Models (Data Structures)

Entities are initialized during Phase 1 (World Building) and populated in the active target language (`SV` or `EN`).

### 👤 Agent (Character)
* `id`: String — Unique identifier (e.g., `agent_0`, `agent_1`).
* `name`: String — Display name (e.g., "Dr. Cassius Wren", "Inspector Elara Voss").
* `role`: String — Social / operational profession (e.g., "Chief Geologist", "Detective", "Chairwoman").
* `appearance`: String — Sensory visual description and wardrobe.
* `secretGoal`: String — Character's underlying private agenda or crime motive (e.g., concealing falsified reports, embezzling dividends, stealing gene-seeds).
* `targetAgentId`: String — Specific target of hostility, blackmail, or affection (nullable).
* `relationships`: String / Map — Natural language or structured connections to other characters.
* `alive`: Boolean — Life state (`true` until successfully targeted by a `MURDER` action).
* `detective`: Boolean — Identifies the official investigator.
* `killer`: Boolean — Identifies the designated culprit assigned by the dramaturgist.
* `locationId`: String — Foreign key referencing the character's current `Location`.
* `knowledgeBase`: List<Knowledge> — Accumulated memory bank of observed clues, heard rumors, and inspected items.
* `episodicMemories`: List<String> — Chronological ledger of raw first-person observations and actions.
* `hasInspectedCrimeScene`: Boolean — Tracks if the detective has visited the initial murder location.

### 📍 Location (Place / Room)
* `id`: String — Unique room identifier (e.g., `room_0`, `room_6`).
* `name`: String — Atmospheric room title (e.g., "The Mining Archive", "The Grand Rotunda").
* `description`: String — Sensory environmental backdrop for prose and decision prompting.
* `connectedLocationIds`: List<String> — Spatial adjacency graph determining valid `MOVE` paths.
* `environmentalTraces`: List<String> — Dynamic clues, disturbances, blood droplets, or notes left in the space.

### 💍 Item (Object & Clue)
* `id`: String — Unique item identifier (e.g., `item_0`, `item_25`).
* `name`: String — Atmospheric title (e.g., "Geological Hammer", "Small Brass Compass", "Notebook of Margaret Ashworth").
* `type`: Enum — `WEAPON`, `VALUABLE`, `CLUE`, `NOTEBOOK`.
* `locationId`: String — Room where the item currently rests (null if carried).
* `carrierId`: String — Agent ID currently possessing the item (null if resting in room).
* `isClue`: Boolean — Flags whether the item holds evidentiary significance.
* `isConcealed`: Boolean — Flags whether the item requires an explicit `SEARCH` action to uncover.

### ⚡ GlobalEvent (Scheduled Dramaturgic Incident)
* `id`: String — Unique identifier (e.g., `event_power_loss`, `event_body_alarm`).
* `title`: String — Event headline (e.g., "Alarm: Body Discovered in The Mining Archive!").
* `description`: String — Environmental narrative guidance affecting agent behavior and room states.
* `triggerStep`: Integer — Simulation time step when the event activates.
* `durationSteps`: Integer — How many steps the event exerts influence.
* `locationId`: String — Optional focal room (or null for station-wide incidents).
* `eventType`: Enum — `ATMOSPHERIC`, `ISOLATION`, `ALARM`, `RESOLUTION`.

---

## 🧠 2. Dynamic Mechanics & Algorithmic Safeguards

### 📈 Dramaturgy Director & Pacing Profiles (`DramaturgyDirectorService`)
Narrative pacing is dynamically steered across the simulation lifecycle:
* **Pacing Profiles:** Supports customizable narrative styles (*Classic Golden Age*, *Hardboiled*, *Psychological Thriller*, *Cozy Mystery*) defining incident frequency, aggression ramp-up, and clue distribution.
* **Murder Pacing Windows:** Lethal actions (`MURDER`) are locked during the opening 15–20% of steps (exposition phase) to allow characters to interact, establish alibis, and generate subplots before tragedy strikes.
* **Murderer Urgency Stages:** 
  1. `PLANNING`: Blend in socially, study room layouts, avoid drawing attention.
  2. `ESCALATING`: Stalk the designated victim, acquire weapons, locate opportunities for isolation.
  3. `DESPERATE_STRIKE_WINDOW`: Under strict conditions, execute the lethal attack.
  4. `POST_CRIME`: Immediately feign innocence, discard weapons, establish alibis, and redirect suspicion.

### 🔒 The Isolated Murder Constraint
To prevent immersion-breaking murders in crowded public areas:
* An agent can only execute `MURDER` if the room population is **strictly $\le 2$** (the killer and the victim).
* If bystanders are present in the room, the engine rejects `MURDER` actions during validation and commands the killer to use `MOVE`, `TALK`, or environment manipulation to isolate the target.

### 📓 Notebook Inheritance & Identity Attribution
When an agent is killed, their accumulated memories must not vanish:
1. The engine automatically spawns a physical `NOTEBOOK` item (e.g., `Notebook of Margaret Ashworth`) in the room where the death occurred.
2. The deceased agent's `knowledgeBase` is copied into the notebook.
3. **Identity Preservation Safeguard:** When another character finds and reads the notebook, all acquired memories are prepended with an explicit source prefix:
   ```
   [From Notebook of <Victim Name>]: <Original Memory Information>
   ```
   This prevents identity contamination, ensuring characters never adopt the deceased's first-person perspective or falsely confess to their past actions.

### 🧹 Knowledge Base Deduplication
To prevent agents (particularly detectives) from stalling by repeatedly recording the same rumor:
* Every incoming fact is compared against existing knowledge using exact and normalized string matching.
* Duplicated information is discarded before reaching the knowledge base, forcing the detective to move between rooms to discover new clues.

### 🔇 Anti-Echo Dialogue Guard
To avoid repetitive whisper loops (where multiple characters echo the same phrase back and forth):
* The engine tracks recently uttered quotes per character.
* Incoming dialogue proposals are screened against recent history using quote matching and n-gram overlap.
* If a duplicate dialogue is proposed, the engine rejects or transforms it into a fresh inquiry.

### 🧭 Detective Patrol & Investigation Logic
* **Initial Crime Scene Visit:** Upon arriving after the body discovery alarm, the detective prioritizes inspecting the crime scene room (`hasInspectedCrimeScene = false`).
* **Station-Wide Patrol:** As soon as the crime scene has been examined once (`hasInspectedCrimeScene = true`), the "rush-to-scene" directive is permanently disabled, allowing the detective to systematically patrol other locations and interview suspects.

---

## 🕒 3. Per-Agent Autonomous Decision Loop

Rather than relying on a single monolithic Game Master prompt, **Murder, He Coded** executes a decentralized, per-agent autonomous loop powered by `prompts/agent/agent-decision.st`.

```
                    ┌────────────────────────────┐
                    │  Current Time Step Begins  │
                    └─────────────┬──────────────┘
                                  │
                   ┌──────────────▼──────────────┐
                   │  Filter Active Room Groups  │
                   └──────────────┬──────────────┘
                                  │
         ┌────────────────────────┴────────────────────────┐
         │ For each Living Agent in Room:                  │
         │ 1. Compile Room Context (Occupants, Items)     │
         │ 2. Inject Secret Goal & Target                  │
         │ 3. Inject Dramaturgic Urgency & Cooldowns       │
         │ 4. Render `agent-decision.st` via Spring AI     │
         │ 5. Parse Structured Action JSON                 │
         │ 6. Validate & Mutate World State                │
         └────────────────────────┬────────────────────────┘
                                  │
                    ┌─────────────▼──────────────┐
                    │  Commit Snapshot to State  │
                    │  Append to `eventlog.txt`  │
                    └────────────────────────────┘
```

### 📥 Agent Decision Input Structure
Each agent's decision prompt includes:
* **Persona:** Name, role, appearance, psychological secret goal, and assigned target.
* **Spatial Reality:** Current room name, sensory description, and list of all present characters.
* **Visible Items & Clues:** Items resting openly in the room or held by the agent.
* **Knowledge & Memories:** Agent's accumulated facts and recent observations.
* **Recent Actions & Cooldowns:** History of recent choices (`=== YOUR RECENT ACTIONS ===`) with cooldown rules barring consecutive passive actions (`OBSERVE`, `REFLECT`).
* **Dramaturgic Directive:** Pacing instructions calculated by `DramaturgyDirectorService` for this specific step.

### 📤 Agent Decision Output Schema
The LLM returns an unambiguous JSON object:

```json
{
  "action": "TALK",
  "target": "agent_5",
  "detail": "Dr. Wren approaches Felix Drummond to ask about the quarterly mining dividends.",
  "dialogue": "Felix, have you had an opportunity to review the latest yield figures?",
  "internalThought": "I need to ensure Drummond suspects the board rather than looking at my assay data."
}
```

### 🎯 Supported Action Types
* `TALK` — Converse with an occupant, potentially transferring knowledge or planting false leads.
* `MOVE` — Travel to an adjacent connected room (`targetLocationId`).
* `SEARCH` — Thoroughly examine the current room to uncover hidden items or clues.
* `MANIPULATE_ITEM` — Pick up, conceal, or discard an object.
* `ALTER_ENVIRONMENT` — Leave a note, smudge a mark, or disturb soil/furnishings.
* `OBSERVE` / `REFLECT` — Quiet sensory intake (strictly subject to action cooldowns).
* `MURDER` — Lethal strike against a designated victim (enforces isolation rules).
* `ACCUSE` — Public accusation triggering case resolution.

---

## 🎬 4. Editorial Framing & Prose Authoring Pipeline

The transition from raw discrete simulation events to a cohesive literary novel follows a two-tier pipeline:

### 1. Editorial Curation (`EditorService`)
The editor reviews the raw sequence of discrete events and assembles them into framed `Scene` objects:
* **Location Boundary Constraint:** Every scene is strictly confined to a single room. A change of location immediately triggers a scene break.
* **Dramatic Hooks & Buttons:** Automatically identifies the opening hook (`firstEvent`) and climactic punchline (`lastEvent`) for each scene.
* **Narrative Importance & Suspense Scoring:** Evaluates high-impact turning points (murders, discoveries, accusations) and assigns numerical scores (0.0 to 10.0).
* **Selection for the Final Cut:** Tags scenes as `ESSENTIAL`, `SUPPORTING`, `ATMOSPHERIC`, or `TRANSITIONAL`, allowing dispensable filler to be excluded from the curated edition (`isSelected = true`).

### 2. Prose Authoring (`ProseService`)
* Iterates through curated scenes and pens full prose chapters using `prompts/prose/scene-prose.st`.
* Adopts the chosen authorial voice (e.g., Agatha Christie, Arthur Conan Doyle, Raymond Chandler, Edgar Allan Poe).
* Translates raw internal IDs (`agent_0`, `room_6`, `item_25`) into rich names and atmospheric prose without leaking system variables.
* Generates a climactic concluding chapter via `prompts/prose/resolution-prose.st` covering the grand reveal, suspect confrontation, and final resolution.

---

## 🏆 5. Case Resolution & The Grand Reveal

At the conclusion of the simulation or upon an `ACCUSE` action:
1. The detective compiles the collected knowledge chain (murderer identity, motive, weapon, crime scene location).
2. The dramaturgist convenes all living characters in the central gathering space (e.g. *The Grand Rotunda*).
3. The prose engine pens a dedicated multi-thousand-word finale chapter detailing the detective's deductions, dismantling each suspect's false alibi, and extracting the killer's confession.
