package com.ghost616.agentinteg.model.invoker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import com.ghost616.agentbase.dto.model.EmbeddingRequest;
import com.ghost616.agentbase.dto.model.EmbeddingResponse;
import com.ghost616.agentbase.dto.model.Message;
import com.ghost616.agentbase.dto.model.ToolCall;
import com.ghost616.agentbase.dto.model.ToolCallDelta;
import com.ghost616.agentbase.dto.model.ToolDefinition;
import com.ghost616.agentbase.dto.model.UsageInfo;
import com.ghost616.agentbase.dto.tool.ToolConfigDTO;
import com.ghost616.agentbase.enums.AgentErrorCode;
import com.ghost616.agentbase.enums.FinishReason;
import com.ghost616.agentbase.exception.AgentException;
import com.ghost616.agentbase.service.model.invoker.ModelInvoker;


/**
 * OpenAI 平台模型调用器。
 */
@Slf4j
public class OpenAIInvoker implements ModelInvoker {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    protected final String apiKey;
    protected final String baseUrl;
    protected final String modelName;
    protected final Double defaultTemperature;
    protected final Integer defaultMaxTokens;
    protected final RestClient.Builder restClientBuilder;
    protected final WebClient.Builder webClientBuilder;

    public OpenAIInvoker(String apiKey, String baseUrl, String modelName,
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
            String url = buildChatCompletionsUrl();
            Map<String, Object> requestBody = buildRequestBody(request, false);
            RestClient restClient = restClientBuilder.baseUrl("").build();
            String responseBody = restClient.post()
                    .uri(url)
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);
            return parseResponse(responseBody);
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            log.error("OpenAI invoke HTTP error: status={}", e.getStatusCode().value());
            throw new AgentException(AgentErrorCode.MODEL_INVOKE_ERROR,
                    "HTTP " + e.getStatusCode().value() + ": " + e.getResponseBodyAsString());
        } catch (AgentException e) {
            throw e;
        } catch (Exception e) {
            log.error("OpenAI invoke error", e);
            throw new AgentException(AgentErrorCode.MODEL_INVOKE_ERROR, e.getMessage());
        }
    }

    @Override
    public Flux<ChatChunk> invokeStream(ChatRequest request) {
        try {
            String url = buildChatCompletionsUrl();
            Map<String, Object> requestBody = buildRequestBody(request, true);
            log.debug("Stream request body: {}", requestBody);
            return Flux.defer(() -> {
                return webClientBuilder.baseUrl("").build()
                        .post()
                        .uri(url)
                        .header("Authorization", "Bearer " + apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .bodyValue(requestBody)
                        .retrieve()
                        .bodyToFlux(String.class)
                        .doOnSubscribe(s ->
                                log.debug("Stream request to {} with model {}", url, modelName))
                        .concatMap(chunkRaw -> {
                            if ("[DONE]".equals(chunkRaw)) {
                                return Mono.just(ChatChunk.builder()
                                        .finishReason(FinishReason.STOP).build());
                            }
                            if (chunkRaw.startsWith("{")) {
                                return Mono.just(parseStreamChunk(chunkRaw));
                            }
                            return Mono.empty();
                        })
                        .onErrorResume(this::handleStreamError);
            });
        } catch (AgentException e) {
            return Flux.error(e);
        } catch (Exception e) {
            log.error("OpenAI invokeStream error", e);
            return Flux.error(new AgentException(AgentErrorCode.MODEL_INVOKE_ERROR, e.getMessage()));
        }
    }

    @Override
    public EmbeddingResponse embed(EmbeddingRequest request) {
        try {
            String url = buildEmbeddingsUrl();
            Map<String, Object> requestBody = buildEmbeddingRequestBody(request);
            RestClient restClient = restClientBuilder.baseUrl("").build();
            String responseBody = restClient.post()
                    .uri(url)
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);
            return parseEmbeddingResponse(responseBody);
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            log.error("OpenAI embed HTTP error: status={}", e.getStatusCode().value());
            throw new AgentException(AgentErrorCode.MODEL_INVOKE_ERROR,
                    "HTTP " + e.getStatusCode().value() + ": " + e.getResponseBodyAsString());
        } catch (AgentException e) {
            throw e;
        } catch (Exception e) {
            log.error("OpenAI embed error", e);
            throw new AgentException(AgentErrorCode.MODEL_INVOKE_ERROR, e.getMessage());
        }
    }

    @Override
    public boolean verify() {
        try {
            RestClient restClient = restClientBuilder.baseUrl("").build();
            restClient.get()
                    .uri(baseUrl + "/models")
                    .header("Authorization", "Bearer " + apiKey)
                    .retrieve()
                    .body(String.class);
            return true;
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            log.error("OpenAI verify HTTP error: status={}", e.getStatusCode().value());
            throw new AgentException(AgentErrorCode.MODEL_VERIFY_ERROR,
                    "HTTP " + e.getStatusCode().value() + ": " + e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("OpenAI verify error", e);
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

    protected String buildChatCompletionsUrl() {
        return baseUrl + "/chat/completions";
    }

    protected String buildEmbeddingsUrl() {
        return baseUrl + "/embeddings";
    }

    protected Map<String, Object> buildEmbeddingRequestBody(EmbeddingRequest request) {
        if (request.getInputList() == null && request.getInput() == null) {
            throw new IllegalArgumentException("Embedding request must contain input or inputList");
        }
        Map<String, Object> body = new HashMap<>();
        body.put("model", request.getModel() != null ? request.getModel() : modelName);
        if (request.getInputList() != null) {
            body.put("input", request.getInputList());
        } else if (request.getInput() != null) {
            body.put("input", request.getInput());
        }
        return body;
    }

    protected EmbeddingResponse parseEmbeddingResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            EmbeddingResponse.EmbeddingResponseBuilder builder = EmbeddingResponse.builder();
            JsonNode dataNode = root.get("data");
            if (dataNode != null && dataNode.isArray()) {
                List<EmbeddingResponse.EmbeddingItem> embeddings = new ArrayList<>();
                for (JsonNode itemNode : dataNode) {
                    List<Float> embedding = new ArrayList<>();
                    JsonNode embeddingNode = itemNode.get("embedding");
                    if (embeddingNode != null && embeddingNode.isArray()) {
                        for (JsonNode valueNode : embeddingNode) {
                            embedding.add((float) valueNode.asDouble());
                        }
                    }
                    Integer index = itemNode.get("index") != null ? itemNode.get("index").asInt() : null;
                    embeddings.add(EmbeddingResponse.EmbeddingItem.builder()
                            .index(index)
                            .embedding(embedding)
                            .build());
                }
                builder.embeddings(embeddings);
            }
            JsonNode usageNode = root.get("usage");
            if (usageNode != null) {
                builder.usage(UsageInfo.builder()
                        .promptTokens(usageNode.get("prompt_tokens") != null
                                ? usageNode.get("prompt_tokens").asInt() : null)
                        .completionTokens(usageNode.get("completion_tokens") != null
                                ? usageNode.get("completion_tokens").asInt() : null)
                        .totalTokens(usageNode.get("total_tokens") != null
                                ? usageNode.get("total_tokens").asInt() : null)
                        .build());
            }
            return builder.build();
        } catch (Exception e) {
            log.error("Failed to parse OpenAI embedding response", e);
            throw new AgentException(AgentErrorCode.MODEL_INVOKE_ERROR,
                    "Failed to parse embedding response: " + e.getMessage());
        }
    }

    protected Map<String, Object> buildRequestBody(ChatRequest request, boolean stream) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", request.getModel() != null ? request.getModel() : modelName);
        body.put("stream", stream);
        Double temperature = request.getTemperature() != null ? request.getTemperature() : defaultTemperature;
        if (temperature != null) {
            body.put("temperature", temperature);
        }
        Integer maxTokens = request.getMaxTokens() != null ? request.getMaxTokens() : defaultMaxTokens;
        if (maxTokens != null) {
            body.put("max_tokens", maxTokens);
        }
        body.put("messages", buildMessages(request.getMessages()));
        if (request.getTools() != null && !request.getTools().isEmpty()) {
            body.put("tools", buildTools(request.getTools()));
        }
        if (Boolean.TRUE.equals(request.getThinking())) {
            Map<String, String> thinkingConfig = new HashMap<>();
            thinkingConfig.put("type", "enabled");
            body.put("thinking", thinkingConfig);
        }
        return body;
    }

    protected List<Map<String, Object>> buildMessages(List<Message> messages) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Message msg : messages) {
            Map<String, Object> m = new HashMap<>();
            m.put("role", msg.getRole());
            m.put("content", msg.getContent());
            if ("assistant".equals(msg.getRole()) && msg.getToolCalls() != null && !msg.getToolCalls().isEmpty()) {
                List<Map<String, Object>> toolCalls = new ArrayList<>();
                for (ToolCall tc : msg.getToolCalls()) {
                    Map<String, Object> tcMap = new HashMap<>();
                    tcMap.put("id", tc.getId());
                    tcMap.put("type", "function");
                    Map<String, Object> function = new HashMap<>();
                    function.put("name", tc.getName());
                    function.put("arguments", tc.getArguments());
                    tcMap.put("function", function);
                    toolCalls.add(tcMap);
                }
                m.put("tool_calls", toolCalls);
            }
            if ("tool".equals(msg.getRole())) {
                if (msg.getToolInfo() != null) {
                    m.put("tool_call_id", msg.getToolInfo().toolCallId());
                    if (msg.getToolInfo().toolName() != null) {
                        m.put("name", msg.getToolInfo().toolName());
                    }
                }
            }
            if (msg.getReasoning() != null && !msg.getReasoning().isEmpty()) {
                m.put("reasoning_content", msg.getReasoning());
            }
            result.add(m);
        }
        return result;
    }

    protected List<Map<String, Object>> buildTools(List<ToolDefinition> tools) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (ToolDefinition tool : tools) {
            Map<String, Object> toolMap = new HashMap<>();
            toolMap.put("type", "function");
            Map<String, Object> function = new HashMap<>();
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

    protected ChatResponse parseResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode choices = root.get("choices");
            ChatResponse.ChatResponseBuilder builder = ChatResponse.builder();
            if (choices != null && choices.isArray() && choices.size() > 0) {
                JsonNode firstChoice = choices.get(0);
                JsonNode message = firstChoice.get("message");
                if (message != null) {
                    JsonNode contentNode = message.get("content");
                    if (contentNode != null && !contentNode.isNull()) {
                        builder.content(contentNode.asText());
                    }
                    JsonNode toolCallsNode = message.get("tool_calls");
                    if (toolCallsNode != null && toolCallsNode.isArray()) {
                        List<ToolCall> toolCalls = new ArrayList<>();
                        for (JsonNode tcNode : toolCallsNode) {
                            String tcId = tcNode.get("id").asText();
                            JsonNode functionNode = tcNode.get("function");
                            String tcName = functionNode.get("name").asText();
                            String tcArgs = functionNode.get("arguments").asText();
                            toolCalls.add(ToolCall.builder()
                                    .id(tcId).name(tcName).arguments(tcArgs).build());
                        }
                        builder.toolCalls(toolCalls);
                    }
                }
                JsonNode finishReasonNode = firstChoice.get("finish_reason");
                if (finishReasonNode != null && !finishReasonNode.isNull()) {
                    builder.finishReason(FinishReason.fromCode(finishReasonNode.asText()));
                }
            }
            JsonNode usageNode = root.get("usage");
            if (usageNode != null) {
                builder.usage(UsageInfo.builder()
                        .promptTokens(usageNode.get("prompt_tokens") != null
                                ? usageNode.get("prompt_tokens").asInt() : null)
                        .completionTokens(usageNode.get("completion_tokens") != null
                                ? usageNode.get("completion_tokens").asInt() : null)
                        .totalTokens(usageNode.get("total_tokens") != null
                                ? usageNode.get("total_tokens").asInt() : null)
                        .build());
            }
            return builder.build();
        } catch (Exception e) {
            log.error("Failed to parse OpenAI response", e);
            throw new AgentException(AgentErrorCode.MODEL_INVOKE_ERROR, "Failed to parse response: " + e.getMessage());
        }
    }

    protected Flux<ChatChunk> handleStreamError(Throwable ex) {
        log.error("OpenAI stream invoke failed for model {}: {}", modelName, ex.getMessage());
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

    protected ChatChunk parseStreamChunk(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode errorNode = root.get("error");
            if (errorNode != null && !errorNode.isNull()) {
                String errorMsg = errorNode.has("message")
                        ? errorNode.get("message").asText() : errorNode.toString();
                log.error("Stream chunk contains error from model {}: {}", modelName, errorMsg);
                return ChatChunk.builder().delta(errorMsg).finishReason(FinishReason.ERROR).build();
            }
            JsonNode choices = root.get("choices");
            if (choices != null && choices.isArray() && choices.size() > 0) {
                JsonNode firstChoice = choices.get(0);
                JsonNode delta = firstChoice.get("delta");
                ChatChunk.ChatChunkBuilder builder = ChatChunk.builder();
                if (delta != null) {
                    JsonNode contentNode = delta.get("content");
                    if (contentNode != null && !contentNode.isNull()) {
                        builder.delta(contentNode.asText());
                    }
                    JsonNode reasoningNode = delta.get("reasoning_content");
                    if (reasoningNode != null && !reasoningNode.isNull()) {
                        builder.reasoning(reasoningNode.asText());
                    }
                    JsonNode toolCallsNode = delta.get("tool_calls");
                    if (toolCallsNode != null && toolCallsNode.isArray() && toolCallsNode.size() > 0) {
                        List<ToolCallDelta> toolCalls = new ArrayList<>();
                        for (JsonNode tcNode : toolCallsNode) {
                            ToolCallDelta.ToolCallDeltaBuilder tcBuilder = ToolCallDelta.builder();
                            JsonNode idNode = tcNode.get("id");
                            if (idNode != null && !idNode.isNull()) {
                                tcBuilder.id(idNode.asText());
                            }
                            JsonNode indexNode = tcNode.get("index");
                            if (indexNode != null && !indexNode.isNull()) {
                                tcBuilder.index(indexNode.asInt());
                            }
                            JsonNode funcNode = tcNode.get("function");
                            if (funcNode != null) {
                                JsonNode nameNode = funcNode.get("name");
                                if (nameNode != null && !nameNode.isNull()) {
                                    tcBuilder.name(nameNode.asText());
                                }
                                JsonNode argsNode = funcNode.get("arguments");
                                if (argsNode != null && !argsNode.isNull()) {
                                    tcBuilder.arguments(argsNode.asText());
                                }
                            }
                            toolCalls.add(tcBuilder.build());
                        }
                        builder.toolCalls(toolCalls);
                    }
                }
                JsonNode finishNode = firstChoice.get("finish_reason");
                if (finishNode != null && !finishNode.isNull()) {
                    builder.finishReason(FinishReason.fromCode(finishNode.asText()));
                }
                JsonNode usageNode = root.get("usage");
                if (usageNode != null) {
                    builder.usage(UsageInfo.builder()
                            .promptTokens(usageNode.get("prompt_tokens") != null
                                    ? usageNode.get("prompt_tokens").asInt() : null)
                            .completionTokens(usageNode.get("completion_tokens") != null
                                    ? usageNode.get("completion_tokens").asInt() : null)
                            .totalTokens(usageNode.get("total_tokens") != null
                                    ? usageNode.get("total_tokens").asInt() : null)
                            .build());
                }
                return builder.build();
            }
            return new ChatChunk();
        } catch (Exception e) {
            log.error("Failed to parse stream chunk", e);
            return new ChatChunk();
        }
    }
}
