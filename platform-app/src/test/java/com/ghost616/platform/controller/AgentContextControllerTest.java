package com.ghost616.platform.controller;

import com.ghost616.agentbase.dto.model.ToolInfo;
import com.ghost616.agentbase.service.agent.AgentContextManager;
import com.ghost616.agentbase.service.agent.AgentExecutionContext;
import com.ghost616.agentbase.service.agent.ContextDataProvider;
import com.ghost616.platform.dto.AgentContextBasicDTO;
import com.ghost616.platform.dto.AgentContextDTO;
import com.ghost616.platform.dto.ApiResponse;
import com.ghost616.platform.dto.context.ConversationVariableRequest;
import com.ghost616.platform.dto.context.SessionVariableRequest;
import com.ghost616.platform.entity.Session;
import com.ghost616.platform.repository.SessionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentContextControllerTest {

    @Mock
    private AgentContextManager agentContextManager;

    @Mock
    private SessionMapper sessionMapper;

    @InjectMocks
    private AgentContextController controller;

    @Test
    void getContext_shouldReturnAgentContextDTO() {
        AgentContextManager.AgentSessionContext sessionCtx = mock(AgentContextManager.AgentSessionContext.class);
        AgentExecutionContext ctx = mock(AgentExecutionContext.class);
        when(agentContextManager.get("1")).thenReturn(sessionCtx);
        when(sessionCtx.agentContextData()).thenReturn(ctxData());
        when(sessionCtx.context()).thenReturn(ctx);
        when(ctx.getSessionId()).thenReturn("1");
        when(ctx.getHistory()).thenReturn(List.of());
        when(ctx.getTools()).thenReturn(List.of());
        when(ctx.getSkills()).thenReturn(List.of());
        when(ctx.getProjectDir()).thenReturn("/project");
        when(ctx.getLastResponseId()).thenReturn("resp_001");
        when(ctx.getSessionVariableKeys()).thenReturn(Set.of());
        when(ctx.getConversationVariableKeys()).thenReturn(Set.of());

        ApiResponse<AgentContextDTO> response = controller.getContext(1L);

        assertTrue(response.isSuccess());
        AgentContextDTO dto = response.getData();
        assertNotNull(dto);
        assertEquals(1L, dto.getSessionId());
        assertEquals(100L, dto.getAgentId());
        assertEquals("prompt", dto.getSystemPrompt());
        assertEquals(200L, dto.getModelId());
        assertNull(dto.getParentSessionId());
        assertEquals(10, dto.getRecentMessageCount());
        assertEquals("resp_001", dto.getLastResponseId());
    }

    @Test
    void getContext_shouldMapHistoryEntries() {
        AgentContextManager.AgentSessionContext sessionCtx = mock(AgentContextManager.AgentSessionContext.class);
        AgentExecutionContext ctx = mock(AgentExecutionContext.class);
        when(agentContextManager.get("1")).thenReturn(sessionCtx);
        when(sessionCtx.agentContextData()).thenReturn(ctxData());
        when(sessionCtx.context()).thenReturn(ctx);

        AgentExecutionContext.HistoryEntry entry = new AgentExecutionContext.HistoryEntry(
                "user", "hello", null, new ToolInfo("call-1", "getWeather"),
                LocalDateTime.of(2026, 7, 24, 12, 0), List.of(), null, null, null, null, null);
        when(ctx.getHistory()).thenReturn(List.of(entry));
        when(ctx.getSessionVariableKeys()).thenReturn(Set.of());
        when(ctx.getConversationVariableKeys()).thenReturn(Set.of());

        ApiResponse<AgentContextDTO> response = controller.getContext(1L);

        assertTrue(response.isSuccess());
        List<AgentContextDTO.HistoryEntryDTO> history = response.getData().getHistory();
        assertEquals(1, history.size());
        assertEquals("user", history.get(0).getRole());
        assertEquals("hello", history.get(0).getContent());
    }

    @Test
    void getContext_shouldMapSessionVariables() {
        AgentContextManager.AgentSessionContext sessionCtx = mock(AgentContextManager.AgentSessionContext.class);
        AgentExecutionContext ctx = mock(AgentExecutionContext.class);
        when(agentContextManager.get("1")).thenReturn(sessionCtx);
        when(sessionCtx.agentContextData()).thenReturn(ctxData());
        when(sessionCtx.context()).thenReturn(ctx);
        when(ctx.getHistory()).thenReturn(List.of());
        when(ctx.getSessionVariableKeys()).thenReturn(Set.of("key1", "key2"));
        when(ctx.getSessionVariable("key1")).thenReturn("value1");
        when(ctx.getSessionVariable("key2")).thenReturn("value2");
        when(ctx.getConversationVariableKeys()).thenReturn(Set.of());

        ApiResponse<AgentContextDTO> response = controller.getContext(1L);

        assertTrue(response.isSuccess());
        Map<String, String> vars = response.getData().getSessionVariables();
        assertEquals(2, vars.size());
        assertEquals("value1", vars.get("key1"));
        assertEquals("value2", vars.get("key2"));
    }

    @Test
    void getContext_shouldMapConversationVariables() {
        AgentContextManager.AgentSessionContext sessionCtx = mock(AgentContextManager.AgentSessionContext.class);
        AgentExecutionContext ctx = mock(AgentExecutionContext.class);
        when(agentContextManager.get("1")).thenReturn(sessionCtx);
        when(sessionCtx.agentContextData()).thenReturn(ctxData());
        when(sessionCtx.context()).thenReturn(ctx);
        when(ctx.getHistory()).thenReturn(List.of());
        when(ctx.getSessionVariableKeys()).thenReturn(Set.of());
        when(ctx.getConversationVariableKeys()).thenReturn(Set.of("ck1", "ck2"));
        when(ctx.getConversationVariable("ck1")).thenReturn("cv1");
        when(ctx.getConversationVariable("ck2")).thenReturn("cv2");

        ApiResponse<AgentContextDTO> response = controller.getContext(1L);

        assertTrue(response.isSuccess());
        Map<String, String> vars = response.getData().getConversationVariables();
        assertEquals(2, vars.size());
        assertEquals("cv1", vars.get("ck1"));
        assertEquals("cv2", vars.get("ck2"));
    }

    @Test
    void getContext_shouldReadBasicInfoFromAgentContextData() {
        AgentContextManager.AgentSessionContext sessionCtx = mock(AgentContextManager.AgentSessionContext.class);
        AgentExecutionContext ctx = mock(AgentExecutionContext.class);
        ContextDataProvider.AgentContextData ctxData = new ContextDataProvider.AgentContextData(
                "100", "dataPrompt", "200", 10, List.of(), Map.of(), "50", List.of(), "resp_001", null);
        when(agentContextManager.get("1")).thenReturn(sessionCtx);
        when(sessionCtx.agentContextData()).thenReturn(ctxData);
        when(sessionCtx.context()).thenReturn(ctx);
        // ctx 上暴露与 AgentContextData 不同的基本信息，验证 getContext 从 agentContextData 读取而非懒构建 context
        when(ctx.getSessionId()).thenReturn("1");
        lenient().when(ctx.getAgentId()).thenReturn("999");
        lenient().when(ctx.getSystemPrompt()).thenReturn("ctxPrompt");
        lenient().when(ctx.getModelId()).thenReturn("999");
        lenient().when(ctx.getParentSessionId()).thenReturn("99");
        lenient().when(ctx.getRecentMessageCount()).thenReturn(99);
        when(ctx.getHistory()).thenReturn(List.of());
        when(ctx.getTools()).thenReturn(List.of());
        when(ctx.getSkills()).thenReturn(List.of());
        when(ctx.getProjectDir()).thenReturn("/project");
        when(ctx.getLastResponseId()).thenReturn("resp_001");
        when(ctx.getSessionVariableKeys()).thenReturn(Set.of());
        when(ctx.getConversationVariableKeys()).thenReturn(Set.of());

        ApiResponse<AgentContextDTO> response = controller.getContext(1L);

        assertTrue(response.isSuccess());
        AgentContextDTO dto = response.getData();
        assertEquals(100L, dto.getAgentId());
        assertEquals("dataPrompt", dto.getSystemPrompt());
        assertEquals(200L, dto.getModelId());
        assertEquals(50L, dto.getParentSessionId());
        assertEquals(10, dto.getRecentMessageCount());
        // 需要完整上下文的字段仍来自懒构建 context
        assertEquals("/project", dto.getProjectDir());
        assertEquals("resp_001", dto.getLastResponseId());
    }

    @Test
    void getContext_shouldReturnErrorWhenSessionContextNotFound() {
        when(agentContextManager.get("999")).thenReturn(null);

        ApiResponse<AgentContextDTO> response = controller.getContext(999L);

        assertFalse(response.isSuccess());
        assertEquals("CONTEXT-001", response.getCode());
    }

    @Test
    void getContext_shouldReturnErrorWhenAgentContextDataNull() {
        AgentContextManager.AgentSessionContext sessionCtx = mock(AgentContextManager.AgentSessionContext.class);
        when(agentContextManager.get("999")).thenReturn(sessionCtx);
        when(sessionCtx.agentContextData()).thenReturn(null);

        ApiResponse<AgentContextDTO> response = controller.getContext(999L);

        assertFalse(response.isSuccess());
        assertEquals("CONTEXT-001", response.getCode());
        // 兜底分支：agentContextData 为 null 时不应触发懒构建 context
        verify(sessionCtx, never()).context();
    }

    @Test
    void getContextBasic_shouldReturnBasicFields() {
        Session session = new Session();
        session.setId(1L);
        session.setAgentId(100L);
        session.setModelId(200L);
        session.setLastResponseId("resp_001");
        session.setParentSessionId(null);
        when(sessionMapper.selectById(1L)).thenReturn(session);

        ApiResponse<AgentContextBasicDTO> response = controller.getContextBasic(1L);

        assertTrue(response.isSuccess());
        AgentContextBasicDTO dto = response.getData();
        assertNotNull(dto);
        assertEquals(1L, dto.getSessionId());
        assertEquals(100L, dto.getAgentId());
        assertEquals(200L, dto.getModelId());
        assertEquals("resp_001", dto.getLastResponseId());
        assertNull(dto.getParentSessionId());
    }

    @Test
    void getContextBasic_shouldReturnErrorWhenSessionNotFound() {
        when(sessionMapper.selectById(999L)).thenReturn(null);

        ApiResponse<AgentContextBasicDTO> response = controller.getContextBasic(999L);

        assertFalse(response.isSuccess());
        assertEquals("CONTEXT-001", response.getCode());
    }

    @Test
    void putSessionVariable_shouldPutVariable() {
        AgentContextManager.AgentSessionContext sessionCtx = mock(AgentContextManager.AgentSessionContext.class);
        AgentExecutionContext ctx = mock(AgentExecutionContext.class);
        when(agentContextManager.get("1")).thenReturn(sessionCtx);
        when(sessionCtx.context()).thenReturn(ctx);

        SessionVariableRequest body = new SessionVariableRequest("varKey", "varValue");
        ApiResponse<Void> response = controller.putSessionVariable(1L, body);

        assertTrue(response.isSuccess());
        verify(ctx).putSessionVariable("varKey", "varValue");
    }

    @Test
    void putSessionVariable_shouldReturnErrorWhenSessionContextNotFound() {
        when(agentContextManager.get("999")).thenReturn(null);

        ApiResponse<Void> response = controller.putSessionVariable(999L, new SessionVariableRequest("k", "v"));

        assertFalse(response.isSuccess());
        assertEquals("CONTEXT-001", response.getCode());
    }

    @Test
    void putConversationVariable_shouldPutVariable() {
        AgentContextManager.AgentSessionContext sessionCtx = mock(AgentContextManager.AgentSessionContext.class);
        AgentExecutionContext ctx = mock(AgentExecutionContext.class);
        when(agentContextManager.get("1")).thenReturn(sessionCtx);
        when(sessionCtx.context()).thenReturn(ctx);

        ConversationVariableRequest body = new ConversationVariableRequest("convKey", "convValue");
        ApiResponse<Void> response = controller.putConversationVariable(1L, body);

        assertTrue(response.isSuccess());
        verify(ctx).putConversationVariable("convKey", "convValue");
    }

    @Test
    void putConversationVariable_shouldReturnErrorWhenSessionContextNotFound() {
        when(agentContextManager.get("999")).thenReturn(null);

        ApiResponse<Void> response = controller.putConversationVariable(999L, new ConversationVariableRequest("k", "v"));

        assertFalse(response.isSuccess());
        assertEquals("CONTEXT-001", response.getCode());
    }

    @Test
    void putSessionVariable_shouldHandleMissingKey() {
        AgentContextManager.AgentSessionContext sessionCtx = mock(AgentContextManager.AgentSessionContext.class);
        AgentExecutionContext ctx = mock(AgentExecutionContext.class);
        when(agentContextManager.get("1")).thenReturn(sessionCtx);
        when(sessionCtx.context()).thenReturn(ctx);

        SessionVariableRequest body = SessionVariableRequest.builder().value("val").build();
        ApiResponse<Void> response = controller.putSessionVariable(1L, body);

        assertTrue(response.isSuccess());
        verify(ctx).putSessionVariable(null, "val");
    }

    @Test
    void putSessionVariable_shouldHandleMissingValue() {
        AgentContextManager.AgentSessionContext sessionCtx = mock(AgentContextManager.AgentSessionContext.class);
        AgentExecutionContext ctx = mock(AgentExecutionContext.class);
        when(agentContextManager.get("1")).thenReturn(sessionCtx);
        when(sessionCtx.context()).thenReturn(ctx);

        SessionVariableRequest body = SessionVariableRequest.builder().key("k").build();
        ApiResponse<Void> response = controller.putSessionVariable(1L, body);

        assertTrue(response.isSuccess());
        verify(ctx).putSessionVariable("k", null);
    }

    private ContextDataProvider.AgentContextData ctxData() {
        return new ContextDataProvider.AgentContextData(
                "100", "prompt", "200", 10, List.of(), Map.of(), null, List.of(), "resp_001", null);
    }
}
