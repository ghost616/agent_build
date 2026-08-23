package com.ghost616.agentbase.service.agent;

import com.ghost616.agentbase.core.AgentComponentRegistry;
import com.ghost616.agentbase.dto.model.ToolInfo;
import com.ghost616.agentbase.dto.model.UsageInfo;
import com.ghost616.agentbase.enums.AgentErrorCode;
import com.ghost616.agentbase.enums.LogLevel;
import com.ghost616.agentbase.enums.LogType;
import com.ghost616.agentbase.exception.AgentException;
import com.ghost616.agentbase.service.agent.log.AgentLog;
import com.ghost616.agentbase.service.agent.log.LogData;
import com.ghost616.agentbase.service.agent.log.MessageQueryLogData;
import com.ghost616.agentbase.service.agent.log.MessageRollbackLogData;
import com.ghost616.agentbase.service.agent.log.MessageSaveLogData;
import com.ghost616.agentbase.service.agent.log.SessionErrorLogData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionManagerTest {

    @Mock
    private MessageDataProvider dataProvider;
    @Mock
    private AgentLog agentLog;

    private AgentComponentRegistry registry;
    private SessionManager sessionManager;

    @BeforeEach
    void setUp() {
        registry = new AgentComponentRegistry();
        registry.setMessageDataProvider(dataProvider);
        registry.setAgentLog(agentLog);
        sessionManager = new SessionManager(registry);
    }

    @Test
    void save_sessionId为null时抛出AgentException() {
        SessionManager.MessageSaveBuilder builder = sessionManager.messageSave()
                .role("user")
                .content("hello");
        AgentException ex = assertThrows(AgentException.class, builder::save);
        assertEquals(AgentErrorCode.PARAM_INVALID, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("sessionId 不能为空"));
    }

    @Test
    void save_role为null时抛出AgentException() {
        SessionManager.MessageSaveBuilder builder = sessionManager.messageSave()
                .sessionId("1")
                .content("hello");
        AgentException ex = assertThrows(AgentException.class, builder::save);
        assertEquals(AgentErrorCode.PARAM_INVALID, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("role 不能为空"));
    }

    @Test
    void save_content为null时抛出AgentException() {
        SessionManager.MessageSaveBuilder builder = sessionManager.messageSave()
                .sessionId("1")
                .role("user");
        AgentException ex = assertThrows(AgentException.class, builder::save);
        assertEquals(AgentErrorCode.PARAM_INVALID, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("content 不能为空"));
    }

    @Test
    void save_参数均非null时正常调用dataProvider() {
        when(dataProvider.saveMessage("1", "user", "hello", null, null, null, null, null, null, null, null, null, null))
                .thenReturn("100");

        String result = sessionManager.messageSave()
                .sessionId("1")
                .role("user")
                .content("hello")
                .save();

        assertEquals("100", result);
        verify(dataProvider).saveMessage("1", "user", "hello", null, null, null, null, null, null, null, null, null, null);
    }

    @Test
    void save_参数均非null时正常调用dataProvider_withAllFields() {
        var toolCalls = java.util.List.of(
                new MessageDataProvider.ToolCallData("tc1", "getWeather", "{}"));
        when(dataProvider.saveMessage("1", "assistant", "response", "thinking...",
                new ToolInfo("tc1", "getWeather"), "result_ok", toolCalls, null, null, null, null, null, null)).thenReturn("200");

        String result = sessionManager.messageSave()
                .sessionId("1")
                .role("assistant")
                .content("response")
                .reasoning("thinking...")
                .toolInfo(new ToolInfo("tc1", "getWeather"))
                .toolResult("result_ok")
                .toolCalls(toolCalls)
                .save();

        assertEquals("200", result);
        verify(dataProvider).saveMessage("1", "assistant", "response", "thinking...",
                new ToolInfo("tc1", "getWeather"), "result_ok", toolCalls, null, null, null, null, null, null);
    }

    @Test
    void save_带images_透传给dataProvider() {
        var images = List.of(
                com.ghost616.agentbase.dto.model.ImageContent.builder().imgId("img-1").imgText("data:image/png;base64,AAA").build());
        when(dataProvider.saveMessage("1", "user", "看图", null, null, null, null, null, null, null, null, images, null))
                .thenReturn("300");

        String result = sessionManager.messageSave()
                .sessionId("1")
                .role("user")
                .content("看图")
                .images(images)
                .save();

        assertEquals("300", result);
        verify(dataProvider).saveMessage("1", "user", "看图", null, null, null, null, null, null, null, null, images, null);
    }

    @Test
    void save_带userInput_透传给dataProvider() {
        when(dataProvider.saveMessage("1", "user", "hello", null, null, null, null, null, null, null, null, null, false))
                .thenReturn("400");

        String result = sessionManager.messageSave()
                .sessionId("1")
                .role("user")
                .content("hello")
                .userInput(false)
                .save();

        assertEquals("400", result);
        verify(dataProvider).saveMessage("1", "user", "hello", null, null, null, null, null, null, null, null, null, false);
    }

    // ========== 智能体日志 ==========

    @Test
    void save成功时应记录MESSAGE_SAVE日志() {
        when(dataProvider.saveMessage("1", "user", "hello", null, null, null, null, null, null, null, null, null, null))
                .thenReturn("100");

        String result = sessionManager.messageSave()
                .sessionId("1").role("user").content("hello").save();

        assertEquals("100", result);
        ArgumentCaptor<LogData> captor = ArgumentCaptor.forClass(LogData.class);
        verify(agentLog).addLog(captor.capture());
        LogData logData = captor.getValue();
        assertEquals(LogType.MESSAGE_SAVE, logData.logType());
        MessageSaveLogData saveLog = (MessageSaveLogData) logData;
        assertEquals(LogLevel.INFO, saveLog.getLogLevel());
        assertEquals("1", saveLog.getSessionId());
        assertEquals("user", saveLog.getRole());
        assertEquals("100", saveLog.getMessageId());
        assertEquals("hello", saveLog.getContent());
    }

    @Test
    void save成功时应记录MESSAGE_SAVE全字段日志() {
        var toolCalls = List.of(
                new MessageDataProvider.ToolCallData("tc1", "getWeather", "{}"));
        var webSearchCalls = List.of(
                new MessageDataProvider.WebSearchCallData("i1", 0, List.of()));
        var customToolCalls = List.of(
                new MessageDataProvider.CustomToolCallData("i1", 0, "{}", "{}"));
        UsageInfo usage = new UsageInfo(1, 2, 3);
        when(dataProvider.saveMessage("1", "assistant", "response", "thinking...",
                new ToolInfo("tc1", "getWeather"), "result_ok", toolCalls, usage, webSearchCalls, customToolCalls, "conv-1", null, null))
                .thenReturn("200");

        sessionManager.messageSave()
                .sessionId("1")
                .role("assistant")
                .content("response")
                .reasoning("thinking...")
                .toolInfo(new ToolInfo("tc1", "getWeather"))
                .toolResult("result_ok")
                .toolCalls(toolCalls)
                .usage(usage)
                .webSearchCall(webSearchCalls)
                .customToolCall(customToolCalls)
                .conversationId("conv-1")
                .save();

        ArgumentCaptor<LogData> captor = ArgumentCaptor.forClass(LogData.class);
        verify(agentLog).addLog(captor.capture());
        MessageSaveLogData saveLog = (MessageSaveLogData) captor.getValue();
        assertEquals("200", saveLog.getMessageId());
        assertEquals("response", saveLog.getContent());
        assertEquals("thinking...", saveLog.getReasoning());
        assertEquals(new ToolInfo("tc1", "getWeather"), saveLog.getToolInfo());
        assertEquals("result_ok", saveLog.getToolResult());
        assertEquals(toolCalls, saveLog.getToolCalls());
        assertSame(usage, saveLog.getUsage());
        assertEquals(webSearchCalls, saveLog.getWebSearchCall());
        assertEquals(customToolCalls, saveLog.getCustomToolCall());
        assertEquals("conv-1", saveLog.getConversationId());
    }

    @Test
    void save校验失败时应记录ERROR日志() {
        SessionManager.MessageSaveBuilder builder = sessionManager.messageSave()
                .role("user").content("hello");
        assertThrows(AgentException.class, builder::save);

        ArgumentCaptor<LogData> captor = ArgumentCaptor.forClass(LogData.class);
        verify(agentLog).addLog(captor.capture());
        LogData logData = captor.getValue();
        assertEquals(LogType.ERROR_LOG, logData.logType());
        SessionErrorLogData errorLog = (SessionErrorLogData) logData;
        assertEquals(LogLevel.ERROR, errorLog.getLogLevel());
        assertNull(errorLog.getSessionId());
        assertNull(errorLog.getConversationId());
        assertEquals(AgentErrorCode.PARAM_INVALID.getCode(), errorLog.getErrorCode());
        assertTrue(errorLog.getMessage().contains("sessionId 不能为空"));
    }

    @Test
    void getMessages应记录MESSAGE_QUERY日志() {
        when(dataProvider.getMessages("1")).thenReturn(List.of(
                new MessageDataProvider.MessageDTO("m1", "1", "user", "hello", null, null,
                        LocalDateTime.now(), null, null, null, null, null, null, null, null, null)));

        sessionManager.getMessages("1");

        ArgumentCaptor<LogData> captor = ArgumentCaptor.forClass(LogData.class);
        verify(agentLog).addLog(captor.capture());
        LogData logData = captor.getValue();
        assertEquals(LogType.MESSAGE_QUERY, logData.logType());
        MessageQueryLogData queryLog = (MessageQueryLogData) logData;
        assertEquals(LogLevel.INFO, queryLog.getLogLevel());
        assertEquals("1", queryLog.getSessionId());
        assertEquals(1, queryLog.getMessageCount());
    }

    @Test
    void rollbackToLastUserMessage应记录MESSAGE_ROLLBACK日志() {
        when(dataProvider.rollbackToLastUserMessage("1")).thenReturn(2);

        int count = sessionManager.rollbackToLastUserMessage("1");

        assertEquals(2, count);
        ArgumentCaptor<LogData> captor = ArgumentCaptor.forClass(LogData.class);
        verify(agentLog).addLog(captor.capture());
        LogData logData = captor.getValue();
        assertEquals(LogType.MESSAGE_ROLLBACK, logData.logType());
        MessageRollbackLogData rollbackLog = (MessageRollbackLogData) logData;
        assertEquals(LogLevel.INFO, rollbackLog.getLogLevel());
        assertEquals("1", rollbackLog.getSessionId());
        assertEquals(2, rollbackLog.getRollbackCount());
    }

    @Test
    void agentLog为null时addLog静默跳过() {
        registry.setAgentLog(null);

        sessionManager.getMessages("1");

        verify(agentLog, never()).addLog(any());
    }

    @Test
    void agentLog抛异常时不中断主流程() {
        doThrow(new RuntimeException("log failure")).when(agentLog).addLog(any());
        when(dataProvider.saveMessage("1", "user", "hello", null, null, null, null, null, null, null, null, null, null))
                .thenReturn("100");

        String result = sessionManager.messageSave()
                .sessionId("1").role("user").content("hello").save();

        assertEquals("100", result);
    }
}
