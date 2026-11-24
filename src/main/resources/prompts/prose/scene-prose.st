Write a descriptive, narrative chapter synthesizing the events from Time Step {startStep} ({startTime}) to Time Step {endStep} ({endTime}).

DRAMATIC FOCUS: "{dramaticFocus}"
PRIMARY SETTING: "{primarySetting}"
EDITORIAL EVALUATION: Category: {category} | Importance: {importanceScore}/10 | Suspense: {suspenseScore}/10

{storyTitle}
{premise}
{voiceInstruction}
Write in THIRD-PERSON perspective (He/She/They), like a classic novel. Do NOT use first-person (I/We) unless in dialogue.
DO NOT use markdown formatting like **bolding** or UPPERCASE for names or places. Write like a normal printed book.
DO NOT output JSON or bullet points. Output ONLY the prose text.
CRITICAL: If the event details mention raw IDs with underscores, YOU MUST translate them into their natural story names. NEVER output an ID with an underscore in the prose. Use the Story Reference Directory below to look up any ID.
CRITICAL CAST, SPATIAL CONTINUITY & CHARACTER VITALITY MANDATES:
1. STRICT CLOSED-CAST ENSEMBLE (NO INVENTED / HALLUCINATED NPCS):
- The ONLY characters who exist in this story are the registered characters in the Characters Directory below.
- You are STRICTLY FORBIDDEN from inventing new named NPCs, extra assistants, crew members, officers, or speaking bystanders.
- If a character is alone in a room, they reflect internally, observe surroundings, examine clues, or review memories. NEVER invent someone for them to talk to!

2. STRICT ROOM PRESENCE & SPATIAL CONTINUITY:
- ONLY characters listed under "=== CHARACTERS PRESENT IN THIS SCENE ===" are physically in this room and allowed to appear, speak, move, or interact in this chapter!
- Characters listed under "=== OTHER CHARACTERS (LOCATED ELSEWHERE OR DECEASED - NOT IN THIS SCENE) ===" are NOT present in this room. You are STRICTLY FORBIDDEN from having them enter, appear, speak, exchange glances, or interact in this chapter (present characters may only mention them in dialogue or thoughts).

3. CHARACTER VITALITY INVARIANTS:
- Characters marked [ALIVE] are LIVING, BREATHING participants in the story. You are STRICTLY FORBIDDEN from describing a character marked [ALIVE] as dead, murdered, or a corpse!
- Characters marked [ALIVE AT START OF SCENE -> BECOMES VICTIM / DIES IN THIS SCENE] are alive and active at the beginning of this scene, and meet their demise during this scene's events as specified in the event log below.
- Characters marked [DECEASED] are already dead. They may ONLY be referenced as corpses, or in past tense / retrospective dialogue if their body is discovered.

=== PREVIOUS STORY RECAP & CONTINUITY CONTEXT ===
{previousChaptersContext}

CRITICAL ANTI-REPETITION & FORWARD CONTINUITY MANDATE:
- The previous chapters above have ALREADY taken place in this novel.
- NEVER re-introduce room decor, physical furniture, or items that were already established in earlier chapters. Treat established elements as already known to the reader.
- NEVER repeat speeches, monologues, or conversations that occurred in earlier scenes. Always advance the story and dialogue forward in time!

=== STORY REFERENCE DIRECTORY (ID LOOKUP) ===
Characters:
{charactersList}

Locations:
{locationsList}

Items:
{itemsList}

NARRATIVE INSTRUCTIONS:
{categoryInstructions}
- Weave the events across locations into a single, cohesive, flowing narrative scene.

SCENE LENGTH:
{lengthInstructions}

{framingInstructions}

=== CHRONOLOGICAL EVENTS IN THIS SCENE ===
{eventsSection}
