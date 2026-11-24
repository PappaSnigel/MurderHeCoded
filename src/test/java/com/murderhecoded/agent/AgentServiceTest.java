package com.murderhecoded.agent;

import com.murderhecoded.domain.Action;
import com.murderhecoded.domain.ActionType;
import com.murderhecoded.domain.Agent;
import com.murderhecoded.domain.AgentActionRecord;
import com.murderhecoded.domain.Location;
import com.murderhecoded.llm.LlmExecutor;
import com.murderhecoded.llm.LlmRole;
import com.murderhecoded.repository.SimulationRepository;
import com.murderhecoded.service.DramaturgyDirectorService;
import com.murderhecoded.service.OllamaLlmService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class AgentServiceTest {

    private AgentService agentService;
    private OllamaLlmService ollamaLlmService;
    private LlmExecutor llmExecutor;
    private DramaturgyDirectorService dramaturgyDirectorService;
    private SimulationRepository repository;

    private Agent innocentGuest;
    private Agent murderer;
    private Agent detective;
    private Agent bystanderInRoom;
    private Agent agentInOtherRoom;

    private Location drawingRoom;
    private Location conservatory;

    @BeforeEach
    void setUp() {
        ollamaLlmService = Mockito.mock(OllamaLlmService.class);
        llmExecutor = new LlmExecutor();
        dramaturgyDirectorService = new DramaturgyDirectorService();
        agentService = new AgentService(ollamaLlmService, llmExecutor, dramaturgyDirectorService);

        repository = new SimulationRepository();

        drawingRoom = new Location();
        drawingRoom.setId("room_0");
        drawingRoom.setName("Drawing Room");

        conservatory = new Location();
        conservatory.setId("room_1");
        conservatory.setName("Conservatory");

        innocentGuest = new Agent();
        innocentGuest.setId("agent_guest");
        innocentGuest.setName("Colonel Mustard");
        innocentGuest.setRole("Guest");
        innocentGuest.setCurrentLocationId("room_0");
        innocentGuest.setAlive(true);
        innocentGuest.setHasArrived(true);

        bystanderInRoom = new Agent();
        bystanderInRoom.setId("agent_bystander");
        bystanderInRoom.setName("Mrs. Peacock");
        bystanderInRoom.setRole("Guest");
        bystanderInRoom.setCurrentLocationId("room_0");
        bystanderInRoom.setAlive(true);
        bystanderInRoom.setHasArrived(true);

        agentInOtherRoom = new Agent();
        agentInOtherRoom.setId("agent_other");
        agentInOtherRoom.setName("Miss Scarlet");
        agentInOtherRoom.setRole("Guest");
        agentInOtherRoom.setCurrentLocationId("room_1");
        agentInOtherRoom.setAlive(true);
        agentInOtherRoom.setHasArrived(true);

        murderer = new Agent();
        murderer.setId("agent_murderer");
        murderer.setName("Professor Plum");
        murderer.setRole("Scientist");
        murderer.setHasMurderIntent(true);
        murderer.setCurrentLocationId("room_0");
        murderer.setAlive(true);
        murderer.setHasArrived(true);

        detective = new Agent();
        detective.setId("agent_detective");
        detective.setName("Inspector Vance");
        detective.setRole("Detective");
        detective.setCurrentLocationId("room_0");
        detective.setAlive(true);
        detective.setHasArrived(true);

        repository.initialize(
                List.of(innocentGuest, bystanderInRoom, agentInOtherRoom, murderer, detective),
                List.of(drawingRoom, conservatory),
                List.of(),
                List.of(),
                "Manor Murder",
                "A stormy night mystery"
        );
    }

    @Test
    void testValidateActionNullHandling() {
        assertFalse(agentService.validateAction(null, innocentGuest, repository, List.of()));

        AgentActionRecord nullTypeRecord = new AgentActionRecord(
                innocentGuest.getId(), null, null, null, null, null, null, null, null, "Details", null
        );
        assertFalse(agentService.validateAction(nullTypeRecord, innocentGuest, repository, List.of()));
    }

    @Test
    void testValidateActionObserveAndReflectCooldownRules() {
        AgentActionRecord observeRecord = new AgentActionRecord(
                innocentGuest.getId(), ActionType.OBSERVE, null, null, null, null, null, null, null, "Watching", null
        );
        // Initially allowed
        assertTrue(agentService.validateAction(observeRecord, innocentGuest, repository, List.of()));

        // When observe is on cooldown in the same room -> rejected
        innocentGuest.setConsecutiveObserveCount(1);
        innocentGuest.setLastObservedLocationId("room_0");
        assertFalse(agentService.validateAction(observeRecord, innocentGuest, repository, List.of()));

        // If moved to a different room -> allowed
        innocentGuest.setCurrentLocationId("room_1");
        assertTrue(agentService.validateAction(observeRecord, innocentGuest, repository, List.of()));
        innocentGuest.setCurrentLocationId("room_0");
        innocentGuest.setConsecutiveObserveCount(0);
        innocentGuest.setLastObservedLocationId(null);

        AgentActionRecord reflectRecord = new AgentActionRecord(
                innocentGuest.getId(), ActionType.REFLECT, null, null, null, null, null, null, null, "Thinking", null
        );
        // Initially allowed
        assertTrue(agentService.validateAction(reflectRecord, innocentGuest, repository, List.of()));

        // When reflect is on cooldown -> rejected
        innocentGuest.setConsecutiveReflectCount(1);
        assertFalse(agentService.validateAction(reflectRecord, innocentGuest, repository, List.of()));
        innocentGuest.setConsecutiveReflectCount(0);
    }

    @Test
    void testEmergencyOverrideAndActionDecayInPromptParams() {
        innocentGuest.recordAction(1, "OBSERVE in Drawing Room");
        innocentGuest.recordAction(2, "TALK with Mrs. Peacock");

        java.util.Map<String, Object> params = agentService.buildPromptParams(innocentGuest, repository);
        assertNotNull(params.get("recentActions"));
        String recent = (String) params.get("recentActions");
        assertTrue(recent.contains("OBSERVE in Drawing Room"));
        assertTrue(recent.contains("TALK with Mrs. Peacock"));

        // Now trigger emergency discovery
        dramaturgyDirectorService.setDiscoveryStep(0);
        repository.putGlobalEvent("event_alarm", new com.murderhecoded.domain.GlobalEvent(
                "event_alarm", "Alarm: Body Discovered!", "Body in airlock", 0, 3, "room_0", "ALARM", true, true, false
        ));
        assertTrue(dramaturgyDirectorService.isEmergencyOverrideActive(repository));

        java.util.Map<String, Object> crisisParams = agentService.buildPromptParams(innocentGuest, repository);
        String goal = (String) crisisParams.get("agentSecretGoal");
        assertTrue(goal.contains("EMERGENCY OVERRIDE"), "Secret goal should be overridden during active crisis");
    }

    @Test
    void testValidateActionMurderPermissionEnforcement() {
        AgentActionRecord murderRecord = new AgentActionRecord(
                innocentGuest.getId(), ActionType.MURDER, null, bystanderInRoom.getId(), null, null, null, null, null, "Lethal strike", null
        );

        // Non-murderer attempting murder must be rejected
        assertFalse(agentService.validateAction(murderRecord, innocentGuest, repository, List.of(bystanderInRoom)));

        // Murderer attempting murder is permitted when isolated with victim and step >= 12
        repository.setCurrentStep(15);
        AgentActionRecord killerRecord = new AgentActionRecord(
                murderer.getId(), ActionType.MURDER, null, bystanderInRoom.getId(), null, null, null, null, null, "Lethal strike", null
        );
        assertTrue(agentService.validateAction(killerRecord, murderer, repository, List.of(bystanderInRoom)));
    }

    @Test
    void testValidateActionAccusePermissionEnforcement() {
        AgentActionRecord accuseRecord = new AgentActionRecord(
                innocentGuest.getId(), ActionType.ACCUSE, null, bystanderInRoom.getId(), null, null, null, null, null, "You did it!", null
        );

        // Non-detective attempting accusation must be rejected
        assertFalse(agentService.validateAction(accuseRecord, innocentGuest, repository, List.of(bystanderInRoom)));

        // Detective attempting accusation is permitted
        AgentActionRecord detectiveRecord = new AgentActionRecord(
                detective.getId(), ActionType.ACCUSE, null, bystanderInRoom.getId(), null, null, null, null, null, "You did it!", null
        );
        assertTrue(agentService.validateAction(detectiveRecord, detective, repository, List.of(bystanderInRoom)));
    }

    @Test
    void testValidateActionNavigationRules() {
        // Target room null -> invalid
        AgentActionRecord moveNull = new AgentActionRecord(
                innocentGuest.getId(), ActionType.MOVE, null, null, null, null, null, null, null, "Moving", null
        );
        assertFalse(agentService.validateAction(moveNull, innocentGuest, repository, List.of()));

        // Target room same as current -> invalid
        AgentActionRecord moveSame = new AgentActionRecord(
                innocentGuest.getId(), ActionType.MOVE, "room_0", null, null, null, null, null, null, "Moving", null
        );
        assertFalse(agentService.validateAction(moveSame, innocentGuest, repository, List.of()));

        // Target room non-existent -> invalid
        AgentActionRecord moveNonExistent = new AgentActionRecord(
                innocentGuest.getId(), ActionType.MOVE, "room_999", null, null, null, null, null, null, "Moving", null
        );
        assertFalse(agentService.validateAction(moveNonExistent, innocentGuest, repository, List.of()));

        // Target room different existing room -> valid
        AgentActionRecord moveValid = new AgentActionRecord(
                innocentGuest.getId(), ActionType.MOVE, "room_1", null, null, null, null, null, null, "Moving to conservatory", null
        );
        assertTrue(agentService.validateAction(moveValid, innocentGuest, repository, List.of()));
    }

    @Test
    void testValidateActionTalkRules() {
        List<Agent> presentOthers = List.of(bystanderInRoom);

        // Talk with null target -> invalid
        AgentActionRecord talkNull = new AgentActionRecord(
                innocentGuest.getId(), ActionType.TALK, null, null, "Hello", null, null, null, null, "Speaking", null
        );
        assertFalse(agentService.validateAction(talkNull, innocentGuest, repository, presentOthers));

        // Talk to self -> invalid
        AgentActionRecord talkSelf = new AgentActionRecord(
                innocentGuest.getId(), ActionType.TALK, null, innocentGuest.getId(), "Hello me", null, null, null, null, "Speaking", null
        );
        assertFalse(agentService.validateAction(talkSelf, innocentGuest, repository, presentOthers));

        // Talk to agent in another room -> invalid
        AgentActionRecord talkOtherRoom = new AgentActionRecord(
                innocentGuest.getId(), ActionType.TALK, null, agentInOtherRoom.getId(), "Hello from afar", null, null, null, null, "Speaking", null
        );
        assertFalse(agentService.validateAction(talkOtherRoom, innocentGuest, repository, presentOthers));

        // Talk to agent present in the same room -> valid
        AgentActionRecord talkValid = new AgentActionRecord(
                innocentGuest.getId(), ActionType.TALK, null, bystanderInRoom.getId(), "Did you hear the thunder?", null, null, null, null, "Speaking", null
        );
        assertTrue(agentService.validateAction(talkValid, innocentGuest, repository, presentOthers));
    }

    @Test
    void testDecideActionSuccessfulExecution() {
        AgentActionRecord expectedFromLlm = new AgentActionRecord(
                null,
                ActionType.MOVE,
                "room_1",
                null,
                null,
                null,
                null,
                null,
                null,
                "Walking carefully towards the conservatory",
                "Saw a muddy footprint near the door"
        );

        ChatClient mockChatClient = Mockito.mock(ChatClient.class);
        ChatClient.Builder mockBuilder = Mockito.mock(ChatClient.Builder.class);
        ChatClient.ChatClientRequestSpec mockRequestSpec = Mockito.mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec mockCallSpec = Mockito.mock(ChatClient.CallResponseSpec.class);

        when(ollamaLlmService.getChatClient(LlmRole.AGENT)).thenReturn(mockChatClient);
        when(mockChatClient.mutate()).thenReturn(mockBuilder);
        when(mockBuilder.defaultSystem(anyString())).thenReturn(mockBuilder);
        when(mockBuilder.build()).thenReturn(mockChatClient);
        when(mockChatClient.prompt()).thenReturn(mockRequestSpec);
        when(mockRequestSpec.user(any(java.util.function.Consumer.class))).thenReturn(mockRequestSpec);
        when(mockRequestSpec.call()).thenReturn(mockCallSpec);
        when(mockCallSpec.entity(AgentActionRecord.class)).thenReturn(expectedFromLlm);

        AgentActionRecord decision = agentService.decideAction(innocentGuest, repository);

        assertNotNull(decision);
        assertEquals(innocentGuest.getId(), decision.actorId(), "actorId should be populated from the acting agent");
        assertEquals(ActionType.MOVE, decision.actionType());
        assertEquals("room_1", decision.targetLocationId());
        assertEquals("Walking carefully towards the conservatory", decision.details());
        assertEquals("Saw a muddy footprint near the door", decision.newKnowledgeFact());

        Action converted = decision.toAction();
        assertEquals(innocentGuest.getId(), converted.getActorId());
        assertEquals(ActionType.MOVE, converted.getActionType());
        assertEquals("room_1", converted.getTargetLocationId());
    }

    @Test
    void testDecideActionFailsSemanticValidationAfterMaxRetries() {
        // LLM keeps trying to perform MURDER with an innocent guest
        AgentActionRecord illegalMurder = new AgentActionRecord(
                null,
                ActionType.MURDER,
                null,
                bystanderInRoom.getId(),
                null,
                null,
                null,
                null,
                null,
                "Attacking",
                null
        );

        ChatClient mockChatClient = Mockito.mock(ChatClient.class);
        ChatClient.Builder mockBuilder = Mockito.mock(ChatClient.Builder.class);
        ChatClient.ChatClientRequestSpec mockRequestSpec = Mockito.mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec mockCallSpec = Mockito.mock(ChatClient.CallResponseSpec.class);

        when(ollamaLlmService.getChatClient(LlmRole.AGENT)).thenReturn(mockChatClient);
        when(mockChatClient.mutate()).thenReturn(mockBuilder);
        when(mockBuilder.defaultSystem(anyString())).thenReturn(mockBuilder);
        when(mockBuilder.build()).thenReturn(mockChatClient);
        when(mockChatClient.prompt()).thenReturn(mockRequestSpec);
        when(mockRequestSpec.user(any(java.util.function.Consumer.class))).thenReturn(mockRequestSpec);
        when(mockRequestSpec.call()).thenReturn(mockCallSpec);
        when(mockCallSpec.entity(AgentActionRecord.class)).thenReturn(illegalMurder);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                agentService.decideAction(innocentGuest, repository)
        );
        assertTrue(ex.getMessage().contains("Misslyckades semantiskt efter 3 försök"));
        assertTrue(ex.getMessage().contains("MURDER"));
    }

    @Test
    void testAgentActionRecordToSummaryString() {
        AgentActionRecord record = new AgentActionRecord(
                "agent_1",
                ActionType.TALK,
                null,
                "agent_2",
                "Secret fact",
                null,
                null,
                null,
                null,
                "Talking quietly about the incident in the corridor",
                null
        );
        String summary = record.toSummaryString();
        assertTrue(summary.startsWith("TALK"));
        assertTrue(summary.contains("targetAgent=agent_2"));
        assertTrue(summary.contains("details=\"Talking quietly about the incident in the corridor\""));
        assertEquals(summary, record.toString());
    }

    @Test
    void testValidateActionAloneInRoomRejectsTalkAndCasualInteract() {
        // When alone in room (empty otherAgentsInRoom)
        AgentActionRecord talk = new AgentActionRecord(
                innocentGuest.getId(), ActionType.TALK, null, bystanderInRoom.getId(), null, null, null, null, null, "Talking", null
        );
        assertFalse(agentService.validateAction(talk, innocentGuest, repository, List.of()),
                "TALK must be rejected when alone in the room");

        AgentActionRecord casual = new AgentActionRecord(
                innocentGuest.getId(), ActionType.CASUAL_INTERACT, null, null, null, null, null, null, null, "Idling", null
        );
        assertFalse(agentService.validateAction(casual, innocentGuest, repository, List.of()),
                "CASUAL_INTERACT must be rejected when alone in the room");
    }

    @Test
    void testValidateActionSocialInteractionWithDeadCharacterRejected() {
        bystanderInRoom.setAlive(false);

        // Direct target is dead
        AgentActionRecord talkDead = new AgentActionRecord(
                innocentGuest.getId(), ActionType.TALK, null, bystanderInRoom.getId(), null, null, null, null, null, "Talking to dead", null
        );
        assertFalse(agentService.validateAction(talkDead, innocentGuest, repository, List.of()),
                "TALK to dead character must be rejected");

        // Details mentions dead character
        Agent livingOther = agentInOtherRoom;
        livingOther.setCurrentLocationId("room_0");
        AgentActionRecord ghostTalk = new AgentActionRecord(
                innocentGuest.getId(), ActionType.TALK, null, livingOther.getId(), null, null, null, null, null,
                "Exchanging glances with " + bystanderInRoom.getName() + " across the table", null
        );
        assertFalse(agentService.validateAction(ghostTalk, innocentGuest, repository, List.of(livingOther)),
                "Social action details mentioning dead character must be rejected");
    }

    @Test
    void testValidateActionSearchCooldown() {
        AgentActionRecord search = new AgentActionRecord(
                innocentGuest.getId(), ActionType.SEARCH, null, null, null, null, null, null, null, "Searching room", null
        );
        assertTrue(agentService.validateAction(search, innocentGuest, repository, List.of()));

        innocentGuest.setConsecutiveSearchCount(1);
        assertFalse(agentService.validateAction(search, innocentGuest, repository, List.of()),
                "SEARCH must be rejected on consecutive search cooldown");
    }

    @Test
    void testValidateActionDoubleMurderRejected() {
        repository.setCurrentStep(15);
        AgentActionRecord firstMurder = new AgentActionRecord(
                murderer.getId(), ActionType.MURDER, null, bystanderInRoom.getId(), null, null, null, null, null, "Lethal strike", null
        );
        assertTrue(agentService.validateAction(firstMurder, murderer, repository, List.of(bystanderInRoom)));

        // Once a victim has died, subsequent murder must be rejected!
        bystanderInRoom.setAlive(false);
        Agent secondVictim = innocentGuest;
        AgentActionRecord secondMurder = new AgentActionRecord(
                murderer.getId(), ActionType.MURDER, null, secondVictim.getId(), null, null, null, null, null, "Second strike", null
        );
        assertFalse(agentService.validateAction(secondMurder, murderer, repository, List.of(secondVictim)),
                "Second MURDER must be rejected once a murder has already occurred");
    }

    @Test
    void testValidateActionMurdererCannotAccuse() {
        AgentActionRecord accuse = new AgentActionRecord(
                murderer.getId(), ActionType.ACCUSE, null, bystanderInRoom.getId(), null, null, null, null, null, "I accuse you!", null
        );
        assertFalse(agentService.validateAction(accuse, murderer, repository, List.of(bystanderInRoom)),
                "Murderer must never be allowed to ACCUSE");
    }

    @Test
    void testAllowedActionsExcludesTalkWhenAlone() {
        // Move all other agents to room_1 so innocentGuest is completely alone in room_0
        bystanderInRoom.setCurrentLocationId("room_1");
        murderer.setCurrentLocationId("room_1");
        detective.setCurrentLocationId("room_1");

        java.util.Map<String, Object> params = agentService.buildPromptParams(innocentGuest, repository);
        String allowed = (String) params.get("allowedActions");
        assertFalse(allowed.contains("TALK"), "allowedActions must NOT contain TALK when agent is alone");
        assertFalse(allowed.contains("CASUAL_INTERACT"), "allowedActions must NOT contain CASUAL_INTERACT when agent is alone");
    }

    @Test
    void testAllowedActionsExcludesMurderAndUpdatesGoalWhenCrimeAlreadyCommitted() {
        // Prepare murderer isolated with target victim at step 15
        repository.setCurrentStep(15);
        murderer.setTargetVictimId(bystanderInRoom.getId());
        innocentGuest.setCurrentLocationId("room_1");
        detective.setCurrentLocationId("room_1");

        // Initially, murderer allowedActions has MURDER
        java.util.Map<String, Object> paramsBefore = agentService.buildPromptParams(murderer, repository);
        String allowedBefore = (String) paramsBefore.get("allowedActions");
        assertTrue(allowedBefore.contains("MURDER"), "Murderer must have MURDER in allowed actions before the crime when isolated with victim");

        // Now victim dies
        bystanderInRoom.setAlive(false);

        java.util.Map<String, Object> paramsAfter = agentService.buildPromptParams(murderer, repository);
        String allowedAfter = (String) paramsAfter.get("allowedActions");
        assertFalse(allowedAfter.contains("MURDER"), "MURDER must be removed from allowedActions after a murder has occurred");

        String goalAfter = (String) paramsAfter.get("agentSecretGoal");
        assertTrue(goalAfter.contains("POST-CRIME PHASE"), "Secret goal must permanently switch to post-crime cover up");
    }

    @Test
    void testValidateActionObserveCannotReferenceAbsentAgent() {
        // innocentGuest is in room_0 with bystanderInRoom ("Mrs. Peacock").
        // agentInOtherRoom ("Miss Scarlet") is in room_1.
        AgentActionRecord observeAbsent = new AgentActionRecord(
                innocentGuest.getId(), ActionType.OBSERVE, null, null, null, null, null, null, null,
                "Observing Miss Scarlet whisper suspiciously in the corner", null
        );
        assertFalse(agentService.validateAction(observeAbsent, innocentGuest, repository, List.of(bystanderInRoom)),
                "OBSERVE referencing absent character Miss Scarlet must be rejected");

        AgentActionRecord observePresent = new AgentActionRecord(
                innocentGuest.getId(), ActionType.OBSERVE, null, null, null, null, null, null, null,
                "Observes Mrs. Peacock looking out the window", null
        );
        assertTrue(agentService.validateAction(observePresent, innocentGuest, repository, List.of(bystanderInRoom)),
                "OBSERVE referencing present character Mrs. Peacock must be accepted");

        AgentActionRecord observeRoom = new AgentActionRecord(
                innocentGuest.getId(), ActionType.OBSERVE, null, null, null, null, null, null, null,
                "Observes the antique fireplace and books on the shelf", null
        );
        assertTrue(agentService.validateAction(observeRoom, innocentGuest, repository, List.of()),
                "OBSERVE referencing physical room while alone must be accepted");
    }

    @Test
    void testValidateActionAlterEnvironmentRejectsDuplicateTrace() {
        innocentGuest.getRecentActions().add("Altered environment: Placed a forged note on the desk");

        AgentActionRecord duplicateTrace = new AgentActionRecord(
                innocentGuest.getId(), ActionType.ALTER_ENVIRONMENT, null, null, null, null, "Placed a forged note on the desk", null, null,
                "Leaves a decoy note", null
        );
        assertFalse(agentService.validateAction(duplicateTrace, innocentGuest, repository, List.of()),
                "ALTER_ENVIRONMENT with already executed trace must be rejected to prevent repeating decoys");
    }

    @Test
    void testValidateActionMurderRejectedIfTooEarly() {
        repository.setCurrentStep(5); // Step 5 is before step 12 pacing threshold
        AgentActionRecord earlyMurder = new AgentActionRecord(
                murderer.getId(), ActionType.MURDER, null, bystanderInRoom.getId(), null, null, null, null, null, "Lethal strike", null
        );
        assertFalse(agentService.validateAction(earlyMurder, murderer, repository, List.of(bystanderInRoom)),
                "MURDER must be rejected before step 12 to ensure early story pacing");
    }

    @Test
    void testValidateActionMurderRejectedWithWitnessesWithoutCover() {
        repository.setCurrentStep(15);
        AgentActionRecord witnessMurder = new AgentActionRecord(
                murderer.getId(), ActionType.MURDER, null, bystanderInRoom.getId(), null, null, null, null, null, "Lethal strike", null
        );
        // InnocentGuest is also in the room as an active witness
        assertFalse(agentService.validateAction(witnessMurder, murderer, repository, List.of(bystanderInRoom, innocentGuest)),
                "MURDER in front of witnesses without environmental cover must be rejected");
    }

    @Test
    void testValidateActionTalkRejectsBlurtingSecretGoal() {
        innocentGuest.setSecretGoal("I have been ordered to transport a weapons cache.");
        AgentActionRecord blurtTalk = new AgentActionRecord(
                innocentGuest.getId(), ActionType.TALK, null, bystanderInRoom.getId(),
                "I have been ordered to transport a weapons cache. Any questions?", null, null, null, null,
                "Speaking openly", null
        );
        assertFalse(agentService.validateAction(blurtTalk, innocentGuest, repository, List.of(bystanderInRoom)),
                "TALK that blurts out the agent's secret goal verbatim must be rejected by anti-blurt mandate");
    }

    @Test
    void testObserveBodyRejectedInNonCrimeSceneRoom() {
        // Victim is dead in room_1 (Observation Gallery)
        Agent deadVictim = new Agent();
        deadVictim.setId("victim_1");
        deadVictim.setName("Reginald Ashworth");
        deadVictim.setAlive(false);
        deadVictim.setCurrentLocationId("room_1");
        repository.getAgents().put(deadVictim.getId(), deadVictim);

        // InnocentGuest is in room_0 (Grand Dining Hall) where no corpse exists
        innocentGuest.setCurrentLocationId("room_0");

        // Action in room_0 claiming to observe/examine the body
        AgentActionRecord observeBodyRecord = new AgentActionRecord(
                innocentGuest.getId(), ActionType.OBSERVE, null, null, null, null, null, null, null,
                "Carefully examining the Grand Dining Hall, focusing on the body and the single shard of glass", null
        );
        assertFalse(agentService.validateAction(observeBodyRecord, innocentGuest, repository, List.of()),
                "OBSERVE referencing a body in a room where no deceased body exists must be rejected");

        // Fact claiming to observe embedded shard in neck
        AgentActionRecord observeFactRecord = new AgentActionRecord(
                innocentGuest.getId(), ActionType.OBSERVE, null, null, null, null, null, null, null,
                "Observing the room decor quietly", "Noted the presence of a single shard embedded in the back of the neck"
        );
        assertFalse(agentService.validateAction(observeFactRecord, innocentGuest, repository, List.of()),
                "Fact referencing corpse injury in a room without a corpse must be rejected");
    }

    @Test
    void testTalkRejectedOnDuplicateDialoguePhrase() {
        innocentGuest.getRecentActions().add("Step 61: TALK in The Grand Dining Hall: Approaching Captain Solberg, I whisper urgently. 'I need to speak with you privately about the blue residue I found on the walkway. It could be crucial to the investigation'");

        // 1. Repeating the exact quoted whisper line must be rejected
        AgentActionRecord repeatTalk = new AgentActionRecord(
                innocentGuest.getId(), ActionType.TALK, null, bystanderInRoom.getId(),
                "Blue residue on the walkway", null, null, null, null,
                "I approach with a serious expression. 'I need to speak with you privately about the blue residue I found on the walkway. It could be crucial to the investigation', I whisper.", null
        );
        assertFalse(agentService.validateAction(repeatTalk, innocentGuest, repository, List.of(bystanderInRoom)),
                "TALK repeating identical quoted dialogue from recent actions must be rejected");

        // 2. Repeating matching multi-word phrase without quotes must also be rejected
        AgentActionRecord repeatPhrase = new AgentActionRecord(
                innocentGuest.getId(), ActionType.TALK, null, bystanderInRoom.getId(),
                "Blue residue on the walkway", null, null, null, null,
                "I need to speak with you privately about the blue residue I found on the walkway before the meeting", null
        );
        assertFalse(agentService.validateAction(repeatPhrase, innocentGuest, repository, List.of(bystanderInRoom)),
                "TALK repeating multi-word dialogue phrase from recent actions must be rejected");

        // 3. A new dialogue topic must be accepted
        AgentActionRecord freshTalk = new AgentActionRecord(
                innocentGuest.getId(), ActionType.TALK, null, bystanderInRoom.getId(),
                "The emergency generator failed 20 minutes ago", null, null, null, null,
                "I ask about the electrical circuit breakers in the reactor room.", null
        );
        assertTrue(agentService.validateAction(freshTalk, innocentGuest, repository, List.of(bystanderInRoom)),
                "Fresh, non-repeating TALK action must be accepted");
    }
}
