package com.ghost616.platform.controller;

import com.ghost616.agentbase.dto.model.Message;
import com.ghost616.agentbase.dto.model.ToolInfo;
import com.ghost616.agentbase.service.agent.MessageDataProvider;
import com.ghost616.platform.dto.ApiResponse;
import com.ghost616.platform.dto.PageResult;
import com.ghost616.platform.dto.memory.MemoryPromptSaveRequest;
import com.ghost616.platform.dto.memory.MemoryRegenerateRequest;
import com.ghost616.platform.dto.memory.MemoryRegenerateStatusDTO;
import com.ghost616.platform.dto.memory.MemoryUpdateRequest;
import com.ghost616.platform.dto.session.SessionDTO;
import com.ghost616.platform.dto.session.SessionMessageDTO;
import com.ghost616.platform.dto.session.SubSessionDataDTO;
import com.ghost616.platform.enums.AggregationType;
import com.ghost616.platform.enums.ErrorCode;
import com.ghost616.platform.exception.BusinessException;
import com.ghost616.platform.model.SessionMemoryDocument;
import com.ghost616.platform.service.agent.DefaultMessageDataProvider;
import com.ghost616.platform.service.agent.DefaultSubSessionCallback;
import com.ghost616.platform.service.memory.SessionMemoryService;
import com.ghost616.platform.service.message.MessageService;
import com.ghost616.platform.service.search.SessionMemoryESClient;
import com.ghost616.platform.service.session.SessionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionControllerTest {

    @Mock
    private SessionService sessionService;

    @Mock
    private DefaultSubSessionCallback subSessionCallback;

    @Mock
    private SessionMemoryService sessionMemoryService;

    @Mock
    private SessionMemoryESClient sessionMemoryESClient;

    @Mock
    private MessageService messageService;

    @Mock
    private DefaultMessageDataProvider defaultMessageDataProvider;

    @InjectMocks
    private SessionController controller;

    @Test
    void getSubSessionDataShouldMapThinkingField() {
        DefaultSubSessionCallback.SubSessionData data = mock(DefaultSubSessionCallback.SubSessionData.class);
        when(data.getChildSessionId()).thenReturn(100L);
        when(data.getUserMessage()).thenReturn("hello");
        when(data.getThinking()).thenReturn(true);
        when(subSessionCallback.getSubSessionData(1L)).thenReturn(data);

        ApiResponse<SubSessionDataDTO> response = controller.getSubSessionData(1L);

        assertTrue(response.isSuccess());
        SubSessionDataDTO dto = response.getData();
        assertNotNull(dto);
        assertEquals(100L, dto.getChildSessionId());
        assertEquals("hello", dto.getUserMessage());
        assertTrue(dto.getThinking());
    }

    @Test
    void getSubSessionDataShouldMapThinkingNull() {
        DefaultSubSessionCallback.SubSessionData data = mock(DefaultSubSessionCallback.SubSessionData.class);
        when(data.getChildSessionId()).thenReturn(200L);
        when(data.getUserMessage()).thenReturn("test");
        when(data.getThinking()).thenReturn(null);
        when(subSessionCallback.getSubSessionData(2L)).thenReturn(data);

        ApiResponse<SubSessionDataDTO> response = controller.getSubSessionData(2L);

        assertTrue(response.isSuccess());
        SubSessionDataDTO dto = response.getData();
        assertNull(dto.getThinking());
    }

    @Test
    void getSubSessionDataShouldMapThinkingFalse() {
        DefaultSubSessionCallback.SubSessionData data = mock(DefaultSubSessionCallback.SubSessionData.class);
        when(data.getChildSessionId()).thenReturn(300L);
        when(data.getUserMessage()).thenReturn("no");
        when(data.getThinking()).thenReturn(false);
        when(subSessionCallback.getSubSessionData(3L)).thenReturn(data);

        ApiResponse<SubSessionDataDTO> response = controller.getSubSessionData(3L);

        assertTrue(response.isSuccess());
        SubSessionDataDTO dto = response.getData();
        assertFalse(dto.getThinking());
    }

    @Test
    void getSubSessionDataShouldReturnNullWhenDataNotFound() {
        when(subSessionCallback.getSubSessionData(999L)).thenReturn(null);

        ApiResponse<SubSessionDataDTO> response = controller.getSubSessionData(999L);

        assertTrue(response.isSuccess());
        assertNull(response.getData());
    }

    @Test
    void triggerSessionMemory_shouldTriggerAndReturnSuccess() {
        ApiResponse<Void> response = controller.triggerSessionMemory(1L);

        assertTrue(response.isSuccess());
        assertEquals("记忆摘要生成已触发", response.getMessage());
        verify(sessionMemoryService).triggerSessionMemory(1L);
    }

    @Test
    void triggerSessionMemory_shouldPropagateSessionNotFound() {
        doThrow(new BusinessException(ErrorCode.SESSION_NOT_FOUND))
                .when(sessionMemoryService).triggerSessionMemory(999L);

        BusinessException ex = assertThrows(BusinessException.class, () -> controller.triggerSessionMemory(999L));

        assertEquals(ErrorCode.SESSION_NOT_FOUND, ex.getErrorCode());
        verify(sessionMemoryService).triggerSessionMemory(999L);
    }

    @Test
    void completeSubSession_shouldPropagateToolInfoAndMapToolCalls() {
        CompletableFuture<Message> future = new CompletableFuture<>();
        DefaultSubSessionCallback.SubSessionData data =
                new DefaultSubSessionCallback.SubSessionData(100L, "hi", false, future);
        when(subSessionCallback.getSubSessionData(1L)).thenReturn(data);

        ToolInfo toolInfo = new ToolInfo("call-1", "getWeather");
        SessionMessageDTO assistantMsg = buildMessageDTO(
                "assistant", "response", "thinking", toolInfo, "tc1", "func1", "{}");
        when(sessionService.getMessages(100L)).thenReturn(List.of(
                buildMessageDTO("user", "hi", null, null, null, null, null),
                assistantMsg));

        ApiResponse<Void> response = controller.completeSubSession(1L);

        assertTrue(response.isSuccess());
        Message completed = future.join();
        assertNotNull(completed);
        assertEquals("assistant", completed.getRole());
        assertEquals("response", completed.getContent());
        assertEquals("thinking", completed.getReasoning());
        assertNotNull(completed.getToolInfo());
        assertEquals("call-1", completed.getToolInfo().toolCallId());
        assertEquals("getWeather", completed.getToolInfo().toolName());
        assertNotNull(completed.getToolCalls());
        assertEquals(1, completed.getToolCalls().size());
        assertEquals("tc1", completed.getToolCalls().get(0).getId());
        assertEquals("func1", completed.getToolCalls().get(0).getName());
        assertEquals("{}", completed.getToolCalls().get(0).getArguments());
    }

    @Test
    void completeSubSession_shouldCompleteWithNullToolInfo() {
        CompletableFuture<Message> future = new CompletableFuture<>();
        DefaultSubSessionCallback.SubSessionData data =
                new DefaultSubSessionCallback.SubSessionData(200L, "hi", false, future);
        when(subSessionCallback.getSubSessionData(1L)).thenReturn(data);

        when(sessionService.getMessages(200L)).thenReturn(List.of(
                buildMessageDTO("assistant", "plain", null, null, null, null, null)));

        ApiResponse<Void> response = controller.completeSubSession(1L);

        assertTrue(response.isSuccess());
        Message completed = future.join();
        assertNull(completed.getToolInfo());
        assertNull(completed.getToolCalls());
    }

    @Test
    void completeSubSession_shouldFailWhenDataNotFound() {
        when(subSessionCallback.getSubSessionData(999L)).thenReturn(null);

        ApiResponse<Void> response = controller.completeSubSession(999L);

        assertFalse(response.isSuccess());
        assertEquals("SESSION-004", response.getCode());
        verify(sessionService, never()).getMessages(anyLong());
    }

    @Test
    void completeSubSession_shouldFailWhenNoMessages() {
        CompletableFuture<Message> future = new CompletableFuture<>();
        DefaultSubSessionCallback.SubSessionData data =
                new DefaultSubSessionCallback.SubSessionData(300L, "hi", false, future);
        when(subSessionCallback.getSubSessionData(1L)).thenReturn(data);
        when(sessionService.getMessages(300L)).thenReturn(List.of());

        ApiResponse<Void> response = controller.completeSubSession(1L);

        assertFalse(response.isSuccess());
        assertEquals("SESSION-005", response.getCode());
    }

    @Test
    void completeSubSession_shouldFailWhenNoAssistantMessage() {
        CompletableFuture<Message> future = new CompletableFuture<>();
        DefaultSubSessionCallback.SubSessionData data =
                new DefaultSubSessionCallback.SubSessionData(400L, "hi", false, future);
        when(subSessionCallback.getSubSessionData(1L)).thenReturn(data);
        when(sessionService.getMessages(400L)).thenReturn(List.of(
                buildMessageDTO("user", "hi", null, null, null, null, null)));

        ApiResponse<Void> response = controller.completeSubSession(1L);

        assertFalse(response.isSuccess());
        assertEquals("SESSION-005", response.getCode());
    }

    @Test
    void queryMemory_shouldDelegateToESClientAndReturnData() {
        SessionMemoryDocument doc = SessionMemoryDocument.builder()
                .sessionId("100")
                .aggregationType(AggregationType.GROUP)
                .aggregationStartSeq(1)
                .aggregationEndSeq(3)
                .aggregationText("摘要")
                .build();
        PageResult<SessionMemoryDocument> pageResult =
                new PageResult<>(List.of(doc), 5L, 1, 20);
        when(sessionMemoryESClient.queryBySessionId("100", null, AggregationType.GROUP, 1, 20))
                .thenReturn(pageResult);

        ApiResponse<PageResult<SessionMemoryDocument>> response =
                controller.queryMemory(100L, AggregationType.GROUP, 1, 20);

        assertTrue(response.isSuccess());
        assertSame(pageResult, response.getData());
        verify(sessionMemoryESClient).queryBySessionId("100", null, AggregationType.GROUP, 1, 20);
    }

    @Test
    void queryMemory_shouldForwardNonDefaultPageAndSize() {
        SessionMemoryDocument doc = SessionMemoryDocument.builder().sessionId("7").build();
        PageResult<SessionMemoryDocument> pageResult =
                new PageResult<>(List.of(doc), 2L, 3, 50);
        when(sessionMemoryESClient.queryBySessionId("7", null, AggregationType.DAILY, 3, 50))
                .thenReturn(pageResult);

        ApiResponse<PageResult<SessionMemoryDocument>> response =
                controller.queryMemory(7L, AggregationType.DAILY, 3, 50);

        assertTrue(response.isSuccess());
        assertEquals(3, response.getData().getPage());
        assertEquals(50, response.getData().getSize());
        verify(sessionMemoryESClient).queryBySessionId("7", null, AggregationType.DAILY, 3, 50);
    }

    @Test
    void queryMemory_shouldReturnEmptyResultWhenNoDocuments() {
        PageResult<SessionMemoryDocument> pageResult = new PageResult<>(List.of(), 0L, 1, 20);
        when(sessionMemoryESClient.queryBySessionId("999", null, AggregationType.GROUP, 1, 20))
                .thenReturn(pageResult);

        ApiResponse<PageResult<SessionMemoryDocument>> response =
                controller.queryMemory(999L, AggregationType.GROUP, 1, 20);

        assertTrue(response.isSuccess());
        assertNotNull(response.getData());
        assertTrue(response.getData().getList().isEmpty());
        assertEquals(0L, response.getData().getTotal());
    }

    @Test
    void queryMemory_shouldPropagateClientException() {
        when(sessionMemoryESClient.queryBySessionId(anyString(), any(), any(), anyInt(), anyInt()))
                .thenThrow(new IllegalStateException("按会话查询记忆文档失败: session_memory"));

        assertThrows(IllegalStateException.class,
                () -> controller.queryMemory(1L, AggregationType.GROUP, 1, 20));
    }

    @Test
    void getMessagesBySeqRange_shouldConvertToDTOAndReturnMessages() {
        com.ghost616.platform.entity.Message msg = new com.ghost616.platform.entity.Message();
        msg.setId(1L);
        msg.setSessionId(100L);
        msg.setSequenceNum(2);
        SessionMessageDTO dto =
                buildMessageDTO("user", "hello", null, null, null, null, null);
        when(messageService.getMessagesBySeqRange(100L, 1, 5))
                .thenReturn(List.of(msg));
        when(defaultMessageDataProvider.toSessionMessageDTOs(List.of(msg)))
                .thenReturn(List.of(dto));

        ApiResponse<List<SessionMessageDTO>> response =
                controller.getMessagesBySeqRange(100L, 1, 5);

        assertTrue(response.isSuccess());
        assertEquals(1, response.getData().size());
        assertEquals("hello", response.getData().get(0).getContent());
        verify(messageService).getMessagesBySeqRange(100L, 1, 5);
        verify(defaultMessageDataProvider).toSessionMessageDTOs(List.of(msg));
    }

    @Test
    void getMessagesBySeqRange_shouldReturnEmptyListWhenNoMessages() {
        when(messageService.getMessagesBySeqRange(999L, 10, 20))
                .thenReturn(List.of());
        when(defaultMessageDataProvider.toSessionMessageDTOs(List.of()))
                .thenReturn(List.of());

        ApiResponse<List<SessionMessageDTO>> response =
                controller.getMessagesBySeqRange(999L, 10, 20);

        assertTrue(response.isSuccess());
        assertNotNull(response.getData());
        assertTrue(response.getData().isEmpty());
        verify(messageService).getMessagesBySeqRange(999L, 10, 20);
        verify(defaultMessageDataProvider).toSessionMessageDTOs(List.of());
    }

    @Test
    void getMemoryPrompt_shouldDelegateAndReturnPrompt() {
        when(sessionMemoryService.getMemoryPrompt(100L)).thenReturn("自定义提示语");

        ApiResponse<String> response = controller.getMemoryPrompt(100L);

        assertTrue(response.isSuccess());
        assertEquals("自定义提示语", response.getData());
        verify(sessionMemoryService).getMemoryPrompt(100L);
    }

    @Test
    void saveMemoryPrompt_shouldDelegateAndReturnSuccess() {
        MemoryPromptSaveRequest request = new MemoryPromptSaveRequest("新提示语");

        ApiResponse<Void> response = controller.saveMemoryPrompt(100L, request);

        assertTrue(response.isSuccess());
        verify(sessionMemoryService).saveMemoryPrompt(100L, "新提示语");
    }

    @Test
    void regenerateSummary_shouldDelegateAndReturnStatus() {
        MemoryRegenerateStatusDTO status = MemoryRegenerateStatusDTO.builder()
                .sessionId(100L)
                .docId("100_GROUP_2_3")
                .status("RUNNING")
                .build();
        when(sessionMemoryService.regenerateSummary(100L, "100_GROUP_2_3", 2, 3, "请总结"))
                .thenReturn(status);
        MemoryRegenerateRequest request = MemoryRegenerateRequest.builder()
                .docId("100_GROUP_2_3")
                .startSeq(2)
                .endSeq(3)
                .prompt("请总结")
                .build();

        ApiResponse<MemoryRegenerateStatusDTO> response = controller.regenerateSummary(100L, request);

        assertTrue(response.isSuccess());
        assertSame(status, response.getData());
        verify(sessionMemoryService).regenerateSummary(100L, "100_GROUP_2_3", 2, 3, "请总结");
    }

    @Test
    void getRegenerateStatus_shouldDelegateAndReturnStatus() {
        MemoryRegenerateStatusDTO status = MemoryRegenerateStatusDTO.builder()
                .sessionId(100L)
                .docId("100_GROUP_2_3")
                .status("COMPLETED")
                .aggregationText("摘要")
                .build();
        when(sessionMemoryService.getRegenerateStatus(100L)).thenReturn(status);

        ApiResponse<MemoryRegenerateStatusDTO> response = controller.getRegenerateStatus(100L);

        assertTrue(response.isSuccess());
        assertSame(status, response.getData());
        verify(sessionMemoryService).getRegenerateStatus(100L);
    }

    @Test
    void saveAggregationText_shouldDelegateAndReturnSuccess() {
        MemoryUpdateRequest request = MemoryUpdateRequest.builder()
                .docId("100_GROUP_2_3")
                .text("更新后的摘要")
                .build();

        ApiResponse<Void> response = controller.saveAggregationText(100L, request);

        assertTrue(response.isSuccess());
        verify(sessionMemoryService).saveAggregationText(100L, "100_GROUP_2_3", "更新后的摘要");
    }

    @Test
    void listLogSessions_shouldDelegateAndReturnSessions() {
        SessionDTO dto = SessionDTO.builder()
                .id(100L)
                .agentId(5L)
                .title("评估会话")
                .isEvaluation(true)
                .isChild(false)
                .build();
        when(sessionService.listLogSessions()).thenReturn(List.of(dto));

        ApiResponse<List<SessionDTO>> response = controller.listLogSessions();

        assertTrue(response.isSuccess());
        assertSame(dto, response.getData().get(0));
        verify(sessionService).listLogSessions();
    }

    @Test
    void listLogSessions_shouldReturnEmptyListWhenNoSessions() {
        when(sessionService.listLogSessions()).thenReturn(List.of());

        ApiResponse<List<SessionDTO>> response = controller.listLogSessions();

        assertTrue(response.isSuccess());
        assertNotNull(response.getData());
        assertTrue(response.getData().isEmpty());
        verify(sessionService).listLogSessions();
    }

    private SessionMessageDTO buildMessageDTO(String role, String content, String reasoning,
                                                           ToolInfo toolInfo, String tcId,
                                                           String tcName, String tcArgs) {
        List<MessageDataProvider.ToolCallData> toolCalls = tcId == null ? null
                : List.of(new MessageDataProvider.ToolCallData(tcId, tcName, tcArgs));
        return SessionMessageDTO.builder()
                .id("1")
                .sessionId("100")
                .role(role)
                .content(content)
                .reasoning(reasoning)
                .toolInfo(toolInfo)
                .sequenceNum(1)
                .createTime(LocalDateTime.now())
                .toolCalls(toolCalls)
                .rollback(false)
                .build();
    }
}
