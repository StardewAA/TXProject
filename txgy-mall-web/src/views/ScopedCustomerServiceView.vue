<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { kefuChatApi } from '@/api/kefu-chat'
import KefuConversation from '@/components/KefuConversation.vue'
import MemberPage from '@/components/MemberPage.vue'
import { useAccountOperation } from '@/composables/useAccountOperation'
import { getTenantContext } from '@/utils/tenant-context'
import { assertKefuThread, kefuScope, resourceFromQuery, scopedChatEnabled } from '@/utils/kefu-chat'
import type { KefuCapability, KefuThread } from '@/types/kefu-chat'
const route = useRoute(), operation = useAccountOperation()
const capability = ref<KefuCapability | null>(null), thread = ref<KefuThread | null>(null), loading = ref(false), error = ref('')
const enabled = computed(() => scopedChatEnabled(capability.value)), shop = computed(() => route.params.shopId)
const resource = computed(() => { try { return resourceFromQuery(route.query) } catch { return undefined } })
let generation = 0
async function load() {
  const request = ++generation
  loading.value = true; error.value = ''; thread.value = null; capability.value = null
  const current = () => operation.current() && request === generation
  try {
    const scope = kefuScope(shop.value)
    resourceFromQuery(route.query)
    const config = await kefuChatApi.capability()
    if (!current()) return
    capability.value = config
    if (!scopedChatEnabled(config)) return
    const value = await kefuChatApi.open(scope)
    if (current()) thread.value = assertKefuThread(value, scope, getTenantContext().browseTenantId, undefined, getTenantContext().accountUserId)
  } catch (reason) { if (current()) error.value = reason instanceof Error ? reason.message : '客服暂时无法打开' }
  finally { if (current()) loading.value = false }
}
watch(() => route.fullPath, load, { immediate: true })
onBeforeUnmount(() => { generation++ })
</script>
<template>
  <MemberPage :title="shop ? '店铺自助与人工客服' : '平台自助与人工客服'" description="固定问答来自已发布条目；人工坐席需独立授权，不自动承诺在线。">
    <div v-if="loading" class="loading-box"><el-skeleton :rows="6" animated /></div>
    <section v-else-if="error" class="chat-state" role="alert"><h2>会话暂不可用</h2><p>{{ error }}</p><el-button @click="load">重新核验</el-button></section>
    <section v-else-if="!enabled" class="chat-state" data-testid="kefu-disabled"><h2>店铺范围客服尚未开通</h2><p>{{ capability?.message || '正在准备独立坐席授权与接待能力。不会将你的消息自动发到旧平台或其他店铺。' }}</p><MallLink to="/member/customer-service">查看原平台客服入口与历史消息 →</MallLink></section>
    <template v-else-if="thread"><p class="scope-note">{{ thread.session.scope === 'SHOP' ? `店铺 #${thread.session.shopId}` : '平台服务' }} · 会话归属已由服务端核验</p><KefuConversation :key="thread.session.id" :initial="thread" :resource="resource" @update="thread = $event"/><div v-if="thread.mode === 'CLOSED'" class="chat-state"><el-button @click="load">重新发起本店咨询</el-button></div></template>
  </MemberPage>
</template>
<style scoped>.chat-state{text-align:center;padding:45px 25px;background:#f8faf9;line-height:1.8}.chat-state h2{font-size:20px}.chat-state p,.scope-note{color:#6b7b74;font-size:14px;line-height:1.8}.chat-state a{color:var(--brand)}</style>
