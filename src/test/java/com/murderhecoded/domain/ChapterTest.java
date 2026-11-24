package com.murderhecoded.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChapterTest {

    @Test
    @DisplayName("hasProse returns true for normal text and false for null, blank, or placeholder log")
    void testHasProse() {
        Chapter normal = new Chapter("The night was dark and full of shadows.", true, false, 1, 1, "Hall");
        assertTrue(normal.hasProse());

        Chapter nullText = new Chapter(null, true, false, 1, 1, "Hall");
        assertFalse(nullText.hasProse());

        Chapter blankText = new Chapter("   \n\t  ", true, false, 1, 1, "Hall");
        assertFalse(blankText.hasProse());

        Chapter logFallback = new Chapter("[HÄNDELSELOGG (Prosa avstängd)]\n\nEvent details", true, false, 1, 1, "Hall");
        assertFalse(logFallback.hasProse());
    }

    @Test
    @DisplayName("wordCount returns correct word counts for various texts")
    void testWordCount() {
        Chapter chapter = new Chapter("One two three four five.", true, false, 1, 1, "Hall");
        assertEquals(5, chapter.wordCount());

        Chapter multiLine = new Chapter("  Word1   Word2 \n Word3 \t Word4  ", true, false, 1, 1, "Hall");
        assertEquals(4, multiLine.wordCount());

        Chapter empty = new Chapter("", true, false, 1, 1, "Hall");
        assertEquals(0, empty.wordCount());

        Chapter log = new Chapter("[HÄNDELSELOGG (Prosa avstängd)]\n\nWord", true, false, 1, 1, "Hall");
        assertEquals(0, log.wordCount());
    }
}
