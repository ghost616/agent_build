package com.ghost616.agentbase.service.agent.log;

import com.ghost616.agentbase.enums.LogType;
import com.ghost616.agentbase.sendmessage.HistoryMessage;
import com.ghost616.agentbase.sendmessage.SessionMessage;
import com.ghost616.agentbase.service.agent.AgentExecutionContext;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HandleMessageLogDataTest {

    private SessionMessage createMessage() {
        return new HistoryMessage("s1",
                new AgentExecutionContext.HistoryEntry("user", "hello", null, null,
                        LocalDateTime.now(), List.of(), null, null, null, null, null));
    }

    @Test
    void logType应返回HANDLE_MESSAGE() {
        HandleMessageLogData data = HandleMessageLogData.builder().build();
        assertEquals(LogType.HANDLE_MESSAGE, data.logType());
    }

    @Test
    void 字段应默认为null() {
        HandleMessageLogData data = HandleMessageLogData.builder().build();
        assertNull(data.getContext());
        assertNull(data.getLogLevel());
        assertNull(data.getSessionMessage());
    }

    @Test
    void builder应正确设置继承与自有字段() {
        SessionMessage message = createMessage();
        HandleMessageLogData data = HandleMessageLogData.builder()
                .sessionMessage(message)
                .build();

        assertSame(message, data.getSessionMessage());
        assertEquals("s1", data.getSessionMessage().getSessionId());
    }
}
