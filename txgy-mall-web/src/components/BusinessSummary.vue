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
 <section class="business-summary" :class="{ supplier: shopId!==undefined }" :aria-busy="loading">
  <header><div><small>BUSINESS OVERVIEW</small><h2>{{shopId===undefined?'我的采购状态统计':'店铺经营状态统计'}}</h2></div><el-button :loading="loading" @click="load">刷新统计</el-button></header>
  <p>{{shopId===undefined?'询价仅统计本人创建的单据；订单为本账号PC、移动端全部购物订单，不包含同企业其他人员。':'仅统计当前授权店铺。商品按SPU计数；订单沿用销售列表口径，历史混店订单交平台处理。待发货数量不代表每单都满足发货条件。'}}</p>
  <el-skeleton v-if="loading" :rows="3" animated/>
  <div v-else class="metrics"><div v-for="metric in metrics" :key="metric.key" class="metric" :data-metric="metric.key"><span>{{metric.label}}</span><strong>{{metric.value===null?'—':metric.value}}</strong><small v-if="metric.value===null">暂未取得数据</small></div></div>
  <el-alert v-if="failed" type="warning" title="部分统计加载失败，请刷新重试；缺失数据未按零计算。" :closable="false"/>
  <small v-if="updated">最近读取 {{updated}} · 当前状态数量，非按日期统计；各项分别查询，业务变动后请刷新。{{shopId===undefined?'询价提交不代表已发送给供应商。':'不代表收入或可结算金额。'}}</small>
 </section>
</template>
<style scoped>
.business-summary{margin:24px 0;padding:22px;border:1px solid #dce9e6;border-radius:16px;background:linear-gradient(145deg,#fff 0%,#f7fbfa 100%);box-shadow:0 12px 28px #173e460c}.business-summary.supplier{border-color:#dce5ef;background:linear-gradient(145deg,#fff 0%,#f7f9fc 100%)}header{display:flex;align-items:center;justify-content:space-between;gap:12px}header>div>small{display:block;margin-bottom:5px;color:#16806d;font-size:9px;font-weight:700;letter-spacing:1.6px}.supplier header>div>small{color:#356aa3}h2{margin:0;color:#17323a;font-size:19px}p,small{color:#64748b;line-height:1.7}.metrics{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px;margin:18px 0}.metric{position:relative;overflow:hidden;padding:17px 18px;border:1px solid #dfeae7;border-radius:12px;background:#fff}.metric::before{position:absolute;inset:0 auto 0 0;width:3px;background:#20a087;content:""}.supplier .metric::before{background:#487eb8}.metric span{display:block;color:#607078;font-size:12px}.metric strong{display:block;margin-top:7px;color:#174e48;font-size:28px;line-height:1}.supplier .metric strong{color:#294f78}.metric small{display:block}.el-alert{margin-bottom:12px}@media(max-width:800px){.metrics{grid-template-columns:repeat(2,minmax(0,1fr))}}
</style>
