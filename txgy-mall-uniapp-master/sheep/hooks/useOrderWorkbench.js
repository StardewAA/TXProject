import { reactive, watch, onBeforeUnmount } from 'vue';
import { onShow, onHide, onUnload } from '@dcloudio/uni-app';
import sheep from '@/sheep';
import { getAccessToken, getAccountTenantId } from '@/sheep/request';
import WorkbenchApi from '@/sheep/api/trade/workbench';
import { createNoticeGate, validateSummary, validEventId, noticeDestination, unreadForOrder } from '@/sheep/helper/order-workbench.mjs';

/** Read progress is exclusively server-side. Polling never reloads business forms/lists. */
export function useOrderWorkbench(getParams = () => ({})) {
  const user = sheep.$store('user');
  const state = reactive({ summary: null, loading: false, error: '', lastSuccess: 0, expanded: false,
    list: [], total: 0, pageNo: 1, pageLoading: false, pageError: '', reading: false });
  const gate = createNoticeGate();
  let shown = false, disposed = false, timer, inFlight, identity = '', bannerCursor;
  let pageGeneration = 0;
  // Order APIs use the signed-in account's tenant even while browsing another
  // tenant's public catalog. Do not suppress the workbench in that situation.
  const scope = () => user.isLogin && user.userInfo?.id && getAccessToken() && getAccountTenantId()
    ? `${getAccountTenantId()}:1:${user.userInfo.id}:${getAccessToken()}` : '';
  const queryKey = () => JSON.stringify(getParams());
  const visible = () => shown && !disposed && (typeof document === 'undefined' || !document.hidden);
  function reset() {
    gate.invalidate(); pageGeneration++; bannerCursor = undefined;
    Object.assign(state, { summary: null, loading: false, error: '', lastSuccess: 0, list: [], total: 0,
      pageNo: 1, pageLoading: false, pageError: '', expanded: false, reading: false });
  }
  function checkScope() {
    const next = scope();
    if (next !== identity) { identity = next; reset(); }
    return next;
  }
  function schedule() {
    clearTimeout(timer);
    if (visible() && checkScope()) timer = setTimeout(() => void refresh(), 20_000);
  }
  async function refresh() {
    if (!visible() || !checkScope()) return false;
    if (inFlight) return inFlight;
    const token = gate.capture(), requestIdentity = identity, filter = queryKey();
    const current = () => visible() && gate.current(token) && scope() === requestIdentity && queryKey() === filter;
    state.loading = true;
    inFlight = (async () => {
      try {
        const response = await WorkbenchApi.summary({ ...getParams(), afterCursor: bannerCursor });
        if (!current()) return false;
        if (response?.code !== 0) throw new Error('工作台暂无法更新');
        const data = validateSummary(response.data, `${requestIdentity.split(':').slice(0, 3).join(':')}:BUYER:0`);
        state.summary = data; state.error = ''; state.lastSuccess = data.serverTime;
        if (bannerCursor === undefined || data.resetRequired) bannerCursor = data.cursor;
        return true;
      } catch {
        if (current()) { state.error = '暂无法更新待办，请刷新重试'; if (!state.lastSuccess) state.summary = null; }
        return false;
      } finally {
        inFlight = undefined;
        if (gate.current(token)) state.loading = false;
        schedule();
      }
    })();
    return inFlight;
  }
  async function loadPage(pageNo = 1) {
    if (!visible() || !checkScope() || state.reading) return false;
    const token = gate.capture(), requestedScope = identity, version = ++pageGeneration;
    state.pageLoading = true; state.pageError = '';
    try {
      const response = await WorkbenchApi.page({ pageNo, pageSize: 10 });
      if (!gate.current(token) || scope() !== requestedScope || version !== pageGeneration || !visible()) return false;
      if (response?.code !== 0 || !Array.isArray(response.data?.list) || !Number.isSafeInteger(response.data?.total)
          || response.data.list.some(e => !noticeDestination(e))) throw new Error('待办列表加载失败');
      state.list = response.data.list; state.total = response.data.total; state.pageNo = pageNo;
      return true;
    } catch { if (gate.current(token) && version === pageGeneration) state.pageError = '待办列表暂无法更新，请重试'; return false; }
    finally { if (gate.current(token) && version === pageGeneration) state.pageLoading = false; }
  }
  async function showTasks() {
    state.expanded = !state.expanded;
    if (state.expanded) { bannerCursor = state.summary?.cursor; await loadPage(1); await refresh(); }
  }
  async function markRead(entries) {
    if (!visible() || !checkScope() || state.reading) return false;
    const ids = [...new Set(entries.filter(e => e.unread).map(e => e.eventId))].filter(validEventId);
    if (!ids.length) return true;
    const token = gate.capture(), requestIdentity = identity;
    state.reading = true;
    try {
      for (let offset = 0; offset < ids.length; offset += 100) {
        if (!visible() || !gate.current(token) || scope() !== requestIdentity) return false;
        const result = await WorkbenchApi.read(ids.slice(offset, offset + 100));
        if (!gate.current(token) || scope() !== requestIdentity) return false;
        if (result?.code !== 0) throw new Error('提醒已变化');
      }
      for (const e of state.list) if (ids.includes(e.eventId)) e.unread = false;
      if (inFlight) await inFlight;
      await refresh();
      return visible() && gate.current(token) && scope() === requestIdentity;
    } catch {
      if (gate.current(token)) state.pageError = '标记已读失败或事项已变化，请刷新后重试';
      return false;
    } finally { if (gate.current(token)) state.reading = false; }
  }
  async function open(entry) {
    const target = noticeDestination(entry);
    if (target && await markRead([entry])) sheep.$router.go(target.path, target.query);
  }
  async function readOrder(id, afterSale = false) {
    const requestIdentity = checkScope();
    await markRead(unreadForOrder(state.summary, id, afterSale));
    // A failed reminder write must not disable the existing order-detail route.
    // Switching identity or leaving the page still invalidates this navigation.
    return Boolean(requestIdentity) && visible() && scope() === requestIdentity;
  }
  function pause() { gate.invalidate(); pageGeneration++; clearTimeout(timer); state.loading = false; state.pageLoading = false; state.reading = false; }
  function resume() { if (visible()) { checkScope(); void Promise.resolve(inFlight).then(() => refresh()); } }
  function dispose() { disposed = true; pause(); if (typeof document !== 'undefined') document.removeEventListener('visibilitychange', visibility); }
  function visibility() { if (typeof document !== 'undefined' && document.hidden) pause(); else resume(); }
  watch(() => [user.isLogin, user.userInfo?.id, queryKey()], () => { checkScope(); gate.invalidate(); resume(); });
  if (typeof document !== 'undefined') document.addEventListener('visibilitychange', visibility);
  onShow(() => { shown = true; resume(); });
  onHide(() => { shown = false; pause(); }); onUnload(dispose); onBeforeUnmount(dispose);
  return { state, refresh, loadPage, showTasks, markRead, open, readOrder };
}
