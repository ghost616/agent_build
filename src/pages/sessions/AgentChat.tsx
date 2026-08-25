import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { Button, Dropdown, Input, message, Modal, Select, Spin, Switch, Tabs, Typography } from 'antd';
import type { TabsProps } from 'antd';
import {
  UserOutlined,
  RobotOutlined,
  ToolOutlined,
  InfoCircleOutlined,
  ArrowLeftOutlined,
  DownOutlined,
  UpOutlined,
  DeleteOutlined,
} from '@ant-design/icons';
import {
  agentChatStream,
  completeSubSession,
  continueChatStream,
  deleteSession,
  executeTools,
  fetchConversationId,
  getSession,
  getSessionContextBasic,
  getSessionMessages,
  getSubSessionData,
  getToolStatus,
  listChildSessions,
  rollbackSession,
  stopChat,
  updateSessionThinking,
} from '../../services/session';
import { executeBrowserTool } from '../../services/toolExecutor';
import { listModels } from '../../services/model';
import {
  registerSessionPage,
  SEND_USER_MESSAGE_MARKER,
  unregisterSessionPage,
} from '../../services/messageDispatcher';
import type { SendUserMessagePayload, SessionPageHandler } from '../../services/messageDispatcher';
import type { Session, SessionMessage, ToolInfo, WebSearchCall } from '../../types/session';
import type { ModelConfig } from '../../types/model';

type MessageRole = 'user' | 'assistant' | 'tool' | 'system';

interface ChatMessage {
  role: MessageRole;
  content: string;
  reasoning?: string;
  toolResult?: string;
  toolInfo?: ToolInfo;
  webSearchCall?: WebSearchCall[];
}

/** 子会话流式回复展示状态（写入子会话标签视图，由统一子会话执行器维护）。 */
interface ChildStreamState {
  messages: ChatMessage[];
  currentResponse: string;
  currentReasoning: string;
  loading: boolean;
  /** 子会话工具调用执行中（标签内展示执行中提示）。 */
  toolExecuting?: boolean;
  /** 子会话流程错误信息（标签内保留已产生消息并提示错误）。 */
  error?: string;
}

/** 子会话对话完整执行器参数（两种触发方式的入口差异仅在此参数）。 */
interface ChildSessionFlowParams {
  /** 子会话 ID。 */
  childId: string;
  /** 展示用用户消息内容。 */
  userContent: string;
  /** 传给对话接口的请求内容（WS 触发传 SEND_USER_MESSAGE_MARKER，工具触发传 data.userMessage）。 */
  streamContent: string;
  /** 思考模式（仅工具触发传 data.thinking）。 */
  thinking?: boolean;
  /** 是否自动切换标签（工具触发 true：开始切到子会话标签、结束切回主会话；WS 触发 false）。 */
  switchTab?: boolean;
  /** 子会话的直接父会话 ID（用于标签缺失时的子列表刷新；缺省取页面主会话）。 */
  parentId?: string;
}

const ROLE_CONFIG: Record<MessageRole, { label: string; icon: JSX.Element; color: string }> = {
  user: { label: '你', icon: <UserOutlined />, color: '#569cd6' },
  assistant: { label: '助手', icon: <RobotOutlined />, color: '#4ec9b0' },
  tool: { label: '工具', icon: <ToolOutlined />, color: '#d7ba7d' },
  system: { label: '系统', icon: <InfoCircleOutlined />, color: '#9cdcfe' },
};

const BUBBLE_STYLES: Record<MessageRole, React.CSSProperties> = {
  user: {
    background: '#1a3a5c',
    borderRadius: 12,
    padding: '10px 14px',
  },
  assistant: {
    background: '#2a2a2a',
    borderRadius: 12,
    padding: '10px 14px',
  },
  tool: {
    background: '#3a3a3a',
    borderRadius: 12,
    padding: '10px 14px',
  },
  system: {
    background: '#2d3748',
    borderRadius: 12,
    padding: '10px 14px',
  },
};

/**
 * 将后端会话消息映射为前端聊天消息。
 * @param historyMessages 后端会话消息列表
 * @returns 前端聊天消息列表
 */
function mapSessionMessages(historyMessages: SessionMessage[]): ChatMessage[] {
  return historyMessages.map((msg: SessionMessage) => {
    let content = msg.content;
    if (msg.role === 'tool' && msg.toolResult) {
      try {
        const tr = JSON.parse(msg.toolResult);
        const toolName = msg.toolInfo?.toolName || tr.toolName;
        content = `**工具: ${toolName}**\n\n**参数:**\n\`\`\`json\n${tr.arguments}\n\`\`\`\n\n**执行结果:**\n${tr.result}`;
      } catch {
        // keep original content
      }
    }
    return {
      role: (['user', 'assistant', 'tool', 'system'].includes(msg.role)
        ? msg.role
        : 'assistant') as MessageRole,
      content,
      reasoning: msg.reasoning || undefined,
      toolResult: msg.toolResult || undefined,
      toolInfo: msg.toolInfo || undefined,
      webSearchCall: msg.webSearchCall || undefined,
    };
  });
}

/**
 * 提取工具消息内容中的参数段（JSON），用于子会话历史与流式工具消息的内容级去重。
 * 前端流式工具消息为「正在执行工具 + 参数」，后端历史工具消息为「工具 + 参数 + 执行结果」，
 * 两者格式不同但参数段一致，可据此判定为同一消息。
 * @param content 工具消息内容
 * @returns 参数 JSON 字符串（无参数段时返回空字符串）
 */
function extractToolArguments(content: string): string {
  const match = content.match(/\*\*参数:\*\*\n```json\n([\s\S]*?)\n```/);
  return match ? match[1] : '';
}

/**
 * 判断两条聊天消息是否为同一消息（内容级去重判定）：
 * 普通消息按 role + content + reasoning 全等匹配；工具消息因前后端格式不同按 role + 参数段匹配。
 * @param a 消息 A
 * @param b 消息 B
 * @returns 是否视为同一消息
 */
function isSameMessage(a: ChatMessage, b: ChatMessage): boolean {
  if (a.role !== b.role) return false;
  if (a.role === 'tool') {
    const argsA = extractToolArguments(a.content);
    return argsA !== '' && argsA === extractToolArguments(b.content);
  }
  return a.content === b.content && (a.reasoning ?? undefined) === (b.reasoning ?? undefined);
}

const renderRoleHeader = (role: MessageRole): JSX.Element => {
  const config = ROLE_CONFIG[role];
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 4, marginBottom: 4 }}>
      <span style={{ color: config.color, fontSize: 14 }}>{config.icon}</span>
      <Typography.Text strong style={{ color: config.color, fontSize: 12 }}>
        {config.label}
      </Typography.Text>
    </div>
  );
};

const renderReasoning = (reasoning: string): JSX.Element => (
  <div
    style={{
      background: '#252525',
      borderLeft: '3px solid #ffd700',
      borderRadius: 4,
      padding: '8px 12px',
      marginBottom: 8,
    }}
    className="agent-chat-markdown"
  >
    <Typography.Text
      style={{ color: '#ffd700', fontSize: 12, marginBottom: 4, display: 'block' }}
    >
      思考过程
    </Typography.Text>
    <div style={{ color: '#aaa', fontSize: 13, lineHeight: 1.7 }}>
      <ReactMarkdown remarkPlugins={[remarkGfm]}>{reasoning}</ReactMarkdown>
    </div>
  </div>
);

const renderWebSearchCall = (calls: WebSearchCall[]): JSX.Element => (
  <div
    style={{
      background: '#1e3a4f',
      borderLeft: '3px solid #569cd6',
      borderRadius: 4,
      padding: '8px 12px',
      marginBottom: 8,
    }}
  >
    <Typography.Text style={{ color: '#9cdcfe', fontSize: 12, marginBottom: 4, display: 'block' }}>
      搜索结果
    </Typography.Text>
    {calls.map((call, ci) => (
      <div key={ci}>
        {call.results.map((r, i) => (
          <div key={i} style={{ marginBottom: 6 }}>
            <a
              href={r.url}
              target="_blank"
              rel="noopener noreferrer"
              style={{ color: '#569cd6', fontSize: 13 }}
            >
              {r.title}
            </a>
            <div style={{ color: '#aaa', fontSize: 12, lineHeight: 1.6, marginTop: 2 }}>
              {r.snippet}
            </div>
          </div>
        ))}
      </div>
    ))}
  </div>
);

const renderMessage = (msg: ChatMessage, idx: number): JSX.Element => {
  const isUser = msg.role === 'user';
  return (
    <div
      key={idx}
      style={{
        display: 'flex',
        justifyContent: isUser ? 'flex-end' : 'flex-start',
        marginBottom: 16,
      }}
    >
      <div style={{ maxWidth: '75%' }}>
        {renderRoleHeader(msg.role)}
        {msg.reasoning && renderReasoning(msg.reasoning)}
        {msg.webSearchCall && msg.webSearchCall.length > 0 && renderWebSearchCall(msg.webSearchCall)}
        {msg.content.trim() && (
          <div style={BUBBLE_STYLES[msg.role]} className="agent-chat-markdown">
            <div style={{ color: '#d4d4d4', fontSize: 14, lineHeight: 1.8 }}>
              <ReactMarkdown remarkPlugins={[remarkGfm]}>{msg.content}</ReactMarkdown>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

/**
 * 子会话只读展示视图：加载并展示指定子会话的历史消息。
 * 收到 SEND_USER_MESSAGE 消息时可通过 stream 属性实时展示流式回复。
 * 不含输入框、模型选择、思考模式及发送/回滚/停止等交互控件。
 */
function ChildSessionView({
  childId,
  stream,
}: {
  childId: string;
  stream?: ChildStreamState;
}): JSX.Element {
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [loading, setLoading] = useState(true);
  const childContainerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    let cancelled = false;
    getSessionMessages(childId)
      .then((historyMessages) => {
        if (!cancelled) setMessages(mapSessionMessages(historyMessages));
      })
      .catch(() => {
        if (!cancelled) message.error('加载子会话消息失败');
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [childId]);

  // 合并历史消息与实时流式消息：以 history（messages）为主，遍历 stream.messages 与 history 按内容
  // （role + content，可含 reasoning；工具消息按参数段）去重——history 中已存在的消息不追加，
  // 仅追加 history 中没有的最新消息。子会话执行完成后 stream 消息全部去重、只展示 history；
  // 执行中切换标签展示 history + 不重复的最新增量。
  const mergedMessages = useMemo(() => {
    if (!stream || stream.messages.length === 0) {
      return messages;
    }
    const merged = [...messages];
    for (const streamMsg of stream.messages) {
      if (!merged.some((m) => isSameMessage(m, streamMsg))) {
        merged.push(streamMsg);
      }
    }
    return merged;
  }, [messages, stream]);

  // 消息或流式状态（回复/推理/工具执行/加载）变化时自动滚动到底部，与主会话容器滚动行为一致
  useEffect(() => {
    if (childContainerRef.current) {
      childContainerRef.current.scrollTop = childContainerRef.current.scrollHeight;
    }
  }, [
    mergedMessages,
    stream?.currentResponse,
    stream?.currentReasoning,
    stream?.toolExecuting,
    stream?.loading,
  ]);

  const showStreaming =
    stream !== undefined &&
    (stream.loading ||
      stream.toolExecuting ||
      stream.currentResponse !== '' ||
      stream.currentReasoning !== '');
  const showHistorySpinner = loading && !showStreaming;

  return (
    <div
      ref={childContainerRef}
      style={{
        flex: 1,
        background: '#1e1e1e',
        borderRadius: 8,
        padding: 16,
        overflowY: 'auto',
        minHeight: 200,
        height: '100%',
      }}
    >
      {showHistorySpinner && (
        <div style={{ textAlign: 'center', padding: 40 }}>
          <Spin tip="加载消息..." />
        </div>
      )}
      {!showHistorySpinner && mergedMessages.length === 0 && !showStreaming && (
        <Typography.Text style={{ color: '#6a6a6a', fontSize: 14 }}>
          暂无消息
        </Typography.Text>
      )}
      {!showHistorySpinner && mergedMessages.map((msg, idx) => renderMessage(msg, idx))}
      {showStreaming && (
        <div
          style={{
            display: 'flex',
            justifyContent: 'flex-start',
            marginBottom: 16,
          }}
        >
          <div style={{ maxWidth: '75%' }}>
            {renderRoleHeader('assistant')}
            {stream!.currentReasoning && renderReasoning(stream!.currentReasoning)}
            {stream!.currentResponse ? (
              <div style={BUBBLE_STYLES.assistant} className="agent-chat-markdown">
                <div style={{ color: '#d4d4d4', fontSize: 14, lineHeight: 1.8 }}>
                  <ReactMarkdown remarkPlugins={[remarkGfm]}>
                    {stream!.currentResponse}
                  </ReactMarkdown>
                </div>
              </div>
            ) : (
              !stream!.currentReasoning && (
                <div style={{ marginTop: 8 }}>
                  <Spin size="small" />
                  {stream!.toolExecuting && (
                    <Typography.Text style={{ color: '#aaa', fontSize: 12, marginLeft: 8 }}>
                      正在执行工具调用...
                    </Typography.Text>
                  )}
                </div>
              )
            )}
          </div>
        </div>
      )}
      {stream?.error && (
        <div
          style={{
            background: '#3a1d1d',
            borderRadius: 4,
            padding: '8px 12px',
            marginTop: 8,
          }}
        >
          <Typography.Text style={{ color: '#ff6b6b', fontSize: 13 }}>
            {stream.error}
          </Typography.Text>
        </div>
      )}
    </div>
  );
}

function AgentChat(): JSX.Element {
  const { id } = useParams<{ id: string }>();
  const sessionId = id!;
  const [searchParams] = useSearchParams();
  const isBenchmark = searchParams.get('benchmark') === '1';
  const returnUrlRaw = searchParams.get('returnUrl');
  const returnUrl = returnUrlRaw ? decodeURIComponent(returnUrlRaw) : '/evaluations';
  const navigate = useNavigate();
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [inputValue, setInputValue] = useState('');
  const [loading, setLoading] = useState(false);
  const [toolExecuting, setToolExecuting] = useState(false);
  const [historyLoading, setHistoryLoading] = useState(true);
  const [currentResponse, setCurrentResponse] = useState('');
  const [currentReasoning, setCurrentReasoning] = useState('');
  const [currentWebSearchCall, setCurrentWebSearchCall] = useState<WebSearchCall[]>([]);
  const [thinking, setThinking] = useState(false);
  const [modelId, setModelId] = useState<string | undefined>(undefined);
  const [modelList, setModelList] = useState<ModelConfig[]>([]);
  const containerRef = useRef<HTMLDivElement>(null);
  const abortRef = useRef<AbortController | null>(null);
  const toolAbortRef = useRef(false);
  const hasResponseRef = useRef(false);
  const webSearchCallRef = useRef<WebSearchCall[]>([]);
  const calledRef = useRef(false);
  const responseIdRef = useRef<string | null>(null);
  const executeToolLoopRef = useRef<() => Promise<void>>();
  const runChildSessionFlowRef = useRef<(params: ChildSessionFlowParams) => Promise<void>>();
  const handleSubSessionFlowRef = useRef<(toolId: string) => Promise<void>>();
  const toolCallCounts = useRef<Map<string, number>>(new Map());

  // 子会话流程中止控制（WS 触发与工具触发共用）
  const subAbortRef = useRef<AbortController | null>(null);
  const subToolAbortRef = useRef(false);

  // 路径式导航：从主会话到当前激活子会话的层级链（首位为主会话 ID，末位为当前激活视图）
  const [activePath, setActivePath] = useState<string[]>([sessionId]);
  // 各层级子会话列表缓存：父会话 ID → 子会话列表（按需获取，获取计数时即缓存，支持任意层级）
  const [childListCache, setChildListCache] = useState<Record<string, Session[]>>({});
  // 当前展开下拉面板的父会话 ID（null 表示无展开）
  const [expandedPickerFor, setExpandedPickerFor] = useState<string | null>(null);
  // 子会话流式回复展示状态（按子会话 ID 索引，由 WS 消息分发触发）
  const [childStreams, setChildStreams] = useState<Record<string, ChildStreamState>>({});
  // 子会话列表缓存 ref（供异步流程读取最新值，避免闭包过期）
  const childListCacheRef = useRef<Record<string, Session[]>>({});
  // 子会话列表按需获取中的请求（按父会话 ID 去重，避免并发重复请求）
  const childListPromisesRef = useRef<Partial<Record<string, Promise<Session[] | null>>>>({});
  const childStreamsRef = useRef<Record<string, ChildStreamState>>({});
  const streamChildReplyRef = useRef<(message: SendUserMessagePayload) => void>(() => {});
  // 子→主回传（SEND_USER_MESSAGE sessionId=主会话）统一处理入口 ref（供注册处理器读取最新实现）
  const onSessionMessageRef = useRef<(message: SendUserMessagePayload) => void>(() => {});
  // 主会话状态 ref（供 WS 回调在异步流程中读取最新 loading/toolExecuting，避免闭包过期）
  const loadingRef = useRef(false);
  const toolExecutingRef = useRef(false);
  // 待刷新主会话消息链标记：主会话工具循环/续接期间收到子→主回传时置位，当前流程结束处消费
  const pendingSessionRefreshRef = useRef(false);
  // 主会话 marker 续接与待刷新消费入口 ref（两者相互引用，用 ref 破除 useCallback 循环依赖）
  const continueMainChatRef = useRef<() => void>(() => {});
  const consumePendingMainMessageRef = useRef<() => Promise<void>>(async () => {});

  /** 当前激活视图会话 ID（路径末位）。 */
  const activeTab = activePath[activePath.length - 1];

  useEffect(() => {
    if (containerRef.current) {
      containerRef.current.scrollTop = containerRef.current.scrollHeight;
    }
  }, [messages, currentResponse, currentReasoning]);

  const loadHistory = useCallback(async (): Promise<void> => {
    try {
      const [session, historyMessages] = await Promise.all([
        getSession(sessionId),
        getSessionMessages(sessionId),
      ]);
      const models = await listModels({ status: 'ENABLED', modelType: 'LLM' });
      setModelList(models);
      setModelId(session.modelId);
      if (session.thinking !== undefined) setThinking(session.thinking);
      const mapped: ChatMessage[] = mapSessionMessages(historyMessages);
      setMessages(mapped);
    } catch {
      message.error('加载历史消息失败');
    } finally {
      setHistoryLoading(false);
    }
  }, [sessionId]);

  /**
   * 刷新主会话后台消息链（getSessionMessages → messages），
   * 供子→主回传（SEND_USER_MESSAGE sessionId=主会话）到达时同步最新消息，
   * 使子会话结果在前端立即可见（不重置模型/思考等页面状态）。
   */
  const refreshMainMessages = useCallback(async (): Promise<void> => {
    try {
      const historyMessages = await getSessionMessages(sessionId);
      setMessages(mapSessionMessages(historyMessages));
    } catch {
      // 刷新失败静默处理，不阻塞主会话继续（后续刷新兜底）
    }
  }, [sessionId]);

  /**
   * 按需获取指定父会话的子会话列表（获取计数时即调用 listChildSessions 并缓存，
   * 下拉面板复用缓存数据不重复请求；支持任意层级）。
   * @param parentId 父会话 ID
   * @param force 是否强制刷新（绕过缓存）
   * @returns 子会话列表，获取失败返回 null
   */
  const ensureChildList = useCallback(
    async (parentId: string, force = false): Promise<Session[] | null> => {
      if (!force && childListCacheRef.current[parentId]) {
        return childListCacheRef.current[parentId];
      }
      if (!force && childListPromisesRef.current[parentId]) {
        return childListPromisesRef.current[parentId];
      }
      const promise = listChildSessions(parentId)
        .then((list) => {
          childListCacheRef.current = { ...childListCacheRef.current, [parentId]: list };
          setChildListCache(childListCacheRef.current);
          return list;
        })
        .catch(() => {
          message.error('加载子会话列表失败');
          return null;
        })
        .finally(() => {
          delete childListPromisesRef.current[parentId];
        });
      childListPromisesRef.current[parentId] = promise;
      return promise;
    },
    [],
  );

  useEffect(() => {
    if (!sessionId || calledRef.current) return;
    calledRef.current = true;

    getSessionContextBasic(sessionId)
      .then((ctx) => {
        if (ctx.lastResponseId) {
          responseIdRef.current = ctx.lastResponseId;
        }
      })
      .catch(() => {});

    loadHistory();
  }, [sessionId, loadHistory]);

  // 激活层级变化时按需获取其子会话列表（用于计数标签与下拉面板，任意层级均适用）
  useEffect(() => {
    const activeId = activePath[activePath.length - 1];
    if (!activeId) return;
    void ensureChildList(activeId);
  }, [activePath, ensureChildList]);

  // 切换会话时重置路径与下拉展开状态
  useEffect(() => {
    setActivePath([sessionId]);
    setExpandedPickerFor(null);
  }, [sessionId]);

  // 同步子会话流式状态到 ref（供 WS 回调判断是否正在流式）
  useEffect(() => {
    childStreamsRef.current = childStreams;
  }, [childStreams]);

  /**
   * 更新指定子会话的流式展示状态。
   * @param childId 子会话 ID
   * @param updater 状态更新函数
   */
  const updateChildStream = useCallback(
    (childId: string, updater: (state: ChildStreamState) => ChildStreamState): void => {
      setChildStreams((prev) => {
        const current = prev[childId];
        if (!current) return prev;
        return { ...prev, [childId]: updater(current) };
      });
    },
    [],
  );

  /**
   * 子会话工具状态轮询：轮询指定子会话的工具执行状态，完成后更新子会话标签内工具消息。
   * @param sid 子会话 ID
   * @param tid 工具 ID
   * @returns 工具是否执行成功
   */
  const pollSubToolStatus = useCallback(
    async (sid: string, tid: string): Promise<boolean> =>
      new Promise<boolean>((resolve) => {
        let done = false;
        const poll = async (): Promise<void> => {
          while (!done && !subToolAbortRef.current) {
            await new Promise((r) => setTimeout(r, 1000));
            if (subToolAbortRef.current) {
              resolve(false);
              return;
            }
            try {
              const status = await getToolStatus(sid, tid);
              if (status.status === 'done') {
                done = true;
                updateChildStream(sid, (s) => {
                  const updated = [...s.messages];
                  const lastIdx = updated.length - 1;
                  if (lastIdx >= 0 && updated[lastIdx].role === 'tool') {
                    updated[lastIdx] = {
                      role: 'tool',
                      content: `**工具: ${status.toolName}**\n\n**参数:**\n\`\`\`json\n${status.arguments}\n\`\`\`\n\n**执行结果:**\n${status.result || '无返回结果'}`,
                    };
                  }
                  return { ...s, messages: updated };
                });
                resolve(true);
                return;
              }
              if (status.status === 'idle') continue;
              if (status.toolConfig?.subToolType === 'BROWSER') {
                await executeBrowserTool(sid, tid, status);
                await new Promise((r) => setTimeout(r, 500));
                continue;
              }
              if (status.status === 'failed' || status.status === 'error') {
                done = true;
                updateChildStream(sid, (s) => {
                  const updated = [...s.messages];
                  const lastIdx = updated.length - 1;
                  if (lastIdx >= 0 && updated[lastIdx].role === 'tool') {
                    updated[lastIdx] = {
                      role: 'tool',
                      content: `**工具: ${status.toolName}**\n\n**参数:**\n\`\`\`json\n${status.arguments}\n\`\`\`\n\n**执行失败:** ${status.result || '未知错误'}`,
                    };
                  }
                  return { ...s, messages: updated };
                });
                resolve(false);
                return;
              }
            } catch {
              done = true;
              resolve(false);
              return;
            }
          }
          resolve(false);
        };
        poll();
      }),
    [updateChildStream],
  );

  /**
   * 子会话对话完整执行器（WS 消息分发与主会话工具回调两种触发方式共用）：
   * 写入 childStreams[childId] 初始化状态 → agentChatStream 流式回复（推理+内容）→
   * onDone(hasToolCalls) 为 true 时执行工具循环（executeTools → pollSubToolStatus →
   * continueChatStream）直至无工具调用 → 工具触发（非 WS）时 completeSubSession 收尾（WS 触发跳过）。
   * 入口差异仅参数：WS 触发 streamContent 传 SEND_USER_MESSAGE_MARKER，工具触发传 data.userMessage + thinking。
   * @param params 子会话执行参数
   */
  const runChildSessionFlow = useCallback(
    async (params: ChildSessionFlowParams): Promise<void> => {
      const { childId, userContent, streamContent, thinking, switchTab, parentId } = params;
      if (!childId) return;
      // 同一子会话已有流式请求进行中时忽略新消息（WS 路径；工具路径为一次性调用不受此限制）
      if (!switchTab && childStreamsRef.current[childId]?.loading) return;

      // 标签缺失处理：直接父会话的子列表无对应子会话时强制刷新补出标签（刷新失败忽略，继续执行）
      const flowParentId = parentId || sessionId;
      const parentList = childListCacheRef.current[flowParentId];
      if (!parentList || !parentList.some((c) => c.id === childId)) {
        await ensureChildList(flowParentId, true);
      }

      // 工具触发自动切换到对应子会话路径标签（子会话为页面主会话的直接子级）
      if (switchTab) setActivePath([sessionId, childId]);
      subToolAbortRef.current = false;

      // 写入 childStreams[childId] 初始化状态
      setChildStreams((prev) => {
        const existing = prev[childId];
        const baseMessages = existing ? existing.messages : [];
        return {
          ...prev,
          [childId]: {
            messages: [...baseMessages, { role: 'user', content: userContent }],
            currentResponse: '',
            currentReasoning: '',
            loading: true,
            toolExecuting: false,
            error: undefined,
          },
        };
      });

      /** 将当前流式回复提交为 assistant 消息并入标签消息列表。 */
      const commitAssistant = (): void => {
        updateChildStream(childId, (s) => {
          const hasContent = Boolean(s.currentResponse.trim() || s.currentReasoning.trim());
          return {
            messages: hasContent
              ? [
                  ...s.messages,
                  {
                    role: 'assistant',
                    content: s.currentResponse,
                    reasoning: s.currentReasoning || undefined,
                  },
                ]
              : s.messages,
            currentResponse: '',
            currentReasoning: '',
            loading: false,
          };
        });
      };

      /** 发起一轮流式回复（agentChatStream），返回是否需继续工具调用。 */
      const streamReply = (content: string): Promise<boolean> =>
        new Promise((resolve) => {
          updateChildStream(childId, (s) => ({ ...s, loading: true, toolExecuting: false }));
          subAbortRef.current = agentChatStream(
            { sessionId: childId, content, thinking },
            {
              onDelta: (text) =>
                updateChildStream(childId, (s) => ({
                  ...s,
                  currentResponse: s.currentResponse + text,
                })),
              onReasoning: (text) =>
                updateChildStream(childId, (s) => ({
                  ...s,
                  currentReasoning: s.currentReasoning + text,
                })),
              onDone: (hasToolCalls) => {
                commitAssistant();
                resolve(hasToolCalls);
              },
              onError: (err) => {
                updateChildStream(childId, (s) => ({
                  ...s,
                  loading: false,
                  error: err.message || '子会话请求失败',
                }));
                resolve(false);
              },
            },
          );
        });

      /** 工具执行后的流式续接（continueChatStream），返回是否需继续工具调用。 */
      const continueChildChat = (): Promise<boolean> =>
        new Promise((resolve) => {
          updateChildStream(childId, (s) => ({
            ...s,
            loading: true,
            currentResponse: '',
            currentReasoning: '',
          }));
          subAbortRef.current = continueChatStream(childId, {
            onDelta: (text) =>
              updateChildStream(childId, (s) => ({
                ...s,
                currentResponse: s.currentResponse + text,
              })),
            onReasoning: (text) =>
              updateChildStream(childId, (s) => ({
                ...s,
                currentReasoning: s.currentReasoning + text,
              })),
            onDone: (hasToolCalls) => {
              commitAssistant();
              resolve(hasToolCalls);
            },
            onError: (err) => {
              updateChildStream(childId, (s) => ({
                ...s,
                loading: false,
                error: err.message || '子会话请求失败',
              }));
              resolve(false);
            },
          });
        });

      /** 子会话工具执行循环：executeTools → pollSubToolStatus，直至无更多工具调用。 */
      const runChildTools = async (): Promise<boolean> => {
        updateChildStream(childId, (s) => ({ ...s, toolExecuting: true }));
        subToolAbortRef.current = false;
        try {
          let hasMore = true;
          while (hasMore && !subToolAbortRef.current) {
            const execResult = await executeTools(childId);
            if (subToolAbortRef.current) break;
            if (execResult.status === 'empty') {
              hasMore = false;
              continue;
            }
            hasMore = execResult.hasMore;
            if (!execResult.toolId) {
              hasMore = false;
              continue;
            }
            const key = `${execResult.toolName}:${execResult.arguments}`;
            const count = (toolCallCounts.current.get(key) || 0) + 1;
            toolCallCounts.current.set(key, count);
            if (count >= 5) {
              updateChildStream(childId, (s) => ({
                ...s,
                error: `子会话工具 ${execResult.toolName} 同一参数调用已达 ${count} 次，已终止`,
              }));
              hasMore = false;
              continue;
            }
            updateChildStream(childId, (s) => ({
              ...s,
              messages: [
                ...s.messages,
                {
                  role: 'tool',
                  content: `**正在执行工具: ${execResult.toolName}**\n\n**参数:**\n\`\`\`json\n${execResult.arguments}\n\`\`\``,
                },
              ],
            }));
            const succeeded = await pollSubToolStatus(childId, execResult.toolId);
            if (!succeeded) hasMore = false;
          }
          return !subToolAbortRef.current;
        } catch {
          updateChildStream(childId, (s) => ({ ...s, error: '子会话工具执行失败' }));
          return false;
        } finally {
          updateChildStream(childId, (s) => ({ ...s, toolExecuting: false }));
        }
      };

      try {
        let hasToolCalls = await streamReply(streamContent);
        while (hasToolCalls && !subToolAbortRef.current) {
          const ok = await runChildTools();
          if (!ok) break;
          hasToolCalls = await continueChildChat();
        }
        // 收尾：仅工具触发（streamContent 非 SEND_USER_MESSAGE_MARKER，即 TOOL_CALL 模式）通知后端子会话完成；
        // WS 触发（streamContent === SEND_USER_MESSAGE_MARKER）跳过——后端 subSessionDataMap 仅 TOOL_CALL 模式写入，
        // WS 模式调用必然返回 SUB_SESSION_DATA_NOT_FOUND
        if (streamContent !== SEND_USER_MESSAGE_MARKER) {
          await completeSubSession(sessionId);
        }
      } catch {
        updateChildStream(childId, (s) => ({ ...s, loading: false, error: '子会话流程执行失败' }));
      } finally {
        subAbortRef.current = null;
        // 工具触发执行完成后自动切回主会话标签
        if (switchTab) setActivePath([sessionId]);
      }
    },
    [ensureChildList, pollSubToolStatus, sessionId, updateChildStream],
  );

  runChildSessionFlowRef.current = runChildSessionFlow;

  /**
   * 根据 SEND_USER_MESSAGE 负载的父会话链确定目标子会话在路径中的位置，
   * 展开/补出从主会话到目标子会话的路径标签（父链：第一个=直接父，最后一个=主会话）。
   * @param payload WS 消息负载
   * @returns 是否成功确定路径（父链包含页面主会话时 true）
   */
  const expandPathFromPayload = useCallback(
    async (payload: SendUserMessagePayload): Promise<boolean> => {
      const targetId = payload.sessionId;
      if (!targetId) return false;
      if (targetId === sessionId) {
        setActivePath([sessionId]);
        return true;
      }
      const mainIdx = payload.parentSessionIds.indexOf(sessionId);
      if (mainIdx < 0) return false;
      // 从主会话到目标子会话的层级链：[主会话, ..., 直接父, 目标子会话]
      const chain = [sessionId, ...payload.parentSessionIds.slice(0, mainIdx).reverse(), targetId];
      // 逐级补出：确保每级父会话的子列表已获取（用于路径标签名称，获取失败不影响继续）
      for (let i = 0; i < chain.length - 1; i++) {
        await ensureChildList(chain[i]);
      }
      setActivePath(chain);
      return true;
    },
    [sessionId, ensureChildList],
  );

  /**
   * 子会话消息分发（主→子发送，WS 触发）：收到 SEND_USER_MESSAGE 时按父会话链展开/补出路径标签，
   * 并以特殊标记 [send_user_message] 调用统一子会话执行器，流式展示该子会话的 AI 回复。
   * 子→主回传（sessionId === 页面主会话）不在此处理，转由 onSessionMessage 统一入口。
   * @param payload SEND_USER_MESSAGE 消息负载
   */
  const streamChildReply = useCallback(
    (payload: SendUserMessagePayload): void => {
      const childId = payload.sessionId;
      if (!childId) return;
      // 子→主回传（sessionId 为页面主会话自身）：转由 onSessionMessage 统一处理
      if (childId === sessionId) {
        onSessionMessageRef.current(payload);
        return;
      }
      void (async (): Promise<void> => {
        // 按父会话链展开/补出对应路径标签（失败时仍继续执行流式回复）
        const expanded = await expandPathFromPayload(payload);
        void runChildSessionFlowRef.current!({
          childId,
          userContent: payload.content || '',
          streamContent: SEND_USER_MESSAGE_MARKER,
          switchTab: false,
          parentId: expanded ? payload.parentSessionIds[0] || sessionId : sessionId,
        });
      })();
    },
    [expandPathFromPayload, sessionId],
  );

  streamChildReplyRef.current = streamChildReply;

  /**
   * 主会话基于新消息链续接（子→主回传且主会话空闲时触发）：
   * 以 SEND_USER_MESSAGE_MARKER 作为 content 调用 agentChatStream，使主会话处理子会话回传
   * 结果并继续对话（marker 不会被后端保存为重复用户消息）；流式回复渲染进主会话消息区，
   * onDone 有工具调用时进入主会话工具循环。
   */
  const continueMainChat = useCallback((): void => {
    // 主会话已有流程进行中（工具循环/续接/普通对话）时忽略，避免并发重复调用 chat
    if (loadingRef.current || toolExecutingRef.current) return;
    loadingRef.current = true;
    toolExecutingRef.current = false;
    setLoading(true);
    setToolExecuting(false);
    setCurrentResponse('');
    setCurrentReasoning('');
    setCurrentWebSearchCall([]);
    hasResponseRef.current = false;
    webSearchCallRef.current = [];
    toolAbortRef.current = false;
    abortRef.current = agentChatStream(
      { sessionId, content: SEND_USER_MESSAGE_MARKER, modelId, thinking },
      {
        onDelta: (text: string) => {
          hasResponseRef.current = true;
          setCurrentResponse((prev) => prev + text);
        },
        onReasoning: (text: string) => {
          hasResponseRef.current = true;
          setCurrentReasoning((prev) => prev + text);
        },
        onResponseId: (id: string) => {
          responseIdRef.current = id;
        },
        onWebSearchCall: (calls: WebSearchCall[]) => {
          webSearchCallRef.current = calls;
          setCurrentWebSearchCall(calls);
        },
        onDone: (hasToolCalls: boolean) => {
          setCurrentResponse((prev) => {
            setCurrentReasoning((reasoning) => {
              if ((prev && prev.trim()) || (reasoning && reasoning.trim())) {
                setMessages((msgs) => [
                  ...msgs,
                  {
                    role: 'assistant',
                    content: prev,
                    reasoning: reasoning || undefined,
                    webSearchCall: webSearchCallRef.current.length ? webSearchCallRef.current : undefined,
                  },
                ]);
              }
              return '';
            });
            return '';
          });
          if (hasToolCalls) {
            executeToolLoopRef.current?.();
          } else {
            loadingRef.current = false;
            toolExecutingRef.current = false;
            setLoading(false);
            abortRef.current = null;
            // 续接期间若有新的子→主回传（标记待刷新），消费后基于新消息链继续
            void consumePendingMainMessageRef.current();
          }
        },
        onError: (err: Error) => {
          message.error(err.message || '请求失败');
          loadingRef.current = false;
          toolExecutingRef.current = false;
          setLoading(false);
          abortRef.current = null;
        },
      },
    );
  }, [sessionId, modelId, thinking]);

  continueMainChatRef.current = continueMainChat;

  /**
   * 消费待刷新标记：主会话工具循环/续接期间收到子→主回传时置位，
   * 当前流程结束处（pollToolStatus 返回后、续接 onDone 后）调用——
   * 先刷新主会话消息链；若主会话已空闲且期间有新回传，则触发主会话 marker 续接。
   */
  const consumePendingMainMessage = useCallback(async (): Promise<void> => {
    if (!pendingSessionRefreshRef.current) return;
    pendingSessionRefreshRef.current = false;
    await refreshMainMessages();
    if (!loadingRef.current && !toolExecutingRef.current) {
      continueMainChatRef.current();
    }
  }, [refreshMainMessages]);

  consumePendingMainMessageRef.current = consumePendingMainMessage;

  /**
   * 子→主回传统一处理（WS 触发，sessionId === 页面主会话）：
   * 1. 先刷新主会话消息链（getSessionMessages → messages），使子会话结果在前端立即可见；
   * 2. 再按主会话当前状态继续：
   *    - 工具循环/续接中（loading/toolExecuting 为 true）：仅标记待刷新，由当前流程
   *      poll/续接完成后刷新并继续（不额外调用 chat，避免重复总结）；
   *    - 主会话空闲：以 SEND_USER_MESSAGE_MARKER 调用 agentChatStream 触发主会话基于新消息链继续。
   * @param payload SEND_USER_MESSAGE 消息负载
   */
  const onSessionMessage = useCallback(
    (payload: SendUserMessagePayload): void => {
      const targetId = payload.sessionId;
      if (!targetId) return;
      // 防御：消息非主会话自身（主→子发送）时转由子会话执行器处理
      if (targetId !== sessionId) {
        streamChildReplyRef.current(payload);
        return;
      }
      // 子→主回传：无条件切回主会话标签（不依赖主会话空闲状态），使主会话回复立即可见
      setActivePath([sessionId]);
      void (async (): Promise<void> => {
        // 先刷新主会话后台消息链，使子会话回传结果立即反映到主会话消息区
        await refreshMainMessages();
        if (loadingRef.current || toolExecutingRef.current) {
          // 主会话工具循环/续接进行中：标记待刷新，当前流程结束处消费（避免重复调用 chat 造成重复总结）
          pendingSessionRefreshRef.current = true;
          return;
        }
        // 主会话空闲：基于新消息链触发主会话继续（marker 避免重复保存用户消息）
        continueMainChatRef.current();
      })();
    },
    [refreshMainMessages, sessionId],
  );

  onSessionMessageRef.current = onSessionMessage;
  // 同步主会话状态到 ref（供 WS 回调读取最新值）
  loadingRef.current = loading;
  toolExecutingRef.current = toolExecuting;

  /**
   * 子会话回调工具流程（工具触发）：主会话工具轮询检测到 needsSubSessionFlow 时，
   * 获取子会话数据后调用统一子会话执行器（自动切换子会话标签，完成后切回主会话）。
   * @param toolId 主会话工具 ID
   */
  const handleSubSessionFlow = useCallback(
    async (toolId: string): Promise<void> => {
      try {
        const data = await getSubSessionData(sessionId);
        if (!data) {
          message.error('获取子会话数据失败');
          return;
        }
        await runChildSessionFlowRef.current!({
          childId: data.childSessionId,
          userContent: data.userMessage,
          streamContent: data.userMessage,
          thinking: data.thinking,
          switchTab: true,
        });
      } catch {
        message.error('子会话流程执行失败');
        setToolExecuting(false);
        setLoading(false);
        abortRef.current = null;
      }
    },
    [sessionId],
  );

  handleSubSessionFlowRef.current = handleSubSessionFlow;

  // 进入会话页面：注册消息分发页面处理器
  useEffect(() => {
    if (!sessionId) return;
    const handler: SessionPageHandler = {
      mainSessionId: sessionId,
      streamChildReply: (msg) => streamChildReplyRef.current(msg),
      onSessionMessage: (msg) => onSessionMessageRef.current(msg),
    };
    registerSessionPage(handler);
    return () => {
      unregisterSessionPage(sessionId);
    };
  }, [sessionId]);

  const handleAbort = useCallback(() => {
    stopChat(sessionId).catch(() => {});
    toolAbortRef.current = true;
    subToolAbortRef.current = true;
    if (abortRef.current) {
      abortRef.current.abort();
      abortRef.current = null;
    }
    if (subAbortRef.current) {
      subAbortRef.current.abort();
      subAbortRef.current = null;
    }
  }, [sessionId]);

  useEffect(() => {
    return () => handleAbort();
  }, [handleAbort]);

  const pollToolStatus = useCallback(async (sid: string, toolId: string): Promise<boolean> => {
    let done = false;
    while (!done && !toolAbortRef.current) {
      await new Promise((resolve) => setTimeout(resolve, 1000));
      if (toolAbortRef.current) return false;
      const status = await getToolStatus(sid, toolId);
      if (status.status === 'done') {
        done = true;
        setMessages((msgs) => {
          const updated = [...msgs];
          const lastIdx = updated.length - 1;
          if (lastIdx >= 0 && updated[lastIdx].role === 'tool') {
            updated[lastIdx] = {
              role: 'tool',
              content: `**工具: ${status.toolName}**\n\n**参数:**\n\`\`\`json\n${status.arguments}\n\`\`\`\n\n**执行结果:**\n${status.result || '无返回结果'}`,
            };
          }
          return updated;
        });
        return true;
      }
      if (status.needsSubSessionFlow) {
        await handleSubSessionFlowRef.current!(toolId);
        continue;
      }
      if (status.toolConfig?.subToolType === 'BROWSER') {
        await executeBrowserTool(sid, toolId, status);
        await new Promise((resolve) => setTimeout(resolve, 500));
        continue;
      }
      if (status.status === 'idle') {
        continue;
      }
      if (status.status === 'failed' || status.status === 'error') {
        done = true;
        setMessages((msgs) => {
          const updated = [...msgs];
          const lastIdx = updated.length - 1;
          if (lastIdx >= 0 && updated[lastIdx].role === 'tool') {
            updated[lastIdx] = {
              role: 'tool',
              content: `**工具: ${status.toolName}**\n\n**参数:**\n\`\`\`json\n${status.arguments}\n\`\`\`\n\n**执行失败:** ${status.result || '未知错误'}`,
            };
          }
          return updated;
        });
        return false;
      }
    }
    return false;
  }, []);

  const executeToolLoop = useCallback(async () => {
    setToolExecuting(true);
    toolAbortRef.current = false;
    try {
      let hasMore = true;
      let hadTools = false;
      while (hasMore && !toolAbortRef.current) {
        const execResult = await executeTools(sessionId);
        if (toolAbortRef.current) break;
        if (execResult.status === 'empty') {
          hasMore = false;
          continue;
        }
        hasMore = execResult.hasMore;
        if (!execResult.toolId) {
          hasMore = false;
          continue;
        }
        hadTools = true;
        const key = `${execResult.toolName}:${execResult.arguments}`;
        const count = (toolCallCounts.current.get(key) || 0) + 1;
        toolCallCounts.current.set(key, count);
        if (count >= 5) {
          message.warning(`工具 ${execResult.toolName} 同一参数调用已达 ${count} 次，已终止`);
          hasMore = false;
          continue;
        }
        setMessages((prev) => [
          ...prev,
          {
            role: 'tool',
            content: `**正在执行工具: ${execResult.toolName}**\n\n**参数:**\n\`\`\`json\n${execResult.arguments}\n\`\`\``,
          },
        ]);
        const succeeded = await pollToolStatus(sessionId, execResult.toolId);
        // 子→主回传在工具轮询期间到达：poll 完成后先刷新主会话消息链，再进入 executeToolLoop 下一轮
        // （仅刷新不额外调用 chat，主会话续接由本循环的 continueChatStream 承接，避免重复总结）
        if (pendingSessionRefreshRef.current) {
          await consumePendingMainMessageRef.current();
        }
        if (!succeeded) hasMore = false;
      }
      if (toolAbortRef.current) {
        setToolExecuting(false);
        setLoading(false);
        abortRef.current = null;
        return;
      }
      if (!hadTools) {
        setToolExecuting(false);
        setLoading(false);
        abortRef.current = null;
        return;
      }
      setToolExecuting(false);
      setCurrentResponse('');
      setCurrentReasoning('');
      setCurrentWebSearchCall([]);
      hasResponseRef.current = false;
      webSearchCallRef.current = [];
      abortRef.current = continueChatStream(
        sessionId,
        {
          onDelta: (text) => {
            hasResponseRef.current = true;
            setCurrentResponse((prev) => prev + text);
          },
          onReasoning: (text) => {
            hasResponseRef.current = true;
            setCurrentReasoning((prev) => prev + text);
          },
          onWebSearchCall: (calls: WebSearchCall[]) => {
            webSearchCallRef.current = calls;
            setCurrentWebSearchCall(calls);
          },
          onDone: (hasMoreTools) => {
            setCurrentResponse((prev) => {
              setCurrentReasoning((reasoning) => {
                if ((prev && prev.trim()) || (reasoning && reasoning.trim())) {
                  setMessages((msgs) => [
                    ...msgs,
                    {
                      role: 'assistant',
                      content: prev,
                      reasoning: reasoning || undefined,
                      webSearchCall: webSearchCallRef.current.length ? webSearchCallRef.current : undefined,
                    },
                  ]);
                }
                return '';
              });
              return '';
            });
            if (hasMoreTools) {
              executeToolLoopRef.current?.();
            } else {
              toolCallCounts.current.clear();
              loadingRef.current = false;
              toolExecutingRef.current = false;
              setLoading(false);
              abortRef.current = null;
              // 续接期间收到子→主回传（标记待刷新）：刷新消息链后基于新消息继续（空闲时触发主会话 marker 续接）
              void consumePendingMainMessageRef.current();
            }
          },
          onError: (err) => {
            message.error(err.message || '请求失败');
            setLoading(false);
            abortRef.current = null;
          },
        },
      );
    } catch {
      message.error('工具执行失败');
      setToolExecuting(false);
      setLoading(false);
      abortRef.current = null;
    }
  }, [sessionId, pollToolStatus]);

  executeToolLoopRef.current = executeToolLoop;

  const handleSend = useCallback(async () => {
    if (!inputValue.trim() || loading) return;

    let conversationId: string | undefined;
    try {
      conversationId = await fetchConversationId();
    } catch {
      message.error('获取会话标识失败，请重试');
      return;
    }

    const currentModel = modelList.find((m) => String(m.id) === String(modelId));
    const isResponsesStateful = currentModel?.requestType === 'responses';
    const previousResponseId = isResponsesStateful ? responseIdRef.current || undefined : undefined;

    const userMsg: ChatMessage = { role: 'user', content: inputValue };
    setMessages((prev) => [...prev, userMsg]);
    setInputValue('');
    setLoading(true);
    setToolExecuting(false);
    setCurrentResponse('');
    setCurrentReasoning('');
    setCurrentWebSearchCall([]);
    hasResponseRef.current = false;
    webSearchCallRef.current = [];
    toolAbortRef.current = false;
    // 同步主会话状态到 ref（WS 回调在异步流程中读取最新状态，避免 setState 尚未渲染时的竞态）
    loadingRef.current = true;
    toolExecutingRef.current = false;
    // 用户手动发送开启全新对话流程：清除遗留的待刷新标记，避免旧回传触发多余续接
    pendingSessionRefreshRef.current = false;

    abortRef.current = agentChatStream(
      { sessionId, content: inputValue, modelId, thinking, previousResponseId, conversationId },
      {
        onDelta: (text: string) => {
          hasResponseRef.current = true;
          setCurrentResponse((prev) => prev + text);
        },
        onReasoning: (text: string) => {
          hasResponseRef.current = true;
          setCurrentReasoning((prev) => prev + text);
        },
        onResponseId: (id: string) => {
          responseIdRef.current = id;
        },
        onWebSearchCall: (calls: WebSearchCall[]) => {
          webSearchCallRef.current = calls;
          setCurrentWebSearchCall(calls);
        },
        onDone: (hasToolCalls: boolean) => {
          setCurrentResponse((prev) => {
            setCurrentReasoning((reasoning) => {
              if ((prev && prev.trim()) || (reasoning && reasoning.trim())) {
                setMessages((msgs) => [
                  ...msgs,
                  {
                    role: 'assistant',
                    content: prev,
                    reasoning: reasoning || undefined,
                    webSearchCall: webSearchCallRef.current.length ? webSearchCallRef.current : undefined,
                  },
                ]);
              }
              return '';
            });
            return '';
          });
          if (!hasResponseRef.current && !hasToolCalls) {
            message.warning('未收到回复内容');
          }
          if (hasToolCalls) {
            executeToolLoopRef.current?.();
          } else {
            setLoading(false);
            abortRef.current = null;
          }
        },
        onError: (err: Error) => {
          message.error(err.message || '请求失败');
          setLoading(false);
          abortRef.current = null;
        },
      },
    );
  }, [inputValue, loading, sessionId, modelId, thinking, modelList]);

  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
      if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault();
        handleSend();
      }
    },
    [handleSend],
  );

  const renderMainChat = (): JSX.Element => (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      <style>{`
        .agent-chat-markdown pre {
          background: #2d2d2d;
          border-radius: 6px;
          padding: 12px 16px;
          overflow-x: auto;
        }
        .agent-chat-markdown code {
          font-family: 'Consolas', 'Courier New', monospace;
          font-size: 13px;
        }
        .agent-chat-markdown :not(pre) > code {
          background: #2d2d2d;
          padding: 2px 6px;
          border-radius: 4px;
        }
        .agent-chat-markdown table {
          border-collapse: collapse;
          width: 100%;
          margin: 12px 0;
        }
        .agent-chat-markdown th,
        .agent-chat-markdown td {
          border: 1px solid #444;
          padding: 8px 12px;
          text-align: left;
        }
        .agent-chat-markdown th {
          background: #2d2d2d;
          font-weight: 600;
        }
        .agent-chat-markdown blockquote {
          border-left: 3px solid #555;
          padding-left: 12px;
          margin: 12px 0;
          color: #aaa;
        }
        .agent-chat-markdown a {
          color: #569cd6;
        }
        .agent-chat-markdown ul,
        .agent-chat-markdown ol {
          padding-left: 24px;
        }
        .agent-chat-markdown p {
          margin: 8px 0;
        }
      `}</style>

      <div
        ref={containerRef}
        style={{
          flex: 1,
          background: '#1e1e1e',
          borderRadius: 8,
          padding: 16,
          overflowY: 'auto',
          marginBottom: 16,
          minHeight: 200,
        }}
      >
        {historyLoading && (
          <div style={{ textAlign: 'center', padding: 40 }}>
            <Spin tip="加载历史消息..." />
          </div>
        )}

        {!historyLoading && messages.length === 0 && !loading && !toolExecuting && (
          <Typography.Text style={{ color: '#6a6a6a', fontSize: 14 }}>
            发送消息开始对话
          </Typography.Text>
        )}

        {!historyLoading && messages.map((msg, idx) => renderMessage(msg, idx))}

        {toolExecuting && (
          <div
            style={{
              display: 'flex',
              justifyContent: 'flex-start',
              marginBottom: 16,
            }}
          >
            <div style={{ maxWidth: '75%' }}>
              {renderRoleHeader('assistant')}
              <div style={{ marginTop: 8 }}>
                <Spin size="small" />
                <Typography.Text
                  style={{ color: '#aaa', fontSize: 12, marginLeft: 8 }}
                >
                  正在执行工具调用...
                </Typography.Text>
              </div>
            </div>
          </div>
        )}

        {loading && !toolExecuting && (
          <div
            style={{
              display: 'flex',
              justifyContent: 'flex-start',
              marginBottom: 16,
            }}
          >
            <div style={{ maxWidth: '75%' }}>
              {renderRoleHeader('assistant')}
              {currentReasoning && renderReasoning(currentReasoning)}
              {currentWebSearchCall.length > 0 && renderWebSearchCall(currentWebSearchCall)}
              {currentResponse ? (
                <div style={BUBBLE_STYLES.assistant} className="agent-chat-markdown">
                  <div style={{ color: '#d4d4d4', fontSize: 14, lineHeight: 1.8 }}>
                    <ReactMarkdown remarkPlugins={[remarkGfm]}>
                      {currentResponse}
                    </ReactMarkdown>
                  </div>
                </div>
              ) : (
                !currentReasoning && (
                  <div style={{ marginTop: 8 }}>
                    <Spin size="small" />
                  </div>
                )
              )}
            </div>
          </div>
        )}
      </div>

      <div style={{ marginBottom: 12, display: 'flex', alignItems: 'center', gap: 16 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <Typography.Text type="secondary" style={{ fontSize: 13, whiteSpace: 'nowrap' }}>
            {isBenchmark ? '模型' : '选择模型'}
          </Typography.Text>
          <Select
            placeholder="选择模型"
            allowClear
            disabled={isBenchmark}
            style={{ width: 200 }}
            value={modelId}
            onChange={setModelId}
            options={modelList.map((m) => ({
              value: String(m.id),
              label: m.name,
            }))}
          />
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <Typography.Text type="secondary" style={{ fontSize: 13 }}>
            思考模式
          </Typography.Text>
          <Switch
            checked={thinking}
            onChange={(checked) => {
              setThinking(checked);
              updateSessionThinking(sessionId, checked).catch(() => {});
            }}
            size="small"
          />
        </div>
      </div>

      <div style={{ display: 'flex', gap: 8 }}>
        <Input.TextArea
          value={inputValue}
          onChange={(e) => setInputValue(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder="输入消息，Enter 发送，Shift+Enter 换行"
          rows={3}
          autoSize={{ minRows: 2, maxRows: 6 }}
        />
        <div style={{ display: 'flex', flexDirection: 'column', gap: 8, minWidth: 80 }}>
          <Button
            type="primary"
            onClick={handleSend}
            disabled={loading || !inputValue.trim()}
            loading={loading}
          >
            发送
          </Button>
          {loading && (
            <Button onClick={handleAbort} danger>
              停止
            </Button>
          )}
          <Button
            disabled={loading || toolExecuting}
            onClick={async () => {
              try {
                await rollbackSession(sessionId);
                await loadHistory();
              } catch {
                message.error('回滚失败');
              }
            }}
          >
            回滚
          </Button>
        </div>
      </div>
    </div>
  );

  /**
   * 获取路径中指定会话的展示名称（子会话名称取自其父会话的子列表缓存，无缓存时回退 id）。
   * @param sid 会话 ID
   * @param parentId 直接父会话 ID（用于查找子列表中的名称）
   * @returns 展示名称
   */
  const sessionLabel = (sid: string, parentId?: string): string => {
    const list = parentId ? childListCache[parentId] : undefined;
    return list?.find((c) => c.id === sid)?.title || sid;
  };

  /**
   * 删除子会话（路径标签上的删除图标触发）：
   * 先弹 Modal.confirm 确认（删除后消息不可恢复），确认后调用 deleteSession 删除；
   * 成功后——若删除的是路径中的子会话（含当前激活末位）则截断路径切回其父会话标签；
   * 从父会话子列表缓存移除该子会话并清理其自身子列表缓存、清理对应 childStreams 流式状态；
   * 删除失败提示错误信息。
   * @param childId 要删除的子会话 ID
   * @param parentId 直接父会话 ID（用于从父会话子列表缓存移除）
   */
  const handleDeleteChild = useCallback((childId: string, parentId: string): void => {
    const label =
      childListCacheRef.current[parentId]?.find((c) => c.id === childId)?.title || childId;
    Modal.confirm({
      title: '删除子会话',
      content: `确定要删除子会话「${label}」吗？删除后该子会话的消息将不可恢复。`,
      okText: '删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: async (): Promise<void> => {
        try {
          await deleteSession(childId);
          message.success('删除子会话成功');
          // 若删除的是路径中的子会话（含当前激活末位），截断路径至其父会话标签并移除其后代层级
          // （主会话 idx=0 不处理，删除图标仅出现在 i>0 的子会话标签上）
          setActivePath((prev) => {
            const idx = prev.indexOf(childId);
            return idx > 0 ? prev.slice(0, idx) : prev;
          });
          // 从父会话子列表缓存移除该子会话，并清理其自身子列表缓存
          setChildListCache((prev) => {
            const parentList = prev[parentId];
            const next = { ...prev };
            if (parentList) next[parentId] = parentList.filter((c) => c.id !== childId);
            delete next[childId];
            return next;
          });
          const cache = { ...childListCacheRef.current };
          if (cache[parentId]) cache[parentId] = cache[parentId].filter((c) => c.id !== childId);
          delete cache[childId];
          childListCacheRef.current = cache;
          // 清理对应子会话的流式展示状态（如有）
          setChildStreams((prev) => {
            if (!(childId in prev)) return prev;
            const next = { ...prev };
            delete next[childId];
            return next;
          });
          // 若删除的是当前展开下拉面板的父会话，收起下拉
          setExpandedPickerFor((prev) => (prev === childId ? null : prev));
        } catch {
          message.error('删除子会话失败');
        }
      },
    });
  }, []);

  /** 路径标签项：主会话 + 各级激活子会话（末位为当前激活视图，内容区沿用 ChildSessionView）；
   *  仅路径末位层级标签后紧跟其下一级计数标签项（Dropdown+数量+上下箭头，无子会话时不插入，
   *  选中子会话后父层级计数标签隐藏，点击仅展开下拉不切换内容区，key 为 `${sid}-count`）。 */
  const sessionTabItems: TabsProps['items'] = activePath.flatMap((sid, i) => {
    const levelChildren = childListCache[sid];
    const items: NonNullable<TabsProps['items']> = [
      {
        key: sid,
        label:
          i === 0 ? (
            '主会话'
          ) : (
            <span className="agent-chat-tab-label">
              {sessionLabel(sid, activePath[i - 1])}
              <DeleteOutlined
                className="agent-chat-tab-delete"
                onClick={(e) => {
                  // 阻止冒泡：点击删除图标不触发标签切换
                  e.stopPropagation();
                  handleDeleteChild(sid, activePath[i - 1]);
                }}
              />
            </span>
          ),
        children:
          i === 0 ? (
            renderMainChat()
          ) : (
            <ChildSessionView childId={sid} stream={childStreams[sid]} />
          ),
      },
    ];
    // 该层级为路径末位且有子会话时，在标签后追加其下一级计数标签项（选中子会话后父层级不再显示）
    if (levelChildren && levelChildren.length > 0 && i === activePath.length - 1) {
      items.push({
        key: `${sid}-count`,
        label: (
          <Dropdown
            menu={{
              items: levelChildren.map((c) => ({ key: c.id, label: c.title || c.id })),
              onClick: ({ key }) => handleSelectChild(key),
            }}
            trigger={['click']}
            open={expandedPickerFor === sid}
            onOpenChange={(open) => setExpandedPickerFor(open ? sid : null)}
          >
            <div className="agent-chat-count-tab">
              <span>子会话 {levelChildren.length}</span>
              {expandedPickerFor === sid ? <UpOutlined /> : <DownOutlined />}
            </div>
          </Dropdown>
        ),
        children: null,
      });
    }
    return items;
  });

  /** 点击路径标签：截断路径至该层级并关闭下拉（点击主会话恢复其下一级计数显示）。 */
  const handleTabChange = (key: string): void => {
    // 计数标签项：仅展开下拉，不切换内容区
    if (key.endsWith('-count')) return;
    setExpandedPickerFor(null);
    const idx = activePath.indexOf(key);
    if (idx < 0) return;
    setActivePath(activePath.slice(0, idx + 1));
  };

  /** 从下拉面板选中子会话：追加到路径末位成为新的激活视图（计数标签变为子会话名称、箭头隐藏）。 */
  const handleSelectChild = (childId: string): void => {
    setExpandedPickerFor(null);
    setActivePath((prev) => [...prev, childId]);
  };

  if (!id) {
    return (
      <div style={{ textAlign: 'center', paddingTop: 100 }}>
        <Typography.Text type="secondary">无效的会话</Typography.Text>
      </div>
    );
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: 'calc(100vh - 180px)' }}>
      {isBenchmark && (
        <div style={{ padding: '12px 0 0 12px' }}>
          <Button
            icon={<ArrowLeftOutlined />}
            style={{ alignSelf: 'flex-start', width: 'fit-content' }}
            onClick={() => navigate(returnUrl)}
          >
            返回评估
          </Button>
        </div>
      )}
      <style>{`
        .agent-chat-tabs {
          display: flex;
          flex-direction: column;
          height: 100%;
        }
        .agent-chat-tabs .ant-tabs-nav {
          margin-bottom: 0;
        }
        .agent-chat-tabs .ant-tabs-content-holder {
          flex: 1;
          overflow: hidden;
        }
        .agent-chat-tabs .ant-tabs-content {
          height: 100%;
        }
        .agent-chat-tabs .ant-tabs-tabpane {
          height: 100%;
        }
        .agent-chat-count-tab {
          display: inline-flex;
          align-items: center;
          gap: 4px;
          padding: 0;
          margin: 0;
          color: #8c8c8c;
          font-size: 13px;
          cursor: pointer;
          user-select: none;
          white-space: nowrap;
        }
        .agent-chat-count-tab:hover {
          color: #569cd6;
        }
        .agent-chat-tab-label {
          display: inline-flex;
          align-items: center;
          gap: 6px;
        }
        .agent-chat-tab-delete {
          font-size: 12px;
          color: #8c8c8c;
          cursor: pointer;
          transition: color 0.2s;
        }
        .agent-chat-tab-delete:hover {
          color: #ff4d4f;
        }
        /* 统一标签间距：主会话标签、子会话名称标签、计数标签之间统一 12px（覆盖 antd 默认 tab 间距 32px），选中前后位置一致 */
        .agent-chat-tabs .ant-tabs-tab + .ant-tabs-tab {
          margin-left: 12px;
        }
      `}</style>
      <Tabs
        className="agent-chat-tabs"
        activeKey={activeTab}
        onChange={handleTabChange}
        style={{ display: 'flex', flexDirection: 'column', height: '100%' }}
        items={sessionTabItems}
      />
    </div>
  );
}

export default AgentChat;
