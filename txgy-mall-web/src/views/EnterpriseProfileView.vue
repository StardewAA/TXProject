<script setup lang="ts">
import { useAccountOperation } from '@/composables/useAccountOperation'
const operation = useAccountOperation()
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { enterpriseApi, type EnterpriseApplication } from '@/api/enterprise'
import { useAuthStore } from '@/stores/auth'
const auth = useAuthStore()
const loaded = ref(false), busy = ref(false)
const form = reactive<EnterpriseApplication>({name:'',creditCode:'',licenseData:'',contactName:'',contactMobile:'',address:'',introduction:''})
const editable = computed(() => loaded.value && (form.status === undefined || form.status === 2) && !!auth.profile?.memberIdentity)
async function load() { loaded.value=false; try { await auth.loadProfile(); const row=await enterpriseApi.mine(); Object.assign(form,{id:undefined,status:undefined,enterpriseId:undefined,auditRemark:undefined,name:'',creditCode:'',licenseData:'',contactName:'',contactMobile:'',address:'',introduction:''}); if(row) Object.assign(form,row); loaded.value=true } catch { /* 请求层显示错误；不放行提交 */ } }
async function license(event: Event) {
  const file=(event.target as HTMLInputElement).files?.[0]
  if(!file) return
  if(!['image/png','image/jpeg'].includes(file.type) || file.size>1048576) return void ElMessage.warning('请选择1MB以内PNG或JPEG营业执照')
  const reader=new FileReader(); reader.onload=()=>{if(operation.current())form.licenseData=String(reader.result)}; reader.readAsDataURL(file)
}
async function submit() {
  if(!editable.value || busy.value) return
  if(!form.name.trim() || !/^[0-9A-Z]{18}$/.test(form.creditCode) || !form.licenseData || !form.contactName.trim() || !/^1\d{10}$/.test(form.contactMobile) || !form.address.trim()) return void ElMessage.warning('请完整填写企业名称、信用代码、营业执照及联系地址信息')
  busy.value=true
  try { await enterpriseApi.apply(form); ElMessage.success('已提交后台审核，审核通过后成为企业采购人员'); await load() } finally { busy.value=false }
}
onMounted(load)
</script>
<template>
  <div class="container enterprise-profile panel">
    <header><div><small>企业会员认证</small><h1>完善企业信息</h1></div><el-button @click="load">刷新审核状态</el-button></header>
    <p>账号注册 → 完善企业资料 → 后台审核 → 企业资质认证成功</p>
    <el-alert v-if="!loaded" title="正在读取申请信息；若加载失败请点击刷新" :closable="false" />
    <el-alert v-else-if="!auth.profile?.memberIdentity" title="当前为原有商城账号，企业身份开通请联系平台，原有购物功能不受影响" type="info" :closable="false" />
    <el-alert v-else-if="form.status === 0" title="企业资料已提交，等待后台审核，尚未认证成功" type="warning" :closable="false" />
    <el-alert v-else-if="form.status === 1" title="企业注册、资质认证成功，该账号已成为企业采购人员" type="success" :closable="false" />
    <el-alert v-else-if="form.status === 2" :title="'申请被驳回：'+form.auditRemark" type="error" :closable="false" />
    <el-form label-position="top" :disabled="!editable || busy" @submit.prevent="submit">
      <div class="fields"><el-form-item label="企业名称" required><el-input v-model="form.name" maxlength="100" /></el-form-item><el-form-item label="统一社会信用代码" required><el-input v-model="form.creditCode" maxlength="18" /></el-form-item></div>
      <el-form-item label="营业执照（PNG/JPEG，最大1MB，仅本人及授权审核人员可见）" required><input type="file" accept="image/png,image/jpeg" :disabled="!editable || busy" @change="license" /><img v-if="form.licenseData" :src="form.licenseData" class="license" alt="营业执照预览" /></el-form-item>
      <div class="fields"><el-form-item label="联系人" required><el-input v-model="form.contactName" maxlength="32" /></el-form-item><el-form-item label="联系电话" required><el-input v-model="form.contactMobile" maxlength="11" /></el-form-item></div>
      <el-form-item label="企业地址" required><el-input v-model="form.address" maxlength="255" /></el-form-item><el-form-item label="企业介绍"><el-input v-model="form.introduction" type="textarea" maxlength="1024" :rows="4" /></el-form-item>
      <el-button v-if="editable" type="primary" :loading="busy" @click="submit">{{ form.status===2?'修改并重新提交':'提交后台审核' }}</el-button>
    </el-form>
    <nav><MallLink to="/purchase">采购中心</MallLink><MallLink v-if="auth.profile?.memberIdentity==='supplier'" to="/sales">销售中心</MallLink><MallLink to="/member">普通会员中心</MallLink></nav>
    <p class="note">平台审核不代表外部工商认证；供应商还须关联店铺才能经营。真实 ERP 等系统对接暂未启用。</p>
  </div>
</template>
<style scoped>
.enterprise-profile{margin:32px auto;padding:32px;max-width:960px}header{display:flex;align-items:center;justify-content:space-between}h1{margin:8px 0}small{color:#167664}p{color:#64748b;line-height:1.8}.fields{display:grid;grid-template-columns:1fr 1fr;gap:24px}form{margin-top:24px}.license{display:block;width:180px;max-height:240px;object-fit:contain;margin:16px}nav{display:flex;gap:24px;margin-top:24px;color:#167664}.note{font-size:12px}
</style>
