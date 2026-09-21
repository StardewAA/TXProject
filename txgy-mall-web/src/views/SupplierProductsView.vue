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
const pageOnline = computed(() => rows.value.filter(row => row.status === 1).length)
const pagePending = computed(() => rows.value.filter(row => row.reviewStatus === 10).length)
const pageApproved = computed(() => rows.value.filter(row => row.reviewStatus === 20).length)
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
    <header class="page-hero">
      <div>
        <MallLink class="breadcrumb" to="/sales">销售中心 / 店铺运营</MallLink>
        <p class="eyebrow">PRODUCT OPERATIONS</p>
        <h1>{{ shop?.name || '授权店铺' }} · 商品管理</h1>
        <p class="hero-description">集中管理商品资料、审核版本与上下架状态，保障店铺销售内容准确一致。</p>
      </div>
      <MallLink v-if="shop" class="publish" :to="`/sales/shops/${shopId}/products/new`">新增商品草稿</MallLink>
    </header>

    <div class="summary-grid">
      <article><span>商品总数</span><strong>{{ total }}</strong><small>当前筛选结果</small></article>
      <article><span>本页上架</span><strong>{{ pageOnline }}</strong><small>正在前台销售</small></article>
      <article><span>待审核</span><strong>{{ pagePending }}</strong><small>内容暂不可修改</small></article>
      <article><span>本页已通过</span><strong>{{ pageApproved }}</strong><small>可按版本上架</small></article>
    </div>

    <div class="workflow" aria-label="商品发布流程">
      <div><b>01</b><span><strong>保存草稿</strong><small>完善商品信息</small></span></div>
      <div><b>02</b><span><strong>提交审核</strong><small>冻结当前版本</small></span></div>
      <div><b>03</b><span><strong>审核通过</strong><small>获得销售资格</small></span></div>
      <div><b>04</b><span><strong>主动上架</strong><small>在商城前台展示</small></span></div>
    </div>
    <p class="hint">历史未修改商品可按原状态销售，未有审核记录不代表已批准；历史商品下架后重新上架需补审。商品审核不会修改库存。</p>
    <div class="filter-panel">
      <div class="filter-heading"><strong>商品筛选</strong><span>按名称、销售状态或审核进度快速定位商品</span></div>
      <div class="filters"><el-input v-model="name" placeholder="输入商品名称" clearable :disabled="mutating" @keyup.enter="search" /><el-select v-model="status" placeholder="全部销售状态" clearable :disabled="mutating"><el-option :value="1" label="上架"/><el-option :value="0" label="下架"/></el-select><el-select v-model="reviewStatus" placeholder="全部审核状态" clearable :disabled="mutating"><el-option :value="-1" label="历史过渡 · 待补审"/><el-option v-for="option in productReviewOptions" :key="option.value" :value="option.value" :label="option.label"/></el-select><el-button type="primary" :loading="busy" :disabled="mutating" @click="search">查询 / 刷新</el-button></div>
    </div>
    <el-alert v-if="error" title="加载失败，请重试；不会展示其他店铺商品" type="error" :closable="false"/><el-alert v-if="actionError" :title="actionError" type="warning" :closable="false"/>
    <div class="table-shell">
    <el-table :data="rows" v-loading="busy"><el-table-column prop="id" label="编号" width="100"/><el-table-column label="商品" min-width="250"><template #default="{row}"><div class="product"><img :src="row.picUrl" alt="商品图"/><span>{{row.name}}</span></div></template></el-table-column><el-table-column label="售价" width="120"><template #default="{row}"><strong class="price">¥{{((row.price||0)/100).toFixed(2)}}</strong></template></el-table-column><el-table-column prop="stock" label="库存缓存" width="110"/><el-table-column label="销售状态" width="110"><template #default="{row}"><el-tag :type="row.status===1?'success':'info'" effect="plain">{{row.status===1?'上架':'下架'}}</el-tag></template></el-table-column>
      <el-table-column label="审核 / 版本" min-width="180"><template #default="{row}"><el-tag :type="row.reviewStatus===20?'success':row.reviewStatus===30?'danger':'info'">{{ productReviewLabel(row.reviewStatus) }}</el-tag><p class="version">当前 v{{ row.contentVersion ?? '待读取' }}<span v-if="row.approvedVersion"> · 批准 v{{ row.approvedVersion }}</span></p><p v-if="row.reviewRemark" class="remark">{{ row.reviewRemark }}</p></template></el-table-column>
      <el-table-column label="操作" min-width="300"><template #default="{row}"><div class="operations"><MallLink :to="`/sales/shops/${shopId}/products/${row.id}`">{{ productReviewActions(row).edit ? '编辑' : '查看' }}</MallLink><el-button v-if="productReviewActions(row).submit" link :disabled="busy||mutating" @click="act(row.id,'submit')">提交审核</el-button><el-button v-if="productReviewActions(row).withdraw" link :disabled="busy||mutating" @click="act(row.id,'withdraw')">撤回审核</el-button><el-button v-if="productReviewActions(row).publish" link type="primary" :disabled="busy||mutating" @click="act(row.id,'publish')">上架</el-button><el-button v-if="productReviewActions(row).unpublish" link :disabled="busy||mutating" @click="act(row.id,'unpublish')">下架</el-button><ProductReviewHistory :shop-id="shopId" :product-id="row.id" :refresh-key="row.submissionId" :disabled="busy||mutating"/></div></template></el-table-column>
    </el-table>
    </div>
    <el-pagination v-model:current-page="page" :disabled="mutating" :page-size="15" :total="total" layout="total, prev, pager, next" @current-change="load"/>
  </section>
</template>
<style scoped>
.supplier-products{padding:0 0 32px;margin:28px auto;min-height:620px;overflow:hidden;border:1px solid #dbe5e7;border-radius:22px;background:#fff;box-shadow:0 18px 45px rgba(15,42,51,.09)}
.page-hero{display:flex;justify-content:space-between;align-items:center;gap:28px;padding:34px 38px;background:linear-gradient(118deg,#102e38 0%,#164953 58%,#126c62 100%);color:#fff;position:relative;overflow:hidden}
.page-hero::after{content:"";position:absolute;width:320px;height:320px;border:1px solid rgba(255,255,255,.11);border-radius:50%;right:12%;top:-220px;box-shadow:0 0 0 42px rgba(255,255,255,.035)}
.breadcrumb{display:inline-flex;color:#99e3d4!important;font-size:14px;font-weight:700;position:relative;z-index:1}.eyebrow{margin:18px 0 7px;color:#7ed5c4;font-size:12px;font-weight:800;letter-spacing:.22em}.page-hero h1{margin:0;font-size:30px;line-height:1.25;letter-spacing:.01em;position:relative;z-index:1}.hero-description{margin:10px 0 0;max-width:720px;color:#c9dcdf;font-size:14px;line-height:1.7;position:relative;z-index:1}.publish{padding:13px 22px;background:#fff;color:#116d61!important;border-radius:10px;font-weight:800;white-space:nowrap;box-shadow:0 10px 24px rgba(0,0,0,.16);position:relative;z-index:1;transition:.2s ease}.publish:hover{transform:translateY(-2px);box-shadow:0 14px 28px rgba(0,0,0,.2)}
.summary-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:14px;padding:24px 38px 0}.summary-grid article{position:relative;padding:18px 20px;border:1px solid #dfe8ea;border-radius:14px;background:linear-gradient(145deg,#fff,#f7faf9);overflow:hidden}.summary-grid article::before{content:"";position:absolute;left:0;top:0;bottom:0;width:4px;background:#148574}.summary-grid span{display:block;color:#556a72;font-size:13px;font-weight:700}.summary-grid strong{display:block;margin:7px 0 2px;color:#15313b;font-size:26px;line-height:1}.summary-grid small{color:#8a9aa0;font-size:12px}
.workflow{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:0;margin:20px 38px 0;border:1px solid #dbe6e7;border-radius:14px;background:#f5f9f8;overflow:hidden}.workflow>div{display:flex;align-items:center;gap:12px;padding:15px 18px;position:relative}.workflow>div:not(:last-child){border-right:1px solid #dbe6e7}.workflow b{display:grid;place-items:center;width:32px;height:32px;border-radius:9px;background:#dcefeb;color:#13796b;font-size:12px}.workflow span{display:flex;flex-direction:column;gap:2px}.workflow strong{color:#29434b;font-size:13px}.workflow small{color:#85959a;font-size:11px}.hint{margin:14px 38px 0;padding:11px 14px;border-left:3px solid #d9a23a;background:#fff9ed;color:#7a6848;font-size:13px;line-height:1.65}
.filter-panel{margin:22px 38px 18px;padding:18px 20px;border:1px solid #dce6e8;border-radius:15px;background:#fbfcfc}.filter-heading{display:flex;align-items:baseline;gap:12px;margin-bottom:14px}.filter-heading strong{color:#1c3942;font-size:15px}.filter-heading span{color:#87979d;font-size:12px}.filters{display:flex;gap:12px;flex-wrap:wrap}.filters .el-input{width:min(320px,100%)}.filters .el-select{width:180px}.filters :deep(.el-input__wrapper){min-height:40px;border-radius:9px;box-shadow:0 0 0 1px #d7e2e5 inset}.filters :deep(.el-button){min-height:40px;padding:0 24px;border-radius:9px;background:#137f70;border-color:#137f70;font-weight:700}.supplier-products>:deep(.el-alert){margin:0 38px 12px;width:auto;border-radius:10px}
.table-shell{margin:0 38px;border:1px solid #dce5e8;border-radius:15px;overflow:hidden}.table-shell :deep(.el-table__header th){height:52px;background:#f0f5f5!important;color:#3d5961;font-weight:800}.table-shell :deep(.el-table__row td){padding:13px 0}.table-shell :deep(.el-table__row:hover>td){background:#f6fbfa!important}.product{display:flex;align-items:center;gap:14px;color:#273f47;font-weight:700}.product img{width:60px;height:60px;padding:5px;object-fit:contain;border:1px solid #e0e8ea;border-radius:10px;background:#fff}.price{color:#d96332;font-size:15px}.operations{display:flex;align-items:center;gap:2px;flex-wrap:wrap}.operations>a{margin-right:5px;padding:5px 8px;border-radius:6px;background:#e9f5f2;color:#127668!important;font-weight:700}.operations :deep(.el-button){margin-left:0}.el-pagination{justify-content:flex-end;margin:22px 38px 0}a{color:#167664}.version{font-size:12px;color:#6f838a;margin:8px 0}.remark{font-size:12px;line-height:1.55;color:#596e76;white-space:pre-wrap;overflow-wrap:anywhere}.el-alert+.el-alert{margin-top:12px}
@media(max-width:900px){.supplier-products{margin:16px}.page-hero{padding:26px;align-items:flex-start;flex-direction:column}.summary-grid{grid-template-columns:repeat(2,1fr);padding:20px 22px 0}.workflow{grid-template-columns:repeat(2,1fr);margin:18px 22px}.workflow>div:nth-child(2){border-right:0}.workflow>div:nth-child(-n+2){border-bottom:1px solid #dbe6e7}.hint,.filter-panel,.table-shell{margin-left:22px;margin-right:22px}.el-pagination{margin-right:22px}.filter-heading{align-items:flex-start;flex-direction:column}.filters .el-input,.filters .el-select{width:100%}}
</style>
