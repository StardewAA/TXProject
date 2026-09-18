<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAccountMessageBox } from '@/composables/useAccountOperation'
const ElMessageBox = useAccountMessageBox()
import { supplierApi, type SupplierProduct } from '@/api/supplier'
import { enterpriseApi } from '@/api/enterprise'
import type { Shop } from '@/types/mall'
import ProductReviewHistory from '@/components/ProductReviewHistory.vue'
import { createProductReviewRequestGate, productReviewActions, productReviewCommand, productReviewLabel, productReviewOptions } from '@/utils/product-review'
const route = useRoute(), router = useRouter(), shopId = computed(() => Number(route.params.shopId))
const shop = ref<Shop>(), rows = ref<SupplierProduct[]>([])
const busy = ref(false), mutating = ref(false), error = ref(false), actionError = ref('')
const page = ref(1), total = ref(0), name = ref(''), status = ref<number>(), reviewStatus = ref<number>()
const gate = createProductReviewRequestGate()
let mutationGeneration = 0
function search() { if (mutating.value) return; page.value = 1; void load() }
async function load() {
  const ticket = gate.begin(), scope = shopId.value
  busy.value = true; error.value = false
  try {
    const shops = await enterpriseApi.salesShops()
    if (!gate.isCurrent(ticket)) return
    shop.value = shops.find(s => Number(s.id) === scope)
    if (!shop.value) { rows.value = []; await router.replace('/sales'); return }
    const result = await supplierApi.page({ shopId: scope, pageNo: page.value, pageSize: 15, name: name.value, status: status.value, reviewStatus: reviewStatus.value })
    if (!gate.isCurrent(ticket)) return
    rows.value = result.list; total.value = result.total
  } catch { if (gate.isCurrent(ticket)) { error.value = true; rows.value = []; total.value = 0 } }
  finally { if (gate.isCurrent(ticket)) busy.value = false }
}
async function act(productId: unknown, action: 'submit' | 'withdraw' | 'publish' | 'unpublish') {
  const row = rows.value.find(product => product.id === productId)
  if (!row) return
  if (busy.value || mutating.value || !productReviewActions(row)[action]) return
  const ticket = gate.current(), scope = shopId.value
  const wording = {
    submit: row.status === 1 ? '提交审核会先下架当前商品，审核通过后需自行重新上架。确认提交？' : '确认将当前保存版本提交后台审核？待审核期间不能修改内容。',
    withdraw: '确认撤回当前审核申请？撤回后仍为下架草稿，可修改后重新送审。',
    publish: '确认上架当前审核通过版本？企业、店铺及快递配送条件仍由服务端再次校验。',
    unpublish: '确认下架当前商品？不会修改历史订单和成交价格。',
  }
  try { await ElMessageBox.confirm(wording[action], row.name, { confirmButtonText: '确定', cancelButtonText: '取消' }) } catch { return }
  if (!gate.isCurrent(ticket) || scope !== shopId.value || mutating.value) return
  const operation = ++mutationGeneration
  mutating.value = true; actionError.value = ''
  try {
    const command = productReviewCommand(scope, row)
    if (action === 'submit') await supplierApi.submitReview(command)
    else if (action === 'withdraw') await supplierApi.withdrawReview({ ...command, submissionId: row.submissionId! })
    else await supplierApi.status(scope, row.id!, action === 'publish' ? 1 : 0, command.expectedVersion)
    if (!gate.isCurrent(ticket)) return
    ElMessage.success({ submit: '已提交后台审核，尚未上架', withdraw: '已撤回，可修改后重新提交', publish: '商品已上架', unpublish: '商品已下架' }[action])
    await load()
  } catch (cause) { if (gate.isCurrent(ticket)) actionError.value = `${cause instanceof Error ? cause.message : '操作失败'}。未自动重试；请刷新核对最新版本及审核状态。` }
  finally { if (operation === mutationGeneration) mutating.value = false }
}
watch(shopId, () => { mutationGeneration++; mutating.value = false; rows.value = []; shop.value = undefined; page.value = 1; actionError.value = ''; void load() }, { immediate: true })
onBeforeUnmount(() => { gate.invalidate(); mutationGeneration++ })
</script>
<template>
  <section class="container panel supplier-products">
    <header><div><MallLink to="/sales">← 销售中心</MallLink><h1>{{ shop?.name || '授权店铺' }} · 商品管理</h1></div><MallLink v-if="shop" class="publish" :to="`/sales/shops/${shopId}/products/new`">新增商品草稿</MallLink></header>
    <el-alert title="保存草稿 → 提交后台审核 → 审核通过 → 主动上架。经营资料有修改须重新审核；待审核内容冻结，先撤回才能修改。" :closable="false" />
    <p class="hint">历史未修改商品可按原状态销售，未有审核记录不代表已批准；历史商品下架后重新上架需补审。库存和 WMS/ERP 流程不由商品审核修改。</p>
    <div class="filters"><el-input v-model="name" placeholder="商品名称" clearable :disabled="mutating" @keyup.enter="search" /><el-select v-model="status" placeholder="全部销售状态" clearable :disabled="mutating"><el-option :value="1" label="上架"/><el-option :value="0" label="下架"/></el-select><el-select v-model="reviewStatus" placeholder="全部审核状态" clearable :disabled="mutating"><el-option :value="-1" label="历史过渡 · 待补审"/><el-option v-for="option in productReviewOptions" :key="option.value" :value="option.value" :label="option.label"/></el-select><el-button :loading="busy" :disabled="mutating" @click="search">查询 / 刷新</el-button></div>
    <el-alert v-if="error" title="加载失败，请重试；不会展示其他店铺商品" type="error" :closable="false"/><el-alert v-if="actionError" :title="actionError" type="warning" :closable="false"/>
    <el-table :data="rows" v-loading="busy"><el-table-column prop="id" label="编号" width="100"/><el-table-column label="商品" min-width="230"><template #default="{row}"><div class="product"><img :src="row.picUrl" alt="商品图"/><span>{{row.name}}</span></div></template></el-table-column><el-table-column label="售价" width="110"><template #default="{row}">¥{{((row.price||0)/100).toFixed(2)}}</template></el-table-column><el-table-column prop="stock" label="库存缓存" width="100"/><el-table-column label="销售状态" width="100"><template #default="{row}">{{row.status===1?'上架':'下架'}}</template></el-table-column>
      <el-table-column label="审核 / 版本" min-width="180"><template #default="{row}"><el-tag :type="row.reviewStatus===20?'success':row.reviewStatus===30?'danger':'info'">{{ productReviewLabel(row.reviewStatus) }}</el-tag><p class="version">当前 v{{ row.contentVersion ?? '待读取' }}<span v-if="row.approvedVersion"> · 批准 v{{ row.approvedVersion }}</span></p><p v-if="row.reviewRemark" class="remark">{{ row.reviewRemark }}</p></template></el-table-column>
      <el-table-column label="操作" min-width="300"><template #default="{row}"><MallLink :to="`/sales/shops/${shopId}/products/${row.id}`">{{ productReviewActions(row).edit ? '编辑' : '查看' }}</MallLink><el-button v-if="productReviewActions(row).submit" link :disabled="busy||mutating" @click="act(row.id,'submit')">提交审核</el-button><el-button v-if="productReviewActions(row).withdraw" link :disabled="busy||mutating" @click="act(row.id,'withdraw')">撤回审核</el-button><el-button v-if="productReviewActions(row).publish" link type="primary" :disabled="busy||mutating" @click="act(row.id,'publish')">上架</el-button><el-button v-if="productReviewActions(row).unpublish" link :disabled="busy||mutating" @click="act(row.id,'unpublish')">下架</el-button><ProductReviewHistory :shop-id="shopId" :product-id="row.id" :refresh-key="row.submissionId" :disabled="busy||mutating"/></template></el-table-column>
    </el-table><el-pagination v-model:current-page="page" :disabled="mutating" :page-size="15" :total="total" layout="total, prev, pager, next" @current-change="load"/>
  </section>
</template>
<style scoped>
.supplier-products{padding:32px;margin:32px auto;min-height:560px}header{display:flex;justify-content:space-between;align-items:center;gap:20px;margin-bottom:24px}h1{margin:16px 0}.publish{padding:12px 22px;background:#167664;color:white;border-radius:6px;white-space:nowrap}.filters{display:flex;gap:16px;margin:24px 0;flex-wrap:wrap}.filters .el-input{max-width:320px}.filters .el-select{width:160px}.product{display:flex;align-items:center;gap:16px}.product img{width:56px;height:56px;object-fit:contain}a{color:#167664}.el-pagination{margin-top:24px}.hint,.version{font-size:13px;color:#64748b;margin:10px 0}.remark{font-size:13px;white-space:pre-wrap;overflow-wrap:anywhere}.el-alert+.el-alert{margin-top:12px}
</style>
