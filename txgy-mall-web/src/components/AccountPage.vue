<script setup lang="ts">
import { onScopeDispose, ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import { getTenantContext, isCurrentTenantEpoch } from '@/utils/tenant-context'

// Mounted only for authenticated routes. Never remount the login form during its own login request.
const epoch = getTenantContext().epoch
const expired = ref(false)
function check() {
  if (isCurrentTenantEpoch(epoch)) return
  expired.value = true
  ElMessageBox.close()
}
window.addEventListener('mall:tenant-context-updated', check)
onScopeDispose(() => window.removeEventListener('mall:tenant-context-updated', check))
function reload() { window.location.reload() }
</script>

<template>
  <section v-if="expired" class="account-page-expired container panel" role="alert">
    <h2>登录账号已变化</h2>
    <p>为防止将原账号的资料提交到新账号，本页已停止操作并清除旧表单。请重新加载后继续。</p>
    <el-button type="primary" @click="reload">重新加载当前页面</el-button>
    <MallLink to="/">返回商城首页</MallLink>
  </section>
  <slot v-else />
</template>

<style scoped>
.account-page-expired { margin: 32px auto; padding: 32px; }
.account-page-expired p { margin: 20px 0; }
.account-page-expired a { margin-left: 20px; }
</style>
