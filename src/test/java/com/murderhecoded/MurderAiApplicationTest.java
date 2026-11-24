package com.murderhecoded;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class MurderAiApplicationTest {

    @org.springframework.beans.factory.annotation.Autowired
    private com.murderhecoded.ui.MainController mainController;

    @Test
    void contextLoads() {
        // Verifies that all Spring beans and dependencies instantiate successfully
        org.junit.jupiter.api.Assertions.assertTrue(true, "Context should load");
    }

    @Test
    void testAppVersionResolution() {
        org.junit.jupiter.api.Assertions.assertNotNull(mainController.getAppVersion());
        org.junit.jupiter.api.Assertions.assertFalse(mainController.getAppVersion().isBlank());
        org.junit.jupiter.api.Assertions.assertEquals("0.0.1-SNAPSHOT", mainController.getAppVersion());
    }
}
