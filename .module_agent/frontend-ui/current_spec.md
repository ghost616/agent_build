前端 UI：模型管理、工具管理、HOOK 管理、智能体配置、对话交互界面
## 模型管理界面

- baseUrl 显示由 isCustom 决定（仅"自定义"CUSTOM 平台显示 Base URL 输入框），其余平台隐藏并自动填充 defaultBaseUrl；disabled 由 needsManualInput 决定（编辑模式与添加模式行为一致）：showBaseUrl = isCustom，disabled = !needsManualInput

- 模型测试页面按 modelType 分支：LLM 显示对话测试界面（chatStream 流式对话、思考模式、推理展示），EMBEDDINGS 显示嵌入测试界面（输入限制 1000 字符超出报错、embed API 调用、结果区展示前 100 维向量超出显示 '...'）；页面头部显示 modelType 标签
- types/model.ts 提供 EmbeddingRequest/EmbeddingResponse 类型；services/model.ts 提供 embed(id, request) 调用 POST /api/models/{id}/embed

- EmbeddingResponse 与后端 DTO 一致：{ embeddings: EmbeddingItem[]; usage?: UsageInfo }，EmbeddingItem 为 { index: number; embedding: number[] }；EmbeddingTest 组件取 result.embeddings[0].embedding 展示，embeddings 为空或缺失时显示占位文本"未返回嵌入向量"
## 工具管理界面

- 工具配置管理页面 `/tools`，支持工具列表展示、搜索筛选、新增/编辑/删除/启用禁用
- Table 列：名称、工具类型(JAVA/TYPESCRIPT/PYTHON Tag)、描述(ellipsis)、状态(Tag)、创建时间、操作(编辑/删除/Switch)
- 筛选栏：名称搜索(Input.Search)、工具类型(Select)、状态(Select)
- 新增/编辑 Modal：name(必填)、toolType(必填Select)、description(TextArea)、parameterSchema(TextArea JSON)、returnSchema(TextArea JSON)、implPath
- 删除使用 Popconfirm 确认
- 启用/禁用使用 Switch 切换
- API 服务封装：listTools、getTool、createTool、updateTool、deleteTool、updateToolStatus
- 工具名称表单项新增 pattern 校验规则：仅允许小写字母、数字和下划线（/^[a-z0-9_]+$/）
- 工具列表表格添加 pagination={false}，移除分页器，全量展示
- 新建 SchemaEditor 组件：结构化 JSON Schema 编辑器，解析 properties 为 PropertyDef 列表，每行编辑属性名/类型/描述/必填，构建回写 onChange
- ToolList.tsx parameterSchema Form.Item 替换 TextArea 为 SchemaEditor 组件
- 安装 @codemirror/view @codemirror/state @codemirror/lang-json @codemirror/basic-setup，提供 JSON 代码编辑器支持
- 新建 JsonEditor 组件（src/components/JsonEditor.tsx）：封装 CodeMirror EditorView + json() 语法高亮，value/onChange 兼容 Ant Design Form.Item，深色主题自适应高度
- ToolList.tsx：parameterSchema 和 returnSchema 的 TextArea/SchemaEditor 替换为 JsonEditor
- 删除 SchemaEditor.tsx（已被 JsonEditor 替代）
- 修复 CodeMirror 多实例冲突：卸载 @codemirror/basic-setup（v0.x 自带旧版 view/state 副本），安装 @codemirror/commands @codemirror/language
- JsonEditor.tsx 移除 basicSetup，改用独立扩展组装：lineNumbers/highlightActiveLineGutter/highlightSpecialChars/drawSelection @codemirror/view；defaultKeymap/history/historyKeymap @codemirror/commands；indentOnInput/bracketMatching/closeBrackets @codemirror/language
- 工具类型新增 MCP_HTTP：TOOL_TYPE_LABELS 添加 'MCP HTTP' 标签，TOOL_TYPE_COLORS 添加 'purple' 颜色
- MCP_HTTP 表单专项：通过 Form.useWatch('toolType') 监听，仅 MCP_HTTP 时显示 Authorization 输入框
- MCP_HTTP 类型隐藏 parameterSchema / returnSchema 表单项
- 提交逻辑：toolType === MCP_HTTP 时，取 authorization 值组装 authConfig: JSON.stringify({type: "bearer", token: authorization})，并清空 parameterSchema/returnSchema
- 编辑回填：编辑 MCP_HTTP 工具时从 authConfig JSON 解析 token 回填 authorization 表单字段
## 智能体管理界面

- 智能体配置管理页面 `/agents`，支持智能体列表展示、搜索筛选、新增/编辑/删除/启用禁用
- Table 列：名称、描述(ellipsis)、关联模型名称、状态(Tag)、创建时间、操作(编辑/删除/Switch)
- 筛选栏：名称搜索(Input.Search)、状态筛选(Select)
- 新增/编辑 Modal：name(必填)、description(TextArea)、systemPrompt(TextArea)、modelId(Select 从模型列表获取)、toolIds(Select multiple 从工具列表获取)
- 删除使用 Popconfirm 确认
- 启用/禁用使用 Switch 切换
- API 服务封装：listAgents、getAgent、createAgent、updateAgent、deleteAgent、updateAgentStatus
- 模型列表和工具列表数据通过 Promise.all 并行加载用于表单下拉选择
- 路由 /agents 注册，侧边栏"智能体管理"菜单项（RobotOutlined 图标）
- 智能体列表表格添加 pagination={false}，移除分页器，全量展示
- AgentConfig 与 AgentFormData 类型新增 recentMessageCount?: number 字段（最近消息数量）
- 新增/编辑 Modal 新增 Form.Item name="recentMessageCount" label="保留对话轮数"（extra 辅助说明"保留最近 N 轮对话对 AI 可见，更早对话折叠"）：InputNumber，initialValue=10、min=1、max=100、宽度 100%
- 编辑回填时同步设置 recentMessageCount 字段
- Table columns 新增"最近消息"列（dataIndex=recentMessageCount，width 100），值为空时显示 '-'
- 新增/编辑 Modal 新增 skills 多选：通过 Promise.all 并行加载模型/工具/技能列表，新增 skillList state 存储技能数据，fetchModelsAndTools 改名为 fetchRefData 同步加载三种引用数据，表单中添加 skillIds (Select multiple) 字段实现技能多选，编辑回填时同步设置 skillIds
- AgentConfig/AgentFormData 中 toolIds/skillIds 改为 tools/skills（数组，每项含 id + sessionAuth 字段）
- SessionAuthType：ALL（所有会话）/ PARENT（父会话）/ CHILD（子会话）
- 表单工具/技能选择使用 SessionAuthSelect 组件：多选 + 每项可配置 sessionAuth 下拉（默认 ALL）
- 表格列中 tools/skills 显示为 Tag 标签，颜色区分 sessionAuth 类型（blue/green/orange）
- 智能体绑定知识库：AgentConfig/AgentFormData 新增 knowledgeBaseIds?: string[] 字段，新增 KnowledgeBaseItem 类型（{ knowledgeBaseId: string; name: string }）；fetchRefData 并行加载知识库列表（listKnowledgeBases({})）构建 knowledgeBaseList 与 knowledgeBaseMap；表单新增"绑定知识库"多选 Select（mode=multiple，从知识库列表获取）；表格新增"绑定知识库"列（knowledgeBaseMap 映射 ID→名称渲染 Tag 列表，空显示 '-'）；createAgent/updateAgent 复用透传 knowledgeBaseIds
- 智能体向量模型：AgentConfig 与 AgentFormData 新增 vectorModelId?: string 字段；AgentList fetchRefData 并行加载 listModels({modelType:'EMBEDDINGS'}) 构建 vectorModelList，编辑回填 vectorModelId，表单新增"向量模型"下拉（仅 memoryEnabled=true 时显示，hidden={!memoryEnabled}），提交时 memoryEnabled 为 false 则 vectorModelId 置 undefined
- 记忆功能表单联动：memoryEnabled=false 时"保留消息组数量"（memoryGroupCount，label="保留消息组数量"，extra 辅助说明"保留最近 N 个消息组对 AI 可见，更早的消息组归档为记忆"）与"向量模型"（vectorModelId）表单项均隐藏（hidden={!memoryEnabled}），memoryEnabled=true 时显示；提交时 memoryEnabled 为 false 则 vectorModelId 置 undefined
- AgentConfig 与 AgentFormData 类型新增 subSessionOpenMode?: SubSessionOpenMode 字段，新增 SubSessionOpenMode 类型（'WEBSOCKET' | 'TOOL_CALL'，对齐后端枚举序列化）
- 列表 columns 新增「子会话打开方式」列（dataIndex=subSessionOpenMode，width 140）：Tag 展示，SUB_SESSION_OPEN_MODE_LABELS（WEBSOCKET→'WebSocket推送'、TOOL_CALL→'前台工具调用'）与 SUB_SESSION_OPEN_MODE_COLORS（cyan/geekblue），值缺失时默认按 TOOL_CALL 显示'前台工具调用'
- 新增/编辑 Modal 新增 Form.Item name="subSessionOpenMode" label="子会话打开方式"：Select 下拉（SUB_SESSION_OPEN_MODE_OPTIONS，选项 WebSocket推送/前台工具调用），initialValue="TOOL_CALL"
- 编辑回填 setFieldsValue 同步设置 subSessionOpenMode，缺失时默认 'TOOL_CALL'
## 会话管理界面

- AgentChat 历史消息加载：loadHistory/ChildSessionView 从 SessionMessage.webSearchCall 数组映射渲染已持久化的多个搜索结果引用
- AgentChat 路径式导航标签栏（替换原扁平子会话标签）：Tabs 项由 activePath 层级链生成 [主会话][子会话A][孙会话B]...（首位=主会话，末位=当前激活视图，key=会话 id；子会话标签名取自其父会话子列表缓存的 title，无缓存或 title 为空时回退 id）；激活层级右侧经 tabBarExtraContent 显示下一级计数标签（子会话数量+上下箭头，默认下箭头不展开），点击展开 Dropdown 下拉面板展示该层级子会话列表（复用 childListCache 缓存数据不重复请求），选中后标签变为子会话名称、箭头隐藏、内容区切换展示该子会话（沿用 ChildSessionView 只读视图，无输入框/模型选择/思考模式/发送/回滚/停止）；点击主会话截断路径恢复其下一级计数显示；无子会话时不显示计数标签；不设层级深度限制
- AgentChat 层级数据按需获取：ensureChildList(parentId) 获取计数时即调用 listChildSessions(parentId) 并写入 childListCache 缓存（childListPromisesRef 按父会话 ID 去重避免并发重复请求，force=true 强制刷新），激活层级变化 effect 自动获取激活级子列表，支持任意层级；已移除扁平子会话列表 Table、childSessionColumns、「查看会话」「返回子会话列表」「刷新」按钮、viewingChildId 二级状态及 childSessions 单层状态
- 对话 conversationId 支持：ChatRequest 类型（src/types/session.ts）新增 conversationId?: string 可选字段（对应后端 ChatRequest DTO）；services/session.ts 新增 fetchConversationId() 调用 GET /conversation-id 返回 conversationId，agentChatStream 参数类型改为 ChatRequest；AgentChat handleSend 每次用户发送前先 await fetchConversationId() 获取新 conversationId 传入 agentChatStream，获取失败时 message.error 并中止发送；工具续接 continueChatStream（[tool_continue]）请求不传 conversationId
- 会话历史功能：SessionMessage 接口新增 conversationId?: string 可选字段；services/session.ts 新增 getConversationMessages(conversationId) 调用 GET /api/conversations/{conversationId}/messages 返回对话消息列表；新建 ConversationHistory.tsx（路由 /conversations 展示主会话列表复用 listSessions，点击行跳转 /conversations/:sessionId；该页基于路由参数 sessionId 用 getSessionMessages 拉取并按 role==='user' 过滤展示用户消息列表，每条显示内容/时间，有 conversationId 的显示「查看详情」按钮跳转 /conversations/:conversationId/detail）；新建 ConversationDetail.tsx（路由 /conversations/:conversationId/detail，调用 getConversationMessages 展示该 conversationId 下所有消息，列：角色 Tag/内容/时间）；App.tsx 新增「会话历史」菜单项（HistoryOutlined）与 /conversations、/conversations/:sessionId、/conversations/:conversationId/detail 路由
- 对话详情页增强（ConversationDetail.tsx）：内容列对 assistant 且有 toolCalls 的消息显示「查看工具 (N)」按钮，对 tool 角色消息显示「查看结果」按钮，点击弹出 Modal 以 <pre> 展示 JSON.stringify(toolCalls/toolResult, null, 2)；新增「来源会话」列展示 sessionId（Tooltip 悬浮完整 ID，可见截短为前8后4…）；Table 使用 rowClassName 按 record.sessionId===sessionId 区分主/子会话背景色（conversation-main-row 暖黄 / conversation-child-row 浅蓝，样式定义在 index.css）
- 对话详情页内容列重构（ConversationDetail.tsx）：内容列改为可点击纵向三行展示（单行省略，LINE_ROW_STYLE nowrap/ellipsis）——💭 reasoning（有则）、📝 content（有则）、操作按钮（assistant 有 toolCalls 显示「🔧 工具调用 (N)」，tool 角色显示「📋 工具结果」）；点击内容区域（onClick setDetailVisible(true)）打开「对话详情」Modal 展示完整对话流（renderMessageFlow）：user 消息显示 content，assistant 消息显示 💭reasoning/📝content/🔧工具调用列表（renderToolCallFlow 展示 each 工具名称+参数 JSON，并按 toolCallId 通过 findToolResult 遍历后续 tool 消息配对展示该工具的 📋结果 JSON），tool 消息显示 toolName 与 result JSON；「来源会话」列与 rowClassName 主/子会话背景色逻辑保留
- App.tsx MENU_ITEMS：「会话历史」（HistoryOutlined）菜单位置从评估管理之前调整到评估管理之后，路由不变
- AgentChat 子会话对话统一执行器（runChildSessionFlow，WS 消息分发与主会话工具回调两种触发方式共用）：写入 childStreams[childId] 初始化状态 → agentChatStream 流式回复（推理+内容）→ onDone(hasToolCalls) 为 true 时执行工具循环（executeTools → pollSubToolStatus → continueChatStream）直至无工具调用 → completeSubSession 收尾；入口差异仅参数：WS 触发 streamContent 传 SEND_USER_MESSAGE_MARKER（switchTab=false），工具触发传 data.userMessage + thinking（switchTab=true）；已移除子会话对话 Modal（renderSubSessionModal 及 subSessionModalVisible/subMessages/subCurrentResponse/subCurrentReasoning/subLoading/subToolExecuting/subContainerRef 等弹窗状态），子会话过程消息与错误直接在对应子会话标签内渲染（ChildStreamState 新增 toolExecuting/error 字段，ChildSessionView 展示工具执行中提示与错误信息）
- AgentChat 子会话标签缺失处理与切换：执行前检查直接父会话子列表（childListCacheRef[parentId||sessionId]）是否含对应子会话，缺失时 ensureChildList(parentId, true) 强制刷新补出标签（刷新失败忽略继续执行），两种触发均适用；工具触发开始自动切换路径标签 setActivePath([sessionId, childId])、结束（含失败）自动切回主会话 setActivePath([sessionId])，WS 触发不自动切回（路径标签由 WS 按父会话链展开）；子会话流程失败时标签内保留已产生的过程消息并通过 error 字段提示错误
- AgentChat 子会话标签自动滚动：ChildSessionView 滚动容器（overflowY:auto div）添加 childContainerRef；useEffect 监听 mergedMessages 与 stream 的 currentResponse/currentReasoning/toolExecuting/loading 变化，内容变化时 scrollTop = scrollHeight 自动滚动到底部（与主会话 containerRef 自动滚动行为一致）
- AgentChat 计数标签路径项化：计数标签（Dropdown+数量+上下箭头）不再使用 tabBarExtraContent={{ right: countTabNode }}，改为 Tabs items 中的路径项——sessionTabItems 由 activePath.flatMap 构建，每个层级标签后紧跟其下一级计数标签项（key 为 `${sid}-count`，数据取 childListCache[sid]，无子会话时不插入），点击计数标签仅展开下拉不切换内容区（handleTabChange 对 `-count` key 直接 return），保持路径式导航视觉 [主会话] [▾ 3] [子会话A] [▾ 2] [孙会话B]；.agent-chat-count-tab 样式与 Tabs 标签视觉一致（贴紧、同高、无多余边距：去掉固定 height/边框/背景，margin 0，并用 .agent-chat-tabs .ant-tabs-tab:has(.agent-chat-count-tab) 覆盖 antd 默认 tab 间距为 4px）
## 技能管理界面

- 技能配置管理页面 `/skills`，支持技能列表展示、搜索筛选、新增/编辑/删除/启用禁用
- Table 列：名称、描述(ellipsis)、状态(Tag green/red)、创建时间、操作(编辑/删除Popconfirm/Switch)
- 筛选栏：名称搜索(Input.Search)、状态筛选(Select)
- 新增/编辑 Modal：name(必填)、description、prompt(必填 TextArea rows=6)、toolIds(Select multiple 从工具列表获取)
- 删除使用 Popconfirm 确认
- 启用/禁用使用 Switch 切换
- API 服务封装：listSkills、getSkill、createSkill、updateSkill、deleteSkill、updateSkillStatus
- 工具列表数据用于表单 toolIds 下拉选择
- 路由 /skills 已注册，侧边栏"技能管理"菜单项（ThunderboltOutlined 图标）
- Table pagination={false} 全量展示
## 评估管理界面

- 智能体评估配置管理页面 `/evaluations`，支持评估列表展示、新增/编辑/删除/启用禁用
- Table 列：名称、描述、智能体名称、状态(Tag green/red)、创建时间、操作(编辑/进行评估/禁用/删除Popconfirm)
- Table pagination={false} 全量展示
- 新增/编辑 Modal：name(Input 必填)、description(TextArea)、agentId(Select 从智能体列表获取)
- 删除使用 Popconfirm 确认
- 禁用使用 Switch 切换
- 「进行评估」按钮跳转 `/evaluations/{id}/items`（进入该智能体评估的评估项列表）
- 评估项列表页面 `/evaluations/:agentEvalId/items`：从路由参数获取 agentEvalId
- Table 列：名称、描述、智能体名称、执行次数、模型ID、创建时间、操作(编辑/进行评估/查看结果/删除Popconfirm)
- 新增/编辑 Modal：name、description、agentEvalId(只读显示)、modelId(Select)、executionCount(InputNumber)
- 返回按钮导航到 `/evaluations`
- 评估结果历史列表页面 `/evaluations/items/:evaluationId/results`：展示指定评估的执行结果历史
- 页面标题显示评估名称，上方有「执行」按钮（暂无功能，点击提示"功能开发中"）
- 返回按钮根据评估的 agentEvalId 导航回 `/evaluations/:agentEvalId/items`
- Table 列：ID、会话ID、Token消耗、结果摘要、创建时间、操作(修改按钮暂无功能)
- API 服务封装：
  - agentEvaluation.ts: getAgentEvaluationList、getAgentEvaluation、createAgentEvaluation、updateAgentEvaluation、deleteAgentEvaluation、updateAgentEvaluationStatus
  - evaluation.ts: getEvaluationList(支持 agentEvalId 筛选)、getEvaluation、createEvaluation、updateEvaluation、deleteEvaluation、getEvaluationResults
- EvaluationList 操作列新增"清空结果"按钮（danger 类型）：Modal.confirm 二次确认后调用 clearEvaluationResults(record.id) 清空该评估下所有评估结果，成功后 message.success 并刷新列表，失败 message.error；按钮始终可点击（后端对空列表无操作处理）
- useEvaluationExecute FOREGROUND 模式：sendForegroundMessage 在调用 agentChatStream 前先 await fetchConversationId() 获取 conversationId，获取失败时抛出错误中止；conversationId 传入 agentChatStream 请求参数（{ sessionId, content, conversationId }）
- useEvaluationExecute BACKGROUND 模式为 status→stream(cacheId)→remove(cacheId)→status 循环：executeEvaluation 返回 ExecutionStatusResponse（含 executionSessionId），拿到 executionSessionId 后先调 getEvaluationCacheStatus（GET /evaluations/cache/status?sessionId=xxx）获取 CacheStatusResponse（hasCache+cacheId），hasCache 为 true 且存在 cacheId 时用 getEvaluationStream 连接 GET /api/evaluations/cache/{cacheId}/stream（SSE，复用 processSSEStream），onDelta/onReasoning 增量追加到前台日志显示区域；流结束后调用 removeEvaluationCache（DELETE /evaluations/cache/{cacheId}）清理缓存，再回到 status 循环直到 hasCache=false；执行完成后刷新结果列表
- evaluation.ts 服务层：executeEvaluation 返回 ExecutionStatusResponse；getEvaluationStream 连接 GET /api/evaluations/cache/{cacheId}/stream（fetch + processSSEStream，返回 AbortController）；getEvaluationCacheStatus 调用 GET /evaluations/cache/status?sessionId=xxx 返回 CacheStatusResponse { hasCache, cacheId? }；removeEvaluationCache 调用 DELETE /evaluations/cache/{cacheId}（三个缓存接口均对齐后端 EvaluationExecutionController @RequestMapping(/api/evaluations) 前缀）；types/evaluation.ts 的 CacheStatusResponse 含 hasCache 与 cacheId 可选字段，ExecutionStatusResponse 含 evaluationId/executionSessionId 可选字段
- session.ts 导出 processSSEStream/StreamCallbacks/ChatChunk 供 evaluation.ts 复用
- EvaluationResultList 执行日志 Modal 标题由「前台执行」改为「执行日志」（BACKGROUND/FOREGROUND 共用）
- evaluation.ts 服务层移除 getExecutionStatus（GET /evaluations/{id}/execute/status）与 useEvaluationExecute 中 pollExecutionStatus/getExecutionStatus 轮询逻辑；executeEvaluation（POST /evaluations/{id}/execute）返回后直接进入 status→stream→remove 循环；types/evaluation.ts 的 ExecutionStatusResponse 移除 status/currentStep/totalSteps 字段，仅保留 evaluationId/executionSessionId
- useEvaluationExecute 新增 logLinesRef 统一持有日志数组（执行流程与 WebSocket 回调共用同一引用解决异步闭包过期，每次 execute 开始重置，activeChildIdsRef 对同一子会话并发/重发消息去重）；pushLog/appendStreamText 两个稳定辅助函数统一写日志
- handleSubSessionFlow 统一参数化（childId/userContent/streamContent/thinking/fromWs），工具触发与 WebSocket 触发共用：WS 触发 streamContent 传 SEND_USER_MESSAGE_MARKER（消息已由后端保存不重复保存 user 消息）、fromWs=true 跳过 completeSubSession（后端 subSessionDataMap 仅工具模式写入）、完整复用子会话工具循环（executeTools → pollSubToolStatus → continueChatStream）；工具触发由 runToolCycle 检测 needsSubSessionFlow 时 getSubSessionData 取数据后以统一参数调用
- 前台执行创建执行会话后以 executionSessionId 注册 SessionPageHandler（registerEvaluationHandler/unregisterEvaluationHandler，重复执行先注销旧 handler）：streamChildReply（主→子）追加 [子会话] {content} 日志并触发 handleSubSessionFlow（marker）执行子会话（[子会话AI]/[子会话思考] 流式日志行）；onSessionMessage（子→主）追加 [子会话回传] {content} 日志，执行会话执行中（loadingRef/toolExecutingRef）仅追加日志、空闲以 SEND_USER_MESSAGE_MARKER 调用 agentChatStream 触发续接（[AI] 行，onDone 有工具调用进入主会话工具循环）；执行结束（finally）/取消（handleCancelForeground）/组件卸载均注销 handler；前台执行未运行时无 handler、多页面共存互不干扰（按主会话 ID 索引）、重复执行重置日志与 handler
- 前台模式 Bug 修复：WebSocket 触发的子会话执行/续接受主流程等待——pendingAsyncRef（Set<Promise<void>>）跟踪 WS 触发的异步活动（streamChildReply 的 fromWs 子会话执行、onSessionMessage 的 marker 续接经 trackAsync 加入集合并在 finally 移除）；continueEvaluationChat 改造为返回 Promise<void>（await new Promise 包裹 agentChatStream，onDone/onError resolve，含 runToolCycle）；waitForPendingAsync 循环等待集合清空（while executingRef.current && size>0，Array.from 快照 + Promise.all，嵌套期间新增活动继续等待，取消时 executingRef=false 立即退出防死锁）；execute 主流程每条消息 sendForegroundMessage 完成后与所有消息处理完生成评估结果前均调用 waitForPendingAsync；execute 开始/finally 与 handleCancelForeground 取消时清空 pendingAsyncRef（重复执行重置、取消立即退出等待）；不改变 WS 消息处理逻辑本身（仅增加跟踪与等待）
## 知识库管理界面

- 知识库管理页面 `/knowledge`：列表展示、名称搜索、状态筛选、新增/编辑/删除/启用禁用，"管理文件"跳转 `/knowledge/:kbId/files`
- 知识文件列表页面 `/knowledge/:kbId/files`：按路由参数 kbId 加载，新增/编辑/删除/启用禁用、发布文件占位按钮；新建/编辑弹窗仅 fileName/fileDescription（不含 fileContent）
- 知识文件内容编辑页面 `/knowledge/:kbId/files/:fileId/edit`：并行调用 getKnowledgeFile 加载文件元信息（文件名）与 getKnowledgeFileContent 加载内容，左右分栏（左 TextArea 编辑 Markdown，右 react-markdown + remark-gfm 实时预览，左右使用相同高度设置），底部右下角（flex-end justify）提供"保存"（调用 updateKnowledgeFileContent）与"关闭"按钮，无返回按钮
- API 服务封装：知识库/知识文件 CRUD + 状态切换 + 内容专用接口（getKnowledgeFileContent/updateKnowledgeFileContent，路径 /knowledge-bases/{kbId}/files/{id}/content），路径基于 /knowledge-bases 与 /knowledge-bases/{kbId}/files
- updateKnowledgeFileContent 请求体为原始字符串并覆盖 Content-Type: text/plain（与后端 consumes 对齐，避免 415）；getKnowledgeFileContent 返回 ApiResponse.data 内容字符串
- KnowledgeFile 类型不含 fileContent 字段，KFFormData 仅 fileName/fileDescription，文件内容通过内容专用接口单独读写
- 知识文件发布功能：types/knowledge.ts 新增 PublishStatus 类型（UNPUBLISHED/PUBLISHING/PUBLISHED/PENDING_PUBLISH/PUBLISH_ERROR）；KnowledgeFile 新增 publishStatus 字段；KnowledgeBase 新增 vectorModelId/esIndex/rebuilding 字段；KBFormData 排除 rebuilding
- services/knowledge.ts 新增三个接口：publishKnowledgeFile(kbId, fileId) POST /knowledge-bases/{kbId}/files/{fileId}/publish、refreshKnowledgeFiles(kbId) PUT /knowledge-bases/{kbId}/files/refresh、rebuildKnowledgeBaseES(kbId) POST /knowledge-bases/{kbId}/rebuild-es
- KnowledgeBaseList：操作列新增「ES数据重构」按钮（调用 rebuildKnowledgeBaseES，执行中 loading，rebuilding=true 时禁用）；知识库 rebuilding=true 时「管理文件」按钮禁用置灰；编辑弹窗新增向量模型下拉（listModels({modelType:'EMBEDDINGS'}) 加载 EMBEDDINGS 模型）与 ES 索引输入框，提交时空字符串归一化为 undefined
- KnowledgeFileList：发布状态 Tag 列（UNPUBLISHED=default 灰、PUBLISHING=processing 蓝、PUBLISHED=success 绿、PENDING_PUBLISH=warning 橙、PUBLISH_ERROR=error 红）；「发布」按钮仅 UNPUBLISHED/PENDING_PUBLISH/PUBLISH_ERROR 可用，publishStatus=PUBLISHING 时显示「发布中」并 disabled，点击调用 publishKnowledgeFile；知识库 rebuilding=true 时禁用发布按钮；新增「刷新」按钮调用 refreshKnowledgeFiles 后重新拉取列表；页面加载 getKnowledgeBase 获取 rebuilding 状态
- KnowledgeFileEdit：文件 publishStatus=PUBLISHING 时禁用 TextArea 与保存按钮，文件名旁显示「发布中，暂不可编辑」Tag
## 日志查看界面

- 会话日志页 src/pages/logs/SessionLogList.tsx（路由 /logs）：调用 listLogSessions（GET /api/sessions/log-sessions）展示当前用户所有主会话（含评估会话），Table 列：会话名(title ellipsis)、是否评估(Tag：评估会话 gold/普通会话 default)、创建时间、操作（「查看日志」按钮跳转 /logs/{sessionId}）；分页关闭全量展示，scroll={{ x: 720, y: useTableScrollY(216) }}
- 运行日志页面 src/pages/logs/AgentLogList.tsx（路由 /logs/:sessionId）：从路由参数读取主会话 sessionId，日志查询携带 rootSessionId（按该主会话及其全部子会话过滤；路由参数缺失时不传）；筛选栏含会话名搜索(Input.Search)、日志类型 Select、日志等级 Select，变更后重置到第 1 页
- Table 列：会话名(sessionName)、会话类型(Tag：isChild=true 子会话 blue/否则主会话 default)、对话ID(conversationId)、日志类型(中文 Tag)、日志等级(彩色 Badge: INFO=blue/ERROR=red)、日志数据(超 60 字符截断+展开按钮)、会话变量/对话变量(截断+展开)、创建时间
- 分页支持每页条数切换(20/50/100)，默认按创建时间倒序（后端排序），showTotal 展示总条数
- 日志详情/变量详情 Modal：展示会话名/对话ID/日志类型/日志等级/创建时间元信息 + 完整日志数据(<pre> JSON 美化)
- types/log.ts 提供 AgentLog 类型（含 isChild 可选字段标识子会话日志）、AgentLogQueryParams 查询参数（含 rootSessionId 主会话过滤）、LogType/LogLevel 常量枚举（code 对齐后端枚举，label 中文）；LogType 不含 CALL_SOURCE，LogLevel 仅含 INFO/ERROR（不含 WARN）
- AgentLogList 中 LOG_LEVEL_LABELS/LOG_LEVEL_OPTIONS 由 LogLevel 枚举自动生成，LOG_LEVEL_COLORS 仅映射 INFO/ERROR（未知等级回退 default 颜色与原始值）
- services/log.ts 提供 listAgentLogs(params) 调用 GET /api/agent-logs（params 含 rootSessionId）返回 PageResult<AgentLog>
- services/session.ts 提供 listLogSessions() 调用 GET /api/sessions/log-sessions 返回 Session[]（当前用户所有主会话含评估会话，按创建时间倒序）；types/session.ts Session 接口含 isEvaluation 可选字段
- src/types/__tests__/log.test.ts 提供 LogType/LogLevel 枚举结构静态测试（不含 CALL_SOURCE/WARN、仅 INFO/ERROR、code/label 非空）
## 记忆回看界面

- 记忆修改会话列表页面 `/memory`（页面标题"记忆修改"）：调用 listSessions 与 listAgents 并行加载，过滤 memoryEnabled=true 智能体会话（agentMap[session.agentId]?.memoryEnabled === true），表格列：会话名称（title，ellipsis）、智能体名（agentMap 映射）、最近消息时间（getSessionMessages 取末条消息 createTime，为空显示 '-'）；每条操作提供「按日聚合」「按分类聚合」两个按钮，分别跳转 `/memory/:sessionId/DAILY` 与 `/memory/:sessionId/GROUP`；Table pagination={false}
- 记忆聚合列表页面 `/memory/:sessionId/:type`：根据 type 参数调用 getSessionMemory（GET /api/sessions/{id}/memory，params type/page/size），返回 PageResult<SessionMemoryDocument>；DAILY 类型显示「聚合日期」列（aggregationStartTime 毫秒时间戳格式化为日期），GROUP 类型显示「起始-结束」列（aggregationStartSeq - aggregationEndSeq）；「聚合文本」列 ellipsis 超长省略；支持分页（pageSizeOptions 10/20/50，showTotal，页码变化重置逻辑）；页面标题按 type 显示「按日聚合记忆」/「按分类聚合记忆」；返回按钮跳转 /memory
- types/memory.ts 提供 MemoryAggregationType（GROUP/DAILY）、SessionMemoryDocument（sessionId/aggregationType/aggregationStartSeq/aggregationEndSeq/aggregationStartTime/aggregationEndTime/aggregationText/vector，与后端 SessionMemoryDocument 对齐）、MemoryQueryParams 类型
- services/memory.ts 提供 getSessionMemory(sessionId, type, page, size) 调用 GET /api/sessions/{id}/memory 返回分页结果
- App.tsx 新增「记忆修改」菜单项（EyeOutlined）与 /memory、/memory/:sessionId/:type 路由
- 记忆聚合文档详情页 /memory/:sessionId/:type/:seqRange（MemoryDocumentDetail.tsx）：左侧聚合文本以 Markdown 渲染显示（ReactMarkdown+remarkGfm，agent-chat-markdown 样式与右侧消息气泡一致，容器 overflowY:auto 内滚，空时显示"暂无聚合文本"）——聚合文本由 useState 管理（初始值取路由 state.aggregationText），仅编辑弹窗保存成功（POST /memory/update）时通过 setAggregationText(editText) 回调更新详情页展示，重新生成成功不更新详情页（仅 setEditText 回填编辑弹窗右侧输入框）；右侧按序列号区间调用 getSessionMessagesRange 获取消息并以对话气泡样式展示——与 AgentChat 一致的 ROLE_CONFIG/BUBBLE_STYLES（角色标签+图标+气泡背景色：user=你 #569cd6/#1a3a5c、assistant=助手 #4ec9b0/#2a2a2a、tool=工具 #d7ba7d/#3a3a3a、system=系统 #9cdcfe/#2d3748），ReactMarkdown+remarkGfm 渲染（内联 .agent-chat-markdown 样式），按 sequenceNum 升序排列；消息渲染与 AgentChat 对齐：msg.reasoning 非空时显示"思考过程"块（#252525 背景 + #ffd700 黄色左边框），content 为空时仅保留角色头部不渲染气泡（无"（空消息）"占位）；工具消息渲染：role=tool 且 toolResult 非空时 buildToolContent 解析 toolResult JSON，content 格式化为"**工具: toolName**\n\n**参数:**\n```json\narguments\n```\n\n**执行结果:**\nresult"（toolName 取 toolInfo.toolName 或 JSON 内 toolName 兜底，解析失败保持原始内容）；工具消息不再显示 toolInfo 头部（toolName+toolCallId 已移除）；左右两栏等高容器布局（alignItems: stretch + 固定高度 520），各自内容区独立滚动；返回按钮跳转 /memory/:sessionId/:type
- MemoryDetail.tsx 聚合列表新增「详情」操作按钮：跳转 /memory/:sessionId/:type/:seqRange（seqRange 格式 startSeq-endSeq，序号缺省时为 0-0），通过 navigate state 携带 startSeq/endSeq/aggregationText 参数；表格 scroll.x 调整为 910
- services/session.ts 新增 getSessionMessagesRange(sessionId, startSeq, endSeq) 调用 GET /api/sessions/{id}/messages/range（params startSeq/endSeq）返回 SessionMessage[]，对应后端 SessionController messages/range 端点
- 聚合文档详情页功能按钮与弹窗（MemoryDocumentDetail.tsx）：页面右下角新增「配置」「编辑」两个功能按钮；「配置」打开提示语配置弹窗（仅提示语 Input.TextArea + 保存/关闭按钮，打开时加载 getMemoryPrompt，保存调用 saveMemoryPrompt PUT /sessions/{id}/memory-prompt body {prompt}，模型下拉已迁移到编辑弹窗）；「编辑」打开编辑聚合文档弹窗（左右两个 TextArea：左显示已保存提示语 getMemoryPrompt、右显示聚合文本 editText=aggregationText 状态，下方模型下拉 Select 仅选择不保存——从配置弹窗迁移而来，打开编辑弹窗时加载 getMemoryPrompt、getSession（获取会话 modelId，sessionModelId=session?.modelId）与 listModels({modelType:'LLM'})，模型列表加载完成后若会话 modelId 存在于列表则 setEditModelId 预选该模型，否则保持未选中，底部 取消/保存/重新生成 三按钮）；保存按钮调用 updateMemoryDocument POST /sessions/{id}/memory/update body {docId,text} 将右侧聚合文本重新向量化并更新 ES（docId=`${sessionId}_${aggType}_${startSeq}_${endSeq}`），成功后 setAggregationText(editText) 回调更新详情页聚合文本；重新生成按钮调用 regenerateMemory POST /sessions/{id}/memory/regenerate body {docId,startSeq,endSeq,prompt} 触发异步重生成，随后每 2000ms 轮询 getRegenerateStatus GET /sessions/{id}/memory/regenerate/status（最多 30 次），COMPLETED 时仅 setEditText 回填右侧输入框（不更新详情页聚合文本）、FAILED 时提示 error
- types/memory.ts 新增 MemoryPromptSaveRequest（prompt）/MemoryUpdateRequest（docId/text）/MemoryRegenerateRequest（docId/startSeq/endSeq/prompt）/MemoryRegenerateStatusEnum（RUNNING/COMPLETED/FAILED）/MemoryRegenerateStatus（sessionId/docId/status/aggregationText/error），对应后端 memory DTO
- services/memory.ts 新增 5 个接口：getMemoryPrompt（GET /sessions/{id}/memory-prompt 返回 string）、saveMemoryPrompt（PUT /sessions/{id}/memory-prompt body {prompt}）、updateMemoryDocument（POST /sessions/{id}/memory/update body {docId,text}）、regenerateMemory（POST /sessions/{id}/memory/regenerate 返回 MemoryRegenerateStatus）、getRegenerateStatus（GET /sessions/{id}/memory/regenerate/status 返回 MemoryRegenerateStatus）
## 通用表格滚动

- 通用表格滚动 Hook：src/hooks/useTableScrollY.ts 根据 window.innerHeight 减去固定偏移量动态计算表格可滚动高度（Math.max(innerHeight - offset, 0)），监听 window resize 实时更新，卸载时移除监听，返回 scrollY 供 Table scroll={{ x, y }} 使用
- 应用范围：13 个带 scroll.x 的表格统一改为 scroll={{ x: 原宽度, y: useTableScrollY(偏移量) }}，实现固定表头 + 底部可见横向滚动条；无分页页面偏移量 216（AgentList/ModelList/SkillList/SessionList/ToolList/EvaluationList/AgentEvaluationList/EvaluationResultList/MemoryList/KnowledgeBaseList/KnowledgeFileList），含分页页面偏移量 272（AgentLogList/MemoryDetail）
## 登录与用户管理界面

- 登录页 src/pages/login/Login.tsx：登录名 + 密码表单（Form + Input.Password），调用 login 接口，成功后 message.success 并 navigate('/') 进入主界面，失败展示接口错误信息；独立全屏居中 Card 布局（不依赖主界面 Layout）
- 用户管理页 src/pages/users/UserList.tsx：仅管理员可见可操作（getCurrentUser()?.userType === USER_TYPE_ADMIN 拦截，非管理员渲染 Result 403「无权限访问」；后端接口同样强制校验）
  - 页面头部 flex/justify-between 布局：「用户管理」标题在左、「添加用户」按钮在最右
  - 分页表格（page/size，pageSizeOptions 10/20/50，showTotal，scroll={{ x: 940, y: useTableScrollY(272) }}）
  - 列：登录名、显示名（空显示 '-'）、登录状态（Tag：允许登录 green/禁止登录 red）、创建时间、操作（修改/禁止登录|恢复登录 Popconfirm）——无「用户类型」列，列表仅含普通用户（管理员由后端过滤，前端不过滤）
  - 添加用户 Modal：loginName（必填）、displayName、password（必填）；提交组装 UserCreateRequest（不含 userType/enabled，用户类型固定普通用户、登录开关由后端默认允许）
  - 修改用户 Modal：displayName、password（留空则不修改密码）；提交组装 UserUpdateRequest（不含 userType/enabled）
  - 禁止登录 = updateUser(id, { enabled: 0 })，恢复登录 = updateUser(id, { enabled: 1 })
- src/services/auth.ts：login(data: LoginRequest) 调用 POST /api/auth/login 返回 User 并保存 localStorage（CURRENT_USER_KEY='currentUser'）；getCurrentUser() 读取本地用户（未登录/损坏返回 null）；clearCurrentUser() 清除；saveCurrentUser(user) 更新本地当前用户（修改显示名等自助操作后同步）；logout() 调用 POST /api/auth/logout 注销会话并无论成败清除本地登录状态
- src/services/user.ts：listUsers({page,size}) 调用 GET /api/users 返回 PageResult<User>；createUser(data) 调用 POST /api/users；updateUser(id, data) 调用 PUT /api/users/{id}；updateCurrentUser(data) 调用 PUT /api/auth/me 自助修改当前用户显示名/密码（返回更新后 User）；导出 UserListParams 类型
- 登录守卫（src/App.tsx）：未登录（getCurrentUser() 为 null）访问任意页面（除 /login）渲染 <Navigate to="/login" replace /> 自动跳转登录页；登录页独立全屏展示不套主界面 Layout
- 角色落地页：登录成功或访问根路径 / 时按 userType 重定向——管理员（USER_TYPE_ADMIN=2）跳 /users，普通用户跳 /models（getLandingPath 函数；根路由 element 为 <Navigate to={landingPath} replace />）
- 侧边栏菜单角色过滤：getVisibleMenuItems 按当前用户 userType 过滤 MENU_ITEMS，用户管理（/users）菜单仅管理员可见，其余菜单所有登录用户可见；Menu items 使用过滤后的 menuItems
- Header 用户菜单（src/App.tsx）：Header 右上角 Avatar + 显示名 Dropdown（hover 展开），菜单按角色区分——普通用户显示「修改显示名/修改密码/退出」三项，管理员显示「修改密码/退出」两项（无修改显示名）；「修改显示名」「修改密码」分别弹出 Modal（修改密码不需验证旧密码）；修改显示名成功后 updateCurrentUser 返回的用户经 saveCurrentUser 同步 localStorage 并触发重渲染（refreshUser 版本号 state）刷新 Header 显示；「退出」调用 logout 接口清除本地登录状态并跳转 /login
- 登录成功跳转（src/pages/login/Login.tsx）：login 返回 User 后按 user.userType 跳转落地页（管理员 /users、普通用户 /models），替代原固定 navigate('/')
- e2e 登录态：e2e/utils/seedAuth.ts 提供 seedAdminLogin/seedNormalLogin（page.addInitScript 注入 localStorage currentUser），12 个既有 e2e spec 顶部 test.beforeEach 注入管理员登录态绕过守卫；e2e/Login.spec.ts 覆盖守卫跳转、管理员/普通用户落地页与菜单可见性
## WebSocket 实时消息

- 全局 WebSocket 客户端（src/services/websocket.ts，单例 webSocketClient）：
  - 登录后建立一条到后端 /ws 端点的连接（连接地址由当前站点协议+主机+WS_ENDPOINT 构建，复用后端 SESSION_ID Cookie 完成握手鉴权；开发环境经 vite.config.ts /ws 代理（ws:true）转发，生产环境经 serve.js upgrade 事件代理转发）
  - 登录/登出生命周期：auth.ts login 成功后 connect()、logout 时 close()；App.tsx 登录态 useEffect 调 connect()/close()（页面刷新后自动重连兜底）
  - 消息监听：onMessage/offMessage 注册监听器，服务端 JSON 消息解析后分发（单个监听器异常隔离）
  - 测试模式（vitest MODE=test）与无 WebSocket 环境（jsdom）静默跳过连接
- 消息分发处理（src/services/messageDispatcher.ts）：
  - 订阅全局 WS 客户端，按 messageName 分发 SEND_USER_MESSAGE（与后端 SendUserMessage/SessionMessage 序列化结构一致：sessionId/parentSessionIds/conversationId/content/messageName；parentSessionIds 为父会话链有序数组：第一个=直接父会话 ID，最后一个=主会话 ID，中间为各层父会话，主会话自身无父链时为 null 或空列表）
  - 会话页面注册机制：registerSessionPage/unregisterSessionPage（SessionPageHandler：mainSessionId/streamChildReply/onSessionMessage，当前仅支持继续会话页面 AgentChat 注册）
  - 定位规则：消息 sessionId 等于页面主会话 ID，或消息 parentSessionIds 包含页面主会话 ID（消息来自页面主会话的某层子会话）即归属该页面
  - 分发结果（统一 WebSocket 消息处理，按 message.sessionId 分流）：命中页面后——sessionId 等于页面主会话（子→主回传）→ 调用 onSessionMessage，由页面先刷新主会话消息链再按主会话状态决定继续工具或调用 chat；sessionId 为子会话（主→子发送）→ 调用 streamChildReply，以特殊标记 [send_user_message]（SEND_USER_MESSAGE_MARKER，对应后端 ChatService.SEND_USER_MESSAGE_MARKER）调用对话接口流式展示回复（页面负责按父会话链展开/补出路径标签）；找不到对应会话页面 → 触发子会话列表变更事件（subscribeChildSessionsChanged/unsubscribeChildSessionsChanged，SessionList 订阅后 fetchList 刷新）
- AgentChat.tsx 接入（路径式导航 + WS 消息驱动路径展开 + 统一消息处理）：
  - 挂载时 registerSessionPage（unmount 时 unregisterSessionPage），注册处理器含 mainSessionId/streamChildReply/onSessionMessage（均经 ref 读取最新实现）
  - ChildSessionView 新增 stream 属性（ChildStreamState：messages/currentResponse/currentReasoning/loading/toolExecuting/error）：合并渲染历史消息与实时流式消息（历史末条与流式首条相同用户消息去重），流式中展示思考过程块/流式气泡/加载指示；滚动容器 childContainerRef 内容变化自动滚动到底部
  - WS 消息驱动路径展开：streamChildReply（主→子发送）收到 SEND_USER_MESSAGE 时按 payload.parentSessionIds（第一个=直接父，最后一个=主会话）调用 expandPathFromPayload 确定目标子会话在路径中的位置并逐级补出路径标签（链：主会话→...→直接父→目标子会话，逐级 ensureChildList 补出名称），随后沿用统一子会话执行器 runChildSessionFlow 流式展示回复（switchTab=false，childStreams 状态 + childStreamsRef/streamChildReplyRef 供回调读取最新值，同一子会话流式中忽略新消息）；streamChildReply 对 sessionId===页面主会话的消息转由 onSessionMessage 处理
  - 统一消息处理 onSessionMessage（子→主回传，sessionId===页面主会话）：先 refreshMainMessages（getSessionMessages → setMessages，不重置模型/思考状态）刷新主会话消息链使子会话结果在前端立即可见；再按主会话状态继续——工具循环/续接中（loadingRef/toolExecutingRef 为 true）仅置 pendingSessionRefreshRef 待刷新标记（不额外调用 chat，避免重复总结），主会话空闲时 continueMainChat 以 SEND_USER_MESSAGE_MARKER 调用 agentChatStream 触发基于新消息链继续（marker 不被后端保存为重复用户消息，流式回复渲染进主会话消息区，onDone 有工具调用则进入主会话工具循环）
  - 工具循环与 WS 消息交互：executeToolLoop 在 pollToolStatus 返回后、进入下一轮 executeTools 前消费待刷新标记（consumePendingMainMessage：刷新消息链，主会话空闲则触发 marker 续接）；continueChatStream 续接完成（onDone 无更多工具）后同样消费待刷新标记；handleSend 用户手动发送时同步 loadingRef/toolExecutingRef 并清除遗留待刷新标记
  - 路径展开成功后直接父会话为 payload.parentSessionIds[0]，作为 runChildSessionFlow 的 parentId 用于标签缺失刷新
## WebSocket 实时消息

- 全局 WebSocket 客户端（src/services/websocket.ts，单例 webSocketClient）：
  - 登录后建立一条到后端 /ws 端点的连接（连接地址由当前站点协议+主机+WS_ENDPOINT 构建，复用后端 SESSION_ID Cookie 完成握手鉴权；开发环境经 vite.config.ts /ws 代理（ws:true）转发，生产环境经 serve.js upgrade 事件代理转发）
  - 登录/登出生命周期：auth.ts login 成功后 connect()、logout 时 close()；App.tsx 登录态 useEffect 调 connect()/close()（页面刷新后自动重连兜底）
  - 会话绑定：bindSession(sessionId) 记录当前会话 ID 并发送 {type:'BIND', sessionId}，切换绑定前先发 UNBIND 更新旧绑定（切换页面只更新绑定不断链）；连接未就绪先建立连接，由连接建立（handleOpen）补发当前绑定；断线自动重连（指数退避 1s→30s 上限，重连成功后自动重发当前会话绑定消息）；unbindSession 发送 UNBIND
  - 消息监听：onMessage/offMessage 注册监听器，服务端 JSON 消息解析后分发（单个监听器异常隔离）
  - 测试模式（vitest MODE=test）与无 WebSocket 环境（jsdom）静默跳过连接
- 消息分发处理（src/services/messageDispatcher.ts）：
  - 订阅全局 WS 客户端，按 messageName 分发 SEND_USER_MESSAGE（与后端 SendUserMessage 序列化结构一致：sessionId/parentSessionId/mainSessionId/conversationId/content/messageName）
  - 会话页面注册机制：registerSessionPage/unregisterSessionPage（SessionPageHandler：mainSessionId/isChildActive/streamChildReply/refreshChildSessions，当前仅支持继续会话页面 AgentChat 注册）
  - 定位规则：按消息的 mainSessionId/parentSessionId/sessionId 任一匹配页面主会话 ID
  - 分发结果：对应子会话为激活视图 → streamChildReply 以特殊标记 [send_user_message]（SEND_USER_MESSAGE_MARKER，对应后端 ChatService.SEND_USER_MESSAGE_MARKER）调用对话接口请求数据流流式展示回复；否则 → refreshChildSessions 刷新子会话列表；找不到对应会话页面 → 触发子会话列表变更事件（subscribeChildSessionsChanged/unsubscribeChildSessionsChanged，SessionList 订阅后 fetchList 刷新）
- AgentChat.tsx 接入：
  - 挂载时 webSocketClient.bindSession(sessionId) 绑定当前主会话并 registerSessionPage（unmount 时 unregisterSessionPage）
  - ChildSessionView 新增 stream 属性（ChildStreamState：messages/currentResponse/currentReasoning/loading）：合并渲染历史消息与实时流式消息（历史末条与流式首条相同用户消息去重），流式中展示思考过程块/流式气泡/加载指示
  - streamChildReply：以特殊标记调用 agentChatStream 流式展示子会话回复（childStreams 状态 + activeTabRef/childStreamsRef/streamChildReplyRef 供 WS 回调读取最新值，同一子会话流式中忽略新消息，完成后 assistant 消息并入 messages）
- 类型支持：src/vite-env.d.ts 引入 vite/client 类型（import.meta.env）
