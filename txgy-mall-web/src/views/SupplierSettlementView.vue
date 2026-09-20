<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAccountMessageBox } from '@/composables/useAccountOperation'
const ElMessageBox = useAccountMessageBox()
import { settlementApi, settlementStatus, type SettlementBill, type SettlementDetail } from '@/api/settlement'
import { formatMoney, formatDate } from '@/utils/format'
const route=useRoute(), shopId=computed(()=>Number(route.params.shopId))
const rows=ref<SettlementBill[]>([]),total=ref(0),page=ref(1),busy=ref(false),error=ref(false),detail=ref<SettlementDetail>(),opened=ref(false),proof=ref('')
let version=0
async function load(){const run=++version;busy.value=true;error.value=false;try{const data=await settlementApi.page(shopId.value,page.value);if(run===version){rows.value=data.list;total.value=data.total}}catch{if(run===version){rows.value=[];error.value=true}}finally{if(run===version)busy.value=false}}
async function show(row:{id?:number}){if(!row.id)return;busy.value=true;proof.value='';try{detail.value=await settlementApi.get(shopId.value,row.id);opened.value=true}finally{busy.value=false}}
async function confirm(){if(!detail.value||busy.value)return;const b=detail.value.bill;const accepted=await ElMessageBox.confirm('确认本版订单、退款及应结金额无误？此操作仅确认账单，不代表收到款项。','确认店铺账单',{confirmButtonText:'确认账单',cancelButtonText:'返回核对'}).then(()=>true,()=>false);if(!accepted)return;busy.value=true;try{await settlementApi.confirm(shopId.value,b.id,b.version);ElMessage.success('账单已确认，等待平台处理');opened.value=false;await load()}finally{busy.value=false}}
async function viewProof(){if(detail.value)proof.value=await settlementApi.proof(shopId.value,detail.value.bill.id)}
watch(shopId,()=>{page.value=1;opened.value=false;detail.value=undefined;void load()},{immediate:true})
</script>
<template><div class="container page-section"><MallLink to="/sales">← 销售中心</MallLink><h1>店铺结算</h1><p>平台审核后由您核对确认；线下付款由平台登记，未接自动打款。金额单位：元。</p><el-button :disabled="busy" @click="load">刷新账单</el-button><el-alert v-if="error" title="账单加载失败，请确认店铺授权后重试" type="error" :closable="false"/>
<el-table :data="rows" v-loading="busy"><el-table-column prop="no" label="结算单号" min-width="190"/><el-table-column prop="billMonth" label="月份"/><el-table-column prop="orderCount" label="订单数"/><el-table-column label="应结金额"><template #default="{row}">{{formatMoney(row.settlementAmount)}}</template></el-table-column><el-table-column label="状态" min-width="170"><template #default="{row}">{{settlementStatus[row.status]}}</template></el-table-column><el-table-column label="操作"><template #default="{row}"><el-button :disabled="busy" @click="show(row)">查看账单</el-button></template></el-table-column></el-table>
<el-pagination v-model:current-page="page" :page-size="10" :total="total" :disabled="busy" layout="prev,pager,next,total" @current-change="load"/>
<el-dialog v-model="opened" title="店铺结算账单" width="90%" :close-on-click-modal="false"><template v-if="detail"><h2>{{detail.bill.shopName}} · {{detail.bill.billMonth}} · 第{{detail.bill.version}}版</h2><p>{{settlementStatus[detail.bill.status]}} / {{detail.bill.no}}</p>
<div class="summary"><span>订单实付（含运费）：{{formatMoney(detail.bill.orderPayAmount)}}</span><span>已退：{{formatMoney(detail.bill.refundAmount)}}</span><span>佣金：{{formatMoney(detail.bill.commissionAmount)}}</span><span>跨期退款调整：{{formatMoney(detail.bill.adjustmentAmount)}}</span><span>上期抵扣：{{formatMoney(detail.bill.carryAmount)}}</span><strong>应结：{{formatMoney(detail.bill.settlementAmount)}}</strong></div>
<p>佣金以退款后实付（含运费）为基数，逐订单四舍五入到分；补结订单与跨期退款均单独保留快照。争议请联系平台退回重算。</p><p v-if="detail.bill.auditRemark">审核意见：{{detail.bill.auditRemark}}</p>
<el-table :data="detail.items" max-height="350"><el-table-column prop="orderNo" label="订单号" min-width="170"/><el-table-column label="类型"><template #default="{row}">{{row.kind===1?'订单结算':'跨期退款'}}</template></el-table-column><el-table-column label="原实付"><template #default="{row}">{{formatMoney(row.payAmount)}}</template></el-table-column><el-table-column label="累计退款"><template #default="{row}">{{formatMoney(row.refundTotal)}}</template></el-table-column><el-table-column label="佣金/冲回"><template #default="{row}">{{formatMoney(row.commissionAmount)}}</template></el-table-column><el-table-column label="本项应结"><template #default="{row}">{{formatMoney(row.amount)}}</template></el-table-column></el-table>
<h3>操作记录</h3><p v-for="(log,i) in detail.logs" :key="i">{{formatDate(log.createTime)}} · 版本{{log.version}} · {{log.remark}}</p>
<p v-if="detail.bill.paymentReference">线下付款流水：{{detail.bill.paymentReference}}　{{formatDate(detail.bill.paymentTime)}}</p><el-button v-if="detail.bill.status===40" @click="viewProof">查看私有付款凭证</el-button><img v-if="proof" :src="proof" alt="线下付款凭证" style="display:block;max-width:100%;max-height:500px"/>
<el-button v-if="detail.bill.status===20" type="primary" :disabled="busy" @click="confirm">确认账单</el-button></template></el-dialog></div></template>
<style scoped>.summary{display:flex;flex-wrap:wrap;gap:18px;padding:18px;background:#f4f7fa}.page-section>p{color:#64748b}.el-pagination{margin-top:20px}</style>
