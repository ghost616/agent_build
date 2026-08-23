import { useCallback, useEffect, useRef, useState } from 'react';
import { message } from 'antd';
import type { Evaluation } from '../../../types/evaluation';
import {
  executeEvaluation,
  createEvalSession,
  generateEvalResult,
  getEvaluationCacheStatus,
  getEvaluationStream,
  getGenerateStatus,
  removeEvaluationCache,
} from '../../../services/evaluation';
import {
  agentChatStream,
  completeSubSession,
  continueChatStream,
  executeTools,
  fetchConversationId,
  getSubSessionData,
  getToolStatus,
} from '../../../services/session';
import { executeBrowserTool } from '../../../services/toolExecutor';
import {
  registerSessionPage,
  unregisterSessionPage,
  SEND_USER_MESSAGE_MARKER,
} from '../../../services/messageDispatcher';
import type {
  SendUserMessagePayload,
  SessionPageHandler,
} from '../../../services/messageDispatcher';

const sleep = (ms: number): Promise<void> =>
  new Promise((r) => setTimeout(r, ms));

/**
 * 子会话流程执行参数（工具触发与 WebSocket 触发共用，入口差异仅参数）。
 */
interface SubSessionFlowParams {
  /** 子会话 ID。 */
  childId: string;
  /** 展示用用户消息内容。 */
  userContent: string;
  /** 传给对话接口的请求内容（WS 触发传 SEND_USER_MESSAGE_MARKER，工具触发传 data.userMessage）。 */
  streamContent: string;
  /** 思考模式（仅工具触发传 data.thinking）。 */
  thinking?: boolean;
  /** 是否由 WebSocket 触发（true 时跳过 completeSubSession：消息已由后端保存，不重复保存 user 消息）。 */
  fromWs?: boolean;
}

export function useEvaluationExecute(): {
  execute: (evaluationId: string, evaluation: Evaluation, onRefresh?: () => Promise<void>) => Promise<void>;
  executing: boolean;
  executionProgress: string;
  foregroundModalVisible: boolean;
  foregroundLog: string[];
  foregroundLogRef: React.RefObject<HTMLDivElement | null>;
  handleCancelForeground: () => void;
} {
  const [executing, setExecuting] = useState(false);
  const [executionProgress, setExecutionProgress] = useState('');
  const [foregroundModalVisible, setForegroundModalVisible] = useState(false);
  const [foregroundLog, setForegroundLog] = useState<string[]>([]);
  const abortRef = useRef<AbortController | null>(null);
  const executingRef = useRef(false);
  const foregroundLogRef = useRef<HTMLDivElement | null>(null);
  /** 统一日志数组引用：执行流程与 WebSocket 回调共用同一引用（解决异步闭包过期），每次 execute 开始重置。 */
  const logLinesRef = useRef<string[]>([]);
  /** 当前执行会话 ID（用于注册/注销 WebSocket 消息分发处理器）。 */
  const executionSessionIdRef = useRef<string | null>(null);
  /** 执行会话对话流式请求进行中（WS 回调据此判断执行会话是否空闲）。 */
  const loadingRef = useRef(false);
  /** 执行会话工具循环进行中（WS 回调据此判断执行会话是否空闲）。 */
  const toolExecutingRef = useRef(false);
  /** 进行中的子会话 ID 集合（同一子会话并发/重发消息去重）。 */
  const activeChildIdsRef = useRef<Set<string>>(new Set());
  const streamChildReplyRef = useRef<(message: SendUserMessagePayload) => void>(() => {});
  const onSessionMessageRef = useRef<(message: SendUserMessagePayload) => void>(() => {});
  const handleSubSessionFlowRef = useRef<(params: SubSessionFlowParams) => Promise<void>>(async () => {});
  const continueEvaluationChatRef = useRef<() => void>(() => {});

  useEffect(() => {
    if (foregroundLogRef.current) {
      foregroundLogRef.current.scrollTop = foregroundLogRef.current.scrollHeight;
    }
  }, [foregroundLog]);

  /** 追加日志行并同步到前台日志状态（写入 logLinesRef 持有的统一日志数组）。 */
  const pushLog = useCallback((...lines: string[]): void => {
    logLinesRef.current.push(...lines);
    setForegroundLog([...logLinesRef.current]);
  }, []);

  /** 追加流式文本：末行以指定前缀开头时续接，否则新建日志行。 */
  const appendStreamText = useCallback((prefix: string, text: string): void => {
    const logLines = logLinesRef.current;
    const last = logLines[logLines.length - 1];
    if (last?.startsWith(prefix)) {
      logLines[logLines.length - 1] = last + text;
    } else {
      logLines.push(`${prefix} ${text}`);
    }
    setForegroundLog([...logLines]);
  }, []);

  const streamEvaluation = useCallback(
    async (cacheId: string): Promise<void> => {
      return new Promise<void>((resolve, reject) => {
        const controller = getEvaluationStream(cacheId, {
          onDelta: (text) => appendStreamText('[AI]', text),
          onReasoning: (text) => appendStreamText('[思考]', text),
          onDone: () => resolve(),
          onError: (err) => reject(err),
        });
        abortRef.current = controller;
      });
    },
    [appendStreamText],
  );

  const streamBackground = useCallback(
    async (executionSessionId: string): Promise<void> => {
      let hasCache = true;
      while (hasCache && executingRef.current) {
        const cacheStatus = await getEvaluationCacheStatus(executionSessionId);
        hasCache = cacheStatus.hasCache;
        if (!hasCache || !cacheStatus.cacheId) break;
        await streamEvaluation(cacheStatus.cacheId);
        if (!executingRef.current) return;
        await removeEvaluationCache(cacheStatus.cacheId);
      }
    },
    [streamEvaluation],
  );

  const pollGenerateStatus = useCallback(
    async (id: string, sessionId: string): Promise<void> => {
      while (executingRef.current) {
        const status = await getGenerateStatus(id, sessionId);
        setExecutionProgress(`生成进度: ${status.currentStep ?? '-'}/${status.totalSteps ?? '-'}`);
        if (status.status.toUpperCase() === 'COMPLETED' || status.status.toUpperCase() === 'FAILED') {
          return;
        }
        await sleep(2000);
      }
    },
    [],
  );

  /**
   * 子会话完整执行器（工具触发与 WebSocket 触发共用）：
   * agentChatStream 流式回复（[子会话AI]/[子会话思考] 日志行）→ onDone(hasToolCalls) 为 true 时
   * 执行子会话工具循环（executeTools → pollSubToolStatus → continueChatStream）直至无工具调用 →
   * 工具触发（fromWs=false）时 completeSubSession 收尾（WS 触发 fromWs=true 跳过，消息已由后端保存）。
   * 入口差异仅参数：WS 触发 streamContent 传 SEND_USER_MESSAGE_MARKER，工具触发传 data.userMessage + thinking。
   * @param params 子会话执行参数
   */
  const handleSubSessionFlow = useCallback(
    async (params: SubSessionFlowParams): Promise<void> => {
      const { childId, userContent, streamContent, thinking, fromWs } = params;
      if (!childId) return;
      // 同一子会话已有流程进行中时忽略（WS 重发/并发防护）
      if (activeChildIdsRef.current.has(childId)) return;
      activeChildIdsRef.current.add(childId);

      try {
        pushLog('[子会话] 开始子会话流程...');
        if (!fromWs) {
          pushLog(`[子会话] 用户消息: ${userContent}`);
        }

        const sendSubMessage = (content: string): Promise<boolean> =>
          new Promise((resolve) => {
            const controller = agentChatStream(
              { sessionId: childId, content, thinking },
              {
                onDelta: (text) => appendStreamText('[子会话AI]', text),
                onReasoning: (text) => appendStreamText('[子会话思考]', text),
                onDone: (hasToolCalls) => resolve(hasToolCalls),
                onError: () => resolve(false),
              },
            );
            abortRef.current = controller;
          });

        const continueSubChat = (): Promise<boolean> =>
          new Promise((resolve) => {
            const controller = continueChatStream(childId, {
              onDelta: (text) => appendStreamText('[子会话AI]', text),
              onReasoning: (text) => appendStreamText('[子会话思考]', text),
              onDone: (hasToolCalls) => resolve(hasToolCalls),
              onError: () => resolve(false),
            });
            abortRef.current = controller;
          });

        const pollSubToolStatus = async (sid: string, tid: string): Promise<boolean> => {
          while (executingRef.current) {
            await sleep(1000);
            const status = await getToolStatus(sid, tid);
            pushLog(`[子会话工具] 状态: ${status.status}`);

            if (status.status === 'done') {
              if (status.result) {
                pushLog(`[子会话工具] 结果: ${status.result}`);
              }
              return true;
            }
            if (status.status === 'error') {
              pushLog(`[子会话工具] 执行失败: ${status.result || '未知错误'}`);
              return false;
            }
            if (status.status === 'idle') continue;
            if (status.toolConfig?.subToolType === 'BROWSER') {
              await executeBrowserTool(sid, tid, status);
              await sleep(500);
              continue;
            }
          }
          return false;
        };

        const runSubTools = async (): Promise<boolean> => {
          while (executingRef.current) {
            const execResult = await executeTools(childId);
            if (execResult.status === 'empty') return true;
            if (execResult.status === 'failed') {
              pushLog('[子会话工具] 工具执行失败');
              return false;
            }

            pushLog(`[子会话工具] 执行: ${execResult.toolName || execResult.toolId || ''}`);
            if (execResult.arguments) {
              pushLog(`[子会话工具] 参数: ${execResult.arguments}`);
            }

            if (!execResult.toolId) return true;

            const succeeded = await pollSubToolStatus(childId, execResult.toolId);
            if (!succeeded) return false;
          }
          return false;
        };

        let hasToolCalls = await sendSubMessage(streamContent);
        while (hasToolCalls && executingRef.current) {
          const ok = await runSubTools();
          if (!ok) break;
          hasToolCalls = await continueSubChat();
        }

        // 收尾：仅工具触发（fromWs=false，TOOL_CALL 模式）通知后端子会话完成；
        // WS 触发（fromWs=true）跳过——消息已由后端保存，后端 subSessionDataMap 仅工具模式写入
        if (!fromWs) {
          const parentSessionId = executionSessionIdRef.current;
          if (parentSessionId) {
            await completeSubSession(parentSessionId);
          }
        }
        pushLog('[子会话] 子会话流程完成');
      } catch {
        pushLog('[子会话] 子会话流程执行失败');
        message.error('子会话流程执行失败');
      } finally {
        activeChildIdsRef.current.delete(childId);
      }
    },
    [appendStreamText, pushLog],
  );

  handleSubSessionFlowRef.current = handleSubSessionFlow;

  /**
   * 主会话工具循环（含子会话回调）：executeTools → 轮询 getToolStatus →
   * 检测 needsSubSessionFlow 时获取子会话数据并调用统一子会话执行器（工具触发）→
   * 全部工具完成后 continueChatStream 流式续接，onDone 有更多工具时递归继续。
   * @param sessionId 执行会话 ID
   * @param toolLoopCount 工具循环计数对象（跨递归共享）
   * @param maxToolLoops 最大工具循环次数
   */
  const runToolCycle = useCallback(
    async (
      sessionId: string,
      toolLoopCount: { current: number },
      maxToolLoops: number,
    ): Promise<void> => {
      if (toolLoopCount.current >= maxToolLoops) {
        pushLog('[工具] 达到最大工具调用次数');
        return;
      }
      toolLoopCount.current++;
      toolExecutingRef.current = true;
      try {
        while (executingRef.current) {
          const execResult = await executeTools(sessionId);
          if (execResult.status === 'empty') return;
          if (execResult.status === 'failed') {
            pushLog('[工具] 工具执行失败');
            return;
          }

          let currentResult = execResult;
          pushLog(`[工具] 执行: ${currentResult.toolName || currentResult.toolId || ''}`);
          if (currentResult.arguments) {
            pushLog(`[工具] 参数: ${currentResult.arguments}`);
          }

          if (!currentResult.toolId) {
            throw new Error('工具 ID 为空');
          }

          const toolId: string = currentResult.toolId;
          let pollComplete = false;
          while (executingRef.current) {
            const toolStatus = await getToolStatus(sessionId, toolId);
            pushLog(`[工具] 状态: ${toolStatus.status}`);

            if (toolStatus.needsSubSessionFlow) {
              const data = await getSubSessionData(sessionId);
              if (data) {
                await handleSubSessionFlow({
                  childId: data.childSessionId,
                  userContent: data.userMessage,
                  streamContent: data.userMessage,
                  thinking: data.thinking,
                  fromWs: false,
                });
              } else {
                pushLog('[子会话] 获取子会话数据失败');
              }
              continue;
            }

            if (toolStatus.status === 'done' || toolStatus.status === 'error' || toolStatus.status === 'failed') {
              if (toolStatus.result) {
                pushLog(`[工具] 结果: ${toolStatus.result}`);
              }
              if (toolStatus.hasMore) {
                const nextExec = await executeTools(sessionId);
                if (nextExec.status === 'empty') return;
                pushLog(
                  `[工具] 继续执行: ${nextExec.toolName || nextExec.toolId || ''}`,
                );
                currentResult = nextExec;
                continue;
              }
              pollComplete = true;
              break;
            }
            if (toolStatus.toolConfig?.subToolType === 'BROWSER') {
              await executeBrowserTool(sessionId, toolId, toolStatus);
              await sleep(500);
              continue;
            }
            await sleep(1000);
          }

          if (pollComplete) {
            break;
          }
        }

        await new Promise<void>((resolve, reject) => {
          const controller = continueChatStream(sessionId, {
            onDelta: (text) => appendStreamText('[AI]', text),
            onReasoning: (text) => appendStreamText('[思考]', text),
            onDone: async (moreToolCalls) => {
              if (moreToolCalls) {
                try {
                  await runToolCycle(sessionId, toolLoopCount, maxToolLoops);
                  resolve();
                } catch (err) {
                  reject(err);
                }
              } else {
                resolve();
              }
            },
            onError: (err) => reject(err),
          });
          abortRef.current = controller;
        });
      } finally {
        toolExecutingRef.current = false;
      }
    },
    [appendStreamText, handleSubSessionFlow, pushLog],
  );

  /**
   * 发送前台消息：先获取 conversationId，再 agentChatStream 流式接收（[AI]/[思考] 日志行），
   * onDone 有工具调用时进入主会话工具循环；loadingRef 标记执行会话对话进行中。
   * @param sessionId 执行会话 ID
   * @param content 消息内容
   */
  const sendForegroundMessage = useCallback(
    async (sessionId: string, content: string): Promise<void> => {
      const toolLoopCount = { current: 0 };
      const MAX_TOOL_LOOPS = 10;

      let conversationId: string;
      try {
        conversationId = await fetchConversationId();
      } catch {
        throw new Error('获取会话标识失败');
      }

      loadingRef.current = true;
      try {
        await new Promise<void>((resolve, reject) => {
          const controller = agentChatStream(
            { sessionId, content, conversationId },
            {
              onDelta: (text) => appendStreamText('[AI]', text),
              onReasoning: (text) => appendStreamText('[思考]', text),
              onDone: async (hasToolCalls) => {
                try {
                  if (hasToolCalls) {
                    await runToolCycle(sessionId, toolLoopCount, MAX_TOOL_LOOPS);
                  }
                  resolve();
                } catch (err) {
                  reject(err);
                }
              },
              onError: (err) => reject(err),
            },
          );
          abortRef.current = controller;
        });
      } finally {
        loadingRef.current = false;
      }
    },
    [appendStreamText, runToolCycle],
  );

  /**
   * 执行会话基于新消息链续接（子→主回传且执行会话空闲时触发）：
   * 以 SEND_USER_MESSAGE_MARKER 作为 content 调用 agentChatStream
   * （marker 不会被后端保存为重复用户消息），流式回复追加 [AI]/[思考] 日志行，
   * onDone 有工具调用时进入主会话工具循环。
   */
  const continueEvaluationChat = useCallback((): void => {
    if (loadingRef.current || toolExecutingRef.current) return;
    const sessionId = executionSessionIdRef.current;
    if (!sessionId) return;
    loadingRef.current = true;
    const toolLoopCount = { current: 0 };
    const MAX_TOOL_LOOPS = 10;
    abortRef.current = agentChatStream(
      { sessionId, content: SEND_USER_MESSAGE_MARKER },
      {
        onDelta: (text) => appendStreamText('[AI]', text),
        onReasoning: (text) => appendStreamText('[思考]', text),
        onDone: async (hasToolCalls) => {
          if (hasToolCalls) {
            try {
              await runToolCycle(sessionId, toolLoopCount, MAX_TOOL_LOOPS);
            } catch {
              // 续接工具循环异常不中断执行日志
            }
          }
          loadingRef.current = false;
        },
        onError: () => {
          loadingRef.current = false;
        },
      },
    );
  }, [appendStreamText, runToolCycle]);

  continueEvaluationChatRef.current = continueEvaluationChat;

  /**
   * 主→子发送（WS 触发）：追加 [子会话] {content} 日志行并以 SEND_USER_MESSAGE_MARKER
   * 调用统一子会话执行器流式展示回复（回复经 handleSubSessionFlow 追加 [子会话AI] 行）。
   * 子→主回传（sessionId 为执行会话自身）转由 onSessionMessage 统一处理。
   * @param payload SEND_USER_MESSAGE 消息负载
   */
  const streamChildReply = useCallback(
    (payload: SendUserMessagePayload): void => {
      const childId = payload.sessionId;
      if (!childId) return;
      const executionSessionId = executionSessionIdRef.current;
      if (!executionSessionId) return;
      // 子→主回传（sessionId 为执行会话自身）转由 onSessionMessage 统一处理
      if (childId === executionSessionId) {
        onSessionMessageRef.current(payload);
        return;
      }
      // 同一子会话已有流程进行中时忽略新消息
      if (activeChildIdsRef.current.has(childId)) return;
      const content = payload.content || '';
      pushLog(`[子会话] ${content}`);
      void handleSubSessionFlowRef.current({
        childId,
        userContent: content,
        streamContent: SEND_USER_MESSAGE_MARKER,
        fromWs: true,
      });
    },
    [pushLog],
  );

  streamChildReplyRef.current = streamChildReply;

  /**
   * 子→主回传统一处理（WS 触发，sessionId === 执行会话）：
   * 追加 [子会话回传] {content} 日志行；执行会话执行中（loading/toolExecuting）仅追加日志，
   * 空闲则以 SEND_USER_MESSAGE_MARKER 触发主会话续接（[AI] 行）。
   * @param payload SEND_USER_MESSAGE 消息负载
   */
  const onSessionMessage = useCallback(
    (payload: SendUserMessagePayload): void => {
      const targetId = payload.sessionId;
      if (!targetId) return;
      // 防御：消息非执行会话自身（主→子发送）时转由子会话执行器处理
      if (targetId !== executionSessionIdRef.current) {
        streamChildReplyRef.current(payload);
        return;
      }
      pushLog(`[子会话回传] ${payload.content || ''}`);
      // 执行中仅追加日志；空闲则以 marker 触发主会话基于新消息链继续
      if (loadingRef.current || toolExecutingRef.current) return;
      if (!executingRef.current) return;
      continueEvaluationChatRef.current();
    },
    [pushLog],
  );

  onSessionMessageRef.current = onSessionMessage;

  /**
   * 注册 WebSocket 消息分发页面处理器（以执行会话 ID 为主会话）：
   * 同一页面重复执行时先注销旧执行会话的处理器；多页面共存互不干扰（按主会话 ID 索引）。
   * @param executionSessionId 执行会话 ID
   */
  const registerEvaluationHandler = useCallback((executionSessionId: string): void => {
    if (executionSessionIdRef.current && executionSessionIdRef.current !== executionSessionId) {
      unregisterSessionPage(executionSessionIdRef.current);
    }
    executionSessionIdRef.current = executionSessionId;
    const handler: SessionPageHandler = {
      mainSessionId: executionSessionId,
      streamChildReply: (msg) => streamChildReplyRef.current(msg),
      onSessionMessage: (msg) => onSessionMessageRef.current(msg),
    };
    registerSessionPage(handler);
  }, []);

  /** 注销当前执行会话的 WebSocket 消息分发处理器（前台执行未运行时无 handler）。 */
  const unregisterEvaluationHandler = useCallback((): void => {
    const executionSessionId = executionSessionIdRef.current;
    if (executionSessionId) {
      unregisterSessionPage(executionSessionId);
      executionSessionIdRef.current = null;
    }
  }, []);

  // 组件卸载时注销处理器（避免遗留 handler 干扰其他页面）
  useEffect(() => {
    return () => {
      unregisterEvaluationHandler();
    };
  }, [unregisterEvaluationHandler]);

  const execute = useCallback(
    async (
      evaluationId: string,
      evaluation: Evaluation,
      onRefresh?: () => Promise<void>,
    ): Promise<void> => {
      if (!evaluationId || !evaluation) return;
      setExecuting(true);
      executingRef.current = true;
      // 每次执行重置统一日志数组与前台日志（重复执行重置日志与 handler）
      logLinesRef.current = [];
      activeChildIdsRef.current.clear();
      setForegroundLog([]);
      const executionType = evaluation.executionType || 'BACKGROUND';

      try {
        if (executionType === 'BACKGROUND') {
          setForegroundModalVisible(true);

          for (let i = 0; i < evaluation.executionCount; i++) {
            if (!executingRef.current) break;
            pushLog(`\n========== 第 ${i + 1}/${evaluation.executionCount} 次执行 ==========`);
            setExecutionProgress(`执行中(第 ${i + 1}/${evaluation.executionCount} 次)...`);

            const execResult = await executeEvaluation(evaluationId);
            const executionSessionId = execResult.executionSessionId;
            if (executionSessionId) {
              pushLog(`执行会话: ${executionSessionId}`);
              await streamBackground(executionSessionId);
            }
          }

          if (executingRef.current) {
            message.success('后台执行完成');
          }
        } else {
          setForegroundModalVisible(true);

          for (let i = 0; i < evaluation.executionCount; i++) {
            if (!executingRef.current) break;
            pushLog(`\n========== 第 ${i + 1}/${evaluation.executionCount} 次执行 ==========`);

            pushLog('正在创建评估会话...');

            const evalSession = await createEvalSession(evaluationId);
            pushLog(`会话已创建: ${evalSession.sessionId}`);
            pushLog(`待处理消息数: ${evalSession.userMessages.length}`);

            // 创建执行会话后以 executionSessionId 注册 WebSocket 子会话分发处理器
            registerEvaluationHandler(evalSession.sessionId);

            for (let j = 0; j < evalSession.userMessages.length; j++) {
              if (!executingRef.current) break;
              const msg = evalSession.userMessages[j];
              pushLog(`\n--- 消息 ${j + 1}/${evalSession.userMessages.length} ---`);
              pushLog(`[用户] ${msg}`);

              await sendForegroundMessage(evalSession.sessionId, msg);
            }

            if (!executingRef.current) break;

            pushLog('\n正在生成评估结果...');
            await generateEvalResult(evaluationId, evalSession.sessionId);
            await pollGenerateStatus(evaluationId, evalSession.sessionId);
            pushLog('评估结果已生成');
          }

          if (executingRef.current) {
            message.success('前台执行完成');
          }
        }
      } catch (err) {
        if (err instanceof DOMException && err.name === 'AbortError') {
          return;
        }
        message.error('执行失败: ' + (err instanceof Error ? err.message : String(err)));
      } finally {
        setExecuting(false);
        executingRef.current = false;
        setExecutionProgress('');
        loadingRef.current = false;
        toolExecutingRef.current = false;
        activeChildIdsRef.current.clear();
        // 执行结束注销 WebSocket 消息分发处理器
        unregisterEvaluationHandler();
        if (onRefresh) {
          await onRefresh();
        }
        await sleep(1500);
        setForegroundModalVisible(false);
      }
    },
    [
      pollGenerateStatus,
      pushLog,
      registerEvaluationHandler,
      sendForegroundMessage,
      streamBackground,
      unregisterEvaluationHandler,
    ],
  );

  const handleCancelForeground = (): void => {
    if (abortRef.current) {
      abortRef.current.abort();
      abortRef.current = null;
    }
    executingRef.current = false;
    setExecuting(false);
    setForegroundModalVisible(false);
    loadingRef.current = false;
    toolExecutingRef.current = false;
    activeChildIdsRef.current.clear();
    // 取消执行注销 WebSocket 消息分发处理器
    unregisterEvaluationHandler();
  };

  return {
    execute,
    executing,
    executionProgress,
    foregroundModalVisible,
    foregroundLog,
    foregroundLogRef,
    handleCancelForeground,
  };
}
