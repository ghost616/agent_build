import { useCallback, useEffect, useState } from 'react';
import {
  Button,
  Form,
  Input,
  InputNumber,
  message,
  Modal,
  Popconfirm,
  Select,
  Space,
  Switch,
  Table,
  Tag,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import type { CommonStatus } from '../../types/common';
import type {
  AgentConfig,
  AgentFormData,
  KnowledgeBaseItem,
  SessionAuthType,
  SubSessionOpenMode,
} from '../../types/agent';
import type { ModelConfig } from '../../types/model';
import type { ToolConfig } from '../../types/tool';
import {
  createAgent,
  deleteAgent,
  listAgents,
  updateAgent,
  updateAgentStatus,
} from '../../services/agent';
import { listModels } from '../../services/model';
import { listTools } from '../../services/tool';
import { listSkills } from '../../services/skill';
import { listKnowledgeBases } from '../../services/knowledge';
import type { SkillConfig } from '../../types/skill';
import type { KnowledgeBase } from '../../types/knowledge';
import useTableScrollY from '../../hooks/useTableScrollY';

const STATUS_LABELS: Record<CommonStatus, string> = {
  ENABLED: '启用',
  DISABLED: '禁用',
};

const STATUS_OPTIONS = Object.entries(STATUS_LABELS).map(([value, label]) => ({
  value,
  label,
}));

const SESSION_AUTH_LABELS: Record<SessionAuthType, string> = {
  ALL: '所有会话',
  PARENT: '父会话',
  CHILD: '子会话',
};

const SESSION_AUTH_COLORS: Record<SessionAuthType, string> = {
  ALL: 'blue',
  PARENT: 'green',
  CHILD: 'orange',
};

const SESSION_AUTH_OPTIONS = Object.entries(SESSION_AUTH_LABELS).map(([value, label]) => ({
  value,
  label,
}));

const SUB_SESSION_OPEN_MODE_LABELS: Record<SubSessionOpenMode, string> = {
  WEBSOCKET: 'WebSocket推送',
  TOOL_CALL: '前台工具调用',
};

const SUB_SESSION_OPEN_MODE_COLORS: Record<SubSessionOpenMode, string> = {
  WEBSOCKET: 'cyan',
  TOOL_CALL: 'geekblue',
};

const SUB_SESSION_OPEN_MODE_OPTIONS = Object.entries(SUB_SESSION_OPEN_MODE_LABELS).map(
  ([value, label]) => ({
    value,
    label,
  }),
);

function SessionAuthSelect({
  value = [],
  onChange,
  options,
  placeholder,
  idField,
}: {
  value?: ({ [key: string]: string } & { sessionAuth: SessionAuthType })[];
  onChange?: (value: ({ [key: string]: string } & { sessionAuth: SessionAuthType })[]) => void;
  options: { value: string; label: string }[];
  placeholder?: string;
  idField: 'toolId' | 'skillId';
}): JSX.Element {
  const valueMap: Record<string, SessionAuthType> = {};
  value.forEach((v) => { valueMap[v[idField]] = v.sessionAuth; });
  const selectedIds = Object.keys(valueMap);

  const handleSelectChange = (ids: string[]): void => {
    const newValue = ids.map((id) => ({
      [idField]: id,
      sessionAuth: valueMap[id] || ('ALL' as SessionAuthType),
    }));
    onChange?.(newValue);
  };

  const handleAuthChange = (id: string, sessionAuth: SessionAuthType): void => {
    const newValue = value.map((v) =>
      v[idField] === id ? { ...v, sessionAuth } : v,
    );
    onChange?.(newValue);
  };

  return (
    <div>
      <Select
        mode="multiple"
        value={selectedIds}
        onChange={handleSelectChange}
        placeholder={placeholder}
        allowClear
        showSearch
        optionFilterProp="label"
        options={options}
        style={{ width: '100%' }}
      />
      {selectedIds.length > 0 && (
        <div style={{ marginTop: 8 }}>
          {selectedIds.map((id) => {
            const opt = options.find((o) => o.value === id);
            return (
              <div key={id} style={{ display: 'flex', alignItems: 'center', marginBottom: 4, gap: 8 }}>
                <Tag>{opt?.label || id}</Tag>
                <Select
                  value={valueMap[id]}
                  onChange={(v) => handleAuthChange(id, v as SessionAuthType)}
                  size="small"
                  style={{ width: 130 }}
                  options={SESSION_AUTH_OPTIONS}
                />
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}

function AgentList(): JSX.Element {
  const [dataSource, setDataSource] = useState<AgentConfig[]>([]);
  const [loading, setLoading] = useState(false);
  const [searchName, setSearchName] = useState('');
  const [filterStatus, setFilterStatus] = useState<string | undefined>(undefined);

  const [modalVisible, setModalVisible] = useState(false);
  const [editingAgent, setEditingAgent] = useState<AgentConfig | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm<AgentFormData>();
  const memoryEnabled = Form.useWatch('memoryEnabled', form);

  const [modelList, setModelList] = useState<ModelConfig[]>([]);
  const [vectorModelList, setVectorModelList] = useState<ModelConfig[]>([]);
  const [toolList, setToolList] = useState<ToolConfig[]>([]);
  const [skillList, setSkillList] = useState<SkillConfig[]>([]);
  const [knowledgeBaseList, setKnowledgeBaseList] = useState<KnowledgeBase[]>([]);
  const [modelMap, setModelMap] = useState<Record<string, string>>({});
  const [toolMap, setToolMap] = useState<Record<string, string>>({});
  const [skillMap, setSkillMap] = useState<Record<string, string>>({});

  const fetchList = useCallback(async () => {
    setLoading(true);
    try {
      const result = await listAgents({
        name: searchName || undefined,
        status: filterStatus as CommonStatus | undefined,
      });
      setDataSource(result);
    } catch {
      message.error('获取智能体列表失败');
    } finally {
      setLoading(false);
    }
  }, [searchName, filterStatus]);

  const fetchRefData = useCallback(async () => {
    try {
      const [models, vectorModels, tools, skills, knowledgeBases] = await Promise.all([
        listModels({ modelType: 'LLM' }),
        listModels({ modelType: 'EMBEDDINGS' }),
        listTools({}),
        listSkills({}),
        listKnowledgeBases({}),
      ]);
      setModelList(models);
      setVectorModelList(vectorModels);
      setToolList(tools);
      setSkillList(skills);
      setKnowledgeBaseList(knowledgeBases);
      const modelMapData: Record<string, string> = {};
      models.forEach((m) => {
        modelMapData[m.id] = m.name;
      });
      setModelMap(modelMapData);
      const toolMapData: Record<string, string> = {};
      tools.forEach((t) => {
        toolMapData[t.id] = t.name;
      });
      setToolMap(toolMapData);
      const skillMapData: Record<string, string> = {};
      skills.forEach((s) => {
        skillMapData[s.id] = s.name;
      });
      setSkillMap(skillMapData);
    } catch {
      message.error('获取模型/工具/技能/知识库列表失败');
    }
  }, []);

  useEffect(() => {
    fetchList();
  }, [fetchList]);

  useEffect(() => {
    fetchRefData();
  }, [fetchRefData]);

  const handleSearch = (value: string): void => {
    setSearchName(value);
  };

  const handleAdd = (): void => {
    setEditingAgent(null);
    form.resetFields();
    setModalVisible(true);
  };

  const handleEdit = (record: AgentConfig): void => {
    setEditingAgent(record);
    setModalVisible(true);
  };

  useEffect(() => {
    if (!editingAgent || !modalVisible) return;
    form.setFieldsValue({
      name: editingAgent.name,
      description: editingAgent.description,
      systemPrompt: editingAgent.systemPrompt,
      modelId: editingAgent.modelId,
      tools: editingAgent.tools,
      skills: editingAgent.skills,
      knowledgeBaseIds: editingAgent.knowledgeBases?.map((kb) => kb.id),
      recentMessageCount: editingAgent.recentMessageCount,
      memoryEnabled: editingAgent.memoryEnabled,
      memoryGroupCount: editingAgent.memoryGroupCount,
      vectorModelId: editingAgent.vectorModelId,
      subSessionOpenMode: editingAgent.subSessionOpenMode ?? 'TOOL_CALL',
    });
  }, [editingAgent, modalVisible, form]);

  const handleDelete = async (record: AgentConfig): Promise<void> => {
    try {
      await deleteAgent(record.id);
      message.success('删除成功');
      fetchList();
    } catch {
      message.error('删除失败');
    }
  };

  const handleStatusChange = async (
    checked: boolean,
    record: AgentConfig,
  ): Promise<void> => {
    const status: CommonStatus = checked ? 'ENABLED' : 'DISABLED';
    try {
      await updateAgentStatus(record.id, status);
      message.success(status === 'ENABLED' ? '已启用' : '已禁用');
      fetchList();
    } catch {
      message.error('状态更新失败');
    }
  };

  const handleModalOk = async (): Promise<void> => {
    let values: AgentFormData;
    try {
      values = await form.validateFields();
    } catch {
      return;
    }
    if (!values.memoryEnabled) {
      values.vectorModelId = undefined;
    }

    setSubmitting(true);
    try {
      if (editingAgent) {
        await updateAgent(editingAgent.id, values);
        message.success('更新成功');
      } else {
        await createAgent(values);
        message.success('创建成功');
      }
      setModalVisible(false);
      fetchList();
    } catch {
      message.error(editingAgent ? '更新失败' : '创建失败');
    } finally {
      setSubmitting(false);
    }
  };

  const columns: ColumnsType<AgentConfig> = [
    {
      title: '名称',
      dataIndex: 'name',
      width: 160,
      ellipsis: true,
    },
    {
      title: '描述',
      dataIndex: 'description',
      width: 240,
      ellipsis: true,
    },
    {
      title: '关联模型',
      dataIndex: 'modelId',
      width: 140,
      render: (value: string | undefined) =>
        value ? modelMap[value] || '-' : '-',
    },
    {
      title: '挂载工具',
      dataIndex: 'tools',
      width: 200,
      render: (value: { toolId: string; sessionAuth: SessionAuthType }[] | undefined) => {
        if (!value || value.length === 0) return '-';
        return (
          <Space size={[0, 4]} wrap>
            {value.map((item) => (
              <Tag key={item.toolId} color={SESSION_AUTH_COLORS[item.sessionAuth]}>
                {toolMap[item.toolId] || item.toolId}: {SESSION_AUTH_LABELS[item.sessionAuth]}
              </Tag>
            ))}
          </Space>
        );
      },
    },
    {
      title: '挂载技能',
      dataIndex: 'skills',
      width: 200,
      render: (value: { skillId: string; sessionAuth: SessionAuthType }[] | undefined) => {
        if (!value || value.length === 0) return '-';
        return (
          <Space size={[0, 4]} wrap>
            {value.map((item) => (
              <Tag key={item.skillId} color={SESSION_AUTH_COLORS[item.sessionAuth]}>
                {skillMap[item.skillId] || item.skillId}: {SESSION_AUTH_LABELS[item.sessionAuth]}
              </Tag>
            ))}
          </Space>
        );
      },
    },
    {
      title: '绑定知识库',
      dataIndex: 'knowledgeBases',
      width: 200,
      render: (value: KnowledgeBaseItem[] | undefined) => {
        if (!value || value.length === 0) return '-';
        return (
          <Space size={[0, 4]} wrap>
            {value.map((item) => (
              <Tag key={item.id}>{item.name}</Tag>
            ))}
          </Space>
        );
      },
    },
    {
      title: '最近消息',
      dataIndex: 'recentMessageCount',
      width: 100,
      render: (value: number | undefined) =>
        value === undefined || value === null ? '-' : value,
    },
    {
      title: '子会话打开方式',
      dataIndex: 'subSessionOpenMode',
      width: 140,
      render: (value: SubSessionOpenMode | undefined) => {
        const mode: SubSessionOpenMode = value ?? 'TOOL_CALL';
        return (
          <Tag color={SUB_SESSION_OPEN_MODE_COLORS[mode]}>
            {SUB_SESSION_OPEN_MODE_LABELS[mode]}
          </Tag>
        );
      },
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 80,
      render: (value: CommonStatus) => (
        <Tag color={value === 'ENABLED' ? 'green' : 'red'}>
          {STATUS_LABELS[value]}
        </Tag>
      ),
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      width: 180,
    },
    {
      title: '操作',
      key: 'actions',
      width: 200,
      render: (_: unknown, record: AgentConfig) => (
        <Space size="small">
          <Button type="link" size="small" onClick={() => handleEdit(record)}>
            编辑
          </Button>
          <Popconfirm
            title="确定删除该智能体？"
            onConfirm={() => handleDelete(record)}
          >
            <Button type="link" size="small" danger>
              删除
            </Button>
          </Popconfirm>
          <Switch
            checked={record.status === 'ENABLED'}
            onChange={(checked) => handleStatusChange(checked, record)}
          />
        </Space>
      ),
    },
  ];

  return (
    <div>
      <Space style={{ marginBottom: 16 }} wrap>
        <Input.Search
          placeholder="搜索智能体名称"
          allowClear
          style={{ width: 240 }}
          onSearch={handleSearch}
        />
        <Select
          placeholder="状态"
          allowClear
          style={{ width: 120 }}
          options={STATUS_OPTIONS}
          value={filterStatus}
          onChange={(value) => {
            setFilterStatus(value);
          }}
        />
        <Button type="primary" onClick={handleAdd}>
          新增智能体
        </Button>
      </Space>

      <Table<AgentConfig>
        rowKey="id"
        columns={columns}
        dataSource={dataSource}
        loading={loading}
        pagination={false}
        scroll={{ x: 1600, y: useTableScrollY(216) }}
      />

      <Modal
        title={editingAgent ? '编辑智能体' : '新增智能体'}
        open={modalVisible}
        onOk={handleModalOk}
        onCancel={() => setModalVisible(false)}
        confirmLoading={submitting}
        width={640}
        destroyOnClose
      >
        <Form form={form} layout="vertical" preserve={false}>
          <Form.Item
            name="name"
            label="名称"
            rules={[{ required: true, message: '请输入智能体名称' }]}
          >
            <Input placeholder="请输入智能体名称" maxLength={100} />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea placeholder="请输入智能体描述" rows={3} maxLength={500} showCount />
          </Form.Item>
          <Form.Item name="systemPrompt" label="系统提示词">
            <Input.TextArea placeholder="请输入系统提示词" rows={4} maxLength={2000} showCount />
          </Form.Item>
          <Form.Item name="modelId" label="关联模型">
            <Select
              placeholder="请选择关联模型"
              allowClear
              showSearch
              optionFilterProp="label"
              options={modelList.map((m) => ({
                value: m.id,
                label: m.name,
              }))}
            />
          </Form.Item>
          <Form.Item
            name="subSessionOpenMode"
            label="子会话打开方式"
            initialValue="TOOL_CALL"
          >
            <Select
              placeholder="请选择子会话打开方式"
              options={SUB_SESSION_OPEN_MODE_OPTIONS}
            />
          </Form.Item>
          <Form.Item name="tools" label="挂载工具">
            <SessionAuthSelect
              idField="toolId"
              placeholder="请选择挂载工具"
              options={toolList.map((t) => ({
                value: t.id,
                label: t.name,
              }))}
            />
          </Form.Item>
          <Form.Item name="skills" label="挂载技能">
            <SessionAuthSelect
              idField="skillId"
              placeholder="请选择挂载技能"
              options={skillList.map((s) => ({
                value: s.id,
                label: s.name,
              }))}
            />
          </Form.Item>
          <Form.Item name="knowledgeBaseIds" label="绑定知识库">
            <Select
              mode="multiple"
              placeholder="请选择绑定知识库"
              allowClear
              showSearch
              optionFilterProp="label"
              options={knowledgeBaseList.map((kb) => ({
                value: kb.id,
                label: kb.name,
              }))}
            />
          </Form.Item>
          <Form.Item
            name="recentMessageCount"
            label="保留对话轮数"
            extra="保留最近 N 轮对话对 AI 可见，更早对话折叠"
            initialValue={10}
          >
            <InputNumber
              placeholder="请输入保留对话轮数"
              min={1}
              max={100}
              style={{ width: '100%' }}
            />
          </Form.Item>
          <Form.Item
            name="memoryEnabled"
            label="记忆功能"
            valuePropName="checked"
          >
            <Switch />
          </Form.Item>
          <Form.Item
            name="memoryGroupCount"
            label="保留消息组数量"
            extra="保留最近 N 个消息组对 AI 可见，更早的消息组归档为记忆"
            initialValue={30}
            hidden={!memoryEnabled}
          >
            <InputNumber
              placeholder="请输入保留消息组数量"
              min={1}
              style={{ width: '100%' }}
            />
          </Form.Item>
          <Form.Item
            name="vectorModelId"
            label="向量模型"
            hidden={!memoryEnabled}
          >
            <Select
              placeholder="请选择向量模型"
              allowClear
              showSearch
              optionFilterProp="label"
              options={vectorModelList.map((m) => ({
                value: m.id,
                label: m.name,
              }))}
            />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}

export default AgentList;
