<script setup lang="ts">
import { useAccountOperation } from '@/composables/useAccountOperation'
const operation = useAccountOperation()
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { supplierApi } from '@/api/supplier'
import { normalizeImage } from '@/utils/format'
const props=defineProps<{modelValue:string[];disabled?:boolean}>()
const emit=defineEmits<{ 'update:modelValue':[string[]]; busy:[boolean] }>()
const uploading=ref(false),input=ref<HTMLInputElement>()
function remove(index:number){emit('update:modelValue',props.modelValue.filter((_,i)=>i!==index))}
function move(index:number){const images=[...props.modelValue];[images[index-1],images[index]]=[images[index]!,images[index-1]!];emit('update:modelValue',images)}
async function upload(event:Event){
 const target=event.target as HTMLInputElement,files=Array.from(target.files||[]);target.value=''
 if(!files.length||uploading.value||props.disabled)return
 if(files.length+props.modelValue.length>10)return void ElMessage.warning('商品最多10张图片，请先删除多余图片')
 uploading.value=true;emit('busy',true)
 try{for(const file of files){
  if(!['image/png','image/jpeg','image/webp'].includes(file.type)||!file.size||file.size>2*1024*1024){ElMessage.warning(`${file.name}：仅支持2MB以内PNG/JPG/WebP`);continue}
  const local=URL.createObjectURL(file)
  try{
   await new Promise<void>((resolve,reject)=>{const image=new Image();image.onload=()=>image.naturalWidth*image.naturalHeight<=25000000?resolve():reject(Error('图片像素过大'));image.onerror=()=>reject(Error('图片无法读取'));image.src=local})
  if(!operation.current())return
  const url=await supplierApi.uploadImage(file)
  if(!operation.current())return
   if(!/^https?:\/\//i.test(url)||url.length>512)throw Error('图片存储地址无效')
   if(!props.modelValue.includes(url))emit('update:modelValue',[...props.modelValue,url])
  }catch(e){if(operation.current())ElMessage.error(`${file.name}：${e instanceof Error?e.message:'上传失败'}`)}finally{URL.revokeObjectURL(local)}
 }}finally{uploading.value=false;if(operation.current())emit('busy',false)}
}
</script>
<template>
 <div class="product-images"><div class="image-list"><div v-for="(url,index) in modelValue" :key="url" class="image-item"><img :src="normalizeImage(url)" :alt="index===0?'商品主图':'商品轮播图'"/><span>{{index===0?'主图':`轮播图 ${index+1}`}}</span><div><el-button v-if="index>0" size="small" :disabled="disabled||uploading" @click="move(index)">前移</el-button><el-button size="small" :disabled="disabled||uploading" @click="remove(index)">删除</el-button></div></div></div>
 <input ref="input" type="file" accept="image/png,image/jpeg,image/webp" multiple hidden @change="upload"/>
 <el-button :loading="uploading" :disabled="disabled||modelValue.length>=10" @click="input?.click()">上传商品图片</el-button><p>最多10张，每张2MB以内PNG/JPG/WebP；第一张为主图。调整顺序或删除后需保存商品才生效。</p></div>
</template>
<style scoped>.image-list{display:flex;gap:16px;flex-wrap:wrap;margin-bottom:16px}.image-item{display:flex;flex-direction:column;gap:8px;align-items:center;border:1px solid #ddd;padding:12px}.image-item img{width:128px;height:128px;object-fit:contain}p{color:#64748b;font-size:13px}</style>
