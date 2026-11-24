package com.murderhecoded.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.murderhecoded.domain.Action;
import com.murderhecoded.domain.ActionType;
import com.murderhecoded.domain.SetupResponse;
import com.murderhecoded.llm.LlmRole;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;
import com.murderhecoded.domain.AgentActionRecord;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Service
@Slf4j
public class OllamaLlmService {

    private WebClient webClient;
    private final ObjectMapper objectMapper;
    private String model;
    private String baseUrl;
    private String worldModel;
    private String agentModel;
    private String proseModel;
    private String directorModel;
    private double worldTemperature;
    private double agentTemperature;
    private double proseTemperature;
    private double directorTemperature;
    private int worldNumCtx;
    private int agentNumCtx;
    private int proseNumCtx;
    private int directorNumCtx;
    private final Preferences prefs;

    @Autowired
    public OllamaLlmService(ObjectMapper objectMapper,
                            @Value("${app.ollama.url:}") String propertyUrl) {
        this(objectMapper, propertyUrl, Preferences.userNodeForPackage(OllamaLlmService.class));
    }

    public OllamaLlmService(ObjectMapper objectMapper, String propertyUrl, Preferences prefs) {
        this.objectMapper = objectMapper;
        this.prefs = (prefs != null) ? prefs : Preferences.userNodeForPackage(OllamaLlmService.class);

        // Hämta inställningar (property -> preferences -> fallback)
        this.baseUrl = (propertyUrl != null && !propertyUrl.isEmpty()) ? propertyUrl : this.prefs.get("baseUrl", "http://localhost:11434");
        String fallbackModel = this.prefs.get("model", "llama3.1:8b");
        this.worldModel = this.prefs.get(LlmRole.WORLD.getPrefKey(), fallbackModel);
        this.agentModel = this.prefs.get(LlmRole.AGENT.getPrefKey(), fallbackModel);
        this.proseModel = this.prefs.get(LlmRole.PROSE.getPrefKey(), fallbackModel);
        this.directorModel = this.prefs.get(LlmRole.DIRECTOR.getPrefKey(), this.proseModel != null ? this.proseModel : fallbackModel);
        this.model = this.agentModel;

        this.worldTemperature = this.prefs.getDouble("temp.world", LlmRole.WORLD.getDefaultTemperature());
        this.agentTemperature = this.prefs.getDouble("temp.agent", LlmRole.AGENT.getDefaultTemperature());
        this.proseTemperature = this.prefs.getDouble("temp.prose", LlmRole.PROSE.getDefaultTemperature());
        this.directorTemperature = this.prefs.getDouble("temp.director", LlmRole.DIRECTOR.getDefaultTemperature());

        this.worldNumCtx = this.prefs.getInt("numctx.world", LlmRole.WORLD.getDefaultNumCtx());
        this.agentNumCtx = this.prefs.getInt("numctx.agent", LlmRole.AGENT.getDefaultNumCtx());
        this.proseNumCtx = this.prefs.getInt("numctx.prose", LlmRole.PROSE.getDefaultNumCtx());
        this.directorNumCtx = this.prefs.getInt("numctx.director", LlmRole.DIRECTOR.getDefaultNumCtx());

        buildWebClient();
    }

    public void configure(String baseUrl, String model) {
        configure(baseUrl, model, model, model);
    }

    public void configure(String baseUrl, String worldModel, String agentModel, String proseModel) {
        configure(baseUrl, worldModel, agentModel, proseModel,
                this.worldTemperature, this.agentTemperature, this.proseTemperature,
                this.worldNumCtx, this.agentNumCtx, this.proseNumCtx);
    }

    public void configure(String baseUrl, String worldModel, String agentModel, String proseModel,
                          double worldTemp, double agentTemp, double proseTemp,
                          int worldCtx, int agentCtx, int proseCtx) {
        this.baseUrl = baseUrl;
        this.worldModel = (worldModel != null && !worldModel.isBlank()) ? worldModel : this.worldModel;
        this.agentModel = (agentModel != null && !agentModel.isBlank()) ? agentModel : this.agentModel;
        this.proseModel = (proseModel != null && !proseModel.isBlank()) ? proseModel : this.proseModel;
        this.model = this.agentModel;

        this.worldTemperature = worldTemp;
        this.agentTemperature = agentTemp;
        this.proseTemperature = proseTemp;

        this.worldNumCtx = worldCtx;
        this.agentNumCtx = agentCtx;
        this.proseNumCtx = proseCtx;

        if (baseUrl != null) {
            prefs.put("baseUrl", baseUrl);
        }
        prefs.put("model", this.model);
        prefs.put(LlmRole.WORLD.getPrefKey(), this.worldModel);
        prefs.put(LlmRole.AGENT.getPrefKey(), this.agentModel);
        prefs.put(LlmRole.PROSE.getPrefKey(), this.proseModel);

        prefs.putDouble("temp.world", worldTemp);
        prefs.putDouble("temp.agent", agentTemp);
        prefs.putDouble("temp.prose", proseTemp);

        prefs.putInt("numctx.world", worldCtx);
        prefs.putInt("numctx.agent", agentCtx);
        prefs.putInt("numctx.prose", proseCtx);

        buildWebClient();
    }

    public ChatClient getChatClient() {
        return getChatClient(LlmRole.AGENT);
    }

    public ChatClient getChatClient(LlmRole role) {
        String selectedModel = getModel(role);
        double temp = getTemperature(role);
        int numCtx = getNumCtx(role);

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMinutes(5));
        requestFactory.setReadTimeout(Duration.ofMinutes(10));
        RestClient.Builder restClientBuilder = RestClient.builder().requestFactory(requestFactory);
        WebClient.Builder webClientBuilder = WebClient.builder();

        OllamaApi ollamaApi = new OllamaApi(this.baseUrl, restClientBuilder, webClientBuilder);

        OllamaOptions options = OllamaOptions.builder()
                .withModel(selectedModel)
                .withTemperature(temp)
                .withNumCtx(numCtx)
                .withNumPredict(role == LlmRole.AGENT ? 2048 : 8192)
                .build();

        OllamaChatModel chatModel = OllamaChatModel.builder()
                .withOllamaApi(ollamaApi)
                .withDefaultOptions(options)
                .build();

        return ChatClient.builder(chatModel).build();
    }

    public String getModel(LlmRole role) {
        if (role == null) {
            return this.agentModel != null ? this.agentModel : this.model;
        }
        return switch (role) {
            case WORLD -> this.worldModel != null ? this.worldModel : this.model;
            case AGENT -> this.agentModel != null ? this.agentModel : this.model;
            case PROSE -> this.proseModel != null ? this.proseModel : this.model;
            case DIRECTOR -> (this.directorModel != null && !this.directorModel.isBlank())
                    ? this.directorModel
                    : (this.proseModel != null && !this.proseModel.isBlank() ? this.proseModel : this.model);
        };
    }

    public double getTemperature(LlmRole role) {
        if (role == null) {
            return this.agentTemperature;
        }
        return switch (role) {
            case WORLD -> this.worldTemperature;
            case AGENT -> this.agentTemperature;
            case PROSE -> this.proseTemperature;
            case DIRECTOR -> this.directorTemperature;
        };
    }

    public int getNumCtx(LlmRole role) {
        if (role == null) {
            return this.agentNumCtx;
        }
        return switch (role) {
            case WORLD -> this.worldNumCtx;
            case AGENT -> this.agentNumCtx;
            case PROSE -> this.proseNumCtx;
            case DIRECTOR -> this.directorNumCtx;
        };
    }

    public String getWorldModel() {
        return this.worldModel;
    }

    public String getAgentModel() {
        return this.agentModel;
    }

    public String getProseModel() {
        return this.proseModel;
    }

    public double getWorldTemperature() {
        return this.worldTemperature;
    }

    public double getAgentTemperature() {
        return this.agentTemperature;
    }

    public double getProseTemperature() {
        return this.proseTemperature;
    }

    public int getWorldNumCtx() {
        return this.worldNumCtx;
    }

    public int getAgentNumCtx() {
        return this.agentNumCtx;
    }

    public int getProseNumCtx() {
        return this.proseNumCtx;
    }

    private void buildWebClient() {
        reactor.netty.http.client.HttpClient httpClient = reactor.netty.http.client.HttpClient.create()
                .responseTimeout(Duration.ofMinutes(10))
                .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, 300000);

        this.webClient = WebClient.builder()
                .baseUrl(this.baseUrl)
                .clientConnector(new org.springframework.http.client.reactive.ReactorClientHttpConnector(httpClient))
                .build();
    }

    public String getBaseUrl() {
        return this.baseUrl;
    }

    public String getModel() {
        return this.model;
    }

    public List<String> getAvailableModels() {
        return getAvailableModels(this.baseUrl);
    }

    @SuppressWarnings("unchecked")
    public List<String> getAvailableModels(String customUrl) {
        try {
            WebClient client = (customUrl == null || customUrl.isEmpty()) ? this.webClient : WebClient.builder().baseUrl(customUrl).build();
            Map<String, Object> response = client.get()
                    .uri("/api/tags")
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            List<String> models = new ArrayList<>();
            if (response != null && response.containsKey("models")) {
                List<Map<String, Object>> modelsList = (List<Map<String, Object>>) response.get("models");
                for (Map<String, Object> m : modelsList) {
                    String name = (String) m.get("name");
                    Object sizeObj = m.get("size");
                    if (sizeObj instanceof Number num) {
                        double sizeGb = num.doubleValue() / 1_073_741_824.0;
                        name = String.format("%s (%.1f GB)", name, sizeGb);
                    }
                    models.add(name);
                }
            }
            return models;
        } catch (WebClientException e) {
            if (log.isErrorEnabled()) {
                log.error("Network/client error fetching models: {}", e.getMessage());
            }
            return new ArrayList<>();
        } catch (Exception e) {
            if (log.isErrorEnabled()) {
                log.error("Error fetching models: {}", e.getMessage());
            }
            return new ArrayList<>();
        }
    }

    public String generateText(String prompt) {
        return generateText(prompt, LlmRole.AGENT);
    }

    public String generateText(String prompt, LlmRole role) {
        if (Thread.currentThread().isInterrupted()) {
            return "";
        }
        String selectedModel = getModel(role);
        try {
            String content = getChatClient(role).prompt().user(prompt).call().content();
            if (content != null && !content.isBlank()) {
                return content;
            }
        } catch (Exception e) {
            log.warn("Spring AI generateText failed for model {}, trying WebClient: {}", selectedModel, e.getMessage());
        }
        try {
            Map<String, Object> request = Map.of(
                    "model", selectedModel,
                    "prompt", prompt,
                    "stream", false,
                    "options", Map.of(
                            "temperature", getTemperature(role),
                            "num_ctx", getNumCtx(role),
                            "num_predict", (role == LlmRole.PROSE || role == LlmRole.DIRECTOR) ? 4096 : 2048
                    )
            );

            Map response = webClient.post()
                    .uri("/api/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response != null && response.containsKey("response")) {
                return (String) response.get("response");
            }
            return "";
        } catch (WebClientException e) {
            if (log.isErrorEnabled()) {
                log.error("Network/client error calling LLM (model {}): {}", selectedModel, e.getMessage());
            }
            return "";
        } catch (IllegalStateException e) {
            if (Thread.currentThread().isInterrupted()) {
                log.info("LLM generateText cancelled via interrupt");
                return "";
            }
            if (log.isErrorEnabled()) {
                log.error("IllegalStateException calling LLM (model {}): {}", selectedModel, e.getMessage());
            }
            return "";
        }
    }



    public SetupResponse generateSetup(String prompt) {
        if (Thread.currentThread().isInterrupted()) {
            return null;
        }
        try {
            Map<String, Object> request = Map.of(
                    "model", this.model,
                    "prompt", prompt + "\nRespond ONLY with valid JSON using the exact schema provided. Do not include markdown formatting or extra text.",
                    "stream", false,
                    "format", "json"
            );

            Map response = webClient.post()
                    .uri("/api/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response != null && response.containsKey("response")) {
                String jsonStr = (String) response.get("response");
                String cleaned = cleanJson(jsonStr);
                return objectMapper.readValue(cleaned, SetupResponse.class);
            }
        } catch (WebClientException | JsonProcessingException e) {
            if (log.isErrorEnabled()) {
                String msg = e.getMessage();
                String safeMsg = msg.substring(0, Math.min(msg.length(), 128));
                log.error("Error generating setup: {}", safeMsg);
                return null;
            }
        } catch (IllegalStateException e) {
            if (Thread.currentThread().isInterrupted()) {
                log.info("LLM generateSetup aborted due to interrupt");
                return null;

            }
            if (log.isErrorEnabled()) {
                log.error("Error generating setup: {}", e.getMessage(), e);
                return null;

            }
        }
        return null;
    }

    public Action generateSingleAction(String prompt) {
        if (Thread.currentThread().isInterrupted()) {
            return null;
        }
        try {
            AgentActionRecord record = getChatClient().prompt()
                    .user(prompt)
                    .call()
                    .entity(AgentActionRecord.class);
            if (record != null) {
                return record.toAction();
            }
        } catch (Exception e) {
            log.warn("Spring AI generateAgentAction failed, trying WebClient: {}", e.getMessage());
        }
        try {
            Map<String, Object> request = Map.of(
                    "model", this.model,
                    "prompt", prompt + "\nRespond ONLY with valid JSON using the exact schema provided. Do not include markdown formatting or extra text.",
                    "stream", false,
                    "format", "json"
            );

            Map response = webClient.post()
                    .uri("/api/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response != null && response.containsKey("response")) {
                String jsonStr = (String) response.get("response");
                return parseSingleAction(jsonStr);
            }
        } catch (WebClientException e) {
            if (log.isErrorEnabled()) {
                log.error("Network error generating single action: {}", e.getMessage());
            }
        } catch (IllegalStateException e) {
            if (Thread.currentThread().isInterrupted()) {
                log.info("LLM generateSingleAction aborted due to interrupt");
                return null;
            }
            if (log.isErrorEnabled()) {
                log.error("Error generating single action: {}", e.getMessage());
            }
        }
        return null;
    }

    private Action parseSingleAction(String jsonStr) {
        String cleaned = cleanJson(jsonStr);
        try {
            Action action = objectMapper.readValue(cleaned, Action.class);
            if (action != null && action.getActionType() == null) {
                action.setActionType(ActionType.OBSERVE);
            }
            return action;
        } catch (JsonProcessingException e) {
            if (log.isErrorEnabled()) {
                log.error("Failed to parse single action JSON: {}", cleaned, e);
            }
            return null;
        }
    }

    private String cleanJson(String rawJson) {
        if (rawJson == null) {
            return "";
        }
        String cleaned = rawJson.trim();
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        return cleaned.trim();
    }

    public String testConnectionBatch(String testUrl, String testWorldModel, String testAgentModel, String testProseModel) {
        String worldRes = testModelPing(testUrl, testWorldModel);
        String agentRes = testModelPing(testUrl, testAgentModel);
        String proseRes = testModelPing(testUrl, testProseModel);

        boolean allOk = "OK".equals(worldRes) && "OK".equals(agentRes) && "OK".equals(proseRes);
        if (allOk) {
            return "Success! World: OK | Agent: OK | Prose: OK";
        }
        return String.format("Status: World: %s | Agent: %s | Prose: %s", worldRes, agentRes, proseRes);
    }

    private String testModelPing(String testUrl, String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return "No model selected";
        }
        try {
            String cleanName = modelName.contains(" (") ? modelName.substring(0, modelName.indexOf(" (")) : modelName;
            WebClient client = WebClient.builder().baseUrl(testUrl != null ? testUrl : this.baseUrl).build();
            Map<String, Object> request = Map.of("name", cleanName);
            Map response = client.post()
                    .uri("/api/show")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            if (response != null && (response.containsKey("modelfile") || response.containsKey("details") || response.containsKey("model_info") || response.containsKey("template") || !response.isEmpty())) {
                return "OK";
            }
            return "OK";
        } catch (Exception e) {
            return "Error (" + e.getMessage() + ")";
        }
    }

    public String testConnection(String testUrl, String testModel, String prompt) {
        if (testUrl == null || testUrl.equals(this.baseUrl)) {
            try {
                String reply = getChatClient().prompt()
                        .user(prompt != null && !prompt.isBlank() ? prompt : "Say 'Hello' in one word.")
                        .call()
                        .content();
                if (reply != null && !reply.isBlank()) {
                    return reply.trim();
                }
            } catch (Exception e) {
                log.warn("Spring AI testConnection failed, trying WebClient: {}", e.getMessage());
            }
        }
        try {
            WebClient client = WebClient.builder().baseUrl(testUrl != null ? testUrl : this.baseUrl).build();
            Map<String, Object> request = Map.of(
                    "model", testModel,
                    "prompt", prompt,
                    "stream", false
            );
            Map response = client.post()
                    .uri("/api/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            if (response != null && response.containsKey("response")) {
                return (String) response.get("response");
            }
            return "Error: No response field in JSON.";
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
}