<script setup lang="ts">
import { useAccountOperation } from '@/composables/useAccountOperation'
const operation = useAccountOperation()
import { onBeforeUnmount, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { inquiryApi } from '@/api/inquiry'
import { normalizeImage } from '@/utils/format'
const props=defineProps<{modelValue?:string|null;disabled?:boolean}>()
const emit=defineEmits<{'update:modelValue':[string];busy:[boolean]}>()
const uploading=ref(false),input=ref<HTMLInputElement>();let alive=true
onBeforeUnmount(()=>{alive=false})
async function upload(event:Event){
 const target=event.target as HTMLInputElement,file=target.files?.[0];target.value=''
 if(!file||props.disabled||uploading.value)return
 if(!['image/png','image/jpeg','image/webp'].includes(file.type)||!file.size||file.size>2097152)return void ElMessage.warning('询价图片仅支持2MB以内PNG/JPG/WebP')
 uploading.value=true;emit('busy',true);const local=URL.createObjectURL(file)
 try{
  await new Promise<void>((resolve,reject)=>{const image=new Image();image.onload=()=>image.naturalWidth*image.naturalHeight<=25000000?resolve():reject(Error('图片像素过大'));image.onerror=()=>reject(Error('图片无法读取'));image.src=local})
  if(!operation.current())return
  const url=await inquiryApi.uploadImage(file)
  if(!operation.current())return
  if(!/^https?:\/\//i.test(url)||url.length>512)throw Error('返回图片地址无效')
  if(alive){emit('update:modelValue',url);ElMessage.success('询价图片已上传，请保存草稿')}
 }catch(e){if(alive&&operation.current())ElMessage.error(e instanceof Error?e.message:'图片上传失败，原图已保留')}
 finally{URL.revokeObjectURL(local);uploading.value=false;if(alive)emit('busy',false)}
}
</script>
<template><div class="inquiry-item-image">
 <a v-if="modelValue" :href="normalizeImage(modelValue)" target="_blank" rel="noopener noreferrer"><img :src="normalizeImage(modelValue)" alt="询价物品参考图"/></a><span v-else>暂无图片</span>
 <input ref="input" type="file" accept="image/png,image/jpeg,image/webp" hidden :disabled="disabled||uploading" @change="upload"/>
 <div v-if="!disabled||uploading"><el-button size="small" :disabled="disabled||uploading" @click="input?.click()">{{modelValue?'替换图片':'上传图片'}}</el-button><el-button v-if="modelValue" size="small" :disabled="disabled||uploading" @click="emit('update:modelValue','')">移除图片</el-button></div>
 <span v-if="uploading">上传中…</span>
</div></template>
<style scoped>.inquiry-item-image{display:grid;gap:6px;min-width:145px;font-size:12px;color:#64748b}.inquiry-item-image img{width:80px;height:80px;object-fit:contain;border:1px solid #e2e8f0}.inquiry-item-image .el-button+.el-button{margin-left:4px}</style>
