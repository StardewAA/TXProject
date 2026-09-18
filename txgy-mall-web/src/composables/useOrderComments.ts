import { onBeforeUnmount, ref } from 'vue'
import { tradeApi } from '@/api/mall'
import { useAccountOperation } from './useAccountOperation'
import type { OrderItem, TradeOrder } from '@/types/mall'

export interface CommentForm { item: OrderItem; descriptionScores: number; benefitScores: number; content: string; anonymous: boolean; picUrlsText: string }
export function useOrderComments(orderId: string) {
  const operation = useAccountOperation(), forms = ref<CommentForm[]>([]), order = ref<TradeOrder | null>(null)
  const loading = ref(false), submitting = ref(false), error = ref(''), needsRefresh = ref(false)
  const succeeded = new Set<string>(); let generation = 0, alive = true
  const current = (request: number) => alive && operation.current() && request === generation
  function reconcile(value: TradeOrder) {
    if (!value || String(value.id) !== orderId || !Array.isArray(value.items)) throw new Error('订单信息不一致，请从订单详情重新进入')
    if (value.status === 30 && (typeof value.commentStatus !== 'boolean' || value.items.some(item => typeof item.commentStatus !== 'boolean'))) throw new Error('评价状态不完整，请刷新核验后再试')
    order.value = value
    const drafts = new Map(forms.value.map(form => [String(form.item.id), form]))
    // Match the existing server gate. Unknown states do not grant comment eligibility.
    const items = value.status === 30 && value.commentStatus === false ? value.items.filter(item => item.commentStatus === false && !succeeded.has(String(item.id))) : []
    forms.value = items.map(item => ({ ...(drafts.get(String(item.id)) || { descriptionScores: 5, benefitScores: 5, content: '', anonymous: false, picUrlsText: '' }), item }))
    needsRefresh.value = false
  }
  async function load() {
    if (!alive || !operation.current() || submitting.value || loading.value) return
    const request = ++generation; loading.value = true; error.value = ''
    try { const value = await tradeApi.getOrder(orderId); if (current(request)) reconcile(value) }
    catch (reason) { if (current(request)) { needsRefresh.value = true; error.value = reason instanceof Error ? reason.message : '订单读取失败，请刷新核验后再评价' } }
    finally { if (current(request)) loading.value = false }
  }
  async function submit() {
    if (!alive || !operation.current() || loading.value || submitting.value || needsRefresh.value || !forms.value.length) return false
    if (forms.value.some(form => !form.content.trim() || form.content.trim().length > 500 || ![form.descriptionScores, form.benefitScores].every(score => Number.isInteger(score) && score >= 1 && score <= 5))) {
      error.value = '请填写每件待评价商品的内容（1—500 字），并选择 1—5 星评分'; return false
    }
    const request = ++generation; submitting.value = true; error.value = ''
    try {
      // A different device may have already evaluated some items since this page loaded.
      const fresh = await tradeApi.getOrder(orderId); if (!current(request)) return false; reconcile(fresh)
      if (fresh.status !== 30) { error.value = '订单状态已变化，当前不能评价；没有提交新的评价。'; return false }
      const queue = [...forms.value]
      for (const form of queue) {
        if (!current(request)) return false
        await tradeApi.createComment({ anonymous: form.anonymous, orderItemId: form.item.id, descriptionScores: form.descriptionScores, benefitScores: form.benefitScores, content: form.content.trim(), picUrls: form.picUrlsText.split(/[\n,，]/).map(value => value.trim()).filter(Boolean).slice(0, 9) })
        if (!current(request)) return false
        succeeded.add(String(form.item.id)); forms.value = forms.value.filter(value => String(value.item.id) !== String(form.item.id))
      }
      return current(request) && forms.value.length === 0
    } catch (reason) {
      if (!current(request)) return false
      const message = reason instanceof Error ? reason.message : '评价提交失败'
      needsRefresh.value = true
      try {
        const fresh = await tradeApi.getOrder(orderId); if (!current(request)) return false; reconcile(fresh)
        error.value = `${message}。已重新核验：已成功项目不会重发，剩余草稿已保留，请确认后重试。`
      } catch {
        if (current(request)) error.value = `${message}。暂时无法核验提交结果，已暂停再次提交；请刷新核验，勿重复发送。`
      }
      return false
    } finally { if (current(request)) submitting.value = false }
  }
  onBeforeUnmount(() => { alive = false; generation++; forms.value = []; order.value = null })
  return { forms, order, loading, submitting, error, needsRefresh, load, submit, current: operation.current }
}
