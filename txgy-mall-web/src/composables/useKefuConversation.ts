import { onBeforeUnmount, ref } from 'vue'
import { kefuChatApi } from '@/api/kefu-chat'
import { getTenantContext } from '@/utils/tenant-context'
import { assertKefuThread, chatText, mergeKefuMessages, messageCursor, newClientMessageId } from '@/utils/kefu-chat'
import type { KefuMessage, KefuResource, KefuSend, KefuThread } from '@/types/kefu-chat'
import { useAccountOperation } from './useAccountOperation'

export function useKefuConversation(initial: KefuThread, agent: boolean) {
  // Pin every follow-up response to the server-resolved session tenant. A platform
  // member may legitimately talk to a shop owned by another tenant.
  const operation = useAccountOperation(), scope = { scope: initial.session.scope, shopId: initial.session.shopId }, tenant = String(initial.session.tenantId)
  const memberId = agent ? undefined : getTenantContext().accountUserId
  const thread = ref(assertKefuThread(initial, scope, tenant, undefined, memberId)), messages = ref<KefuMessage[]>([])
  const input = ref(''), error = ref(''), sending = ref(false), loading = ref(false), failed = ref(false)
  const uncertain = ref(false), loadedCursor = ref('0'), acknowledging = ref(false)
  const sentResourceKey = ref('')
  const sessionId = initial.session.id
  let cursor = '0', generation = 0, timer = 0, active = true, polling = false
  let pending: KefuSend | null = null
  const visible = () => typeof document === 'undefined' || document.visibilityState !== 'hidden'
  const current = (request: number) => active && operation.current() && generation === request
  const mayReply = () => !agent || (thread.value.session.state === 'ACTIVE' && thread.value.session.agentType === 1 && String(thread.value.session.agentId) === getTenantContext().accountUserId)
  function denied(reason: unknown) { const value = reason as { code?: number; status?: number }; return [403, 1013016002].includes(value?.code ?? 0) || value?.status === 403 }
  function hideHistory() { generation++; polling = false; loading.value = false; sending.value = false; failed.value = true; messages.value = []; cursor = '0'; loadedCursor.value = '0'; window.clearTimeout(timer) }
  function apply(value: KefuThread) {
    assertKefuThread(value, scope, tenant, sessionId, memberId)
    if (value.session.version >= thread.value.session.version) thread.value = value
  }
  function schedule(delay = 5000) {
    window.clearTimeout(timer)
    if (active && operation.current() && visible() && !failed.value) timer = window.setTimeout(() => void refresh(true), delay)
  }
  async function refresh(silent = false) {
    if (!active || !operation.current() || !visible() || sending.value || polling) return
    const request = ++generation; polling = true
    if (!silent) loading.value = true
    try {
      const [state, page] = await Promise.all([kefuChatApi.thread(sessionId, agent), kefuChatApi.messages(sessionId, cursor, agent)])
      if (!current(request) || !visible()) return
      apply(state)
      const next = messageCursor(page.nextCursor)
      if (BigInt(next) < BigInt(cursor)) throw new Error('消息游标已变化，请重新打开客服')
      messages.value = mergeKefuMessages(messages.value, page.items, sessionId, scope.scope === 'SHOP' ? scope.shopId : undefined); cursor = next; loadedCursor.value = next
      failed.value = false; error.value = ''
      if (current(request)) schedule(page.hasMore ? 250 : 5000)
    } catch (reason) {
      if (current(request)) { hideHistory(); error.value = reason instanceof Error ? reason.message : '会话读取失败，请重新核验权限' }
    } finally { if (current(request)) { polling = false; loading.value = false } }
  }
  async function send() {
    if (!active || !visible() || !operation.current() || uncertain.value || sending.value || failed.value || thread.value.mode === 'CLOSED' || !mayReply()) return
    let text: string
    try { text = chatText(input.value) } catch (reason) { error.value = (reason as Error).message; return }
    try { if (!pending || pending.text !== text) pending = { sessionId, version: thread.value.session.version, clientMessageId: newClientMessageId(), text } }
    catch (reason) { error.value = (reason as Error).message; return }
    await transmit()
  }
  async function sendResource(resource: KefuResource) {
    if (agent || !active || !visible() || !operation.current() || uncertain.value || sending.value || failed.value || thread.value.mode === 'CLOSED') return
    try { if (!pending || pending.resourceType !== resource.resourceType || String(pending.resourceId) !== String(resource.resourceId)) pending = { sessionId, version: thread.value.session.version, clientMessageId: newClientMessageId(), ...resource } }
    catch (reason) { error.value = (reason as Error).message; return }
    await transmit()
  }
  async function transmit() {
    if (!pending) return
    const payload = { ...pending }, request = ++generation
    window.clearTimeout(timer); polling = false; loading.value = false; sending.value = true; error.value = ''
    try {
      const response = await kefuChatApi.send(payload, agent)
      if (!current(request)) return
      apply(response.thread)
      messages.value = mergeKefuMessages(messages.value, response.messages, sessionId, scope.scope === 'SHOP' ? scope.shopId : undefined)
      if (payload.resourceType && payload.resourceId !== undefined) sentResourceKey.value = `${payload.resourceType}:${String(payload.resourceId)}`
      pending = null; uncertain.value = false; if (payload.text) input.value = ''
    } catch (reason) {
      if (current(request)) {
        if (denied(reason)) { hideHistory(); pending = null; uncertain.value = false }
        else uncertain.value = true
        error.value = `${reason instanceof Error ? reason.message : '发送失败'}${denied(reason) ? '；已隐藏历史，请重新核验权限' : '；结果未确认，请重试原消息或明确放弃继续重试'}`
      }
    }
    finally { if (current(request)) { sending.value = false; schedule(300) } }
  }
  async function human() {
    if (agent || !active || !visible() || !operation.current() || uncertain.value || sending.value || failed.value || thread.value.mode !== 'SELF_SERVICE') return
    const request = ++generation; window.clearTimeout(timer); polling = false; sending.value = true; error.value = ''
    try { const response = await kefuChatApi.human(sessionId, thread.value.session.version); if (current(request)) apply(response) }
    catch (reason) { if (current(request)) { if (denied(reason)) hideHistory(); error.value = reason instanceof Error ? reason.message : '转人工失败，请刷新状态重试' } }
    finally { if (current(request)) { sending.value = false; schedule() } }
  }
  async function retryPending() {
    if (active && visible() && operation.current() && uncertain.value && pending && !sending.value && !failed.value && mayReply()) await transmit()
  }
  function discardPending() {
    if (!active || !operation.current() || sending.value) return
    pending = null; uncertain.value = false; input.value = ''; error.value = '已放弃继续重试；原消息可能已送达，请先刷新记录，避免重复发送。'
  }
  async function acknowledge() {
    if (!active || !visible() || !operation.current() || failed.value || acknowledging.value || loadedCursor.value === '0') return
    const request = generation, throughId = loadedCursor.value; acknowledging.value = true
    try { const value = await kefuChatApi.read(sessionId, throughId, agent); if (current(request) && visible()) apply(value) }
    catch (reason) { if (current(request)) { if (denied(reason)) hideHistory(); error.value = reason instanceof Error ? reason.message : '已读标记失败，可稍后重试' } }
    finally { acknowledging.value = false }
  }
  function visibilityChanged() {
    if (sending.value && pending) uncertain.value = true
    generation++; polling = false; loading.value = false; sending.value = false; window.clearTimeout(timer)
    if (visible()) void refresh()
  }
  document.addEventListener('visibilitychange', visibilityChanged)
  onBeforeUnmount(() => { active = false; generation++; window.clearTimeout(timer); document.removeEventListener('visibilitychange', visibilityChanged) })
  void refresh()
  return { thread, messages, input, error, sending, loading, failed, uncertain, sentResourceKey, loadedCursor, acknowledging, acknowledge, retryPending, discardPending, send, sendResource, human, refresh }
}
