# platform-data 模块功能说明

> 待力牧首次执行后填充，记录模块公共方法与功能。
## 数据实体层

- **SubToolType**：子工具类型枚举（BROWSER/RAG_KNOWLEDGE），@EnumValue 标记 code 字段
- **AgentLogEntity**：智能体日志实体，继承 BaseEntity，映射 agent_log 表，含 sessionId(会话ID)/conversationId(对话ID)/logType(日志类型，存储 LogType 枚举 code 值)/logLevel(日志等级，存储 LogLevel 枚举 code 值)/logData(LogData 对象序列化 JSON 文本) 字段
- **AgentConfig**：智能体配置实体，继承 BaseEntity，映射 agent_config 表，含 name/description/systemPrompt/modelId/status/recentMessageCount/memoryEnabled/memoryGroupCount/vectorModelId（关联 model_config.id）字段
- **Session**：会话实体，继承 BaseEntity，映射 session 表，含 agentId/modelId/title/systemPrompt/parentSessionId/isChild/description/totalTokenUsed/lastResponseId/isEvaluation/thinking/memoryPointSequenceNum/memoryPrompt（记忆提示语，仅通过独立接口读写、不加入 SessionDTO）字段
- **AggregationType**：聚合类型枚举（GROUP/DAILY），@EnumValue 标记 code 字段
- **User**：用户实体，继承 BaseEntity，映射 user 表，含 loginName(登录名)/displayName(显示名)/userType(用户类型 Integer，1=普通用户，2=管理员)/password(密码)/enabled(登录开关 Integer，1=可登录，0=禁止登录，默认 1) 字段
- 数据归属扩展：model_config、tool_config、session、session_variable、agent_config、skill_config、agent_evaluation、evaluation、evaluation_result、knowledge_base、knowledge_file、message、agent_log 共 13 个实体类新增 userId 字段（Long，@TableField("user_id")），标识记录归属用户
- AgentConfig 实体新增 subSessionOpenMode 字段（SubSessionOpenMode 枚举：WEBSOCKET/TOOL_CALL，@EnumValue 存储 code，默认 TOOL_CALL，映射 sub_session_open_mode 列，表示子会话打开方式）
- **MessageImage**：消息图片映射实体，映射 message_image 表，含 id(雪花ID，@JsonSerialize ToStringSerializer)/messageId(关联消息ID)/imgId(图片标识 String)/imgText(大模型图像理解结果文本) 字段，用于大模型图像理解场景的图片-消息映射持久化
## 数据访问层

- **MessageMapper**：继承 BaseMapper\<Message\>，额外提供 rollbackBySessionIdAndGeSequenceNum 批量更新方法、selectByConversationId 按会话查询未回滚消息（按创建时间升序）、countUserMessages 统计会话下 user 角色未回滚消息总数、findNthUserSequenceNum 查找会话内第 n 个 user 未回滚消息的 sequenceNum（按 sequence_num 升序，LIMIT 1 OFFSET n）；添加 @DS("message") 注解路由至副数据源
- **AgentLogMapper**：AgentLogEntity 的 MyBatis-Plus Mapper 接口，继承 BaseMapper；添加 @DS("message") 注解路由至副数据源
- **多数据源支持**：platform-data 引入 dynamic-datasource-spring-boot3-starter 4.1.1，支持 @DS 注解按 Mapper 动态路由主/副数据源
- **MessageImageMapper**：MessageImage 实体的 MyBatis-Plus Mapper 接口，继承 BaseMapper；添加 @DS("message") 注解路由至消息数据源，与 message/agent_log/message_tool_call 同库
## 数据库初始化与迁移

- **schema.sql** (classpath 根路径)：主数据源 DDL 初始化脚本，定义除 message、agent_log、message_tool_call 外的所有业务表（model_config、tool_config、session、session_tool、agent_config、agent_tool、agent_skill、skill_config、skill_tool、session_variable、session_skill、evaluation、evaluation_result、agent_evaluation、knowledge_base、knowledge_file、agent_knowledge_base）的建表语句和索引
- **schema-message.sql** (classpath 根路径)：消息数据源 DDL 初始化脚本，定义 message、agent_log、message_tool_call 三张表的建表语句和索引（含 message_tool_call 的 message_id/tool_call_id 索引）
- **MessageSchemaMigration**（com.ghost616.platform.config）：消息数据源 Schema 迁移组件，继承 ApplicationRunner，注入 @Qualifier("messageJdbcTemplate")，通过 ScriptUtils 执行 schema-message.sql 建表（message/agent_log/message_tool_call 三表齐全）；建表失败时抛 IllegalStateException fail-fast 终止启动，避免应用带病启动；随后执行 message/agent_log/message_tool_call 三表 9 条 ALTER 增量列迁移
- **PrimarySchemaMigration**（com.ghost616.platform.config）：主数据源 Schema 迁移组件，继承 ApplicationRunner，注入 @Qualifier("primaryJdbcTemplate")，执行主数据源各表 78 条 ALTER 增量列迁移（不含 message/agent_log/message_tool_call，message_tool_call 迁移已移至 MessageSchemaMigration），并回填 session_auth NULL 值
新增 agent_evaluation 表建表语句及 evaluation 表 agent_eval_id/agent_id/execution_type 列；PrimarySchemaMigration 新增 agent_evaluation 表全字段迁移及 evaluation.agent_eval_id/evaluation.agent_id/evaluation.execution_type 迁移条目
新增 knowledge_base、knowledge_file、agent_knowledge_base 三张表建表语句及对应索引；PrimarySchemaMigration 新增三张表全字段迁移条目
PrimarySchemaMigration 新增 knowledge_file.publish_status（VARCHAR(32)，默认 'UNPUBLISHED'）、knowledge_base.vector_model_id（BIGINT）、knowledge_base.es_index（VARCHAR(255)）、knowledge_base.rebuilding（TINYINT(1)，默认 0）四个列迁移条目；schema.sql knowledge_base/knowledge_file 表 DDL 同步新增对应列
新增 agent_log 表建表语句及 session_id/conversation_id 索引；schema-message.sql 定义 message、agent_log（智能体日志，含 session_id/conversation_id/log_type/log_level/log_data）两表
agent_config 表新增 memory_enabled（TINYINT(1) 默认 0）、memory_group_count（INTEGER 默认 30）列，PrimarySchemaMigration 同步新增对应迁移条目；AgentConfig 实体新增 memoryEnabled/memoryGroupCount 字段
agent_config 表新增 vector_model_id（BIGINT）列、session 表新增 memory_point_sequence_num（INTEGER）列，PrimarySchemaMigration 同步新增对应迁移条目；AgentConfig 实体新增 vectorModelId 字段，Session 实体新增 memoryPointSequenceNum 字段
session 表新增 memory_prompt（VARCHAR(500)）列，PrimarySchemaMigration 同步新增对应迁移条目；Session 实体新增 memoryPrompt 字段（记忆提示语，仅通过独立接口读写、不加入 SessionDTO）
- schema.sql 新增 user 表建表语句（login_name/display_name/user_type/password/enabled 默认 1）；model_config、tool_config、session、session_variable、agent_config、skill_config、agent_evaluation、evaluation、evaluation_result、knowledge_base、knowledge_file 11 张表新增 user_id BIGINT DEFAULT 1 列（user 为 H2 保留字，H2 测试库通过 NON_KEYWORDS=USER 连接参数支持，生产 SQLite 原生支持）
- schema-message.sql 中 message、agent_log 表新增 user_id BIGINT DEFAULT 1 列
- PrimarySchemaMigration 新增 11 条 user_id 迁移条目（总数 78→89）；MessageSchemaMigration 新增 message.user_id、agent_log.user_id 两条迁移条目（总数 9→11）；platform-app SchemaMigrationTest 计数常量同步更新（PRIMARY 89、MESSAGE 11）
- agent_config 表新增 sub_session_open_mode（VARCHAR(32)，默认 'TOOL_CALL'）列；AgentConfig 实体新增 subSessionOpenMode 字段（SubSessionOpenMode 枚举，默认 TOOL_CALL）
- schema-message.sql 新增 message_image 表建表语句（id/message_id/img_id VARCHAR(255)/img_text MEDIUMTEXT）及 idx_message_image_message_id 索引；MessageSchemaMigration 新增 message_image.message_id/img_id/img_text 三条 ALTER 增量迁移（迁移总数 11→14）
## 统一错误码与异常

- **ErrorCode**：统一业务错误码枚举（com.ghost616.platform.enums.ErrorCode），共 31 个，覆盖 SYS/MODEL/TOOL/AGENT/SKILL/SESSION/EVAL/AGENT-EVAL/KNOWLEDGE 各业务域错误码，提供 getCode/getMessage。含 SYSTEM_ERROR（SYS-001）、PARAM_INVALID（SYS-002）、AGENT_MEMORY_NOT_ENABLED（AGENT-CONFIG-005，智能体未开启记忆功能）等
- **BaseException**：平台统一基础异常，继承 RuntimeException，携带 ErrorCode 与 detail，提供 getErrorCode/getDetail
- **BusinessException**：业务逻辑异常，继承 BaseException，用于业务规则校验失败抛出
- 已移除未使用的 UNAUTHORIZED（SYS-004）、EVALUATION_SESSION_NOT_CREATED（EVAL-EXEC-002），枚举总数由 33 精简为 31；ErrorCodeTest 断言同步更新
- **ErrorCode 新增 USER 域错误码**：USER_NOT_LOGIN（USER-NOT-LOGIN，请登录）、USER_FORBIDDEN（USER-FORBIDDEN，无权限）、USER_ALREADY_EXISTS（USER-ALREADY-EXISTS，登录名已存在）、USER_NOT_FOUND（USER-NOT-FOUND，用户不存在），枚举总数由 31 增至 35；ErrorCodeTest 断言同步更新