<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { tradeApi } from '@/api/mall'

const route = useRoute()
const router = useRouter()
const success = ref(false)
const loading = ref(true)

onMounted(async () => {
  const orderId = String(route.query.orderId || '')
  if (orderId) {
    try { const order = await tradeApi.getOrder(orderId, true); success.value = Boolean(order.payStatus || order.status > 0 && order.status < 40) } catch { success.value = false }
  }
  loading.value = false
})
</script>

<template><div class="container page-section panel result-page" v-loading="loading"><el-result v-if="!loading" :icon="success ? 'success' : 'warning'" :title="success ? '支付成功' : '支付结果待确认'" :sub-title="success ? '后端已确认订单支付状态' : '请进入订单详情刷新最新状态'"><template #extra><el-button @click="router.push('/goods')">继续采购</el-button><el-button type="primary" @click="router.push('/member/orders')">查看订单</el-button></template></el-result></div></template>

<style scoped>.result-page { min-height: 480px; padding-top: 70px; }</style>
