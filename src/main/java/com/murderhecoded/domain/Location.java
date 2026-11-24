package com.murderhecoded.domain;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
public class Location {
    private String id;
    private String name;
    private String description;
    private List<EnvironmentTrace> traces = new ArrayList<>();
    private List<Agent> agents = new ArrayList<>();
    private List<Item> items = new ArrayList<>();

    public Location(LocationRecord record) {
        if (record != null) {
            this.id = record.id();
            this.name = record.name();
            this.description = record.description();
        }
    }

    public String id() {
        return id;
    }

    public String description() {
        return description;
    }

    public LocationSnapshot snapshot() {
        List<EnvironmentTraceSnapshot> traceSnaps = traces != null
                ? traces.stream().map(EnvironmentTrace::snapshot).collect(Collectors.toList())
                : new ArrayList<>();
        return new LocationSnapshot(id, name, description, traceSnaps);
    }

    public LocationRecord toRecord() {
        return LocationRecord.from(this);
    }

    public static Location fromRecord(LocationRecord record) {
        return record != null ? record.toLocation() : null;
    }
}