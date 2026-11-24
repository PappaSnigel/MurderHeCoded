# 🕵️‍♂️ Murder, He Coded
### *Autonomous Multi-Agent Murder Mystery Simulator & Literary Novel Generator*

[![Java 21](https://img.shields.io/badge/Java-21-orange.svg?logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.4-brightgreen.svg?logo=springboot)](https://spring.io/projects/spring-boot)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-1.0.0--M4-blue.svg)](https://spring.io/projects/spring-ai)
[![Ollama](https://img.shields.io/badge/LLM-Ollama%20(Local)-purple.svg?logo=ollama)](https://ollama.com/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![NaNoGenMo](https://img.shields.io/badge/NaNoGenMo-Submission-black.svg)](https://github.com/NaNoGenMo)

Welcome to **Murder, He Coded**, an autonomous murder mystery simulator and literary novel generator created for **NaNoGenMo** (National Novel Generation Month). 

Unlike conventional rule-based or linear narrative systems, **Murder, He Coded** deploys local Large Language Models (LLMs) via **Spring AI** and **Ollama** across an autonomous multi-agent simulation. Characters navigate physical spaces, form dynamic goals, converse, manipulate clues, and carry out covert crimes. An editorial curation engine then frames the emergent events into dramatic scenes, and a dedicated authorial prose engine pens them into a full-length mystery novel (producing over 80,000 words across 60+ chapters).

The entire system is visualized in real-time through a multi-threaded **JavaFX** workspace featuring interactive timeline scrubbing, entity inspectors, dramaturgy pacing curves, and dual-format (TXT & PDF) exports.

---

## 🏗️ The 4-Phase Generation Pipeline

The novel generation lifecycle is structured into four independent, decoupled stages that can be executed, inspected, and refined individually:

```
┌─────────────────────────┐      ┌─────────────────────────┐      ┌─────────────────────────┐      ┌─────────────────────────┐
│   Phase 1: World        │ ───> │   Phase 2: Simulation   │ ───> │   Phase 3: Scenes       │ ───> │   Phase 4: Prose        │
│   World Building        │      │   Agent Simulation      │      │   Editorial Curation    │      │   Literary Prose        │
│   (Cast, Rooms, Clues)  │      │   (Drives, Crimes, GM)  │      │   (Beats, Cut, Pacing)  │      │   (Chapter Writing)     │
└─────────────────────────┘      └─────────────────────────┘      └─────────────────────────┘      └─────────────────────────┘
             │                                │                                │                                │
      [🌍 1. Build World]              [▶ 2. Start Sim]                 [🎬 3. Scenes]                   [✍️ 4. Prose]
```

### 🌍 Phase 1: World Building (`WorldBuilderService`)
* **Dynamic Mystery Architecture:** Generates cast members (names, appearances, psychological profiles, secret goals, relationships), locations with distinct sensory descriptions, initial items (weapons, valuables, hidden clues), and scheduled global incidents.
* **Inspect Before Simulating:** Browse characters, places, and initial layout in the UI before committing to execution.

### ▶ Phase 2: Agent Simulation (`SimulationService` & `DramaturgyDirectorService`)
* **Autonomous Agency:** Advances the story tick-by-tick (e.g. 15-minute time steps across 24–36 simulated hours).
* **Spatial & Social Reasoning:** Agents converse, search rooms, alter environments, conceal or discover weapons, and forge alibis.
* **Event-Sourced Snapshots:** All state mutations, knowledge transfers, and item movements are committed to an immutable, $O(1)$-scrubbable timeline history.

### 🎬 Phase 3: Editorial Scene Framing (`EditorService`)
* **Location-Boundary Rule:** Analyzes recorded simulation events and partitions them into coherent dramatic scenes strictly bounded by location transitions.
* **Dramaturgic Scoring:** Evaluates high-impact turning points (murders, discoveries, accusations) and assigns numerical Importance & Suspense scores (0–10).
* **The Final Cut:** Categorizes scenes into `ESSENTIAL`, `SUPPORTING`, `ATMOSPHERIC`, or `TRANSITIONAL`, allowing dispensable fluff to be pruned while keeping core plot threads intact.

### ✍️ Phase 4: Literary Prose Authoring (`ProseService`)
* **Atmospheric Chapter Writing:** Translates raw event logs and dramatic beats into immersive, narrative prose.
* **Authorial Voice:** Supports customizable literary styles (Agatha Christie, Arthur Conan Doyle, Raymond Chandler, Edgar Allan Poe) and pacing lengths.
* **Selective Regeneration:** Enables rewriting individual chapters or generating the complete novel end-to-end.

---

## 🛡️ Core Algorithmic Safeguards & Stability Systems

To prevent common multi-agent simulation traps (hallucinations, identity bleed, dialogue loops), the engine incorporates several robust architectural safeguards:

* 🔒 **Isolated Murder Rule:** Murder actions are strictly barred from succeeding if bystanders are present (requiring killer and victim to be completely isolated), and locked during initial exposition steps (first 15–20%) to allow dramatic tension and subplots to build naturally.
* 📓 **Notebook Inheritance with Identity Preservation:** When a character dies, their dynamic memories drop as a physical `NOTEBOOK` item. When read by another character or detective, memories are strictly attributed (`[From Notebook of Character]: ...`), eliminating identity confusion and preventing characters from falsely adopting another's past.
* 🧹 **Knowledge Base Deduplication:** Semantic and string deduplication prevents agents from accumulating dozens of redundant copies of the same observation, forcing the detective to actively patrol and interview suspects.
* 🔇 **Anti-Echo Dialogue Guard:** Tracks active and historical dialogue quotes to prevent repetitive whisper loops or echo chambers across characters.
* 🧭 **Systematic Detective Patrol:** After completing the initial crime scene inspection, the detective's prompt disables "rush-to-scene" behavior, ensuring thorough, station-wide interrogations.
* 💾 **Safe Atomic Persistence:** File operations utilize atomic temporary staging (`.tmp` $\rightarrow$ target) with automatic retry logic to guarantee zero data loss against Windows file locks.

---

## 🌐 Dual-Language Localization (I18n)

* **UI Localization:** All buttons, dialogs, tabs, real-time log outputs, and status bars seamlessly toggle between **English** and **Swedish** (`ResourceBundle`).
* **Generative Alignment:** Changing the active language dynamically switches system prompts and persona generation, ensuring names, descriptions, dialogues, and prose are penned natively in the selected language.

---

## ⚙️ Tri-Model LLM Hierarchy & Hyperparameters

The architecture allows different local models and parameters to be assigned based on functional strengths:

| Role | Responsibility | Recommended Model | Temperature |
| :--- | :--- | :--- | :---: |
| **World Generation** | Premise, cast, room geography, items, and scheduled incidents | `llama3.1:8b` | 0.70 |
| **Agent Simulation** | Fast, deterministic per-agent action resolution and spatial moves | `llama3.1:8b` / `mistral` | 0.40–0.60 |
| **Prose Generation** | Highly articulate, atmospheric literary prose and dialogue | `llama3.1:8b` / `qwen2.5:14b` | 0.75 |

* **Connection Batch Validation:** One-click batch testing validates Ollama endpoint health and model availability before running.
* **Context Size (`num_ctx`):** Configurable up to 32,768 tokens per role to prevent context truncation during long climaxes.

---

## 🚀 Quick Start Guide

### 1. Prerequisites
* **Java 21** or later (JDK).
* **Gradle** (Wrapper included).
* **Ollama** installed and running (default: `http://localhost:11434` or configured in `application.properties`).

### 2. Fetch the Local AI Model
Pull a supported model via Ollama:
```bash
ollama run llama3.1:8b
```

### 3. Build & Run the Application
Clone the repository and launch the application:

```bash
# On Linux / macOS
./gradlew bootRun

# On Windows (PowerShell / Command Prompt)
.\gradlew.bat bootRun
```

### 4. Running Tests
Run the unit, integration, and architectural verification suite:
```bash
.\gradlew.bat test
```

---

## 📚 Detailed Documentation Sitemap

For deeper technical breakdowns, see the dedicated reference documents:

* 📖 **[AGENT.md](AGENT.md):** Complete specification of the domain entities (`Agent`, `Location`, `Item`, `GlobalEvent`), per-agent decision loops, JSON schemas, notebook inheritance mechanics, and dramaturgic validation rules.
* 🎨 **[FEATURES.md](FEATURES.md):** Comprehensive guide to the multi-threaded JavaFX interface (Simulation, Scenes, Event Log, Story tabs), status bar pulsing indicators, timeline scrubbing, and dual-format TXT/PDF exports.

---

## 📄 License

This project is licensed under the **Apache License 2.0** — see the [LICENSE](LICENSE) file for details.
