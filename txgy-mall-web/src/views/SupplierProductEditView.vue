<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAccountMessageBox } from '@/composables/useAccountOperation'
const ElMessageBox = useAccountMessageBox()
import { supplierApi, type SupplierProduct, type SupplierOptions, type SupplierSku } from '@/api/supplier'
import { enterpriseApi } from '@/api/enterprise'
import { productApi } from '@/api/mall'
import type { Category } from '@/types/mall'
import ProductImages from '@/components/ProductImages.vue'
import SupplierStock from '@/components/SupplierStock.vue'
import SupplierSkuImage from '@/components/SupplierSkuImage.vue'
import ProductReviewHistory from '@/components/ProductReviewHistory.vue'
import { sanitizeRichHtml } from '@/utils/format'
import { createProductReviewRequestGate, productEditableFingerprint, productReviewActions, productReviewCommand, productReviewLabel } from '@/utils/product-review'
const uploadingImages=ref(false)
const uploadingSkuImages=ref(0)
const anyUploading=computed(()=>uploadingImages.value||uploadingSkuImages.value>0)
const originalSkuImages=new Map<number,string|undefined>()
const dimensions=ref<number[]>([])
function syncDimensions(){for(const sku of form.skus)sku.properties=dimensions.value.map(propertyId=>({propertyId,valueId:sku.properties?.find(p=>p.propertyId===propertyId)?.valueId}))}
const skuKeys=new WeakMap<SupplierSku,number>()
let nextSkuKey=0
function skuKey(sku:SupplierSku){if(!skuKeys.has(sku))skuKeys.set(sku,++nextSkuKey);return skuKeys.get(sku)!}
function removeSku(sku:SupplierSku){
 if(editing.value||busy.value||anyUploading.value)return
 if(form.skus.length<=1)return void ElMessage.warning('商品至少保留一个规格；可修改当前规格内容')
 const index=form.skus.indexOf(sku)
 if(index>=0)form.skus.splice(index,1)
}
function addSku(){if(form.skus.length>=100)return;form.skus.push({price:100,marketPrice:100,initialStock:0,barCode:'',weight:0,volume:0,properties:dimensions.value.map(propertyId=>({propertyId}))})}
async function changeSpecType(value:unknown){
 if(!value&&form.skus.length>1){form.specType=true;ElMessage.warning('请先删除多余SKU，再切换单规格');return}
 for(const sku of form.skus)sku.properties=value?dimensions.value.map(propertyId=>({propertyId})):undefined
}

const route = useRoute(), router = useRouter(), shopId = computed(() => Number(route.params.shopId))
const editing = computed(() => route.params.id !== 'new'), busy = ref(false), loaded = ref(false), error = ref(false)
const saveError = ref(''), baseline = ref(''), renderVersion = ref(0)
const gate = createProductReviewRequestGate()
const categories = ref<Category[]>([]), options = ref<SupplierOptions>({ brands: [], templates: [] })
const blank = (): SupplierProduct => ({ shopId: shopId.value, name: '', keyword: '', introduction: '', descriptionText: '', picUrl: '', sliderPicUrls: [],
  skus: [{ price: 100, marketPrice: 100, initialStock: 0, barCode: '', weight: 0, volume: 0 }] })
const form = reactive<SupplierProduct>(blank())
const actions = computed(() => productReviewActions(form))
const editable = computed(() => loaded.value && (!editing.value || actions.value.edit))
const dirty = computed(() => loaded.value && productEditableFingerprint(form) !== baseline.value)
const safeDescription = computed(() => sanitizeRichHtml(form.description))
const leafCategories = computed(() => categories.value.filter(c => Number(c.parentId) !== 0 && !categories.value.some(child => String(child.parentId) === String(c.id))))
const materialPropertyNames=['材质','厚度','容重','公称直径','壁厚','包装规格','长度','宽度','颜色','规格','单位','重量']
const propertyGroups=computed(()=>[
 {label:'防腐保温常用规格',items:materialPropertyNames.flatMap(name=>(options.value.properties||[]).filter(p=>p.name===name))},
 {label:'其他已有属性',items:(options.value.properties||[]).filter(p=>!materialPropertyNames.includes(p.name))},
].filter(group=>group.items.length))
const template = computed(() => options.value.templates.find(t => t.id === form.deliveryTemplateId))
async function load() {
  const ticket = gate.begin(), scope = shopId.value, productId = Number(route.params.id), isEditing = editing.value
  busy.value = true; error.value = false; loaded.value = false; saveError.value = ''
  try {
    const shops = await enterpriseApi.salesShops()
    if (!gate.isCurrent(ticket)) return
    if (!shops.some(s => Number(s.id) === scope)) { await router.replace('/sales'); return }
    const [choices, cats, data] = await Promise.all([supplierApi.options(scope), productApi.getCategories(), isEditing ? supplierApi.detail(scope, productId) : Promise.resolve(undefined)])
    if (!gate.isCurrent(ticket)) return
    for (const key of Object.keys(form)) delete (form as unknown as Record<string, unknown>)[key]
    Object.assign(form, blank()); dimensions.value = []; originalSkuImages.clear()
    options.value = choices; categories.value = cats
    if (data) {
      Object.assign(form, data, { descriptionText: '' })
      form.sliderPicUrls=Array.from(new Set([data.picUrl,...(data.sliderPicUrls||[])].filter(Boolean)))
      form.skus = data.skus.map(s => ({ ...s, weight: s.weight || 0, volume: s.volume || 0, marketPrice: s.marketPrice || 0 }))
      originalSkuImages.clear();for(const sku of data.skus)if(sku.id)originalSkuImages.set(sku.id,sku.picUrl)
    }
    baseline.value = productEditableFingerprint(form)
    renderVersion.value++
    loaded.value = true
  } catch { if (gate.isCurrent(ticket)) error.value = true }
  finally { if (gate.isCurrent(ticket)) busy.value = false }
}
async function save() {
  if (!editable.value || busy.value || anyUploading.value) return
  if (editing.value && !dirty.value) return void ElMessage.info('经营内容没有变化，原版本和审核状态保持不变')
  if (!form.name.trim() || !form.keyword.trim() || !form.introduction.trim() || !form.picUrl.trim()
      || !form.categoryId || !form.brandId || !form.deliveryTemplateId || (!editing.value && !form.descriptionText?.trim()))
    return void ElMessage.warning('请完整填写商品、分类、品牌、图片、详情和运费模板')
  if (form.skus.some(s => s.price <= 0 || (template.value?.chargeMode === 2 && s.weight <= 0) || (template.value?.chargeMode === 3 && s.volume <= 0)))
    return void ElMessage.warning('售价须大于零；按重量/体积计费须填写对应正数值')
  if(!editing.value&&form.specType){
    if(!dimensions.value.length||form.skus.some(s=>s.properties?.length!==dimensions.value.length||s.properties.some(p=>!p.valueId)))return void ElMessage.warning('请选择规格属性，并为每个SKU选择完整规格值')
    const keys=form.skus.map(s=>[...(s.properties||[])].sort((a,b)=>a.propertyId-b.propertyId).map(p=>`${p.propertyId}:${p.valueId}`).join(','))
    if(new Set(keys).size!==keys.length)return void ElMessage.warning('规格组合重复，请修改')
  }
  const ticket = gate.current(), scope = shopId.value, isEditing = editing.value
  if (isEditing && (form.status === 1 || form.reviewStatus === 20)) {
    try { await ElMessageBox.confirm('保存经营资料修改后，将下架商品并作废原版本的发布资格。修改版本必须重新审核，通过后再手动上架；历史订单不受影响。确认保存？', '修改后须重新审核', { confirmButtonText: '确认保存并重新审核', cancelButtonText: '继续编辑' }) } catch { return }
    if (!gate.isCurrent(ticket) || !editable.value || busy.value) return
  }
  busy.value = true
  saveError.value = ''
  try {
    const expectedVersion = isEditing ? productReviewCommand(scope, form).expectedVersion : 0
    const id = await supplierApi.save({ ...form, expectedVersion, skus: form.skus.map(s => ({ id: s.id, price: s.price, marketPrice: s.marketPrice,
      picUrl:!isEditing||s.picUrl!==originalSkuImages.get(s.id!)?s.picUrl:undefined,
      weight: s.weight, volume: s.volume, barCode: s.barCode, initialStock: isEditing ? undefined : s.initialStock,properties:!isEditing&&form.specType?s.properties:undefined })) })
    if (!gate.isCurrent(ticket)) return
    baseline.value = productEditableFingerprint(form)
    ElMessage.success('商品草稿已保存，请核对后提交审核；尚未上架')
    if (!isEditing) { busy.value = false; await router.replace(`/sales/shops/${scope}/products/${id}`) }
    else await load()
  } catch (cause) {
    if (gate.isCurrent(ticket)) saveError.value = `${cause instanceof Error ? cause.message : '保存失败'}。已保留当前填写内容；版本冲突时请先记录修改，再刷新核对，未自动覆盖服务端数据。`
  } finally { if (gate.isCurrent(ticket)) busy.value = false }
}
async function review(action: 'submit' | 'withdraw') {
  if (!loaded.value || busy.value || anyUploading.value || !actions.value[action]) return
  if (dirty.value) return void ElMessage.warning('请先保存草稿，再提交已保存的商品版本')
  const ticket = gate.current()
  try { await ElMessageBox.confirm(action === 'submit' ? (form.status === 1 ? '提交审核会先下架当前商品，审核通过后需主动上架。确认提交？' : '确认将当前已保存版本提交后台审核？待审期间内容不可修改。') : '确认撤回当前审核？撤回后保持下架，修改并重新送审。', action === 'submit' ? '提交商品审核' : '撤回审核', { confirmButtonText: '确定', cancelButtonText: '取消' }) } catch { return }
  if (!gate.isCurrent(ticket) || busy.value) return
  busy.value = true; saveError.value = ''
  try {
    const command = productReviewCommand(shopId.value, form)
    if (action === 'submit') await supplierApi.submitReview(command)
    else await supplierApi.withdrawReview({ ...command, submissionId: form.submissionId! })
    if (!gate.isCurrent(ticket)) return
    ElMessage.success(action === 'submit' ? '已提交后台审核，尚未上架' : '已撤回，可修改后重新提交')
    await load()
  } catch (cause) { if (gate.isCurrent(ticket)) saveError.value = `${cause instanceof Error ? cause.message : '操作失败'}。请刷新核对最新审核状态，未自动重复提交。` }
  finally { if (gate.isCurrent(ticket)) busy.value = false }
}
async function confirmLeave() {
  if (!loaded.value) return true
  if (anyUploading.value || busy.value) { ElMessage.warning('正在上传或处理商品，请完成后再离开'); return false }
  if (!dirty.value) return true
  try { await ElMessageBox.confirm('有未保存的商品修改，确认离开并放弃本页修改？', '未保存的修改', { confirmButtonText: '离开', cancelButtonText: '继续编辑' }); return true } catch { return false }
}
async function reload() { if (await confirmLeave()) await load() }
onBeforeRouteLeave(to => to.name === 'login' ? true : confirmLeave())
onBeforeRouteUpdate(confirmLeave)
watch(() => [route.params.shopId, route.params.id], () => { loaded.value = false; void load() }, { immediate: true })
onBeforeUnmount(gate.invalidate)
</script>
<template>
  <section class="container panel supplier-edit" v-loading="busy">
    <MallLink :to="`/sales/shops/${shopId}/products`">← 返回商品管理</MallLink>
    <h1>{{editing?(editable?'编辑商品':'查看商品'):'新增商品草稿'}}</h1>
    <div v-if="editing&&loaded" class="review-summary"><el-tag>{{productReviewLabel(form.reviewStatus)}}</el-tag><span>内容版本 v{{form.contentVersion ?? '待读取'}}<template v-if="form.approvedVersion"> · 批准版本 v{{form.approvedVersion}}</template></span><ProductReviewHistory v-if="form.id" :shop-id="shopId" :product-id="form.id" :refresh-key="form.submissionId" :disabled="busy||anyUploading"/><el-button :disabled="busy||anyUploading" @click="reload">刷新状态</el-button><el-button v-if="actions.submit" type="primary" :disabled="busy||anyUploading||dirty" @click="review('submit')">提交当前版本审核</el-button><el-button v-if="actions.withdraw" :disabled="busy||anyUploading" @click="review('withdraw')">撤回审核</el-button></div>
    <el-alert v-if="form.reviewRemark" :title="`审核意见：${form.reviewRemark}`" :type="form.reviewStatus===30?'error':'info'" :closable="false"/>
    <el-alert v-if="actions.pending" title="当前版本待后台审核，内容已冻结；需要修改请先撤回审核。审核通过不会自动上架。" type="warning" :closable="false"/>
    <el-alert v-else title="新增或修改商品先保存下架草稿，再提交后台审核。所有经营内容修改均须重新审核；无实际差异的保存不会改变原版本。" :closable="false"/>
    <p v-if="dirty" role="status">有未保存的经营资料修改，请保存草稿后再提交审核。</p>
    <el-alert v-if="saveError" :title="saveError" type="error" :closable="false"/>
    <SupplierStock v-if="editing&&loaded&&form.id" :shop-id="shopId" :spu-id="form.id" :disabled="busy||anyUploading" @updated="skus=>{for(const sku of form.skus){const fresh=skus.find(s=>s.id===sku.id);if(fresh){sku.stock=fresh.stock;sku.wmsManaged=fresh.wmsManaged}}}"/>
    <el-alert title="新建支持单规格或多规格（最多100个SKU、3个属性）。请选择实际销售的规格组合，为每种规格填写售价和初始库存。" :closable="false"/>
    <el-alert v-if="error" title="读取商品或运费配置失败" type="error" :closable="false"><el-button @click="load">重试</el-button></el-alert>
    <section v-if="editing&&loaded" class="current-description"><h3>当前商品详情（已保存内容）</h3><div v-if="safeDescription" class="safe-description" v-html="safeDescription"/><p v-else>当前商品没有可展示的详情正文。</p><p>下方替换内容留空会保留此处正文；待审页面仅为授权预览，不是公开商品。</p></section>
    <el-form v-if="loaded" :key="renderVersion" label-position="top" :disabled="!editable||busy||anyUploading" @submit.prevent="save">
      <el-form-item label="商品名称" required><el-input v-model="form.name" maxlength="100"/></el-form-item>
      <div class="columns"><el-form-item label="商品分类" required><el-select v-model="form.categoryId" filterable><el-option v-for="c in leafCategories" :key="c.id" :value="Number(c.id)" :label="c.name"/></el-select></el-form-item>
        <el-form-item label="品牌" required><el-select v-model="form.brandId" filterable><el-option v-for="b in options.brands" :key="b.id" :value="b.id" :label="b.name"/></el-select></el-form-item></div>
      <el-form-item label="搜索关键词" required><el-input v-model="form.keyword" maxlength="255"/></el-form-item>
      <el-form-item label="商品简介" required><el-input v-model="form.introduction" maxlength="255"/></el-form-item>
      <el-form-item label="商品图片" required><ProductImages :model-value="form.sliderPicUrls||[]" :disabled="!editable||busy||anyUploading" @busy="v=>uploadingImages=v" @update:model-value="images=>{form.sliderPicUrls=images;form.picUrl=images[0]||''}"/></el-form-item>
      <el-form-item :label="editing?'替换商品详情（纯文本，留空保留原详情）':'商品详情（纯文本）'" :required="!editing"><el-input v-model="form.descriptionText" type="textarea" :rows="5" maxlength="10000"/></el-form-item>
      <el-form-item label="快递运费模板" required><el-select v-model="form.deliveryTemplateId"><el-option v-for="t in options.templates" :key="t.id" :value="t.id" :label="`${t.name}（${['','按件','按重量','按体积'][t.chargeMode]||'请核对计费方式'}）`"/></el-select></el-form-item>
      <p>模板由平台维护；能否送达具体地区及实际运费，以结算页按收货地址计算为准。</p>
      <div v-if="!editing"><el-form-item label="启用多规格"><el-switch v-model="form.specType" @change="changeSpecType"/></el-form-item>
       <el-form-item v-if="form.specType" label="规格属性（最多3个，由平台维护）"><el-select v-model="dimensions" multiple :multiple-limit="3" filterable @change="syncDimensions"><el-option-group v-for="group in propertyGroups" :key="group.label" :label="group.label"><el-option v-for="p in group.items" :key="p.id" :label="p.name" :value="p.id" :disabled="!p.values.length"/></el-option-group></el-select></el-form-item>
       <p v-if="form.specType">保温板可选择材质、厚度、容重；管材可选择公称直径、壁厚；涂料可选择包装规格、颜色。每行填写一种实际销售组合。</p>
      </div>
      <div v-for="(sku,index) in form.skus" :key="skuKey(sku)" class="sku">
        <div class="sku-heading"><h3>{{editing?(sku.specification||'默认规格'):form.specType?`规格 ${index+1}`:'默认规格'}}</h3><el-button v-if="!editing&&form.specType" type="danger" plain :disabled="form.skus.length<=1" @click="removeSku(sku)">删除此SKU</el-button></div>
        <p v-if="!editing&&form.specType&&form.skus.length===1" class="sku-help">至少保留一个规格；添加多行后可删除多余行。</p>
        <template v-if="!editing&&form.specType"><div class="columns"><el-form-item v-for="p in sku.properties||[]" :key="p.propertyId" :label="options.properties?.find(x=>x.id===p.propertyId)?.name" required><el-select v-model="p.valueId" filterable placeholder="请选择规格值"><el-option v-for="v in options.properties?.find(x=>x.id===p.propertyId)?.values||[]" :key="v.id" :label="v.name" :value="v.id"/></el-select></el-form-item></div></template>
        <el-form-item label="SKU独立图片（选填）"><SupplierSkuImage v-model="sku.picUrl" :disabled="!editable||busy||anyUploading" @busy="v=>uploadingSkuImages+=v?1:-1"/><p>每个SKU一张，PNG/JPG/WebP、2MB以内。点击“使用当前主图”会在保存时复制当前商品主图；未修改的原图保持不变。</p></el-form-item>
        <p v-if="editing">当前库存：{{sku.stock}} · {{sku.wmsManaged?'系统管理（不可手工修改）':'可在库存管理中调整'}}</p>
        <div class="columns"><el-form-item label="售价（元）" required><el-input-number :model-value="sku.price/100" :min="0.01" :max="10000000" :precision="2" @update:model-value="v=>sku.price=Math.round((v||0)*100)"/></el-form-item>
          <el-form-item label="市场价（元）"><el-input-number :model-value="sku.marketPrice/100" :min="0" :max="10000000" :precision="2" @update:model-value="v=>sku.marketPrice=Math.round((v||0)*100)"/></el-form-item>
          <el-form-item v-if="!editing" label="本地初始库存"><el-input-number v-model="sku.initialStock" :min="0" :max="1000000" :precision="0"/></el-form-item></div>
        <div class="columns"><el-form-item label="单件重量（kg）"><el-input-number v-model="sku.weight" :min="0" :max="1000000" :precision="4"/></el-form-item>
          <el-form-item label="单件体积（m³）"><el-input-number v-model="sku.volume" :min="0" :max="1000000" :precision="6"/></el-form-item>
          <el-form-item label="SKU条码"><el-input v-model="sku.barCode" :disabled="editing" maxlength="64"/></el-form-item></div>
      </div>
      <el-button v-if="!editing&&form.specType" :disabled="form.skus.length>=100" @click="addSku">添加SKU</el-button>
      <el-button v-if="editable" type="primary" :loading="busy" :disabled="anyUploading" @click="save">{{editing?'保存修改为草稿':'保存商品草稿'}}</el-button>
    </el-form>
  </section>
</template>
<style scoped>
.sku-heading{display:flex;align-items:center;justify-content:space-between;gap:16px}.sku-help{font-size:13px}.supplier-edit{padding:32px;margin:32px auto;max-width:1100px}h1{margin:20px 0}form{margin-top:24px}.columns{display:flex;gap:24px}.columns>*{flex:1}.sku{padding:20px;background:#f4f8f7;border:1px solid #dbe6e2;margin:24px 0}a{color:#167664}p{color:#64748b;margin:14px 0}.el-select{width:100%}.review-summary{display:flex;align-items:center;gap:12px;flex-wrap:wrap;margin-bottom:16px}.el-alert{margin:12px 0}.current-description{border:1px solid #dbe6e2;padding:18px;margin-top:20px}.safe-description{max-height:360px;overflow:auto;overflow-wrap:anywhere}.safe-description :deep(img){max-width:100%;height:auto}
</style>
