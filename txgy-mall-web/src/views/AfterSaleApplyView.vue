<script setup lang="ts">
import { useBuyerNavigation } from '@/composables/useBuyerNavigation'
const { buyerTo } = useBuyerNavigation()
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'

import { tradeApi } from '@/api/mall'
import MemberPage from '@/components/MemberPage.vue'
import type { OrderItem, TradeOrder } from '@/types/mall'
import { formatMoney, normalizeImage, propertyText } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const order = ref<TradeOrder | null>(null)
const item = ref<OrderItem | null>(null)
const loading = ref(true)
const submitting = ref(false)
const reasons = ref<string[]>(['其他'])
const form = reactive({ way: 10, applyReason: '', applyDescription: '', applyPicUrlsText: '' })
const refundPrice = computed(() => Number(item.value?.payPrice ?? (item.value ? item.value.price * item.value.count : 0)))
const wayOptions = computed(() => order.value?.status === 10 ? [{ label: '仅退款', value: 10 }] : [{ label: '仅退款', value: 10 }, { label: '退货退款', value: 20 }])

async function submit() {
  if (!item.value || !form.applyReason) return ElMessage.warning('请选择售后原因')
  if (refundPrice.value <= 0) return ElMessage.warning('退款金额无效')
  submitting.value = true
  try {
    await tradeApi.createAfterSale({
      orderItemId: item.value.id,
      way: form.way,
      refundPrice: refundPrice.value,
      applyReason: form.applyReason,
      applyDescription: form.applyDescription,
      applyPicUrls: form.applyPicUrlsText.split(/[\n,，]/).map((value) => value.trim()).filter(Boolean),
    })
    ElMessage.success('售后申请已提交')
    await router.replace(buyerTo('/member/after-sales'))
  } finally { submitting.value = false }
}

onMounted(async () => {
  const orderId = String(route.query.orderId || '')
  const itemId = String(route.query.itemId || '')
  if (!orderId || !itemId) { loading.value = false; return }
  try {
    const [orderData, config] = await Promise.all([tradeApi.getOrder(orderId), tradeApi.getConfig()])
    order.value = orderData
    item.value = orderData.items?.find((entry) => String(entry.id) === itemId) || null
    const configured = form.way === 10 ? config.afterSaleRefundReasons : config.afterSaleReturnReasons
    if (Array.isArray(configured) && configured.length) reasons.value = configured.map(String)
  } finally { loading.value = false }
})
</script>

<template>
  <MemberPage title="申请售后" description="提交后可在 PC 与移动端查看处理进度">
    <div v-if="loading" class="loading-box"><el-skeleton :rows="8" animated /></div>
    <el-result v-else-if="!item" icon="warning" title="订单商品不存在" sub-title="请从订单详情重新进入"><template #extra><el-button @click="router.push(buyerTo('/member/orders'))">返回订单</el-button></template></el-result>
    <template v-else>
      <div class="apply-product"><div><img v-if="item.picUrl" :src="normalizeImage(item.picUrl)" alt="商品" /></div><section><strong>{{ item.spuName || '商品' }}</strong><p>{{ propertyText(item.properties) }}</p><span>购买数量：{{ item.count }}</span></section><b>可申请金额 ¥{{ formatMoney(refundPrice) }}</b></div>
      <el-form class="apply-form" label-position="top"><el-form-item label="售后方式"><el-radio-group v-model="form.way"><el-radio-button v-for="option in wayOptions" :key="option.value" :value="option.value">{{ option.label }}</el-radio-button></el-radio-group></el-form-item><el-form-item label="申请原因"><el-select v-model="form.applyReason" placeholder="请选择原因"><el-option v-for="reason in reasons" :key="reason" :label="reason" :value="reason" /></el-select></el-form-item><el-form-item label="补充说明"><el-input v-model="form.applyDescription" type="textarea" :rows="5" maxlength="500" show-word-limit /></el-form-item><el-form-item label="凭证图片地址"><el-input v-model="form.applyPicUrlsText" type="textarea" :rows="3" placeholder="可选；多个已上传图片地址使用逗号或换行分隔" /></el-form-item><el-button type="primary" size="large" :loading="submitting" @click="submit">提交售后申请</el-button></el-form>
    </template>
  </MemberPage>
</template>

<style scoped lang="scss">
.apply-product { display: grid; grid-template-columns: 90px 1fr 220px; gap: 18px; align-items: center; padding: 20px; background: #f7f8f9; }.apply-product > div { width: 82px; height: 82px; background: #eee; }.apply-product img { width: 100%; height: 100%; object-fit: cover; }.apply-product p, .apply-product span { color: var(--muted); }.apply-product b { color: #df421f; text-align: right; }.apply-form { max-width: 680px; margin-top: 26px; }
</style>
