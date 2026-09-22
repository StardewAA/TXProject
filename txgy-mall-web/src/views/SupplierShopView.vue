<script setup lang="ts">
import { useAccountOperation } from '@/composables/useAccountOperation'
const operation = useAccountOperation()
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { supplierShopApi, type SupplierShop } from '@/api/supplier-shop'
import { normalizeImage } from '@/utils/format'
const route=useRoute(),data=ref<SupplierShop>(),busy=ref(false),saving=ref(false)
const uploading=ref(false),fileInput=ref<HTMLInputElement>()
async function uploadLogo(event:Event){
 const input=event.target as HTMLInputElement,file=input.files?.[0];input.value=''
 if(!file||!data.value||uploading.value||saving.value)return
 if(!['image/png','image/jpeg','image/webp'].includes(file.type)||!file.size||file.size>2*1024*1024)return void ElMessage.warning('请选择2MB以内的PNG、JPG或WebP图片')
 const current=data.value,currentVersion=version;uploading.value=true
 const localUrl=URL.createObjectURL(file)
 try{
  await new Promise<void>((resolve,reject)=>{const img=new Image();img.onload=()=>img.naturalWidth*img.naturalHeight<=25000000?resolve():reject(new Error('图片像素过大，请缩小后上传'));img.onerror=()=>reject(new Error('图片无法读取，请选择有效图片'));img.src=localUrl})
  if(!operation.current()||currentVersion!==version||data.value!==current)return
  const url=await supplierShopApi.uploadLogo(file)
  if(!operation.current())return
  if(currentVersion!==version||data.value!==current)return
  if(!/^https?:\/\//i.test(url)||url.length>512)throw new Error('上传返回的图片地址无效，请联系管理员检查文件存储配置')
  current.logoUrl=url;ElMessage.success('Logo上传成功，请保存店铺资料')
 }catch(error){if(operation.current())ElMessage.error(error instanceof Error?error.message:'Logo上传失败，原图片未更改')}
 finally{URL.revokeObjectURL(localUrl);uploading.value=false}
}
let version=0
async function load(){const v=++version;busy.value=true;data.value=undefined;try{const result=await supplierShopApi.get(Number(route.params.shopId));if(v!==version)return;result.returnAddress??={receiverName:'',receiverMobile:'',address:''};data.value=result}finally{if(v===version)busy.value=false}}
async function save(){
 if(!data.value||saving.value||uploading.value)return
 const a=data.value.returnAddress
 if(!data.value.name.trim()||!a?.receiverName.trim()||!a.address.trim()||!/[0-9]/.test(a.receiverMobile)||!/^[-0-9+() ]{5,32}$/.test(a.receiverMobile))return void ElMessage.warning('请填写店铺名称及完整有效的退货联系人、电话和地址')
 saving.value=true;try{await supplierShopApi.save(data.value);ElMessage.success('店铺资料已保存');await load()}finally{saving.value=false}
}
watch(()=>route.params.shopId,load,{immediate:true})
</script>
<template>
 <div class="container shop-settings"><header><MallLink to="/sales">销售中心 / 店铺设置</MallLink><small>STORE PROFILE</small><h1>店铺资料与退货地址</h1><p>统一维护前台展示资料和仅用于售后履约的私有退货信息。</p></header>
  <el-alert title="公开资料会展示在商城店铺；退货资料仅用于售后，不会公开展示。同意退货时保存地址快照，之后修改不会影响已有快照。" :closable="false"/>
  <div v-loading="busy"><el-form v-if="data" label-position="top" :disabled="saving">
   <h2>公开店铺资料</h2><el-form-item label="店铺名称（必填）"><el-input v-model="data.name" maxlength="64"/></el-form-item>
   <el-form-item label="店铺 Logo">
    <div class="logo-upload"><img v-if="data.logoUrl" class="logo-preview" :src="normalizeImage(data.logoUrl)" alt="店铺Logo预览"/>
     <input ref="fileInput" type="file" accept="image/png,image/jpeg,image/webp" hidden :disabled="saving||uploading" @change="uploadLogo"/>
     <el-button :loading="uploading" :disabled="saving" @click="fileInput?.click()">{{data.logoUrl?'更换图片':'上传图片'}}</el-button>
     <el-button v-if="data.logoUrl" :disabled="saving||uploading" @click="data.logoUrl=''">移除图片</el-button>
     <small>支持PNG、JPG、WebP，最大2MB。上传或移除后点击“保存店铺资料”生效。</small>
    </div>
   </el-form-item>
   <el-form-item label="公开联系人"><el-input v-model="data.contactName" maxlength="32"/></el-form-item><el-form-item label="公开联系电话"><el-input v-model="data.contactMobile" maxlength="32"/></el-form-item>
   <el-form-item label="店铺介绍"><el-input v-model="data.introduction" type="textarea" maxlength="1024" :rows="4"/></el-form-item>
   <template v-if="data.returnAddress"><h2>私有退货资料</h2><el-form-item label="退货收件人（必填）"><el-input v-model="data.returnAddress.receiverName" maxlength="32"/></el-form-item>
   <el-form-item label="退货联系电话（必填）"><el-input v-model="data.returnAddress.receiverMobile" maxlength="32"/></el-form-item>
   <el-form-item label="完整退货地址（省、市、区及详细门牌，必填）"><el-input v-model="data.returnAddress.address" type="textarea" maxlength="512" :rows="3"/></el-form-item></template>
   <el-button type="primary" :loading="saving" :disabled="uploading" @click="save">保存店铺资料</el-button>
  </el-form><el-empty v-else-if="!busy" description="无权访问或加载失败"><el-button @click="load">重试</el-button></el-empty></div>
 </div>
</template>
<style scoped>
.shop-settings{max-width:980px;margin:28px auto;padding:0 34px 34px;border:1px solid #dce5e8;border-radius:20px;background:white;overflow:hidden;box-shadow:0 16px 38px rgba(20,48,58,.08)}.shop-settings>header{margin:0 -34px 24px;padding:30px 36px;background:linear-gradient(118deg,#102f3a,#17685f);color:#fff}.shop-settings>header a{color:#95e1d3;font-size:13px;font-weight:700}.shop-settings>header small{display:block;margin:15px 0 6px;color:#77cbbb;font-size:11px;font-weight:800;letter-spacing:.2em}.shop-settings>header h1{margin:0;font-size:29px}.shop-settings>header p{margin:8px 0 0;color:#c8dcde}.shop-settings>:deep(.el-alert){border-radius:10px}.el-form{display:grid;grid-template-columns:1fr 1fr;gap:0 20px;margin-top:24px}.el-form h2{grid-column:1/-1;margin:16px 0;padding:12px 15px;border-left:4px solid #168173;border-radius:0 9px 9px 0;background:#f1f7f6;color:#1d3a43;font-size:18px}.el-form>.el-form-item:nth-of-type(2),.el-form>.el-form-item:nth-of-type(5),.el-form>.el-form-item:last-of-type{grid-column:1/-1}.el-form>:deep(.el-button--primary){grid-column:1/-1;justify-self:start;min-width:150px;min-height:42px;border-radius:9px;background:#167f71;border-color:#167f71;font-weight:700}.logo-upload{display:flex;align-items:center;gap:12px;flex-wrap:wrap}.logo-preview{width:104px;height:104px;padding:5px;object-fit:contain;border:1px solid #dce6e8;border-radius:12px;background:#fff}.logo-upload small{flex-basis:100%;color:#647b82}.shop-settings :deep(.el-input__wrapper),.shop-settings :deep(.el-textarea__inner){border-radius:9px;box-shadow:0 0 0 1px #d8e3e5 inset}@media(max-width:720px){.shop-settings{margin:16px;padding:0 18px 24px}.shop-settings>header{margin:0 -18px 20px}.el-form{grid-template-columns:1fr}}
</style>
