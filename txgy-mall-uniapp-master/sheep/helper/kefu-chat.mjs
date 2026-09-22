const positive = (value) => Number.isSafeInteger(value) && value > 0;
export function chatTarget(input = {}) {
  const scope = input.scope,
    shopId = Number(input.shopId);
  if (!((scope === 'SHOP' && positive(shopId)) || (scope === 'PLATFORM' && shopId === 0)))
    throw new Error('客服范围不明确，请从商品或订单重新进入');
  let resource;
  if (input.resourceType != null || input.resourceId != null) {
    const id = Number(input.resourceId);
    if (!['PRODUCT', 'ORDER'].includes(input.resourceType) || !positive(id))
      throw new Error('咨询内容编号不正确');
    resource = { resourceType: input.resourceType, resourceId: id };
  }
  return { scope, shopId, ...(resource || {}) };
}
export function chatEntry(shopId, resourceType, resourceId) {
  const id = Number(shopId),
    target = positive(id) ? { scope: 'SHOP', shopId: id } : { scope: 'PLATFORM', shopId: 0 };
  if (['PRODUCT', 'ORDER'].includes(resourceType) && positive(Number(resourceId)))
    Object.assign(target, { resourceType, resourceId: Number(resourceId) });
  return target;
}
export function chatData(response) {
  if (response?.code !== 0 || response.data == null) {
    const error = new Error(response?.msg || '客服请求失败，请稍后重试');
    error.code = response?.code;
    throw error;
  }
  return response.data;
}
export function chatEnabled(value) {
  return value?.enabled === true && value.version === 'scoped-chat-v1'
    && value.mode === 'FAQ_HUMAN' && value.attachmentsEnabled === false;
}
export function chatThread(value, target, sessionId, identity) {
  const session = value?.session;
  if (
    !session ||
    typeof session.id !== 'string' ||
    !session.id ||
    (sessionId && session.id !== sessionId) ||
    session.scope !== target.scope ||
    session.shopId !== target.shopId ||
    !Number.isSafeInteger(session.version) ||
    session.version < 0 ||
    !['SELF_SERVICE', 'WAITING_HUMAN', 'HUMAN', 'CLOSED'].includes(value.mode) ||
    (identity &&
      (!positive(Number(identity.tenantId)) ||
        !positive(Number(identity.memberId)) ||
        String(session.tenantId) !== String(identity.tenantId) ||
        String(session.memberId) !== String(identity.memberId)))
  )
    throw new Error('会话范围或状态已变化，请重新进入');
  return value;
}
export function chatPage(value, sessionId, afterId, target) {
  if (
    !value ||
    !Array.isArray(value.items) ||
    value.items.length > 50 ||
    typeof value.hasMore !== 'boolean' ||
    !Number.isSafeInteger(value.nextCursor) ||
    value.nextCursor < afterId
  )
    throw new Error('消息分页响应无效');
  let previous = afterId;
  for (const item of value.items) {
    if (
      !positive(item.id) ||
      item.id <= previous ||
      item.sessionId !== sessionId ||
      !['MEMBER', 'AGENT', 'FAQ', 'AI', 'SYSTEM'].includes(item.senderRole) ||
      typeof item.text !== 'string'
    )
      throw new Error('消息范围或顺序不正确');
    if (
      item.card &&
      (!['PRODUCT', 'ORDER'].includes(item.card.type) ||
        !positive(item.card.resourceId) ||
        !positive(item.card.shopId) ||
        typeof item.card.title !== 'string' ||
        typeof item.card.text !== 'string' ||
        (target?.scope === 'SHOP' && item.card.shopId !== target.shopId))
    )
      throw new Error('咨询卡片范围或格式不正确');
    previous = item.id;
  }
  if (value.nextCursor !== previous || (value.hasMore && !value.items.length))
    throw new Error('消息游标不正确');
  return value;
}
export function chatUuid() {
  if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID();
  // Idempotency key, not a security token. Native runtimes without crypto still get a fresh UUID shape.
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const n = Math.floor(Math.random() * 16);
    return (c === 'x' ? n : (n & 3) | 8).toString(16);
  });
}
export function createChatController(api, current, state, uuid = chatUuid, identity) {
  let generation = 0,
    active = false,
    target = null;
  const reset = () =>
    Object.assign(state, {
      capability: null,
      thread: null,
      messages: [],
      cursor: 0,
      hasMore: false,
      busy: false,
      pending: null,
      error: '',
      ready: false,
      draft: '',
      sentResource: false,
    });
  reset();
  const invalidate = () => {
    generation++;
    active = false;
    state.busy = false;
  };
  const capture = () => {
    const revision = generation;
    return () => active && current() && generation === revision;
  };
  const getThread = (value, sessionId) => chatThread(value, target, sessionId, identity?.());
  const expired = () => {
    invalidate();
    reset();
  };
  async function next(valid) {
    const id = state.thread.session.id,
      cursor = state.cursor;
    const page = chatPage(chatData(await api.messages(id, cursor)), id, cursor, target);
    if (!valid()) return;
    state.messages.push(...page.items);
    state.cursor = page.nextCursor;
    state.hasMore = page.hasMore;
  }
  async function run(operation) {
    if (!active || !current() || state.busy) return false;
    const valid = capture();
    state.busy = true;
    state.error = '';
    try {
      await operation(valid);
      return valid();
    } catch (error) {
      if (valid()) {
        state.error = error?.message || '连接中断，请重试';
        if ([1013016000, 1013016002, 401, 403].includes(error?.code)) {
          state.thread = null;
          state.messages = [];
          state.ready = false;
          state.pending = null;
        }
        if (error?.code === 1013016003) {
          state.pending = null;
          state.ready = false;
        }
      }
      return false;
    } finally {
      if (valid()) state.busy = false;
    }
  }
  async function open(value) {
    invalidate();
    reset();
    target = chatTarget(value);
    active = true;
    return run(async (valid) => {
      const capability = chatData(await api.capability());
      if (!valid()) return;
      state.capability = capability;
      if (!chatEnabled(capability)) return;
      const thread = getThread(
        chatData(await api.open({ scope: target.scope, shopId: target.shopId })),
      );
      if (!valid()) return;
      state.thread = thread;
      state.ready = true;
      await next(valid);
    });
  }
  const refresh = () =>
    run(async (valid) => {
      if (!state.thread) return;
      const thread = getThread(
        chatData(await api.thread(state.thread.session.id)),
        state.thread.session.id,
      );
      if (!valid()) return;
      state.thread = thread;
      state.ready = true;
      await next(valid);
    });
  async function send(resource = false) {
    if (!state.ready || !state.thread || state.thread.mode === 'CLOSED') return false;
    if (state.pending) {
      state.error = '有一条结果未确认的消息，请先重试该消息';
      return false;
    }
    const text = state.draft.trim();
    if (!resource && (!text || text.length > 2000)) {
      state.error = '请输入 1—2000 字消息';
      return false;
    }
    if (resource && (!target.resourceType || state.sentResource)) return false;
    const payload = {
      sessionId: state.thread.session.id,
      version: state.thread.session.version,
      clientMessageId: uuid(),
      ...(resource
        ? { resourceType: target.resourceType, resourceId: target.resourceId }
        : { text }),
    };
    if (state.busy || !active || !current()) return false;
    state.pending = payload;
    return retry();
  }
  const retry = () =>
    run(async (valid) => {
      const pending = state.pending;
      if (!pending || !state.thread) return;
      const result = chatData(await api.send(pending));
      if (!valid()) return;
      state.thread = getThread(result.thread, pending.sessionId);
      state.pending = null;
      if (pending.resourceType) state.sentResource = true;
      else if (state.draft.trim() === pending.text) state.draft = '';
      await next(valid);
    });
  const human = () =>
    run(async (valid) => {
      if (!state.ready || !state.thread || state.pending || state.thread.mode === 'CLOSED') return;
      const { id: sessionId, version } = state.thread.session;
      const thread = getThread(chatData(await api.human({ sessionId, version })), sessionId);
      if (!valid()) return;
      state.thread = thread;
      await next(valid);
    });
  const read = () =>
    run(async (valid) => {
      if (!state.thread || !state.cursor) return;
      const id = state.thread.session.id;
      const thread = getThread(
        chatData(await api.read({ sessionId: id, messageId: state.cursor })),
        id,
      );
      if (valid()) state.thread = thread;
    });
  return {
    open,
    refresh,
    send,
    retry,
    human,
    read,
    suspend: invalidate,
    expire: expired,
    resume() {
      active = true;
    },
    canPoll: () =>
      active && current() && state.ready && !state.busy && !state.hasMore && !state.pending,
  };
}
