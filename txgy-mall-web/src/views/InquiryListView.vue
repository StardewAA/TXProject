<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useAccountMessageBox } from '@/composables/useAccountOperation'
const ElMessageBox = useAccountMessageBox()
import { inquiryApi, type Inquiry } from '@/api/inquiry'
const rows=ref<Inquiry[]>([]),total=ref(0),page=ref(1),status=ref<number>(),keyword=ref(''),busy=ref(false)
const closing=ref<number>(),loadFailed=ref(false)
let version=0,alive=true
const labels=['草稿','已提交','已截止','已关闭']
async function load(){
 const current=++version;busy.value=true;loadFailed.value=false
 try{
  const data=await inquiryApi.page({pageNo:page.value,pageSize:10,status:typeof status.value==='number'?status.value:undefined,keyword:keyword.value.trim()})
  if(!alive||current!==version)return
  const last=Math.max(1,Math.ceil(data.total/10))
  if(page.value>last){page.value=last;await load();return}
  rows.value=data.list;total.value=data.total
 }catch{
  if(alive&&current===version){rows.value=[];total.value=0;loadFailed.value=true}
 }finally{if(alive&&current===version)busy.value=false}
}
function search(){if(busy.value||closing.value!==undefined)return;page.value=1;void load()}
async function close(row:{id?:number;status?:number}){
 if(busy.value||closing.value!==undefined||row.status===3||!row.id)return
 const id=row.id;closing.value=id
 try{
  const confirmed=await ElMessageBox.confirm('关闭后不能再修改或提交，确认关闭？','关闭询价单',{confirmButtonText:'确认关闭',cancelButtonText:'取消'}).then(()=>true,()=>false)
  if(!confirmed||!alive)return
  try{await inquiryApi.close(id)}
  catch{if(alive)ElMessage.warning('关闭结果未确认，请刷新列表核对后再操作');return}
  if(!alive)return
  ElMessage.success('已关闭');await load()
 }finally{if(alive)closing.value=undefined}
}
onBeforeUnmount(()=>{alive=false;version++})
onMounted(load)
</script>
<template><div class="container panel inquiry-list"><header><h1>管理询价单</h1><MallLink to="/purchase/inquiries/new">发布询价单</MallLink><MallLink to="/purchase">采购中心</MallLink></header><p>仅显示本人创建的采购需求，可在此查看提交状态和后续进度。</p><div class="filters"><el-input :disabled="busy||closing!==undefined" @keyup.enter="search" v-model="keyword" placeholder="按标题搜索" clearable/><el-select :disabled="busy||closing!==undefined" v-model="status" placeholder="全部状态" clearable><el-option v-for="(label,i) in labels" :key="i" :label="label" :value="i"/></el-select><el-button :disabled="busy||closing!==undefined" @click="search">查询</el-button><el-button :disabled="busy||closing!==undefined" @click="load">刷新列表</el-button></div><p v-if="loadFailed" role="alert">列表加载失败，请点击刷新列表重试；这不代表没有询价单。</p><el-table :data="rows" v-loading="busy"><el-table-column prop="no" label="询价单号" min-width="200"><template #default="{row}">{{row.no||'草稿（未生成单号）'}}</template></el-table-column><el-table-column prop="title" label="标题"/><el-table-column label="状态"><template #default="{row}">{{labels[row.status]}}</template></el-table-column><el-table-column label="截止时间"><template #default="{row}">{{new Date(row.deadline).toLocaleString()}}</template></el-table-column><el-table-column label="操作" width="180"><template #default="{row}"><MallLink :to="'/purchase/inquiries/'+row.id">{{row.status===0?'编辑 / 查看':'查看详情'}}</MallLink><el-button v-if="row.status!==3" link type="danger" :disabled="busy||closing!==undefined" :loading="closing===row.id" @click="close(row)">关闭</el-button></template></el-table-column></el-table><el-pagination :disabled="busy||closing!==undefined" v-model:current-page="page" :page-size="10" :total="total" layout="prev,pager,next,total" @current-change="load"/></div></template>
<style scoped>.inquiry-list{padding:32px;margin:32px auto;min-height:550px}header,.filters{display:flex;align-items:center;gap:24px}header h1{flex:1}p{color:#64748b}.filters{margin:24px 0}.filters .el-input{width:300px}.filters .el-select{width:160px}.el-pagination{margin-top:24px}a{color:#167664}</style>
