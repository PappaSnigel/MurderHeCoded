package com.murderhecoded.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PresenterRecordsTest {

    @Test
    void testItemTreeItemData() {
        ItemDetailPresenter.ItemTreeItemData category = new ItemDetailPresenter.ItemTreeItemData("🗡️ Weapons", null, true);
        assertTrue(category.isCategory());
        assertNull(category.itemId());
        assertEquals("🗡️ Weapons", category.toString());
        assertEquals("🗡️ Weapons", category.label());

        ItemDetailPresenter.ItemTreeItemData leaf = new ItemDetailPresenter.ItemTreeItemData("Poisoned Dagger [📍 Library]", "item_1", false);
        assertFalse(leaf.isCategory());
        assertEquals("item_1", leaf.itemId());
        assertEquals("Poisoned Dagger [📍 Library]", leaf.toString());
    }

    @Test
    void testLocationTreeItemData() {
        LocationDetailPresenter.LocationTreeItemData category = new LocationDetailPresenter.LocationTreeItemData("Locations", null, true);
        assertTrue(category.isCategory());
        assertNull(category.locationId());
        assertEquals("Locations", category.toString());

        LocationDetailPresenter.LocationTreeItemData leaf = new LocationDetailPresenter.LocationTreeItemData("The Library [👥 2 | 📦 3]", "room_1", false);
        assertFalse(leaf.isCategory());
        assertEquals("room_1", leaf.locationId());
        assertEquals("The Library [👥 2 | 📦 3]", leaf.toString());
    }
}
