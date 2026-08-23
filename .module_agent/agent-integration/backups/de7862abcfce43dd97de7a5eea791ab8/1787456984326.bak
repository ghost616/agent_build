package com.ghost616.agentinteg.model.invoker;

import com.ghost616.agentbase.dto.model.ChatChunk;
import com.ghost616.agentbase.dto.model.ChatResponse;
import com.ghost616.agentbase.dto.model.EmbeddingRequest;
import com.ghost616.agentbase.dto.model.EmbeddingResponse;
import com.ghost616.agentbase.dto.model.Message;
import com.ghost616.agentbase.dto.model.ToolInfo;
import com.ghost616.agentbase.dto.model.UsageInfo;
import com.ghost616.agentbase.enums.FinishReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class OpenAIInvokerTest {

    private OpenAIInvoker invoker;

    @BeforeEach
    void setUp() {
        invoker = new OpenAIInvoker(
                "test-key", "https://api.openai.com", "gpt-4",
                0.7, 2048,
                RestClient.builder(), WebClient.builder()
        );
    }

    @Test
    void parseStreamChunkWithUsage() {
        String json = "{"
                + "\"choices\":[{"
                + "\"delta\":{\"content\":\"Hello\"},"
                + "\"finish_reason\":\"stop\""
                + "}],"
                + "\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":20,\"total_tokens\":30}"
                + "}";

        ChatChunk chunk = invoker.parseStreamChunk(json);

        assertNotNull(chunk);
        assertEquals("Hello", chunk.getDelta());
        assertEquals(FinishReason.STOP, chunk.getFinishReason());
        assertNotNull(chunk.getUsage());
        assertEquals(10, chunk.getUsage().getPromptTokens());
        assertEquals(20, chunk.getUsage().getCompletionTokens());
        assertEquals(30, chunk.getUsage().getTotalTokens());
    }

    @Test
    void parseStreamChunkWithoutUsage() {
        String json = "{"
                + "\"choices\":[{"
                + "\"delta\":{\"content\":\"Hi\"},"
                + "\"finish_reason\":null"
                + "}]"
                + "}";

        ChatChunk chunk = invoker.parseStreamChunk(json);

        assertNotNull(chunk);
        assertEquals("Hi", chunk.getDelta());
        assertNull(chunk.getUsage());
    }

    @Test
    void parseStreamChunkWithNullUsage() {
        String json = "{"
                + "\"choices\":[{"
                + "\"delta\":{\"content\":\"test\"}"
                + "}],"
                + "\"usage\":null"
                + "}";

        ChatChunk chunk = invoker.parseStreamChunk(json);

        assertNotNull(chunk);
        assertEquals("test", chunk.getDelta());
        assertNotNull(chunk.getUsage());
        assertNull(chunk.getUsage().getPromptTokens());
        assertNull(chunk.getUsage().getCompletionTokens());
        assertNull(chunk.getUsage().getTotalTokens());
    }

    @Test
    void parseStreamChunkWithPartialUsage() {
        String json = "{"
                + "\"choices\":[{"
                + "\"delta\":{\"content\":\"partial\"}"
                + "}],"
                + "\"usage\":{\"prompt_tokens\":5,\"completion_tokens\":null,\"total_tokens\":null}"
                + "}";

        ChatChunk chunk = invoker.parseStreamChunk(json);

        assertNotNull(chunk);
        assertEquals("partial", chunk.getDelta());
        assertNotNull(chunk.getUsage());
        assertEquals(5, chunk.getUsage().getPromptTokens());
        assertEquals(0, chunk.getUsage().getCompletionTokens());
        assertEquals(0, chunk.getUsage().getTotalTokens());
    }

    @Test
    void parseStreamChunkMalformedJsonReturnsEmptyChunk() {
        ChatChunk chunk = invoker.parseStreamChunk("{invalid json");

        assertNotNull(chunk);
        assertNull(chunk.getDelta());
        assertNull(chunk.getUsage());
    }

    @Test
    void parseStreamChunkEmptyChoices() {
        String json = "{\"choices\":[],\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":2,\"total_tokens\":3}}";

        ChatChunk chunk = invoker.parseStreamChunk(json);

        assertNotNull(chunk);
        assertNull(chunk.getUsage());
    }

    @Test
    void buildMessagesToolRoleMapsToolCallIdAndName() {
        Message toolMsg = Message.builder()
                .role("tool")
                .content("result")
                .toolInfo(new ToolInfo("call_1", "getWeather"))
                .build();

        List<Map<String, Object>> result = invoker.buildMessages(List.of(toolMsg));

        assertEquals(1, result.size());
        Map<String, Object> m = result.get(0);
        assertEquals("tool", m.get("role"));
        assertEquals("result", m.get("content"));
        assertEquals("call_1", m.get("tool_call_id"));
        assertEquals("getWeather", m.get("name"));
    }

    @Test
    void buildMessagesToolRoleWithNullToolNameOmitsName() {
        Message toolMsg = Message.builder()
                .role("tool")
                .content("result")
                .toolInfo(new ToolInfo("call_2", null))
                .build();

        List<Map<String, Object>> result = invoker.buildMessages(List.of(toolMsg));

        assertEquals(1, result.size());
        Map<String, Object> m = result.get(0);
        assertEquals("tool", m.get("role"));
        assertEquals("call_2", m.get("tool_call_id"));
        assertFalse(m.containsKey("name"));
    }

    @Test
    void buildMessagesToolRoleWithNullToolInfoOmitsBoth() {
        Message toolMsg = Message.builder()
                .role("tool")
                .content("result")
                .build();

        List<Map<String, Object>> result = invoker.buildMessages(List.of(toolMsg));

        assertEquals(1, result.size());
        Map<String, Object> m = result.get(0);
        assertEquals("tool", m.get("role"));
        assertFalse(m.containsKey("tool_call_id"));
        assertFalse(m.containsKey("name"));
    }

    @Test
    void buildMessagesNonToolRoleHasNoNameField() {
        Message userMsg = Message.builder()
                .role("user")
                .content("hello")
                .build();

        List<Map<String, Object>> result = invoker.buildMessages(List.of(userMsg));

        assertEquals(1, result.size());
        Map<String, Object> m = result.get(0);
        assertEquals("user", m.get("role"));
        assertFalse(m.containsKey("name"));
        assertFalse(m.containsKey("tool_call_id"));
    }

    @Test
    void buildEmbeddingsUrlAppendsEmbeddingsPath() {
        assertEquals("https://api.openai.com/embeddings", invoker.buildEmbeddingsUrl());
    }

    @Test
    void buildEmbeddingRequestBodyWithSingleInput() {
        EmbeddingRequest request = EmbeddingRequest.builder()
                .model("text-embedding-3-small")
                .input("hello world")
                .build();

        Map<String, Object> body = invoker.buildEmbeddingRequestBody(request);

        assertEquals("text-embedding-3-small", body.get("model"));
        assertEquals("hello world", body.get("input"));
    }

    @Test
    void buildEmbeddingRequestBodyWithInputList() {
        EmbeddingRequest request = EmbeddingRequest.builder()
                .model("text-embedding-3-small")
                .inputList(List.of("a", "b"))
                .build();

        Map<String, Object> body = invoker.buildEmbeddingRequestBody(request);

        assertEquals("text-embedding-3-small", body.get("model"));
        assertEquals(List.of("a", "b"), body.get("input"));
    }

    @Test
    void buildEmbeddingRequestBodyInputListWinsOverInput() {
        EmbeddingRequest request = EmbeddingRequest.builder()
                .model("text-embedding-3-small")
                .input("single")
                .inputList(List.of("a", "b"))
                .build();

        Map<String, Object> body = invoker.buildEmbeddingRequestBody(request);

        assertEquals(List.of("a", "b"), body.get("input"));
    }

    @Test
    void buildEmbeddingRequestBodyFallsBackToDefaultModel() {
        EmbeddingRequest request = EmbeddingRequest.builder()
                .input("hello")
                .build();

        Map<String, Object> body = invoker.buildEmbeddingRequestBody(request);

        assertEquals("gpt-4", body.get("model"));
        assertEquals("hello", body.get("input"));
    }

    @Test
    void buildEmbeddingRequestBodyWithNullInputThrows() {
        EmbeddingRequest request = EmbeddingRequest.builder()
                .model("text-embedding-3-small")
                .build();

        assertThrows(IllegalArgumentException.class, () -> invoker.buildEmbeddingRequestBody(request));
    }

    @Test
    void parseEmbeddingResponseExtractsEmbeddingsAndUsage() {
        String json = "{"
                + "\"data\":["
                + "{\"index\":0,\"embedding\":[0.1,0.2,0.3]},"
                + "{\"index\":1,\"embedding\":[0.4,0.5]}"
                + "],"
                + "\"usage\":{\"prompt_tokens\":2,\"completion_tokens\":0,\"total_tokens\":2}"
                + "}";

        EmbeddingResponse response = invoker.parseEmbeddingResponse(json);

        assertNotNull(response);
        assertNotNull(response.getEmbeddings());
        assertEquals(2, response.getEmbeddings().size());
        EmbeddingResponse.EmbeddingItem first = response.getEmbeddings().get(0);
        assertEquals(Integer.valueOf(0), first.getIndex());
        assertNotNull(first.getEmbedding());
        assertEquals(3, first.getEmbedding().size());
        assertEquals(0.1f, first.getEmbedding().get(0), 0.0001f);
        assertEquals(0.3f, first.getEmbedding().get(2), 0.0001f);
        assertEquals(Integer.valueOf(1), response.getEmbeddings().get(1).getIndex());
        assertNotNull(response.getUsage());
        assertEquals(2, response.getUsage().getPromptTokens());
        assertEquals(0, response.getUsage().getCompletionTokens());
        assertEquals(2, response.getUsage().getTotalTokens());
    }

    @Test
    void parseEmbeddingResponseEmptyData() {
        String json = "{\"data\":[],\"usage\":{\"prompt_tokens\":0,\"completion_tokens\":0,\"total_tokens\":0}}";

        EmbeddingResponse response = invoker.parseEmbeddingResponse(json);

        assertNotNull(response);
        assertNotNull(response.getEmbeddings());
        assertTrue(response.getEmbeddings().isEmpty());
        assertNotNull(response.getUsage());
    }

    @Test
    void parseEmbeddingResponseMalformedJsonThrows() {
        assertThrows(Exception.class, () -> invoker.parseEmbeddingResponse("{invalid"));
    }

    @Test
    void parseStreamChunkMapsFinishReasonValues() {
        assertEquals(FinishReason.LENGTH, invoker.parseStreamChunk(
                "{\"choices\":[{\"delta\":{},\"finish_reason\":\"length\"}]}").getFinishReason());
        assertEquals(FinishReason.TOOL_CALLS, invoker.parseStreamChunk(
                "{\"choices\":[{\"delta\":{},\"finish_reason\":\"tool_calls\"}]}").getFinishReason());
        assertEquals(FinishReason.CONTENT_FILTER, invoker.parseStreamChunk(
                "{\"choices\":[{\"delta\":{},\"finish_reason\":\"content_filter\"}]}").getFinishReason());
        assertNull(invoker.parseStreamChunk(
                "{\"choices\":[{\"delta\":{},\"finish_reason\":\"unknown\"}]}").getFinishReason());
    }

    @Test
    void parseResponseMapsStopFinishReason() {
        String json = "{\"choices\":[{\"message\":{\"content\":\"hi\"},\"finish_reason\":\"stop\"}]}";

        ChatResponse response = invoker.parseResponse(json);

        assertEquals(FinishReason.STOP, response.getFinishReason());
    }

    @Test
    void handleStreamErrorReturnsErrorFinishReason() {
        ChatChunk chunk = invoker.handleStreamError(new RuntimeException("boom")).blockFirst();

        assertEquals(FinishReason.ERROR, chunk.getFinishReason());
        assertNotNull(chunk.getDelta());
    }
}
