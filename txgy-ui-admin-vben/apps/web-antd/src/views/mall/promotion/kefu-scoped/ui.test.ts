import { createApp, h, nextTick } from 'vue';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const api = vi.hoisted(() => ({
  getChatCapability: vi.fn(), getScopedAgents: vi.fn(), getMyScopedScopes: vi.fn(), grantScopedAgent: vi.fn(),
  getScopedWorkbench: vi.fn(), getAgentThread: vi.fn(), getAgentMessages: vi.fn(), sendAgentMessage: vi.fn(),
  claimScopedSession: vi.fn(), closeScopedSession: vi.fn(), readAgentMessages: vi.fn(), heartbeatAgent: vi.fn(),
  getScopedFaq: vi.fn(), saveScopedFaq: vi.fn(), publishScopedFaq: vi.fn(),
}));
vi.mock('#/api/mall/promotion/kefu-scoped', () => api);
const productApi = vi.hoisted(() => ({ getBusinessScopeShopList: vi.fn() }));
vi.mock('#/api/mall/product/shop', () => productApi);
vi.mock('@vben/stores', async () => {
  const { reactive } = await import('vue');
  const access = reactive({ accessToken: 'local-test-only', tenantId: 1, visitTenantId: null as number | null, accessCodes: [] as string[] });
  const user = reactive({ userInfo: { id: 71 } });
  return { useAccessStore: () => access, useUserStore: () => user };
});
vi.mock('@vben/access', async () => {
  const { useAccessStore } = await import('@vben/stores');
  return { useAccess: () => ({ hasAccessByCodes: (codes: string[]) => codes.some((code) => useAccessStore().accessCodes.includes(code)) }) };
});
vi.mock('@vben/common-ui', async () => {
  const { defineComponent, h } = await import('vue');
  return { Page: defineComponent({ setup: (_, { slots }) => () => h('main', slots.default?.()) }) };
});
vi.mock('ant-design-vue', async () => {
  const { defineComponent, h } = await import('vue');
  const pass = (tag = 'div') => defineComponent({ inheritAttrs: false, setup: (_, { attrs, slots }) => () => h(tag, attrs, slots.default?.()) });
  const input = defineComponent({ props: ['value', 'disabled'], emits: ['update:value'], setup: (props, { emit, attrs }) => () => h('input', { ...attrs, value: props.value, disabled: props.disabled, onInput: (event: Event) => emit('update:value', (event.target as HTMLInputElement).value) }) });
  const number = defineComponent({ props: ['value', 'disabled'], emits: ['update:value'], setup: (props, { emit, attrs }) => () => h('input', { ...attrs, type: 'number', value: props.value, disabled: props.disabled, onInput: (event: Event) => emit('update:value', Number((event.target as HTMLInputElement).value)) }) });
  const select = defineComponent({ props: ['value', 'options', 'disabled'], emits: ['update:value'], setup: (props, { emit, attrs }) => () => h('select', { ...attrs, value: props.value, disabled: props.disabled, onChange: (event: Event) => { const value = (event.target as HTMLSelectElement).value; const option = props.options.find((entry: { value: unknown }) => String(entry.value) === value); emit('update:value', option?.value); } }, props.options.map((entry: { value: string; label: string }) => h('option', { value: entry.value }, entry.label))) });
  return {
    Button: pass('button'), Tag: pass('span'), Badge: pass('span'), Select: select, InputNumber: number,
    Input: Object.assign(input, { TextArea: input }), Switch: pass('button'),
    Alert: defineComponent({ props: ['message'], setup: (props) => () => h('p', { role: 'alert' }, props.message) }),
    Popconfirm: defineComponent({ props: ['disabled'], emits: ['confirm'], setup: (props, { slots, emit }) => () => h('div', { onClick: () => { if (!props.disabled) emit('confirm'); } }, slots.default?.()) }),
  };
});

import { useAccessStore } from '@vben/stores';
import Agents from './agents.vue';
import Workbench from './workbench.vue';
import Faq from './faq.vue';
import { FAQ_PERMISSION, MANAGE_PERMISSION, RECEIVE_PERMISSION } from './model';

let dispose: (() => void) | undefined;
const access = useAccessStore();
const agent = { scope: 'PLATFORM', shopId: 0, actorType: 1, actorId: 22, enabled: true, version: 2 };
const session = { scope: 'PLATFORM', shopId: 0, tenantId: 1, id: 'local-session', memberId: 8, state: 'ACTIVE', agentType: 2, agentId: 71, version: 3, createTime: '', updateTime: '' };
const thread = { session, mode: 'HUMAN', humanRequested: true, onlineAgents: 0, availabilityMessage: '暂无在线承诺', lastMessageId: 0, readMessageId: 0, unreadCount: 0, faq: [], attachmentsEnabled: false };
async function flush() { for (let i = 0; i < 12; i++) { await Promise.resolve(); await nextTick(); } }
function mount(component: object) { const host = document.createElement('div'); document.body.append(host); const app = createApp({ render: () => h(component) }); app.mount(host); dispose = () => { app.unmount(); host.remove(); }; return host; }
function button(host: HTMLElement, label: string) { const result = [...host.querySelectorAll('button')].find((element) => element.textContent?.trim() === label); if (!result) throw new Error(`Button missing ${label}`); result.click(); }
function input(host: HTMLElement, selector: string, value: string) { const element = host.querySelector<HTMLInputElement>(selector); if (!element) throw new Error(`Input missing ${selector}`); element.value = value; element.dispatchEvent(new Event('input', { bubbles: true })); }

beforeEach(() => {
  vi.clearAllMocks();
  access.accessToken = 'local-test-only'; access.tenantId = 1; access.visitTenantId = null; access.accessCodes = [];
  api.getChatCapability.mockResolvedValue({ enabled: true, mode: 'FAQ_HUMAN', attachmentsEnabled: false, version: 'scoped-chat-v1', message: '' });
  api.getScopedAgents.mockResolvedValue([agent]); api.getMyScopedScopes.mockResolvedValue([agent]); api.grantScopedAgent.mockResolvedValue(true);
  api.getScopedWorkbench.mockResolvedValue([thread]); api.getAgentThread.mockResolvedValue(thread);
  api.getAgentMessages.mockResolvedValue({ items: [], nextCursor: 0, hasMore: false });
  api.heartbeatAgent.mockResolvedValue({ online: true, expiresAt: Date.now() + 90_000 });
  api.getScopedFaq.mockResolvedValue({ scope: 'PLATFORM', shopId: 0, version: 0, publishedVersion: 0, entries: [], publishedEntries: [] });
  productApi.getBusinessScopeShopList.mockResolvedValue([{ id: 41, name: '测试企业店铺', status: 0 }]);
});
afterEach(() => { dispose?.(); dispose = undefined; vi.useRealTimers(); });

describe('scoped admin pages fail closed', () => {
  it('never reads grants without manage permission', async () => { const host = mount(Agents); await flush(); expect(host.textContent).toContain('缺少'); expect(api.getChatCapability).not.toHaveBeenCalled(); expect(api.getScopedAgents).not.toHaveBeenCalled(); });
  it('does not confuse receive permission with manage permission', async () => { access.accessCodes = [RECEIVE_PERMISSION]; mount(Agents); await flush(); expect(api.getScopedAgents).not.toHaveBeenCalled(); });
  it('does not access business data when capability is off', async () => { access.accessCodes = [MANAGE_PERMISSION]; api.getChatCapability.mockResolvedValue({ enabled: false }); const host = mount(Agents); await flush(); expect(host.textContent).toContain('尚未开通'); expect(api.getScopedAgents).not.toHaveBeenCalled(); });
  it('blocks cross-tenant admin mode', async () => { access.accessCodes = [MANAGE_PERMISSION]; access.visitTenantId = 2; const host = mount(Agents); await flush(); expect(host.textContent).toContain('不支持跨租户'); expect(api.getScopedAgents).not.toHaveBeenCalled(); });
  it('loads platform grants with shop zero and no actor identity override', async () => { access.accessCodes = [MANAGE_PERMISSION]; const host = mount(Agents); await flush(); expect(api.getScopedAgents.mock.calls[0]?.[0]).toEqual({ scope: 'PLATFORM', shopId: 0, page: 1, size: 20 }); expect(host.textContent).toContain('商城会员 MEMBER'); expect(api.grantScopedAgent).not.toHaveBeenCalled(); });
  it('clears old data immediately when permission is removed', async () => { access.accessCodes = [MANAGE_PERMISSION]; const host = mount(Agents); await flush(); expect(host.textContent).toContain('商城会员 MEMBER'); access.accessCodes = []; await flush(); expect(host.textContent).not.toContain('商城会员 MEMBER'); expect(host.textContent).toContain('缺少'); });
  it('rejects a late former-account grant response', async () => {
    access.accessCodes = [MANAGE_PERMISSION]; let resolve!: (rows: typeof agent[]) => void;
    api.getScopedAgents.mockImplementationOnce(() => new Promise((done) => { resolve = done; }));
    const host = mount(Agents); await flush(); access.accessToken = ''; await flush(); resolve([agent]); await flush();
    expect(host.textContent).not.toContain('商城会员 MEMBER');
  });
  it('does not load FAQ under agent-management permission', async () => { access.accessCodes = [MANAGE_PERMISSION]; mount(Faq); await flush(); expect(api.getScopedFaq).not.toHaveBeenCalled(); });
  it('reads FAQ draft without implicitly saving or publishing', async () => { access.accessCodes = [FAQ_PERMISSION]; const host = mount(Faq); await flush(); expect(host.textContent).toContain('暂无草稿'); expect(api.getScopedFaq).toHaveBeenCalledTimes(1); expect(api.saveScopedFaq).not.toHaveBeenCalled(); expect(api.publishScopedFaq).not.toHaveBeenCalled(); });
  it('reads a scoped queue without going online or claiming automatically', async () => { access.accessCodes = [RECEIVE_PERMISSION]; const host = mount(Workbench); await flush(); expect(host.textContent).toContain('local-session'); expect(api.heartbeatAgent).not.toHaveBeenCalled(); expect(api.claimScopedSession).not.toHaveBeenCalled(); expect(api.getAgentMessages).not.toHaveBeenCalled(); });
  it('automatically loads the signed-in agents authorized shop queue', async () => {
    access.accessCodes = [RECEIVE_PERMISSION];
    const shopGrant = { ...agent, scope: 'SHOP', shopId: 202400203, actorType: 2, actorId: 71 };
    const shopThread = { ...thread, session: { ...session, scope: 'SHOP', shopId: 202400203 } };
    api.getMyScopedScopes.mockResolvedValue([shopGrant]); api.getScopedWorkbench.mockResolvedValue([shopThread]);
    const host = mount(Workbench); await flush();
    expect(api.getScopedWorkbench.mock.calls.at(-1)?.[0]).toMatchObject({ scope: 'SHOP', shopId: 202400203, state: 'WAITING' });
    expect(host.textContent).toContain('店铺 #202400203');
  });
  it('uses only selected session messages and does not mark read automatically', async () => { access.accessCodes = [RECEIVE_PERMISSION]; const host = mount(Workbench); await flush(); button(host, '查看会话'); await flush(); expect(api.getAgentMessages.mock.calls[0]?.[0]).toEqual({ sessionId: 'local-session', afterId: 0, limit: 50 }); expect(api.readAgentMessages).not.toHaveBeenCalled(); });
  it('opens the conversation immediately after starting service', async () => {
    access.accessCodes = [RECEIVE_PERMISSION];
    const waiting = { ...thread, mode: 'WAITING_HUMAN', session: { ...session, state: 'WAITING', agentType: null, agentId: null } };
    const claimed = { ...session, state: 'ACTIVE', agentType: 2, agentId: 71, version: 4 };
    api.getScopedWorkbench.mockResolvedValue([waiting]); api.claimScopedSession.mockResolvedValue(claimed);
    api.getAgentThread.mockResolvedValue({ ...waiting, mode: 'HUMAN', session: claimed });
    const host = mount(Workbench); await flush(); button(host, '开始接待'); await flush();
    expect(api.claimScopedSession.mock.calls[0]?.[0]).toEqual({ sessionId: 'local-session', version: 3 });
    expect(api.getAgentMessages).toHaveBeenCalled();
    expect(host.querySelector('[aria-label="当前接待会话"]')).not.toBeNull();
    expect(host.querySelector('[aria-label="客服回复"]')).not.toBeNull();
  });
  it('does not allow sending as a same-id member assignee', async () => { access.accessCodes = [RECEIVE_PERMISSION]; api.getScopedWorkbench.mockResolvedValue([{ ...thread, session: { ...session, agentType: 1 } }]); const host = mount(Workbench); await flush(); const view = [...host.querySelectorAll('button')].find((element) => element.textContent === '查看会话'); expect(view?.disabled).toBe(true); expect(api.getAgentMessages).not.toHaveBeenCalled(); });
  it('never reads or claims self-service before the member requests human assistance', async () => {
    access.accessCodes = [RECEIVE_PERMISSION]; api.getScopedWorkbench.mockResolvedValue([{ ...thread, humanRequested: false, mode: 'SELF_SERVICE', session: { ...session, state: 'WAITING', agentType: null, agentId: null } }]);
    const host = mount(Workbench); await flush(); const view = [...host.querySelectorAll('button')].find((element) => element.textContent === '查看会话');
    expect(view?.disabled).toBe(true); expect([...host.querySelectorAll('button')].some((element) => element.textContent === '开始接待')).toBe(false);
    expect(api.claimScopedSession).not.toHaveBeenCalled(); expect(api.getAgentMessages).not.toHaveBeenCalled();
  });
  it('requires an explicit confirmed reason before creating a zero-version grant', async () => {
    access.accessCodes = [MANAGE_PERMISSION]; const host = mount(Agents); await flush(); button(host, '新增独立坐席授权'); await flush();
    input(host, '[aria-label="坐席账号编号"]', '123'); input(host, '[placeholder^="记录本次授予"]', '测试独立授权原因'); await flush();
    expect(api.grantScopedAgent).not.toHaveBeenCalled(); button(host, '确认保存授权'); await flush();
    expect(api.grantScopedAgent.mock.calls[0]?.[0]).toEqual({ scope: 'PLATFORM', shopId: 0, actorType: 1, actorId: 123, enabled: true, version: 0, reason: '测试独立授权原因' });
  });
  it('revokes with the exact actor identity and returned version, never zero reset', async () => {
    access.accessCodes = [MANAGE_PERMISSION]; const host = mount(Agents); await flush(); button(host, '撤销授权'); await flush();
    input(host, '[placeholder^="记录本次授予"]', '测试撤权原因'); await flush(); button(host, '确认保存授权'); await flush();
    expect(api.grantScopedAgent.mock.calls[0]?.[0]).toEqual({ ...agent, enabled: false, reason: '测试撤权原因' });
  });
  it('changes to shop scope without querying shop zero', async () => {
    access.accessCodes = [MANAGE_PERMISSION]; const host = mount(Agents); await flush();
    api.getScopedAgents.mockResolvedValue([{ ...agent, scope: 'SHOP', shopId: 41 }]);
    const select = host.querySelector<HTMLSelectElement>('[aria-label="客服范围"]')!; select.value = 'SHOP'; select.dispatchEvent(new Event('change')); await flush();
    expect(api.getScopedAgents.mock.calls.at(-1)?.[0]).toEqual({ scope: 'SHOP', shopId: 41, page: 1, size: 20 });
    expect(api.getScopedAgents.mock.calls.some(([query]) => query.scope === 'SHOP' && query.shopId === 0)).toBe(false);
  });
  it('keeps the new capability check alive while switching between two valid shops', async () => {
    access.accessCodes = [MANAGE_PERMISSION]; const host = mount(Agents); await flush();
    api.getScopedAgents.mockResolvedValue([{ ...agent, scope: 'SHOP', shopId: 41 }]);
    const select = host.querySelector<HTMLSelectElement>('[aria-label="客服范围"]')!; select.value = 'SHOP'; select.dispatchEvent(new Event('change')); await flush();
    let resolve!: (value: object) => void;
    api.getChatCapability.mockImplementationOnce(() => new Promise((done) => { resolve = done; }));
    productApi.getBusinessScopeShopList.mockResolvedValue([{ id: 42, name: '第二测试店铺', status: 0 }]);
    api.getScopedAgents.mockResolvedValue([{ ...agent, scope: 'SHOP', shopId: 42 }]); button(host, '刷新店铺'); await flush();
    expect(host.textContent).not.toContain('商城会员 MEMBER');
    const signal = api.getChatCapability.mock.calls.at(-1)?.[0] as AbortSignal; expect(signal.aborted).toBe(false);
    resolve({ enabled: true, version: 'scoped-chat-v1', mode: 'FAQ_HUMAN', attachmentsEnabled: false }); await flush();
    expect(api.getScopedAgents.mock.calls.at(-1)?.[0]).toEqual({ scope: 'SHOP', shopId: 42, page: 1, size: 20 });
  });
  it('retries a timed-out send with the exact original id and version', async () => {
    access.accessCodes = [RECEIVE_PERMISSION]; api.sendAgentMessage.mockRejectedValueOnce(new Error('timeout')).mockResolvedValueOnce({ thread, messages: [] });
    const host = mount(Workbench); await flush(); button(host, '查看会话'); await flush(); input(host, '[aria-label="客服回复"]', '本地测试纯文本'); await flush(); button(host, '发送回复'); await flush();
    const original = api.sendAgentMessage.mock.calls[0]?.[0]; expect(original).toMatchObject({ sessionId: 'local-session', version: 3, text: '本地测试纯文本' });
    expect(host.querySelector<HTMLInputElement>('[aria-label="客服回复"]')?.disabled).toBe(true);
    button(host, '重试原消息'); await flush(); expect(api.sendAgentMessage.mock.calls[1]?.[0]).toEqual(original);
  });
  it('drops private message content immediately after a revoked send', async () => {
    access.accessCodes = [RECEIVE_PERMISSION]; api.getAgentMessages.mockResolvedValue({ items: [{ id: 1, sessionId: 'local-session', text: 'private-message-test', senderRole: 'MEMBER', createdAt: 1 }], nextCursor: 1, hasMore: false });
    api.sendAgentMessage.mockRejectedValue({ code: 1013016002 }); const host = mount(Workbench); await flush(); button(host, '查看会话'); await flush(); expect(host.textContent).toContain('private-message-test');
    input(host, '[aria-label="客服回复"]', 'reply'); await flush(); button(host, '发送回复'); await flush(); expect(host.textContent).not.toContain('private-message-test'); expect(host.textContent).toContain('授权已撤销');
  });
  it('renders untrusted message markup as plain text', async () => {
    access.accessCodes = [RECEIVE_PERMISSION]; api.getAgentMessages.mockResolvedValue({ items: [{ id: 1, sessionId: 'local-session', text: '<img src=x onerror=alert(1)>', senderRole: 'MEMBER', createdAt: 1 }], nextCursor: 1, hasMore: false });
    const host = mount(Workbench); await flush(); button(host, '查看会话'); await flush(); expect(host.textContent).toContain('<img src=x'); expect(host.querySelector('img')).toBeNull();
  });
  it('displays service-owned cards as inert text without requesting remote images or links', async () => {
    access.accessCodes = [RECEIVE_PERMISSION]; api.getAgentMessages.mockResolvedValue({ items: [{ id: 1, sessionId: 'local-session', text: '商品信息快照', senderRole: 'MEMBER', createdAt: 1,
      card: { type: 'PRODUCT', resourceId: 101, shopId: 41, title: '<b>商品测试</b>', text: '以实时商品为准', amount: 1234, status: 1, picUrl: 'https://remote.invalid/private-image' } }], nextCursor: 1, hasMore: false });
    const host = mount(Workbench); await flush(); button(host, '查看会话'); await flush();
    expect(host.textContent).toContain('<b>商品测试</b>'); expect(host.textContent).toContain('¥12.34'); expect(host.querySelector('img, a[href]')).toBeNull();
  });
  it('keeps FAQ save separate from publish and sends no implicit publication', async () => {
    access.accessCodes = [FAQ_PERMISSION]; api.saveScopedFaq.mockImplementation(async (data) => ({ ...data, version: 1, publishedVersion: 0, publishedEntries: [] }));
    const host = mount(Faq); await flush(); button(host, '添加问题'); await flush(); input(host, '[placeholder="最多 100 字"]', '物流何时更新'); input(host, '[placeholder="最多 1000 字，只展示纯文本"]', '请查看实际物流轨迹，不承诺到达时间'); await flush(); button(host, '保存草稿'); await flush();
    expect(api.saveScopedFaq).toHaveBeenCalledTimes(1); expect(api.publishScopedFaq).not.toHaveBeenCalled(); expect(host.textContent).toContain('尚未对顾客发布');
  });
});
