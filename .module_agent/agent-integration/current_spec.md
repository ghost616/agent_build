# agent-integration 模块功能说明

> 待力牧首次执行后填充，记录模块公共方法与功能。
## agent-integration 模块功能说明

- **AgentAssembler**：Agent 组件组装类，build() 方法不再调用 chatService.initHooks()，hooks 初始化移至 AgentContextConfiguration 的 chatService Bean 中显式执行，以解决 MessageSavePostHook 创建时的 Bean 时序依赖问题
- **AgentAssembler**：build() 方法构造 ChatDataProviderProxy 代理，将 MessageSavePostHook 通过代理注入到 ChatService；暴露 messageSavePostHook() getter 并在 Result record 中包含该实例
- **AgentAssembler**：build() 方法构造 SystemToolProviderProxy 代理，确保 history_query/load_skills/unload_skills 三个系统工具始终可用；SystemToolManager 使用代理而非原始 SystemToolProvider
- **AgentAssembler**：build() 方法内部使用 AgentComponentRegistry 统一装配所有 Provider/Manager/Tracker，registry 不对外暴露；对外 getter 方法签名不变
- **SubSessionCallbackSystemTool**：参数 schema 新增 thinking（boolean，可选）；execute() 解析工具参数 JSON 中的 thinking 字段（默认 null）并传递给 createChildSession 方法
- **SubSessionCallbackSystemTool**：execute() 方法改为通过构造函数注入的 SubSessionCallback 回调发送子会话消息，而非直接调用 ctx.sendUserMessage()；thinking 参数通过回调的第三个参数传递
- **AgentAssembler**：构造函数新增 MessageSender 参数（可为 null），build() 中调用 registry.setMessageSender(messageSender) 传入 MessageSender 实例
- **AgentAssembler**：build() 方法构造 SystemToolProviderProxy 代理，确保 history_query/load_skills/unload_skills/session_variable/conversation_variable 五个系统工具始终可用；SystemToolManager 使用代理而非原始 SystemToolProvider
- **AgentAssembler**：build() 方法中创建共享 HookManager 实例，通过 setter 注入 ChatService 和 ToolExecutionService，并统一调用 hookManager.refreshHooks(chatDataProvider.getHooks())，将 hooks 初始化从 AgentContextConfiguration 移至 build() 中统一管理
- **AgentAssembler**：新增私有字段 hookManager 及公开方法 refreshHooks()，将刷新操作从 build() 中解耦，支持事后调用刷新
- **ChatDataProviderProxy**：实现 ChatDataProvider.getHooks(Long sessionId) 方法，直接委托给 delegate.getHooks(sessionId)
- **AgentAssembler**：refreshHooks() 方法中更改 hookManager.refreshHooks(chatDataProviderProxy.getHooks()) 为 hookManager.refreshHooks() 无参调用；HookManager 构造函数改为 new HookManager(registry)
- **AgentAssembler**：移除 messageSavePostHook() 公开 getter 方法（无人调用）；Result record 和 ChatDataProviderProxy 内部仍保留对 MessageSavePostHook 的引用
- **AgentAssembler**：新增 setAgentLog(AgentLog) 公开方法，仅当 registry 已存在（build() 之后）时设置到 registry，无自有 AgentLog 暂存字段；build() 不自动注册 agentLog
- **AgentAssembler**：新增 setChatDataCacheManager(ChatDataCacheManager) 公开方法，参考 setAgentLog 模式，仅当 registry 已存在（build() 之后）时设置到 registry，无自有 ChatDataCacheManager 暂存字段；build() 不自动注册 chatDataCacheManager
- **AgentAssembler**：新增 private AgentMessageProxy agentMessageProxy 字段保留 build() 创建的引用；setChatDataCacheManager(ChatDataCacheManager) 方法在设置 registry 后，若 agentMessageProxy 非 null 则调用 agentMessageProxy.setChatDataCacheManager(chatDataCacheManager) 透传
- **AgentAssembler**：新增 setThreadVariableHandler(ThreadVariableHandler) 公开方法与 threadVariableHandler 暂存字段，build() 中通过 registry.setThreadVariableHandler(threadVariableHandler) 将线程变量处理器注册到 AgentComponentRegistry；setThreadVariableHandler 在赋值暂存字段后，若 registry 已存在（build() 之后）则立即同步注册到已建 registry（与 setAgentLog/setChatDataCacheManager 注册模式一致），build 前后调用均生效
- **SubSessionCallbackSystemTool**：execute() 中调用 callback.execute(ctx, childSessionId, userMessage, thinking)，适配 SubSessionCallback 接口新增的 AgentExecutionContext 首参（agent-base 已变更接口签名为 execute(AgentExecutionContext ctx, String sessionId, String userMessage, Boolean thinking)），将当前执行上下文 ctx 透传给回调；测试同步适配 mock 调用增加 ctx 参数
- **SubSessionCallbackTool**：由 SubSessionCallbackSystemTool 重命名而来，由实现 SystemTool 接口改为继承 CustomToolInvoker（工具名沿用 callback_sub_session）。构造函数改为 (ToolConfigDTO toolConfig, SubSessionCallback callback)，新增静态 createToolConfig() 生成 ToolConfigDTO（toolType=CUSTOM、id=null、name=callback_sub_session，描述与参数 schema 与原 getDescription/getParameterSchema 内容一致），移除 getToolName/getDescription/getParameterSchema 接口实现；execute() 核心逻辑与错误返回行为不变。注册方由 platform-app AgentContextConfiguration.systemToolProvider 迁移至 DefaultToolDataProvider（getToolById 返回 createToolConfig 并 setId(工具名)、getCustomInvoker 构造 SubSessionCallbackTool、getSessionToolIds 恒注入 callback_sub_session）；ToolExecutionController 的 _sys_callback_sub_session 特判同步改为 callback_sub_session
- **SendResultToParentTool**：新增自定义工具 send_result_to_parent（extends CustomToolInvoker，无 @Component），构造函数传参 ToolConfigDTO，静态 createToolConfig() 生成 ToolConfigDTO（toolType=CUSTOM、id=null、name=send_result_to_parent，parameterSchema 含单个必填参数 result）；execute() 解析 result 参数后从 ctx.getParentSessionId() 获取父会话 ID，无父会话 ID 返回错误 JSON，有则调用 ctx.sendUserMessage(parentSessionId, result, ctx.getModelId(), null) 发送执行结果并返回成功 JSON；错误处理沿用 HistoryQueryTool/SubSessionCallbackTool 风格（JsonMapper 序列化错误 JSON，异常捕获返回错误 JSON）。
- **SendResultToParentTool 发送方式变更**：execute() 由 ctx.sendUserMessage(parentSessionId, result, ctx.getModelId(), null) 改为调用 agent-base 新增的 ctx.sendParentMessage(result)（AgentExecutionContext 新方法，内部从 context.parentSessionId 获取父会话 ID，null/空白静默忽略，callback 为 null 静默忽略，动态获取 conversationId 后经 sendParentMessageCallback 发送到父会话）；execute() 中仍保留 ctx.getParentSessionId() 前置检查（无父会话 ID 返回 {"status":"error","errMsg":"当前会话不是子会话，无法发送结果到父会话"}），result 必填校验与成功返回 JSON（{"status":"success","message":"已发送执行结果到父会话"}）不变。
## 模块职责
提供多平台模型调用器的实现（ModelInvoker）和 Agent 组件的组装能力。

## 核心功能

### 模型调用器（ModelInvoker 实现）
- **OpenAIInvoker**：OpenAI 兼容平台的模型调用，支持同步 invoke、流式 invokeStream、模型 verify 和工具定义转换
- **OllamaInvoker**：Ollama 本地模型的调用，支持同步/流式模式
- **AnthropicInvoker**：Anthropic Claude 模型的调用，使用 SSE 事件流解析
- **AzureInvoker**：Azure OpenAI 服务调用，继承 OpenAIInvoker 并覆盖 API URL
- **DeepSeekInvoker**：DeepSeek 平台调用（OpenAI 兼容协议）
- **KimiInvoker**：Kimi 平台调用（OpenAI 兼容协议，继承 OpenAIInvoker）
- **VolcEngineInvoker**：火山引擎平台调用（OpenAI 兼容协议，继承 OpenAIInvoker）
- **CustomInvoker**：自定义通用 OpenAI 兼容端点调用

### 工厂与组装
- **DefaultModelInvokerFactory**：根据平台类型（OPENAI/ANTHROPIC/AZURE/OLLAMA/KIMI/VOLCENGINE/DEEPSEEK/CUSTOM）创建对应 Invoker
- **Build**：接收 DataProvider 和 ModelInvokerFactory 依赖，组装完整的 ChatService 和 ToolExecutionService 实例

- **SubSessionCallbackSystemTool**：实现 SystemTool 接口的系统工具。工具名 callback_sub_session，通过构造函数注入 SubSessionCallback 回调，支持按名称列表匹配工具和技能创建子会话，并通过回调执行用户消息返回结果。
### Usage 导出到流式 Chunk
- **OpenAIInvoker.parseStreamChunk**：从流式 JSON 根节点解析 usage（prompt_tokens/completion_tokens/total_tokens），设置到 ChatChunk.usage 字段
- **AnthropicInvoker.invokeStream**：通过 usageHolder 捕获 message_delta 事件中的 usage（input_tokens/output_tokens），在最终 stop chunk 中设置 ChatChunk.usage
- **OllamaInvoker.parseStreamChunk**：在 done=true 的最终 chunk 中解析 eval_count/prompt_eval_count，设置到 ChatChunk.usage

### 浏览器工具（Browser Tool）
- **BrowserToolProvider**：函数式接口（原 BrowserToolCallback，已重命名），定义 `execute(String sessionId, String toolConfigId, String toolName, String toolParams)` 方法，用于浏览器工具的回调执行
- **BrowserToolInvoker**：继承 CustomToolInvoker 的自定义工具调用器。构造函数注入 BrowserToolProvider 回调，execute() 从 AgentExecutionContext 获取 sessionId、从 ToolConfigDTO 获取 toolId/toolName，将参数 JSON 传递给回调执行；提供 loadJsContent() 方法从 classpath 加载 browser_tool_executor.js、getJsContent() 返回缓存的 JS 内容
- **browser_tool_executor.js**：JS 工具执行引擎，定义 AgentExecutionContext 对象、ToolFunction 工具函数定义、ToolManager 工具函数管理器（按 toolName 绑定/添加/移除/get）、四个工具执行函数：getAgentExecutionContext 获取上下文、getToolResult 从管理器获取工具执行结果、passToolResult 回传结果给宿主、execute 主执行入口

- **OpenAIResponsesInvoker**：OpenAI Responses API 模型调用器，实现 ModelInvoker 接口，使用 /v1/responses 端点。同步 invoke 使用 instructions+input 请求格式，解析 output 数组提取 message content（output_text）与 function_call 到 ChatResponse（含 responseId 供多轮续接）；流式 invokeStream 解析 SSE 事件：response.output_text.delta→delta、response.function_call_arguments.delta→toolCalls（index+arguments）、response.web_search_call.in_progress/searching/completed→webSearchCall（completed 时从 results 数组解析 title/url/snippet）、response.completed→finishReason+usage；verify() 使用 GET /v1/models；toToolDefinition() 与 OpenAIInvoker 一致。input 消息转换：system 跳过、user/assistant 普通文本、assistant 工具调用转为 content 中的 function_call 部件、tool 角色转为 function_call_output
- **OpenAIResponsesInvoker.buildBuiltinTools(ChatRequest)**：受保护方法从 request.builtinTools 读取内置工具列表（未传或为空返回空列表，请求不传则不启用）；buildRequestBody() 在 buildTools(request.getTools()) 之后将内置工具合并到 tools 数组，仅当 tools 非空时写入 body；子类（DeepSeek/Kimi/VolcEngine/Azure/Custom）不覆写该方法，统一由父类处理

- **DefaultModelInvokerFactory**：createInvoker() 先判断 requestType（responses/responses_stateless）再按 platformType 路由到对应 ResponsesInvoker（OPENAI/DEEPSEEK/KIMI/VOLCENGINE/AZURE/CUSTOM），否则走原 switch 返回 Chat Completions Invoker；ANTHROPIC/OLLAMA 不参与 Responses 路由，保持原分支不变
- **DeepSeekResponsesInvoker**：DeepSeek 平台 Responses API 调用器，继承 OpenAIResponsesInvoker，无额外覆写
- **KimiResponsesInvoker**：Kimi 平台 Responses API 调用器，继承 OpenAIResponsesInvoker；覆写 buildRequestBody 保留模型适配逻辑：K2_7_CODE 模型移除 reasoning，K3 模型将 thinking 映射为 reasoning（effort=max）
- **VolcEngineResponsesInvoker**：火山引擎平台 Responses API 调用器，继承 OpenAIResponsesInvoker，无额外覆写
- **AzureResponsesInvoker**：Azure OpenAI 平台 Responses API 调用器，继承 OpenAIResponsesInvoker；覆写 buildResponsesUrl=mountResponsesResourceUrl 使用 Azure 部署资源路径 + api-version，invoke/invokeStream/verify 使用 api-key header 认证
- **CustomResponsesInvoker**：自定义平台 Responses API 调用器，继承 OpenAIResponsesInvoker，无额外覆写
- **OpenAIResponsesInvoker.parseStreamEvent 新增事件**：response.reasoning_text.delta 解析 delta 写入 ChatChunk.reasoning；response.custom_tool_call.in_progress/done 解析 item_id/output_index/input 构建 ChatChunk.CustomToolCall 写入 customToolCall

- **MemoryQueryProvider**：AI 记忆查询 Provider 接口，由外部模块提供实现。定义两个方法：getMemories(sessionId, searchType, memoryType, startTime, endTime, query) 按搜索类型与过滤条件查询记忆返回 List<MemoryResult>；getMessageSeqsByRole(sessionId, List<SeqRange> ranges) 按序号区间列表批量查询消息序号并按角色分类返回 MessageSeqByRole（合并去重后的 user/tool/assistant 序号列表，原单区间签名已改为批量 List<SeqRange>，消除 N+1 查询）。
- **MemoryResult**：AI 记忆查询结果数据类，record 封装 content/startSeq/endSeq/memoryType。
- **MessageSeqByRole**：消息序号按角色分类的结果数据类，record 封装 userSeqList/toolSeqList/assistantSeqList 三个序号列表。
- **SeqRange**：消息序号区间数据类，record 封装 startSeq/endSeq，用于批量消息序号查询。
- **MemoryQueryTool**（default_tool_memory_search）：继承 CustomToolInvoker（非 SystemTool），无 @Component 注解，构造函数传参（ToolConfigDTO + MemoryQueryProvider），提供静态 createToolConfig() 返回 ToolConfigDTO（id=null, toolType=CUSTOM）。参数 query(必填)/searchType(必填 enum VECTOR/FULLTEXT/HYBRID)/memoryType(可选 GROUP=分类 DAILY=按天)/startTime、endTime(可选毫秒时间戳)。execute 通过 ctx.getSessionId() 获取会话 ID，调用 provider.getMemories 获取记忆结果，buildOutput 收集所有结果序号区间构造 List<SeqRange> 后在循环外仅一次调用 provider.getMessageSeqsByRole(sessionId, ranges) 批量查询，再合并去重得到 user/tool/assistant 序号列表，返回 {results:[{content,startSeq,endSeq,memoryType}], userSeqList, toolSeqList, assistantSeqList} JSON。参数缺失/无效返回 {"status":"error","errMsg":...}，无匹配结果返回空列表（非错误）。错误 JSON 序列化使用 JsonMapper。
- **HistoryMessageQueryProvider**：历史消息查询 Provider 接口，由外部模块提供实现。定义方法 getMessagesBySeqs(sessionId, seqs, includeReasoning)：按消息序号列表查询会话历史消息，includeReasoning 控制是否返回推理内容，返回 List<HistoryMessageItem>。
- **HistoryMessageItem**：历史消息项数据类，record 封装 role/content/reasoning/toolCalls(工具调用列表)/toolResult(工具结果)。嵌套 HistoryToolCallItem(toolCallId/toolCallName/toolCallArguments) 与 HistoryToolResultItem(toolCallId/toolCallName，结果内容即消息 content)。
- **HistoryQueryTool**（default_tool_history_query）：继承 CustomToolInvoker（非 SystemTool），无 @Component 注解，构造函数传参（ToolConfigDTO + HistoryMessageQueryProvider），提供静态 createToolConfig() 返回 ToolConfigDTO（id=null, toolType=CUSTOM）。参数 seqs(必填 integer 数组)/includeReasoning(可选 boolean 默认 false)。execute 通过 ctx.getSessionId() 获取会话 ID，调用 provider.getMessagesBySeqs 获取历史消息，返回 {"messages":[{role,content,reasoning,toolCalls,toolResult}]} JSON（reasoning 仅 includeReasoning=true 时返回）。seqs 缺失/空/含非整数时返回 {"status":"error","errMsg":...}，无匹配消息返回空列表（非错误）。错误 JSON 序列化使用 JsonMapper。
- **SubSessionCallbackSystemTool 会话复用**：execute() 在创建子会话前先调用 ctx.getChildSessions() 按 sessionName 精确匹配（sessionName.equals(child.sessionName())）查找已存在子会话；命中时复用已有 sessionId 直接 callback.execute(ctx, sessionId, userMessage, thinking) 发送消息（忽略 description/toolNames/skillNames 配置，不重新配置会话），未命中时走原逻辑 createChildSession 新建后回调；sessionName 参数描述已体现复用概念
- **SubSessionCallbackTool**（callback_sub_session）：继承 CustomToolInvoker 的自定义工具调用器（非 SystemTool），无 @Component 注解，构造函数传参（ToolConfigDTO + SubSessionCallback），提供静态 createToolConfig() 返回 ToolConfigDTO（id=null, toolType=CUSTOM, name=callback_sub_session），描述与参数 schema 由 ToolConfigDTO 承载（与改造前 getDescription/getParameterSchema 内容一致，id 由注册方 DefaultToolDataProvider 设置为工具名）。execute() 解析 JSON 参数后先按 sessionName 精确匹配 ctx.getChildSessions() 复用已存在子会话（命中时忽略 description/toolNames/skillNames 直接回调发送消息），未命中时按 toolNames/skillNames 从上下文匹配获取 toolIds/skillIds 调用 createChildSession 创建子会话，通过注入的 callback.execute(ctx, childSessionId, userMessage, thinking) 执行用户消息并返回消息内容；支持 thinking 参数（默认 null）透传给回调；异常时返回 {"status":"error","errMsg":...} JSON。
- **SendResultToParentTool**（send_result_to_parent）：继承 CustomToolInvoker 的自定义工具调用器（非 SystemTool），无 @Component 注解，构造函数传参（ToolConfigDTO），提供静态 createToolConfig() 返回 ToolConfigDTO（id=null, toolType=CUSTOM, name=send_result_to_parent），描述与参数 schema 由 ToolConfigDTO 承载（parameterSchema 含单个必填参数 result（string，子会话执行结果））。execute() 解析 arguments 获取 result 参数（缺失/空白返回错误 JSON），从 ctx.getParentSessionId() 获取父会话 ID；无父会话 ID（当前非子会话）返回 {"status":"error","errMsg":"当前会话不是子会话，无法发送结果到父会话"}，有父会话 ID 时调用 ctx.sendUserMessage(parentSessionId, result, ctx.getModelId(), null) 发送执行结果到父会话并返回 {"status":"success","message":"已发送执行结果到父会话"}；错误 JSON 使用 JsonMapper 序列化（异常捕获兜底返回错误 JSON）。
- **SendResultToParentTool 发送方式**（send_result_to_parent）：execute() 通过 ctx.sendParentMessage(result) 向父会话发送执行结果（agent-base AgentExecutionContext.sendParentMessage 从 parentSessionId 获取父会话，null/空白静默忽略，callback null 静默忽略，动态获取 conversationId 后发送）；execute() 保留 ctx.getParentSessionId() 前置检查，无父会话 ID 返回 {"status":"error","errMsg":"当前会话不是子会话，无法发送结果到父会话"}；result 必填校验（缺失/空白返回错误 JSON "缺少 result 参数"）与成功返回 {"status":"success","message":"已发送执行结果到父会话"} 不变。
## 工厂与组装

- **DefaultModelInvokerFactory**：根据平台类型（OPENAI/ANTHROPIC/AZURE/OLLAMA/KIMI/VOLCENGINE/DEEPSEEK/CUSTOM/SILICONFLOW）创建对应 Invoker；createChatCompletionsInvoker 新增 SILICONFLOW case 分支创建 SiliconFlowInvoker；supportsResponses 中不包含 SILICONFLOW（SILICONFLOW 仅支持 chat-completions，不支持 Responses API，与前端 RESPONSES_SUPPORTED 保持一致）
## 模型调用器（ModelInvoker 实现）

- **OpenAIInvoker**：新增 embed(EmbeddingRequest) 方法，调用 baseUrl+/embeddings 接口，请求体构建 model + input/inputList（inputList 优先；input 与 inputList 均为 null 时抛出 IllegalArgumentException），解析响应返回 EmbeddingResponse（embeddings 列表与 usage），错误处理遵循 invoke() 模式；新增 buildEmbeddingsUrl/buildEmbeddingRequestBody/parseEmbeddingResponse 受保护方法
## 知识库查询工具

### 知识库查询工具（Knowledge Base Query Tools）
- **SearchType**：知识库文本块搜索类型枚举（VECTOR/FULLTEXT/HYBRID），用于 searchChunks 的搜索类型参数。
- **KnowledgeBaseQueryProvider**：知识库查询 Provider 接口，定义四类查询能力：getKnowledgeBaseInfo(sessionId) 获取会话关联知识库信息列表（返回 List\<KnowledgeBaseInfo\>，会话或知识库不存在时返回空列表）、searchFiles(kbId, fileName, limit) 按文件名搜索文件（返回 List\<FileInfo\>，仅返回已发布到 ES 的文件）、searchChunks(kbId, fileId, searchType, query, topK) 搜索文本块（返回 List\<TextChunkWithFile\>，searchType 为 SearchType 枚举，不含上下文扩展，返回纯匹配结果；参数 fileId 作为 ES 查询过滤条件在查询层面生效（非内存过滤），非 null 时仅返回该文件下的文本块）、getFileChunks(kbId, fileId, startLine, endLine) 获取行号范围文本块（返回 TextChunkWithFile）。由外部模块提供实现。
- **KnowledgeBaseInfo/FileInfo/TextChunkWithFile**：知识库查询数据类。KnowledgeBaseInfo 含 kbId/kbName/kbDescription；FileInfo 含 fileId/fileName/fileDescription/maxLineCount；TextChunkWithFile 含 knowledgeBaseId/fileId/fileName/chunkList，嵌套 TextChunk(lineNumber/text)。
- 四个知识库工具类均继承 CustomToolInvoker（非 SystemTool），无 @Component 注解，构造函数传参（ToolConfigDTO + KnowledgeBaseQueryProvider），提供静态 createToolConfig() 返回 ToolConfigDTO（id=null, toolType=CUSTOM），工具名/描述/参数 schema 定义在 ToolConfigDTO 中：
  - **KnowledgeBaseInfoTool**（default_tool_rag_info）：无参数，execute 通过 ctx.getSessionId() 获取会话 ID 调用 getKnowledgeBaseInfo 返回知识库信息列表 JSON（null 时序列化为 []）。
  - **KnowledgeFileInfoTool**（default_tool_rag_file_info）：参数 knowledgeBaseId(必填)/fileId(可选)/fileName/searchLimit(默认10)，调用 searchFiles 返回文件列表 JSON，传 fileId 时按 fileId 过滤。
  - **KnowledgeSearchTool**（default_tool_rag_search）：参数 knowledgeBaseId(必填)/fileId/searchType(必填，enum VECTOR/FULLTEXT/HYBRID)/query(必填)/searchLimit(默认10)/contextLines(默认3)。调用 searchChunks 获取纯匹配结果后，用 contextLines 扩大每个 chunk 的行范围（line-contextLines ~ line+contextLines，下限 1），将同文件重叠/相邻行范围经 mergeRanges 合并后逐个调用 provider.getFileChunks() 获取上下文文本块；随后按 (knowledgeBaseId, fileId) 分组到 LinkedHashMap，组内按 lineNumber 去重（LinkedHashMap putIfAbsent 保持插入顺序）后按行号升序合并连续行号块，返回 List\<{knowledgeBaseId, fileId, chunks}\> 结构。
  - **KnowledgeFileChunkTool**（default_tool_rag_file_chunk）：参数 knowledgeBaseId(必填)/fileId(必填)/startLine(默认0)/endLine(默认文件最大行数)。endLine 未传时通过 searchFiles 解析文件 maxLineCount，找不到则用 Integer.MAX_VALUE，调用 getFileChunks 后将 chunkList 按行号升序合并为纯文本字符串返回（块之间以换行分隔，null/空列表返回空字符串）。
- 以上工具错误 JSON 序列化使用 JsonMapper。
- **ID 类型统一为 String**：KnowledgeBaseInfo.kbId、FileInfo.fileId、TextChunkWithFile.knowledgeBaseId/fileId 由 Long 改为 String；KnowledgeBaseQueryProvider 的 searchFiles(kbId,...)/searchChunks(kbId, fileId,...)/getFileChunks(kbId, fileId,...) 三个方法 Long 参数改为 String；三个工具类（KnowledgeFileInfoTool/KnowledgeSearchTool/KnowledgeFileChunkTool）execute 解析参数改为 JsonNode.asText()（兼容数字与字符串输入），参数 schema 中 knowledgeBaseId/fileId 类型由 integer 改为 string。
## 子会话结果兜底回传

- **SubSessionResultProvider**：子会话结果回传 Provider 接口（integration 定义契约，由 platform-app 提供实现）。定义 shouldSendResultToParent(String sessionId) 判断指定子会话是否需要向父会话兜底回传执行结果。
- **SubSessionResultFallbackHook**：子会话结果兜底回传 HOOK，implements SystemHook<ChatChunkHookData, EmptyHookResult>（适配 agent-base HookInvoker<D, R> 泛型化改造），由 HookManager 在 triggerHooks(phase) 中按 phase 分发。getPhase 返回 AFTER_MESSAGE_RECEIVE，getIndex=100 确保在 MessageSavePostHook（默认 0）之后执行，此时上下文历史已包含本次最终 assistant 消息。execute(AgentExecutionContext ctx, ChatChunkHookData data) 返回 EmptyHookResult（跳过分支返回 null，正常回传完成返回 EmptyHookResult.INSTANCE），内部经 data.getChatChunk() 读取 ChatChunk。由于 AFTER_MESSAGE_RECEIVE 仅在 ChatService 流式响应 doOnComplete 结束时触发一次（completeChunk 仅含 hasToolCalls 字段、无 finishReason），execute() 不再依赖 FinishReason.STOP 判定，仅在「子会话（非主会话）且最终回复无待执行工具调用（hasToolCalls 非 true）」时继续，经 SubSessionResultProvider 判定需要发送后，取 ctx.getHistory() 最后一条 assistant 消息 content，通过 ctx.sendParentMessage(content) 复用既有 sendParentMessage 通道回传父会话（保存 user 消息到父会话并推送 SEND_USER_MESSAGE）；非子会话、非 WEBSOCKET 子会话（由 Provider 实现内判定）、有工具调用、Provider 判定无需发送等情况一律静默跳过，不影响原有流程。
## 技能提示词与已加载技能工具注入

## 技能提示词与已加载技能工具注入

- **AvailableSkillsSystemHook**：可用技能（SKILL）列表与已加载技能提示词 HOOK，implements SystemHook<SystemPromptHookData, SystemPromptHookResult>（无 Spring 依赖，可直接 new），getPhase 返回 AFTER_PRE_SYSTEM_PROMPT_BUILD。execute 先判断 data.getToolDefinitions() 是否包含 LoadSkillsSystemTool.FULL_TOOL_NAME（_sys_load_skills）同名工具定义（缺失返回 null），再生成两段提示词并拼接为一个 SystemPromptHookResult 返回：
  - 可用技能列表段（在前）：遍历 ctx.getSkills()（null 安全），主会话跳过 sessionAuth==CHILD 的技能，含描述输出 "- name: description"、不含/空白描述输出 "- name"，提示文本逐字复刻原 ChatService.buildContextSystemInfo 逻辑（引导使用 load_skills 系统工具加载，禁止直接以技能名称作为工具调用）
  - 已加载技能段（在后）：经 LoadedSkillsHelper 从会话变量 _sys_loading_SKILLS 读取并过滤已加载技能，生成"以下技能已加载，请按照其提示词指导执行任务：\n\n" + "## 技能名\n" + 提示词（非空时追加）
  - 拼接规则：可用技能列表在前、已加载技能在后（两段间以 "\n\n" 分隔）；无可用技能但有已加载技能时仅返回已加载段；两者皆无返回 null
- **LoadedSkillsToolHook**：已加载技能工具注入 HOOK，implements SystemHook<ToolDefinitionsHookData, ToolDefinitionsHookResult>，getPhase 返回 BEFORE_TOOL_DEFINITIONS_BUILD（ChatService 检测到该阶段存在 HOOK 时以返回结果接管工具列表）。execute 从会话变量（LoadSkillsSystemTool.SESSION_KEY）读取已加载技能名，经 LoadedSkillsHelper 过滤出已加载技能（主会话跳过 CHILD），收集这些技能的 skillTools（null 安全）作为追加工具；返回 ToolDefinitionsHookResult 其 tools = 基础工具（data.getTools()）+ 追加的已加载技能工具（按名称/ID 去重，name 非空优先，保持顺序）；无可追加工具时透传 data.getTools() 本身，data==null 返回 null 工具列表
- **LoadedSkillsHelper**：已加载技能解析辅助类（包级私有），parseLoadedSkillNames(ctx) 从会话变量 _sys_loading_SKILLS 读取 JSON 解析技能名列表（缺失/空白/JSON 解析失败降级返回空列表，经 JsonMapper.MAPPER 与 TypeReference<List<String>>），collectLoadedSkills(ctx) 从 ctx.getSkills() 按名称过滤出已加载技能配置（主会话跳过 sessionAuth==CHILD），复刻原 ChatService.parseLoadedSkills 逻辑
