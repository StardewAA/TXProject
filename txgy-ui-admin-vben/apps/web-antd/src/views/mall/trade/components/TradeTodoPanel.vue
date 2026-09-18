<script setup lang="ts">
import type { TodoAction, TodoEntry, WorkbenchSummary } from '#/api/mall/trade/workbench';

import { computed, onActivated, onDeactivated, onMounted, onUnmounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';

import { useAccess } from '@vben/access';
import { useAccessStore } from '@vben/stores';

import { Alert, Badge, Button, Drawer, Empty, Pagination, Select, Spin, Tag } from 'ant-design-vue';

import { getWorkbenchPage, getWorkbenchSummary, readWorkbenchEvents } from '#/api/mall/trade/workbench';

import { commonFilters, countText, exactEventIds, nextBannerCount } from './workbench-model';

const props = withDefaults(defineProps<{
  filters?: Record<string, unknown>;
  orderIds?: number[];
  afterSaleIds?: number[];
}>(), { filters: () => ({}), orderIds: () => [], afterSaleIds: () => [] });
const emit = defineEmits<{
  snapshot: [data: WorkbenchSummary | undefined, failed: boolean];
}>();
const access = useAccessStore();
const { hasAccessByCodes } = useAccess();
const router = useRouter();
const summary = ref<WorkbenchSummary>();
const failed = ref(false), loading = ref(false), busy = ref(false);
const newestCount = ref(0), visible = ref(false), pageFailed = ref(false), pageLoading = ref(false);
const entries = ref<TodoEntry[]>([]), total = ref(0), pageNo = ref(1);
const action = ref<TodoAction>(), unreadOnly = ref(false);
const lastUpdate = ref('');
const pageSize = 20;
let active = false, pageEnabled = false, requestVersion = 0, listVersion = 0;
let timer: ReturnType<typeof setTimeout> | undefined;
let controller: AbortController | undefined, listController: AbortController | undefined;
const context = computed(() => [access.tenantId, access.visitTenantId, access.accessToken, access.accessCodes.join('|')]);
const scopeName = computed(() => `当前租户 ${access.visitTenantId ?? access.tenantId ?? '—'} · 平台授权范围全部待办`);
const actionOptions = computed(() => [
  { label: '全部事项', value: '' },
  ...(hasAccessByCodes(['trade:order:query']) ? [{ label: '待发货订单', value: 'DELIVERY' }] : []),
  ...(hasAccessByCodes(['trade:after-sale:refund']) ? [{ label: '待平台退款', value: 'PLATFORM_REFUND' }] : []),
  ...(hasAccessByCodes(['trade:order:update']) ? [{ label: 'ERP 异常待核实', value: 'ERP_EXCEPTION' }] : []),
]);
const names: Partial<Record<TodoAction, string>> = { DELIVERY: '待发货订单', PLATFORM_REFUND: '待平台退款', ERP_EXCEPTION: 'ERP 异常待核实' };

function stop() {
  active = false;
  requestVersion++;
  listVersion++;
  if (timer) clearTimeout(timer);
  timer = undefined;
  controller?.abort();
  listController?.abort();
  loading.value = false;
  pageLoading.value = false;
}

async function refresh() {
  if (!active || document.hidden || !access.accessToken) return;
  if (timer) clearTimeout(timer);
  controller?.abort();
  controller = new AbortController();
  const version = ++requestVersion;
  loading.value = true;
  try {
    const result = await getWorkbenchSummary({
      ...commonFilters(props.filters), afterCursor: summary.value?.cursor,
      orderIds: props.orderIds.slice(0, 100).join(',') || undefined,
      afterSaleIds: props.afterSaleIds.slice(0, 100).join(',') || undefined,
    }, controller.signal);
    if (!active || version !== requestVersion) return;
    newestCount.value = nextBannerCount(summary.value, result, newestCount.value);
    summary.value = result;
    lastUpdate.value = new Date(result.serverTime).toLocaleTimeString();
    failed.value = false;
    emit('snapshot', result, false);
  } catch {
    if (!active || version !== requestVersion) return;
    failed.value = true;
    emit('snapshot', summary.value, true);
  } finally {
    if (active && version === requestVersion) {
      loading.value = false;
      timer = setTimeout(() => { void refresh(); }, Math.min(25, Math.max(5, summary.value?.pollIntervalSeconds || 20)) * 1000);
    }
  }
}

function start() {
  if (active || !pageEnabled) return;
  active = true;
  void refresh();
}
function resume() {
  if (!pageEnabled) return;
  if (document.hidden) stop();
  else if (active) void refresh();
  else start();
}

async function loadPage() {
  listController?.abort();
  listController = new AbortController();
  const version = ++listVersion;
  pageLoading.value = true;
  pageFailed.value = false;
  try {
    const data = await getWorkbenchPage({ pageNo: pageNo.value, pageSize, action: action.value || undefined, unreadOnly: unreadOnly.value || undefined }, listController.signal);
    if (!active || version !== listVersion) return;
    entries.value = data.list;
    total.value = data.total;
  } catch {
    if (active && version === listVersion) pageFailed.value = true;
  } finally {
    if (active && version === listVersion) pageLoading.value = false;
  }
}
function openTodos(kind?: TodoAction, unread = false) {
  action.value = kind;
  unreadOnly.value = unread;
  pageNo.value = 1;
  newestCount.value = 0;
  visible.value = true;
  void loadPage();
}
async function markRead(rows: TodoEntry[]) {
  const ids = exactEventIds(rows);
  if (!ids.length) return;
  const version = requestVersion;
  busy.value = true;
  try {
    await readWorkbenchEvents(ids);
    if (!active || version !== requestVersion) return;
    await Promise.all([refresh(), visible.value ? loadPage() : Promise.resolve()]);
  } finally { busy.value = false; }
}
async function openEntry(entry: TodoEntry) {
  const scope = summary.value?.scopeKey;
  try { await markRead([entry]); } catch { /* 提醒已读失败不改变业务；详情接口仍会再次鉴权。 */ }
  if (!active || scope !== summary.value?.scopeKey) return;
  visible.value = false;
  if (entry.afterSaleId) await router.push({ name: 'TradeAfterSaleDetail', params: { id: entry.afterSaleId } });
  else await router.push({ name: 'TradeOrderDetail', params: { id: entry.orderId } });
}
function rowEvents(id: number | undefined, type: 'AFTER_SALE' | 'ORDER') {
  if (!id || failed.value) return [];
  return (summary.value?.rowEntries || []).filter((entry) => entry.unread && (type === 'ORDER' ? entry.orderId === id : entry.afterSaleId === id));
}
watch(context, () => {
  stop(); summary.value = undefined; entries.value = []; newestCount.value = 0; lastUpdate.value = ''; visible.value = false;
  emit('snapshot', undefined, false);
  start();
});
watch(() => [props.filters, props.orderIds, props.afterSaleIds], () => { void refresh(); }, { deep: true });
onMounted(() => {
  pageEnabled = true;
  document.addEventListener('visibilitychange', resume);
  window.addEventListener('online', resume);
  start();
});
onActivated(() => { pageEnabled = true; start(); });
onDeactivated(() => { pageEnabled = false; stop(); });
onUnmounted(() => {
  pageEnabled = false;
  stop(); document.removeEventListener('visibilitychange', resume); window.removeEventListener('online', resume);
});
defineExpose({ refresh, rowEvents, markRead });
</script>

<template>
  <section class="trade-todo-panel bg-card" aria-label="平台订单待办">
    <div class="trade-todo-heading">
      <strong>{{ scopeName }}</strong>
      <div class="trade-todo-controls">
        <Button :loading="loading" @click="refresh">刷新提醒与统计</Button>
        <Button @click="openTodos(undefined, true)">未读提醒 <Badge :count="failed ? undefined : summary?.unreadCount" :show-zero="!failed" /></Button>
      </div>
    </div>
    <p class="trade-todo-hint">与下方订单筛选独立；未读不等于未处理，查看提醒不会发货、退款或修改订单。</p>
    <Alert v-if="failed" type="warning" show-icon :message="`暂无法更新待办及统计；上次成功 ${lastUpdate || '尚无'}。请重试，不代表没有待办。`" />
    <div class="trade-todo-controls">
      <Button @click="openTodos()">待处理事项 {{ countText(summary?.pendingCount, failed) }}</Button>
      <Button v-access:code="['trade:order:query']" @click="openTodos('DELIVERY')">待发货提醒 {{ countText(summary?.todoCounts?.DELIVERY, failed) }}</Button>
      <Button v-access:code="['trade:after-sale:refund']" @click="openTodos('PLATFORM_REFUND')">待平台退款 {{ countText(summary?.todoCounts?.PLATFORM_REFUND, failed) }}</Button>
      <Button v-access:code="['trade:order:update']" @click="openTodos('ERP_EXCEPTION')">ERP 异常待核实 {{ countText(summary?.todoCounts?.ERP_EXCEPTION, failed) }}</Button>
      <span class="trade-todo-hint">上次更新 {{ lastUpdate || '尚未获取' }} · 默认无声音</span>
    </div>
    <Alert v-if="newestCount > 0 && !failed" type="info" show-icon class="mt-3" :message="`刚刚新增 ${newestCount} 条待处理事项`">
      <template #description>
        <Button type="link" @click="openTodos(undefined, true)">点击查看当前未读待办</Button>
        <Button type="link" @click="newestCount = 0">收起提示（不标记已读）</Button>
      </template>
    </Alert>
    <Drawer v-model:open="visible" title="平台授权范围待处理事项" width="min(760px, 100vw)" @close="listController?.abort()">
      <p class="mb-3">展示完整授权范围的事项，不受订单列表的页签、搜索和历史分页影响。</p>
      <div class="trade-todo-controls mb-3">
        <Select v-model:value="action" :options="actionOptions" class="w-48" placeholder="全部事项" @change="pageNo = 1; loadPage()" />
        <Button @click="unreadOnly = !unreadOnly; pageNo = 1; loadPage()">{{ unreadOnly ? '仅未读' : '包含已读' }}</Button>
        <Button :loading="pageLoading" @click="loadPage">刷新事项</Button>
        <Button :disabled="busy || pageFailed || !exactEventIds(entries).length" @click="markRead(entries)">当前页标记已读</Button>
      </div>
      <Alert v-if="pageFailed" type="warning" message="待办列表暂无法更新，请重试；不将失败当作空列表。" show-icon />
      <Spin :spinning="pageLoading">
        <Empty v-if="!pageFailed && !pageLoading && !entries.length" description="当前范围没有待处理事项" />
        <article v-for="entry in entries" :key="entry.eventId" class="trade-todo-entry">
          <div><Tag v-if="entry.unread" color="orange">新 · 未读</Tag><strong>{{ names[entry.action] || entry.action }}</strong> · {{ entry.no }}</div>
          <p>{{ entry.reason }}</p>
          <p class="trade-todo-hint">店铺 {{ entry.shopId ?? '平台' }} · {{ entry.createdTime }}</p>
          <Button type="link" :disabled="busy || pageFailed" @click="openEntry(entry)">查看并处理</Button>
          <Button v-if="entry.unread" type="link" :disabled="busy || pageFailed" @click="markRead([entry])">标记已读</Button>
        </article>
      </Spin>
      <Pagination v-model:current="pageNo" class="mt-4" :page-size="pageSize" :total="total" :show-size-changer="false" @change="loadPage" />
    </Drawer>
  </section>
</template>

<style scoped>
.trade-todo-panel { margin-bottom: 16px; padding: 18px; border: 1px solid var(--border-color-base, #d9d9d9); border-radius: 8px; }
.trade-todo-heading, .trade-todo-controls { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; }
.trade-todo-heading { justify-content: space-between; }
.trade-todo-hint { margin: 8px 0; font-size: 13px; opacity: .8; }
.trade-todo-entry { border-bottom: 1px solid #e8e8e8; padding: 14px 0; }
@media (max-width: 640px) { .trade-todo-panel { padding: 12px; } .trade-todo-controls { gap: 8px; } }
</style>
