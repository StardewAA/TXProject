<script setup lang="ts">
import { ref } from 'vue'
import { formatDate } from '@/utils/format'
import { ElMessage } from 'element-plus'
import { useAccountMessageBox } from '@/composables/useAccountOperation'
const ElMessageBox = useAccountMessageBox()
import { supplierApi, type SupplierSku } from '@/api/supplier'
import { supplierStockApi, type StockAdjust, type StockEntry } from '@/api/supplier-stock'
const props=defineProps<{shopId:number;spuId:number;disabled?:boolean}>()
const emit=defineEmits<{updated:[skus:SupplierSku[]]}>()
const open=ref(false),busy=ref(false),failed=ref(false),loading=ref(false)
const skus=ref<SupplierSku[]>([]),selected=ref<number>(),delta=ref(0),reason=ref('')
const rows=ref<StockEntry[]>([]),page=ref(1),total=ref(0),pending=ref<StockAdjust>()
async function refresh(){
 loading.value=true;failed.value=false
 try {
  const [product,records]=await Promise.all([supplierApi.detail(props.shopId,props.spuId),supplierStockApi.page(props.shopId,props.spuId,page.value)])
  skus.value=product.skus;rows.value=records.list;total.value=records.total;emit('updated',product.skus)
 }catch{failed.value=true}finally{loading.value=false}
}
async function show(){open.value=true;page.value=1;await refresh()}
async function submit(){
 if(busy.value||loading.value||failed.value)return
 if(!pending.value){
  const sku=skus.value.find(s=>s.id===selected.value)
  if(!sku?.id||sku.wmsManaged||sku.stock===undefined||!Number.isInteger(delta.value)||!delta.value||Math.abs(delta.value)>1000000||!reason.value.trim())return void ElMessage.warning('请选择本地SKU，填写非零整数数量及原因')
  if(sku.stock+delta.value<0)return void ElMessage.warning('调整后库存不能小于零')
  try{await ElMessageBox.confirm(`将可售库存从 ${sku.stock} 调整为 ${sku.stock+delta.value}，确认数量和原因无误？`,'确认库存调整',{confirmButtonText:'确认调整',cancelButtonText:'取消',type:'warning'})}catch{return}
  pending.value={shopId:props.shopId,spuId:props.spuId,skuId:sku.id,expectedStock:sku.stock,delta:delta.value,reason:reason.value.trim(),requestKey:crypto.randomUUID()}
 }
 busy.value=true
 try{
  await supplierStockApi.adjust(pending.value)
  pending.value=undefined;delta.value=0;reason.value='';ElMessage.success('库存已调整，记录已保存');await refresh()
 }catch{
  ElMessage.warning('调整未确认成功。请先核对记录；重试会沿用同一请求编号，不会重复增减。')
 }finally{busy.value=false}
}
async function discard(){
 try{await ElMessageBox.confirm('仅在已核对调整记录或确认请求被拒绝后重新填写。网络超时的请求可能已经成功，请勿重复补货。','重新核对',{confirmButtonText:'已核对，重新填写',cancelButtonText:'返回'})}catch{return}
 pending.value=undefined;await refresh()
}
</script>
<template>
 <el-button :disabled="disabled" @click="show">本地库存调整与记录</el-button>
 <el-dialog v-model="open" title="本地可售库存调整" width="850px" :close-on-click-modal="false" :close-on-press-escape="!busy" :show-close="!busy">
  <el-alert title="仅调整可售库存，不改变销量，也不代表采购入库。系统管理的 SKU 不可手工调整；每笔操作会保留操作人和原因。" :closable="false"/>
  <el-alert v-if="failed" type="error" title="库存或记录读取失败，请重试" :closable="false"/>
  <div class="stock-fields">
   <label>选择SKU<el-select v-model="selected" :disabled="!!pending||busy||loading"><el-option v-for="s in skus" :key="s.id" :value="s.id!" :label="`${s.specification||'默认规格'} · 库存 ${s.stock}${s.wmsManaged?'（系统管理）':''}`" :disabled="s.wmsManaged"/></el-select></label>
   <label>增减数量（正数补货，负数调减）<input v-model.number="delta" class="stock-quantity" type="number" step="1" min="-1000000" max="1000000" :disabled="!!pending||busy||loading"/></label>
   <label>调整原因<el-input v-model="reason" maxlength="200" type="textarea" :disabled="!!pending||busy||loading" placeholder="例如：核对本地可售数量后补货20件"/></label>
  </div>
  <el-button type="primary" :loading="busy" :disabled="failed||loading" @click="submit">{{pending?'重试同一调整':'确认库存调整'}}</el-button>
  <el-button :disabled="busy||loading" @click="refresh">刷新库存和记录</el-button>
  <el-button v-if="pending" :disabled="busy||loading" @click="discard">核对后重新填写</el-button>
  <p v-if="pending">请求编号：{{pending.requestKey}}。结果未确认时，不要直接再次补货。</p>
  <h3>本商品调整记录</h3>
  <el-table :data="rows" v-loading="loading" empty-text="暂无库存调整记录"><el-table-column prop="id" label="记录号" width="85"/><el-table-column prop="skuId" label="SKU" width="85"/><el-table-column prop="delta" label="增减" width="70"/><el-table-column label="调整前 → 后" width="120"><template #default="{row}">{{row.beforeStock}} → {{row.afterStock}}</template></el-table-column><el-table-column prop="reason" label="原因"/><el-table-column prop="memberId" label="会员" width="75"/><el-table-column label="时间" width="170"><template #default="{row}">{{formatDate(row.createTime)}}</template></el-table-column></el-table>
  <el-pagination v-model:current-page="page" :page-size="10" :total="total" layout="prev,pager,next,total" :disabled="busy||loading" @current-change="refresh"/>
 </el-dialog>
</template>
<style scoped>.stock-fields{display:grid;gap:16px;margin:20px 0}.stock-fields label{display:grid;gap:8px}.stock-quantity{width:220px;padding:8px 12px;border:1px solid #dcdfe6;border-radius:4px;font:inherit}.stock-quantity:disabled{background:#f5f7fa;color:#909399}.el-select{width:100%}h3{margin:24px 0 12px}p{color:#a16207;font-size:13px}.el-pagination{margin-top:16px}</style>
