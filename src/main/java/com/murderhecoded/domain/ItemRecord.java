package com.murderhecoded.domain;

public record ItemRecord(
    String id,
    String name,
    ItemType type,
    String currentLocationId,
    boolean movable,
    boolean hidden
) {
    public Item toItem() {
        Item item = new Item();
        item.setId(id);
        item.setName(name);
        item.setType(type != null ? type : ItemType.CLUE);
        item.setCurrentLocationId(currentLocationId);
        item.setMovable(movable);
        item.setHidden(hidden);
        return item;
    }

    public static ItemRecord from(Item item) {
        if (item == null) {
            return null;
        }
        return new ItemRecord(
            item.getId(),
            item.getName(),
            item.getType(),
            item.getCurrentLocationId(),
            item.isMovable(),
            item.isHidden()
        );
    }
}
