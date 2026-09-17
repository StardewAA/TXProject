<script setup lang="ts">
import { computed, watch } from 'vue'
import { useKefuConversation } from '@/composables/useKefuConversation'
import { useAccountMessageBox } from '@/composables/useAccountOperation'
import { getTenantContext } from '@/utils/tenant-context'
import { kefuModeLabel } from '@/utils/kefu-chat'
import { formatMoney } from '@/utils/format'
import type { KefuResource, KefuThread } from '@/types/kefu-chat'
const props = defineProps<{ initial: KefuThread; agent?: boolean; resource?: KefuResource }>()
const emit = defineEmits<{ update: [thread: KefuThread] }>()
const chat = useKefuConversation(props.initial, props.agent === true)
const { thread, messages, input, error, sending, loading, failed, uncertain, loadedCursor, acknowledging, acknowledge, retryPending, discardPending, send, sendResource, human, refresh } = chat
const accountModal = useAccountMessageBox()
async function abandon() { try { await accountModal.confirm('原消息可能已经送达，放弃只停止继续重试，不会撤回消息。请先刷新记录再决定是否重发。', '放弃继续重试？', { type: 'warning', confirmButtonText: '确认放弃继续重试', cancelButtonText: '保留并重试' }); discardPending() } catch { /* cancelled or original account left */ } }
const canReply = computed(() => !props.agent || (thread.value.session.state === 'ACTIVE' && thread.value.session.agentType === 1 && String(thread.value.session.agentId) === getTenantContext().accountUserId))
const senderNames = { MEMBER: '用户', AGENT: '人工客服', FAQ: '固定问答', SYSTEM: '系统提示' }
const time = (value: number) => Number.isFinite(value) ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : ''
watch(thread, value => emit('update', value))
</script>
<template>
  <section class="kefu-thread" data-testid="kefu-thread">
    <header><div><h2>{{ kefuModeLabel(thread) }}</h2><p>{{ thread.availabilityMessage }}</p><small>仅固定问答与人工沟通，不自动付款、退款、发货或修改库存。</small></div><el-button :loading="loading" :disabled="sending" @click="refresh()">刷新会话</el-button></header>
    <div v-if="!agent && thread.mode === 'SELF_SERVICE' && thread.faq?.length" class="faq"><b>常见问题（点击填入，确认后发送）</b><div><el-button v-for="item in thread.faq" :key="String(item.id)" :disabled="sending || failed || uncertain" @click="input = item.question">{{ item.question }}</el-button></div></div>
    <div v-if="error" role="alert" class="error">{{ error }}</div>
    <div v-if="uncertain" class="resource-note"><b>上一条消息送达结果未确认，已暂停发送新内容。</b><p>重试保留同一消息编号，避免重复；放弃重试不会撤回可能已送达的消息。</p><el-button :disabled="sending || failed" @click="retryPending">重试原消息</el-button><el-button :disabled="sending" @click="abandon">放弃继续重试</el-button></div>
    <section v-if="resource && !agent && thread.mode !== 'CLOSED'" class="resource-note"><b>待发送：{{ resource.resourceType === 'PRODUCT' ? '商品' : '本人订单' }}引用 #{{ resource.resourceId }}</b><p>只在你确认后发送。卡片内容由服务器核验店铺和资源权限后生成，不含你在网址填写的价格或描述。</p><el-button :disabled="sending || failed || uncertain" @click="sendResource(resource)">确认发送{{ resource.resourceType === 'PRODUCT' ? '商品' : '订单' }}卡片</el-button></section>
    <div class="messages" aria-live="polite"><p v-if="!messages.length && !loading" class="muted">暂无可显示消息。消息权限异常时会隐藏记录，请刷新核验。</p><article v-for="message in messages" :key="String(message.id)" :class="{ mine: agent ? message.senderRole === 'AGENT' : message.senderRole === 'MEMBER', automatic: message.senderRole === 'FAQ' }"><div><small>{{ senderNames[message.senderRole] }} · {{ time(message.createdAt) }}</small><p v-if="message.text">{{ message.text }}</p><section v-if="message.card" class="resource-card"><b>{{ message.card.title }}</b><p>{{ message.card.text }}</p><small v-if="typeof message.card.amount === 'number'">参考金额 ¥{{ formatMoney(message.card.amount) }} · 不锁价、不作为履约承诺</small><MallLink v-if="message.card.type === 'PRODUCT' && message.card.spuId" :to="`/goods/${message.card.spuId}`">查看商品 →</MallLink><MallLink v-if="!agent && message.card.type === 'ORDER' && message.card.orderId" :to="`/member/orders/${message.card.orderId}`">查看本人订单 →</MallLink></section><small v-if="message.senderRole === 'FAQ'">固定问答条目 {{ message.faqId }} · 版本 {{ message.faqVersion }}，不代表人工承诺</small></div></article></div>
    <p v-if="agent && !canReply" class="muted">需由当前独立授权坐席领取会话后才能回复；经营权限不等于客服权限。</p>
    <el-button :disabled="loadedCursor === '0' || failed" :loading="acknowledging" @click="acknowledge">标记已加载消息为已读</el-button>
    <div v-if="thread.mode !== 'CLOSED'" class="composer"><el-input v-model="input" type="textarea" :rows="3" maxlength="2000" show-word-limit :disabled="sending || failed || uncertain || !canReply" placeholder="请输入纯文本消息；请勿发送密码、完整证件或付款凭证" @keydown.ctrl.enter="send" /><div><span>附件与真实 AI 暂未开放</span><el-button v-if="!agent && thread.mode === 'SELF_SERVICE'" :disabled="sending || failed || uncertain" @click="human">转人工 / 留言</el-button><el-button type="primary" :loading="sending" :disabled="failed || uncertain || !canReply" @click="send">发送消息</el-button></div></div>
  </section>
</template>
<style scoped>
.resource-note{padding:18px;background:#fff9ec;border:1px solid #eee1c8;line-height:1.7}.resource-note p{font-size:13px;color:#736951}.resource-card{padding:18px;border:1px solid #b9d4ce;background:#f1f8f5;border-radius:8px}.resource-card a{display:block;margin-top:10px;color:var(--brand)}
.kefu-thread header{display:flex;justify-content:space-between;gap:16px;padding:20px;background:#eff6f4}.kefu-thread h2{font-size:18px;margin:0 0 8px}.kefu-thread header p{margin:6px 0}.kefu-thread small,.muted{font-size:12px;color:#697a75}.faq{padding:18px;border-bottom:1px solid var(--line)}.faq div{display:flex;flex-wrap:wrap;gap:8px;margin-top:12px}.faq .el-button{margin:0;max-width:100%;white-space:normal;height:auto;line-height:1.6}.error{color:#a73826;padding:16px;background:#fff2ed}.messages{max-height:490px;min-height:260px;overflow-y:auto;padding:20px;background:#f8faf9}.messages article{display:flex;margin-bottom:18px}.messages article.mine{justify-content:flex-end}.messages article>div{max-width:82%}.messages article p{white-space:pre-wrap;overflow-wrap:anywhere;padding:14px;margin:6px 0;background:white;border:1px solid #e1e9e6;border-radius:8px;line-height:1.7}.messages .mine p{background:#eaf6ef}.messages .automatic p{border-left:3px solid #55918b}.composer{padding:18px;border:1px solid var(--line)}.composer>div{display:flex;justify-content:flex-end;gap:12px;align-items:center;margin-top:12px}.composer span{font-size:12px;color:#758078;margin-right:auto}
</style>
