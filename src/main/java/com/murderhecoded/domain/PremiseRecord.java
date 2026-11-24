package com.murderhecoded.domain;

public record PremiseRecord(
        String title,
        String premise,
        int locations,
        int items_per_location,
        int characters
) {
}
