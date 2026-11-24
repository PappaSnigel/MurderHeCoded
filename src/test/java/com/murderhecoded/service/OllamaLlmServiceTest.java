package com.murderhecoded.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.murderhecoded.llm.LlmRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.*;

class OllamaLlmServiceTest {

    private OllamaLlmService service;
    private ObjectMapper objectMapper;
    private Preferences testPrefs;

    @BeforeEach
    void setUp() throws Exception {
        testPrefs = Preferences.userRoot().node("com/murderhecoded/test_service");
        testPrefs.clear();
        objectMapper = new ObjectMapper();
        service = new OllamaLlmService(objectMapper, "http://localhost:11434", testPrefs);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (testPrefs != null) {
            try {
                testPrefs.clear();
                testPrefs.removeNode();
            } catch (Exception ignored) {
            }
        }
    }

    @Test
    void testInitialRoleDefaults() {
        assertNotNull(service.getBaseUrl());
        assertEquals("http://localhost:11434", service.getBaseUrl());

        assertNotNull(service.getModel(LlmRole.WORLD));
        assertNotNull(service.getModel(LlmRole.AGENT));
        assertNotNull(service.getModel(LlmRole.PROSE));

        assertEquals(0.3, service.getTemperature(LlmRole.WORLD), 0.01);
        assertEquals(0.2, service.getTemperature(LlmRole.AGENT), 0.01);
        assertEquals(0.8, service.getTemperature(LlmRole.PROSE), 0.01);

        assertEquals(4096, service.getNumCtx(LlmRole.WORLD));
        assertEquals(4096, service.getNumCtx(LlmRole.AGENT));
        assertEquals(8192, service.getNumCtx(LlmRole.PROSE));
    }

    @Test
    void testConfigureThreeDistinctModels() {
        service.configure(
                "http://test-server:11434",
                "llama3.3:70b",
                "qwen2.5:7b",
                "command-r:35b",
                0.35,
                0.15,
                0.85,
                8192,
                4096,
                16384
        );

        assertEquals("http://test-server:11434", service.getBaseUrl());
        assertEquals("llama3.3:70b", service.getModel(LlmRole.WORLD));
        assertEquals("qwen2.5:7b", service.getModel(LlmRole.AGENT));
        assertEquals("command-r:35b", service.getModel(LlmRole.PROSE));

        assertEquals(0.35, service.getTemperature(LlmRole.WORLD), 0.001);
        assertEquals(0.15, service.getTemperature(LlmRole.AGENT), 0.001);
        assertEquals(0.85, service.getTemperature(LlmRole.PROSE), 0.001);

        assertEquals(8192, service.getNumCtx(LlmRole.WORLD));
        assertEquals(4096, service.getNumCtx(LlmRole.AGENT));
        assertEquals(16384, service.getNumCtx(LlmRole.PROSE));
    }

    @Test
    void testLegacyConfigureSetsAllThreeModels() {
        service.configure("http://legacy:11434", "mistral:7b");

        assertEquals("http://legacy:11434", service.getBaseUrl());
        assertEquals("mistral:7b", service.getModel(LlmRole.WORLD));
        assertEquals("mistral:7b", service.getModel(LlmRole.AGENT));
        assertEquals("mistral:7b", service.getModel(LlmRole.PROSE));
        assertEquals("mistral:7b", service.getModel());
    }

    @Test
    void testGetModelNullRoleFallback() {
        service.configure("http://localhost:11434", "world-m", "agent-m", "prose-m");
        assertEquals("agent-m", service.getModel(null));
        assertEquals(service.getAgentTemperature(), service.getTemperature(null), 0.01);
        assertEquals(service.getAgentNumCtx(), service.getNumCtx(null));
    }

    @Test
    void testTestConnectionBatchMissingModels() {
        String result = service.testConnectionBatch("http://invalid-url:9999", "", null, "   ");
        assertTrue(result.contains("No model selected"));
    }

    @Test
    void testGetChatClientNotNull() {
        org.springframework.ai.chat.client.ChatClient client = service.getChatClient(LlmRole.WORLD);
        assertNotNull(client);

        org.springframework.http.client.SimpleClientHttpRequestFactory rf = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(java.time.Duration.ofMinutes(5));
        rf.setReadTimeout(java.time.Duration.ofMinutes(10));
        org.springframework.web.client.RestClient.Builder builder = org.springframework.web.client.RestClient.builder().requestFactory(rf);
        org.springframework.ai.ollama.api.OllamaApi customApi = new org.springframework.ai.ollama.api.OllamaApi(
                "http://localhost:11434", builder, org.springframework.web.reactive.function.client.WebClient.builder());
        assertNotNull(customApi);
    }
}
