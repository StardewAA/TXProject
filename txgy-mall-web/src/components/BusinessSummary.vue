<script setup lang="ts">
import { onUnmounted,ref,watch } from 'vue'
import { loadBusinessSummary,type SummaryMetric } from '@/api/business-summary'
import OrderWorkbench from '@/components/OrderWorkbench.vue'
const props=defineProps<{shopId?:number}>()
const metrics=ref<SummaryMetric[]>([]),loading=ref(false),failed=ref(false),updated=ref('')
let version=0
async function load(){
 const current=++version;loading.value=true;failed.value=false;metrics.value=[];updated.value=''
 try{const result=await loadBusinessSummary(props.shopId);if(current!==version)return;metrics.value=result;failed.value=result.some(x=>x.value===null);updated.value=new Date().toLocaleTimeString('zh-CN',{hour12:false})}
 catch{if(current===version)failed.value=true}
 finally{if(current===version)loading.value=false}
}
watch(()=>props.shopId,load,{immediate:true})
onUnmounted(()=>{++version})
</script>
<template>
 <OrderWorkbench :role="shopId===undefined?'BUYER':'SUPPLIER'" :shop-id="shopId" />
 <section class="business-summary" :aria-busy="loading">
  <header><h2>{{shopId===undefined?'我的采购状态统计':'店铺经营状态统计'}}</h2><el-button :loading="loading" @click="load">刷新统计</el-button></header>
  <p>{{shopId===undefined?'询价仅统计本人创建的单据；订单为本账号PC、移动端全部购物订单，不包含同企业其他人员。':'仅统计当前授权店铺。商品按SPU计数；订单沿用销售列表口径，历史混店订单交平台处理。待发货数量不代表每单都满足发货条件。'}}</p>
  <el-skeleton v-if="loading" :rows="3" animated/>
  <div v-else class="metrics"><div v-for="metric in metrics" :key="metric.key" class="metric" :data-metric="metric.key"><span>{{metric.label}}</span><strong>{{metric.value===null?'—':metric.value}}</strong><small v-if="metric.value===null">暂未取得数据</small></div></div>
  <el-alert v-if="failed" type="warning" title="部分统计加载失败，请刷新重试；缺失数据未按零计算。" :closable="false"/>
  <small v-if="updated">最近读取 {{updated}} · 当前状态数量，非按日期统计；各项分别查询，业务变动后请刷新。{{shopId===undefined?'询价提交不代表已发送给供应商。':'不代表收入或可结算金额。'}}</small>
 </section>
</template>
<style scoped>
.business-summary{margin:24px 0;padding:20px;background:#fff;border:1px solid #dbe4e8;border-radius:8px}header{display:flex;align-items:center;justify-content:space-between;gap:12px}h2{font-size:18px;margin:0}p,small{color:#64748b;line-height:1.7}.metrics{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px;margin:16px 0}.metric{padding:18px;background:#f3f7fa;border-radius:6px}.metric span{display:block;font-size:13px;color:#475569}.metric strong{display:block;font-size:28px;margin-top:10px;color:#245c65}.metric small{display:block}.el-alert{margin-bottom:12px}@media(max-width:800px){.metrics{grid-template-columns:repeat(2,minmax(0,1fr))}}
</style>
