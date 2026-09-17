<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { kefuChatApi } from '@/api/kefu-chat'
import KefuConversation from '@/components/KefuConversation.vue'
import { useAccountOperation } from '@/composables/useAccountOperation'
import { useKefuPresence } from '@/composables/useKefuPresence'
import { getTenantContext } from '@/utils/tenant-context'
import { assertKefuThread, kefuScope, kefuModeLabel, scopedChatEnabled } from '@/utils/kefu-chat'
import type { KefuThread } from '@/types/kefu-chat'
const route = useRoute(), operation = useAccountOperation(), scope = kefuScope(route.params.shopId)
const presence = useKefuPresence(scope), { online, pending: presencePending, error: presenceError } = presence
const checked = ref(false), enabled = ref(false), allowed = ref(false), error = ref(''), loading = ref(false), changing = ref(false)
const rows = ref<KefuThread[]>([]), selected = ref<KefuThread | null>(null), state = ref('WAITING'), page = ref(1)
const threadInstance = ref(0)
const size = 20, hasNext = computed(() => rows.value.length === size)
let generation = 0, timer = 0, active = true
const current = (request: number) => active && operation.current() && request === generation
function cancelPoll() { window.clearTimeout(timer) }
function schedule() { cancelPoll(); if (active && allowed.value && document.visibilityState !== 'hidden') timer = window.setTimeout(() => void load(true), 10000) }
async function load(silent = false) {
  if (!operation.current() || changing.value) return
  const request = ++generation; if (!silent) loading.value = true
  try {
    if (!checked.value) { const config = await kefuChatApi.capability(); if (!current(request)) return; enabled.value = scopedChatEnabled(config); checked.value = true }
    if (!enabled.value) return
    const result = await kefuChatApi.workbench({ ...scope, state: state.value, page: page.value, size })
    if (!current(request)) return
    rows.value = result.map(value => assertKefuThread(value, scope, getTenantContext().browseTenantId)); allowed.value = true; error.value = ''; schedule()
  } catch (reason) { if (current(request)) { allowed.value = false; rows.value = []; selected.value = null; presence.setOnline(false); cancelPoll(); error.value = reason instanceof Error ? reason.message : '无客服接待权限或服务暂不可用' } }
  finally { if (current(request)) loading.value = false }
}
async function view(row: KefuThread) {
  const request = ++generation; cancelPoll(); changing.value = true; selected.value = null; threadInstance.value++
  try { const value = await kefuChatApi.thread(row.session.id, true); if (current(request)) selected.value = assertKefuThread(value, scope, getTenantContext().browseTenantId, row.session.id) }
  catch (reason) { if (current(request)) error.value = reason instanceof Error ? reason.message : '无权读取此会话' }
  finally { if (current(request)) { changing.value = false; schedule() } }
}
async function transition(row: KefuThread, action: 'claim' | 'close') {
  if (!operation.current() || changing.value) return
  const request = ++generation; cancelPoll(); changing.value = true
  try {
    await kefuChatApi[action](row.session.id, row.session.version)
    if (!current(request)) return
    const updated = await kefuChatApi.thread(row.session.id, true)
    if (current(request)) { threadInstance.value++; selected.value = assertKefuThread(updated, scope, getTenantContext().browseTenantId, row.session.id); error.value = '' }
  } catch (reason) { if (current(request)) { selected.value = null; error.value = reason instanceof Error ? reason.message : '状态已变化，请刷新再操作' } }
  finally { if (current(request)) { changing.value = false; void load() } }
}
const mine = (row: KefuThread) => row.session.agentType === 1 && String(row.session.agentId) === getTenantContext().accountUserId
function visibility() { cancelPoll(); if (document.visibilityState !== 'hidden') void load() }
watch([state, page], () => { selected.value = null; void load() })
onMounted(() => void load())
document.addEventListener('visibilitychange', visibility)
onBeforeUnmount(() => { active = false; generation++; cancelPoll(); document.removeEventListener('visibilitychange', visibility) })
</script>
<template>
  <div class="container page-section agent-page" data-testid="agent-workbench"><MallLink to="/sales">← 返回销售中心</MallLink><h1>店铺 #{{ scope.shopId }} · 客服接待台</h1><p>仅独立授权的会员坐席可接待，不随供应商资格或商品经营权限自动开通。</p>
    <section v-if="checked && !enabled" class="panel empty"><h2>范围客服尚未开通</h2><p>后台确认坐席名单并启用后才可接待。此页面不会调用旧管理员广播。</p></section>
    <template v-else><div v-if="error" class="error" role="alert">{{ error }}<el-button :loading="loading" @click="load()">重新核验权限</el-button></div><section v-if="allowed" class="panel presence"><div><b>{{ online ? '在线状态已由服务端确认' : '当前未声明在线' }}</b><p>仅此页面保持可见时每 30 秒续期，服务端有效期 90 秒；离开或隐藏页面停止续期。不可用时不承诺在线。</p><small v-if="presenceError" class="error">{{ presenceError }}</small></div><el-button :loading="presencePending" :disabled="online" @click="presence.setOnline(true)">开始接待 / 上线</el-button><el-button :disabled="!online && !presencePending" @click="presence.setOnline(false)">下线</el-button></section><div v-if="allowed" class="toolbar"><select v-model="state" aria-label="会话状态"><option value="WAITING">等待接待</option><option value="ACTIVE">正在接待</option><option value="CLOSED">已结束</option></select><el-button :loading="loading" :disabled="changing" @click="load()">刷新队列</el-button><span>第 {{ page }} 页</span><el-button :disabled="page === 1 || changing" @click="page--">上一页</el-button><el-button :disabled="!hasNext || changing" @click="page++">下一页</el-button></div><div v-loading="loading" class="agent-columns"><section v-if="allowed" class="panel queue"><p v-if="!rows.length" class="empty">当前页暂无会话</p><article v-for="row in rows" :key="row.session.id"><h3>会员 #{{ row.session.memberId }}</h3><p>{{ kefuModeLabel(row) }}</p><small>未读 {{ row.unreadCount }} · 版本 {{ row.session.version }}</small><div><el-button :disabled="changing" @click="view(row)">查看会话</el-button><el-button v-if="row.session.state === 'WAITING' && row.humanRequested" :disabled="changing" @click="transition(row, 'claim')">领取</el-button><el-button v-if="row.session.state === 'ACTIVE' && mine(row)" :disabled="changing" @click="transition(row, 'close')">结束接待</el-button></div></article></section><KefuConversation v-if="selected" :key="`${selected.session.id}:${threadInstance}`" :initial="selected" agent @update="selected = $event" /></div></template>
  </div>
</template>
<style scoped>.agent-page>p{color:#687c75;line-height:1.8}.agent-page h1{font-size:25px}.presence{display:flex;align-items:center;gap:14px;padding:20px;margin:20px 0}.presence>div{flex:1}.presence p{font-size:13px;line-height:1.7;color:#6d7b77}.toolbar{display:flex;align-items:center;gap:12px;margin:20px 0}.toolbar select{padding:9px;border:1px solid var(--line)}.agent-columns{display:grid;grid-template-columns:310px minmax(0,1fr);gap:22px}.queue article{padding:20px;border-bottom:1px solid var(--line)}.queue h3{font-size:16px;margin:0}.queue p,.queue small{font-size:13px;color:#697a73}.queue article>div{margin-top:12px;display:flex;gap:8px;flex-wrap:wrap}.queue .el-button{margin:0}.empty{padding:45px;text-align:center;color:#718077}.error{padding:14px;background:#fff1eb;color:#a13927}.error .el-button{margin-left:16px}</style>
