package com.murderhecoded.domain;

public record LocationRecord(
    String id,
    String name,
    String description
) {
    public Location toLocation() {
        return new Location(this);
    }

    public static LocationRecord from(Location location) {
        if (location == null) {
            return null;
        }
        return new LocationRecord(
            location.getId(),
            location.getName(),
            location.getDescription()
        );
    }
}
