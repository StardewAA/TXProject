import { onBeforeUnmount, ref } from 'vue'
import { kefuChatApi } from '@/api/kefu-chat'
import { getTenantContext } from '@/utils/tenant-context'
import type { KefuScope } from '@/types/kefu-chat'

/** Explicit opt-in only; serialize offline behind any in-flight renewal to avoid late online resurrection. */
export function useKefuPresence(scope: KefuScope) {
  const epoch = getTenantContext().epoch
  const online = ref(false), pending = ref(false), error = ref('')
  let desired = false, active = true, participated = false, renew = 0, expiry = 0
  const ownsContext = () => epoch === getTenantContext().epoch
  const stopTimers = () => { window.clearTimeout(renew); window.clearTimeout(expiry) }
  async function transmit(wanted: boolean) {
    if (!ownsContext()) { online.value = false; stopTimers(); return }
    // An unopened/unauthorized page must never issue a heartbeat merely on leaving.
    if (!wanted && !participated) return
    if (wanted) participated = true
    pending.value = true
    try {
      const result = await kefuChatApi.heartbeat({ ...scope, online: wanted })
      if (!ownsContext()) return
      online.value = wanted && desired && active && result.online === true && Number.isFinite(result.expiresAt) && result.expiresAt > Date.now()
      error.value = ''
      if (!wanted && result.online === false) participated = false
      stopTimers()
      if (online.value) {
        expiry = window.setTimeout(() => { online.value = false }, Math.min(90000, result.expiresAt - Date.now()))
        renew = window.setTimeout(() => { if (desired && active && ownsContext()) void transmit(true) }, 30000)
      }
    } catch (reason) {
      if (ownsContext()) { online.value = false; desired = false; stopTimers(); error.value = reason instanceof Error ? reason.message : '在线状态无法确认，请重新核验坐席权限' }
    } finally {
      pending.value = false
      if (ownsContext() && wanted !== desired) void transmit(desired)
    }
  }
  function setOnline(value: boolean) {
    if (value && (!active || document.visibilityState === 'hidden')) return
    desired = value; stopTimers()
    if (!value) online.value = false
    if (!pending.value) void transmit(value)
  }
  function hidden() { if (document.visibilityState === 'hidden') setOnline(false) }
  function accountChanged() { if (!ownsContext()) { desired = false; online.value = false; stopTimers() } }
  document.addEventListener('visibilitychange', hidden)
  window.addEventListener('mall:tenant-context-updated', accountChanged)
  onBeforeUnmount(() => { active = false; desired = false; online.value = false; stopTimers(); if (!pending.value && ownsContext()) void transmit(false); document.removeEventListener('visibilitychange', hidden); window.removeEventListener('mall:tenant-context-updated', accountChanged) })
  return { online, pending, error, setOnline }
}
