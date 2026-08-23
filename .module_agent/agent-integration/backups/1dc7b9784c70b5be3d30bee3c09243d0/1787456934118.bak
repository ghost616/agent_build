package com.ghost616.agentinteg.model.invoker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import com.ghost616.agentbase.dto.model.ChatChunk;
import com.ghost616.agentbase.dto.model.ChatRequest;
import com.ghost616.agentbase.dto.model.ChatResponse;
import com.ghost616.agentbase.dto.model.Message;
import com.ghost616.agentbase.dto.model.ToolDefinition;
import com.ghost616.agentbase.dto.model.UsageInfo;
import com.ghost616.agentbase.dto.tool.ToolConfigDTO;
import com.ghost616.agentbase.enums.AgentErrorCode;
import com.ghost616.agentbase.enums.FinishReason;
import com.ghost616.agentbase.exception.AgentException;
import com.ghost616.agentbase.service.model.invoker.ModelInvoker;


/**
 * Ollama 平台模型调用器。
 */
@Slf4j
public class OllamaInvoker implements ModelInvoker {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final String apiKey;
    private final String baseUrl;
    private final String modelName;
    private final Double defaultTemperature;
    private final Integer defaultMaxTokens;
    private final RestClient.Builder restClientBuilder;
    private final WebClient.Builder webClientBuilder;

    public OllamaInvoker(String apiKey, String baseUrl, String modelName,
            Double defaultTemperature, Integer defaultMaxTokens,
            RestClient.Builder restClientBuilder, WebClient.Builder webClientBuilder) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.modelName = modelName;
        this.defaultTemperature = defaultTemperature;
        this.defaultMaxTokens = defaultMaxTokens;
        this.restClientBuilder = restClientBuilder;
        this.webClientBuilder = webClientBuilder;
    }

    @Override
    public ChatResponse invoke(ChatRequest request) {
        try {
            Map<String, Object> requestBody = buildRequestBody(request, false);
            RestClient restClient = restClientBuilder.baseUrl("").build();
            String responseBody = restClient.post()
                    .uri(baseUrl + "/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);
            return parseResponse(responseBody);
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            log.error("Ollama invoke HTTP error: status={}", e.getStatusCode().value());
            throw new AgentException(AgentErrorCode.MODEL_INVOKE_ERROR,
                    "HTTP " + e.getStatusCode().value() + ": " + e.getResponseBodyAsString());
        } catch (AgentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Ollama invoke error", e);
            throw new AgentException(AgentErrorCode.MODEL_INVOKE_ERROR, e.getMessage());
        }
    }

    @Override
    public Flux<ChatChunk> invokeStream(ChatRequest request) {
        try {
            Map<String, Object> requestBody = buildRequestBody(request, true);
            String url = baseUrl + "/api/chat";
            AtomicBoolean hasContent = new AtomicBoolean(false);
            return webClientBuilder.baseUrl("").build()
                    .post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_NDJSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToFlux(String.class)
                    .doOnSubscribe(s -> log.debug("Ollama stream request to {} with model {}", url, modelName))
                    .flatMap(chunk -> {
                        String[] lines = chunk.split("\n", -1);
                        return Flux.fromArray(lines);
                    })
                    .filter(line -> !line.isBlank())
                    .map(this::parseStreamChunk)
                    .doOnNext(chunk -> {
                        if (chunk.getDelta() != null && !chunk.getDelta().isEmpty()) {
                            hasContent.set(true);
                        }
                    })
                    .concatWith(Mono.defer(() -> {
                        if (!hasContent.get()) {
                            log.debug("Ollama model {} stream produced no content", modelName);
                        }
                        return Mono.just(ChatChunk.builder().finishReason(FinishReason.STOP).build());
                    }))
                    .onErrorResume(this::handleStreamError);
        } catch (AgentException e) {
            return Flux.error(e);
        } catch (Exception e) {
            log.error("Ollama invokeStream error", e);
            return Flux.error(new AgentException(AgentErrorCode.MODEL_INVOKE_ERROR, e.getMessage()));
        }
    }

    @Override
    public boolean verify() {
        try {
            RestClient restClient = restClientBuilder.baseUrl("").build();
            restClient.get()
                    .uri(baseUrl + "/api/tags")
                    .retrieve()
                    .body(String.class);
            return true;
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            log.error("Ollama verify HTTP error: status={}", e.getStatusCode().value());
            throw new AgentException(AgentErrorCode.MODEL_VERIFY_ERROR,
                    "HTTP " + e.getStatusCode().value() + ": " + e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("Ollama verify error", e);
            throw new AgentException(AgentErrorCode.MODEL_VERIFY_ERROR, e.getMessage());
        }
    }

    @Override
    public ToolDefinition toToolDefinition(ToolConfigDTO tool) {
        if (tool.getParameterSchema() == null || tool.getParameterSchema().isBlank()) {
            return createMinimalToolDefinition(tool);
        }
        try {
            JsonNode schemaNode = objectMapper.readTree(tool.getParameterSchema());
            Map<String, Object> params;
            if (schemaNode.has("type")) {
                params = objectMapper.convertValue(schemaNode, Map.class);
            } else {
                Map<String, Object> wrapper = new LinkedHashMap<>();
                wrapper.put("type", "object");
                wrapper.put("properties", objectMapper.convertValue(schemaNode, Map.class));
                params = wrapper;
            }
            return ToolDefinition.builder()
                    .name(tool.getName())
                    .description(tool.getDescription())
                    .parameters(params)
                    .build();
        } catch (Exception e) {
            log.error("Failed to convert tool {} to ToolDefinition", tool.getName(), e);
            return createMinimalToolDefinition(tool);
        }
    }

    private Map<String, Object> buildRequestBody(ChatRequest request, boolean stream) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", request.getModel() != null ? request.getModel() : modelName);
        body.put("stream", stream);
        List<Map<String, Object>> messages = new ArrayList<>();
        for (Message msg : request.getMessages()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("role", msg.getRole());
            m.put("content", msg.getContent() != null ? msg.getContent() : "");
            messages.add(m);
        }
        body.put("messages", messages);
        Map<String, Object> options = new LinkedHashMap<>();
        Double temperature = request.getTemperature() != null ? request.getTemperature() : defaultTemperature;
        if (temperature != null) {
            options.put("temperature", temperature);
        }
        Integer maxTokens = request.getMaxTokens() != null ? request.getMaxTokens() : defaultMaxTokens;
        if (maxTokens != null) {
            options.put("num_predict", maxTokens);
        }
        if (!options.isEmpty()) {
            body.put("options", options);
        }
        if (request.getTools() != null && !request.getTools().isEmpty()) {
            body.put("tools", buildTools(request.getTools()));
        }
        return body;
    }

    private List<Map<String, Object>> buildTools(List<ToolDefinition> tools) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (ToolDefinition tool : tools) {
            Map<String, Object> toolMap = new LinkedHashMap<>();
            toolMap.put("type", "function");
            Map<String, Object> function = new LinkedHashMap<>();
            function.put("name", tool.getName());
            function.put("description", tool.getDescription());
            if (tool.getParameters() != null) {
                function.put("parameters", tool.getParameters());
            }
            toolMap.put("function", function);
            result.add(toolMap);
        }
        return result;
    }

    private ChatResponse parseResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            ChatResponse.ChatResponseBuilder builder = ChatResponse.builder();
            JsonNode messageNode = root.get("message");
            if (messageNode != null) {
                JsonNode contentNode = messageNode.get("content");
                if (contentNode != null && !contentNode.isNull()) {
                    builder.content(contentNode.asText());
                }
            }
            JsonNode doneReasonNode = root.get("done_reason");
            if (doneReasonNode != null && !doneReasonNode.isNull()) {
                builder.finishReason(mapDoneReason(doneReasonNode.asText()));
            }
            JsonNode evalCount = root.get("eval_count");
            JsonNode promptEvalCount = root.get("prompt_eval_count");
            if (evalCount != null || promptEvalCount != null) {
                builder.usage(UsageInfo.builder()
                        .promptTokens(promptEvalCount != null ? promptEvalCount.asInt() : null)
                        .completionTokens(evalCount != null ? evalCount.asInt() : null)
                        .totalTokens(null)
                        .build());
            }
            return builder.build();
        } catch (Exception e) {
            log.error("Failed to parse Ollama response", e);
            throw new AgentException(AgentErrorCode.MODEL_INVOKE_ERROR,
                    "Failed to parse response: " + e.getMessage());
        }
    }

    private Flux<ChatChunk> handleStreamError(Throwable ex) {
        log.error("Ollama stream invoke failed for model {}: {}", modelName, ex.getMessage());
        String errorMsg;
        if (ex instanceof org.springframework.web.reactive.function.client.WebClientResponseException wcre) {
            errorMsg = "模型请求失败 (HTTP " + wcre.getStatusCode().value() + "): "
                    + truncate(wcre.getResponseBodyAsString(), 200);
        } else {
            errorMsg = "模型请求失败: " + ex.getMessage();
        }
        return Flux.just(ChatChunk.builder().delta(errorMsg).finishReason(FinishReason.ERROR).build());
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    private FinishReason mapDoneReason(String doneReason) {
        if (doneReason == null || doneReason.isBlank()) {
            return null;
        }
        return switch (doneReason) {
            case "stop", "load", "unload" -> FinishReason.STOP;
            case "error" -> FinishReason.ERROR;
            default -> FinishReason.fromCode(doneReason);
        };
    }

    private ChatChunk parseStreamChunk(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            ChatChunk.ChatChunkBuilder builder = ChatChunk.builder();
            JsonNode messageNode = root.get("message");
            if (messageNode != null) {
                JsonNode contentNode = messageNode.get("content");
                if (contentNode != null && !contentNode.isNull()) {
                    builder.delta(contentNode.asText());
                }
            }
            JsonNode doneNode = root.get("done");
            if (doneNode != null && doneNode.asBoolean()) {
                JsonNode doneReasonNode = root.get("done_reason");
                builder.finishReason(mapDoneReason(doneReasonNode != null && !doneReasonNode.isNull()
                        ? doneReasonNode.asText() : "stop"));
                JsonNode evalCount = root.get("eval_count");
                JsonNode promptEvalCount = root.get("prompt_eval_count");
                if (evalCount != null || promptEvalCount != null) {
                    builder.usage(UsageInfo.builder()
                            .promptTokens(promptEvalCount != null ? promptEvalCount.asInt() : null)
                            .completionTokens(evalCount != null ? evalCount.asInt() : null)
                            .totalTokens(null)
                            .build());
                }
            }
            return builder.build();
        } catch (Exception e) {
            log.error("Failed to parse Ollama stream chunk", e);
            return new ChatChunk();
        }
    }
}
