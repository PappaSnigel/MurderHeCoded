# 🎨 User Interface & System Features — "Murder, He Coded"

This document details the multi-threaded JavaFX interface, real-time visualization features, interactive timeline controls, and generation pipeline for **Murder, He Coded**.

---

## 🗂️ 1. Main Application Tabs

The primary workspace is organized into four top-level tabs:

```
[ 🎮 Simulation ]  |  [ 🎬 Scenes ]  |  [ 📜 Event Log ]  |  [ 📖 Story ]
```

---

### 🎮 1. Simulation Tab
The central command dashboard for world generation, agent progression, and real-time state exploration:
* **Generation Dashboard:**
  * **World Status Badge:** Displays current phase state (`Ready`, `Generating...`, `Resumed`, `Completed`).
  * **Simulation Progress Bar:** Real-time indicator comparing current step against target steps and remaining hours.
  * **Prose Queue Progress Bar:** Visual ratio of completed written chapters versus total curated scenes.
  * **Direct Action Toolbar:** Four independent phase controls:
    * **🌍 1. Build World** (and **🔄 Rebuild World**)
    * **▶ 2. Start Simulation** (with **⏯ Resume** and pinned **⏹ Stop**)
    * **🎬 3. Generate Scenes** (and **🔄 Regenerate Scenes**)
    * **✍️ 4. Generate Prose** (and **🔄 Regenerate Prose**)
    * **💾 Export Story (TXT & PDF)**
* **Entity & World Inspection:**
  * **Characters (Agents):** Card grid and tree view inspecting dynamic goals, alive status, held items, known clues, and a dedicated **Activity Log & Movements** card tracking every action, dialogue, search, and crime chronologically.
  * **Places (Locations):** Complete inventory per room, tracking occupants, items, environmental traces, and an **Activity Log & History** card showing all historical events that transpired in that space.
  * **Items & Clues:** Complete inspection of properties, clue markers, plus an interactive **Chain of Custody & Activity Log** tracking initial placement, pickups, drops, concealments, and weapon usage.
  * **Event-Sourced Timeline Scrubber:** Instantaneous $O(1)$ scrubbing through past time steps using lightweight event sourcing and in-memory index projections, eliminating lag and reducing project file sizes from ~500 MB down to ~200 KB.
* **Dramaturgy Curve & Events Tree:**
  * Real-time line chart mapping tension, conflict, and incident pacing over time.
  * Hierarchical tree view categorizing events by time step and location.

---

### 🎬 2. Scenes Tab
Positioned directly after the Event Log, this tab bridges raw simulation events and literary prose:
* **Curated Scene Cards:** Presented in chronological order with:
  * **Header:** Scene number, primary location tag, step range, and formatted clock span.
  * **Narrative Badges:**
    * Category: `ESSENTIAL`, `SUPPORTING`, `ATMOSPHERIC`, or `TRANSITIONAL`.
    * Importance & Suspense score meters (e.g., `Importance: 8.5/10`, `Suspense: 7.2/10`).
    * Climax flags: `⭐ Pivotal Scene` or `Dispensable`.
    * Editorial status: `✅ Included in Final Cut` vs. `✂️ Cut / Atmospheric Summary`.
  * **Dramatic Focus:** Short editorial summary explaining why the scene was framed and what dramatic question it explores.
* **Expandable Events Card:**
  * Shows every discrete agent action that occurred during the scene (movements, dialogues, item pickups, searches, attacks).
  * $O(1)$ fast lookup ensures smooth, responsive browsing without timeline lag.
* **Per-Scene Prose Actions:**
  * Individual button on each card (`✍️ Write Prose` / `🔄 Regenerate Prose`) to rewrite or refine a single chapter via LLM without touching the rest of the novel.

---

### 📜 3. Event Log Tab
* **Chronological Action Stream:** Complete textual feed showing every Game Master resolution and agent decision step-by-step.
* **Editorial Decision Markers:** Logs why scene boundaries were triggered (location shifts, high-impact actions, or accumulation thresholds).
* **Filterable & Searchable:** Quickly locate specific characters, locations, or incident types.

---

### 📖 4. Story Tab
* **Novel Reader View:** Clean, modern reader view rendering written chapters with customizable typography.
* **Text Mode Selector:**
  * **Raw Text Mode:** Complete reading of all scenes, including transitional movements and quiet atmospheric moments.
  * **Edited Text Mode:** The curated "Final Cut", filtering out dispensable fluff to provide a fast-paced, high-tension reading experience.
* **Highlights & Key Events Panel:**
  * **⚡ Major Dramaturgic & Global Events:** Visual timeline cards for scheduled global incidents (power outages, storms, body discoveries) with exact step and duration markers.
  * **🔍 Critical Turning Points & Clues:** Highlighted cards for murders, discoveries, hidden items found, and final accusations.
* **Generation Metadata Appendix:**
  * A dedicated styled card appended to the bottom of the novel, documenting all generation parameters: title, premise, simulation duration, cast count, authorial voice, Ollama URL, LLM models used, temperatures, and context window sizes.

---

## 🚦 2. Status Bar & Build Version Display

Fixed to the bottom of the main window, the status bar is **globally visible across all tabs**:
* **Pulsing Status Lamp (`statusLamp`):**
  * **Glowing Amber (`#f59e0b`):** Pulses continuously when generation is running (world initialization, agent simulation loop, scene framing, or prose writing).
  * **Steady Green (`#10b981`):** Displays solid green when the engine is idle and ready for user actions.
* **Status Label:** Displays real-time operational messages (e.g. `Simulating Agents [llama3.1:8b] (Step 45/144)...` or `Writing prose chapters [llama3.1:8b]... (Scene 4/12 completed)`).
* **Build Version Badge:** Dynamically displays the compiled application build version (e.g. `v0.0.1-SNAPSHOT` / Git commit info via Spring Boot buildInfo).
* **Right-Side Story Stats:** Dedicated label (`📖 X words | Y scenes`) that updates live as chapters are penned without overwriting the generation status.
* **Center Timeline Scrubber:** Quick access to scrub the timeline from any tab.

---

## 💾 3. Project Management, Autosave & Export

* **Active Project Header Bar:**
  * Prominently displayed directly beneath the menu bar at the top of the application window (`📁 Active Project: <Title> [<Directory>]`).
  * Continuously updates window title and header indicator during creation, loading, and autosaves.
* **File Menu:**
  * **File -> New (`Ctrl+N`):** Prompts for confirmation if generation is active, resets all state, and prepares for a clean mystery.
  * **File -> Load Novel (`Ctrl+O`):** Displays wait cursor during fast deserialization and restores full event-sourced state and chapters.
* **Dual-Format Novel Export (`💾 Export Story (TXT & PDF)`):**
  * Placed directly adjacent to **✍️ 4. Generate Prose** (and in the Story view).
  * Automatically exports both `<ProjectName>.txt` and `<ProjectName>.pdf` directly into the project directory without modal prompts.
  * Appends the complete **Generation Parameters & Setup** appendix to both exported files.
* **Safe Autosave:**
  * Automatically saves progress after each milestone using atomic replacement (`.tmp` $\rightarrow$ target file) with retry logic for Windows file locking.

---

## ⚙️ 4. LLM Configuration Dialog

Accessible via **Config -> LLM Settings**:
* **Ollama Connection & Model Discovery:** Fetches and populates installed models directly from Ollama via `Fetch Models`.
* **Independent Role Mapping:**
  * **World Generation Model:** For creating premise, characters, rooms, and items.
  * **Agent Simulation Model:** For per-agent time step resolution.
  * **Prose Generation Model:** For drafting literary chapters.
* **Advanced Hyperparameters (Collapsible):**
  * Independent **Temperature** sliders (0.00 – 1.50) for each model role.
  * Independent **Context Size (`num_ctx`)** spinners (e.g. 2,048 – 32,768) to prevent prompt truncation on long scenes.
* **Batch Test Connection:** Validates network connectivity and model availability for all three assigned roles simultaneously.
* **Adaptive Resizing & Scrolling:** The dialog is fully resizable and scrollable, automatically adjusting via `sizeToScene()` when expanding advanced settings so no controls are clipped.

---

## 📚 Related Documentation
* 📖 **[README.md](README.md):** Project overview, architecture flow, and quick start guide.
* 🤖 **[AGENT.md](AGENT.md):** Domain models, per-agent autonomous decision loop, and algorithmic safeguards.
