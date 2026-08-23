package com.ghost616.agentinteg.model.invoker;

import com.ghost616.agentbase.dto.model.ChatChunk;
import com.ghost616.agentbase.enums.FinishReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.lang.reflect.Method;

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
