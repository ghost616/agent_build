package com.ghost616.platform.controller;

import com.ghost616.platform.dto.ApiResponse;
import com.ghost616.platform.dto.PageResult;
import com.ghost616.platform.dto.session.CreateSessionRequest;
import com.ghost616.platform.dto.session.SessionDTO;
import com.ghost616.platform.dto.session.SubSessionDataDTO;
import com.ghost616.platform.service.session.SessionService;
import com.ghost616.platform.service.message.MessageService;
import com.ghost616.platform.service.memory.SessionMemoryService;
import com.ghost616.platform.dto.memory.MemoryPromptSaveRequest;
import com.ghost616.platform.dto.memory.MemoryRegenerateRequest;
import com.ghost616.platform.dto.memory.MemoryRegenerateStatusDTO;
import com.ghost616.platform.dto.memory.MemoryUpdateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import com.ghost616.agentbase.dto.model.Message;
import com.ghost616.agentbase.dto.model.ToolCall;
import com.ghost616.platform.enums.AggregationType;
import com.ghost616.platform.enums.ErrorCode;
import com.ghost616.platform.dto.session.SessionMessageDTO;
import com.ghost616.platform.model.SessionMemoryDocument;
import com.ghost616.platform.service.agent.DefaultMessageDataProvider;
import com.ghost616.platform.service.agent.DefaultSubSessionCallback;
import com.ghost616.platform.service.search.SessionMemoryESClient;
import com.ghost616.platform.session.UserContextUtil;
import com.ghost616.platform.util.IdConverter;

import java.util.Map;


@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;
    private final DefaultSubSessionCallback subSessionCallback;
    private final SessionMemoryService sessionMemoryService;
    private final SessionMemoryESClient sessionMemoryESClient;
    private final MessageService messageService;
    private final DefaultMessageDataProvider defaultMessageDataProvider;

    @GetMapping
    public ApiResponse<List<SessionDTO>> listSessions(@RequestParam(required = false) Long agentId) {
        List<SessionDTO> result = sessionService.listSessions(agentId);
        return ApiResponse.success(result);
    }

    @GetMapping("/log-sessions")
    public ApiResponse<List<SessionDTO>> listLogSessions() {
        List<SessionDTO> result = sessionService.listLogSessions();
        return ApiResponse.success(result);
    }

    @PostMapping
    public ApiResponse<SessionDTO> createSession(@Valid @RequestBody CreateSessionRequest request) {
        SessionDTO result = sessionService.createSession(
                request.getAgentId(), request.getModelId(), request.getTitle());
        return ApiResponse.success(result);
    }

    @GetMapping("/{id}")
    public ApiResponse<SessionDTO> getSession(@PathVariable Long id) {
        SessionDTO result = sessionService.getSession(id);
        return ApiResponse.success(result);
    }

    @GetMapping("/{id}/messages")
    public ApiResponse<List<SessionMessageDTO>> getMessages(@PathVariable Long id) {
        List<SessionMessageDTO> result = sessionService.getMessages(id);
        return ApiResponse.success(result);
    }

    @GetMapping("/{id}/messages/range")
    public ApiResponse<List<SessionMessageDTO>> getMessagesBySeqRange(
            @PathVariable Long id,
            @RequestParam Integer startSeq,
            @RequestParam Integer endSeq) {
        List<SessionMessageDTO> result =
                defaultMessageDataProvider.toSessionMessageDTOs(messageService.getMessagesBySeqRange(id, startSeq, endSeq));
        return ApiResponse.success(result);
    }

    @PostMapping("/{id}/rollback")
    public ApiResponse<Integer> rollback(@PathVariable Long id) {
        int deleted = sessionService.rollback(id);
        return ApiResponse.success(deleted);
    }

    @PutMapping("/{id}/thinking")
    public ApiResponse<Void> updateThinking(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        sessionService.updateThinking(id, body.get("thinking"));
        return ApiResponse.success(null);
    }

    @GetMapping("/{id}/children")
    public ApiResponse<List<SessionDTO>> listChildSessions(@PathVariable Long id) {
        List<SessionDTO> result = sessionService.listChildSessions(id);
        return ApiResponse.success(result);
    }

    @GetMapping("/{id}/memory/trigger")
    public ApiResponse<Void> triggerSessionMemory(@PathVariable Long id) {
        sessionMemoryService.triggerSessionMemory(id);
        return ApiResponse.success("记忆摘要生成已触发", null);
    }

    @GetMapping("/{id}/memory-prompt")
    public ApiResponse<String> getMemoryPrompt(@PathVariable Long id) {
        String prompt = sessionMemoryService.getMemoryPrompt(id);
        return ApiResponse.success(prompt);
    }

    @PutMapping("/{id}/memory-prompt")
    public ApiResponse<Void> saveMemoryPrompt(@PathVariable Long id,
                                              @RequestBody MemoryPromptSaveRequest request) {
        sessionMemoryService.saveMemoryPrompt(id, request.getPrompt());
        return ApiResponse.success(null);
    }

    @PostMapping("/{id}/memory/regenerate")
    public ApiResponse<MemoryRegenerateStatusDTO> regenerateSummary(
            @PathVariable Long id,
            @RequestBody MemoryRegenerateRequest request) {
        MemoryRegenerateStatusDTO status = sessionMemoryService.regenerateSummary(
                id, request.getDocId(), request.getStartSeq(), request.getEndSeq(), request.getPrompt());
        return ApiResponse.success("聚合文本重生成已触发", status);
    }

    @GetMapping("/{id}/memory/regenerate/status")
    public ApiResponse<MemoryRegenerateStatusDTO> getRegenerateStatus(@PathVariable Long id) {
        MemoryRegenerateStatusDTO status = sessionMemoryService.getRegenerateStatus(id);
        return ApiResponse.success(status);
    }

    @PostMapping("/{id}/memory/update")
    public ApiResponse<Void> saveAggregationText(@PathVariable Long id,
                                                 @RequestBody MemoryUpdateRequest request) {
        sessionMemoryService.saveAggregationText(id, request.getDocId(), request.getText());
        return ApiResponse.success(null);
    }

    @GetMapping("/{id}/memory")
    public ApiResponse<PageResult<SessionMemoryDocument>> queryMemory(@PathVariable Long id,
                                                                      @RequestParam AggregationType type,
                                                                      @RequestParam(defaultValue = "1") int page,
                                                                      @RequestParam(defaultValue = "20") int size) {
        PageResult<SessionMemoryDocument> result = sessionMemoryESClient.queryBySessionId(
                IdConverter.toString(id), UserContextUtil.currentUserIdOrNull(), type, page, size);
        return ApiResponse.success(result);
    }

    @GetMapping("/{id}/sub-session-data")
    public ApiResponse<SubSessionDataDTO> getSubSessionData(@PathVariable Long id) {
        DefaultSubSessionCallback.SubSessionData data = subSessionCallback.getSubSessionData(id);
        if (data == null) {
            return ApiResponse.success(null);
        }
        SubSessionDataDTO result = SubSessionDataDTO.builder()
                .childSessionId(data.getChildSessionId())
                .userMessage(data.getUserMessage())
                .thinking(data.getThinking())
                .build();
        return ApiResponse.success(result);
    }

    @PostMapping("/{id}/complete-sub-session")
    public ApiResponse<Void> completeSubSession(@PathVariable Long id) {
        DefaultSubSessionCallback.SubSessionData data = subSessionCallback.getSubSessionData(id);
        if (data == null) {
            return ApiResponse.fail(ErrorCode.SUB_SESSION_DATA_NOT_FOUND);
        }
        List<SessionMessageDTO> messages = sessionService.getMessages(data.getChildSessionId());
        if (messages == null || messages.isEmpty()) {
            return ApiResponse.fail(ErrorCode.CHILD_SESSION_NO_MESSAGES);
        }
        SessionMessageDTO lastAssistantMsg = null;
        for (int i = messages.size() - 1; i >= 0; i--) {
            if ("assistant".equals(messages.get(i).getRole())) {
                lastAssistantMsg = messages.get(i);
                break;
            }
        }
        if (lastAssistantMsg == null) {
            return ApiResponse.fail(ErrorCode.CHILD_SESSION_NO_MESSAGES);
        }
        List<ToolCall> toolCalls = null;
        if (lastAssistantMsg.getToolCalls() != null && !lastAssistantMsg.getToolCalls().isEmpty()) {
            toolCalls = lastAssistantMsg.getToolCalls().stream()
                    .map(tc -> ToolCall.builder()
                            .id(tc.toolCallId())
                            .name(tc.toolCallName())
                            .arguments(tc.toolCallArguments())
                            .build())
                    .toList();
        }
        Message message = Message.builder()
                .role(lastAssistantMsg.getRole())
                .content(lastAssistantMsg.getContent())
                .reasoning(lastAssistantMsg.getReasoning())
                .toolInfo(lastAssistantMsg.getToolInfo())
                .toolCalls(toolCalls)
                .build();
        data.getMessageResult().complete(message);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteSession(@PathVariable Long id) {
        sessionService.deleteSession(id);
        return ApiResponse.success(null);
    }
}
