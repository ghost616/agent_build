import { test, expect, Page } from '@playwright/test';
import { seedAdminLogin } from './utils/seedAuth';

test.describe.configure({ timeout: 120000 });

/**
 * 针对「userInput 前端支持」变更的 E2E 测试：
 * 1. 会话历史用户消息列表请求携带 userInput=true 查询参数
 * 2. 对话详情表格角色列：userInput=false 的 user 消息显示「传递」标签，userInput=true 显示「用户」
 * 3. 消息流 Modal 中 userInput=false 的 user 消息同样显示「传递」标签
 */

const MOCK_USER_INPUT_MESSAGES = [
  {
    id: 'ui-1',
    sessionId: 'session-1',
    conversationId: 'conv-1',
    role: 'user',
    userInput: true,
    content: '用户真实输入',
    sequenceNum: 1,
    createTime: '2026-07-11T03:01:00Z',
  },
  {
    id: 'ui-2',
    sessionId: 'session-1',
    conversationId: 'conv-1',
    role: 'user',
    userInput: false,
    content: '会话间传递消息',
    sequenceNum: 2,
    createTime: '2026-07-11T03:02:00Z',
  },
];

const MOCK_DETAIL_LABEL_MESSAGES = [
  {
    id: 'dl-1',
    sessionId: 'session-1',
    conversationId: 'conv-1',
    role: 'user',
    userInput: false,
    content: '传递的消息',
    sequenceNum: 1,
    createTime: '2026-07-11T03:01:00Z',
  },
  {
    id: 'dl-2',
    sessionId: 'session-1',
    conversationId: 'conv-1',
    role: 'user',
    userInput: true,
    content: '真实输入',
    sequenceNum: 2,
    createTime: '2026-07-11T03:02:00Z',
  },
  {
    id: 'dl-3',
    sessionId: 'session-1',
    conversationId: 'conv-1',
    role: 'assistant',
    content: '助手回复',
    sequenceNum: 3,
    createTime: '2026-07-11T03:03:00Z',
  },
];

test.beforeEach(async ({ page }) => {
  await seedAdminLogin(page);
});

test.describe('会话历史用户消息列表 userInput 过滤', () => {
  test('fetchUserMessages 请求 /sessions/{id}/messages 应携带 userInput=true 查询参数', async ({ page }) => {
    let capturedUrl = '';
    await page.route('**/api/sessions/session-1/messages', async (route) => {
      capturedUrl = route.request().url();
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ success: true, data: MOCK_USER_INPUT_MESSAGES }),
      });
    });

    await page.goto('/conversations/session-1');
    await page.waitForSelector('.ant-table');

    expect(capturedUrl).toContain('/api/sessions/session-1/messages');
    expect(capturedUrl).toContain('userInput=true');
    await expect(page.locator('text=用户真实输入')).toBeVisible();
  });

  test('用户消息列表应保留 role 为 user 的过滤兜底', async ({ page }) => {
    const mixed = [
      ...MOCK_USER_INPUT_MESSAGES,
      {
        id: 'ui-3',
        sessionId: 'session-1',
        conversationId: 'conv-1',
        role: 'assistant',
        content: '助手不应出现在用户列表',
        sequenceNum: 3,
        createTime: '2026-07-11T03:03:00Z',
      },
    ];
    await page.route('**/api/sessions/session-1/messages', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ success: true, data: mixed }),
      });
    });

    await page.goto('/conversations/session-1');
    await page.waitForSelector('.ant-table');

    await expect(page.locator('text=用户真实输入')).toBeVisible();
    await expect(page.locator('text=会话间传递消息')).toBeVisible();
    await expect(page.locator('text=助手不应出现在用户列表')).toHaveCount(0);
  });
});

test.describe('对话详情「传递」标签', () => {
  test('角色列 userInput=false 的 user 消息显示「传递」，userInput=true 显示「用户」', async ({ page }) => {
    await page.route('**/api/conversations/conv-1/messages', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ success: true, data: MOCK_DETAIL_LABEL_MESSAGES }),
      });
    });

    await page.goto('/conversations/conv-1/detail');
    await page.waitForSelector('.ant-table');

    await expect(page.locator('text=传递')).toBeVisible();
    await expect(page.locator('text=真实输入')).toBeVisible();
    const table = page.locator('.ant-table');
    await expect(table.locator('text=传递')).toHaveCount(1);
    await expect(table.locator('text=用户')).toHaveCount(1);
  });

  test('消息流 Modal 中 userInput=false 的 user 消息同样显示「传递」标签', async ({ page }) => {
    await page.route('**/api/conversations/conv-1/messages', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ success: true, data: MOCK_DETAIL_LABEL_MESSAGES }),
      });
    });

    await page.goto('/conversations/conv-1/detail');
    await page.waitForSelector('.ant-table');

    await page.locator('.ant-table-tbody tr').first().click();
    await expect(page.locator('.ant-modal-content')).toBeVisible();
    await expect(page.locator('.ant-modal-content')).toContainText('传递');
    await expect(page.locator('.ant-modal-content')).toContainText('传递的消息');
    await expect(page.locator('.ant-modal-content')).toContainText('真实输入');
  });
});


