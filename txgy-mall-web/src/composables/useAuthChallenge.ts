import { computed, onBeforeUnmount, onMounted, ref, watch, type Ref } from 'vue'
import { authApi } from '@/api/mall'
import { challengeRequest, getAuthEntry } from '@/api/mall-auth'
import { createChallengeState, assertAuthAvailable, type AuthEntry, type AuthPurpose } from '@/utils/auth-challenge.mjs'
import { entryTenantId } from '@/utils/tenant-context'

export function useAuthChallenge(mobile: Ref<string>, purpose: Ref<AuthPurpose>, clearCode: () => void,
  active?: Ref<boolean>, targetTenant?: Ref<string | undefined>) {
  const entry = ref<Readonly<AuthEntry> | null>(null)
  const entryError = ref('')
  const sending = ref(false)
  const now = ref(Date.now())
  const state = createChallengeState()
  // 注册账号固定归属于当前商城入口。公共商城入口由服务端解析为公共租户，
  // 租户商城入口仍归属于自己的租户，用户不再手工选择或覆盖租户。
  const registrationTarget = computed(() => entry.value?.tenantId)
  let alive = true, revision = 0
  const binding = () => ({ tenantId: entry.value?.tenantId || targetTenant?.value || entryTenantId, mobile: mobile.value,
    purpose: purpose.value, protocol: entry.value?.protocol || 'legacy' as const })
  const countdown = computed(() => { void now.value; return state.remaining(mobile.value) })
  const newProtocol = computed(() => entry.value?.protocol === 'challenge-v1')
  const available = computed(() => Boolean(!entryError.value && entry.value?.available && (purpose.value !== 'REGISTER' || entry.value.registrationAvailable)))
  const refresh = async () => {
    if (active?.value === false) { revision++; entry.value = null; entryError.value = ''; return }
    const rev = ++revision; entry.value = null; entryError.value = ''
    try {
      const value = await getAuthEntry(targetTenant?.value || entryTenantId)
      if (alive && rev === revision) entry.value = value
    }
    catch { if (alive && rev === revision) entryError.value = '认证配置加载失败，请重试；不会自动切换旧登录接口' }
  }
  watch([mobile, purpose, entry], () => { state.invalidate(); clearCode(); sending.value = false }, { flush: 'sync' })
  watch(purpose, () => { if (active?.value !== false) void refresh() }, { flush: 'sync' })
  if (active && targetTenant) watch([active, targetTenant], ([enabled]) => {
    if (enabled) void refresh(); else { revision++; entry.value = null; entryError.value = '' }
  }, { flush: 'sync' })
  else if (active) watch(active, enabled => {
    if (enabled) void refresh(); else { revision++; entry.value = null; entryError.value = '' }
  }, { flush: 'sync' })
  else if (targetTenant) watch(targetTenant, () => { void refresh() }, { flush: 'sync' })
  const timer = window.setInterval(() => { now.value = Date.now() }, 1000)
  onMounted(() => { if (active?.value !== false) void refresh() })
  onBeforeUnmount(() => { alive = false; revision++; state.invalidate(); window.clearInterval(timer) })
  function requireEntry() {
    if (entryError.value) throw new Error(entryError.value)
    assertAuthAvailable(entry.value, purpose.value)
    return entry.value!
  }
  async function send() {
    const config = requireEntry()
    if (!/^1\d{10}$/.test(mobile.value)) throw new Error('请输入正确的手机号')
    const ticket = state.begin(binding()); sending.value = true
    try {
      const result = config.protocol === 'challenge-v1'
        ? await challengeRequest('send-code', { tenantId: ticket.tenantId, mobile: ticket.mobile, purpose: ticket.purpose })
        : await authApi.sendSmsCode(ticket.mobile, ticket.purpose === 'RESET_PASSWORD' ? 4 : 1)
      const accepted = alive && state.accept(ticket, result); now.value = Date.now(); return accepted
    } finally { state.finish(ticket); if (state.current(ticket)) sending.value = false }
  }
  function payload(code: string) { requireEntry(); return state.payload(binding(), code) }
  return { entry, entryError, sending, countdown, newProtocol, available, refresh, send, payload, requireEntry,
    registrationTarget }
}
