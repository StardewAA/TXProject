import { computed, onBeforeUnmount, onMounted, ref, watch, type Ref } from 'vue'
import { authApi } from '@/api/mall'
import { challengeRequest, getAuthEntry, getRegistrationOptions } from '@/api/mall-auth'
import { resolveRegistrationChoice, assertRegistrationContext, registrationChoiceNotice, type RegistrationOptions } from '@/utils/registration-choice.mjs'
import { createChallengeState, assertAuthAvailable, type AuthEntry, type AuthPurpose } from '@/utils/auth-challenge.mjs'
import { entryTenantId } from '@/utils/tenant-context'

export function useAuthChallenge(mobile: Ref<string>, purpose: Ref<AuthPurpose>, clearCode: () => void) {
  const entry = ref<Readonly<AuthEntry> | null>(null)
  const entryError = ref('')
  const sending = ref(false)
  const now = ref(Date.now())
  const state = createChallengeState()
  const registrationOptions = ref<RegistrationOptions | null>(null)
  const selectedTenant = ref<string | undefined>(new URLSearchParams(window.location?.search || '').get('registrationTenant') || undefined)
  const registrationChoice = computed(() => resolveRegistrationChoice(registrationOptions.value, selectedTenant.value, entry.value?.tenantId || entryTenantId))
  const choiceNotice = computed(() => {
    try { return registrationChoiceNotice(registrationChoice.value) } catch (error) { return error instanceof Error ? error.message : '请重新选择商城' }
  })
  const choiceError = computed(() => {
    if (purpose.value !== 'REGISTER' || !registrationOptions.value?.enabled) return ''
    try { assertRegistrationContext(registrationChoice.value, entryTenantId); return '' }
    catch (error) { return error instanceof Error ? error.message : '当前商城尚未开放' }
  })
  const registrationTarget = computed(() => { try { return registrationChoice.value.tenantId } catch { return undefined } })
  let alive = true, revision = 0
  const binding = () => ({ tenantId: purpose.value === 'REGISTER' ? registrationChoice.value.tenantId : entry.value?.tenantId || entryTenantId, mobile: mobile.value,
    purpose: purpose.value, protocol: entry.value?.protocol || 'legacy' as const })
  const countdown = computed(() => { void now.value; return state.remaining(mobile.value) })
  const newProtocol = computed(() => entry.value?.protocol === 'challenge-v1')
  const available = computed(() => Boolean(!entryError.value && !choiceError.value && entry.value?.available && (purpose.value !== 'REGISTER' || entry.value.registrationAvailable)))
  const refresh = async () => {
    const rev = ++revision; entry.value = null; registrationOptions.value = null; entryError.value = ''
    try {
      const value = await getAuthEntry()
      const options = purpose.value === 'REGISTER' && value.protocol === 'challenge-v1' ? await getRegistrationOptions() : null
      if (alive && rev === revision) { registrationOptions.value = options; entry.value = value }
    }
    catch { if (alive && rev === revision) entryError.value = '认证配置加载失败，请重试；不会自动切换旧登录接口' }
  }
  watch([mobile, purpose, entry, selectedTenant, registrationOptions], () => { state.invalidate(); clearCode(); sending.value = false }, { flush: 'sync' })
  watch(purpose, refresh, { flush: 'sync' })
  const timer = window.setInterval(() => { now.value = Date.now() }, 1000)
  onMounted(refresh)
  onBeforeUnmount(() => { alive = false; revision++; state.invalidate(); window.clearInterval(timer) })
  function requireEntry() {
    if (entryError.value) throw new Error(entryError.value)
    assertAuthAvailable(entry.value, purpose.value)
    if (purpose.value === 'REGISTER') assertRegistrationContext(registrationChoice.value, entryTenantId)
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
    registrationOptions, selectedTenant, registrationChoice, registrationTarget, choiceNotice, choiceError }
}
