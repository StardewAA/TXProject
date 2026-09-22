import { onBeforeUnmount, onMounted, ref } from 'vue'
import { createPaymentClock } from '@/utils/payment-deadline'

/** One clock per page, not one interval per order. Resume always rechecks server state. */
export function usePaymentClock(refresh: () => Promise<unknown>) {
  const clock = createPaymentClock()
  const now = ref<number>()
  let timer = 0
  let lastTick = performance.now()
  let lastWallTick = Date.now()
  let disposed = false
  let refreshing = false
  let pendingRefresh = false
  let generation = 0

  function invalidate() { generation += 1; clock.invalidate(); now.value = undefined }
  function capture() { return generation }
  function isCurrent(requestGeneration: number) { return !disposed && !document.hidden && requestGeneration === generation }
  function sync(serverTime: unknown, requestGeneration: number) {
    if (!isCurrent(requestGeneration)) return false
    clock.sync(serverTime)
    now.value = clock.now()
    return now.value !== undefined
  }
  async function resume() {
    if (disposed || document.hidden) return
    invalidate()
    pendingRefresh = true
    if (refreshing) return
    refreshing = true
    try {
      while (pendingRefresh && !disposed && !document.hidden) {
        pendingRefresh = false
        try { await refresh() } catch { /* Payment stays unavailable when calibration fails. */ }
      }
    }
    finally { refreshing = false }
  }
  function visibilityChanged() {
    if (document.hidden) invalidate()
    else void resume()
  }
  onMounted(() => {
    timer = window.setInterval(() => {
      const elapsed = performance.now()
      const wall = Date.now()
      // Detect sleep and clock jumps; Date.now only invalidates, never authorizes payment.
      const needsCalibration = elapsed - lastTick > 15000 || Math.abs((wall - lastWallTick) - (elapsed - lastTick)) > 5000
      lastTick = elapsed
      lastWallTick = wall
      if (needsCalibration) { invalidate(); void resume() }
      else now.value = document.hidden ? undefined : clock.now()
    }, 1000)
    document.addEventListener('visibilitychange', visibilityChanged)
    window.addEventListener('focus', resume)
    window.addEventListener('pageshow', resume)
  })
  onBeforeUnmount(() => {
    disposed = true
    pendingRefresh = false
    invalidate()
    window.clearInterval(timer)
    document.removeEventListener('visibilitychange', visibilityChanged)
    window.removeEventListener('focus', resume)
    window.removeEventListener('pageshow', resume)
  })
  function sample() { now.value = disposed || document.hidden ? undefined : clock.now() }
  return { now, sync, invalidate, sample, capture, isCurrent }
}
