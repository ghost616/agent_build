import { useCallback, useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Button, message, Space, Table, Typography } from 'antd';
import { ArrowLeftOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import type { Session, SessionMessage } from '../../types/session';
import { getSessionMessages, listSessions } from '../../services/session';

function ConversationHistory(): JSX.Element {
  const { sessionId } = useParams<{ sessionId?: string }>();
  const navigate = useNavigate();

  const [sessions, setSessions] = useState<Session[]>([]);
  const [sessionLoading, setSessionLoading] = useState(false);
  const [userMessages, setUserMessages] = useState<SessionMessage[]>([]);
  const [messagesLoading, setMessagesLoading] = useState(false);

  const fetchSessions = useCallback(async (): Promise<void> => {
    setSessionLoading(true);
    try {
      const result = await listSessions();
      setSessions(result);
    } catch {
      message.error('获取会话列表失败');
    } finally {
      setSessionLoading(false);
    }
  }, []);

  const fetchUserMessages = useCallback(async (sid: string): Promise<void> => {
    setMessagesLoading(true);
    try {
      const history = await getSessionMessages(sid);
      const users = history.filter((msg) => msg.role === 'user');
      setUserMessages(users);
    } catch {
      message.error('获取会话消息失败');
      setUserMessages([]);
    } finally {
      setMessagesLoading(false);
    }
  }, []);

  useEffect(() => {
    if (sessionId) {
      fetchUserMessages(sessionId);
    } else {
      fetchSessions();
    }
  }, [sessionId, fetchSessions, fetchUserMessages]);

  const sessionColumns: ColumnsType<Session> = [
    {
      title: '标题',
      dataIndex: 'title',
      key: 'title',
      width: 200,
      ellipsis: true,
      render: (text: string) => text || '未命名会话',
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      key: 'createTime',
      width: 180,
    },
    {
      title: '操作',
      key: 'action',
      width: 120,
      render: (_: unknown, record: Session) => (
        <Button
          type="link"
          size="small"
          onClick={() => navigate(`/conversations/${record.id}`)}
        >
          查看消息
        </Button>
      ),
    },
  ];

  const messageColumns: ColumnsType<SessionMessage> = [
    {
      title: '内容',
      dataIndex: 'content',
      key: 'content',
      ellipsis: true,
    },
    {
      title: '时间',
      dataIndex: 'createTime',
      key: 'createTime',
      width: 180,
    },
    {
      title: '操作',
      key: 'action',
      width: 120,
      render: (_: unknown, record: SessionMessage) => (
        <Space size="small">
          {record.conversationId ? (
            <Button
              type="link"
              size="small"
              onClick={() =>
                navigate(`/conversations/${record.conversationId}/detail`, { state: { sessionId } })
              }
            >
              查看详情
            </Button>
          ) : (
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              -
            </Typography.Text>
          )}
        </Space>
      ),
    },
  ];

  return (
    <div>
      {!sessionId && (
        <>
          <Typography.Title level={5} style={{ marginBottom: 16 }}>
            会话历史
          </Typography.Title>
          <Table<Session>
            rowKey="id"
            columns={sessionColumns}
            dataSource={sessions}
            loading={sessionLoading}
            pagination={false}
          />
        </>
      )}

      {sessionId && (
        <>
          <Button
            icon={<ArrowLeftOutlined />}
            style={{ marginBottom: 12 }}
            onClick={() => navigate('/conversations')}
          >
            返回
          </Button>
          <Typography.Title level={5} style={{ marginBottom: 16 }}>
            用户消息列表
          </Typography.Title>
          <Table<SessionMessage>
            rowKey="id"
            columns={messageColumns}
            dataSource={userMessages}
            loading={messagesLoading}
            pagination={false}
          />
        </>
      )}
    </div>
  );
}

export default ConversationHistory;