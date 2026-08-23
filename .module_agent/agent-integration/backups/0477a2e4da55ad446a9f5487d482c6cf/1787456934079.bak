package com.ghost616.agentinteg.model.invoker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import com.ghost616.agentbase.dto.model.ChatChunk;
import com.ghost616.agentbase.dto.model.ChatRequest;
import com.ghost616.agentbase.dto.model.ChatResponse;
import com.ghost616.agentbase.dto.model.CustomToolCall;
import com.ghost616.agentbase.dto.model.Message;
import com.ghost616.agentbase.dto.model.ToolCall;
import com.ghost616.agentbase.dto.model.ToolCallDelta;
import com.ghost616.agentbase.dto.model.ToolDefinition;
import com.ghost616.agentbase.dto.model.UsageInfo;
import com.ghost616.agentbase.dto.model.WebSearchCall;
import com.ghost616.agentbase.dto.tool.ToolConfigDTO;
import com.ghost616.agentbase.enums.AgentErrorCode;
import com.ghost616.agentbase.enums.FinishReason;
import com.ghost616.agentbase.exception.AgentException;
import com.ghost616.agentbase.service.model.invoker.ModelInvoker;


/**
 * OpenAI Responses API 模型调用器，使用 /v1/responses 端点，支持 instructions+input 请求格式。
 */
@Slf4j
public class OpenAIResponsesInvoker implements ModelInvoker {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    protected final String apiKey;
    protected final String baseUrl;
    protected final String modelName;
    protected final Double defaultTemperature;
    protected final Integer defaultMaxTokens;
    protected final RestClient.Builder restClientBuilder;
    protected final WebClient.Builder webClientBuilder;

    public OpenAIResponsesInvoker(String apiKey, String baseUrl, String modelName,
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
            String url = buildResponsesUrl();
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
            log.error("OpenAI Responses invoke HTTP error: status={}", e.getStatusCode().value());
            throw new AgentException(AgentErrorCode.MODEL_INVOKE_ERROR,
                    "HTTP " + e.getStatusCode().value() + ": " + e.getResponseBodyAsString());
        } catch (AgentException e) {
            throw e;
        } catch (Exception e) {
            log.error("OpenAI Responses invoke error", e);
            throw new AgentException(AgentErrorCode.MODEL_INVOKE_ERROR, e.getMessage());
        }
    }

    @Override
    public Flux<ChatChunk> invokeStream(ChatRequest request) {
        try {
            String url = buildResponsesUrl();
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
                        .concatMap(this::parseStreamEvent)
                        .onErrorResume(this::handleStreamError);
            });
        } catch (AgentException e) {
            return Flux.error(e);
        } catch (Exception e) {
            log.error("OpenAI Responses invokeStream error", e);
            return Flux.error(new AgentException(AgentErrorCode.MODEL_INVOKE_ERROR, e.getMessage()));
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
            log.error("OpenAI Responses verify HTTP error: status={}", e.getStatusCode().value());
            throw new AgentException(AgentErrorCode.MODEL_VERIFY_ERROR,
                    "HTTP " + e.getStatusCode().value() + ": " + e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("OpenAI Responses verify error", e);
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

    protected String buildResponsesUrl() {
        return baseUrl + "/responses";
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
            body.put("max_output_tokens", maxTokens);
        }
        if (request.getInstructions() != null && !request.getInstructions().isEmpty()) {
            body.put("instructions", request.getInstructions());
        }
        if (request.getPreviousResponseId() != null && !request.getPreviousResponseId().isEmpty()) {
            body.put("previous_response_id", request.getPreviousResponseId());
        }
        List<Object> input = buildInput(request.getMessages());
        if (!input.isEmpty()) {
            body.put("input", input);
        }
        List<Map<String, Object>> tools = new ArrayList<>();
        if (request.getTools() != null && !request.getTools().isEmpty()) {
            tools.addAll(buildTools(request.getTools()));
        }
        tools.addAll(buildBuiltinTools(request));
        if (!tools.isEmpty()) {
            body.put("tools", tools);
        }
        if (Boolean.TRUE.equals(request.getThinking())) {
            body.put("reasoning", buildReasoning());
        }
        return body;
    }

    protected Map<String, Object> buildReasoning() {
        Map<String, Object> reasoning = new HashMap<>();
        reasoning.put("effort", "high");
        return reasoning;
    }

    protected List<Object> buildInput(List<Message> messages) {
        List<Object> result = new ArrayList<>();
        if (messages == null) {
            return result;
        }
        for (Message msg : messages) {
            if ("system".equals(msg.getRole())) {
                continue;
            }
            if ("tool".equals(msg.getRole())) {
                Map<String, Object> output = new LinkedHashMap<>();
                output.put("type", "function_call_output");
                output.put("call_id", msg.getToolInfo() != null ? msg.getToolInfo().toolCallId() : null);
                output.put("output", msg.getContent());
                result.add(output);
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("role", msg.getRole());
            if ("assistant".equals(msg.getRole())
                    && msg.getToolCalls() != null && !msg.getToolCalls().isEmpty()) {
                List<Object> parts = new ArrayList<>();
                if (msg.getContent() != null && !msg.getContent().isEmpty()) {
                    Map<String, Object> textPart = new LinkedHashMap<>();
                    textPart.put("type", "output_text");
                    textPart.put("text", msg.getContent());
                    parts.add(textPart);
                }
                for (ToolCall tc : msg.getToolCalls()) {
                    Map<String, Object> fc = new LinkedHashMap<>();
                    fc.put("type", "function_call");
                    fc.put("call_id", tc.getId());
                    fc.put("name", tc.getName());
                    fc.put("arguments", tc.getArguments());
                    parts.add(fc);
                }
                m.put("content", parts);
            } else {
                m.put("content", msg.getContent());
            }
            result.add(m);
        }
        return result;
    }

    protected List<Map<String, Object>> buildBuiltinTools(ChatRequest request) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (request.getBuiltinTools() == null || request.getBuiltinTools().isEmpty()) {
            return result;
        }
        result.addAll(request.getBuiltinTools());
        return result;
    }

    protected List<Map<String, Object>> buildTools(List<ToolDefinition> tools) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (ToolDefinition tool : tools) {
            Map<String, Object> toolMap = new HashMap<>();
            toolMap.put("type", "function");
            toolMap.put("name", tool.getName());
            toolMap.put("description", tool.getDescription());
            if (tool.getParameters() != null) {
                toolMap.put("parameters", tool.getParameters());
            }
            result.add(toolMap);
        }
        return result;
    }

    protected ChatResponse parseResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            ChatResponse.ChatResponseBuilder builder = ChatResponse.builder();
            StringBuilder contentBuilder = new StringBuilder();
            List<ToolCall> toolCalls = new ArrayList<>();
            JsonNode output = root.get("output");
            if (output != null && output.isArray()) {
                for (JsonNode item : output) {
                    String type = item.has("type") ? item.get("type").asText() : "";
                    if ("message".equals(type)) {
                        JsonNode contentNode = item.get("content");
                        if (contentNode != null && contentNode.isArray()) {
                            for (JsonNode part : contentNode) {
                                if ("output_text".equals(part.path("type").asText())) {
                                    JsonNode textNode = part.get("text");
                                    if (textNode != null && !textNode.isNull()) {
                                        contentBuilder.append(textNode.asText());
                                    }
                                }
                            }
                        }
                    } else if ("function_call".equals(type)) {
                        String id = item.has("id") ? item.get("id").asText() : null;
                        String name = item.has("name") ? item.get("name").asText() : null;
                        String arguments = item.has("arguments") ? item.get("arguments").asText() : null;
                        toolCalls.add(ToolCall.builder()
                                .id(id).name(name).arguments(arguments).build());
                    }
                }
            }
            if (contentBuilder.length() > 0) {
                builder.content(contentBuilder.toString());
            }
            if (!toolCalls.isEmpty()) {
                builder.toolCalls(toolCalls);
            }
            JsonNode idNode = root.get("id");
            if (idNode != null && !idNode.isNull()) {
                builder.responseId(idNode.asText());
            }
            String status = root.path("status").asText("");
            builder.finishReason(mapFinishReason(status));
            JsonNode usageNode = root.get("usage");
            if (usageNode != null && !usageNode.isNull()) {
                builder.usage(UsageInfo.builder()
                        .promptTokens(usageNode.get("input_tokens") != null
                                ? usageNode.get("input_tokens").asInt() : null)
                        .completionTokens(usageNode.get("output_tokens") != null
                                ? usageNode.get("output_tokens").asInt() : null)
                        .totalTokens(usageNode.get("total_tokens") != null
                                ? usageNode.get("total_tokens").asInt() : null)
                        .build());
            }
            return builder.build();
        } catch (Exception e) {
            log.error("Failed to parse OpenAI Responses response", e);
            throw new AgentException(AgentErrorCode.MODEL_INVOKE_ERROR, "Failed to parse response: " + e.getMessage());
        }
    }

    protected Flux<ChatChunk> parseStreamEvent(String eventRaw) {
        try {
            if (eventRaw == null || eventRaw.isBlank()) {
                return Flux.empty();
            }
            JsonNode root = objectMapper.readTree(eventRaw);
            String type = root.path("type").asText("");
            ChatChunk.ChatChunkBuilder builder = ChatChunk.builder();
            switch (type) {
                case "response.output_text.delta" -> {
                    String delta = root.path("delta").asText(null);
                    if (delta != null) {
                        builder.delta(delta);
                    }
                }
                case "response.function_call_arguments.delta" -> {
                    String delta = root.path("delta").asText(null);
                    if (delta != null) {
                        int index = root.path("output_index").asInt(0);
                        builder.toolCalls(List.of(ToolCallDelta.builder()
                                .index(index).arguments(delta).build()));
                        builder.hasToolCalls(true);
                    }
                }
                case "response.reasoning_text.delta" -> {
                    String delta = root.path("delta").asText(null);
                    if (delta != null) {
                        builder.reasoning(delta);
                    }
                }
                case "response.custom_tool_call.in_progress",
                        "response.custom_tool_call.done" -> {
                    CustomToolCall.CustomToolCallBuilder customToolCallBuilder =
                            CustomToolCall.builder()
                                    .itemId(root.path("item_id").asText(null));
                    if (root.has("output_index")) {
                        customToolCallBuilder.outputIndex(root.get("output_index").asInt());
                    }
                    JsonNode inputNode = root.get("input");
                    if (inputNode != null && !inputNode.isNull()) {
                        customToolCallBuilder.input(inputNode.isValueNode()
                                ? inputNode.asText() : inputNode.toString());
                    }
                    builder.customToolCall(customToolCallBuilder.build());
                }
                case "response.web_search_call.in_progress",
                        "response.web_search_call.searching",
                        "response.web_search_call.completed" -> {
                    WebSearchCall.WebSearchCallBuilder webSearchBuilder =
                            WebSearchCall.builder()
                                    .itemId(root.path("item_id").asText(null));
                    if (root.has("output_index")) {
                        webSearchBuilder.outputIndex(root.get("output_index").asInt());
                    }
                    if ("response.web_search_call.completed".equals(type)) {
                        webSearchBuilder.results(parseWebSearchResults(root));
                    }
                    builder.webSearchCall(webSearchBuilder.build());
                }
                case "response.completed" -> {
                    JsonNode responseNode = root.get("response");
                    if (responseNode != null) {
                        String status = responseNode.path("status").asText("");
                        builder.finishReason(mapFinishReason(status));
                        JsonNode idNode = responseNode.get("id");
                        if (idNode != null && !idNode.isNull()) {
                            builder.responseId(idNode.asText());
                        }
                        JsonNode usageNode = responseNode.get("usage");
                        if (usageNode != null && !usageNode.isNull()) {
                            builder.usage(UsageInfo.builder()
                                    .promptTokens(usageNode.get("input_tokens") != null
                                            ? usageNode.get("input_tokens").asInt() : null)
                                    .completionTokens(usageNode.get("output_tokens") != null
                                            ? usageNode.get("output_tokens").asInt() : null)
                                    .totalTokens(usageNode.get("total_tokens") != null
                                            ? usageNode.get("total_tokens").asInt() : null)
                                    .build());
                        }
                    }
                }
                default -> {
                    return Flux.empty();
                }
            }
            return Flux.just(builder.build());
        } catch (Exception e) {
            log.error("Failed to parse Responses stream event", e);
            return Flux.empty();
        }
    }

    private List<WebSearchCall.WebSearchResult> parseWebSearchResults(JsonNode root) {
        List<WebSearchCall.WebSearchResult> results = new ArrayList<>();
        JsonNode resultsNode = root.get("results");
        if (resultsNode != null && resultsNode.isArray()) {
            for (JsonNode item : resultsNode) {
                results.add(WebSearchCall.WebSearchResult.builder()
                        .title(item.path("name").asText(null))
                        .url(item.path("url").asText(null))
                        .snippet(item.path("description").asText(null))
                        .build());
            }
        }
        return results;
    }

    private FinishReason mapFinishReason(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return switch (status) {
            case "completed" -> FinishReason.STOP;
            case "incomplete" -> FinishReason.LENGTH;
            case "failed" -> FinishReason.ERROR;
            case "cancelled" -> FinishReason.CANCELLED;
            default -> FinishReason.fromCode(status);
        };
    }

    protected Flux<ChatChunk> handleStreamError(Throwable ex) {
        log.error("OpenAI Responses stream invoke failed for model {}: {}", modelName, ex.getMessage());
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
}
