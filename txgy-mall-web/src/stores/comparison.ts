import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { comparisonApi } from '@/api/comparison'
import { ensureAccountSession, tokenStorage, StaleSessionError } from '@/api/http'
import { getTenantContext } from '@/utils/tenant-context'
import { combineComparisonIds, comparisonIds, comparisonQuantity, comparisonStorageKey, readAnonymousComparison } from '@/utils/comparison-selection'
import type { ComparisonList, ComparisonPreference } from '@/types/comparison'

/** Authenticated selections are never cached into guest storage or another member's browser cache. */
export const useComparisonStore = defineStore('mall-comparison', () => {
  const tenant = getTenantContext().browseTenantId
  const anonymousIds = ref(readAnonymousComparison(localStorage, tenant))
  const ids = ref<string[]>(tokenStorage.getAccess() ? [] : [...anonymousIds.value])
  const version = ref<string | number>('0')
  const quantity = ref(1)
  const preference = ref<ComparisonPreference>('PRICE')
  const loading = ref(false)
  const saving = ref(false)
  const ready = ref(!tokenStorage.getAccess())
  const error = ref('')
  const signedIn = ref(Boolean(tokenStorage.getAccess()))
  let generation = 0
  let owner = ownerKey()
  const count = computed(() => ids.value.length)
  const hasAnonymousMerge = computed(() => signedIn.value && anonymousIds.value.length > 0)

  function ownerKey() {
    const context = getTenantContext()
    return JSON.stringify([context.epoch, context.browseTenantId, context.accountTenantId, context.accountUserId, Boolean(tokenStorage.getAccess())])
  }
  function apply(data: ComparisonList) {
    if (!data || !/^(0|[1-9]\d*)$/.test(String(data.version)) || (typeof data.version === 'number' && !Number.isSafeInteger(data.version))) throw new Error('对比清单版本无效，请刷新重试')
    const selected = comparisonIds(data.skuIds)
    const amount = comparisonQuantity(data.quantity)
    if (!['PRICE', 'DELIVERY', 'BALANCED'].includes(data.preference)) throw new Error('对比清单偏好无效')
    version.value = data.version
    ids.value = selected
    quantity.value = amount; preference.value = data.preference
    ready.value = true
  }
  function saveAnonymous(selected: string[]) {
    try { localStorage.setItem(comparisonStorageKey(tenant), JSON.stringify(selected)) }
    catch { throw new Error('浏览器无法保存临时对比清单，请允许本站使用本地存储') }
    anonymousIds.value = [...selected]
    ids.value = [...selected]
  }
  function resetContext() {
    const next = ownerKey()
    if (owner === next) return
    owner = next; generation += 1
    signedIn.value = Boolean(tokenStorage.getAccess())
    ids.value = signedIn.value ? [] : [...anonymousIds.value]
    ready.value = !signedIn.value; loading.value = false; saving.value = false; error.value = ''; version.value = '0'; quantity.value = 1; preference.value = 'PRICE'
    if (signedIn.value && getTenantContext().accountUserId) void refresh()
  }
  async function refresh() {
    resetContext()
    if (saving.value) return false
    if (!tokenStorage.getAccess()) {
      anonymousIds.value = readAnonymousComparison(localStorage, tenant)
      ids.value = [...anonymousIds.value]; ready.value = true; error.value = ''
      return true
    }
    const initialEpoch = getTenantContext().epoch
    try { await ensureAccountSession() }
    catch (reason) { if (initialEpoch === getTenantContext().epoch) { error.value = reason instanceof Error ? reason.message : '请重新登录'; ready.value = false }; return false }
    if (initialEpoch !== getTenantContext().epoch || !getTenantContext().accountUserId) return false
    resetContext()
    const expected = ownerKey(), request = ++generation
    loading.value = true; error.value = ''
    try {
      const data = await comparisonApi.list()
      if (expected !== ownerKey() || request !== generation) return false
      apply(data); return true
    } catch (reason) {
      if (expected === ownerKey() && request === generation) { error.value = reason instanceof Error ? reason.message : '对比清单读取失败'; ready.value = false }
      return false
    } finally { if (expected === ownerKey() && request === generation) loading.value = false }
  }
  async function replace(value: string[], amount = quantity.value, preferred = preference.value) {
    const selected = comparisonIds(value)
    comparisonQuantity(amount)
    if (!['PRICE', 'DELIVERY', 'BALANCED'].includes(preferred)) throw new Error('对比偏好无效')
    resetContext()
    if (saving.value || loading.value) throw new Error('对比清单正在同步，请稍后重试')
    if (!signedIn.value) { saveAnonymous(selected); quantity.value = amount; preference.value = preferred; return }
    if (!ready.value || !getTenantContext().accountUserId) throw new Error('请先刷新并核验当前账号的对比清单')
    const expected = ownerKey(), request = ++generation
    saving.value = true; error.value = ''
    try {
      const data = await comparisonApi.replace({ version: version.value, skuIds: selected, quantity: amount, preference: preferred })
      if (expected !== ownerKey() || request !== generation) throw new StaleSessionError()
      apply(data)
    } catch (reason) {
      if (expected === ownerKey() && request === generation) {
        // A concurrent-device conflict is never retried with an overwritten version.
        error.value = reason instanceof Error ? reason.message : '保存失败，请刷新后重试'
        ready.value = false
      }
      throw reason
    } finally { if (expected === ownerKey() && request === generation) saving.value = false }
  }
  async function add(skuId: string | number) {
    const epoch = getTenantContext().epoch
    const selected = comparisonIds([skuId])[0]!
    if (!ready.value && !await refresh()) throw new Error(error.value || '请先刷新对比清单')
    if (epoch !== getTenantContext().epoch) throw new StaleSessionError()
    if (ids.value.includes(selected)) return
    await replace(combineComparisonIds(ids.value, [selected]))
  }
  const remove = (skuId: string) => replace(ids.value.filter(id => id !== skuId))
  async function mergeAnonymous() {
    if (!signedIn.value || !ready.value) throw new Error('请先登录并刷新对比清单')
    const expected = ownerKey(), guest = [...anonymousIds.value]
    await replace(combineComparisonIds(ids.value, guest))
    if (expected !== ownerKey()) throw new StaleSessionError()
    // Another browser tab may have changed its temporary selection during this request.
    const latest = readAnonymousComparison(localStorage, tenant)
    if (JSON.stringify(latest) === JSON.stringify(guest)) {
      localStorage.removeItem(comparisonStorageKey(tenant)); anonymousIds.value = []
    }
  }
  function discardAnonymous() {
    localStorage.removeItem(comparisonStorageKey(tenant)); anonymousIds.value = []
    if (!signedIn.value) ids.value = []
  }
  window.addEventListener('mall:tenant-context-updated', resetContext)
  window.addEventListener('mall:token-updated', resetContext)
  window.addEventListener('storage', event => {
    if (event.key !== null && event.key !== comparisonStorageKey(tenant)) return
    anonymousIds.value = readAnonymousComparison(localStorage, tenant)
    if (!signedIn.value) ids.value = [...anonymousIds.value]
  })
  return { ids, version, quantity, preference, anonymousIds, signedIn, ready, loading, saving, error, count, hasAnonymousMerge, refresh, replace, add, remove, mergeAnonymous, discardAnonymous, ownerKey }
})
