<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'

import { customerServiceApi, type CustomerMessage } from '@/api/mall'
import MemberPage from '@/components/MemberPage.vue'
import { formatDate } from '@/utils/format'

const messages = ref<CustomerMessage[]>([])
const input = ref('')
const loading = ref(true)
const sending = ref(false)
const messageBox = ref<HTMLElement>()
let timer = 0

function contentText(content: string) {
  try { return JSON.parse(content)?.text || content } catch { return content }
}

async function load(silent = false) {
  if (!silent) loading.value = true
  try {
    const list = await customerServiceApi.getMessages({ limit: 100 })
    messages.value = [...(list || [])].reverse()
    const conversationId = messages.value.at(-1)?.conversationId
    if (conversationId) void customerServiceApi.markRead(conversationId).catch(() => undefined)
    await nextTick()
    if (messageBox.value) messageBox.value.scrollTop = messageBox.value.scrollHeight
  } finally { if (!silent) loading.value = false }
}

async function send() {
  const text = input.value.trim()
  if (!text) return ElMessage.warning('请输入消息内容')
  sending.value = true
  try {
    await customerServiceApi.sendMessage(text)
    input.value = ''
    await load(true)
  } finally { sending.value = false }
}

onMounted(() => { void load(); timer = window.setInterval(() => void load(true), 5000) })
onBeforeUnmount(() => window.clearInterval(timer))
</script>

<template>
  <MemberPage title="在线客服" description="消息与后台客服中心实时同步">
    <div class="service-status"><i></i><div><b>平台客服</b><span>在线咨询商品、订单、配送及售后问题</span></div></div>
    <div ref="messageBox" class="message-box" v-loading="loading">
      <div v-if="!loading && !messages.length" class="message-empty">暂无消息，发送第一条咨询吧</div>
      <article v-for="message in messages" :key="message.id" :class="{ mine: message.senderType === 1 }"><div><p>{{ contentText(message.content) }}</p><small>{{ message.senderType === 1 ? '我' : '平台客服' }} · {{ formatDate(message.createTime) }}</small></div></article>
    </div>
    <div class="message-input"><el-input v-model="input" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="请输入需要咨询的问题" @keydown.ctrl.enter="send" /><div><span>Ctrl + Enter 发送</span><el-button type="primary" :loading="sending" @click="send">发送消息</el-button></div></div>
  </MemberPage>
</template>

<style scoped lang="scss">
.service-status { display: flex; align-items: center; gap: 13px; padding: 16px 20px; border-radius: 8px 8px 0 0; background: #f7f8f9; }.service-status i { width: 11px; height: 11px; border-radius: 50%; background: #24a46d; box-shadow: 0 0 0 5px #dff4eb; }.service-status b, .service-status span { display: block; }.service-status span { margin-top: 4px; color: var(--muted); font-size: 13px; }.message-box { height: 430px; overflow-y: auto; padding: 24px; border: 1px solid var(--line); background: #fbfcfc; }.message-box article { display: flex; margin-bottom: 18px; }.message-box article.mine { justify-content: flex-end; }.message-box article > div { max-width: 65%; }.message-box p { margin: 0; padding: 12px 16px; border-radius: 3px 14px 14px; background: white; box-shadow: 0 2px 8px rgba(31,42,54,.08); line-height: 1.6; }.message-box .mine p { border-radius: 14px 3px 14px 14px; background: #fff0e8; }.message-box small { display: block; margin-top: 6px; color: #98a1a8; }.message-box .mine small { text-align: right; }.message-empty { padding: 150px 0; color: var(--muted); text-align: center; }.message-input { padding: 16px; border: 1px solid var(--line); border-top: 0; }.message-input > div { display: flex; justify-content: flex-end; align-items: center; gap: 16px; margin-top: 10px; }.message-input span { color: var(--muted); font-size: 12px; }
</style>
