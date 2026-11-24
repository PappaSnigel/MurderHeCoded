package com.murderhecoded.domain;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

@Data
public class Action {
    private String actorId;
    @JsonAlias({"action", "type", "action_type"})
    private ActionType actionType = ActionType.OBSERVE;
    private String targetLocationId;
    private String targetAgentId;
    private String sharedKnowledge;
    private String weaponItemId;
    private String details;
    private String traceDescription;
    private String targetItemId;
    private String interaction;
    private String newKnowledgeFact;

    public ActionType getActionType() {
        return actionType != null ? actionType : ActionType.OBSERVE;
    }
    
    public boolean hasNewKnowledge() {
        return newKnowledgeFact != null && !newKnowledgeFact.trim().isEmpty() && !"None".equalsIgnoreCase(newKnowledgeFact.trim());
    }
}