import { useCallback, useEffect, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import { Button, message, Modal, Table, Tag, Tooltip, Typography } from 'antd';
import { ArrowLeftOutlined } from '@ant-design/icons';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import type { CSSProperties } from 'react';
import type { ColumnsType } from 'antd/es/table';
import type { SessionMessage, ToolCallData } from '../../types/session';
import { getConversationMessages } from '../../services/session';

const ROLE_LABELS: Record<string, { text: string; color: string }> = {
  user: { text: '用户', color: 'blue' },
  assistant: { text: '助手', color: 'green' },
  tool: { text: '工具', color: 'purple' },
  system: { text: '系统', color: 'default' },
};

const LINE_ROW_STYLE: CSSProperties = {
  whiteSpace: 'nowrap',
  overflow: 'hidden',
  textOverflow: 'ellipsis',
  lineHeight: '1.5',
};

const CONTENT_CELL_STYLE: CSSProperties = {
  cursor: 'pointer',
  maxHeight: 63,
  overflow: 'hidden',
};

const PRE_STYLE: CSSProperties = {
  maxHeight: 220,
  overflow: 'auto',
  margin: '4px 0',
  padding: 8,
  backgroundColor: '#f5f5f5',
  borderRadius: 4,
  fontSize: 12,
  whiteSpace: 'pre-wrap',
  wordBreak: 'break-all',
};

interface ToolResultPair {
  toolName?: string;
  result?: string;
}

function shortenSessionId(id: string): string {
  if (!id || id.length <= 12) {
    return id || '-';
  }
  return `${id.slice(0, 8)}…${id.slice(-4)}`;
}

function findToolResult(messages: SessionMessage[], fromIndex: number, toolCallId: string): ToolResultPair | undefined {
  for (let i = fromIndex + 1; i < messages.length; i += 1) {
    const msg = messages[i];
    if (msg.role === 'tool' && msg.toolInfo?.toolCallId === toolCallId) {
      return { toolName: msg.toolInfo.toolName, result: msg.toolResult };
    }
  }
  return undefined;
}

function renderToolCallFlow(messages: SessionMessage[], msgIndex: number, toolCalls: ToolCallData[]): JSX.Element {
  return (
    <div style={{ marginTop: 8 }}>
      <div>🔧 工具调用</div>
      {toolCalls.map((tc) => {
        const pair = findToolResult(messages, msgIndex, tc.toolCallId);
        return (
          <div key={tc.toolCallId} style={{ marginTop: 8 }}>
            <Tag color="purple">🔧 {tc.toolCallName}</Tag>
            <pre style={PRE_STYLE}>{tc.toolCallArguments}</pre>
            {pair ? (
              <>
                <div style={{ marginTop: 4 }}>📋 {pair.toolName}</div>
                <pre style={PRE_STYLE}>{pair.result ?? '无结果'}</pre>
              </>
            ) : null}
          </div>
        );
      })}
    </div>
  );
}

function renderMessageFlow(messages: SessionMessage[], sessionId?: string): JSX.Element[] {
  return messages.map((msg, index) => {
    const roleCfg = ROLE_LABELS[msg.role] || { text: msg.role, color: 'default' };
    return (
      <div
        key={msg.id}
        id={`msg-${msg.id}`}
        style={{ marginBottom: 16, padding: 12, border: '1px solid #f0f0f0', borderRadius: 4 }}
      >
        <div style={{ marginBottom: 8 }}>
          <Tag color={roleCfg.color}>{roleCfg.text}</Tag>
          {sessionId ? (
            <Tag color={msg.sessionId === sessionId ? 'gold' : 'cyan'}>
              {msg.sessionId === sessionId ? '主会话' : '子会话'}
            </Tag>
          ) : null}
        </div>
        {msg.role === 'user' && msg.content ? (
          <div>📝 <ReactMarkdown remarkPlugins={[remarkGfm]}>{msg.content}</ReactMarkdown></div>
        ) : null}
        {msg.role === 'assistant' ? (
          <div>
            {msg.reasoning ? (
              <div style={{ marginBottom: 8 }}>
                <div>💭 推理</div>
                <pre style={PRE_STYLE}>{msg.reasoning}</pre>
              </div>
            ) : null}
            {msg.content ? (
              <div style={{ marginBottom: 8 }}>
                <div>📝 内容</div>
                <ReactMarkdown remarkPlugins={[remarkGfm]}>{msg.content}</ReactMarkdown>
              </div>
            ) : null}
            {msg.toolCalls && msg.toolCalls.length > 0
              ? renderToolCallFlow(messages, index, msg.toolCalls)
              : null}
          </div>
        ) : null}
        {msg.role === 'tool' ? (
          <div>
            <div>📋 {msg.toolInfo?.toolName ?? '工具结果'}</div>
            <pre style={PRE_STYLE}>{msg.toolResult ?? ''}</pre>
          </div>
        ) : null}
        {msg.role !== 'user' && msg.role !== 'assistant' && msg.role !== 'tool' && msg.content ? (
          <div>📝 <ReactMarkdown remarkPlugins={[remarkGfm]}>{msg.content}</ReactMarkdown></div>
        ) : null}
        <div style={{ marginTop: 8, color: '#999', fontSize: 12 }}>{msg.createTime}</div>
      </div>
    );
  });
}

function ConversationDetail(): JSX.Element {
  const { conversationId } = useParams<{ conversationId?: string }>();
  const location = useLocation();
  const navigate = useNavigate();
  const sessionId = (location.state as { sessionId?: string } | null)?.sessionId;
  const [messages, setMessages] = useState<SessionMessage[]>([]);
  const [loading, setLoading] = useState(false);
  const [detailVisible, setDetailVisible] = useState(false);
  const [clickedMsgId, setClickedMsgId] = useState<string>('');

  const fetchMessages = useCallback(async (cid: string): Promise<void> => {
    setLoading(true);
    try {
      const result = await getConversationMessages(cid);
      setMessages(result);
    } catch {
      message.error('获取对话消息失败');
      setMessages([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (conversationId) {
      fetchMessages(conversationId);
    }
  }, [conversationId, fetchMessages]);

  const rowClassName = (record: SessionMessage): string => {
    if (!sessionId) {
      return '';
    }
    return record.sessionId === sessionId ? 'conversation-main-row' : 'conversation-child-row';
  };

  const columns: ColumnsType<SessionMessage> = [
    {
      title: '角色',
      dataIndex: 'role',
      key: 'role',
      width: 100,
      render: (role: string) => {
        const config = ROLE_LABELS[role] || { text: role, color: 'default' };
        return <Tag color={config.color}>{config.text}</Tag>;
      },
    },
    {
      title: '内容',
      dataIndex: 'content',
      key: 'content',
      width: 300,
      ellipsis: true,
      render: (_content: string, record: SessionMessage) => (
        <div
          style={CONTENT_CELL_STYLE}
          onClick={() => {
            setClickedMsgId(record.id);
            setDetailVisible(true);
          }}
        >
          {record.reasoning != null ? (
            <div style={LINE_ROW_STYLE}>💭 {record.reasoning}</div>
          ) : null}
          {record.content != null ? <div style={LINE_ROW_STYLE}>📝 {record.content}</div> : null}
          {record.role === 'assistant' && record.toolCalls && record.toolCalls.length > 0 ? (
            <div style={LINE_ROW_STYLE}>
              <Button size="small" type="text">
                🔧 工具调用 ({record.toolCalls.length})
              </Button>
            </div>
          ) : null}
          {record.role === 'tool' ? (
            <div style={LINE_ROW_STYLE}>
              <Button size="small" type="text">
                📋 工具结果
              </Button>
            </div>
          ) : null}
          {record.reasoning == null &&
          record.content == null &&
          !(record.role === 'assistant' && record.toolCalls && record.toolCalls.length > 0) &&
          record.role !== 'tool' ? (
            <div style={LINE_ROW_STYLE}>-</div>
          ) : null}
        </div>
      ),
    },
    {
      title: '来源会话',
      dataIndex: 'sessionId',
      key: 'sessionId',
      width: 160,
      render: (sid: string) => (
        <Tooltip title={sid}>
          <span style={{ fontFamily: 'monospace' }}>{shortenSessionId(sid)}</span>
        </Tooltip>
      ),
    },
    {
      title: '时间',
      dataIndex: 'createTime',
      key: 'createTime',
      width: 180,
    },
  ];

  return (
    <div>
      <Button
        icon={<ArrowLeftOutlined />}
        style={{ marginBottom: 12 }}
        onClick={() => navigate(sessionId ? `/conversations/${sessionId}` : '/conversations')}
      >
        返回
      </Button>
      <Typography.Title level={5} style={{ marginBottom: 16 }}>
        对话详情
      </Typography.Title>
      <Table<SessionMessage>
        rowKey="id"
        columns={columns}
        dataSource={messages}
        loading={loading}
        pagination={false}
        bordered
        rowClassName={rowClassName}
      />
      <Modal
        title="对话详情"
        open={detailVisible}
        onCancel={() => setDetailVisible(false)}
        footer={null}
        width={960}
        afterOpenChange={(open) => {
          if (open && clickedMsgId) {
            setTimeout(() => {
              document.getElementById(`msg-${clickedMsgId}`)?.scrollIntoView({
                behavior: 'instant',
                block: 'center',
              });
            }, 0);
          }
        }}
      >
        <div style={{ maxHeight: 520, overflow: 'auto' }}>{renderMessageFlow(messages, sessionId)}</div>
      </Modal>
    </div>
  );
}

export default ConversationDetail;