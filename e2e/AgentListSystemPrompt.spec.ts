import { test, expect, Page } from '@playwright/test';
import { seedAdminLogin } from './utils/seedAuth';

/**
 * 待测功能：智能体配置弹窗（新增/编辑共用）「系统提示词」上限 2000 -> 8000。
 * 覆盖：DOM 属性、8000 字符输入与提交、超长截断与计数器、空值允许、同弹窗其他字段上限、编辑回显。
 */

const AGENT_ID = 'agent-8000';

/** 8000 字符的系统提示词（上限值） */
const PROMPT_8000 = 'A'.repeat(7999) + 'Z';
/** 7999 字符（上限 - 1） */
const PROMPT_7999 = 'B'.repeat(7999);
/** 8001 字符（超长输入源，用于验证浏览器截断） */
const PROMPT_8001 = 'C'.repeat(8001);

const MOCK_MODEL = { id: 'model-1', name: 'LLM模型A', platformType: 'openai', modelName: 'gpt-4' };

const MOCK_AGENT_LONG_PROMPT = {
  id: AGENT_ID,
  name: '长提示词智能体',
  description: 'desc',
  systemPrompt: PROMPT_8000,
  status: 'ENABLED',
  tools: [],
  skills: [],
  recentMessageCount: 10,
  memoryEnabled: false,
  createTime: '2026-08-01T00:00:00',
  updateTime: '2026-08-01T00:00:00',
};

async function setupMocks(page: Page, agents: unknown[] = [MOCK_AGENT_LONG_PROMPT]) {
  await page.route('**/api/knowledge-bases*', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ success: true, data: [] }) });
  });
  await page.route('**/api/models*', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ success: true, data: [MOCK_MODEL] }) });
  });
  await page.route('**/api/tools*', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ success: true, data: [] }) });
  });
  await page.route('**/api/skills*', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ success: true, data: [] }) });
  });
  await page.route('**/api/agents*', async (route) => {
    const method = route.request().method();
    if (method === 'GET') {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ success: true, data: agents }) });
      return;
    }
    if (method === 'POST' || method === 'PUT') {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ success: true, data: MOCK_AGENT_LONG_PROMPT }) });
      return;
    }
    await route.fulfill({ status: 200, contentType: 'application/json', body: '{}' });
  });
}

function promptItem(page: Page) {
  return page.locator('.ant-modal .ant-form-item').filter({ hasText: '系统提示词' });
}
function promptTextarea(page: Page) {
  return promptItem(page).locator('textarea');
}
function promptCount(page: Page) {
  return promptItem(page).locator('.ant-input-data-count');
}
function formItem(page: Page, label: string) {
  return page.locator('.ant-modal .ant-form-item').filter({ hasText: label });
}

async function openAddModal(page: Page) {
  await page.getByRole('button', { name: '新增智能体' }).click();
  await page.locator('.ant-modal').waitFor();
}

test.beforeEach(async ({ page }) => {
  await seedAdminLogin(page);
});

test.describe('智能体管理页 - 系统提示词长度上限 8000', () => {
  test.setTimeout(120000);
  test.beforeEach(async ({ page }) => {
    await setupMocks(page);
    await page.goto('/agents', { waitUntil: 'domcontentloaded', timeout: 120000 });
    await page.waitForSelector('.ant-table', { timeout: 60000 });
  });

  test('新增弹窗：系统提示词 TextArea maxlength=8000 且保留字符计数器', async ({ page }) => {
    await openAddModal(page);
    const ta = promptTextarea(page);
    await expect(ta).toBeVisible();
    await expect(ta).toHaveAttribute('maxlength', '8000');
    await expect(ta).toHaveAttribute('placeholder', '请输入系统提示词');
    await expect(promptCount(page)).toBeVisible();
    await expect(promptCount(page)).toHaveText('0 / 8000');
  });

  test('新增：可输入并提交 8000 字符系统提示词，POST payload 完整携带', async ({ page }) => {
    await openAddModal(page);
    await page.locator('.ant-modal').getByLabel('名称').fill('长提示词新增');

    const ta = promptTextarea(page);
    await ta.fill(PROMPT_8000);
    expect((await ta.inputValue()).length).toBe(8000);
    await expect(promptCount(page)).toHaveText('8000 / 8000');

    const [request] = await Promise.all([
      page.waitForRequest((req) => req.method() === 'POST' && req.url().includes('/api/agents')),
      page.locator('.ant-modal-footer .ant-btn-primary').click(),
    ]);
    const payload = request.postDataJSON();
    expect(payload.name).toBe('长提示词新增');
    expect(typeof payload.systemPrompt).toBe('string');
    expect(payload.systemPrompt.length).toBe(8000);
    expect(payload.systemPrompt).toBe(PROMPT_8000);
  });

  test('达到 8000 字符后不再接受新字符，计数器显示 8000 / 8000', async ({ page }) => {
    await openAddModal(page);
    const ta = promptTextarea(page);
    await ta.fill(PROMPT_8000);
    await ta.click();
    await ta.press('End');
    await ta.pressSequentially('XYZ');
    await expect(ta).toHaveValue(PROMPT_8000);
    await expect(promptCount(page)).toHaveText('8000 / 8000');
  });

  test('超长文本粘贴/写入被截断至 8000（边界 - 超限）', async ({ page }) => {
    await openAddModal(page);
    const ta = promptTextarea(page);
    await ta.click();
    await page.keyboard.insertText(PROMPT_8001);
    const value = await ta.inputValue();
    expect(value.length).toBe(8000);
    await expect(promptCount(page)).toHaveText('8000 / 8000');
  });

  test('边界值 7999 字符可完整保留并提交', async ({ page }) => {
    await openAddModal(page);
    await page.locator('.ant-modal').getByLabel('名称').fill('7999字符');
    const ta = promptTextarea(page);
    await ta.fill(PROMPT_7999);
    await expect(promptCount(page)).toHaveText('7999 / 8000');

    const [request] = await Promise.all([
      page.waitForRequest((req) => req.method() === 'POST' && req.url().includes('/api/agents')),
      page.locator('.ant-modal-footer .ant-btn-primary').click(),
    ]);
    expect(request.postDataJSON().systemPrompt.length).toBe(7999);
  });

  test('系统提示词允许为空，未新增必填校验', async ({ page }) => {
    await openAddModal(page);
    await page.locator('.ant-modal').getByLabel('名称').fill('空提示词');
    await expect(promptTextarea(page)).toHaveValue('');

    const [request] = await Promise.all([
      page.waitForRequest((req) => req.method() === 'POST' && req.url().includes('/api/agents')),
      page.locator('.ant-modal-footer .ant-btn-primary').click(),
    ]);
    const payload = request.postDataJSON();
    expect(payload.systemPrompt ?? '').toBe('');
    await expect(page.locator('.ant-modal')).toBeHidden();
  });

  test('同弹窗其他字段限制不变：名称 maxlength=100 且必填，描述 maxlength=500', async ({ page }) => {
    await openAddModal(page);
    await expect(formItem(page, '名称').locator('input').first()).toHaveAttribute('maxlength', '100');
    await expect(formItem(page, '描述').locator('textarea')).toHaveAttribute('maxlength', '500');

    // 名称必填校验仍生效：清空后提交不应发出请求
    let requested = false;
    page.on('request', (req) => {
      if (req.method() === 'POST' && req.url().includes('/api/agents')) requested = true;
    });
    await page.locator('.ant-modal').getByLabel('名称').fill('');
    await page.locator('.ant-modal-footer .ant-btn-primary').click();
    await expect(page.locator('.ant-modal').getByText('请输入智能体名称')).toBeVisible();
    expect(requested).toBe(false);
    await expect(page.locator('.ant-modal')).toBeVisible();
  });

  test('编辑弹窗完整回显 8000 字系统提示词，提交 PUT payload 一致', async ({ page }) => {
    const row = page.getByRole('row', { name: /长提示词智能体/ });
    await row.getByRole('button', { name: '编辑' }).click();
    await page.locator('.ant-modal').waitFor();

    const ta = promptTextarea(page);
    expect((await ta.inputValue()).length).toBe(8000);
    expect(await ta.inputValue()).toBe(PROMPT_8000);
    await expect(promptCount(page)).toHaveText('8000 / 8000');

    const [request] = await Promise.all([
      page.waitForRequest((req) => req.method() === 'PUT' && req.url().includes(`/api/agents/${AGENT_ID}`)),
      page.locator('.ant-modal-footer .ant-btn-primary').click(),
    ]);
    const payload = request.postDataJSON();
    expect(payload.systemPrompt.length).toBe(8000);
    expect(payload.systemPrompt).toBe(PROMPT_8000);
  });

  test('异常路径：编辑弹窗内清空系统提示词后可正常提交（无校验阻塞）', async ({ page }) => {
    const row = page.getByRole('row', { name: /长提示词智能体/ });
    await row.getByRole('button', { name: '编辑' }).click();
    await page.locator('.ant-modal').waitFor();

    const ta = promptTextarea(page);
    await ta.fill('');
    await expect(promptCount(page)).toHaveText('0 / 8000');

    const [request] = await Promise.all([
      page.waitForRequest((req) => req.method() === 'PUT' && req.url().includes(`/api/agents/${AGENT_ID}`)),
      page.locator('.ant-modal-footer .ant-btn-primary').click(),
    ]);
    expect(request.postDataJSON().systemPrompt ?? '').toBe('');
  });
});