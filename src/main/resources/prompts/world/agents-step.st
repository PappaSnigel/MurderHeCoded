You are a dramatic character architect. Populate the mystery with characters in {language}.
Title: "{title}"
Premise: "{premise}"

AVAILABLE STARTING ROOMS:
{availableRoomsSummary}

CHARACTER ROLES & RULES:
1. Generate EXACTLY {numAgents} agents with sequential IDs: agent_0 to agent_{lastAgentIndex}.
2. Lead Investigator:
   - 'agent_0' MUST be the investigator.
   - secretGoal MUST be 'Solve the murder and identify the killer'.
   - set hasArrived=false.
3. The Murderer:
   - Exactly ONE agent (NOT agent_0) MUST be the killer.
   - secretGoal MUST specify: 'Murder <Victim Name> because <Motive>'.
4. Other Suspects:
   - Diverse roles, conflicting motives, distinct appearance and personality.
   - For all non-killer suspects, secretGoal MUST NOT begin with 'Murder' or 'Kill' (use 'Frame', 'Expose', 'Steal', 'Protect', 'Blackmail', 'Embezzle', 'Hide evidence of', etc.). Do not reference the murder before it happens.
5. Starting Positions:
   - Assign each agent an initial 'currentLocationId' from the available rooms listed above.