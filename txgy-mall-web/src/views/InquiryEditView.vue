<script setup lang="ts">
import { useAccountOperation } from '@/composables/useAccountOperation'
const operation = useAccountOperation()
import { computed,onMounted,onBeforeUnmount,reactive,ref,watch } from 'vue'
import { useRoute,useRouter,onBeforeRouteLeave,onBeforeRouteUpdate } from 'vue-router'
import { ElMessage } from 'element-plus'
import { inquiryApi,type Inquiry,type InquiryItem,type ImportRow } from '@/api/inquiry'
import { enterpriseApi } from '@/api/enterprise'
import InquiryProductPicker from '@/components/InquiryProductPicker.vue'
import InquiryItemImage from '@/components/InquiryItemImage.vue'
import InquiryCategoryInput from '@/components/InquiryCategoryInput.vue'
import { productApi } from '@/api/mall'
import { inquiryCategories, type InquiryCategoryOption } from '@/utils/inquiry-category'
import InquiryImportPreview from '@/components/InquiryImportPreview.vue'
import { inquiryDraftSnapshot } from '@/utils/inquiry-draft'
import { hasInquiryInput } from '@/utils/inquiry-import'
const route=useRoute(),router=useRouter(),busy=ref(false),loaded=ref(false),preview=ref<ImportRow[]>([]),previewOpen=ref(false)
const freshForm=():Inquiry=>({id:undefined,no:undefined,expectedDate:undefined,erpStatus:undefined,requestKey:crypto.randomUUID(),title:'',contactName:'',contactMobile:'',address:'',remark:'',deadline:Date.now()+86400000,status:0})
const form=reactive<Inquiry>(freshForm()),loadVersion=ref(0),loadFailed=ref(false)
const items=ref<InquiryItem[]>([])
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
  if(id!=='new'){
   const number=Number(id);if(!Number.isSafeInteger(number)||number<=0)throw new Error('询价单编号无效')
   const data=await inquiryApi.detail(number)
   if(version!==loadVersion.value)return
   Object.assign(form,data.order);items.value=data.items
  }else{form.contactName=company.contactName;form.contactMobile=company.contactMobile;form.address=company.address;items.value=[blank()]}
  savedSnapshot.value=inquiryDraftSnapshot(form,items.value);loaded.value=true
 }catch{if(version===loadVersion.value)loadFailed.value=true}
}
async function save(submit=false){if(!editable.value||locked.value)return;if(!form.title.trim()||!items.value.length||items.value.some(i=>!i.name.trim()||!i.category.trim()||!i.unit.trim()||!(Number(i.quantity)>0)))return void ElMessage.warning('请填写标题以及每行物品名称、分类、数量和单位');const version=loadVersion.value;busy.value=true;try{const id=await inquiryApi.save({...form,deadline:Number(form.deadline),expectedDate:form.expectedDate?Number(form.expectedDate):undefined,items:items.value});if(!operation.current()||version!==loadVersion.value)return;form.id=id;savedSnapshot.value=inquiryDraftSnapshot(form,items.value);if(submit){await inquiryApi.submit(id);if(!operation.current()||version!==loadVersion.value)return;ElMessage.success('询价单已保存并提交，尚未发送至ERP或供应商');savedNavigation=true;await router.push('/purchase/inquiries')}else{ElMessage.success('草稿已保存');savedNavigation=true;await router.replace('/purchase/inquiries/'+id)}}finally{savedNavigation=false;if(version===loadVersion.value)busy.value=false}}
async function upload(event:Event){const input=event.target as HTMLInputElement,file=input.files?.[0];if(!file||!editable.value||locked.value)return;const version=loadVersion.value;try{if(!file.name.toLowerCase().endsWith('.xlsx')||file.size>2097152)return void ElMessage.warning('请选择2MB以内.xlsx文件，最多200行');busy.value=true;const rows=await inquiryApi.preview(file);if(version!==loadVersion.value)return;preview.value=rows;previewOpen.value=true}finally{if(version===loadVersion.value)busy.value=false;input.value=''}}
function confirmImport(imported:InquiryItem[]){if(!editable.value||locked.value)return;const current=items.value.filter(hasInput);if(current.length+imported.length>200)return void ElMessage.warning('明细总数不能超过200行');items.value=[...current,...imported];previewOpen.value=false;ElMessage.success('明细已加入，请保存草稿；询价单尚未提交')}
async function template(){const version=loadVersion.value;const current=()=>operation.current()&&version===loadVersion.value;try{await inquiryApi.template(current)}catch(e){if(current())ElMessage.error((e as Error).message)}}
watch(()=>route.params.id,()=>{void load()},{immediate:true})
onBeforeUnmount(()=>{loadVersion.value++;window.removeEventListener('beforeunload',beforeUnload)})
onMounted(()=>{void loadCategories();window.addEventListener('beforeunload',beforeUnload)})
</script>
<template><div class="container panel inquiry-edit"><header><h1>{{form.status===0?'发布 / 编辑询价单':'询价单详情'}}</h1><MallLink to="/purchase/inquiries">返回询价单列表</MallLink></header><el-alert title="仅保存商城采购需求，不自动下单、扣款或发送至ERP/供应商" :closable="false"/><p v-if="form.no">单号：{{form.no}}　状态：{{['草稿','已提交','已截止','已关闭'][form.status!]}}</p><p v-if="dirty" role="status">有未保存修改，请保存草稿后离开。</p><p v-if="loadFailed" role="alert">询价单加载失败，原单据未修改。<el-button @click="load">重新加载</el-button></p><p v-else-if="!loaded" role="status">正在加载询价单…</p><el-form :key="loadVersion" label-position="top" :disabled="!editable||locked"><el-form-item label="询价标题" required><el-input v-model="form.title" maxlength="100"/></el-form-item><div class="row"><el-form-item label="联系人" required><el-input v-model="form.contactName" maxlength="50"/></el-form-item><el-form-item label="联系电话" required><el-input v-model="form.contactMobile" maxlength="11"/></el-form-item><el-form-item label="报价截止时间" required><el-date-picker v-model="form.deadline" type="datetime" value-format="x"/></el-form-item></div><el-form-item label="收货地区 / 地址" required><el-input v-model="form.address" maxlength="255"/></el-form-item><h2>询价物品</h2><p>分类名称按商城目录辅助核对，仍保存名称快照；非商城物料可手填。同名分类请在备注说明，不自动关联ERP或换算单位。<el-button link :disabled="categoriesLoading" @click="loadCategories">{{categoriesLoading?'加载分类中…':'刷新分类目录'}}</el-button></p><p>每行可上传1张2MB以内PNG/JPG/WebP参考图。图片使用现有文件服务，链接可访问；请勿上传营业执照、身份证或其他敏感资料。上传后需保存，移除仅取消关联。</p><div v-if="editable" class="import"><InquiryProductPicker :disabled="locked||!editable||items.length>=200" @select="addProduct"/><el-button @click="template">下载Excel模板</el-button><label>导入Excel（最多200行、2MB）<input type="file" accept=".xlsx" :disabled="locked" @change="upload"/></label><el-button :disabled="items.length>=200" @click="items.push(blank())">添加物品</el-button></div><div class="items"><table><thead><tr><th>名称 *</th><th>编码（SKU条码）</th><th>分类 *</th><th>规格</th><th>数量 *</th><th>单位 *</th><th>备注</th><th>参考图片</th><th/></tr></thead><tbody><tr v-for="(item,i) in items" :key="itemKey(item)"><td><el-input v-model="item.name" maxlength="200"/></td><td><el-input v-model="item.code" maxlength="100" :disabled="!!item.skuId"/></td><td><InquiryCategoryInput v-model="item.category" :options="categoryOptions" :ready="categoriesReady" :disabled="!editable||locked"/></td><td><el-input v-model="item.specification" maxlength="500"/></td><td><el-input-number v-model="item.quantity" :min="0.001" :max="999999999.999" :precision="3" :controls="false" style="width:120px"/></td><td><el-input v-model="item.unit" maxlength="20"/></td><td><el-input v-model="item.remark" maxlength="500"/></td><td><InquiryItemImage v-model="item.picUrl" :disabled="!editable||locked" @busy="v=>imageUploads+=v?1:-1"/></td><td><el-button link type="danger" @click="items.splice(i,1)">移除</el-button></td></tr></tbody></table></div><el-form-item label="期望到货日期"><el-date-picker v-model="form.expectedDate" type="datetime" value-format="x"/></el-form-item><el-form-item label="补充说明"><el-input v-model="form.remark" type="textarea" maxlength="2000"/></el-form-item><div v-if="editable"><el-button :loading="locked" @click="save(false)">保存草稿</el-button><el-button type="primary" :loading="locked" @click="save(true)">保存并提交</el-button></div></el-form><el-dialog v-model="previewOpen" title="Excel导入预览：核对后加入明细" width="94%" :close-on-click-modal="false"><InquiryImportPreview v-if="previewOpen" :rows="preview" :existing="items" :categories="categoryOptions" :categories-ready="categoriesReady" :disabled="locked||!editable" @cancel="previewOpen=false" @confirm="confirmImport"/></el-dialog></div></template>
<style scoped>.inquiry-edit{padding:32px;margin:32px auto}header,.row,.import{display:flex;gap:24px;align-items:center}header{justify-content:space-between}.row>*{flex:1}form{margin-top:24px}.import{margin:18px 0}.items{overflow:auto;margin-bottom:24px}table{width:100%;min-width:1000px;border-spacing:8px}th{text-align:left;color:#64748b;font-weight:500}td{min-width:95px}a{color:#167664}</style>
