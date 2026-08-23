package com.ghost616.agentinteg.model.invoker;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
 * Anthropic 平台模型调用器。
 */
@Slf4j
public class AnthropicInvoker implements ModelInvoker {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final String apiKey;
    private final String baseUrl;
    private final String modelName;
    private final Double defaultTemperature;
    private final Integer defaultMaxTokens;
    private final RestClient.Builder restClientBuilder;
    private final WebClient.Builder webClientBuilder;

    public AnthropicInvoker(String apiKey, String baseUrl, String modelName,
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
                    .uri(baseUrl + "/messages")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", ANTHROPIC_VERSION)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);
            return parseResponse(responseBody);
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            log.error("Anthropic invoke HTTP error: status={}", e.getStatusCode().value());
            throw new AgentException(AgentErrorCode.MODEL_INVOKE_ERROR,
                    "HTTP " + e.getStatusCode().value() + ": " + e.getResponseBodyAsString());
        } catch (AgentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Anthropic invoke error", e);
            throw new AgentException(AgentErrorCode.MODEL_INVOKE_ERROR, e.getMessage());
        }
    }

    @Override
    public Flux<ChatChunk> invokeStream(ChatRequest request) {
        try {
            Map<String, Object> requestBody = buildRequestBody(request, true);
            Map<Integer, AnthropicBlockState> blockStates = new ConcurrentHashMap<>();
            String[] stopReason = new String[]{null};
            UsageInfo[] usageHolder = new UsageInfo[]{null};
            AtomicBoolean hasContent = new AtomicBoolean(false);
            String url = baseUrl + "/messages";
            return webClientBuilder.baseUrl("").build()
                    .post()
                    .uri(url)
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", ANTHROPIC_VERSION)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToFlux(String.class)
                    .doOnSubscribe(s -> log.debug("Anthropic stream request to {} with model {}", url, modelName))
                    .flatMap(chunk -> {
                        String[] lines = chunk.split("\n", -1);
                        return Flux.fromArray(lines);
                    })
                    .filter(line -> !line.isEmpty())
                    .map(this::parseSSELine)
                    .filter(event -> event != null)
                    .flatMap(event -> handleStreamEvent(event, blockStates, stopReason, usageHolder))
                    .doOnNext(chunk -> {
                        if (chunk.getDelta() != null && !chunk.getDelta().isEmpty()) {
                            hasContent.set(true);
                        }
                    })
                    .concatWith(Mono.defer(() -> {
                        if (!hasContent.get()) {
                            log.debug("Anthropic model {} stream produced no content", modelName);
                        }
                        ChatChunk.ChatChunkBuilder stopBuilder = ChatChunk.builder()
                                .finishReason(mapStopReason(stopReason[0]));
                        if (usageHolder[0] != null) {
                            stopBuilder.usage(usageHolder[0]);
                        }
                        return Mono.just(stopBuilder.build());
                    }))
                    .onErrorResume(this::handleStreamError);
        } catch (AgentException e) {
            return Flux.error(e);
        } catch (Exception e) {
            log.error("Anthropic invokeStream error", e);
            return Flux.error(new AgentException(AgentErrorCode.MODEL_INVOKE_ERROR, e.getMessage()));
        }
    }

    @Override
    public boolean verify() {
        try {
            RestClient restClient = restClientBuilder.baseUrl("").build();
            restClient.get()
                    .uri(baseUrl + "/models")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", ANTHROPIC_VERSION)
                    .retrieve()
                    .body(String.class);
            return true;
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            log.error("Anthropic verify HTTP error: status={}", e.getStatusCode().value());
            throw new AgentException(AgentErrorCode.MODEL_VERIFY_ERROR,
                    "HTTP " + e.getStatusCode().value() + ": " + e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("Anthropic verify error", e);
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

    private Flux<ChatChunk> handleStreamError(Throwable ex) {
        log.error("Anthropic stream invoke failed for model {}: {}", modelName, ex.getMessage());
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

    private FinishReason mapStopReason(String stopReason) {
        if (stopReason == null || stopReason.isBlank()) {
            return FinishReason.STOP;
        }
        return switch (stopReason) {
            case "end_turn", "stop_sequence" -> FinishReason.STOP;
            case "max_tokens" -> FinishReason.LENGTH;
            case "tool_use" -> FinishReason.TOOL_CALLS;
            case "error" -> FinishReason.ERROR;
            default -> FinishReason.fromCode(stopReason);
        };
    }

    private Map<String, Object> buildRequestBody(ChatRequest request, boolean stream) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", request.getModel() != null ? request.getModel() : modelName);
        body.put("stream", stream);
        Integer maxTokens = request.getMaxTokens() != null ? request.getMaxTokens() : defaultMaxTokens;
        body.put("max_tokens", maxTokens != null ? maxTokens : 4096);
        Double temperature = request.getTemperature() != null ? request.getTemperature() : defaultTemperature;
        if (temperature != null) {
            body.put("temperature", temperature);
        }
        List<Map<String, Object>> contentBlocks = buildAnthropicMessages(request.getMessages(), body);
        body.put("messages", contentBlocks);
        if (request.getTools() != null && !request.getTools().isEmpty()) {
            body.put("tools", buildAnthropicTools(request.getTools()));
        }
        return body;
    }

    private List<Map<String, Object>> buildAnthropicMessages(List<Message> messages,
            Map<String, Object> requestBody) {
        List<Map<String, Object>> anthropicMessages = new ArrayList<>();
        List<Map<String, Object>> systemBlocks = new ArrayList<>();
        for (Message msg : messages) {
            if ("system".equals(msg.getRole())) {
                Map<String, Object> textBlock = new HashMap<>();
                textBlock.put("type", "text");
                textBlock.put("text", msg.getContent());
                systemBlocks.add(textBlock);
            } else if ("user".equals(msg.getRole())) {
                Map<String, Object> userMsg = new HashMap<>();
                userMsg.put("role", "user");
                List<Map<String, Object>> content = new ArrayList<>();
                Map<String, Object> textBlock = new HashMap<>();
                textBlock.put("type", "text");
                textBlock.put("text", msg.getContent() != null ? msg.getContent() : "");
                content.add(textBlock);
                userMsg.put("content", content);
                anthropicMessages.add(userMsg);
            } else if ("assistant".equals(msg.getRole())) {
                Map<String, Object> assistantMsg = new HashMap<>();
                assistantMsg.put("role", "assistant");
                List<Map<String, Object>> content = new ArrayList<>();
                if (msg.getContent() != null && !msg.getContent().isEmpty()) {
                    Map<String, Object> textBlock = new HashMap<>();
                    textBlock.put("type", "text");
                    textBlock.put("text", msg.getContent());
                    content.add(textBlock);
                }
                if (msg.getToolCalls() != null) {
                    for (ToolCall tc : msg.getToolCalls()) {
                        Map<String, Object> toolUseBlock = new HashMap<>();
                        toolUseBlock.put("type", "tool_use");
                        toolUseBlock.put("id", tc.getId());
                        toolUseBlock.put("name", tc.getName());
                        try {
                            toolUseBlock.put("input",
                                    objectMapper.readValue(tc.getArguments(), Map.class));
                        } catch (JsonProcessingException e) {
                            toolUseBlock.put("input", Map.of());
                        }
                        content.add(toolUseBlock);
                    }
                }
                assistantMsg.put("content", content);
                anthropicMessages.add(assistantMsg);
            } else if ("tool".equals(msg.getRole())) {
                Map<String, Object> userMsg = new HashMap<>();
                userMsg.put("role", "user");
                List<Map<String, Object>> content = new ArrayList<>();
                Map<String, Object> toolResult = new LinkedHashMap<>();
                toolResult.put("type", "tool_result");
                toolResult.put("tool_use_id", msg.getToolInfo() != null ? msg.getToolInfo().toolCallId() : null);
                toolResult.put("content", msg.getContent() != null ? msg.getContent() : "");
                content.add(toolResult);
                userMsg.put("content", content);
                anthropicMessages.add(userMsg);
            }
        }
        if (!systemBlocks.isEmpty()) {
            if (systemBlocks.size() == 1) {
                requestBody.put("system", ((Map<String, Object>) systemBlocks.get(0)).get("text"));
            } else {
                requestBody.put("system", systemBlocks);
            }
        }
        return anthropicMessages;
    }

    private List<Map<String, Object>> buildAnthropicTools(List<ToolDefinition> tools) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (ToolDefinition tool : tools) {
            Map<String, Object> toolMap = new LinkedHashMap<>();
            toolMap.put("name", tool.getName());
            toolMap.put("description", tool.getDescription());
            toolMap.put("input_schema", tool.getParameters() != null
                    ? tool.getParameters() : Map.of("type", "object", "properties", Map.of()));
            result.add(toolMap);
        }
        return result;
    }

    private ChatResponse parseResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            ChatResponse.ChatResponseBuilder builder = ChatResponse.builder();
            List<ToolCall> toolCalls = new ArrayList<>();
            StringBuilder contentBuilder = new StringBuilder();
            JsonNode contentArray = root.get("content");
            if (contentArray != null && contentArray.isArray()) {
                for (JsonNode block : contentArray) {
                    String type = block.get("type").asText();
                    if ("text".equals(type)) {
                        JsonNode textNode = block.get("text");
                        if (textNode != null) {
                            contentBuilder.append(textNode.asText());
                        }
                    } else if ("tool_use".equals(type)) {
                        String tcId = block.get("id").asText();
                        String tcName = block.get("name").asText();
                        String tcArgs = objectMapper.writeValueAsString(block.get("input"));
                        toolCalls.add(ToolCall.builder()
                                .id(tcId).name(tcName).arguments(tcArgs).build());
                    }
                }
            }
            if (contentBuilder.length() > 0) {
                builder.content(contentBuilder.toString());
            }
            if (!toolCalls.isEmpty()) {
                builder.toolCalls(toolCalls);
            }
            JsonNode stopReasonNode = root.get("stop_reason");
            builder.finishReason(mapStopReason(stopReasonNode != null && !stopReasonNode.isNull()
                    ? stopReasonNode.asText() : null));
            JsonNode usageNode = root.get("usage");
            if (usageNode != null) {
                builder.usage(UsageInfo.builder()
                        .promptTokens(usageNode.get("input_tokens") != null
                                ? usageNode.get("input_tokens").asInt() : null)
                        .completionTokens(usageNode.get("output_tokens") != null
                                ? usageNode.get("output_tokens").asInt() : null)
                        .totalTokens(null)
                        .build());
            }
            return builder.build();
        } catch (Exception e) {
            log.error("Failed to parse Anthropic response", e);
            throw new AgentException(AgentErrorCode.MODEL_INVOKE_ERROR,
                    "Failed to parse response: " + e.getMessage());
        }
    }

    private SSERecord parseSSELine(String line) {
        try {
            if (line.startsWith("event: ")) {
                SSERecord record = new SSERecord();
                record.event = line.substring(7).trim();
                return record;
            }
            if (line.startsWith("data: ")) {
                SSERecord record = new SSERecord();
                record.data = objectMapper.readTree(line.substring(6).trim());
                return record;
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private Flux<ChatChunk> handleStreamEvent(SSERecord event,
            Map<Integer, AnthropicBlockState> blockStates, String[] stopReason, UsageInfo[] usageHolder) {
        if (event.data == null) {
            return Flux.empty();
        }
        try {
            String type = event.data.get("type").asText();
            if ("content_block_start".equals(type)) {
                JsonNode contentBlock = event.data.get("content_block");
                int index = event.data.get("index").asInt();
                String blockType = contentBlock.get("type").asText();
                AnthropicBlockState state = new AnthropicBlockState();
                state.blockType = blockType;
                if ("tool_use".equals(blockType)) {
                    state.id = contentBlock.get("id").asText();
                    state.name = contentBlock.get("name").asText();
                }
                blockStates.put(index, state);
                return Flux.empty();
            }
            if ("content_block_delta".equals(type)) {
                int index = event.data.get("index").asInt();
                AnthropicBlockState state = blockStates.get(index);
                if (state == null) {
                    return Flux.empty();
                }
                JsonNode delta = event.data.get("delta");
                if ("text_delta".equals(delta.get("type").asText())) {
                    String text = delta.get("text").asText();
                    return Flux.just(ChatChunk.builder().delta(text).index(index).build());
                }
                if ("input_json_delta".equals(delta.get("type").asText())) {
                    String partialJson = delta.get("partial_json").asText();
                    return Flux.just(ChatChunk.builder()
                            .toolCalls(List.of(ToolCallDelta.builder()
                                    .id(state.id).name(state.name)
                                    .arguments(partialJson).build()))
                            .build());
                }
                return Flux.empty();
            }
            if ("message_delta".equals(type)) {
                JsonNode delta = event.data.get("delta");
                if (delta != null) {
                    JsonNode stopReasonNode = delta.get("stop_reason");
                    if (stopReasonNode != null) {
                        stopReason[0] = stopReasonNode.asText();
                    }
                }
                JsonNode usageNode = event.data.get("usage");
                if (usageNode != null) {
                    usageHolder[0] = UsageInfo.builder()
                            .promptTokens(usageNode.get("input_tokens") != null
                                    ? usageNode.get("input_tokens").asInt() : null)
                            .completionTokens(usageNode.get("output_tokens") != null
                                    ? usageNode.get("output_tokens").asInt() : null)
                            .totalTokens(null)
                            .build();
                }
                return Flux.empty();
            }
            return Flux.empty();
        } catch (Exception e) {
            log.error("Failed to handle stream event", e);
            return Flux.empty();
        }
    }

    private static class AnthropicBlockState {
        String blockType;
        String id;
        String name;
    }

    private static class SSERecord {
        String event;
        JsonNode data;
    }
}
