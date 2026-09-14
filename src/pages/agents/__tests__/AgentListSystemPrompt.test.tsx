import { describe, it, expect } from 'vitest';
import { readFileSync, readdirSync, statSync } from 'fs';
import { resolve, join } from 'path';

/**
 * 变更点：智能体配置弹窗（新增/编辑共用）「系统提示词」输入框长度上限 2000 -> 8000。
 *
 * 说明：本文件为静态验证（源码契约校验），与 AgentList.test.tsx 的既有验证风格一致。
 * 真实 DOM 交互（输入 8000 字符、计数器、超长截断、编辑回显）由 e2e/AgentListSystemPrompt.spec.ts 覆盖。
 */
const SOURCE = readFileSync(resolve(__dirname, '../AgentList.tsx'), 'utf-8');

/** 提取 name="xxx" 对应的 Form.Item 源码块 */
function formItemBlock(name: string): string {
  const re = new RegExp(`<Form\\.Item\\s+name="${name}"[\\s\\S]*?</Form\\.Item>`);
  const block = SOURCE.match(re);
  expect(block, `未找到 Form.Item name="${name}"`).not.toBeNull();
  return block ? block[0] : '';
}

/** 递归收集 src 下所有 .ts/.tsx 生产源码（排除 __tests__ 与 *.test.*，避免测试自身字面量干扰） */
function collectSources(dir: string, acc: string[] = []): string[] {
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    if (statSync(full).isDirectory()) {
      if (entry === '__tests__') continue;
      collectSources(full, acc);
    } else if (/\.(ts|tsx)$/.test(entry) && !/\.(test|spec)\.tsx?$/.test(entry)) {
      acc.push(full);
    }
  }
  return acc;
}

describe('系统提示词输入框长度上限 8000 (静态验证)', () => {
  it('systemPrompt 使用 Input.TextArea，maxLength=8000', () => {
    const block = formItemBlock('systemPrompt');
    expect(block).toContain('<Input.TextArea');
    expect(block).toContain('maxLength={8000}');
  });

  it('systemPrompt 保留 showCount 字符计数器与 rows=4、placeholder', () => {
    const block = formItemBlock('systemPrompt');
    expect(block).toContain('showCount');
    expect(block).toContain('rows={4}');
    expect(block).toContain('placeholder="请输入系统提示词"');
    expect(block).toContain('label="系统提示词"');
  });

  it('旧的 2000 上限已移除（反向验证）', () => {
    const block = formItemBlock('systemPrompt');
    expect(block).not.toContain('maxLength={2000}');
    expect(SOURCE).not.toMatch(/name="systemPrompt"[\s\S]*?maxLength=\{2000\}/);
  });

  it('systemPrompt 仍允许为空：Form.Item 未新增 required/rules 校验', () => {
    const block = formItemBlock('systemPrompt');
    expect(block).not.toContain('rules=');
    expect(block).not.toContain('required');
  });

  it('同弹窗名称字段保持 maxLength=100 且仍为必填', () => {
    const block = formItemBlock('name');
    expect(block).toContain('maxLength={100}');
    expect(block).toContain("rules={[{ required: true, message: '请输入智能体名称' }]}");
  });

  it('同弹窗描述字段保持 maxLength=500（showCount 保持不变）', () => {
    const block = formItemBlock('description');
    expect(block).toContain('maxLength={500}');
    expect(block).toContain('showCount');
  });

  it('编辑场景回填 systemPrompt（systemPrompt: editingAgent.systemPrompt）', () => {
    expect(SOURCE).toContain('systemPrompt: editingAgent.systemPrompt');
    const effectBlock = SOURCE.match(
      /useEffect\(\(\) => \{[\s\S]*?editingAgent\.subSessionOpenMode[\s\S]*?\}, \[/,
    );
    expect(effectBlock).not.toBeNull();
    if (effectBlock) {
      expect(effectBlock[0]).toContain('systemPrompt: editingAgent.systemPrompt');
    }
  });

  it('新增与编辑共用同一弹窗与同一输入框（一处修改覆盖两个场景）', () => {
    expect(SOURCE).toContain("title={editingAgent ? '编辑智能体' : '新增智能体'}");
    const all = collectSources(resolve(__dirname, '../../..'));
    const hits: string[] = [];
    for (const file of all) {
      const content = readFileSync(file, 'utf-8');
      const matches = content.match(/name="systemPrompt"/g);
      if (matches) hits.push(...matches.map(() => file));
    }
    expect(hits.length).toBe(1);
    expect(hits[0].endsWith(join('agents', 'AgentList.tsx'))).toBe(true);
  });

  it('提交时透传 values（含 systemPrompt）到 createAgent / updateAgent', () => {
    const modalOkBlock = SOURCE.match(/const handleModalOk[\s\S]*?};/);
    expect(modalOkBlock).not.toBeNull();
    if (modalOkBlock) {
      expect(modalOkBlock[0]).toContain('updateAgent(editingAgent.id, values)');
      expect(modalOkBlock[0]).toContain('createAgent(values)');
    }
  });

  it('类型定义中 systemPrompt 为可选字符串（接口契约未改动）', () => {
    const agentTypes = readFileSync(resolve(__dirname, '../../../types/agent.ts'), 'utf-8');
    expect(agentTypes).toContain('systemPrompt?: string;');
  });

  it('未越界改动：仅 AgentList.tsx 使用 maxLength={8000}', () => {
    const all = collectSources(resolve(__dirname, '../../..'));
    const offenders = all.filter((file) => {
      const content = readFileSync(file, 'utf-8');
      return (
        content.includes('maxLength={8000}') &&
        !file.endsWith(join('agents', 'AgentList.tsx'))
      );
    });
    expect(offenders).toEqual([]);
  });
});

describe('系统提示词边界值契约 (静态验证)', () => {
  it('systemPrompt 的 maxLength 恰为 8000（非其他上限）', () => {
    const promptBlock = formItemBlock('systemPrompt');
    const promptMax = promptBlock.match(/maxLength=\{(\d+)\}/);
    expect(promptMax).not.toBeNull();
    expect(Number(promptMax ? promptMax[1] : 0)).toBe(8000);
  });

  it('maxLength 为数字字面量而非字符串（避免类型/行为异常）', () => {
    const block = formItemBlock('systemPrompt');
    expect(block).toMatch(/maxLength=\{8000\}/);
    expect(block).not.toMatch(/maxLength="8000"/);
  });

  it('上限放宽后不影响其他字段上限（名称 100 / 描述 500）', () => {
    const values = [...SOURCE.matchAll(/maxLength=\{(\d+)\}/g)].map((m) => Number(m[1]));
    expect(values).toContain(100);
    expect(values).toContain(500);
    expect(values).toContain(8000);
  });
});