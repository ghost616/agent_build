import { describe, it, expect } from 'vitest';
import { readFileSync } from 'fs';
import { resolve } from 'path';

const pagePath = resolve(__dirname, '../ConversationDetail.tsx');

describe('ConversationDetail 对话详情 (静态验证)', () => {
  it('应导入并调用 getConversationMessages', () => {
    const source = readFileSync(pagePath, 'utf-8');
    expect(source).toContain("getConversationMessages");
    expect(source).toContain("getConversationMessages(cid)");
  });

  it('应从 useParams 取 conversationId 并触发 fetchMessages', () => {
    const source = readFileSync(pagePath, 'utf-8');
    expect(source).toContain('conversationId');
    expect(source).toContain('fetchMessages(conversationId)');
  });

  it('应按角色展示 Tag（用户/助手/工具/系统）', () => {
    const source = readFileSync(pagePath, 'utf-8');
    expect(source).toContain("ROLE_LABELS");
    expect(source).toContain("user: { text: '用户'");
    expect(source).toContain("assistant: { text: '助手'");
    expect(source).toContain("tool: { text: '工具'");
    expect(source).toContain("system: { text: '系统'");
    expect(source).toContain("<Tag");
  });

  it('应展示内容与时间列', () => {
    const source = readFileSync(pagePath, 'utf-8');
    expect(source).toContain("dataIndex: 'content'");
    expect(source).toContain("dataIndex: 'createTime'");
  });

  it('应包含返回按钮（返回），从 state 取 sessionId 返回 /conversations/:sessionId', () => {
    const source = readFileSync(pagePath, 'utf-8');
    expect(source).toContain('返回');
    expect(source).toContain('useLocation');
    expect(source).toContain('location.state');
    expect(source).toContain('`/conversations/${sessionId}`');
    expect(source).toContain("'/conversations'");
  });

  it('内容列应展示三行（💭推理/📝内容/按钮）且单行省略', () => {
    const source = readFileSync(pagePath, 'utf-8');
    expect(source).toContain('record.reasoning');
    expect(source).toContain('record.content');
    expect(source).toContain('LINE_ROW_STYLE');
    expect(source).toContain("whiteSpace: 'nowrap'");
    expect(source).toContain('textOverflow: \'ellipsis\'');
  });

  it('内容列应通过 CONTENT_CELL_STYLE 限制三行高度使行高一致', () => {
    const source = readFileSync(pagePath, 'utf-8');
    expect(source).toContain('CONTENT_CELL_STYLE');
    expect(source).toContain('maxHeight: 63');
    expect(source).toContain('overflow: \'hidden\'');
    expect(source).toContain('style={CONTENT_CELL_STYLE}');
  });

  it('LINE_ROW_STYLE 应使用 lineHeight: 1.5 与省略号三件套', () => {
    const source = readFileSync(pagePath, 'utf-8');
    expect(source).toContain('lineHeight: \'1.5\'');
    expect(source).toContain("textOverflow: 'ellipsis'");
    expect(source).toContain("whiteSpace: 'nowrap'");
  });

  it('内容列应设置 column ellipsis: true 与 width: 300', () => {
    const source = readFileSync(pagePath, 'utf-8');
    expect(source).toContain('width: 300');
    expect(source).toContain('ellipsis: true');
  });

  it('内容列渲染条件应使用 != null 判断使空字符串可渲染', () => {
    const source = readFileSync(pagePath, 'utf-8');
    expect(source).toContain('record.reasoning != null');
    expect(source).toContain('record.content != null');
    expect(source).not.toContain('record.reasoning ? <div style={LINE_ROW_STYLE}>💭');
    expect(source).not.toContain('record.content ? <div style={LINE_ROW_STYLE}>📝');
  });

  it('内容为空且无工具时使用 "-" 兜底显示', () => {
    const source = readFileSync(pagePath, 'utf-8');
    expect(source).toContain('record.reasoning == null');
    expect(source).toContain('record.content == null');
    expect(source).toContain('record.role !== \'tool\'');
    expect(source).toContain('-');
  });

  it('assistant 有 toolCalls 时应显示 🔧 图标按钮和数量', () => {
    const source = readFileSync(pagePath, 'utf-8');
    expect(source).toContain("record.role === 'assistant'");
    expect(source).toContain('record.toolCalls');
    expect(source).toContain('🔧 工具调用');
    expect(source).toContain('toolCalls.length');
  });

  it('tool 角色应显示 📋 图标按钮', () => {
    const source = readFileSync(pagePath, 'utf-8');
    expect(source).toContain("record.role === 'tool'");
    expect(source).toContain('📋 工具结果');
  });

  it('点击内容区域应打开 Modal 并按对话流展示消息', () => {
    const source = readFileSync(pagePath, 'utf-8');
    expect(source).toContain('Modal');
    expect(source).toContain('detailVisible');
    expect(source).toContain('renderMessageFlow');
    expect(source).toContain('setClickedMsgId(record.id)');
    expect(source).toContain('setDetailVisible(true)');
  });

  it('Modal 打开时应记录点击消息 id 并在 flow 每行设置 msg-{id}', () => {
    const source = readFileSync(pagePath, 'utf-8');
    expect(source).toContain('clickedMsgId');
    expect(source).toContain('id={`msg-${msg.id}`}');
    expect(source).toContain('afterOpenChange');
  });

  it('Modal 打开后应以 setTimeout 定位滚动到当前消息行', () => {
    const source = readFileSync(pagePath, 'utf-8');
    expect(source).toContain('setTimeout');
    expect(source).toContain('document.getElementById');
    expect(source).toContain('`msg-${clickedMsgId}`');
    expect(source).toContain('scrollIntoView');
    expect(source).toContain("behavior: 'instant'");
    expect(source).toContain("block: 'center'");
  });

  it('Modal 内 assistant 应展示 💭推理、📝内容与 🔧工具调用列表（名称+参数JSON）', () => {
    const source = readFileSync(pagePath, 'utf-8');
    expect(source).toContain('💭 推理');
    expect(source).toContain('📝 内容');
    expect(source).toContain('🔧 工具调用');
    expect(source).toContain('tc.toolCallArguments');
    expect(source).toContain('tc.toolCallName');
  });

  it('Modal 内 tool 消息应展示 toolName 和结果 JSON；user 消息展示 content', () => {
    const source = readFileSync(pagePath, 'utf-8');
    expect(source).toContain("msg.role === 'tool'");
    expect(source).toContain('msg.toolInfo?.toolName');
    expect(source).toContain('msg.toolResult');
    expect(source).toContain("msg.role === 'user'");
    expect(source).toContain('msg.content');
  });

  it('应导入 ReactMarkdown 与 remark-gfm', () => {
    const source = readFileSync(pagePath, 'utf-8');
    expect(source).toContain("import ReactMarkdown from 'react-markdown'");
    expect(source).toContain("import remarkGfm from 'remark-gfm'");
  });

  it('Modal 消息卡片应增加 Tag 标签区分主/子会话（sessionId 对比）', () => {
    const source = readFileSync(pagePath, 'utf-8');
    expect(source).toContain('msg.sessionId === sessionId');
    expect(source).toContain('主会话');
    expect(source).toContain('子会话');
    expect(source).toContain('renderMessageFlow(messages, sessionId)');
  });

  it('user/assistant 的 content 应使用 ReactMarkdown 渲染，reasoning/toolResult 保持 pre', () => {
    const source = readFileSync(pagePath, 'utf-8');
    expect(source).toContain('<ReactMarkdown remarkPlugins={[remarkGfm]}>{msg.content}</ReactMarkdown>');
    expect(source).toContain('<pre style={PRE_STYLE}>{msg.reasoning}</pre>');
    expect(source).toContain('<pre style={PRE_STYLE}>{msg.toolResult ?? \'\'}</pre>');
  });

  it('Table 应增加 bordered 属性', () => {
    const source = readFileSync(pagePath, 'utf-8');
    expect(source).toContain('bordered');
  });

  it('应按 toolCallId 配对 assistant 的 toolCalls 与后续 tool 的 toolResult', () => {
    const source = readFileSync(pagePath, 'utf-8');
    expect(source).toContain('findToolResult');
    expect(source).toContain('toolCallId');
    expect(source).toContain("msg.toolInfo?.toolCallId === toolCallId");
    expect(source).toContain('fromIndex + 1');
    expect(source).toContain("pair.toolName");
    expect(source).toContain("pair.result");
  });

  it('应包含来源会话列并截短显示 sessionId（前8后4）', () => {
    const source = readFileSync(pagePath, 'utf-8');
    expect(source).toContain("title: '来源会话'");
    expect(source).toContain("dataIndex: 'sessionId'");
    expect(source).toContain('shortenSessionId');
    expect(source).toContain('id.slice(0, 8)');
    expect(source).toContain('id.slice(-4)');
  });

  it('应使用 rowClassName 按 sessionId 是否等于主会话设置不同背景色', () => {
    const source = readFileSync(pagePath, 'utf-8');
    expect(source).toContain('rowClassName={rowClassName}');
    expect(source).toContain("record.sessionId === sessionId");
    expect(source).toContain("'conversation-main-row'");
    expect(source).toContain("'conversation-child-row'");
  });
});
