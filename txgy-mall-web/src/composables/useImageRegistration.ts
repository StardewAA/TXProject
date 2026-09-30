import { computed, onBeforeUnmount, ref, watch, type Ref } from 'vue'
import { challengeRequest } from '@/api/mall-auth'
import { createCaptchaSession, createImageRegistrationState, normalizeLoginName, type ImageChallenge } from '@/utils/image-registration.mjs'

export function useImageRegistration(loginName: Ref<string>, tenantId: Ref<string | undefined>, enabled: Ref<boolean>, clearCode: () => void) {
  const state = createImageRegistrationState()
  const image = ref('')
  const error = ref('')
  const loading = ref(false)
  const now = ref(Date.now())
  let captchaSession = '', alive = true
  let refreshTimer: ReturnType<typeof setTimeout> | undefined
  const countdown = computed(() => { void now.value; return state.remaining() })
  const expiresIn = computed(() => { void now.value; return state.expiresIn() })
  const binding = () => {
    if (!enabled.value || !tenantId.value) throw new Error('当前商城暂未开放图片验证码注册')
    if (!captchaSession) captchaSession = createCaptchaSession()
    return { tenantId: tenantId.value, loginName: normalizeLoginName(loginName.value), captchaSession }
  }
  function invalidate() {
    state.invalidate(); image.value = ''; error.value = ''; loading.value = false; clearCode()
    if (refreshTimer) clearTimeout(refreshTimer)
  }
  async function refresh() {
    if (loading.value) return
    if (refreshTimer) clearTimeout(refreshTimer)
    error.value = ''
    let ticket
    try { ticket = state.begin(binding()) }
    catch (cause) { error.value = cause instanceof Error ? cause.message : '图片验证码暂不可用'; return }
    image.value = ''; clearCode(); loading.value = true
    try {
      const result = await challengeRequest<ImageChallenge>('image-challenge', { ...ticket }, () => alive && state.current(ticket))
      if (alive && state.accept(ticket, result)) { image.value = result.imageDataUrl; now.value = Date.now() }
    } catch (cause) {
      if (alive && state.current(ticket)) error.value = cause instanceof Error ? cause.message : '图片验证码获取失败，请重试'
    } finally {
      state.finish(ticket)
      if (alive && state.current(ticket)) loading.value = false
    }
  }
  watch([loginName, tenantId, enabled], () => {
    invalidate()
    if (!enabled.value) return
    try { normalizeLoginName(loginName.value) } catch { return }
    refreshTimer = setTimeout(() => { void refresh() }, Math.max(350, state.remaining() * 1000))
  }, { flush: 'sync' })
  const timer = window.setInterval(() => { now.value = Date.now() }, 1000)
  onBeforeUnmount(() => { alive = false; invalidate(); window.clearInterval(timer) })
  return { image, error, loading, countdown, expiresIn, refresh, invalidate, payload: (code: string) => state.payload(binding(), code) }
}
