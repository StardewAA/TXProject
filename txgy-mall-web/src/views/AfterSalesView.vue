<script setup lang="ts">
import { onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import { tradeApi } from '@/api/mall'
import EmptyState from '@/components/EmptyState.vue'
import MemberPage from '@/components/MemberPage.vue'
import type { AfterSale, Identifier } from '@/types/mall'
import { formatDate, formatMoney, normalizeImage } from '@/utils/format'

const statusNames: Record<number, string> = {
  10: '申请中', 20: '商家已同意，请退货', 30: '等待商家收货', 40: '等待退款',
  50: '退款完成', 61: '已取消', 62: '商家已拒绝', 63: '商家拒绝收货',
}
const list = ref<AfterSale[]>([])
const loading = ref(true)
const submitting = ref(false)
const deliveryVisible = ref(false)
const expresses = ref<Array<{ id: Identifier; name: string }>>([])
const deliveryForm = reactive<{ id: Identifier | ''; logisticsId: Identifier | ''; logisticsNo: string }>({
  id: '', logisticsId: '', logisticsNo: '',
})

async function load() {
  loading.value = true
  try { list.value = (await tradeApi.getAfterSales({ pageNo: 1, pageSize: 20 })).list || [] } finally { loading.value = false }
}

async function openDelivery(item: AfterSale) {
  deliveryForm.id = item.id
  deliveryForm.logisticsNo = ''
  if (!expresses.value.length) expresses.value = await tradeApi.getDeliveryExpresses()
  deliveryForm.logisticsId = expresses.value[0]?.id || ''
  deliveryVisible.value = true
}

async function submitDelivery() {
  if (!deliveryForm.id || !deliveryForm.logisticsId || !deliveryForm.logisticsNo.trim()) {
    return ElMessage.warning('请选择物流公司并填写物流单号')
  }
  submitting.value = true
  try {
    await tradeApi.deliverAfterSale({
      id: deliveryForm.id,
      logisticsId: deliveryForm.logisticsId,
      logisticsNo: deliveryForm.logisticsNo.trim(),
    })
    ElMessage.success('退货物流已提交')
    deliveryVisible.value = false
    await load()
  } finally { submitting.value = false }
}

async function cancel(item: AfterSale) {
  await ElMessageBox.confirm('确定取消这次售后申请吗？', '取消售后', { type: 'warning', confirmButtonText: '确定取消', cancelButtonText: '返回' })
  await tradeApi.cancelAfterSale(item.id)
  ElMessage.success('售后申请已取消')
  await load()
}

function refreshWhenFocused() {
  void load()
}

onMounted(() => {
  void load()
  window.addEventListener('focus', refreshWhenFocused)
})
onUnmounted(() => window.removeEventListener('focus', refreshWhenFocused))
</script>

<template>
  <MemberPage title="退款售后" description="查看 PC 与移动端共同提交的售后记录">
    <div v-if="loading" class="loading-box"><el-skeleton :rows="8" animated /></div>
    <EmptyState v-else-if="!list.length" title="暂无售后记录" description="符合售后条件的订单可从订单详情发起申请" />
    <div v-else class="after-list">
      <article v-for="item in list" :key="item.id">
        <div class="after-image"><img v-if="item.picUrl" :src="normalizeImage(item.picUrl)" alt="商品" /></div>
        <div>
          <strong>{{ item.spuName || '售后商品' }}</strong>
          <p>{{ item.applyReason || item.reason || '售后申请' }}</p>
          <small>售后单：{{ item.no || item.id }}　{{ formatDate(item.createTime) }}</small>
          <small v-if="item.logisticsNo">退货物流单号：{{ item.logisticsNo }}</small>
        </div>
        <div class="after-actions">
          <b>{{ item.statusName || statusNames[item.status] || `状态 ${item.status}` }}</b>
          <span>退款金额 ¥{{ formatMoney(item.refundPrice) }}</span>
          <el-button v-if="item.status === 20" type="primary" @click="openDelivery(item)">填写退货物流</el-button>
          <el-button v-if="[10, 20].includes(item.status)" @click="cancel(item)">取消售后</el-button>
        </div>
      </article>
    </div>
    <el-dialog v-model="deliveryVisible" title="填写退货物流" width="480px">
      <el-form label-position="top">
        <el-form-item label="物流公司">
          <el-select v-model="deliveryForm.logisticsId" placeholder="请选择物流公司" style="width: 100%">
            <el-option v-for="item in expresses" :key="String(item.id)" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="物流单号">
          <el-input v-model="deliveryForm.logisticsNo" maxlength="64" placeholder="请输入退货物流单号" />
        </el-form-item>
      </el-form>
      <template #footer><el-button @click="deliveryVisible = false">取消</el-button><el-button type="primary" :loading="submitting" @click="submitDelivery">提交物流</el-button></template>
    </el-dialog>
  </MemberPage>
</template>

<style scoped lang="scss">
.after-list article { display: grid; grid-template-columns: 84px 1fr 210px; gap: 16px; align-items: center; padding: 18px; border-bottom: 1px solid var(--line); }
.after-image { width: 76px; height: 76px; background: #f5f5f5; }
.after-image img { width: 100%; height: 100%; object-fit: cover; }
.after-list p, .after-list small { display: block; color: var(--muted); }
.after-actions { text-align: right; }
.after-actions b, .after-actions span { display: block; }
.after-actions b { color: var(--brand); }
.after-actions span { margin: 10px 0; color: #59666f; }
.after-actions .el-button { margin-top: 6px; }
</style>
