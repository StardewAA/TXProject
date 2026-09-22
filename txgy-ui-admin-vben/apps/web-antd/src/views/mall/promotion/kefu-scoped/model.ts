import type { AgentGrant, ChatMessage, FaqEntry, KefuScope, ScopedSession, ScopeQuery } from '../../../../api/mall/promotion/kefu-scoped';

export const MANAGE_PERMISSION = 'promotion:kefu-agent:manage';
export const RECEIVE_PERMISSION = 'promotion:kefu-agent:receive';
export const FAQ_PERMISSION = 'promotion:kefu-faq:manage';
export const stateNames = { WAITING: '待接待', ACTIVE: '接待中', CLOSED: '已结束' };
export const roleNames = { MEMBER: '顾客', AGENT: '人工坐席', FAQ: '常见问题', AI: 'AI 客服', SYSTEM: '系统' };
export const actorName = (type: number) => type === 1 ? '商城会员 MEMBER' : type === 2 ? '后台管理员 ADMIN' : '未知身份';
export const positiveId = (value: unknown) => typeof value === 'number' && Number.isSafeInteger(value) && value > 0;
export const validVersion = (value: unknown) => typeof value === 'number' && Number.isSafeInteger(value) && value >= 0;
export function scopeQuery(scope: KefuScope, shopId?: number): ScopeQuery | undefined {
  if (scope === 'PLATFORM') return { scope, shopId: 0 };
  return scope === 'SHOP' && positiveId(shopId) ? { scope, shopId: shopId! } : undefined;
}
export function sameScope(value: ScopeQuery, query: ScopeQuery) { return value.scope === query.scope && value.shopId === query.shopId; }
export function validateGrant(value: AgentGrant): string {
  if (!scopeQuery(value.scope, value.shopId) || (value.scope === 'PLATFORM' && value.shopId !== 0)) return '请选择合法客服范围';
  if (![1, 2].includes(value.actorType) || !positiveId(value.actorId)) return '请选择账号类型，并输入正整数账号编号';
  if (!validVersion(value.version)) return '授权版本无效，请重新读取列表';
  if (typeof value.enabled !== 'boolean') return '请选择授权状态';
  if (!value.reason.trim() || value.reason.trim().length > 500) return '请填写 1–500 字授权变更原因';
  return '';
}
export function canClaim(session: ScopedSession) { return session.state === 'WAITING' && validVersion(session.version); }
export function ownsActive(session: ScopedSession, adminId: unknown) {
  return session.state === 'ACTIVE' && session.agentType === 2 && positiveId(adminId)
    && session.agentId === adminId && validVersion(session.version);
}
export function canReadSession(session: ScopedSession, adminId: unknown) {
  return session.state === 'WAITING' || (session.agentType === 2 && positiveId(adminId) && session.agentId === adminId);
}
export function kefuErrorCode(error: unknown): number | undefined {
  const value = error as { code?: number; data?: { code?: number }; response?: { data?: { code?: number } } } | null;
  return value?.data?.code ?? value?.response?.data?.code ?? value?.code;
}
export function kefuError(error: unknown): string {
  const code = kefuErrorCode(error);
  if (code === 1013016000) return '店铺范围客服尚未开通，请等待受控启用；不会自动切换到旧客服。';
  if (code === 1013016001) return '填写内容不符合要求，请检查范围、账号或消息内容。';
  if (code === 1013016002 || code === 403) return '无权访问该范围，或账号／店铺已停用、坐席授权已撤销，请重新核对授权。';
  if (code === 1013016003) return '状态或版本已变化，请刷新后重新确认；本次操作不会强行覆盖最新状态。';
  if (code === 401) return '登录已失效，请重新登录。';
  return '暂时无法完成请求，请重试。若是发送或授权超时，请先刷新核实结果。';
}
export function validateFaq(entries: FaqEntry[]): string {
  if (entries.length > 30) return '一个范围最多 30 条常见问题';
  const ids = new Set<string>();
  for (const entry of entries) {
    if (!/^[\w-]{1,40}$/.test(entry.id) || ids.has(entry.id)) return '问题标识须为唯一的 1–40 位字母、数字、下划线或连字符';
    ids.add(entry.id);
    if (!entry.question.trim() || entry.question.trim().length > 100) return '问题须为 1–100 字';
    if (!entry.answer.trim() || entry.answer.trim().length > 1000) return '答案须为 1–1000 字';
    if (entry.keywords.length > 8 || entry.keywords.some((keyword) => keyword.trim().length < 2 || keyword.trim().length > 30)) return '每题最多 8 个关键词，每个 2–30 字';
  }
  return '';
}
export function mergeMessages(current: ChatMessage[], incoming: ChatMessage[], sessionId: string): ChatMessage[] {
  const rows = new Map<number, ChatMessage>();
  for (const item of [...current, ...incoming]) if (item.sessionId === sessionId && positiveId(item.id)) rows.set(item.id, item);
  return [...rows.values()].sort((left, right) => left.id - right.id);
}
/** Abort is best effort; the epoch also rejects a late response after account/scope changes. */
export function createRequestFence() {
  let epoch = 0;
  let controller: AbortController | undefined;
  return {
    invalidate() { epoch++; controller?.abort(); controller = undefined; },
    begin() {
      controller?.abort(); controller = new AbortController();
      const version = ++epoch;
      const signal = controller.signal;
      return { signal, current: () => epoch === version && !signal.aborted };
    },
  };
}
