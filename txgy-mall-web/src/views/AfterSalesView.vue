<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAccountMessageBox } from '@/composables/useAccountOperation'
import { useBuyerNavigation } from '@/composables/useBuyerNavigation'
const ElMessageBox = useAccountMessageBox()
const { buyerTo } = useBuyerNavigation()

import { tradeApi } from '@/api/mall'
import EmptyState from '@/components/EmptyState.vue'
import MemberPage from '@/components/MemberPage.vue'
import OrderWorkbench from '@/components/OrderWorkbench.vue'
import { useOrderWorkbench, useOrderWorkbenchRows } from '@/composables/useOrderWorkbench'
import { workbenchRowEntries } from '@/utils/order-workbench'
import type { AfterSale, Identifier } from '@/types/mall'
import { formatDate, formatMoney, normalizeImage } from '@/utils/format'

const statusNames: Record<number, string> = {
  10: '申请中', 20: '商家已同意，请退货', 30: '等待商家收货', 40: '等待退款',
  50: '退款完成', 61: '已取消', 62: '商家已拒绝', 63: '商家拒绝收货',
}
const list = ref<AfterSale[]>([])
const route = useRoute(), router = useRouter()
const page = ref(1), total = ref(0), status = ref<number>(), failed = ref('')
const selectedId = computed(() => { const value=Number(route.query.openAfter); return Number.isSafeInteger(value)&&value>0 ? value : undefined })
const workbench = useOrderWorkbench()
const notices = useOrderWorkbenchRows(workbench, computed(()=>({afterSaleIds:list.value.map(row=>row.id).join(',')})))
function rowNotices(id:Identifier){return workbenchRowEntries(notices.snapshot.value?.rowEntries||[],id,true)}
const loading = ref(true)
const submitting = ref(false)
const deliveryVisible = ref(false)
const expresses = ref<Array<{ id: Identifier; name: string }>>([])
const deliveryForm = reactive<{ id: Identifier | ''; logisticsId: Identifier | ''; logisticsNo: string }>({
  id: '', logisticsId: '', logisticsNo: '',
})

let requestVersion = 0
async function load() {
  const version = ++requestVersion
  loading.value = true
  failed.value = ''
  try {
    if(selectedId.value){
      const row=await tradeApi.getAfterSale(selectedId.value)
      if(version!==requestVersion)return
      list.value=row?[row]:[];total.value=list.value.length
    }else{
      const result=await tradeApi.getAfterSales({pageNo:page.value,pageSize:20,status:status.value})
      if(version!==requestVersion)return
      list.value=result.list||[];total.value=result.total
    }
  } catch(error){if(version===requestVersion)failed.value=error instanceof Error?error.message:'售后记录加载失败'}
  finally { if(version===requestVersion)loading.value = false }
}
function clearSelection(){const query={...route.query};delete query.openAfter;void router.replace({path:route.path,query})}

async function openDelivery(item: AfterSale) {
  const key=workbench.key.value,id=item.id
  deliveryForm.id = item.id
  deliveryForm.logisticsNo = ''
  if (!expresses.value.length) {
    const result=await tradeApi.getDeliveryExpresses()
    if(!key||key!==workbench.key.value||deliveryForm.id!==id)return
    expresses.value=result
  }
  if(!key||key!==workbench.key.value||deliveryForm.id!==id)return
  deliveryForm.logisticsId = expresses.value[0]?.id || ''
  deliveryVisible.value = true
  void workbench.read(rowNotices(item.id)).catch(()=>{})
}

async function submitDelivery() {
  if (!deliveryForm.id || !deliveryForm.logisticsId || !deliveryForm.logisticsNo.trim()) {
    return ElMessage.warning('请选择物流公司并填写物流单号')
  }
  submitting.value = true
  const key=workbench.key.value
  try {
    if(!key)return
    await tradeApi.deliverAfterSale({
      id: deliveryForm.id,
      logisticsId: deliveryForm.logisticsId,
      logisticsNo: deliveryForm.logisticsNo.trim(),
    })
    if(key!==workbench.key.value)return
    ElMessage.success('退货物流已提交')
    deliveryVisible.value = false
    await load()
    await workbench.refresh()
  } finally { submitting.value = false }
}

async function cancel(item: AfterSale) {
  const key=workbench.key.value,id=item.id
  try { await ElMessageBox.confirm('确定取消这次售后申请吗？', '取消售后', { type: 'warning', confirmButtonText: '确定取消', cancelButtonText: '返回' }) } catch { return }
  if(!key||key!==workbench.key.value)return
  await tradeApi.cancelAfterSale(id)
  if(key!==workbench.key.value)return
  ElMessage.success('售后申请已取消')
  await load()
  await workbench.refresh()
}

function refreshWhenFocused() {
  if(!deliveryVisible.value&&!submitting.value)void load()
}
watch([selectedId,page,status],()=>{if(!deliveryVisible.value)void load()},{immediate:true})
watch(selectedId,()=>{list.value=[];total.value=0},{flush:'sync'})
watch(()=>workbench.state.value.updatedAt,()=>{if(!deliveryVisible.value&&!submitting.value&&!loading.value)void load()})
watch(workbench.key,(key)=>{++requestVersion;loading.value=false;list.value=[];total.value=0;deliveryVisible.value=false;deliveryForm.id='';deliveryForm.logisticsId='';deliveryForm.logisticsNo='';expresses.value=[];if(key)void load()},{flush:'sync'})

onMounted(() => {
  window.addEventListener('focus', refreshWhenFocused)
})
onUnmounted(() => {requestVersion+=1;window.removeEventListener('focus', refreshWhenFocused)})
</script>

<template>
  <MemberPage title="退款售后" description="查看 PC 与移动端共同提交的售后记录">
    <OrderWorkbench :navigation-blocked="deliveryVisible||submitting" />
    <div class="after-filters"><template v-if="selectedId"><span>正在查看提醒关联的售后单 #{{selectedId}}</span><el-button @click="clearSelection">返回全部售后</el-button></template><template v-else><el-select v-model="status" clearable placeholder="全部售后状态" @change="page=1"><el-option v-for="(name,value) in statusNames" :key="value" :value="Number(value)" :label="name"/></el-select><el-button @click="load">刷新列表</el-button></template></div>
    <el-alert v-if="failed" :title="failed" type="error" :closable="false" />
    <div v-if="loading" class="loading-box"><el-skeleton :rows="8" animated /></div>
    <EmptyState v-else-if="!list.length" title="暂无售后记录" description="符合售后条件的订单可从订单详情发起申请" />
    <div v-else class="after-list">
      <article v-for="item in list" :key="item.id">
        <div class="after-image"><img v-if="item.picUrl" :src="normalizeImage(item.picUrl)" alt="商品" /></div>
        <div>
          <strong>{{ item.spuName || '售后商品' }}</strong>
          <b v-if="rowNotices(item.id).length" class="new-order">新待办</b>
          <p>{{ item.applyReason || item.reason || '售后申请' }}</p>
          <small>售后单：{{ item.no || item.id }}　{{ formatDate(item.createTime) }}</small>
          <small v-if="item.logisticsNo">退货物流单号：{{ item.logisticsNo }}</small>
          <p v-if="item.returnAddress">退货收件人：{{ item.returnReceiverName }}　{{ item.returnReceiverMobile }}<br />退货地址：{{ item.returnAddress }}</p>
          <p v-else-if="item.status === 20">此历史/平台审核售后未记录退货地址，请先联系商家确认后再寄出。</p>
          <p v-if="item.auditReason">审核说明：{{ item.auditReason }}</p>
          <p v-if="item.receiveReason">拒收原因：{{ item.receiveReason }}</p>
        </div>
        <div class="after-actions">
          <b>{{ item.statusName || statusNames[item.status] || `状态 ${item.status}` }}</b>
          <span>退款金额 ¥{{ formatMoney(item.refundPrice) }}</span>
          <MallLink v-if="item.shopId && item.orderId" :to="{ path: `/member/customer-service/shop/${item.shopId}`, query: { resourceType: 'ORDER', resourceId: item.orderId } }">售后沟通（关联本人订单）</MallLink><MallLink v-else :to="buyerTo('/member/orders')">从关联订单联系商家</MallLink>
          <el-button v-if="rowNotices(item.id).length" @click="workbench.read(rowNotices(item.id)).catch(()=>{})">此提醒标为已读</el-button>
          <el-button v-if="item.status === 20" type="primary" @click="openDelivery(item)">填写退货物流</el-button>
          <el-button v-if="[10, 20].includes(item.status)" @click="cancel(item)">取消售后</el-button>
        </div>
      </article>
    </div>
    <el-pagination v-if="!selectedId&&total>20" v-model:current-page="page" :page-size="20" :total="total" layout="prev,pager,next,total" />
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
.after-filters{display:flex;gap:12px;align-items:center;margin-bottom:18px}.after-filters .el-select{width:240px}.new-order{font-size:12px;color:#a74410;border:1px solid #a74410;padding:1px 5px;margin-left:8px}.el-pagination{margin-top:20px}
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
