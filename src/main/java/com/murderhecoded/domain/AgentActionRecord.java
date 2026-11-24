package com.murderhecoded.domain;

public record AgentActionRecord(
    String actorId,
    ActionType actionType,
    String targetLocationId,
    String targetAgentId,
    String sharedKnowledge,
    String weaponItemId,
    String traceDescription,
    String targetItemId,
    String interaction,
    String details,
    String newKnowledgeFact
) {
    public Action toAction() {
        Action a = new Action();
        a.setActorId(actorId);
        a.setActionType(actionType != null ? actionType : ActionType.OBSERVE);
        a.setTargetLocationId(targetLocationId);
        a.setTargetAgentId(targetAgentId);
        a.setSharedKnowledge(sharedKnowledge);
        a.setWeaponItemId(weaponItemId);
        a.setTraceDescription(traceDescription);
        a.setTargetItemId(targetItemId);
        a.setInteraction(interaction);
        a.setDetails(details);
        a.setNewKnowledgeFact(newKnowledgeFact);
        return a;
    }

    public static AgentActionRecord from(Action a) {
        if (a == null) {
            return null;
        }
        return new AgentActionRecord(
            a.getActorId(),
            a.getActionType(),
            a.getTargetLocationId(),
            a.getTargetAgentId(),
            a.getSharedKnowledge(),
            a.getWeaponItemId(),
            a.getTraceDescription(),
            a.getTargetItemId(),
            a.getInteraction(),
            a.getDetails(),
            a.getNewKnowledgeFact()
        );
    }

    public String toSummaryString() {
        StringBuilder sb = new StringBuilder();
        sb.append(actionType != null ? actionType : "UNKNOWN_TYPE");
        java.util.List<String> parts = new java.util.ArrayList<>();
        if (targetAgentId != null && !targetAgentId.isBlank()) {
            parts.add("targetAgent=" + targetAgentId);
        }
        if (targetLocationId != null && !targetLocationId.isBlank()) {
            parts.add("targetLocation=" + targetLocationId);
        }
        if (targetItemId != null && !targetItemId.isBlank()) {
            parts.add("targetItem=" + targetItemId);
        }
        if (interaction != null && !interaction.isBlank()) {
            parts.add("interaction=" + interaction);
        }
        if (weaponItemId != null && !weaponItemId.isBlank()) {
            parts.add("weapon=" + weaponItemId);
        }
        if (details != null && !details.isBlank()) {
            String detailsSnippet = details.trim();
            if (detailsSnippet.length() > 60) {
                detailsSnippet = detailsSnippet.substring(0, 57) + "...";
            }
            parts.add("details=\"" + detailsSnippet + "\"");
        }
        if (!parts.isEmpty()) {
            sb.append(" [").append(String.join(", ", parts)).append("]");
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return toSummaryString();
    }
}
