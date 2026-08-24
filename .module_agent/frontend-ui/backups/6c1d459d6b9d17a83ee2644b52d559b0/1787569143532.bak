export interface Session {
  id: string;
  agentId: string;
  modelId: string;
  title: string;
  systemPrompt?: string;
  parentSessionId?: string;
  isChild?: boolean;
  /** 是否为评估会话（true 表示由评估执行创建的会话）。 */
  isEvaluation?: boolean;
  thinking?: boolean;
  createTime: string;
  updateTime: string;
  totalTokenUsed?: string;
}

export interface CreateSessionParams {
  agentId: string;
  modelId: string;
  title: string;
}

export interface ChatRequest {
  sessionId: string;
  content: string;
  modelId?: string;
  thinking?: boolean;
  previousResponseId?: string;
  conversationId?: string;
}

export interface ToolCallData {
  toolCallId: string;
  toolCallName: string;
  toolCallArguments: string;
}

export interface ToolInfo {
  toolCallId: string;
  toolName: string;
}

export interface WebSearchResult {
  title: string;
  url: string;
  snippet: string;
}

export interface WebSearchCall {
  itemId: string;
  outputIndex: number;
  results: WebSearchResult[];
}

export interface SessionMessage {
  id: string;
  sessionId: string;
  conversationId?: string;
  role: string;
  content: string;
  reasoning?: string;
  toolResult?: string;
  toolInfo?: ToolInfo;
  sequenceNum: number;
  createTime: string;
  toolCalls?: ToolCallData[];
  webSearchCall?: WebSearchCall[];
}
