import { test, expect, Page } from '@playwright/test';
import { seedAdminLogin } from './utils/seedAuth';

const MOCK_SESSION = {
  id: 'session-1',
  agentId: 'agent-1',
  modelId: 'gpt-4',
  title: '测试会话',
  systemPrompt: 'You are a helpful assistant',
  parentSessionId: undefined,
  isChild: false,
  createTime: '2026-07-11T03:00:00Z',
  updateTime: '2026-07-11T03:30:00Z',
};

const MOCK_MESSAGES = [
  { id: 'msg-1', sessionId: 'session-1', role: 'user', content: '你好', sequenceNum: 1, createTime: '2026-07-11T03:01:00Z' },
  { id: 'msg-2', sessionId: 'session-1', role: 'assistant', content: '你好！有什么可以帮助你的？', reasoning: '思考中...', sequenceNum: 2, createTime: '2026-07-11T03:01:05Z' },
];

const MOCK_CHILD_SESSIONS = [
  { id: 'child-1', agentId: 'agent-1', modelId: 'gpt-4', title: '子会话1', isChild: true, parentSessionId: 'session-1', createTime: '2026-07-11T03:10:00Z', updateTime: '2026-07-11T03:20:00Z' },
  { id: 'child-2', agentId: 'agent-1', modelId: 'gpt-4', title: '子会话2', isChild: true, parentSessionId: 'session-1', createTime: '2026-07-11T03:15:00Z', updateTime: '2026-07-11T03:25:00Z' },
];

const MOCK_CHILD_WITHOUT_TITLE = [
  { id: 'child-3', agentId: 'agent-1', modelId: 'gpt-4', title: '', isChild: true, parentSessionId: 'session-1', createTime: '2026-07-11T03:10:00Z', updateTime: '2026-07-11T03:20:00Z' },
];

const MOCK_CHILD_MESSAGES = [
  { id: 'cmsg-1', sessionId: 'child-1', role: 'user', content: '子会话问题', sequenceNum: 1, createTime: '2026-07-11T03:11:00Z' },
  { id: 'cmsg-2', sessionId: 'child-1', role: 'assistant', content: '子会话回答', sequenceNum: 2, createTime: '2026-07-11T03:11:05Z' },
];

async function setupMocks(page: Page, childSessions = MOCK_CHILD_SESSIONS, childMessages = MOCK_CHILD_MESSAGES) {
  await page.route('**/api/sessions/session-1', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ success: true, data: MOCK_SESSION }) });
  });
  await page.route('**/api/sessions/session-1/messages', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ success: true, data: MOCK_MESSAGES }) });
  });
  await page.route('**/api/sessions/session-1/children', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ success: true, data: childSessions }) });
  });
  // 子会话无下一级：计数标签按需获取其子列表为空（验证不显示计数）
  await page.route('**/api/sessions/child-1/children', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ success: true, data: [] }) });
  });
  await page.route('**/api/sessions/child-1/messages', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ success: true, data: childMessages }) });
  });
  await page.route('**/api/models*', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ success: true, data: [] }) });
  });
}

test.beforeEach(async ({ page }) => {
  await seedAdminLogin(page);
});

test.describe('AgentChat 路径式导航', () => {
  test('进入页面显示「主会话」路径标签与其后的子会话计数标签', async ({ page }) => {
    await setupMocks(page);
    await page.goto('/sessions/session-1/chat');
    await page.waitForSelector('.ant-tabs');

    // 计数标签作为路径项紧跟主会话标签之后：[主会话][子会话 2]
    await expect(page.locator('.ant-tabs-tab')).toHaveCount(2);
    await expect(page.locator('.ant-tabs-tab').first()).toHaveText('主会话');
    await expect(page.locator('.agent-chat-count-tab')).toHaveText('子会话 2');
    await expect(page.locator('.ant-tabs-tab').nth(1).locator('.agent-chat-count-tab')).toHaveText('子会话 2');
  });

  test('子会话无 title 时选中后路径标签显示子会话 id', async ({ page }) => {
    await setupMocks(page, MOCK_CHILD_WITHOUT_TITLE);
    await page.goto('/sessions/session-1/chat');
    await page.waitForSelector('.ant-tabs');

    await expect(page.locator('.agent-chat-count-tab')).toHaveText('子会话 1');
    await page.locator('.agent-chat-count-tab').click();
    await page.locator('.ant-dropdown-menu-item', { hasText: 'child-3' }).click();
    await page.waitForTimeout(300);
    // 路径 [主会话][child-3]，child-3 为末位路径项（选中后父层级计数标签隐藏）
    await expect(page.locator('.ant-tabs-tab')).toHaveCount(2);
    await expect(page.locator('.ant-tabs-tab').nth(1)).toHaveText('child-3');
  });

  test('无子会话时不显示计数标签，仅「主会话」一个路径标签', async ({ page }) => {
    await setupMocks(page, []);
    await page.goto('/sessions/session-1/chat');
    await page.waitForSelector('.ant-tabs');

    await expect(page.locator('.ant-tabs-tab')).toHaveCount(1);
    await expect(page.locator('.ant-tabs-tab').first()).toHaveText('主会话');
    await expect(page.locator('.agent-chat-count-tab')).toHaveCount(0);
  });

  test('点击计数标签展开下拉选择子会话，路径变为 [主会话][子会话] 并展示子会话消息', async ({ page }) => {
    await setupMocks(page);
    await page.goto('/sessions/session-1/chat');
    await page.waitForSelector('.ant-tabs');

    await page.locator('.agent-chat-count-tab').click();
    await expect(page.locator('.ant-dropdown-menu-item')).toHaveCount(2);
    await page.locator('.ant-dropdown-menu-item', { hasText: '子会话1' }).click();
    await page.waitForTimeout(500);

    // 路径 [主会话][子会话1]，子会话1 为末位路径项（选中后父层级计数标签隐藏）
    await expect(page.locator('.ant-tabs-tab')).toHaveCount(2);
    await expect(page.locator('.ant-tabs-tab').nth(1)).toHaveText('子会话1');
    await expect(page.locator('text=子会话问题')).toBeVisible();
    await expect(page.locator('text=子会话回答')).toBeVisible();
  });

  test('选中子会话后：父层级计数标签隐藏，无子级的子会话后不显示计数标签', async ({ page }) => {
    await setupMocks(page);
    await page.goto('/sessions/session-1/chat');
    await page.waitForSelector('.ant-tabs');

    await page.locator('.agent-chat-count-tab').click();
    await page.locator('.ant-dropdown-menu-item', { hasText: '子会话1' }).click();
    await page.waitForTimeout(500);

    // 路径 [主会话][子会话1]：主会话不再是末位层级故计数标签隐藏，子会话1 无子级故其后也无计数标签
    await expect(page.locator('.ant-tabs-tab')).toHaveCount(2);
    await expect(page.locator('.agent-chat-count-tab')).toHaveCount(0);
    await expect(page.locator('.ant-tabs-tab').last()).toHaveText('子会话1');
  });

  test('点击主会话恢复计数显示并返回主会话视图', async ({ page }) => {
    await setupMocks(page);
    await page.goto('/sessions/session-1/chat');
    await page.waitForSelector('.ant-tabs');

    await page.locator('.agent-chat-count-tab').click();
    await page.locator('.ant-dropdown-menu-item', { hasText: '子会话1' }).click();
    await page.waitForTimeout(300);

    await page.locator('.ant-tabs-tab', { hasText: '主会话' }).click();
    await page.waitForTimeout(300);

    // 回到 [主会话][子会话 2]（主会话回到末位层级，计数标签恢复显示）
    await expect(page.locator('.ant-tabs-tab')).toHaveCount(2);
    await expect(page.locator('.ant-tabs-tab').first()).toHaveText('主会话');
    await expect(page.locator('.agent-chat-count-tab')).toHaveText('子会话 2');
  });

  test('子会话视图为只读：无输入框、模型选择、思考开关及发送/回滚按钮', async ({ page }) => {
    await setupMocks(page);
    await page.goto('/sessions/session-1/chat');
    await page.waitForSelector('.ant-tabs');

    await page.locator('.agent-chat-count-tab').click();
    await page.locator('.ant-dropdown-menu-item', { hasText: '子会话1' }).click();
    await page.waitForTimeout(500);

    await expect(page.getByPlaceholder('输入消息，Enter 发送，Shift+Enter 换行')).not.toBeVisible();
    await expect(page.locator('.ant-select')).not.toBeVisible();
    await expect(page.locator('.ant-switch')).not.toBeVisible();
    await expect(page.getByRole('button', { name: '发送' })).not.toBeVisible();
    await expect(page.getByRole('button', { name: '回滚' })).not.toBeVisible();
  });
});
