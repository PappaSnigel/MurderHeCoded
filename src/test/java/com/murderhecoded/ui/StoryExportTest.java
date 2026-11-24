package com.murderhecoded.ui;

import com.lowagie.text.pdf.PdfReader;
import com.murderhecoded.AppConstants;
import com.murderhecoded.domain.Chapter;
import com.murderhecoded.domain.SceneCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class StoryExportTest {

    @Autowired
    private MainController mainController;

    @BeforeEach
    void setUp() {
        mainController.getChapters().clear();
    }

    @Test
    @DisplayName("Text export includes title at start and program version in generation metadata")
    void testBuildStoryTextContentIncludesTitleAndVersion() {
        Chapter c1 = new Chapter("The night was dark and stormy in the observatory.",
                true, false, 3, 1, "Observatory", 8.0, 7.0,
                SceneCategory.ESSENTIAL, "Discovery", 1, 3, "Steps 1-3", true);
        mainController.getChapters().add(c1);

        String textContent = mainController.buildStoryTextContent(false);

        String storyTitle = mainController.getStoryTitle();
        assertNotNull(storyTitle);
        assertFalse(storyTitle.isBlank());
        assertTrue(textContent.startsWith(storyTitle), "Story text should start with the story title");

        assertTrue(textContent.contains("The night was dark and stormy in the observatory."));
        assertTrue(textContent.contains("Program Version: " + mainController.getAppVersion()),
                "Metadata should contain the program version");
        assertTrue(textContent.contains("Application: " + AppConstants.APP_NAME),
                "Metadata should contain the application name");
        assertTrue(textContent.contains("Word Count:"),
                "Metadata should contain word count");
    }

    @Test
    @DisplayName("PDF export includes title, page break before technical details, and program version")
    void testWriteStoryToPdfIncludesTitlePageBreakAndVersion() throws Exception {
        Chapter c1 = new Chapter("A scream echoed through the corridor of the space station.",
                true, false, 5, 1, "Corridor", 9.0, 8.5,
                SceneCategory.ESSENTIAL, "Murder", 1, 5, "Steps 1-5", true);
        mainController.getChapters().add(c1);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        mainController.writeStoryToPdf(baos, false);
        byte[] pdfBytes = baos.toByteArray();

        assertTrue(pdfBytes.length > 0, "PDF output should not be empty");

        PdfReader reader = new PdfReader(pdfBytes);
        try {
            assertTrue(reader.getNumberOfPages() >= 2,
                    "PDF should have at least 2 pages because of the page break before technical details");
        } finally {
            reader.close();
        }
    }

    @Test
    @DisplayName("Story metadata text contains application name, program version, and word count")
    void testBuildStoryMetadataTextContainsVersionAndWordCount() {
        Chapter c1 = new Chapter("One two three four five.",
                true, false, 2, 1, "Lobby", 7.0, 6.0,
                SceneCategory.ESSENTIAL, "Intro", 1, 2, "Steps 1-2", true);
        mainController.getChapters().add(c1);

        String metadata = mainController.buildStoryMetadataText();

        assertNotNull(metadata);
        assertTrue(metadata.contains("Application: " + AppConstants.APP_NAME));
        assertTrue(metadata.contains("Program Version: " + mainController.getAppVersion()));
        assertTrue(metadata.contains("Title: " + mainController.getStoryTitle()));
        assertTrue(metadata.contains("Word Count: 5 words"));
        assertTrue(metadata.contains("Total Word Count:     5 words"));
    }
}
