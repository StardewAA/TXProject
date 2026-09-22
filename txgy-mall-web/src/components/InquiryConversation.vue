<script setup lang="ts">
import { ref } from 'vue'
import type { InquiryMessage } from '@/api/inquiry-market'
import { formatDate } from '@/utils/format'

const props = defineProps<{
  messages: InquiryMessage[]
  loading?: boolean
  sending?: boolean
  disabled?: boolean
  side?: 'BUYER' | 'SUPPLIER'
}>()
const emit = defineEmits<{
  send: [content: string, complete: (success: boolean) => void]
  refresh: []
}>()
const draft = ref('')

function send() {
  const content = draft.value.trim()
  if (!content || props.disabled || props.sending) return
  // 等服务端确认成功后再清空；超时或断网时保留原文以便同键重试。
  emit('send', content, (success) => {
    if (success && draft.value.trim() === content) draft.value = ''
  })
}
</script>

<template>
  <section class="conversation" aria-label="询价沟通记录">
    <header><h3>沟通记录</h3><button type="button" :disabled="loading" @click="emit('refresh')">刷新</button></header>
    <div v-if="loading" class="notice">正在加载沟通记录…</div>
    <div v-else-if="!messages.length" class="notice">暂无消息，可围绕交期、规格和报价补充沟通。</div>
    <ol v-else>
      <li v-for="(message, index) in messages" :key="message.id || message.clientMessageId || index" :class="{ own: side === message.senderSide }">
        <div><strong>{{ message.senderName || (message.senderSide === 'BUYER' ? '采购方' : '供应方') }}</strong><time>{{ formatDate(message.createTime) }}</time></div>
        <p>{{ message.content }}</p>
      </li>
    </ol>
    <form @submit.prevent="send">
      <textarea v-model="draft" maxlength="500" :disabled="disabled || sending" placeholder="输入沟通内容；最终成交以采纳后的订单为准" />
      <button type="submit" :disabled="disabled || sending || !draft.trim()">{{ sending ? '发送中…' : '发送消息' }}</button>
    </form>
  </section>
</template>

<style scoped>
.conversation{border:1px solid #dbe7e4;border-radius:8px;background:#f8fbfa;padding:18px}.conversation header{display:flex;align-items:center;justify-content:space-between}.conversation h3{margin:0}.conversation button{border:1px solid #167664;border-radius:5px;background:white;color:#126b59;padding:8px 14px;cursor:pointer}.conversation button:disabled{opacity:.55;cursor:not-allowed}.conversation ol{list-style:none;margin:16px 0;padding:0;display:grid;gap:12px;max-height:360px;overflow:auto}.conversation li{width:min(78%,620px);border:1px solid #dce6e4;border-radius:8px;background:white;padding:12px}.conversation li.own{margin-left:auto;background:#eaf6f2;border-color:#bedbd2}.conversation li>div{display:flex;justify-content:space-between;gap:16px;color:#526b67;font-size:12px}.conversation p{margin:8px 0 0;white-space:pre-wrap;overflow-wrap:anywhere;color:#243b38}.notice{padding:28px 0;text-align:center;color:#64748b}.conversation form{display:grid;grid-template-columns:1fr auto;gap:12px}.conversation textarea{min-height:72px;resize:vertical;border:1px solid #cbd9d6;border-radius:6px;padding:10px;font:inherit}.conversation form button{align-self:end;background:#167664;color:white}
</style>
