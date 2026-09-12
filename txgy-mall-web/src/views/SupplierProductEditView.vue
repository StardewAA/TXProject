<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { supplierApi, type SupplierProduct, type SupplierOptions, type SupplierSku } from '@/api/supplier'
import { enterpriseApi } from '@/api/enterprise'
import { productApi } from '@/api/mall'
import type { Category } from '@/types/mall'
import ProductImages from '@/components/ProductImages.vue'
import SupplierStock from '@/components/SupplierStock.vue'
import SupplierSkuImage from '@/components/SupplierSkuImage.vue'
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
 if(editing||busy.value||anyUploading.value)return
 if(form.skus.length<=1)return void ElMessage.warning('商品至少保留一个规格；可修改当前规格内容')
 const index=form.skus.indexOf(sku)
 if(index>=0)form.skus.splice(index,1)
}
function addSku(){if(form.skus.length>=100)return;form.skus.push({price:100,marketPrice:100,initialStock:0,barCode:'',weight:0,volume:0,properties:dimensions.value.map(propertyId=>({propertyId}))})}
async function changeSpecType(value:unknown){
 if(!value&&form.skus.length>1){form.specType=true;ElMessage.warning('请先删除多余SKU，再切换单规格');return}
 for(const sku of form.skus)sku.properties=value?dimensions.value.map(propertyId=>({propertyId})):undefined
}

const route = useRoute(), router = useRouter(), shopId = Number(route.params.shopId)
const editing = route.params.id !== 'new', busy = ref(false), loaded = ref(false), error = ref(false)
const categories = ref<Category[]>([]), options = ref<SupplierOptions>({ brands: [], templates: [] })
const form = reactive<SupplierProduct>({ shopId, name: '', keyword: '', introduction: '', descriptionText: '', picUrl: '',
  skus: [{ price: 100, marketPrice: 100, initialStock: 0, barCode: '', weight: 0, volume: 0 }] })
const leafCategories = computed(() => categories.value.filter(c => Number(c.parentId) !== 0 && !categories.value.some(child => String(child.parentId) === String(c.id))))
const materialPropertyNames=['材质','厚度','容重','公称直径','壁厚','包装规格','长度','宽度','颜色','规格','单位','重量']
const propertyGroups=computed(()=>[
 {label:'防腐保温常用规格',items:materialPropertyNames.flatMap(name=>(options.value.properties||[]).filter(p=>p.name===name))},
 {label:'其他已有属性',items:(options.value.properties||[]).filter(p=>!materialPropertyNames.includes(p.name))},
].filter(group=>group.items.length))
const template = computed(() => options.value.templates.find(t => t.id === form.deliveryTemplateId))
async function load() {
  busy.value = true; error.value = false; loaded.value = false
  try {
    const shops = await enterpriseApi.salesShops()
    if (!shops.some(s => Number(s.id) === shopId)) { await router.replace('/sales'); return }
    const [choices, cats] = await Promise.all([supplierApi.options(shopId), productApi.getCategories()])
    options.value = choices; categories.value = cats
    if (editing) {
      const data = await supplierApi.detail(shopId, Number(route.params.id))
      Object.assign(form, data, { descriptionText: '' })
      form.sliderPicUrls=Array.from(new Set([data.picUrl,...(data.sliderPicUrls||[])].filter(Boolean)))
      form.skus = data.skus.map(s => ({ ...s, weight: s.weight || 0, volume: s.volume || 0, marketPrice: s.marketPrice || 0 }))
      originalSkuImages.clear();for(const sku of data.skus)if(sku.id)originalSkuImages.set(sku.id,sku.picUrl)
    }
    loaded.value = true
  } catch { error.value = true }
  finally { busy.value = false }
}
async function save() {
  if (!loaded.value || busy.value || anyUploading.value) return
  if (!form.name.trim() || !form.keyword.trim() || !form.introduction.trim() || !form.picUrl.trim()
      || !form.categoryId || !form.brandId || !form.deliveryTemplateId || (!editing && !form.descriptionText?.trim()))
    return void ElMessage.warning('请完整填写商品、分类、品牌、图片、详情和运费模板')
  if (form.skus.some(s => s.price <= 0 || (template.value?.chargeMode === 2 && s.weight <= 0) || (template.value?.chargeMode === 3 && s.volume <= 0)))
    return void ElMessage.warning('售价须大于零；按重量/体积计费须填写对应正数值')
  if(!editing&&form.specType){
    if(!dimensions.value.length||form.skus.some(s=>s.properties?.length!==dimensions.value.length||s.properties.some(p=>!p.valueId)))return void ElMessage.warning('请选择规格属性，并为每个SKU选择完整规格值')
    const keys=form.skus.map(s=>[...(s.properties||[])].sort((a,b)=>a.propertyId-b.propertyId).map(p=>`${p.propertyId}:${p.valueId}`).join(','))
    if(new Set(keys).size!==keys.length)return void ElMessage.warning('规格组合重复，请修改')
  }
  busy.value = true
  try {
    await supplierApi.save({ ...form, skus: form.skus.map(s => ({ id: s.id, price: s.price, marketPrice: s.marketPrice,
      picUrl:!editing||s.picUrl!==originalSkuImages.get(s.id!)?s.picUrl:undefined,
      weight: s.weight, volume: s.volume, barCode: s.barCode, initialStock: editing ? undefined : s.initialStock,properties:!editing&&form.specType?s.properties:undefined })) })
    ElMessage.success(editing ? '商品已更新，库存和WMS映射未改动' : '商品已保存为下架，请核对后上架')
    await router.push(`/sales/shops/${shopId}/products`)
  } finally { busy.value = false }
}
onMounted(load)
</script>
<template>
  <section class="container panel supplier-edit" v-loading="busy">
    <RouterLink :to="`/sales/shops/${shopId}/products`">← 返回商品管理</RouterLink>
    <h1>{{editing?'编辑商品':'发布商品'}}</h1>
    <SupplierStock v-if="editing&&loaded&&form.id" :shop-id="shopId" :spu-id="form.id" :disabled="busy||anyUploading" @updated="skus=>{for(const sku of form.skus){const fresh=skus.find(s=>s.id===sku.id);if(fresh){sku.stock=fresh.stock;sku.wmsManaged=fresh.wmsManaged}}}"/>
    <el-alert title="新建支持单规格或多规格（最多100个SKU、3个属性）。请选择实际销售的规格组合，为每种规格填写售价和初始库存。" :closable="false"/>
    <el-alert v-if="error" title="读取商品或运费配置失败" type="error" :closable="false"><el-button @click="load">重试</el-button></el-alert>
    <el-form v-if="loaded" label-position="top" :disabled="busy||anyUploading" @submit.prevent="save">
      <el-form-item label="商品名称" required><el-input v-model="form.name" maxlength="100"/></el-form-item>
      <div class="columns"><el-form-item label="商品分类" required><el-select v-model="form.categoryId" filterable><el-option v-for="c in leafCategories" :key="c.id" :value="Number(c.id)" :label="c.name"/></el-select></el-form-item>
        <el-form-item label="品牌" required><el-select v-model="form.brandId" filterable><el-option v-for="b in options.brands" :key="b.id" :value="b.id" :label="b.name"/></el-select></el-form-item></div>
      <el-form-item label="搜索关键词" required><el-input v-model="form.keyword" maxlength="255"/></el-form-item>
      <el-form-item label="商品简介" required><el-input v-model="form.introduction" maxlength="255"/></el-form-item>
      <el-form-item label="商品图片" required><ProductImages :model-value="form.sliderPicUrls||[]" :disabled="busy||anyUploading" @busy="v=>uploadingImages=v" @update:model-value="images=>{form.sliderPicUrls=images;form.picUrl=images[0]||''}"/></el-form-item>
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
        <el-form-item label="SKU独立图片（选填）"><SupplierSkuImage v-model="sku.picUrl" :disabled="busy||anyUploading" @busy="v=>uploadingSkuImages+=v?1:-1"/><p>每个SKU一张，PNG/JPG/WebP、2MB以内。点击“使用当前主图”会在保存时复制当前商品主图；未修改的原图保持不变。</p></el-form-item>
        <p v-if="editing">库存缓存：{{sku.stock}} · {{sku.wmsManaged?'WMS管理（此处不修改）':'本地库存（此处不覆盖）'}}</p>
        <div class="columns"><el-form-item label="售价（元）" required><el-input-number :model-value="sku.price/100" :min="0.01" :max="10000000" :precision="2" @update:model-value="v=>sku.price=Math.round((v||0)*100)"/></el-form-item>
          <el-form-item label="市场价（元）"><el-input-number :model-value="sku.marketPrice/100" :min="0" :max="10000000" :precision="2" @update:model-value="v=>sku.marketPrice=Math.round((v||0)*100)"/></el-form-item>
          <el-form-item v-if="!editing" label="本地初始库存"><el-input-number v-model="sku.initialStock" :min="0" :max="1000000" :precision="0"/></el-form-item></div>
        <div class="columns"><el-form-item label="单件重量（kg）"><el-input-number v-model="sku.weight" :min="0" :max="1000000" :precision="4"/></el-form-item>
          <el-form-item label="单件体积（m³）"><el-input-number v-model="sku.volume" :min="0" :max="1000000" :precision="6"/></el-form-item>
          <el-form-item label="SKU条码"><el-input v-model="sku.barCode" :disabled="editing" maxlength="64"/></el-form-item></div>
      </div>
      <el-button v-if="!editing&&form.specType" :disabled="form.skus.length>=100" @click="addSku">添加SKU</el-button>
      <el-button type="primary" :loading="busy" :disabled="anyUploading" @click="save">{{editing?'保存修改':'保存商品（暂不上架）'}}</el-button>
    </el-form>
  </section>
</template>
<style scoped>
.sku-heading{display:flex;align-items:center;justify-content:space-between;gap:16px}.sku-help{font-size:13px}.supplier-edit{padding:32px;margin:32px auto;max-width:1100px}h1{margin:20px 0}form{margin-top:24px}.columns{display:flex;gap:24px}.columns>*{flex:1}.sku{padding:20px;background:#f4f8f7;border:1px solid #dbe6e2;margin:24px 0}a{color:#167664}p{color:#64748b;margin:14px 0}.el-select{width:100%}
</style>
