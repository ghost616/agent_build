package com.ghost616.agentinteg.model.invoker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghost616.agentbase.dto.model.ChatChunk;
import com.ghost616.agentbase.dto.model.UsageInfo;
import com.ghost616.agentbase.enums.FinishReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class AnthropicInvokerTest {

    private AnthropicInvoker invoker;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        invoker = new AnthropicInvoker(
                "test-key", "https://api.anthropic.com", "claude-3-opus-20240229",
                0.7, 4096,
                RestClient.builder(), WebClient.builder()
        );
    }

    private Object createSSERecord(String event, JsonNode data) throws Exception {
        Class<?> sseClass = Class.forName("com.ghost616.agentinteg.model.invoker.AnthropicInvoker$SSERecord");
        Constructor<?> ctor = sseClass.getDeclaredConstructor();
        ctor.setAccessible(true);
        Object record = ctor.newInstance();
        Field eventField = sseClass.getDeclaredField("event");
        eventField.setAccessible(true);
        eventField.set(record, event);
        Field dataField = sseClass.getDeclaredField("data");
        dataField.setAccessible(true);
        dataField.set(record, data);
        return record;
    }

    @SuppressWarnings("unchecked")
    private Flux<ChatChunk> invokeHandleStreamEvent(Object event,
            Map<Integer, Object> blockStates, String[] stopReason, UsageInfo[] usageHolder) throws Exception {
        Method method = AnthropicInvoker.class.getDeclaredMethod("handleStreamEvent",
                Class.forName("com.ghost616.agentinteg.model.invoker.AnthropicInvoker$SSERecord"),
                Map.class, String[].class, UsageInfo[].class);
        method.setAccessible(true);
        return (Flux<ChatChunk>) method.invoke(invoker, event, blockStates, stopReason, usageHolder);
    }

    @Test
    void messageDeltaWithUsage() throws Exception {
        String json = "{"
                + "\"type\":\"message_delta\","
                + "\"delta\":{\"stop_reason\":\"end_turn\"},"
                + "\"usage\":{\"input_tokens\":15,\"output_tokens\":25}"
                + "}";

        Object event = createSSERecord("message_delta", objectMapper.readTree(json));
        Map<Integer, Object> blockStates = new ConcurrentHashMap<>();
        String[] stopReason = new String[]{null};
        UsageInfo[] usageHolder = new UsageInfo[]{null};

        Flux<ChatChunk> result = invokeHandleStreamEvent(event, blockStates, stopReason, usageHolder);

        StepVerifier.create(result)
                .expectNextCount(0)
                .verifyComplete();

        assertEquals("end_turn", stopReason[0]);
        assertNotNull(usageHolder[0]);
        assertEquals(15, usageHolder[0].getPromptTokens());
        assertEquals(25, usageHolder[0].getCompletionTokens());
        assertNull(usageHolder[0].getTotalTokens());
    }

    @Test
    void messageDeltaWithoutUsage() throws Exception {
        String json = "{"
                + "\"type\":\"message_delta\","
                + "\"delta\":{\"stop_reason\":\"max_tokens\"}"
                + "}";

        Object event = createSSERecord("message_delta", objectMapper.readTree(json));
        Map<Integer, Object> blockStates = new ConcurrentHashMap<>();
        String[] stopReason = new String[]{null};
        UsageInfo[] usageHolder = new UsageInfo[]{null};

        Flux<ChatChunk> result = invokeHandleStreamEvent(event, blockStates, stopReason, usageHolder);

        StepVerifier.create(result)
                .expectNextCount(0)
                .verifyComplete();

        assertEquals("max_tokens", stopReason[0]);
        assertNull(usageHolder[0]);
    }

    @Test
    void messageDeltaWithPartialUsage() throws Exception {
        String json = "{"
                + "\"type\":\"message_delta\","
                + "\"delta\":{\"stop_reason\":\"end_turn\"},"
                + "\"usage\":{\"input_tokens\":10,\"output_tokens\":null}"
                + "}";

        Object event = createSSERecord("message_delta", objectMapper.readTree(json));
        Map<Integer, Object> blockStates = new ConcurrentHashMap<>();
        String[] stopReason = new String[]{null};
        UsageInfo[] usageHolder = new UsageInfo[]{null};

        Flux<ChatChunk> result = invokeHandleStreamEvent(event, blockStates, stopReason, usageHolder);

        StepVerifier.create(result)
                .expectNextCount(0)
                .verifyComplete();

        assertNotNull(usageHolder[0]);
        assertEquals(10, usageHolder[0].getPromptTokens());
        assertEquals(0, usageHolder[0].getCompletionTokens());
    }

    @Test
    void nonMessageDeltaEventDoesNotAffectUsage() throws Exception {
        String json = "{"
                + "\"type\":\"content_block_start\","
                + "\"index\":0,"
                + "\"content_block\":{\"type\":\"text\",\"text\":\"hello\"}"
                + "}";

        Object event = createSSERecord("content_block_start", objectMapper.readTree(json));
        Map<Integer, Object> blockStates = new ConcurrentHashMap<>();
        String[] stopReason = new String[]{null};
        UsageInfo[] usageHolder = new UsageInfo[]{null};

        Flux<ChatChunk> result = invokeHandleStreamEvent(event, blockStates, stopReason, usageHolder);

        StepVerifier.create(result)
                .expectNextCount(0)
                .verifyComplete();

        assertNull(stopReason[0]);
        assertNull(usageHolder[0]);
    }

    @Test
    void messageDeltaStopReasonCaptured() throws Exception {
        String json = "{"
                + "\"type\":\"message_delta\","
                + "\"delta\":{\"stop_reason\":\"tool_use\"}"
                + "}";

        Object event = createSSERecord("message_delta", objectMapper.readTree(json));
        Map<Integer, Object> blockStates = new ConcurrentHashMap<>();
        String[] stopReason = new String[]{null};
        UsageInfo[] usageHolder = new UsageInfo[]{null};

        Flux<ChatChunk> result = invokeHandleStreamEvent(event, blockStates, stopReason, usageHolder);

        StepVerifier.create(result)
                .expectNextCount(0)
                .verifyComplete();

        assertEquals("tool_use", stopReason[0]);
    }

    private FinishReason mapStopReason(String stopReason) throws Exception {
        Method method = AnthropicInvoker.class.getDeclaredMethod("mapStopReason", String.class);
        method.setAccessible(true);
        return (FinishReason) method.invoke(invoker, stopReason);
    }

    @Test
    void mapStopReasonMapsKnownValues() throws Exception {
        assertEquals(FinishReason.STOP, mapStopReason("end_turn"));
        assertEquals(FinishReason.STOP, mapStopReason("stop_sequence"));
        assertEquals(FinishReason.LENGTH, mapStopReason("max_tokens"));
        assertEquals(FinishReason.TOOL_CALLS, mapStopReason("tool_use"));
        assertEquals(FinishReason.ERROR, mapStopReason("error"));
    }

    @Test
    void mapStopReasonNullOrBlankMapsToStop() throws Exception {
        assertEquals(FinishReason.STOP, mapStopReason(null));
        assertEquals(FinishReason.STOP, mapStopReason(""));
    }

    @Test
    void mapStopReasonUnknownFallsBackToFromCode() throws Exception {
        assertNull(mapStopReason("unknown"));
    }
}
