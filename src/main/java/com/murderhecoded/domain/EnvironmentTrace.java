package com.murderhecoded.domain;

import lombok.Data;

@Data
public class EnvironmentTrace {
    private String id;
    private String description;
    private String creatorId;
    private int timeStepCreated;
    
    public EnvironmentTraceSnapshot snapshot() {
        return new EnvironmentTraceSnapshot(id, description, creatorId, timeStepCreated);
    }
    
    public static EnvironmentTrace create(String description, String creatorId, int timeStepCreated) {
        EnvironmentTrace trace = new EnvironmentTrace();
        trace.setId("trace_" + java.util.UUID.randomUUID().toString().substring(0, 8));
        trace.setDescription(description);
        trace.setCreatorId(creatorId);
        trace.setTimeStepCreated(timeStepCreated);
        return trace;
    }
}