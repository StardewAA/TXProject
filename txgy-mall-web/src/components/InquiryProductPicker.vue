<script setup lang="ts">
import {ref,watch} from 'vue'
import {ElMessage} from 'element-plus'
import {productApi} from '@/api/mall'
import type {Product,Category} from '@/types/mall'
import type {InquiryItem} from '@/api/inquiry'
import { inquiryProductItem, inquirySpecification as spec } from '@/utils/inquiry-product'
const props=defineProps<{disabled?:boolean}>()
const page=ref(1),total=ref(0);let run=0,opening=0
const emit=defineEmits<{select:[InquiryItem]}>()
const open=ref(false),keyword=ref(''),rows=ref<Product[]>([]),categories=ref<Category[]>([]),selected=ref<Product>(),skuId=ref<string>(),unit=ref(''),busy=ref(false)
watch(open,value=>{if(!value){run++;opening++;busy.value=false;selected.value=undefined;skuId.value=undefined}})
async function search(target=1){if(busy.value||props.disabled)return;const current=++run;busy.value=true;selected.value=undefined;skuId.value=undefined;unit.value='';try{const data=await productApi.getPage({pageNo:target,pageSize:20,keyword:keyword.value.trim()});if(current===run&&open.value){rows.value=data.list;total.value=data.total;page.value=target}}finally{if(current===run)busy.value=false}}
async function show(){if(props.disabled)return;open.value=true;selected.value=undefined;skuId.value=undefined;unit.value='';rows.value=[];page.value=1;total.value=0;categories.value=[];const current=++opening;void productApi.getCategories().then(data=>{if(current===opening&&open.value)categories.value=data}).catch(()=>{});await search()}
async function choose(id:string|number){if(busy.value||props.disabled)return;const current=++run;busy.value=true;selected.value=undefined;skuId.value=undefined;unit.value='';try{const data=await productApi.getDetail(id);if(current!==run||!open.value)return;selected.value=data;skuId.value=data.skus?.length===1?String(data.skus[0]!.id):undefined}finally{if(current===run)busy.value=false}}
function add(){if(busy.value||props.disabled||!selected.value)return;try{emit('select',inquiryProductItem(selected.value,skuId.value||'',unit.value,categories.value));open.value=false}catch(e){ElMessage.warning((e as Error).message)}}
</script>
<template><el-button :disabled="disabled" @click="show">选择商城商品</el-button><el-dialog v-model="open" title="选择采购商品及规格" width="760px"><div style="display:flex;gap:16px"><el-input :disabled="busy||disabled" v-model="keyword" placeholder="商品名称" @keyup.enter="search(1)"/><el-button :disabled="busy||disabled" @click="search(1)">搜索</el-button></div><el-table :data="rows" v-loading="busy" max-height="300"><el-table-column prop="name" label="商品名称"/><el-table-column label="操作" width="100"><template #default="{row}"><el-button link :disabled="busy||disabled" @click="choose(row.id)">选择</el-button></template></el-table-column></el-table><div class="picker-pages"><span>共 {{total}} 件 · 第 {{page}} 页</span><el-button :disabled="busy||disabled||page<=1" @click="search(page-1)">上一页</el-button><el-button :disabled="busy||disabled||page*20>=total" @click="search(page+1)">下一页</el-button></div><div v-if="selected" style="margin-top:20px"><h3>{{selected.name}}</h3><el-select :disabled="busy||disabled" v-model="skuId" placeholder="选择具体规格" style="width:100%"><el-option v-for="sku in selected.skus" :key="sku.id" :value="String(sku.id)" :label="spec(sku)"/></el-select><p v-if="skuId">已选规格：{{spec(selected.skus!.find(s=>String(s.id)===skuId)!)}}；询价不占用库存</p><el-input :disabled="busy||disabled" v-model="unit" maxlength="20" placeholder="采购单位（需自行核对，例如米、吨、桶）" style="margin-top:12px"/></div><template #footer><el-button type="primary" :disabled="busy||disabled||!selected||!skuId||!unit.trim()" @click="add">加入询价明细</el-button></template></el-dialog></template>
<style scoped>.picker-pages{display:flex;gap:12px;justify-content:flex-end;align-items:center;margin-top:12px}</style>
