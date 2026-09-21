<script setup lang="ts">
import { useAccountOperation } from '@/composables/useAccountOperation'
const operation = useAccountOperation()
import { computed,onMounted,onBeforeUnmount,reactive,ref,watch } from 'vue'
import { useRoute,useRouter,onBeforeRouteLeave,onBeforeRouteUpdate } from 'vue-router'
import { ElMessage } from 'element-plus'
import { inquiryApi,type Inquiry,type InquiryInvitationOption,type InquiryInvitationScope,type InquiryItem,type ImportRow,type InquirySupplierShopOption } from '@/api/inquiry'
import { enterpriseApi } from '@/api/enterprise'
import InquiryProductPicker from '@/components/InquiryProductPicker.vue'
import InquiryItemImage from '@/components/InquiryItemImage.vue'
import InquiryCategoryInput from '@/components/InquiryCategoryInput.vue'
import { productApi, systemApi, type AreaNode } from '@/api/mall'
import { inquiryCategories, type InquiryCategoryOption } from '@/utils/inquiry-category'
import InquiryImportPreview from '@/components/InquiryImportPreview.vue'
import { inquiryDraftSnapshot } from '@/utils/inquiry-draft'
import { hasInquiryInput } from '@/utils/inquiry-import'
const route=useRoute(),router=useRouter(),busy=ref(false),loaded=ref(false),preview=ref<ImportRow[]>([]),previewOpen=ref(false)
const freshForm=():Inquiry=>({id:undefined,no:undefined,expectedDate:undefined,deliveryAreaId:undefined,requestKey:crypto.randomUUID(),title:'',contactName:'',contactMobile:'',address:'',remark:'',deadline:Date.now()+86400000,status:0,visibility:'TENANT_PUBLIC',invitedShopIds:[],invitedScopes:[]})
const form=reactive<Inquiry>(freshForm()),loadVersion=ref(0),loadFailed=ref(false)
const items=ref<InquiryItem[]>([])
const supplierShops=ref<InquirySupplierShopOption[]>([]),supplierShopsLoading=ref(false),supplierShopsFailed=ref(false)
const invitationOptions=ref<InquiryInvitationOption[]>([]),invitationOptionsLoading=ref(false),invitationOptionsFailed=ref(false)
let invitationSearchVersion=0
function invitationKey(scope:Pick<InquiryInvitationScope,'targetTenantId'|'targetEnterpriseId'|'targetShopId'>){return `${scope.targetTenantId}:${scope.targetEnterpriseId??''}:${scope.targetShopId??''}`}
function shopScopeFromKey(value:string):InquiryInvitationScope|null{const parts=value.split(':').map(Number);if(parts.length!==3||parts.some(part=>!Number.isSafeInteger(part)||part<=0))return null;return {targetType:'SHOP',targetTenantId:parts[0]!,targetEnterpriseId:parts[1]!,targetShopId:parts[2]!}}
const crossTenantShopKeys=computed<string[]>({
 get:()=>form.invitedScopes?.filter(scope=>scope.targetType==='SHOP'&&scope.targetEnterpriseId&&scope.targetShopId).map(invitationKey)??[],
 set:keys=>{const broad=(form.invitedScopes??[]).filter(scope=>scope.targetType!=='SHOP');form.invitedScopes=[...broad,...keys.map(shopScopeFromKey).filter((scope):scope is InquiryInvitationScope=>scope!==null)]}
})
const displayedInvitationOptions=computed(() => {
 const options=new Map(invitationOptions.value.map(option=>[invitationKey({targetTenantId:option.tenantId,targetEnterpriseId:option.enterpriseId,targetShopId:option.shopId}),option]))
 for(const scope of form.invitedScopes??[]){if(scope.targetType!=='SHOP'||!scope.targetEnterpriseId||!scope.targetShopId)continue;const key=invitationKey(scope);if(!options.has(key))options.set(key,{tenantId:scope.targetTenantId,tenantName:`租户 #${scope.targetTenantId}`,enterpriseId:scope.targetEnterpriseId,enterpriseName:`企业 #${scope.targetEnterpriseId}`,shopId:scope.targetShopId,shopName:`店铺 #${scope.targetShopId}`})}
 return [...options.values()]
})
function invitationLabel(option:InquiryInvitationOption){return `${option.tenantName} / ${option.enterpriseName} / ${option.shopName}`}
function invitationOptionKey(option:InquiryInvitationOption){return invitationKey({targetTenantId:option.tenantId,targetEnterpriseId:option.enterpriseId,targetShopId:option.shopId})}
function broadScopeLabel(scope:InquiryInvitationScope){return scope.targetType==='TENANT'?`租户 #${scope.targetTenantId}`:`租户 #${scope.targetTenantId} / 企业 #${scope.targetEnterpriseId}`}
function removeBroadScope(scope:InquiryInvitationScope){form.invitedScopes=(form.invitedScopes??[]).filter(current=>current!==scope)}
async function loadInvitationOptions(keyword=''){
 const ticket=++invitationSearchVersion;invitationOptionsLoading.value=true;invitationOptionsFailed.value=false
 try{const value=keyword.trim();const rows=await inquiryApi.invitationOptions({keyword:value||undefined,limit:100});if(ticket===invitationSearchVersion)invitationOptions.value=rows}
 catch{if(ticket===invitationSearchVersion){invitationOptions.value=[];invitationOptionsFailed.value=true}}
 finally{if(ticket===invitationSearchVersion)invitationOptionsLoading.value=false}
}
function clearInvitations(value:unknown){if(value!=='INVITED_SHOPS'){form.invitedShopIds=[];form.invitedScopes=[]}}
const areaTree=ref<AreaNode[]>([]),areasLoading=ref(false),areasFailed=ref(false)
async function loadAreas(){if(areasLoading.value)return;areasLoading.value=true;areasFailed.value=false;try{areaTree.value=await systemApi.getAreaTree()}catch{areaTree.value=[];areasFailed.value=true}finally{areasLoading.value=false}}
const categoryOptions=ref<InquiryCategoryOption[]>([]),categoriesReady=ref(false),categoriesLoading=ref(false)
async function loadCategories(){if(categoriesLoading.value)return;categoriesLoading.value=true;categoriesReady.value=false;try{categoryOptions.value=inquiryCategories(await productApi.getCategories());categoriesReady.value=true}catch{/* 不覆盖明细，保留手填并允许重试。 */}finally{categoriesLoading.value=false}}
const imageUploads=ref(0)
const locked=computed(()=>busy.value||imageUploads.value>0)
const itemKeys=new WeakMap<InquiryItem,number>();let nextItemKey=0
function itemKey(item:InquiryItem){if(!itemKeys.has(item))itemKeys.set(item,++nextItemKey);return itemKeys.get(item)!}
const editable=computed(()=>loaded.value && form.status===0)
const savedSnapshot=ref('');let savedNavigation=false
const dirty=computed(()=>editable.value&&inquiryDraftSnapshot(form,items.value)!==savedSnapshot.value)
function confirmLeave(){
 if(savedNavigation||(!dirty.value&&!locked.value))return true
 return window.confirm(locked.value?'请求仍在处理中。离开不会撤销已发送的操作，未保存内容也可能丢失。确定离开？':'询价单有未保存的修改。离开将丢弃这些修改，确定离开？')
}
function beforeUnload(event:BeforeUnloadEvent){if(dirty.value||locked.value){event.preventDefault();event.returnValue=''}}
onBeforeRouteLeave(confirmLeave)
onBeforeRouteUpdate((to,from)=>to.params.id===from.params.id||confirmLeave())
const blank=():InquiryItem=>({code:'',name:'',category:'',specification:'',quantity:1,unit:'',remark:''})
const hasInput=hasInquiryInput
function addProduct(item:InquiryItem){if(!editable.value||locked.value)return;const current=items.value.filter(hasInput);if(current.length>=200)return void ElMessage.warning('最多200行');items.value=[...current,item]}
async function load(){
 const version=++loadVersion.value,id=String(route.params.id||'new')
 loaded.value=false;loadFailed.value=false;busy.value=false;previewOpen.value=false;preview.value=[];items.value=[];imageUploads.value=0
 Object.assign(form,freshForm())
 try{
  const company=await enterpriseApi.mine()
  if(version!==loadVersion.value)return
  if(company?.status!==1){await router.replace('/enterprise/profile');return}
  supplierShopsLoading.value=true;supplierShopsFailed.value=false
  try{supplierShops.value=await inquiryApi.supplierShopOptions()}catch{supplierShops.value=[];supplierShopsFailed.value=true}finally{supplierShopsLoading.value=false}
  await loadInvitationOptions()
  if(version!==loadVersion.value)return
  if(id!=='new'){
   const number=Number(id);if(!Number.isSafeInteger(number)||number<=0)throw new Error('询价单编号无效')
   const data=await inquiryApi.detail(number)
   if(version!==loadVersion.value)return
   Object.assign(form,data.order,{visibility:data.order.visibility??'TENANT_PUBLIC',invitedShopIds:data.invitedShopIds??data.order.invitedShopIds??[],invitedScopes:data.invitedScopes??data.order.invitedScopes??[]});items.value=data.items
  }else{form.contactName=company.contactName;form.contactMobile=company.contactMobile;form.address=company.address;items.value=[blank()]}
  savedSnapshot.value=inquiryDraftSnapshot(form,items.value);loaded.value=true
 }catch{if(version===loadVersion.value)loadFailed.value=true}
}
async function save(submit=false){if(!editable.value||locked.value)return;if(!form.title.trim()||!items.value.length||items.value.some(i=>!i.name.trim()||!i.category.trim()||!i.unit.trim()||!(Number(i.quantity)>0)))return void ElMessage.warning('请填写标题以及每行物品名称、分类、数量和单位');if(form.visibility==='PLATFORM_PUBLIC'&&!form.deliveryAreaId)return void ElMessage.warning('平台公开询价必须选择交付省、市和区县');if(form.visibility==='INVITED_SHOPS'&&!(form.invitedShopIds?.length||form.invitedScopes?.length))return void ElMessage.warning('定向询价至少选择一家本租户或跨租户供应店铺');const version=loadVersion.value;busy.value=true;try{const id=await inquiryApi.save({...form,visibility:form.visibility??'TENANT_PUBLIC',invitedShopIds:form.visibility==='INVITED_SHOPS'?form.invitedShopIds:[],invitedScopes:form.visibility==='INVITED_SHOPS'?form.invitedScopes:[],deadline:Number(form.deadline),expectedDate:form.expectedDate?Number(form.expectedDate):undefined,items:items.value});if(!operation.current()||version!==loadVersion.value)return;form.id=id;savedSnapshot.value=inquiryDraftSnapshot(form,items.value);if(submit){await inquiryApi.submit(id);if(!operation.current()||version!==loadVersion.value)return;ElMessage.success('询价单已保存并提交');savedNavigation=true;await router.push('/purchase/inquiries')}else{ElMessage.success('草稿已保存');savedNavigation=true;await router.replace('/purchase/inquiries/'+id)}}finally{savedNavigation=false;if(version===loadVersion.value)busy.value=false}}
async function upload(event:Event){const input=event.target as HTMLInputElement,file=input.files?.[0];if(!file||!editable.value||locked.value)return;const version=loadVersion.value;try{if(!file.name.toLowerCase().endsWith('.xlsx')||file.size>2097152)return void ElMessage.warning('请选择2MB以内.xlsx文件，最多200行');busy.value=true;const rows=await inquiryApi.preview(file);if(version!==loadVersion.value)return;preview.value=rows;previewOpen.value=true}finally{if(version===loadVersion.value)busy.value=false;input.value=''}}
function confirmImport(imported:InquiryItem[]){if(!editable.value||locked.value)return;const current=items.value.filter(hasInput);if(current.length+imported.length>200)return void ElMessage.warning('明细总数不能超过200行');items.value=[...current,...imported];previewOpen.value=false;ElMessage.success('明细已加入，请保存草稿；询价单尚未提交')}
async function template(){const version=loadVersion.value;const current=()=>operation.current()&&version===loadVersion.value;try{await inquiryApi.template(current)}catch(e){if(current())ElMessage.error((e as Error).message)}}
watch(()=>route.params.id,()=>{void load()},{immediate:true})
onBeforeUnmount(()=>{loadVersion.value++;invitationSearchVersion++;window.removeEventListener('beforeunload',beforeUnload)})
onMounted(()=>{void loadCategories();void loadAreas();window.addEventListener('beforeunload',beforeUnload)})
</script>
<template><div class="container panel inquiry-edit"><header><h1>{{form.status===0?'发布 / 编辑询价单':'询价单详情'}}</h1><MallLink to="/purchase/inquiries">返回询价单列表</MallLink></header><el-alert title="仅保存商城采购需求，不自动下单或扣款" :closable="false"/><el-alert class="privacy-warning" type="warning" title="信息隐私提示" description="平台不主动展示结构化联系人、手机号和详细收货地址；请勿在标题、物料名称、规格、备注、补充说明或图片中填写联系方式、详细地址或二维码。" :closable="false" show-icon/><p v-if="form.no">单号：{{form.no}}　状态：{{['草稿','已提交','已截止','已关闭','已成交'][form.status!]}}</p><p v-if="dirty" role="status">有未保存修改，请保存草稿后离开。</p><p v-if="loadFailed" role="alert">询价单加载失败，原单据未修改。<el-button @click="load">重新加载</el-button></p><p v-else-if="!loaded" role="status">正在加载询价单…</p><el-form :key="loadVersion" label-position="top" :disabled="!editable||locked"><el-form-item label="询价标题" required><el-input v-model="form.title" maxlength="100"/></el-form-item><div class="row"><el-form-item label="联系人" required><el-input v-model="form.contactName" maxlength="50"/></el-form-item><el-form-item label="联系电话" required><el-input v-model="form.contactMobile" maxlength="11"/></el-form-item><el-form-item label="报价截止时间" required><el-date-picker v-model="form.deadline" type="datetime" value-format="x"/></el-form-item></div><el-form-item label="交付省 / 市 / 区县" :required="form.visibility==='PLATFORM_PUBLIC'"><el-cascader v-model="form.deliveryAreaId" :options="areaTree" :props="{value:'id',label:'name',emitPath:false}" :placeholder="areasLoading?'地区目录加载中…':'请选择交付区县'" :disabled="areasLoading||areasFailed" filterable clearable style="width:100%"/><p v-if="areasFailed" role="alert">地区目录加载失败，平台公开询价暂不能保存。<el-button link @click="loadAreas">重新加载</el-button></p></el-form-item><el-form-item label="详细收货地址" required><el-input v-model="form.address" maxlength="255"/></el-form-item><el-form-item label="供应商可见范围" required><el-radio-group v-model="form.visibility" @change="clearInvitations"><el-radio value="PLATFORM_PUBLIC">全平台合格供应店铺公开</el-radio><el-radio value="TENANT_PUBLIC">仅本租户供应店铺公开</el-radio><el-radio value="INVITED_SHOPS">仅定向邀请店铺</el-radio></el-radio-group><p v-if="form.visibility==='PLATFORM_PUBLIC'">仅已开放正式租户中，资质、店铺授权、经营品类及商品运费模板配送地区均匹配的供应商后台账号可查看并报价。</p></el-form-item><template v-if="form.visibility==='INVITED_SHOPS'"><el-form-item label="本租户邀请店铺"><el-select v-model="form.invitedShopIds" multiple filterable collapse-tags collapse-tags-tooltip :multiple-limit="100" :loading="supplierShopsLoading" :disabled="supplierShopsFailed" placeholder="可选择本租户其他企业店铺" style="width:100%"><el-option v-for="shop in supplierShops" :key="shop.id" :label="`${shop.enterpriseName} / ${shop.name}`" :value="shop.id"/></el-select><p v-if="supplierShopsFailed" role="alert">本租户供应店铺目录加载失败，请重新加载页面。</p><p v-else-if="!supplierShopsLoading&&!supplierShops.length">当前租户暂无其他已启用且资质通过的供应店铺。</p></el-form-item><el-form-item label="跨租户邀请店铺"><el-select v-model="crossTenantShopKeys" multiple filterable remote reserve-keyword collapse-tags collapse-tags-tooltip :multiple-limit="100" :remote-method="loadInvitationOptions" :loading="invitationOptionsLoading" placeholder="按租户、企业或店铺名称搜索" style="width:100%"><el-option v-for="option in displayedInvitationOptions" :key="invitationOptionKey(option)" :label="invitationLabel(option)" :value="invitationOptionKey(option)"/></el-select><p v-if="invitationOptionsFailed" role="alert">跨租户邀请目录加载失败；已选范围仍会回显，请重试搜索后再保存。</p><p v-else>只列出已开放商城、资质通过且店铺启用的正式供应店铺；目录不展示联系方式、资质附件和库存。</p><div v-if="(form.invitedScopes??[]).some(scope=>scope.targetType!=='SHOP')" class="existing-scopes"><span>原有定向范围：</span><el-tag v-for="scope in (form.invitedScopes??[]).filter(item=>item.targetType!=='SHOP')" :key="`${scope.targetType}:${scope.targetTenantId}:${scope.targetEnterpriseId??''}`" :closable="editable" @close="removeBroadScope(scope)">{{broadScopeLabel(scope)}}</el-tag></div></el-form-item><p>本租户店铺与跨租户范围可同时选择；每类最多选择 100 个。</p></template><h2>询价物品</h2><p>分类名称按商城目录辅助核对，仍保存名称快照；非商城物料可手填。同名分类请在备注说明，不自动关联系统物料或换算单位。<el-button link :disabled="categoriesLoading" @click="loadCategories">{{categoriesLoading?'加载分类中…':'刷新分类目录'}}</el-button></p><p>每行可上传1张2MB以内PNG/JPG/WebP参考图。图片使用现有文件服务，链接可访问；请勿上传营业执照、身份证、联系方式、详细地址、二维码或其他敏感资料。上传后需保存，移除仅取消关联。</p><div v-if="editable" class="import"><InquiryProductPicker :disabled="locked||!editable||items.length>=200" @select="addProduct"/><el-button @click="template">下载Excel模板</el-button><label>导入Excel（最多200行、2MB）<input type="file" accept=".xlsx" :disabled="locked" @change="upload"/></label><el-button :disabled="items.length>=200" @click="items.push(blank())">添加物品</el-button></div><div class="items"><table><thead><tr><th>名称 *</th><th>编码（SKU条码）</th><th>分类 *</th><th>规格</th><th>数量 *</th><th>单位 *</th><th>备注</th><th>参考图片</th><th/></tr></thead><tbody><tr v-for="(item,i) in items" :key="itemKey(item)"><td><el-input v-model="item.name" maxlength="200"/></td><td><el-input v-model="item.code" maxlength="100" :disabled="!!item.skuId"/></td><td><InquiryCategoryInput v-model="item.category" :options="categoryOptions" :ready="categoriesReady" :disabled="!editable||locked"/></td><td><el-input v-model="item.specification" maxlength="500"/></td><td><el-input-number v-model="item.quantity" :min="0.001" :max="999999999.999" :precision="3" :controls="false" style="width:120px"/></td><td><el-input v-model="item.unit" maxlength="20"/></td><td><el-input v-model="item.remark" maxlength="500"/></td><td><InquiryItemImage v-model="item.picUrl" :disabled="!editable||locked" @busy="v=>imageUploads+=v?1:-1"/></td><td><el-button link type="danger" @click="items.splice(i,1)">移除</el-button></td></tr></tbody></table></div><el-form-item label="期望到货日期"><el-date-picker v-model="form.expectedDate" type="datetime" value-format="x"/></el-form-item><el-form-item label="补充说明"><el-input v-model="form.remark" type="textarea" maxlength="2000"/></el-form-item><div v-if="editable"><el-button :loading="locked" @click="save(false)">保存草稿</el-button><el-button type="primary" :loading="locked" @click="save(true)">保存并提交</el-button></div></el-form><el-dialog v-model="previewOpen" title="Excel导入预览：核对后加入明细" width="94%" :close-on-click-modal="false"><InquiryImportPreview v-if="previewOpen" :rows="preview" :existing="items" :categories="categoryOptions" :categories-ready="categoriesReady" :disabled="locked||!editable" @cancel="previewOpen=false" @confirm="confirmImport"/></el-dialog></div></template>
<style scoped>.inquiry-edit{padding:32px;margin:32px auto}header,.row,.import{display:flex;gap:24px;align-items:center}header{justify-content:space-between}.privacy-warning{margin-top:12px}.row>*{flex:1}form{margin-top:24px}.import{margin:18px 0}.items{overflow:auto;margin-bottom:24px}.existing-scopes{display:flex;gap:8px;align-items:center;flex-wrap:wrap;margin-top:8px}table{width:100%;min-width:1000px;border-spacing:8px}th{text-align:left;color:#64748b;font-weight:500}td{min-width:95px}a{color:#167664}</style>
