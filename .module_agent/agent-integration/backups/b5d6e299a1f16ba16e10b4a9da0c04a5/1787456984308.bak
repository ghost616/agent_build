package com.ghost616.agentinteg.model.invoker;

import com.ghost616.agentbase.dto.model.ChatRequest;
import com.ghost616.agentbase.dto.model.Message;
import com.ghost616.agentbase.dto.model.ToolDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class KimiInvokerTest {

    private KimiInvoker invoker;

    @BeforeEach
    void setUp() {
        invoker = new KimiInvoker(
                "test-key", "https://api.kimi.com", "kimi-k2.5",
                0.7, 2048,
                RestClient.builder(), WebClient.builder()
        );
    }

    private ChatRequest baseRequest() {
        return ChatRequest.builder()
                .messages(List.of(Message.builder().role("user").content("hi").build()))
                .build();
    }

    @Test
    void buildRequestBody_withoutBuiltinTools_shouldNotAddToolsKey() {
        Map<String, Object> body = invoker.buildRequestBody(baseRequest(), false);

        assertNull(body.get("tools"));
    }

    @Test
    void buildRequestBody_withEmptyBuiltinTools_shouldNotAddToolsKey() {
        ChatRequest request = baseRequest();
        request.setBuiltinTools(List.of());

        Map<String, Object> body = invoker.buildRequestBody(request, false);

        assertNull(body.get("tools"));
    }

    @Test
    void buildRequestBody_withBuiltinToolsOnly_shouldCreateNewToolsKey() {
        ChatRequest request = baseRequest();
        request.setBuiltinTools(List.of(
                Map.of("type", "web_search", "web_search", Map.of("enabled", true))
        ));

        Map<String, Object> body = invoker.buildRequestBody(request, false);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tools = (List<Map<String, Object>>) body.get("tools");
        assertNotNull(tools);
        assertEquals(1, tools.size());
        assertEquals("web_search", tools.get(0).get("type"));
    }

    @Test
    void buildRequestBody_withBuiltinToolsAndCustomTools_shouldMerge() {
        ChatRequest request = baseRequest();
        request.setTools(List.of(
                ToolDefinition.builder().name("get_weather").description("weather").build()
        ));
        request.setBuiltinTools(List.of(
                Map.of("type", "web_search", "web_search", Map.of("enabled", true))
        ));

        Map<String, Object> body = invoker.buildRequestBody(request, false);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tools = (List<Map<String, Object>>) body.get("tools");
        assertNotNull(tools);
        assertEquals(2, tools.size());
        assertTrue(tools.stream().anyMatch(t -> {
            Object function = t.get("function");
            return function instanceof Map
                    && "get_weather".equals(((Map<?, ?>) function).get("name"));
        }));
        assertTrue(tools.stream().anyMatch(t -> "web_search".equals(t.get("type"))));
    }

    @Test
    void buildRequestBody_withMultipleBuiltinTools_shouldAppendAll() {
        ChatRequest request = baseRequest();
        request.setBuiltinTools(List.of(
                Map.of("type", "web_search"),
                Map.of("type", "code_interpreter")
        ));

        Map<String, Object> body = invoker.buildRequestBody(request, false);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tools = (List<Map<String, Object>>) body.get("tools");
        assertNotNull(tools);
        assertEquals(2, tools.size());
    }
}
