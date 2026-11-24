package com.murderhecoded.domain;

import java.util.List;

public record LocationSnapshot(String id, String name, String description, List<EnvironmentTraceSnapshot> traces) {}