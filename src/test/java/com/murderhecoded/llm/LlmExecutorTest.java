package com.murderhecoded.llm;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class LlmExecutorTest {

    private final LlmExecutor llmExecutor = new LlmExecutor();

    @Test
    void testExecuteSuccessOnFirstAttempt() {
        String result = llmExecutor.execute(() -> "Hello", s -> s.equals("Hello"), "Failed", 3, 1);
        assertEquals("Hello", result);
    }

    @Test
    void testExecuteSucceedsOnSecondSemanticAttempt() {
        AtomicInteger attempts = new AtomicInteger(0);
        String result = llmExecutor.execute(
                () -> attempts.incrementAndGet() == 1 ? "invalid" : "valid",
                s -> s.equals("valid"),
                "Value was not valid",
                3,
                1
        );
        assertEquals("valid", result);
        assertEquals(2, attempts.get());
    }

    @Test
    void testExecuteFailsAndIncludesTruncatedResultInException() {
        String longPayload = "A".repeat(500);
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                llmExecutor.execute(
                        () -> longPayload,
                        s -> false,
                        "Payload not accepted",
                        2,
                        1
                )
        );

        assertTrue(ex.getMessage().contains("Misslyckades semantiskt efter 2 försök"));
        assertTrue(ex.getMessage().contains("Sista fel: Payload not accepted"));
        assertTrue(ex.getMessage().contains("Sista resultat: "));
        assertTrue(ex.getMessage().contains("[truncated, total 500 chars]"));
    }

    @Test
    void testExecuteFailsWithNullResultInException() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                llmExecutor.execute(
                        () -> null,
                        s -> false,
                        "Null rejected",
                        1,
                        1
                )
        );

        assertTrue(ex.getMessage().contains("Sista resultat: null"));
    }
}
