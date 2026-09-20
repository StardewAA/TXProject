import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { fulfillmentApi } from '@/api/fulfillment'
import { useAccountOperation } from './useAccountOperation'
import type { Identifier } from '@/types/mall'
import type { OrderLogistics } from '@/types/fulfillment'
import { logisticsRetrySeconds } from '@/utils/fulfillment'

export function useOrderLogistics(orderId: () => Identifier) {
  const operation = useAccountOperation()
  const data = ref<OrderLogistics | null>(null)
  const loading = ref(false)
  const failed = ref(false)
  const remaining = ref(0)
  let generation = 0
  let nextRefreshAt = 0
  const timer = window.setInterval(() => { remaining.value = Math.max(0, Math.ceil((nextRefreshAt - Date.now()) / 1000)) }, 1000)
  const canRefresh = computed(() => !loading.value && remaining.value === 0)

  async function load() {
    if (!operation.current() || !canRefresh.value) return
    const current = ++generation
    const id = orderId()
    loading.value = true
    failed.value = false
    const isCurrent = () => operation.current() && current === generation && String(id) === String(orderId())
    try {
      const result = await fulfillmentApi.getLogistics(id)
      if (!isCurrent()) return
      if (String(result?.orderId) !== String(id)) throw new Error('Order identity mismatch')
      data.value = result
      remaining.value = logisticsRetrySeconds(result.refreshAfterSeconds)
      nextRefreshAt = Date.now() + remaining.value * 1000
    } catch {
      if (!isCurrent()) return
      failed.value = true
      // A failed transport is not an empty successful query. Keep any earlier snapshot visibly stale.
      if (data.value) data.value = { ...data.value, stale: true }
      remaining.value = 2
      nextRefreshAt = Date.now() + 2000
    } finally { if (isCurrent()) loading.value = false }
  }

  watch(orderId, () => {
    generation += 1
    data.value = null
    failed.value = false
    loading.value = false
    remaining.value = 0
    nextRefreshAt = 0
    void load()
  }, { immediate: true, flush: 'sync' })
  onBeforeUnmount(() => { generation += 1; window.clearInterval(timer) })
  return { data, loading, failed, remaining, canRefresh, load }
}
