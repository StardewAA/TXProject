<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAccountMessageBox } from '@/composables/useAccountOperation'
const ElMessageBox = useAccountMessageBox()
import { enterpriseApi } from '@/api/enterprise'
import { supplierTradeApi, type SalesOrder, type SalesOrderDetail, type SalesAfterSale, type SalesAfterSaleDetail, type SalesAfterSaleAction } from '@/api/supplier-trade'
import { formatDate, formatMoney, normalizeImage } from '@/utils/format'
import OrderWorkbench from '@/components/OrderWorkbench.vue'
import OrderWorkbenchBadge from '@/components/OrderWorkbenchBadge.vue'
import { useOrderWorkbench, useOrderWorkbenchRows } from '@/composables/useOrderWorkbench'
import { orderTabs, workbenchRowEntries } from '@/utils/order-workbench'
const route=useRoute(), router=useRouter()
const shopId=computed(()=>Number(route.params.shopId)), after=computed(()=>route.path.endsWith('/after-sales'))
const pageTitle=computed(()=>after.value?'销售售后处理':'销售订单')
const shopName=ref(''), busy=ref(false), error=ref(false), no=ref(''), status=ref<number>(), page=ref(1), total=ref(0)
const orders=ref<SalesOrder[]>([]), afterSales=ref<SalesAfterSale[]>([]), detail=ref<SalesOrderDetail>(), selectedAfter=ref<SalesAfterSale>()
const opened=ref(false), afterOpened=ref(false), submitting=ref(false), detailBusy=ref(false)
const afterDetail=ref<SalesAfterSaleDetail>(), afterBusy=ref(false), reason=ref('')
const carriers=ref<{id:number;name:string}[]>([]), logisticsId=ref<number>(), logisticsNo=ref('')
const orderStates:Record<number,string>={0:'待付款',10:'待发货',20:'待收货',30:'已完成',40:'已取消'}
const afterStates:Record<number,string>={10:'申请中',20:'同意退货',30:'待收退货',40:'等待平台退款',50:'已完成',61:'买家取消',62:'申请被拒绝',63:'退货被拒收'}
const statusOptions=computed(()=>Object.entries(after.value?afterStates:orderStates).map(([value,label])=>({value:Number(value),label})))
const appliedNo=ref('')
const workbench=useOrderWorkbench(()=> 'SUPPLIER',()=>shopId.value)
const counts=useOrderWorkbenchRows(workbench,computed(()=>({no:after.value?undefined:appliedNo.value,orderIds:after.value?undefined:orders.value.map(row=>row.id).join(','),afterSaleIds:after.value?afterSales.value.map(row=>row.id).join(','):undefined})))
function rowNotices(id:number){return workbenchRowEntries(counts.snapshot.value?.rowEntries||[],id,after.value)}
function search(){appliedNo.value=no.value.trim();page.value=1;void load()}
let requestVersion=0, disposed=false
async function load(){
 const version=++requestVersion;busy.value=true;error.value=false
 try{
  const shops=await enterpriseApi.salesShops();if(version!==requestVersion)return
  const shop=shops.find(s=>Number(s.id)===shopId.value)
  if(!shop){await router.replace('/sales');return}shopName.value=shop.name
  const params={shopId:shopId.value,pageNo:page.value,pageSize:15,no:appliedNo.value,status:status.value}
  if(after.value){const result=await supplierTradeApi.afterSales(params);if(version!==requestVersion)return;afterSales.value=result.list;total.value=result.total}
  else{const result=await supplierTradeApi.page(params);if(version!==requestVersion)return;orders.value=result.list;total.value=result.total}
 }catch{if(version===requestVersion){error.value=true;orders.value=[];afterSales.value=[];total.value=0}}
 finally{if(version===requestVersion)busy.value=false}
}
let detailVersion=0
async function openOrder(id:number){
 const version=++detailVersion,shop=shopId.value
 detailBusy.value=true;detail.value=undefined;carriers.value=[];logisticsId.value=undefined;logisticsNo.value='';opened.value=true
 try{
  const result=await supplierTradeApi.detail(shop,id);if(version!==detailVersion)return
  detail.value=result
  if(result.deliverable){const resultCarriers=await supplierTradeApi.express(shop);if(version!==detailVersion)return;carriers.value=resultCarriers}
  void workbench.read(rowNotices(id)).catch(()=>{})
 }finally{if(version===detailVersion)detailBusy.value=false}
}
let afterVersion=0
async function openAfter(id:number){
 const version=++afterVersion,shop=shopId.value
 selectedAfter.value=undefined;afterDetail.value=undefined;reason.value='';afterOpened.value=true;afterBusy.value=true
 try{const result=await supplierTradeApi.afterSaleDetail(shop,id);if(version!==afterVersion)return;afterDetail.value=result;selectedAfter.value=result.row;void workbench.read(rowNotices(id)).catch(()=>{})}
 finally{if(version===afterVersion)afterBusy.value=false}
}
async function processAfterSale(action:SalesAfterSaleAction){
 if(!selectedAfter.value||submitting.value)return
 const key=workbench.key.value,version=afterVersion
 const id=selectedAfter.value.id,shop=shopId.value,memo=reason.value.trim()
 if((action==='REJECT'||action==='REFUSE')&&!memo)return void ElMessage.warning('请填写拒绝原因')
 const message={AGREE:selectedAfter.value.way===20?'同意后将保存当前店铺退货地址并展示给买家，请先核对店铺资料。确认同意退货？':'同意后进入等待平台退款，不会立即退款。是否继续？',REJECT:'确认驳回申请？拒绝原因将展示给买家。',RECEIVE:'请确认退货实物已验收。确认后进入等待平台退款，是否继续？',REFUSE:'确认拒收退货？拒收原因将展示给买家。'}[action]
 submitting.value=true
 try{
  try{await ElMessageBox.confirm(message,'售后处理确认',{confirmButtonText:'确认处理',cancelButtonText:'取消'})}catch{return}
  if(!key||key!==workbench.key.value||version!==afterVersion||selectedAfter.value?.id!==id)return
  await supplierTradeApi.processAfterSale({shopId:shop,id,action,reason:memo})
  ElMessage.success(action==='RECEIVE'?'退货入库请求已提交，请稍后查看处理结果':'售后处理成功');afterOpened.value=false;await load();await workbench.refresh()
 }finally{submitting.value=false}
}
async function deliver(){
 if(!detail.value?.deliverable||submitting.value)return
 const key=workbench.key.value,shop=shopId.value,id=detail.value.order.id,version=detailVersion
 const selected=logisticsId.value,tracking=logisticsNo.value.trim()
 if(!selected||!/^[-A-Za-z0-9]{5,64}$/.test(tracking))return void ElMessage.warning('请选择快递公司并填写有效运单号（5至64位）')
 try{await ElMessageBox.confirm('确认提交出库及物流信息？处理完成后将更新买家的发货状态。','提交发货请求',{confirmButtonText:'提交请求',cancelButtonText:'取消'})}catch{return}
 if(!key||key!==workbench.key.value||shop!==shopId.value||version!==detailVersion||detail.value?.order.id!==id)return
 submitting.value=true
 try{await supplierTradeApi.deliver({shopId:shop,id,logisticsId:selected,logisticsNo:tracking});if(key!==workbench.key.value)return;ElMessage.success('发货请求已提交，请稍后查看订单状态');opened.value=false;await load();await workbench.refresh()}finally{submitting.value=false}
}
function selectOrderStatus(value:string){
 const query={...route.query}
 if(value==='all')delete query.status
 else query.status=value
 void router.push({path:route.path,query})
}
watch(()=>route.fullPath,(_next,previous)=>{
 ++afterVersion;++detailVersion;opened.value=false;afterOpened.value=false;page.value=1
 if(!previous || previous.split('?')[0]!==route.path){no.value='';appliedNo.value=''}
 const queryStatus=route.query.status
 const states=after.value?afterStates:orderStates
 status.value=typeof queryStatus==='string' && Object.hasOwn(states,queryStatus)?Number(queryStatus):undefined
 const path=route.fullPath
 void load().then(()=>{if(disposed||path!==route.fullPath||!workbench.key.value)return;const id=Number(after.value?route.query.openAfter:route.query.openOrder);if(Number.isSafeInteger(id)&&id>0){if(after.value)void openAfter(id);else void openOrder(id)}})
},{immediate:true})
watch(()=>workbench.state.value.updatedAt,()=>{if(!opened.value&&!afterOpened.value&&!submitting.value&&!busy.value)void load()})
watch(workbench.key,(key)=>{++requestVersion;++detailVersion;++afterVersion;busy.value=false;orders.value=[];afterSales.value=[];detail.value=undefined;afterDetail.value=undefined;selectedAfter.value=undefined;opened.value=false;afterOpened.value=false;if(key)void load()},{flush:'sync'})
onBeforeUnmount(()=>{disposed=true;++requestVersion;++detailVersion;++afterVersion})
</script>
<template>
 <div class="container sales-trade">
  <aside><h2>销售中心</h2><p>{{shopName}}</p><MallLink to="/sales">销售工作台</MallLink><MallLink :to="`/sales/shops/${shopId}/products`">管理商品</MallLink><MallLink :class="{active:!after}" :to="`/sales/shops/${shopId}/orders`">销售订单<OrderWorkbenchBadge role="SUPPLIER" :shop-id="shopId"/></MallLink><MallLink :class="{active:after}" :to="`/sales/shops/${shopId}/after-sales`">销售售后处理</MallLink><MallLink :to="`/sales/shops/${shopId}/settlements`">店铺结算</MallLink><MallLink to="/purchase">切换到采购中心 →</MallLink></aside>
  <main class="panel"><h1>{{pageTitle}}</h1>
   <OrderWorkbench role="SUPPLIER" :shop-id="shopId" :navigation-blocked="opened||afterOpened||submitting"/>
   <el-alert :closable="false" :title="after?'可审核本店售后申请及验收退货；同意退货须先维护店铺退货地址，实际退款仍由平台处理。':'仅查看授权店铺的订单快照。已付款、无退款售后的待发货快递订单可填写物流；不支持虚假发货或改动买家金额。'"/>
   <div v-if="!after" class="order-status-tabs" role="tablist" aria-label="销售订单状态">
    <button v-for="tab in orderTabs" :key="tab.value" type="button" role="tab" :aria-selected="(status===undefined?'all':String(status))===tab.value" @click="selectOrderStatus(tab.value)">{{tab.label}} ({{counts.snapshot.value?.orderStatusCounts[tab.count]??'—'}})</button>
   </div>
   <p v-if="!after" class="count-scope">页签数量为当前店铺和订单号共同筛选的全部订单；待发货状态数不等同于可发货事项数。<span v-if="counts.error.value">数量暂无法更新，保留上次结果。</span></p>
   <div class="filters"><el-input v-model="no" placeholder="订单号" clearable @keyup.enter="search"/><el-select v-if="after" v-model="status" clearable placeholder="全部状态"><el-option v-for="s in statusOptions" :key="s.value" :value="s.value" :label="s.label"/></el-select><el-button :loading="busy" @click="search">查询</el-button></div>
   <el-alert v-if="error" type="error" title="加载失败，请重试" :closable="false"/>
   <el-table v-if="!after" :data="orders" v-loading="busy" :empty-text="status===10?'暂无匹配的待发货订单':'暂无匹配的销售订单'"><el-table-column label="订单号" min-width="185"><template #default="{row}">{{row.no}} <b v-if="rowNotices(row.id).length" class="new-order">新待办</b></template></el-table-column><el-table-column label="下单时间" width="165"><template #default="{row}">{{formatDate(row.createTime)}}</template></el-table-column><el-table-column label="订单状态" min-width="170"><template #default="{row}">{{orderStates[row.status]||row.status}}<small v-if="row.status===10&&!row.deliverable">{{row.deliveryBlockReason||'当前不可发货，请查看详情'}}</small></template></el-table-column><el-table-column label="实付金额"><template #default="{row}">¥{{formatMoney(row.payPrice)}}</template></el-table-column><el-table-column label="操作" width="125"><template #default="{row}"><el-button link @click="openOrder(row.id)">{{row.deliverable?'详情 / 发货':'查看详情'}}</el-button></template></el-table-column></el-table>
   <el-table v-else :data="afterSales" v-loading="busy"><el-table-column label="售后编号" min-width="180"><template #default="{row}">{{row.no}} <b v-if="rowNotices(row.id).length" class="new-order">新待办</b></template></el-table-column><el-table-column prop="orderNo" label="订单号" min-width="180"/><el-table-column prop="spuName" label="商品"/><el-table-column label="退款金额"><template #default="{row}">¥{{formatMoney(row.refundPrice)}}</template></el-table-column><el-table-column label="进度" min-width="170"><template #default="{row}">{{afterStates[row.status]||row.status}}<small v-if="row.processBlockReason">{{row.processBlockReason}}</small></template></el-table-column><el-table-column label="操作" width="80"><template #default="{row}"><el-button link @click="openAfter(row.id)">详情</el-button></template></el-table-column></el-table>
   <el-pagination v-model:current-page="page" :page-size="15" :total="total" layout="prev,pager,next" @current-change="load"/>
  </main>
  <el-dialog v-model="opened" title="销售订单详情" width="850px" :close-on-click-modal="!submitting" :show-close="!submitting"><div v-loading="detailBusy"><template v-if="detail"><p>订单号：{{detail.order.no}} · {{orderStates[detail.order.status]}}</p><p>收货人：{{detail.receiverName}}　{{detail.receiverMobile}}</p><p>收货地址：{{detail.address}}</p><p>买家备注：{{detail.userRemark||'无'}}</p><el-table :data="detail.items"><el-table-column label="商品"><template #default="{row}"><div class="item"><img :src="normalizeImage(row.picUrl)" alt="商品"/><span>{{row.name}}<small>{{row.specification}}</small></span></div></template></el-table-column><el-table-column prop="count" label="数量" width="75"/><el-table-column label="金额" width="110"><template #default="{row}">¥{{formatMoney(row.payPrice)}}</template></el-table-column></el-table><p>订单实付 ¥{{formatMoney(detail.order.payPrice)}}　已退 ¥{{formatMoney(detail.order.refundPrice)}}</p><p v-if="detail.logisticsNo">运单号：{{detail.logisticsNo}}　发货时间：{{formatDate(detail.deliveryTime)}}</p>
   <div v-if="detail.deliverable" class="shipping"><h3>快递发货</h3><el-select v-model="logisticsId" filterable placeholder="请选择快递公司" :disabled="submitting"><el-option v-for="e in carriers" :key="e.id" :value="e.id" :label="e.name"/></el-select><el-input v-model="logisticsNo" placeholder="填写真实运单号" maxlength="64" :disabled="submitting"/><el-button type="primary" :loading="submitting" :disabled="detailBusy||!carriers.length" @click="deliver">确认发货</el-button></div><el-alert v-else :closable="false" title="当前订单不可发货：请核对订单状态、付款、配送方式及售后情况。"/></template><el-empty v-else-if="!detailBusy" description="未能读取订单，请关闭后重试"/></div></el-dialog>
  <el-dialog v-model="afterOpened" title="销售售后处理" width="700px" :close-on-click-modal="!submitting" :close-on-press-escape="!submitting" :show-close="!submitting">
   <div v-loading="afterBusy"><template v-if="selectedAfter">
    <p>售后单：{{selectedAfter.no}}　订单：{{selectedAfter.orderNo}}</p><p>{{selectedAfter.spuName}} × {{selectedAfter.count}}</p>
    <p>类型：{{selectedAfter.way===20?'退货退款':'仅退款'}}　状态：{{afterStates[selectedAfter.status]}}　退款金额：¥{{formatMoney(selectedAfter.refundPrice)}}</p>
    <p>申请原因：{{selectedAfter.applyReason}}</p><p>申请说明：{{selectedAfter.applyDescription||'无'}}</p>
    <div class="evidence"><el-image v-for="(pic,index) in afterDetail?.pictures||[]" :key="index" :src="normalizeImage(pic)" :preview-src-list="(afterDetail?.pictures||[]).map(normalizeImage)" preview-teleported style="width:90px;height:90px" fit="contain"/></div>
    <p v-if="afterDetail?.returnAddress">退货收件人：{{afterDetail.returnReceiverName}} {{afterDetail.returnReceiverMobile}}<br/>退货地址：{{afterDetail.returnAddress}}</p><MallLink :to="`/sales/shops/${shopId}/settings`">维护店铺退货地址</MallLink><p>审核说明：{{selectedAfter.auditReason||'无'}}</p><p v-if="afterDetail?.receiveReason">拒收原因：{{afterDetail.receiveReason}}</p><p v-if="selectedAfter.logisticsNo">退货运单：{{selectedAfter.logisticsNo}}</p>
    <el-alert title="实际退款由平台处理；同意退货时保存店铺退货地址快照，缺少地址时请先到店铺资料维护。" :closable="false"/>
    <template v-if="selectedAfter.processable===true && (selectedAfter.status===10||selectedAfter.status===30)">
     <el-input v-model="reason" type="textarea" placeholder="拒绝申请或拒收时必填原因" maxlength="200" show-word-limit :disabled="submitting" style="margin:16px 0"/>
     <el-button v-if="selectedAfter.status===10" type="primary" :disabled="submitting" @click="processAfterSale('AGREE')">同意申请</el-button>
     <el-button v-if="selectedAfter.status===10" type="danger" :disabled="submitting" @click="processAfterSale('REJECT')">驳回申请</el-button>
     <el-button v-if="selectedAfter.status===30" type="primary" :disabled="submitting" @click="processAfterSale('RECEIVE')">提交退货入库确认</el-button>
     <el-button v-if="selectedAfter.status===30" type="danger" :disabled="submitting" @click="processAfterSale('REFUSE')">拒收退货</el-button>
    </template>
    <el-alert v-else-if="selectedAfter.status===10||selectedAfter.status===30" :closable="false" :title="selectedAfter.processBlockReason||'当前事项不可重复处理，请稍后刷新详情。'"/>
   </template><el-empty v-else-if="!afterBusy" description="未能读取售后单，请关闭后重试"/></div>
  </el-dialog>
 </div>
</template>
<style scoped>
.new-order{font-size:12px;color:#a74410;border:1px solid #a74410;padding:1px 5px}.count-scope{font-size:12px;color:#64748b}.count-scope span{color:#a13e15}
.sales-trade{display:grid;grid-template-columns:230px minmax(0,1fr);gap:24px;margin:32px auto;min-height:640px}aside{background:#243952;color:#fff;padding:22px;border-radius:8px;align-self:start}aside a,aside>span{display:block;padding:12px;margin:6px -8px;border-radius:4px}aside>span{color:#b6c6d6}.active{background:#3267a6}aside p{color:#ccdae8}main{padding:28px}h1{margin-bottom:24px}.filters{display:flex;gap:12px;margin:24px 0}.order-status-tabs{display:flex;gap:8px;flex-wrap:wrap;margin-top:24px;border-bottom:1px solid #e4e9ef;padding-bottom:12px}.order-status-tabs button{border:0;border-radius:6px;padding:10px 20px;background:#f1f5f9;color:#475569;cursor:pointer;font:inherit}.order-status-tabs button[aria-selected="true"]{background:#3267a6;color:#fff}.order-status-tabs button:focus-visible{outline:2px solid #3267a6;outline-offset:2px}.filters .el-input{max-width:330px}.filters .el-select{width:175px}.el-pagination{margin-top:24px}.item{display:flex;gap:12px;align-items:center}.item img{width:48px;height:48px;object-fit:contain}small{display:block;color:#64748b}p{margin:16px 0;line-height:1.7}.shipping{display:grid;grid-template-columns:180px 1fr auto;gap:12px;margin-top:24px}.shipping h3{grid-column:1/-1}
</style>
