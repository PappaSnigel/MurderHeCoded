package com.murderhecoded.llm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

@Component
@Slf4j
public class LlmExecutor {
    private static final int DEFAULT_TECHNICAL_RETRIES = 3;
    private static final int DEFAULT_SEMANTIC_ATTEMPTS = 3;

    private Consumer<String> onLogEvent;

    public void setOnLogEvent(Consumer<String> onLogEvent) {
        this.onLogEvent = onLogEvent;
    }

    private void emitLog(String message) {
        log.info(message);
        if (onLogEvent != null) {
            try {
                if (javafx.application.Platform.isFxApplicationThread()) {
                    onLogEvent.accept(message);
                } else {
                    try {
                        javafx.application.Platform.runLater(() -> onLogEvent.accept(message));
                    } catch (IllegalStateException e) {
                        onLogEvent.accept(message);
                    }
                }
            } catch (Exception e) {
                log.debug("Error delivering log event: {}", e.getMessage());
            }
        }
    }

    /**
     * Standardkörning för agenter utan semantisk validering (bara tekniska retries).
     */
    public <T> T execute(Supplier<T> action) {
        return execute(action, result -> result != null, "Resultatet var null",
                DEFAULT_SEMANTIC_ATTEMPTS, DEFAULT_TECHNICAL_RETRIES);
    }

    /**
     * Körning med både semantiska försök och tekniska retries.
     *
     * @param action                    Anropet mot ChatClient
     * @param validator                 Kontroll av innehållet (t.ex. rätt antal rum)
     * @param validationFailureMessage  Loggmeddelande om innehållet inte stämmer
     * @param maxSemanticAttempts       Antal försök att få rätt innehåll (t.ex. 3)
     * @param maxTechnicalRetries       Antal retries om Ollama/nätverket kraschar per försök (t.ex. 3)
     */
    public <T> T execute(
            Supplier<T> action,
            Predicate<T> validator,
            String validationFailureMessage,
            int maxSemanticAttempts,
            int maxTechnicalRetries
    ) {
        T lastResult = null;
        // YTTRE LOOP: Semantiska försök (om innehållet inte blev som tänkt)
        for (int semanticAttempt = 1; semanticAttempt <= maxSemanticAttempts; semanticAttempt++) {
            log.info("LlmExecutor: Startar semantiskt försök {}/{}", semanticAttempt, maxSemanticAttempts);
            emitLog(String.format("[LlmExecutor] Startar semantiskt försök %d/%d...", semanticAttempt, maxSemanticAttempts));

            // INRE ANROP: Utför anropet med tekniska retries om LLM kraschar
            T result = executeWithTechnicalRetry(action, maxTechnicalRetries);
            lastResult = result;

            // KONTROLL: Blev innehållet som tänkt?
            if (result != null && validator.test(result)) {
                log.info("LlmExecutor: Semantisk validering godkänd vid försök {}!", semanticAttempt);
                emitLog(String.format("[LlmExecutor] Semantisk validering godkänd vid försök %d!", semanticAttempt));
                return result;
            }

            log.warn("LlmExecutor: Försök {} underkänt semantiskt: {}. Resultat: {}",
                    semanticAttempt, validationFailureMessage, formatResultSummary(result));
            emitLog(String.format("[LlmExecutor Varning] Försök %d/%d underkänt semantiskt: %s",
                    semanticAttempt, maxSemanticAttempts, validationFailureMessage));

            if (semanticAttempt < maxSemanticAttempts) {
                sleepQuietly(Duration.ofMillis(500)); // Kort paus innan ny prompt körs
            }
        }

        String failMsg = "Misslyckades semantiskt efter " + maxSemanticAttempts
                + " försök. Sista fel: " + validationFailureMessage
                + ". Sista resultat: " + formatResultSummary(lastResult);
        emitLog("[LlmExecutor KRASCH] " + failMsg);
        throw new IllegalStateException(failMsg);
    }

    private String formatResultSummary(Object result) {
        if (result == null) {
            return "null";
        }
        String str = result.toString();
        if (str.length() > 300) {
            return str.substring(0, 300) + "... [truncated, total " + str.length() + " chars]";
        }
        return str;
    }

    /**
     * Inre teknisk retry-loop som skyddar mot nätverksfel, Ollama-krascher och JSON-fel.
     */
    private <T> T executeWithTechnicalRetry(Supplier<T> action, int maxAttempts) {
        Exception lastException = null;
        Duration backoff = Duration.ofMillis(1000);

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            if (Thread.currentThread().isInterrupted()) {
                throw new IllegalStateException("LLM-anrop avbröts via interrupt");
            }

            try {
                return action.get(); // Lyckat anrop utan teknisk krasch
            } catch (Exception ex) {
                lastException = ex;
                String errMsg = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
                if (ex.getCause() != null && ex.getCause().getMessage() != null) {
                    errMsg += " (Orsak: " + ex.getCause().getMessage() + ")";
                }
                log.warn("Tekniskt LLM-fel (försök {}/{}): {}", attempt, maxAttempts, errMsg, ex);
                emitLog(String.format("[LlmExecutor Tekniskt Fel] Försök %d/%d misslyckades: %s", attempt, maxAttempts, errMsg));

                if (attempt < maxAttempts) {
                    sleepQuietly(backoff);
                    backoff = backoff.multipliedBy(2); // Exponentiell backoff
                }
            }
        }

        String crashMsg = String.format("LLM kraschade tekniskt efter %d försök: %s",
                maxAttempts, lastException != null ? lastException.getMessage() : "Okänt fel");
        emitLog("[LlmExecutor KRASCH] " + crashMsg);
        throw new RuntimeException("LLM kraschade tekniskt efter " + maxAttempts + " försök", lastException);
    }

    private void sleepQuietly(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Väntan avbruten", ie);
        }
    }
}