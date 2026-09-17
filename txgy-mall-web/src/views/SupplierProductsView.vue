<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { supplierApi, type SupplierProduct } from '@/api/supplier'
import { enterpriseApi } from '@/api/enterprise'
import type { Shop } from '@/types/mall'

const route = useRoute(), router = useRouter()
const shopId = Number(route.params.shopId)
const shop = ref<Shop>(), rows = ref<SupplierProduct[]>([])
const busy = ref(false), error = ref(false), page = ref(1), total = ref(0), name = ref(''), status = ref<number>()
async function load() {
  busy.value = true; error.value = false
  try {
    const shops = await enterpriseApi.salesShops()
    shop.value = shops.find(s => Number(s.id) === shopId)
    if (!shop.value) { await router.replace('/sales'); return }
    const result = await supplierApi.page({ shopId, pageNo: page.value, pageSize: 15, name: name.value, status: status.value })
    rows.value = result.list; total.value = result.total
  } catch { error.value = true; rows.value = []; total.value = 0 }
  finally { busy.value = false }
}
async function toggle(id: number, currentStatus: number, productName: string) {
  if (!id || busy.value) return
  const next = currentStatus === 1 ? 0 : 1
  try { await ElMessageBox.confirm(`确认${next ? '上架' : '下架'}“${productName}”？`, '商品状态', { confirmButtonText: '确定', cancelButtonText: '取消' }) }
  catch { return }
  busy.value = true
  try { await supplierApi.status(shopId, id, next); ElMessage.success('状态已更新'); await load() }
  finally { busy.value = false }
}
onMounted(load)
</script>
<template>
  <section class="container panel supplier-products">
    <header><div><RouterLink to="/sales">← 销售中心</RouterLink><h1>{{ shop?.name || '授权店铺' }} · 商品管理</h1></div>
      <RouterLink v-if="shop" class="publish" :to="`/sales/shops/${shopId}/products/new`">发布商品</RouterLink></header>
    <el-alert title="仅管理后台明确授权的店铺。新商品保存为下架，核对后可上架；库存调拨和WMS映射仍由原管理流程负责。" :closable="false" />
    <div class="filters"><el-input v-model="name" placeholder="商品名称" clearable @keyup.enter="page=1;load()" />
      <el-select v-model="status" placeholder="全部状态" clearable><el-option :value="1" label="上架"/><el-option :value="0" label="下架"/></el-select>
      <el-button :loading="busy" @click="page=1;load()">查询</el-button></div>
    <el-alert v-if="error" title="加载失败，请重试；不会展示其他店铺商品" type="error" :closable="false"/>
    <el-table :data="rows" v-loading="busy"><el-table-column prop="id" label="编号" width="100"/>
      <el-table-column label="商品" min-width="260"><template #default="{row}"><div class="product"><img :src="row.picUrl" alt="商品图"/><span>{{row.name}}</span></div></template></el-table-column>
      <el-table-column label="售价"><template #default="{row}">¥{{((row.price||0)/100).toFixed(2)}}</template></el-table-column>
      <el-table-column prop="stock" label="库存缓存"/>
      <el-table-column label="状态"><template #default="{row}">{{row.status===1?'上架':'下架'}}</template></el-table-column>
      <el-table-column label="操作" width="170"><template #default="{row}"><RouterLink :to="`/sales/shops/${shopId}/products/${row.id}`">编辑</RouterLink><el-button link :disabled="busy" @click="toggle(row.id, row.status, row.name)">{{row.status===1?'下架':'上架'}}</el-button></template></el-table-column>
    </el-table>
    <el-pagination v-model:current-page="page" :page-size="15" :total="total" layout="total, prev, pager, next" @current-change="load"/>
  </section>
</template>
<style scoped>
.supplier-products{padding:32px;margin:32px auto;min-height:560px}header{display:flex;justify-content:space-between;align-items:center;margin-bottom:24px}h1{margin:16px 0}.publish{padding:12px 22px;background:#167664;color:white;border-radius:6px}.filters{display:flex;gap:16px;margin:24px 0}.filters .el-input{max-width:360px}.filters .el-select{width:150px}.product{display:flex;align-items:center;gap:16px}.product img{width:56px;height:56px;object-fit:contain}a{color:#167664}.el-pagination{margin-top:24px}
</style>
