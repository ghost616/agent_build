package com.ghost616.agentinteg.model.invoker;

import com.ghost616.agentbase.dto.model.ChatChunk;
import com.ghost616.agentbase.dto.model.ChatRequest;
import com.ghost616.agentbase.dto.model.ImageContent;
import com.ghost616.agentbase.dto.model.Message;
import com.ghost616.agentbase.enums.FinishReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class OllamaInvokerTest {

    private OllamaInvoker invoker;

    @BeforeEach
    void setUp() {
        invoker = new OllamaInvoker(
                "", "http://localhost:11434", "llama3",
                null, null,
                RestClient.builder(), WebClient.builder()
        );
    }

    private ChatChunk parseStreamChunk(String json) throws Exception {
        Method method = OllamaInvoker.class.getDeclaredMethod("parseStreamChunk", String.class);
        method.setAccessible(true);
        return (ChatChunk) method.invoke(invoker, json);
    }

    @Test
    void doneTrueWithUsage() throws Exception {
        String json = "{"
                + "\"message\":{\"content\":\"Hello\"},"
                + "\"done\":true,"
                + "\"done_reason\":\"stop\","
                + "\"eval_count\":50,"
                + "\"prompt_eval_count\":10"
                + "}";

        ChatChunk chunk = parseStreamChunk(json);

        assertNotNull(chunk);
        assertEquals("Hello", chunk.getDelta());
        assertEquals(FinishReason.STOP, chunk.getFinishReason());
        assertNotNull(chunk.getUsage());
        assertEquals(10, chunk.getUsage().getPromptTokens());
        assertEquals(50, chunk.getUsage().getCompletionTokens());
        assertNull(chunk.getUsage().getTotalTokens());
    }

    @Test
    void doneTrueWithoutUsageFields() throws Exception {
        String json = "{"
                + "\"message\":{\"content\":\"Hi\"},"
                + "\"done\":true,"
                + "\"done_reason\":\"stop\""
                + "}";

        ChatChunk chunk = parseStreamChunk(json);

        assertNotNull(chunk);
        assertEquals("Hi", chunk.getDelta());
        assertEquals(FinishReason.STOP, chunk.getFinishReason());
        assertNull(chunk.getUsage());
    }

    @Test
    void doneFalseDoesNotSetUsage() throws Exception {
        String json = "{"
                + "\"message\":{\"content\":\"thinking\"},"
                + "\"done\":false"
                + "}";

        ChatChunk chunk = parseStreamChunk(json);

        assertNotNull(chunk);
        assertEquals("thinking", chunk.getDelta());
        assertNull(chunk.getFinishReason());
        assertNull(chunk.getUsage());
    }

    @Test
    void doneTrueWithOnlyEvalCount() throws Exception {
        String json = "{"
                + "\"done\":true,"
                + "\"eval_count\":100"
                + "}";

        ChatChunk chunk = parseStreamChunk(json);

        assertNotNull(chunk);
        assertNull(chunk.getDelta());
        assertNull(chunk.getUsage().getPromptTokens());
        assertEquals(100, chunk.getUsage().getCompletionTokens());
    }

    @Test
    void doneTrueWithOnlyPromptEvalCount() throws Exception {
        String json = "{"
                + "\"done\":true,"
                + "\"done_reason\":\"stop\","
                + "\"prompt_eval_count\":25"
                + "}";

        ChatChunk chunk = parseStreamChunk(json);

        assertNotNull(chunk);
        assertEquals(FinishReason.STOP, chunk.getFinishReason());
        assertEquals(25, chunk.getUsage().getPromptTokens());
        assertNull(chunk.getUsage().getCompletionTokens());
    }

    @Test
    void malformedJsonReturnsEmptyChunk() throws Exception {
        ChatChunk chunk = parseStreamChunk("not json");

        assertNotNull(chunk);
        assertNull(chunk.getDelta());
        assertNull(chunk.getUsage());
    }

    @Test
    void doneTrueNullMessage() throws Exception {
        String json = "{"
                + "\"done\":true,"
                + "\"done_reason\":\"stop\","
                + "\"eval_count\":30,"
                + "\"prompt_eval_count\":5"
                + "}";

        ChatChunk chunk = parseStreamChunk(json);

        assertNotNull(chunk);
        assertNull(chunk.getDelta());
        assertEquals(FinishReason.STOP, chunk.getFinishReason());
        assertEquals(5, chunk.getUsage().getPromptTokens());
        assertEquals(30, chunk.getUsage().getCompletionTokens());
    }

    private FinishReason mapDoneReason(String doneReason) throws Exception {
        Method method = OllamaInvoker.class.getDeclaredMethod("mapDoneReason", String.class);
        method.setAccessible(true);
        return (FinishReason) method.invoke(invoker, doneReason);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildRequestBody(ChatRequest request, boolean stream) throws Exception {
        Method method = OllamaInvoker.class.getDeclaredMethod("buildRequestBody",
                ChatRequest.class, boolean.class);
        method.setAccessible(true);
        return (Map<String, Object>) method.invoke(invoker, request, stream);
    }

    @Test
    void buildRequestBody_userWithImages_addsImagesField() throws Exception {
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(Message.builder()
                        .role("user")
                        .content("describe the image")
                        .images(List.of(
                                ImageContent.builder().imgId("img-1")
                                        .imgText("data:image/png;base64,AAA").build(),
                                ImageContent.builder().imgId("img-2")
                                        .imgText("data:image/jpeg;base64,BBB").build()))
                        .build()))
                .build();

        Map<String, Object> body = buildRequestBody(request, false);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages = (List<Map<String, Object>>) body.get("messages");
        assertEquals(1, messages.size());
        Map<String, Object> m = messages.get(0);
        assertEquals("user", m.get("role"));
        assertEquals("describe the image", m.get("content"));
        assertEquals(List.of("data:image/png;base64,AAA", "data:image/jpeg;base64,BBB"),
                m.get("images"));
    }

    @Test
    void buildRequestBody_userWithoutImages_omitsImagesField() throws Exception {
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(Message.builder().role("user").content("hi").build()))
                .build();

        Map<String, Object> body = buildRequestBody(request, false);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages = (List<Map<String, Object>>) body.get("messages");
        assertEquals(1, messages.size());
        assertFalse(messages.get(0).containsKey("images"));
    }

    @Test
    void buildRequestBody_userWithEmptyImages_omitsImagesField() throws Exception {
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(Message.builder()
                        .role("user")
                        .content("hi")
                        .images(List.of())
                        .build()))
                .build();

        Map<String, Object> body = buildRequestBody(request, false);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages = (List<Map<String, Object>>) body.get("messages");
        assertEquals(1, messages.size());
        assertFalse(messages.get(0).containsKey("images"));
    }

    @Test
    void mapDoneReasonMapsKnownValues() throws Exception {
        assertEquals(FinishReason.STOP, mapDoneReason("stop"));
        assertEquals(FinishReason.STOP, mapDoneReason("load"));
        assertEquals(FinishReason.STOP, mapDoneReason("unload"));
        assertEquals(FinishReason.ERROR, mapDoneReason("error"));
    }

    @Test
    void mapDoneReasonUnknownOrBlankReturnsNull() throws Exception {
        assertNull(mapDoneReason("unknown"));
        assertNull(mapDoneReason(""));
        assertNull(mapDoneReason(null));
    }
}
