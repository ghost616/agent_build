import type { ApiResponse } from '../types/common';
import type { Session, CreateSessionParams, SessionMessage, WebSearchCall, ChatRequest } from '../types/session';
import api from './api';

export async function listSessions(agentId?: string): Promise<Session[]> {
  const params = agentId ? { agentId } : undefined;
  const res = await api.get<ApiResponse<Session[]>>('/sessions', { params });
  return res.data.data;
}

/**
 * 获取当前用户的所有主会话（含评估会话），用于会话日志页，按创建时间倒序。
 * @returns 主会话列表（isChild 为 null/false，不过滤 isEvaluation）
 */
export async function listLogSessions(): Promise<Session[]> {
  const res = await api.get<ApiResponse<Session[]>>('/sessions/log-sessions');
  return res.data.data;
}

export async function createSession(data: CreateSessionParams): Promise<Session> {
  const res = await api.post<ApiResponse<Session>>('/sessions', data);
  return res.data.data;
}

export async function getSession(id: string): Promise<Session> {
  const res = await api.get<ApiResponse<Session>>(`/sessions/${id}`);
  return res.data.data;
}

export interface SessionContextBasic {
  sessionId: string;
  agentId: string;
  modelId: string;
  lastResponseId?: string;
  parentSessionId?: string;
}

export async function getSessionContextBasic(sessionId: string): Promise<SessionContextBasic> {
  const res = await api.get<ApiResponse<SessionContextBasic>>(`/context/${sessionId}/basic`);
  return res.data.data;
}

export async function deleteSession(id: string): Promise<void> {
  await api.delete(`/sessions/${id}`);
}

export async function listChildSessions(parentId: string): Promise<Session[]> {
  const res = await api.get<ApiResponse<Session[]>>(`/sessions/${parentId}/children`);
  return res.data.data;
}

export async function getSessionMessages(sessionId: string): Promise<SessionMessage[]> {
  const res = await api.get<ApiResponse<SessionMessage[]>>(`/sessions/${sessionId}/messages`);
  return res.data.data;
}

/**
 * 按序列号区间查询会话消息。
 * @param sessionId 会话 ID
 * @param startSeq 起始序列号
 * @param endSeq 结束序列号
 * @returns 区间内的消息列表
 */
export async function getSessionMessagesRange(
  sessionId: string,
  startSeq: number,
  endSeq: number,
): Promise<SessionMessage[]> {
  const res = await api.get<ApiResponse<SessionMessage[]>>(
    `/sessions/${sessionId}/messages/range`,
    { params: { startSeq, endSeq } },
  );
  return res.data.data;
}

export async function getConversationMessages(conversationId: string): Promise<SessionMessage[]> {
  const res = await api.get<ApiResponse<SessionMessage[]>>(`/conversations/${conversationId}/messages`);
  return res.data.data;
}

export interface ChatChunk {
  delta?: string;
  reasoning?: string;
  responseId?: string;
  finishReason?: string;
  hasToolCalls?: boolean;
  webSearchCall?: WebSearchCall[];
  customToolCall?: Record<string, unknown>;
}

export interface StreamCallbacks {
  onDelta: (text: string) => void;
  onReasoning: (text: string) => void;
  onResponseId?: (id: string) => void;
  onWebSearchCall?: (calls: WebSearchCall[]) => void;
  onDone: (hasToolCalls: boolean) => void;
  onError: (err: Error) => void;
}

export async function processSSEStream(
  response: Response,
  callbacks: StreamCallbacks,
): Promise<void> {
  if (!response.ok) {
    const errorText = await response.text().catch(() => '');
    let errorMsg = `请求失败 (${response.status})`;
    try {
      const parsed = JSON.parse(errorText);
      errorMsg = parsed.message || errorMsg;
    } catch {
      // ignore parse error
    }
    throw new Error(errorMsg);
  }

  const reader = response.body?.getReader();
  if (!reader) {
    throw new Error('无法获取响应流');
  }

  const decoder = new TextDecoder();
  let buffer = '';

  while (true) {
    const { done, value } = await reader.read();
    if (done) {
      callbacks.onDone(false);
      return;
    }

    buffer += decoder.decode(value, { stream: true });
    const lines = buffer.split('\n');
    buffer = lines.pop() || '';

    for (const line of lines) {
      if (!line.trim()) continue;
      const jsonStr = line.startsWith('data:') ? line.slice(5) : line;
      try {
        const chunk: ChatChunk = JSON.parse(jsonStr);
        if (chunk.finishReason === 'error') {
          callbacks.onError(new Error(chunk.delta || '请求失败'));
          continue;
        }
        if (chunk.finishReason === 'stop') {
          callbacks.onDone(chunk.hasToolCalls || false);
          return;
        }
        if (chunk.reasoning) {
          callbacks.onReasoning(chunk.reasoning);
        }
        if (chunk.responseId) {
          callbacks.onResponseId?.(chunk.responseId);
        }
        if (chunk.webSearchCall) {
          callbacks.onWebSearchCall?.(chunk.webSearchCall);
        }
        if (chunk.delta) {
          callbacks.onDelta(chunk.delta);
        }
      } catch {
        // ignore parse error
      }
    }
  }
}
export async function fetchConversationId(): Promise<string> {
  const res = await api.get<ApiResponse<{ conversationId: string }>>('/conversation-id');
  return res.data.data.conversationId;
}

export function agentChatStream(
  params: ChatRequest,
  callbacks: StreamCallbacks,
): AbortController {
  const controller = new AbortController();

  const run = async (): Promise<void> => {
    try {
      const response = await fetch('/api/chat', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(params),
        signal: controller.signal,
      });
      await processSSEStream(response, callbacks);
    } catch (err) {
      if (err instanceof DOMException && err.name === 'AbortError') {
        callbacks.onDone(false);
        return;
      }
      callbacks.onError(err instanceof Error ? err : new Error(String(err)));
    }
  };

  run();
  return controller;
}

export interface ExecuteToolsResult {
  status: string;
  toolId?: string;
  toolName?: string;
  arguments?: string;
  hasMore: boolean;
  needsSubSessionFlow?: boolean;
}

export interface ToolStatusResult {
  status: string;
  toolId: string;
  toolName: string;
  arguments: string;
  result?: string;
  hasMore?: boolean;
  needsSubSessionFlow?: boolean;
  toolConfig?: { id: string; subToolType: string; toolName: string };
}

export interface SubSessionData {
  childSessionId: string;
  userMessage: string;
  thinking?: boolean;
}

export async function getSubSessionData(sessionId: string): Promise<SubSessionData | null> {
  const res = await api.get<ApiResponse<SubSessionData | null>>(
    `/sessions/${sessionId}/sub-session-data`,
  );
  return res.data.data;
}

export async function completeSubSession(sessionId: string): Promise<void> {
  await api.post(`/sessions/${sessionId}/complete-sub-session`);
}

export async function stopChat(sessionId: string): Promise<void> {
  await api.post(`/chat/${sessionId}/stop`);
}

export async function rollbackSession(sessionId: string): Promise<void> {
  await api.post(`/sessions/${sessionId}/rollback`);
}

export async function executeTools(sessionId: string): Promise<ExecuteToolsResult> {
  const res = await api.post<ApiResponse<ExecuteToolsResult>>(
    `/chat/${sessionId}/execute-tools`,
  );
  return res.data.data;
}

export async function getToolStatus(sessionId: string, toolId: string): Promise<ToolStatusResult> {
  const res = await api.get<ApiResponse<ToolStatusResult>>(
    `/chat/${sessionId}/tool-status?toolId=${encodeURIComponent(toolId)}`,
  );
  return res.data.data;
}

export function continueChatStream(
  sessionId: string,
  callbacks: StreamCallbacks,
): AbortController {
  const controller = new AbortController();

  const run = async (): Promise<void> => {
    try {
      const response = await fetch(`/api/chat/${sessionId}/continue`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        signal: controller.signal,
      });
      await processSSEStream(response, callbacks);
    } catch (err) {
      if (err instanceof DOMException && err.name === 'AbortError') {
        callbacks.onDone(false);
        return;
      }
      callbacks.onError(err instanceof Error ? err : new Error(String(err)));
    }
  };

  run();
  return controller;
}

export async function updateSessionThinking(sessionId: string, thinking: boolean): Promise<void> {
  await api.put(`/sessions/${sessionId}/thinking`, { thinking });
}

export async function getBrowserExtension(): Promise<string> {
  const response = await fetch('/api/browser-tool/extension');
  return response.text();
}

export async function getToolScript(toolConfigId: string): Promise<string> {
  const res = await api.get<ApiResponse<string>>(
    `/browser-tool/tool-script/${encodeURIComponent(toolConfigId)}`,
  );
  return res.data.data;
}


