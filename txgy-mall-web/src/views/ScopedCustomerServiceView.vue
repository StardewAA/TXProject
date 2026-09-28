<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { kefuChatApi } from '@/api/kefu-chat'
import KefuConversation from '@/components/KefuConversation.vue'
import MemberPage from '@/components/MemberPage.vue'
import { useAccountOperation } from '@/composables/useAccountOperation'
import { getAccountRequestTenant, getTenantContext } from '@/utils/tenant-context'
import { assertKefuThread, kefuScope, resourceFromQuery, scopedChatEnabled } from '@/utils/kefu-chat'
import type { KefuCapability, KefuThread } from '@/types/kefu-chat'
const route = useRoute(), operation = useAccountOperation()
const capability = ref<KefuCapability | null>(null), thread = ref<KefuThread | null>(null), loading = ref(false), error = ref('')
const openProductSkuId = ref<string>()
const enabled = computed(() => scopedChatEnabled(capability.value)), shop = computed(() => route.params.shopId)
const resource = computed(() => { try { return resourceFromQuery(route.query) } catch { return undefined } })
let generation = 0
function productSkuFromQuery(scope: ReturnType<typeof kefuScope>) {
  const raw = route.query.productSkuId
  if (raw === undefined) return undefined
  if (scope.scope !== 'SHOP' || !/^[1-9]\d*$/.test(String(raw))) throw new Error('商品客服入口缺少有效规格')
  return String(raw)
}
async function load() {
  const request = ++generation
  loading.value = true; error.value = ''; thread.value = null; capability.value = null; openProductSkuId.value = undefined
  const current = () => operation.current() && request === generation
  try {
    const scope = kefuScope(shop.value)
    resourceFromQuery(route.query)
    const productSkuId = productSkuFromQuery(scope)
    openProductSkuId.value = productSkuId
    const config = await kefuChatApi.capability()
    if (!current()) return
    capability.value = config
    if (!scopedChatEnabled(config)) return
    const value = await kefuChatApi.open(productSkuId ? { ...scope, productSkuId } : scope)
    // Private conversations belong to the authenticated account tenant. On the
    // platform portal that can intentionally differ from the public browse tenant.
    const expectedTenant = scope.scope === 'PLATFORM' ? getAccountRequestTenant() : undefined
    if (current()) thread.value = assertKefuThread(value, scope, expectedTenant, undefined, getTenantContext().accountUserId)
  } catch (reason) { if (current()) error.value = reason instanceof Error ? reason.message : '客服暂时无法打开' }
  finally { if (current()) loading.value = false }
}
watch(() => route.fullPath, load, { immediate: true })
onBeforeUnmount(() => { generation++ })
</script>
<template>
  <MemberPage :title="shop ? '店铺客服' : '平台客服'">
    <div v-if="loading" class="loading-box"><el-skeleton :rows="6" animated /></div>
    <section v-else-if="error" class="chat-state" role="alert"><h2>会话暂不可用</h2><p>{{ error }}</p><el-button @click="load">重新核验</el-button></section>
    <section v-else-if="!enabled" class="chat-state" data-testid="kefu-disabled"><h2>{{ shop ? '本店客服暂未开通' : '平台客服暂未开通' }}</h2><p>您可以先浏览商品或查看订单，稍后再来咨询。</p><MallLink to="/member/orders">查看我的订单 →</MallLink></section>
    <template v-else-if="thread"><KefuConversation :key="thread.session.id" :initial="thread" :resource="resource" :portal-product="!!openProductSkuId" @update="thread = $event"/><div v-if="thread.mode === 'CLOSED'" class="chat-state restart"><p>本次服务已结束</p><el-button type="primary" @click="load">再次咨询</el-button></div></template>
  </MemberPage>
</template>
<style scoped>.chat-state{text-align:center;padding:45px 25px;background:#f7f9fc;line-height:1.8;border-radius:12px}.chat-state h2{font-size:20px}.chat-state p{color:#6b7685;font-size:14px;line-height:1.8}.chat-state a{color:var(--brand)}.chat-state.restart{padding:20px;background:#fff;border-radius:0 0 12px 12px}</style>
