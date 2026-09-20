<script setup lang="ts">
import type { ChatMessage, ChatSend, ChatThread, KefuState } from '#/api/mall/promotion/kefu-scoped';
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue';
import { Page } from '@vben/common-ui';
import { useUserStore } from '@vben/stores';
import { Alert, Badge, Button, Input, InputNumber, Popconfirm, Select, Tag } from 'ant-design-vue';
import { claimScopedSession, closeScopedSession, getAgentMessages, getAgentThread, getScopedWorkbench,
  heartbeatAgent, readAgentMessages, sendAgentMessage } from '#/api/mall/promotion/kefu-scoped';
import { actorName, canClaim, canReadSession, kefuError, kefuErrorCode, mergeMessages, ownsActive, RECEIVE_PERMISSION, roleNames, sameScope, stateNames } from './model';
import { useScopedView } from './use-scoped-view';

defineOptions({ name: 'MallScopedKefuWorkbench' });
const user = useUserStore();
const adminId = computed(() => Number(user.userInfo?.id));
const rows = ref<ChatThread[]>([]), page = ref(1), state = ref<KefuState>('WAITING');
const loading = ref(false), loaded = ref(false), busy = ref(false), messageLoading = ref(false);
const error = ref(''), detailError = ref(''), draft = ref('');
const selected = ref<ChatThread>(), messages = ref<ChatMessage[]>([]), cursor = ref(0), hasMore = ref(false);
const conversation = ref<HTMLElement>();
const pending = ref<ChatSend>();
const wantOnline = ref(false), expiresAt = ref(0), now = ref(Date.now()), heartbeatBusy = ref(false);
let timer: ReturnType<typeof setTimeout> | undefined;
let lastQueueRefresh = 0;
function stopTimer() { if (timer) clearTimeout(timer); timer = undefined; }
function clearDetail() { selected.value = undefined; messages.value = []; cursor.value = 0; hasMore.value = false; draft.value = ''; pending.value = undefined; detailError.value = ''; messageLoading.value = false; }
function clear() {
  stopTimer(); rows.value = []; page.value = 1; loading.value = false; loaded.value = false; busy.value = false;
  error.value = ''; wantOnline.value = false; expiresAt.value = 0; heartbeatBusy.value = false; clearDetail();
}
const context = useScopedView(RECEIVE_PERMISSION, clear);
const listFence = context.fence(), detailFence = context.fence(), actionFence = context.fence(), sendFence = context.fence(), heartbeatFence = context.fence();
const online = computed(() => wantOnline.value && expiresAt.value > now.value);
const canSend = computed(() => context.ready.value && selected.value?.humanRequested === true && ownsActive(selected.value.session, adminId.value));
const scopeOptions = [{ label: '平台客服（仅本租户）', value: 'PLATFORM' }, { label: '店铺客服', value: 'SHOP' }];
const stateOptions = Object.entries(stateNames).map(([value, label]) => ({ value, label }));
const pageSize = 20;

function safeThread(thread: ChatThread, id?: string) {
  return thread.humanRequested === true && context.query.value && sameScope(thread.session, context.query.value) && (!id || thread.session.id === id)
    && canReadSession(thread.session, adminId.value);
}
async function focusConversation() {
  await nextTick();
  conversation.value?.scrollIntoView?.({ behavior: 'smooth', block: 'start' });
  conversation.value?.querySelector<HTMLTextAreaElement>('textarea, [aria-label="客服回复"]')?.focus();
}
async function load() {
  if (!context.ready.value || !context.query.value) return;
  const query = { ...context.query.value };
  const request = listFence.begin();
  loading.value = true; loaded.value = false; error.value = '';
  try {
    const data = await getScopedWorkbench({ ...query, state: state.value, page: page.value, size: pageSize }, request.signal);
    if (!request.current()) return;
    if (!Array.isArray(data) || data.some((row) => !sameScope(row.session, query))) throw new Error('Invalid scope');
    rows.value = data; loaded.value = true; lastQueueRefresh = Date.now();
  } catch (cause) { if (request.current()) { rows.value = []; error.value = kefuError(cause); detailFence.invalidate(); sendFence.invalidate(); clearDetail(); } }
  finally { if (request.current()) loading.value = false; }
}
async function show(thread: ChatThread) {
  if (!context.ready.value || !safeThread(thread) || busy.value) return;
  detailFence.invalidate(); sendFence.invalidate(); clearDetail(); selected.value = thread;
  await loadMessages();
  await focusConversation();
}
async function loadMessages() {
  if (!context.ready.value || !selected.value || document.hidden) return;
  const sessionId = selected.value.session.id;
  const request = detailFence.begin();
  messageLoading.value = true; detailError.value = '';
  try {
    const thread = await getAgentThread(sessionId, request.signal);
    if (!request.current()) return;
    if (!safeThread(thread, sessionId)) throw new Error('Permission changed');
    const data = await getAgentMessages({ sessionId, afterId: cursor.value, limit: 50 }, request.signal);
    if (!request.current()) return;
    if (!Number.isSafeInteger(data.nextCursor) || data.nextCursor < cursor.value || data.items.some((row) => row.sessionId !== sessionId)) throw new Error('Invalid page');
    selected.value = thread; messages.value = mergeMessages(messages.value, data.items, sessionId);
    cursor.value = data.nextCursor; hasMore.value = data.hasMore;
  } catch (cause) {
    if (request.current()) { sendFence.invalidate(); clearDetail(); detailError.value = kefuError(cause); }
  } finally { if (request.current()) messageLoading.value = false; }
}
async function changeSession(thread: ChatThread, action: 'claim' | 'close') {
  const session = thread.session;
  if (thread.humanRequested !== true) return;
  if (!context.ready.value || busy.value || !context.query.value || !sameScope(session, context.query.value)) return;
  if (action === 'claim' ? !canClaim(session) : !ownsActive(session, adminId.value)) return;
  const request = actionFence.begin(); busy.value = true; error.value = '';
  try {
    const result = await (action === 'claim' ? claimScopedSession : closeScopedSession)({ sessionId: session.id, version: session.version }, request.signal);
    if (!request.current()) return;
    if (action === 'claim') {
      detailFence.invalidate(); sendFence.invalidate(); clearDetail(); selected.value = { ...thread, session: result };
      await loadMessages();
    } else if (selected.value?.session.id === result.id) {
      selected.value = { ...selected.value, session: result }; await loadMessages();
    }
    await load();
    if (action === 'claim' && selected.value?.session.id === result.id) await focusConversation();
  } catch (cause) { if (request.current()) { error.value = kefuError(cause); detailFence.invalidate(); clearDetail(); } }
  finally { if (request.current()) busy.value = false; }
}
async function send() {
  if (!canSend.value || !selected.value || busy.value || !draft.value.trim() || draft.value.trim().length > 2000) return;
  const session = selected.value.session;
  // A network retry reuses the exact same id, text and version. Editing requires explicit cancel below.
  if (!pending.value) pending.value = { sessionId: session.id, version: session.version, clientMessageId: crypto.randomUUID(), text: draft.value.trim() };
  if (pending.value.sessionId !== session.id || pending.value.text !== draft.value.trim()) return;
  const request = sendFence.begin(); busy.value = true; detailError.value = '';
  try {
    const data = await sendAgentMessage({ ...pending.value }, request.signal);
    if (!request.current()) return;
    if (!safeThread(data.thread, session.id)) throw new Error('Permission changed');
    selected.value = data.thread; messages.value = mergeMessages(messages.value, data.messages, session.id);
    draft.value = ''; pending.value = undefined;
    await loadMessages();
  } catch (cause) {
    if (request.current()) {
      if ([401, 403, 1013016000, 1013016002].includes(kefuErrorCode(cause) ?? 0)) {
        detailFence.invalidate(); clearDetail(); rows.value = []; loaded.value = false; wantOnline.value = false; expiresAt.value = 0;
        detailError.value = kefuError(cause);
      } else detailError.value = `${kefuError(cause)} 发送结果不明确时，保留本次请求并使用“重试原消息”，避免重复发送。`;
    }
  } finally { if (request.current()) busy.value = false; }
}
async function markRead() {
  if (!context.ready.value || !selected.value || busy.value || !messages.value.length || document.hidden) return;
  const sessionId = selected.value.session.id;
  const messageId = messages.value.at(-1)!.id;
  const request = actionFence.begin(); busy.value = true;
  try {
    const data = await readAgentMessages({ sessionId, messageId }, request.signal);
    if (request.current() && safeThread(data, sessionId)) selected.value = data;
  } catch (cause) { if (request.current()) { clearDetail(); detailError.value = kefuError(cause); } }
  finally { if (request.current()) busy.value = false; }
}
async function heartbeat(enable: boolean) {
  if (!context.ready.value || !context.query.value || heartbeatBusy.value || (enable && document.hidden)) return;
  const request = heartbeatFence.begin(); heartbeatBusy.value = true;
  try {
    const data = await heartbeatAgent({ ...context.query.value, online: enable }, request.signal);
    if (!request.current()) return;
    wantOnline.value = enable && data.online; expiresAt.value = data.online ? data.expiresAt : 0; now.value = Date.now();
  } catch (cause) {
    if (request.current()) { wantOnline.value = false; expiresAt.value = 0; rows.value = []; loaded.value = false; detailFence.invalidate(); sendFence.invalidate(); clearDetail(); error.value = kefuError(cause); }
  } finally { if (request.current()) heartbeatBusy.value = false; }
}
async function tick() {
  stopTimer(); now.value = Date.now();
  if (!context.ready.value) return;
  const scopeKey = context.key.value;
  const current = () => context.ready.value && context.key.value === scopeKey;
  if (!document.hidden) {
    if (wantOnline.value && expiresAt.value - now.value <= 60_000) await heartbeat(true);
    if (current() && selected.value && !messageLoading.value && !busy.value) await loadMessages();
    if (current() && wantOnline.value && Date.now() - lastQueueRefresh >= 15_000 && !loading.value && !busy.value) await load();
  }
  if (current()) timer = setTimeout(() => { void tick(); }, 6000);
}
function visibility() { if (document.hidden && wantOnline.value) { wantOnline.value = false; void heartbeat(false); } else if (!document.hidden) void tick(); }
function turn(value: number) { if (loading.value || busy.value) return; page.value = value; void load(); }
function discardPending() { if (!busy.value) { pending.value = undefined; draft.value = ''; detailError.value = '已取消本地重试；不会撤回可能已送达的消息，请先刷新记录核对。'; } }
watch(context.ready, (ready) => { if (ready) { void load(); void tick(); } });
watch(state, () => { page.value = 1; rows.value = []; loaded.value = false; void load(); });
watch(adminId, () => { detailFence.invalidate(); sendFence.invalidate(); clearDetail(); });
onMounted(() => document.addEventListener('visibilitychange', visibility));
onUnmounted(() => { stopTimer(); document.removeEventListener('visibilitychange', visibility); });
</script>

<template>
  <Page title="店铺范围客服 · 接待工作台" description="独立范围授权 + 本人开始接待后回复。当前首版为固定 FAQ 与人工客服，不是 AI 自动客服。">
    <section class="scoped-panel bg-card">
      <Alert type="info" show-icon message="待接待不代表有人在线；上线状态由服务器 90 秒租约决定。页面隐藏后停止在线续约；离开或断网后最迟随租约到期下线。附件暂不开放。" />
      <div class="scoped-toolbar">
        <strong>{{ context.label.value }}</strong>
        <Select v-model:value="context.scope.value" :options="scopeOptions" :disabled="busy" style="width: 220px" aria-label="客服范围" />
        <InputNumber v-if="context.scope.value === 'SHOP'" v-model:value="context.shopId.value" :min="1" :precision="0" :disabled="busy" placeholder="店铺编号" aria-label="店铺编号" />
        <Button :loading="context.capabilityLoading.value" @click="context.refreshCapability">核实开通状态</Button>
      </div>
      <Alert v-if="context.blocked.value" type="warning" show-icon :message="context.blocked.value" />
      <template v-if="context.ready.value">
        <div class="scoped-toolbar">
          <Select v-model:value="state" :options="stateOptions" :disabled="busy" style="width: 150px" aria-label="接待状态" />
          <Button :loading="loading" @click="load">刷新队列</Button>
          <Tag :color="online ? 'green' : 'default'">{{ online ? '在线接待' : '离线／未上线' }}</Tag>
          <Button :loading="heartbeatBusy" :type="online ? 'default' : 'primary'" @click="heartbeat(!online)">{{ online ? '下线' : '上线接待此范围' }}</Button>
          <span class="muted">后台账号 #{{ adminId || '未知' }}；有菜单权限仍需此范围独立坐席授权。</span>
        </div>
        <Alert v-if="error" type="error" show-icon :message="error" />
        <div class="table-wrap">
          <table class="scoped-table"><thead><tr><th>会话</th><th>会员</th><th>状态</th><th>接待客服</th><th>未读</th><th>操作</th></tr></thead><tbody>
            <tr v-for="row in rows" :key="row.session.id">
              <td class="session-id">{{ row.session.id }}</td><td>#{{ row.session.memberId }}</td><td>{{ stateNames[row.session.state] }}</td>
              <td>{{ row.session.agentId ? `${actorName(row.session.agentType || 0)} #${row.session.agentId}` : '暂未接待' }}</td>
              <td><Badge :count="row.unreadCount" :show-zero="true" /></td>
              <td class="scoped-toolbar">
                <Button size="small" :disabled="busy || !row.humanRequested || !canReadSession(row.session, adminId)" @click="show(row)">查看会话</Button>
                <Button v-if="row.humanRequested && canClaim(row.session)" size="small" type="primary" :disabled="busy" @click="changeSession(row, 'claim')">开始接待</Button>
                <Popconfirm v-if="row.humanRequested && ownsActive(row.session, adminId)" title="确认结束本人的接待？会员之后仍可再次转人工。" @confirm="changeSession(row, 'close')"><Button size="small" :disabled="busy">结束</Button></Popconfirm>
              </td>
            </tr>
            <tr v-if="!rows.length"><td colspan="6">{{ loading ? '正在读取…' : loaded ? '当前页没有人工接待会话。' : '未成功读取，不代表没有待接待会话。' }}</td></tr>
          </tbody></table>
        </div>
        <div class="scoped-toolbar"><Button :disabled="page <= 1 || loading || busy" @click="turn(page - 1)">上一页</Button><span>第 {{ page }} 页 · 每页 {{ pageSize }} 条</span><Button :disabled="!loaded || rows.length < pageSize || page >= 10000 || loading || busy" @click="turn(page + 1)">下一页</Button></div>
        <Alert v-if="detailError" type="warning" show-icon :message="detailError" />
        <section v-if="selected" ref="conversation" class="conversation" aria-label="当前接待会话">
          <div class="scoped-toolbar"><h3>会员 #{{ selected.session.memberId }} · {{ stateNames[selected.session.state] }}</h3><span class="muted">版本 {{ selected.session.version }}</span><Button size="small" :loading="messageLoading" :disabled="busy" @click="loadMessages">{{ hasMore ? '继续加载消息' : '刷新消息' }}</Button><Button size="small" :disabled="busy || !messages.length" @click="markRead">标记当前可见消息已读</Button><Button size="small" :disabled="busy" @click="detailFence.invalidate(); sendFence.invalidate(); clearDetail()">收起</Button></div>
          <p class="muted">{{ selected.availabilityMessage }}；消息仅在当前页面内存中保存，不写浏览器本地存储。</p>
          <div class="message-list" aria-label="消息记录" aria-live="polite">
            <article v-for="item in messages" :key="item.id" :class="['chat-message', { own: item.senderRole === 'AGENT' }]">
              <header>{{ roleNames[item.senderRole] }} · {{ new Date(item.createdAt).toLocaleString() }}</header>
              <p>{{ item.text }}</p>
              <section v-if="item.card" class="chat-card" aria-label="顾客发送的业务信息快照">
                <strong>{{ item.card.type === 'ORDER' ? '订单快照' : '商品快照' }} · {{ item.card.title }}</strong>
                <p>店铺 #{{ item.card.shopId }} · {{ item.card.type === 'ORDER' ? '订单' : '商品' }}编号 #{{ item.card.resourceId }}</p>
                <p v-if="Number.isSafeInteger(item.card.amount) && (item.card.amount ?? -1) >= 0">金额快照 ¥{{ ((item.card.amount ?? 0) / 100).toFixed(2) }}</p>
                <p v-if="item.card.status != null">业务状态码 {{ item.card.status }}</p>
                <p>{{ item.card.text }}</p>
                <small>服务端生成的历史快照；不展示收货地址、电话，不自动打开订单、图片或外部链接。</small>
              </section>
            </article>
            <p v-if="!messages.length" class="muted">{{ messageLoading ? '读取消息中…' : '暂无消息。' }}</p>
          </div>
          <Alert v-if="!canSend" type="info" show-icon message="当前管理员尚未开始接待此会话或会话已结束，仅可读取授权范围内的记录。" />
          <template v-if="canSend">
            <Input.TextArea v-model:value="draft" :maxlength="2000" :rows="4" :disabled="busy || !!pending" placeholder="输入纯文本回复（最多 2000 字），不支持附件或 HTML。" aria-label="客服回复" />
            <div class="scoped-toolbar"><Button type="primary" :loading="busy" :disabled="!draft.trim() || draft.trim().length > 2000" @click="send">{{ pending ? '重试原消息' : '发送回复' }}</Button><Popconfirm v-if="pending" title="这不会撤回已送达消息。确认放弃本地重试并清空草稿？" @confirm="discardPending"><Button :disabled="busy">放弃本次重试</Button></Popconfirm></div>
          </template>
        </section>
      </template>
    </section>
  </Page>
</template>

<style scoped>
.scoped-panel{padding:20px;display:grid;gap:16px;border-radius:8px}.scoped-toolbar{display:flex;align-items:center;gap:12px;flex-wrap:wrap}.table-wrap{overflow:auto}.scoped-table{width:100%;border-collapse:collapse;text-align:left}.scoped-table th,.scoped-table td{padding:14px 12px;border-bottom:1px solid #8883}.session-id{max-width:240px;overflow-wrap:anywhere;font-size:12px}.muted{color:#7b8493;font-size:13px}.conversation{border:1px solid #8884;border-radius:8px;padding:20px;display:grid;gap:14px}.conversation h3{font-size:18px;font-weight:600}.message-list{max-height:450px;overflow:auto;display:grid;gap:12px;padding:12px;background:#8881}.chat-message{max-width:85%;padding:12px;border:1px solid #8883;border-radius:8px}.chat-message.own{justify-self:end;background:#1677ff0c}.chat-message header{font-size:12px;color:#7b8493;margin-bottom:6px}.chat-message p{white-space:pre-wrap;overflow-wrap:anywhere}
.chat-card{display:grid;gap:6px;margin-top:10px;padding:12px;border:1px solid #8883;border-radius:6px}.chat-card small{color:#7b8493}
</style>
