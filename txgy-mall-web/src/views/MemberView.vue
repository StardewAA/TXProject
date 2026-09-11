<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'

import { infraApi, memberApi } from '@/api/mall'
import MemberPage from '@/components/MemberPage.vue'
import { useAuthStore } from '@/stores/auth'
import { normalizeImage } from '@/utils/format'

const authStore = useAuthStore()
const editing = ref(false)
const saving = ref(false)
const uploading = ref(false)
const fileInput = ref<HTMLInputElement | null>(null)
const form = reactive({ nickname: authStore.profile?.nickname || '', avatar: authStore.profile?.avatar || '' })

function openEditor() {
  form.nickname = authStore.profile?.nickname || ''
  form.avatar = authStore.profile?.avatar || ''
  editing.value = true
}

async function selectAvatar(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  if (!file.type.startsWith('image/')) return ElMessage.warning('请选择图片文件')
  if (file.size > 5 * 1024 * 1024) return ElMessage.warning('头像图片不能超过 5 MB')
  uploading.value = true
  try {
    form.avatar = await infraApi.uploadAvatar(file)
    ElMessage.success('头像上传成功，请点击保存')
  } finally { uploading.value = false }
}

async function save() {
  saving.value = true
  try {
    await memberApi.updateProfile(form)
    await authStore.loadProfile()
    editing.value = false
    ElMessage.success('资料已更新，移动端刷新后同步显示')
  } finally { saving.value = false }
}
</script>

<template>
  <MemberPage title="账户概览" description="PC 与移动端共用同一个商城会员账户">
    <div class="profile-card">
      <el-avatar :size="88" :src="authStore.profile?.avatar">{{ (authStore.profile?.nickname || '用户').slice(0, 1) }}</el-avatar>
      <div><h2>{{ authStore.profile?.nickname || '商城用户' }}</h2><p>手机号：{{ authStore.profile?.mobile || '-' }}</p><p>会员编号：{{ authStore.profile?.id || '-' }}</p></div>
      <el-button @click="openEditor">编辑资料</el-button>
    </div>
    <div class="quick-grid">
      <RouterLink to="/member/orders"><b>订单中心</b><span>查看全部订单与物流状态</span></RouterLink>
      <RouterLink to="/member/addresses"><b>收货地址</b><span>维护跨端共用的地址簿</span></RouterLink>
      <RouterLink to="/member/favorites"><b>商品收藏</b><span>继续关注需要采购的材料</span></RouterLink>
      <RouterLink to="/member/after-sales"><b>退款售后</b><span>查询售后处理进度</span></RouterLink>
      <RouterLink to="/member/wallet"><b>我的钱包</b><span>查看余额、充值与资金流水</span></RouterLink>
      <RouterLink to="/member/customer-service"><b>在线客服</b><span>咨询商品、订单和售后问题</span></RouterLink>
    </div>
    <el-dialog v-model="editing" title="编辑会员资料" width="480px">
      <el-form label-position="top">
        <el-form-item label="昵称"><el-input v-model="form.nickname" maxlength="30" /></el-form-item>
        <el-form-item label="会员头像">
          <div class="avatar-editor">
            <el-avatar :size="78" :src="normalizeImage(form.avatar)">{{ (form.nickname || '用户').slice(0, 1) }}</el-avatar>
            <div><el-button :loading="uploading" @click="fileInput?.click()">选择图片</el-button><p>支持 JPG、PNG 等图片，大小不超过 5 MB</p></div>
            <input ref="fileInput" type="file" accept="image/*" hidden @change="selectAvatar" />
          </div>
        </el-form-item>
      </el-form>
      <template #footer><el-button @click="editing = false">取消</el-button><el-button type="primary" :loading="saving" :disabled="uploading" @click="save">保存</el-button></template>
    </el-dialog>
  </MemberPage>
</template>

<style scoped lang="scss">
.profile-card { display: grid; grid-template-columns: 88px 1fr auto; gap: 22px; align-items: center; padding: 28px; background: linear-gradient(110deg, #fff6f1, #f5f7f8); border-radius: 8px; }.profile-card h2 { margin: 0 0 10px; }.profile-card p { margin: 6px 0; color: var(--muted); }.quick-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 16px; margin-top: 22px; }.quick-grid a { min-height: 105px; padding: 24px; border: 1px solid var(--line); border-radius: 7px; }.quick-grid a:hover { border-color: var(--brand); background: #fffaf7; }.quick-grid b, .quick-grid span { display: block; }.quick-grid span { margin-top: 10px; color: var(--muted); font-size: 14px; }
.avatar-editor { display: flex; align-items: center; gap: 18px; }.avatar-editor p { margin: 8px 0 0; color: var(--muted); font-size: 13px; }
</style>
