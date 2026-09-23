<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { productApi } from '@/api/mall'
import { portalApi } from '@/api/portal'
import { useKefuConversation } from '@/composables/useKefuConversation'
import { useAccountMessageBox } from '@/composables/useAccountOperation'
import { getTenantContext } from '@/utils/tenant-context'
import { formatMoney, productImage } from '@/utils/format'
import type { KefuMessage, KefuResource, KefuThread } from '@/types/kefu-chat'
import type { Product } from '@/types/mall'
const props = defineProps<{ initial: KefuThread; agent?: boolean; resource?: KefuResource; portalProduct?: boolean }>()
const emit = defineEmits<{ update: [thread: KefuThread] }>()
const chat = useKefuConversation(props.initial, props.agent === true)
const { thread, messages, input, error, sending, loading, failed, uncertain, sentResourceKey, loadedCursor, acknowledging, acknowledge, retryPending, discardPending, send, sendResource, human, refresh } = chat
const accountModal = useAccountMessageBox()
const resourceProduct = ref<Product | null>(null)
const resourceLoading = ref(false)
const resourceError = ref('')
let previewGeneration = 0
watch(() => props.resource, async value => {
  const generation = ++previewGeneration
  resourceProduct.value = null; resourceError.value = ''
  if (!value || value.resourceType !== 'PRODUCT') return
  resourceLoading.value = true
  try {
    const product = props.portalProduct ? await portalApi.getProduct(value.resourceId) : await productApi.getDetail(value.resourceId)
    if (generation !== previewGeneration) return
    const productId = 'spuId' in product ? product.spuId : product.id
    if (String(productId) !== String(value.resourceId) || String(product.shopId) !== String(thread.value.session.shopId)) throw new Error('商品信息与当前店铺不一致')
    resourceProduct.value = 'spuId' in product
      ? { id: product.spuId, name: product.spuName, introduction: product.introduction, picUrl: product.picUrl, price: product.price, shopId: product.shopId }
      : product
  } catch (reason) {
    if (generation === previewGeneration) resourceError.value = reason instanceof Error ? reason.message : '商品信息暂时无法加载'
  } finally { if (generation === previewGeneration) resourceLoading.value = false }
}, { immediate: true })
onBeforeUnmount(() => { previewGeneration++ })
async function abandon() { try { await accountModal.confirm('原消息可能已经送达，放弃只停止继续重试，不会撤回消息。请先刷新记录再决定是否重发。', '放弃继续重试？', { type: 'warning', confirmButtonText: '确认放弃继续重试', cancelButtonText: '保留并重试' }); discardPending() } catch { /* cancelled or original account left */ } }
async function sendQuickQuestion(question: string) {
  if (sending.value || failed.value || uncertain.value) return
  input.value = question
  await send()
}
const canReply = computed(() => !props.agent || (thread.value.session.state === 'ACTIVE' && thread.value.session.agentType === 1 && String(thread.value.session.agentId) === getTenantContext().accountUserId))
const resourceKey = computed(() => props.resource ? `${props.resource.resourceType}:${String(props.resource.resourceId)}` : '')
const resourceAlreadySent = computed(() => {
  if (!props.resource) return false
  const resource = props.resource
  return messages.value.some(message => {
    const card = message.card
    return message.senderRole === 'MEMBER' && card?.type === resource.resourceType && String(card.resourceId) === String(resource.resourceId)
  })
})
const showPendingResource = computed(() => !!props.resource && sentResourceKey.value !== resourceKey.value && !resourceAlreadySent.value)
const legacySnapshotNotes = new Set(['本人订单快照，不包含收货地址、手机号或支付凭据', '商品信息快照，以商品详情实时信息为准'])
const visibleMessageText = (message: KefuMessage) => message.card && legacySnapshotNotes.has(message.text) ? '' : message.text
const visibleCardText = (text: string) => legacySnapshotNotes.has(text) ? '' : text
const serviceTitle = computed(() => thread.value.session.scope === 'PLATFORM' ? '平台客服' : '店铺客服')
const senderName = (role: KefuMessage['senderRole']) => role === 'MEMBER' ? '我' : role === 'AGENT' ? serviceTitle.value : role === 'FAQ' ? '客服助手' : role === 'AI' ? 'AI 客服' : '服务通知'
const serviceStatus = computed(() => {
  if (thread.value.mode === 'CLOSED') return '本次服务已结束'
  if (thread.value.mode === 'HUMAN') return '客服正在为您服务'
  if (thread.value.mode === 'WAITING_HUMAN') return thread.value.onlineAgents > 0 ? '正在为您接入客服' : '已留言，客服上线后会回复'
  return thread.value.onlineAgents > 0 ? '客服在线，欢迎咨询' : '欢迎咨询，您也可以先留言'
})
const time = (value: number) => Number.isFinite(value) ? new Date(value).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false }) : ''
watch(thread, value => emit('update', value))
</script>
<template>
  <section class="kefu-thread" data-testid="kefu-thread">
    <header class="chat-toolbar">
      <div class="service-avatar">客</div>
      <div class="service-meta"><h2>{{ agent ? '客服接待' : serviceTitle }}</h2><p><i :class="{ online: thread.onlineAgents > 0 || thread.mode === 'HUMAN' }"></i>{{ serviceStatus }}</p></div>
      <el-button text :loading="loading" :disabled="sending" @click="refresh()">刷新消息</el-button>
    </header>
    <div v-if="error" role="alert" class="error">{{ error }}</div>
    <div v-if="uncertain" class="delivery-warning"><b>消息发送状态暂未确认</b><span>建议先刷新消息，避免重复发送。</span><el-button :disabled="sending || failed" @click="retryPending">重新发送</el-button><el-button :disabled="sending" @click="abandon">取消</el-button></div>
    <div class="messages" aria-live="polite">
      <div v-if="!messages.length && !loading" class="chat-empty"><span>欢迎光临</span><div class="welcome-avatar">客</div><b>您好，很高兴为您服务</b><p>请告诉我们您想咨询的商品、规格或订单问题</p></div>
      <article v-for="message in messages" :key="String(message.id)" :class="{ mine: agent ? message.senderRole === 'AGENT' : message.senderRole === 'MEMBER', automatic: message.senderRole === 'FAQ' || message.senderRole === 'AI', notice: message.senderRole === 'SYSTEM' }">
        <div class="bubble-avatar">{{ (agent ? message.senderRole === 'AGENT' : message.senderRole === 'MEMBER') ? '我' : '客' }}</div>
        <div class="bubble-wrap"><small>{{ senderName(message.senderRole) }} · {{ time(message.createdAt) }}</small><p v-if="visibleMessageText(message)" class="bubble">{{ visibleMessageText(message) }}</p>
          <section v-if="message.card" class="resource-card">
            <img v-if="message.card.picUrl" :src="message.card.picUrl" :alt="message.card.title" />
            <div><b>{{ message.card.title }}</b><p v-if="visibleCardText(message.card.text)">{{ visibleCardText(message.card.text) }}</p><strong v-if="typeof message.card.amount === 'number'">¥{{ formatMoney(message.card.amount) }}</strong></div>
            <MallLink v-if="message.card.type === 'PRODUCT' && message.card.spuId" :to="`/goods/${message.card.spuId}`">查看商品</MallLink><MallLink v-if="!agent && message.card.type === 'ORDER' && message.card.orderId" :to="`/member/orders/${message.card.orderId}`">查看订单</MallLink>
          </section>
        </div>
      </article>
      <section v-if="resource && showPendingResource && !agent && thread.mode !== 'CLOSED'" class="pending-card">
        <template v-if="resource.resourceType === 'PRODUCT'">
          <img v-if="resourceProduct" :src="productImage(resourceProduct.id, resourceProduct.picUrl)" :alt="resourceProduct.name" />
          <div><span>正在咨询这件商品</span><b>{{ resourceLoading ? '正在读取商品信息…' : resourceProduct?.name || resourceError || '商品信息暂时无法加载' }}</b><strong v-if="resourceProduct">¥{{ formatMoney(resourceProduct.price) }}</strong></div>
          <el-button type="primary" :disabled="sending || failed || uncertain || resourceLoading || !resourceProduct" @click="sendResource(resource)">发送商品</el-button>
        </template>
        <template v-else>
          <div><span>咨询内容</span><b>正在咨询这个订单</b></div>
          <el-button type="primary" :disabled="sending || failed || uncertain" @click="sendResource(resource)">发送订单</el-button>
        </template>
      </section>
    </div>
    <p v-if="agent && !canReply" class="muted">领取本次会话后即可回复。</p>
    <div class="chat-actions">
      <div v-if="!agent && thread.mode === 'SELF_SERVICE' && thread.faq?.length" class="quick-questions"><el-button v-for="item in thread.faq" :key="String(item.id)" round :disabled="sending || failed || uncertain" @click="sendQuickQuestion(item.question)">{{ item.question }}</el-button></div>
      <el-button class="read-button" text :disabled="loadedCursor === '0' || failed" :loading="acknowledging" @click="acknowledge">标记已读</el-button>
    </div>
    <div v-if="thread.mode !== 'CLOSED'" class="composer">
      <el-input v-model="input" type="textarea" :rows="3" maxlength="2000" resize="none" :disabled="sending || failed || uncertain || !canReply" placeholder="请输入您想咨询的内容……" @keydown.ctrl.enter="send" />
      <div><span>请勿发送密码、验证码等敏感信息</span><el-button v-if="!agent && thread.mode === 'SELF_SERVICE'" :disabled="sending || failed || uncertain" @click="human">转人工</el-button><el-button type="primary" :loading="sending" :disabled="failed || uncertain || !canReply || !input.trim()" @click="send">发送</el-button></div>
    </div>
  </section>
</template>
<style scoped>
.kefu-thread{overflow:hidden;border:1px solid #e5e9ef;border-radius:12px;background:#fff;box-shadow:0 8px 30px rgba(31,45,61,.05)}
.chat-toolbar{display:flex;align-items:center;gap:12px;padding:16px 20px;border-bottom:1px solid #e8ebf0;background:#fff}.chat-toolbar>.el-button{margin-left:auto}.service-avatar,.welcome-avatar,.bubble-avatar{display:grid;place-items:center;color:#fff;background:linear-gradient(145deg,#138b78,#0d6e62);font-weight:700}.service-avatar{width:42px;height:42px;border-radius:12px}.service-meta h2{margin:0 0 5px;font-size:17px}.service-meta p{display:flex;align-items:center;gap:6px;margin:0;color:#7a8492;font-size:13px}.service-meta i{width:7px;height:7px;border-radius:50%;background:#aab2bd}.service-meta i.online{background:#20b486;box-shadow:0 0 0 3px #dff6ee}
.error,.delivery-warning{padding:12px 18px}.error{color:#a73826;background:#fff2ed}.delivery-warning{display:flex;align-items:center;gap:12px;color:#875d18;background:#fff8e8}.delivery-warning span{font-size:13px;margin-right:auto}
.messages{height:500px;overflow-y:auto;padding:24px 28px;background:#f5f7fb}.chat-empty{display:flex;flex-direction:column;align-items:center;padding:30px 0 42px;color:#778293}.chat-empty>span{font-size:13px;margin-bottom:16px}.chat-empty .welcome-avatar{width:54px;height:54px;border-radius:50%;margin-bottom:12px;box-shadow:0 8px 18px #0d6e6230}.chat-empty b{font-size:17px;color:#24303d}.chat-empty p{margin:8px 0 0;font-size:13px}
.messages article{display:flex;align-items:flex-start;gap:10px;margin-bottom:20px}.messages article.mine{flex-direction:row-reverse}.bubble-avatar{flex:0 0 auto;width:34px;height:34px;border-radius:50%;font-size:13px}.messages article.mine .bubble-avatar{background:linear-gradient(145deg,#ff8d58,#e4562e)}.bubble-wrap{max-width:72%}.bubble-wrap>small{display:block;margin:0 3px 6px;color:#8b94a1;font-size:12px}.messages article.mine .bubble-wrap>small{text-align:right}.bubble{white-space:pre-wrap;overflow-wrap:anywhere;margin:0;padding:12px 16px;border:1px solid #e2e7ed;border-radius:4px 16px 16px 16px;background:#fff;line-height:1.65;box-shadow:0 3px 10px rgba(31,45,61,.04)}.messages article.mine .bubble{border-color:#dcefe9;border-radius:16px 4px 16px 16px;background:#e9f7f2}.messages article.notice{justify-content:center}.messages article.notice .bubble-avatar{display:none}.messages article.notice .bubble{border:0;background:#e9edf2;color:#6f7884;font-size:12px;box-shadow:none}.messages article.notice .bubble-wrap>small{display:none}
.resource-card{display:grid;grid-template-columns:88px minmax(0,1fr) auto;align-items:center;gap:14px;width:min(570px,65vw);margin-top:6px;padding:14px;border:1px solid #e2e6eb;border-radius:10px;background:#fff;box-shadow:0 5px 18px rgba(31,45,61,.06)}.resource-card img{width:88px;height:88px;object-fit:cover;border-radius:7px;background:#f1f3f5}.resource-card b{display:block;line-height:1.5}.resource-card p{display:-webkit-box;overflow:hidden;margin:7px 0;color:#7c8590;font-size:12px;line-height:1.5;-webkit-line-clamp:2;-webkit-box-orient:vertical}.resource-card strong{color:#e64b24;font-size:18px}.resource-card a{align-self:end;color:var(--brand);white-space:nowrap}
.pending-card{display:flex;align-items:center;justify-content:flex-end;gap:16px;width:min(560px,78%);margin:4px 0 10px auto;padding:14px 16px;border:1px dashed #cfd7e1;border-radius:10px;background:#fff}.pending-card>img{flex:0 0 auto;width:72px;height:72px;object-fit:cover;border-radius:7px;background:#f1f3f5}.pending-card div{display:grid;min-width:0;gap:5px;margin-right:auto}.pending-card span{color:#7a8492;font-size:12px}.pending-card b{overflow:hidden;font-size:14px;line-height:1.5;text-overflow:ellipsis;white-space:nowrap}.pending-card strong{color:#e64b24;font-size:17px}
.chat-actions{display:flex;align-items:center;min-height:54px;padding:9px 16px;border-top:1px solid #e6eaf0;background:#fff}.quick-questions{display:flex;flex-wrap:wrap;gap:8px}.quick-questions .el-button{margin:0;border-color:#e1e6eb;color:#475364;background:#f8fafc}.read-button{margin-left:auto;color:#8a94a1}.muted{margin:0;padding:10px 18px;color:#788390;font-size:12px}
.composer{padding:14px 16px;border-top:1px solid #e6eaf0;background:#fff}.composer :deep(.el-textarea__inner){min-height:82px;border:0;box-shadow:none;padding:8px 4px;font-size:15px}.composer>div{display:flex;align-items:center;justify-content:flex-end;gap:10px;margin-top:8px}.composer span{margin-right:auto;color:#9aa2ad;font-size:12px}.composer .el-button--primary{min-width:88px;background:#e74d2a;border-color:#e74d2a}
@media(max-width:900px){.messages{height:440px;padding:18px 14px}.bubble-wrap{max-width:84%}.resource-card{grid-template-columns:64px minmax(0,1fr);width:min(430px,72vw)}.resource-card img{width:64px;height:64px}.resource-card a{grid-column:2}.composer span{display:none}}
</style>
