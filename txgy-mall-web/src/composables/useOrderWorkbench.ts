import { computed, onScopeDispose, shallowRef, watch, type Ref } from 'vue'
import { tokenStorage } from '@/api/http'
import { orderWorkbenchApi, type TodoEntry, type WorkbenchQuery, type WorkbenchRole, type WorkbenchScope, type WorkbenchSummary } from '@/api/order-workbench'
import { useAuthStore } from '@/stores/auth'
import { getAccountRequestTenant } from '@/utils/tenant-context'
import { createWorkbenchPoller, type WorkbenchPollState } from '@/utils/order-workbench-poller'

type State = WorkbenchPollState<WorkbenchSummary>
const blank = (): State => ({ loading: false, error: '', newCount: 0, interval: 30000 })
const epoch = shallowRef(0)
const sessions = new Map<string, ReturnType<typeof createSession>>()

function resetSessions() {
  sessions.forEach((session) => session.stop())
  sessions.clear()
  epoch.value += 1
}
window.addEventListener('mall:token-updated', resetSessions)
window.addEventListener('mall:unauthorized', resetSessions)
window.addEventListener('mall:tenant-context-updated', resetSessions)

function createSession(scope: WorkbenchScope, expectedScope: string) {
  const state = shallowRef<State>(blank())
  let references = 0, timer = 0, stopped = false
  const poller = createWorkbenchPoller(async (cursor) => {
    const snapshot = await orderWorkbenchApi.summary({ ...scope, afterCursor: cursor })
    if (snapshot.scopeKey !== expectedScope) throw new Error('账号或租户已变化，请重新读取当前待办')
    return { snapshot, cursor: snapshot.cursor, addedCount: snapshot.newCount, pollIntervalSeconds: snapshot.pollIntervalSeconds, resetRequired: snapshot.resetRequired }
  }, (value) => { state.value = value })
  async function refresh(resume = false) {
    if (stopped) return
    window.clearTimeout(timer)
    await poller.refresh(resume)
    window.clearTimeout(timer)
    if (!stopped && references > 0) timer = window.setTimeout(() => { if (!document.hidden && navigator.onLine !== false) void refresh(); else schedule() }, state.value.interval)
  }
  function schedule() { if (!stopped) timer = window.setTimeout(() => { if (!document.hidden && navigator.onLine !== false) void refresh(); else schedule() }, state.value.interval) }
  const resume = () => { if (!document.hidden && navigator.onLine !== false) void refresh(true) }
  function stop() {
    stopped = true
    window.clearTimeout(timer)
    poller.dispose()
    state.value = blank()
    window.removeEventListener('focus', resume)
    window.removeEventListener('online', resume)
    window.removeEventListener('pageshow', resume)
    document.removeEventListener('visibilitychange', resume)
  }
  return {
    state, refresh, dismissBanner: poller.dismissBanner, stop,
    retain() {
      references += 1
      if (references === 1) {
        window.addEventListener('focus', resume); window.addEventListener('online', resume); window.addEventListener('pageshow', resume)
        document.addEventListener('visibilitychange', resume)
        void refresh(true)
      }
    },
    release() { references -= 1; if (references <= 0) stop(); return references },
    async read(entries: TodoEntry[]) {
      if (stopped) return false
      const eventIds = [...new Set(entries.filter((entry) => entry.unread).map((entry) => entry.eventId))]
      if (!eventIds.length) return true
      for (let offset = 0; offset < eventIds.length; offset += 100) {
        if (stopped) return false
        await orderWorkbenchApi.read({ ...scope, eventIds: eventIds.slice(offset, offset + 100) })
      }
      if (!stopped) await refresh()
      return !stopped
    },
  }
}

export function useOrderWorkbench(role: () => WorkbenchRole = () => 'BUYER', shopId: () => number | undefined = () => undefined) {
  const auth = useAuthStore()
  const selected = shallowRef<ReturnType<typeof createSession>>()
  const scope = computed<WorkbenchScope>(() => ({ role: role(), ...(role() === 'SUPPLIER' ? { shopId: shopId() } : {}) }))
  const key = computed(() => {
    if (!auth.isLoggedIn || !auth.profile?.id || !tokenStorage.getAccess()) return ''
    if (scope.value.role === 'SUPPLIER' && (!Number.isSafeInteger(scope.value.shopId) || Number(scope.value.shopId) <= 0)) return ''
    return `${epoch.value}:${getAccountRequestTenant()}:${auth.profile.id}:${scope.value.role}:${scope.value.shopId || 0}`
  })
  const stop = watch(key, (value, _old, cleanup) => {
    selected.value = undefined
    if (!value) return
    let session = sessions.get(value)
    if (!session) { session = createSession({ ...scope.value },`${getAccountRequestTenant()}:1:${auth.profile!.id}:${scope.value.role}:${scope.value.shopId||0}`); sessions.set(value, session) }
    selected.value = session
    session.retain()
    cleanup(() => { if (session!.release() <= 0) sessions.delete(value) })
  }, { immediate: true, flush: 'sync' })
  onScopeDispose(stop)
  return {
    scope, key, state: computed(() => selected.value?.state.value || blank()),
    refresh: () => selected.value?.refresh() || Promise.resolve(),
    dismissBanner: () => selected.value?.dismissBanner(),
    read: (entries: TodoEntry[]) => selected.value?.read(entries) || Promise.resolve(false),
  }
}

/** Filtered counts/precise row markers never overwrite the shared, unfiltered navigation summary. */
export function useOrderWorkbenchRows(workbench: ReturnType<typeof useOrderWorkbench>, query: Ref<Omit<WorkbenchQuery, 'role' | 'shopId'>>) {
  const snapshot = shallowRef<WorkbenchSummary>()
  const error = shallowRef('')
  let version = 0
  let filterKey = ''
  watch([workbench.key, () => workbench.state.value.updatedAt, query], async ([key], previous, cleanup) => {
    const current = ++version
    cleanup(() => { version += 1 })
    const nextFilterKey = JSON.stringify({no:query.value.no||'',commentStatus:query.value.commentStatus})
    if (key !== previous?.[0] || nextFilterKey !== filterKey) { snapshot.value = undefined; error.value = '' }
    filterKey = nextFilterKey
    if (!key) { snapshot.value = undefined; error.value = ''; return }
    try {
      const result = await orderWorkbenchApi.summary({ ...workbench.scope.value, ...query.value })
      if (current !== version) return
      const [, scopeTenant, actorId, role, shop] = key.split(':')
      if (result.scopeKey !== `${scopeTenant}:1:${actorId}:${role}:${shop}`) throw new Error('账号或店铺已变化，请重新读取状态数量')
      snapshot.value = result; error.value = ''
    } catch (reason) { if (current === version) error.value = reason instanceof Error ? reason.message : '状态数量暂无法更新' }
  }, { immediate: true, deep: true })
  return { snapshot, error }
}
