package com.ghost616.agentinteg.model.invoker;

import com.ghost616.agentbase.dto.model.ChatChunk;
import com.ghost616.agentbase.dto.model.ChatRequest;
import com.ghost616.agentbase.dto.model.ChatResponse;
import com.ghost616.agentbase.dto.model.Message;
import com.ghost616.agentbase.dto.model.ToolCall;
import com.ghost616.agentbase.dto.model.ToolCallDelta;
import com.ghost616.agentbase.dto.model.ToolDefinition;
import com.ghost616.agentbase.dto.model.ToolInfo;
import com.ghost616.agentbase.dto.model.UsageInfo;
import com.ghost616.agentbase.dto.model.WebSearchCall;
import com.ghost616.agentbase.dto.tool.ToolConfigDTO;
import com.ghost616.agentbase.dto.model.ModelConfigData;
import com.ghost616.agentbase.enums.FinishReason;
import com.ghost616.agentbase.enums.RequestType;
import com.ghost616.agentbase.service.model.invoker.ModelInvoker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class OpenAIResponsesInvokerTest {

    private OpenAIResponsesInvoker invoker;

    @BeforeEach
    void setUp() {
        invoker = new OpenAIResponsesInvoker(
                "test-key", "https://api.openai.com", "gpt-4o",
                0.7, 2048,
                RestClient.builder(), WebClient.builder()
        );
    }

    @Test
    void parseResponseExtractsContentToolCallsUsageAndId() {
        String json = "{"
                + "\"id\":\"resp_123\","
                + "\"status\":\"completed\","
                + "\"usage\":{\"input_tokens\":10,\"output_tokens\":20,\"total_tokens\":30},"
                + "\"output\":["
                + "{\"type\":\"message\",\"role\":\"assistant\",\"content\":["
                + "{\"type\":\"output_text\",\"text\":\"Hello world\",\"annotations\":[]}]},"
                + "{\"type\":\"function_call\",\"id\":\"fc_1\",\"call_id\":\"call_1\","
                + "\"name\":\"get_weather\",\"arguments\":\"{\\\"city\\\":\\\"beijing\\\"}\"}"
                + "]}";

        ChatResponse response = invoker.parseResponse(json);

        assertNotNull(response);
        assertEquals("Hello world", response.getContent());
        assertNotNull(response.getToolCalls());
        assertEquals(1, response.getToolCalls().size());
        assertEquals("fc_1", response.getToolCalls().get(0).getId());
        assertEquals("get_weather", response.getToolCalls().get(0).getName());
        assertEquals("{\"city\":\"beijing\"}", response.getToolCalls().get(0).getArguments());
        assertEquals(FinishReason.STOP, response.getFinishReason());
        assertNotNull(response.getUsage());
        assertEquals(10, response.getUsage().getPromptTokens());
        assertEquals(20, response.getUsage().getCompletionTokens());
        assertEquals(30, response.getUsage().getTotalTokens());
        assertEquals("resp_123", response.getResponseId());
    }

    @Test
    void parseResponseEmptyOutput() {
        String json = "{\"id\":\"resp_1\",\"status\":\"incomplete\",\"output\":[]}";

        ChatResponse response = invoker.parseResponse(json);

        assertNotNull(response);
        assertNull(response.getContent());
        assertNull(response.getToolCalls());
        assertEquals(FinishReason.LENGTH, response.getFinishReason());
        assertEquals("resp_1", response.getResponseId());
    }

    @Test
    void parseResponseMalformedJsonThrows() {
        assertThrows(Exception.class, () -> invoker.parseResponse("{invalid"));
    }

    @Test
    void buildRequestBodyUsesInstructionsAndInputFormat() {
        List<Message> messages = new ArrayList<>();
        messages.add(Message.builder().role("system").content("sys prompt").build());
        messages.add(Message.builder().role("user").content("hi").build());
        messages.add(Message.builder().role("assistant").content("thinking")
                .toolCalls(List.of(ToolCall.builder()
                        .id("fc_9").name("add").arguments("{\"a\":1}").build()))
                .build());
        messages.add(Message.builder().role("tool").toolInfo(new ToolInfo("fc_9", "add")).content("2").build());

        ChatRequest request = ChatRequest.builder()
                .model("gpt-4o")
                .instructions("You are helpful")
                .previousResponseId("resp_prev")
                .messages(messages)
                .tools(List.of(ToolDefinition.builder()
                        .name("t1").description("d1")
                        .parameters(Map.of("type", "object"))
                        .build()))
                .builtinTools(List.of(Map.of("type", "web_search")))
                .thinking(true)
                .build();

        Map<String, Object> body = invoker.buildRequestBody(request, true);

        assertEquals("gpt-4o", body.get("model"));
        assertEquals(Boolean.TRUE, body.get("stream"));
        assertEquals(0.7, body.get("temperature"));
        assertEquals(2048, body.get("max_output_tokens"));
        assertEquals("You are helpful", body.get("instructions"));
        assertEquals("resp_prev", body.get("previous_response_id"));
        assertNotNull(body.get("reasoning"));

        List<?> input = (List<?>) body.get("input");
        assertNotNull(input);
        assertEquals(3, input.size());
        assertTrue(((Map<?, ?>) input.get(0)).get("role").equals("user"));
        assertEquals("hi", ((Map<?, ?>) input.get(0)).get("content"));

        Map<?, ?> assistant = (Map<?, ?>) input.get(1);
        assertEquals("assistant", assistant.get("role"));
        List<?> contentParts = (List<?>) assistant.get("content");
        assertEquals(2, contentParts.size());
        assertEquals("output_text", ((Map<?, ?>) contentParts.get(0)).get("type"));
        assertEquals("function_call", ((Map<?, ?>) contentParts.get(1)).get("type"));
        assertEquals("fc_9", ((Map<?, ?>) contentParts.get(1)).get("call_id"));
        assertEquals("add", ((Map<?, ?>) contentParts.get(1)).get("name"));

        Map<?, ?> toolOutput = (Map<?, ?>) input.get(2);
        assertEquals("function_call_output", toolOutput.get("type"));
        assertEquals("fc_9", toolOutput.get("call_id"));
        assertEquals("2", toolOutput.get("output"));

        List<?> tools = (List<?>) body.get("tools");
        assertNotNull(tools);
        assertEquals(2, tools.size());
        Map<?, ?> tool = (Map<?, ?>) tools.get(0);
        assertEquals("function", tool.get("type"));
        assertEquals("t1", tool.get("name"));
        assertEquals("d1", tool.get("description"));
        Map<?, ?> builtin = (Map<?, ?>) tools.get(1);
        assertEquals("web_search", builtin.get("type"));
    }

    @Test
    void buildRequestBodyMergesBuiltinToolsWhenNoCustomTools() {
        ChatRequest request = ChatRequest.builder()
                .model("gpt-4o")
                .messages(List.of(Message.builder().role("user").content("hi").build()))
                .builtinTools(List.of(Map.of("type", "web_search")))
                .build();

        Map<String, Object> body = invoker.buildRequestBody(request, false);

        List<?> tools = (List<?>) body.get("tools");
        assertNotNull(tools);
        assertEquals(1, tools.size());
        Map<?, ?> builtin = (Map<?, ?>) tools.get(0);
        assertEquals("web_search", builtin.get("type"));
    }

    @Test
    void buildRequestBodyOmitsToolsWhenNoneConfigured() {
        ChatRequest request = ChatRequest.builder()
                .model("gpt-4o")
                .messages(List.of(Message.builder().role("user").content("hi").build()))
                .build();

        Map<String, Object> body = invoker.buildRequestBody(request, false);

        assertFalse(body.containsKey("tools"));
    }

    @Test
    void parseStreamEventOutputTextDelta() {
        String event = "{\"type\":\"response.output_text.delta\",\"item_id\":\"i1\","
                + "\"output_index\":0,\"content_index\":0,\"delta\":\"Hel\"}";

        ChatChunk chunk = invoker.parseStreamEvent(event).blockFirst();

        assertNotNull(chunk);
        assertEquals("Hel", chunk.getDelta());
        assertNull(chunk.getFinishReason());
        assertNull(chunk.getUsage());
    }

    @Test
    void parseStreamEventFunctionCallArgumentsDelta() {
        String event = "{\"type\":\"response.function_call_arguments.delta\",\"item_id\":\"i1\","
                + "\"output_index\":1,\"delta\":\"{\\\"ci\"}";

        ChatChunk chunk = invoker.parseStreamEvent(event).blockFirst();

        assertNotNull(chunk);
        assertNotNull(chunk.getToolCalls());
        assertEquals(1, chunk.getToolCalls().size());
        ToolCallDelta delta = chunk.getToolCalls().get(0);
        assertEquals(1, delta.getIndex());
        assertEquals("{\"ci", delta.getArguments());
        assertEquals(Boolean.TRUE, chunk.getHasToolCalls());
    }

    @Test
    void parseStreamEventCompleted() {
        String event = "{\"type\":\"response.completed\",\"response\":{"
                + "\"id\":\"r1\",\"status\":\"completed\","
                + "\"usage\":{\"input_tokens\":1,\"output_tokens\":2,\"total_tokens\":3}}}";

        ChatChunk chunk = invoker.parseStreamEvent(event).blockFirst();

        assertNotNull(chunk);
        assertEquals(FinishReason.STOP, chunk.getFinishReason());
        assertEquals("r1", chunk.getResponseId());
        assertNotNull(chunk.getUsage());
        assertEquals(1, chunk.getUsage().getPromptTokens());
        assertEquals(2, chunk.getUsage().getCompletionTokens());
        assertEquals(3, chunk.getUsage().getTotalTokens());
    }

    @Test
    void parseStreamEventWebSearchCallInProgress() {
        String event = "{\"type\":\"response.web_search_call.in_progress\",\"item_id\":\"ws_1\","
                + "\"output_index\":0}";

        ChatChunk chunk = invoker.parseStreamEvent(event).blockFirst();

        assertNotNull(chunk);
        assertNotNull(chunk.getWebSearchCall());
        assertEquals("ws_1", chunk.getWebSearchCall().getItemId());
        assertEquals(Integer.valueOf(0), chunk.getWebSearchCall().getOutputIndex());
        assertNull(chunk.getWebSearchCall().getResults());
    }

    @Test
    void parseStreamEventWebSearchCallSearching() {
        String event = "{\"type\":\"response.web_search_call.searching\",\"item_id\":\"ws_1\","
                + "\"output_index\":0,\"query\":\"weather\"}";

        ChatChunk chunk = invoker.parseStreamEvent(event).blockFirst();

        assertNotNull(chunk);
        assertNotNull(chunk.getWebSearchCall());
        assertEquals("ws_1", chunk.getWebSearchCall().getItemId());
        assertNull(chunk.getWebSearchCall().getResults());
    }

    @Test
    void parseStreamEventWebSearchCallCompletedExtractsResults() {
        String event = "{\"type\":\"response.web_search_call.completed\",\"item_id\":\"ws_1\","
                + "\"output_index\":0,\"results\":["
                + "{\"name\":\"Weather Report\",\"url\":\"https://example.com/weather\","
                + "\"description\":\"Current weather conditions\"},"
                + "{\"name\":\"Forecast\",\"url\":\"https://example.com/forecast\","
                + "\"description\":\"7-day outlook\"}]}";

        ChatChunk chunk = invoker.parseStreamEvent(event).blockFirst();

        assertNotNull(chunk);
        assertNotNull(chunk.getWebSearchCall());
        assertEquals("ws_1", chunk.getWebSearchCall().getItemId());
        assertEquals(Integer.valueOf(0), chunk.getWebSearchCall().getOutputIndex());
        assertNotNull(chunk.getWebSearchCall().getResults());
        assertEquals(2, chunk.getWebSearchCall().getResults().size());
        WebSearchCall.WebSearchResult first = chunk.getWebSearchCall().getResults().get(0);
        assertEquals("Weather Report", first.getTitle());
        assertEquals("https://example.com/weather", first.getUrl());
        assertEquals("Current weather conditions", first.getSnippet());
        WebSearchCall.WebSearchResult second = chunk.getWebSearchCall().getResults().get(1);
        assertEquals("Forecast", second.getTitle());
        assertEquals("https://example.com/forecast", second.getUrl());
        assertEquals("7-day outlook", second.getSnippet());
    }

    @Test
    void parseStreamEventReasoningTextDelta() {
        String event = "{\"type\":\"response.reasoning_text.delta\",\"item_id\":\"i1\","
                + "\"output_index\":0,\"delta\":\"think\"}";

        ChatChunk chunk = invoker.parseStreamEvent(event).blockFirst();

        assertNotNull(chunk);
        assertEquals("think", chunk.getReasoning());
        assertNull(chunk.getDelta());
    }

    @Test
    void parseStreamEventCustomToolCallDone() {
        String event = "{\"type\":\"response.custom_tool_call.done\",\"item_id\":\"ct_1\","
                + "\"output_index\":1,\"input\":\"{\\\"a\\\":1}\"}";

        ChatChunk chunk = invoker.parseStreamEvent(event).blockFirst();

        assertNotNull(chunk);
        assertNotNull(chunk.getCustomToolCall());
        assertEquals("ct_1", chunk.getCustomToolCall().getItemId());
        assertEquals(Integer.valueOf(1), chunk.getCustomToolCall().getOutputIndex());
        assertEquals("{\"a\":1}", chunk.getCustomToolCall().getInput());
    }

    @Test
    void parseStreamEventUnknownTypeEmitsNothing() {
        String event = "{\"type\":\"response.created\",\"response\":{}}";

        List<ChatChunk> chunks = invoker.parseStreamEvent(event).collectList().block();

        assertNotNull(chunks);
        assertTrue(chunks.isEmpty());
    }

    @Test
    void toToolDefinitionWithSchema() {
        ToolConfigDTO tool = ToolConfigDTO.builder()
                .name("calc")
                .description("计算器")
                .parameterSchema("{\"a\":{\"type\":\"number\"}}")
                .build();

        ToolDefinition definition = invoker.toToolDefinition(tool);

        assertNotNull(definition);
        assertEquals("calc", definition.getName());
        assertEquals("计算器", definition.getDescription());
        assertNotNull(definition.getParameters());
        assertEquals("object", definition.getParameters().get("type"));
    }

    @Test
    void factoryCreatesResponsesInvokerForOpenaiResponses() {
        DefaultModelInvokerFactory factory = new DefaultModelInvokerFactory(
                RestClient.builder(), WebClient.builder());

        ModelConfigData responsesConfig = new ModelConfigData(
                "1", "key", "url", "model", 0.7, 2048, "OPENAI", RequestType.RESPONSES.getCode());
        ModelInvoker invoker1 = factory.createInvoker(responsesConfig);
        assertTrue(invoker1 instanceof OpenAIResponsesInvoker);

        ModelConfigData statelessConfig = new ModelConfigData(
                "1", "key", "url", "model", 0.7, 2048, "OPENAI", RequestType.RESPONSES_STATELESS.getCode());
        ModelInvoker invoker2 = factory.createInvoker(statelessConfig);
        assertTrue(invoker2 instanceof OpenAIResponsesInvoker);
    }

    @Test
    void factoryCreatesOpenAIInvokerForOpenaiChatCompletions() {
        DefaultModelInvokerFactory factory = new DefaultModelInvokerFactory(
                RestClient.builder(), WebClient.builder());

        ModelConfigData chatConfig = new ModelConfigData(
                "1", "key", "url", "model", 0.7, 2048, "OPENAI", RequestType.COMPLETIONS.getCode());
        ModelInvoker invoker1 = factory.createInvoker(chatConfig);
        assertTrue(invoker1 instanceof OpenAIInvoker);

        ModelConfigData noRequestType = new ModelConfigData(
                "1", "key", "url", "model", 0.7, 2048, "OPENAI", null);
        ModelInvoker invoker2 = factory.createInvoker(noRequestType);
        assertTrue(invoker2 instanceof OpenAIInvoker);
    }

    @Test
    void factoryRoutesResponsesPlatformsToResponsesInvokers() {
        DefaultModelInvokerFactory factory = new DefaultModelInvokerFactory(
                RestClient.builder(), WebClient.builder());

        assertTrue(factory.createInvoker(platformConfig("OPENAI", RequestType.RESPONSES.getCode()))
                instanceof OpenAIResponsesInvoker);
        assertTrue(factory.createInvoker(platformConfig("DEEPSEEK", RequestType.RESPONSES.getCode()))
                instanceof DeepSeekResponsesInvoker);
        assertTrue(factory.createInvoker(platformConfig("KIMI", RequestType.RESPONSES.getCode()))
                instanceof KimiResponsesInvoker);
        assertTrue(factory.createInvoker(platformConfig("VOLCENGINE", RequestType.RESPONSES_STATELESS.getCode()))
                instanceof VolcEngineResponsesInvoker);
        assertTrue(factory.createInvoker(platformConfig("AZURE", RequestType.RESPONSES.getCode()))
                instanceof AzureResponsesInvoker);
        assertTrue(factory.createInvoker(platformConfig("CUSTOM", RequestType.RESPONSES_STATELESS.getCode()))
                instanceof CustomResponsesInvoker);
    }

    @Test
    void factoryRoutesChatCompletionsPlatformsToOriginalInvokers() {
        DefaultModelInvokerFactory factory = new DefaultModelInvokerFactory(
                RestClient.builder(), WebClient.builder());

        assertTrue(factory.createInvoker(platformConfig("DEEPSEEK", RequestType.COMPLETIONS.getCode()))
                instanceof DeepSeekInvoker);
        assertTrue(factory.createInvoker(platformConfig("KIMI", null))
                instanceof KimiInvoker);
        assertTrue(factory.createInvoker(platformConfig("VOLCENGINE", RequestType.COMPLETIONS.getCode()))
                instanceof VolcEngineInvoker);
        assertTrue(factory.createInvoker(platformConfig("AZURE", null))
                instanceof AzureInvoker);
        assertTrue(factory.createInvoker(platformConfig("CUSTOM", RequestType.COMPLETIONS.getCode()))
                instanceof CustomInvoker);
        assertTrue(factory.createInvoker(platformConfig("ANTHROPIC", RequestType.RESPONSES.getCode()))
                instanceof AnthropicInvoker);
        assertTrue(factory.createInvoker(platformConfig("OLLAMA", RequestType.RESPONSES.getCode()))
                instanceof OllamaInvoker);
    }

    @Test
    void kimiResponsesBuildRequestBodyK3MapsReasoningToMax() {
        KimiResponsesInvoker kimi = new KimiResponsesInvoker(
                "test-key", "https://api.moonshot.cn", "kimi-k3",
                0.7, 2048, RestClient.builder(), WebClient.builder());

        ChatRequest request = ChatRequest.builder()
                .model("kimi-k3")
                .messages(List.of(Message.builder().role("user").content("hi").build()))
                .thinking(true)
                .build();

        Map<String, Object> body = kimi.buildRequestBody(request, false);

        assertFalse(body.containsKey("reasoning_effort"));
        assertNotNull(body.get("reasoning"));
        Map<?, ?> reasoning = (Map<?, ?>) body.get("reasoning");
        assertEquals("max", reasoning.get("effort"));
    }

    @Test
    void kimiResponsesBuildRequestBodyK2_7RemovesReasoning() {
        KimiResponsesInvoker kimi = new KimiResponsesInvoker(
                "test-key", "https://api.moonshot.cn", "kimi-k2.7-code",
                0.7, 2048, RestClient.builder(), WebClient.builder());

        ChatRequest request = ChatRequest.builder()
                .model("kimi-k2.7-code")
                .messages(List.of(Message.builder().role("user").content("hi").build()))
                .thinking(true)
                .build();

        Map<String, Object> body = kimi.buildRequestBody(request, false);

        assertFalse(body.containsKey("reasoning"));
    }

    @Test
    void azureResponsesBuildResponsesUrlUsesDeploymentPath() {
        AzureResponsesInvoker azure = new AzureResponsesInvoker(
                "test-key", "https://my-resource.openai.azure.com", "gpt-4o",
                0.7, 2048, RestClient.builder(), WebClient.builder());

        String url = azure.buildResponsesUrl();

        assertEquals("https://my-resource.openai.azure.com/openai/deployments/gpt-4o/responses?api-version=2024-02-15-preview",
                url);
    }

    private ModelConfigData platformConfig(String platformType, String requestType) {
        return new ModelConfigData("1", "key", "url", "model", 0.7, 2048, platformType, requestType);
    }

    private FinishReason mapFinishReason(String status) throws Exception {
        java.lang.reflect.Method method =
                OpenAIResponsesInvoker.class.getDeclaredMethod("mapFinishReason", String.class);
        method.setAccessible(true);
        return (FinishReason) method.invoke(invoker, status);
    }

    @Test
    void mapFinishReasonMapsAllStatuses() throws Exception {
        assertEquals(FinishReason.STOP, mapFinishReason("completed"));
        assertEquals(FinishReason.LENGTH, mapFinishReason("incomplete"));
        assertEquals(FinishReason.ERROR, mapFinishReason("failed"));
        assertEquals(FinishReason.CANCELLED, mapFinishReason("cancelled"));
    }

    @Test
    void mapFinishReasonUnknownOrBlankReturnsNull() throws Exception {
        assertNull(mapFinishReason("unknown"));
        assertNull(mapFinishReason(""));
        assertNull(mapFinishReason(null));
    }
}
