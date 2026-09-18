<script setup lang="ts">
import { useAccountOperation } from '@/composables/useAccountOperation'
const operation = useAccountOperation()
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'

import { infraApi, memberApi } from '@/api/mall'
import MemberPage from '@/components/MemberPage.vue'
import OrderWorkbench from '@/components/OrderWorkbench.vue'
import CustomerAffiliation from '@/components/CustomerAffiliation.vue'
import { useAuthStore } from '@/stores/auth'
import { normalizeImage } from '@/utils/format'

const authStore = useAuthStore()
const editing = ref(false)
const saving = ref(false)
const uploading = ref(false)
const fileInput = ref<HTMLInputElement | null>(null)
const form = reactive({ nickname: authStore.profile?.nickname || '', avatar: authStore.profile?.avatar || '' })
const shortcuts = [
  { path: 'orders', title: '订单中心', text: '跟进采购订单与物流状态', icon: 'M5 3h14v18H5z M9 7h6 M9 12h6 M9 17h4', tone: 'green' },
  { path: 'addresses', title: '收货地址', text: '管理项目现场与常用收货地', icon: 'M19 10c0 5-7 11-7 11S5 15 5 10a7 7 0 0 1 14 0z M14 10a2 2 0 1 1-4 0 2 2 0 0 1 4 0', tone: 'blue' },
  { path: 'favorites', title: '商品收藏', text: '保存选中的材料，下次快速采购', icon: 'm12 3 3 6 6 1-4.5 4.5 1 6.5-5.5-3-5.5 3 1-6.5L3 10l6-1z', tone: 'gold' },
  { path: 'after-sales', title: '退款售后', text: '查看申请与售后处理进度', icon: 'M7 3 3 7l4 4 M3 7h12a7 7 0 0 1 0 14h-3', tone: 'blue' },
  { path: 'wallet', title: '我的钱包', text: '查看余额、充值与资金流水', icon: 'M3 5h18v15H3z M3 9h18 M16 13h5v4h-5z', tone: 'green' },
  { path: 'customer-service', title: '在线客服', text: '咨询规格、订单及售后问题', icon: 'M4 4h16v13H9l-5 4z M8 8h8 M8 12h5', tone: 'gold' },
]

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
    const avatar = await infraApi.uploadAvatar(file)
    if (!operation.current()) return
    form.avatar = avatar
    ElMessage.success('头像上传成功，请点击保存')
  } finally { uploading.value = false }
}

async function save() {
  saving.value = true
  try {
    await memberApi.updateProfile(form)
    if (!operation.current()) return
    await authStore.loadProfile()
    if (!operation.current()) return
    editing.value = false
    ElMessage.success('资料已更新，移动端刷新后同步显示')
  } finally { saving.value = false }
}
</script>

<template>
  <MemberPage title="采购工作台" description="管理采购、跟进订单，让工程备料更从容">
    <OrderWorkbench :navigation-blocked="editing" />
    <div class="profile-card">
      <el-avatar :size="88" :src="authStore.profile?.avatar">{{ (authStore.profile?.nickname || '用户').slice(0, 1) }}</el-avatar>
      <div class="profile-info"><small>WELCOME BACK / 欢迎回来</small><h2>{{ authStore.profile?.nickname || '商城用户' }}</h2><p>手机号：{{ authStore.profile?.mobile || '-' }} <span>会员编号：{{ authStore.profile?.id || '-' }}</span></p><div class="profile-tag">工程材料采购 · PC / 移动端账户互通</div></div>
      <el-button @click="openEditor">编辑资料</el-button>
    </div>
    <div class="workbench-heading"><h3>我的采购服务</h3><MallLink to="/goods">继续选购材料 ↗</MallLink></div>
    <CustomerAffiliation />
    <div class="quick-grid">
      <MallLink v-for="item in shortcuts" :key="item.path" :to="'/member/' + item.path" :class="item.tone"><div class="shortcut-top"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round" stroke-linecap="round" aria-hidden="true"><path :d="item.icon" /></svg><i>↗</i></div><b>{{ item.title }}</b><span>{{ item.text }}</span></MallLink>
    </div>
    <section class="purchase-guide"><div><small>PROCUREMENT GUIDE</small><h3>每一次采购，都心中有数</h3><p>从选材到收货，关键事项一步不漏。</p><MallLink to="/categories">浏览材料目录 →</MallLink></div><ol><li><b>01</b><div><strong>选材前 · 确认工况</strong><p>核对材质、使用温度与规格单位</p></div></li><li><b>02</b><div><strong>下单时 · 核对信息</strong><p>确认数量、收货地址与结算金额</p></div></li><li><b>03</b><div><strong>收货后 · 验货留存</strong><p>核对到货材料，有问题及时联系售后</p></div></li></ol></section>
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
<style scoped lang="scss">
.profile-card{position:relative;overflow:hidden;min-height:190px;padding:30px;background:radial-gradient(circle at 90% 10%,#378b7c55,transparent 40%),linear-gradient(115deg,#142f3c,#205b56);color:white;border-radius:14px}.profile-card::after{content:'';position:absolute;width:260px;height:260px;right:-100px;top:-150px;border:35px solid #ffffff08;border-radius:50%;pointer-events:none}.profile-card h2{margin:12px 0;font-size:26px}.profile-card p{color:#c1d5d8;font-size:13px}.profile-info small{letter-spacing:2px;color:#9bd1c6;font-size:11px}.profile-info p span{margin-left:18px}.profile-tag{display:inline-block;margin-top:12px;padding:6px 10px;background:#ffffff0d;border:1px solid #ffffff20;border-radius:5px;font-size:11px;color:#cfe6df}.profile-card :deep(.el-avatar){border:4px solid #ffffff25;box-shadow:0 5px 25px #071f3440}.profile-card :deep(.el-button){background:#ffffff12;color:white;border-color:#ffffff50}.workbench-heading{display:flex;justify-content:space-between;align-items:center;margin:26px 0 16px}.workbench-heading h3{margin:0;font-size:18px}.workbench-heading a{font-size:13px;color:#147d6b}.quick-grid{grid-template-columns:repeat(3,minmax(0,1fr));gap:16px;margin:0}.quick-grid a{padding:22px;min-height:155px;border-radius:12px;background:linear-gradient(140deg,#fff,#f6faf8);transition:transform .2s,box-shadow .2s}.quick-grid a:hover{background:#f1f8f5;transform:translateY(-3px);box-shadow:0 8px 22px #214a4010}.shortcut-top{display:flex;justify-content:space-between;align-items:center;margin-bottom:16px}.shortcut-top svg{width:42px;height:42px;padding:9px;box-sizing:border-box;background:#e0f1eb;color:#147d6b;border-radius:12px}.shortcut-top i{font-style:normal;color:#9aafb1}.blue .shortcut-top svg{color:#337799;background:#e5f0f6}.gold .shortcut-top svg{color:#a37d37;background:#f6efdF}.quick-grid span{font-size:12px;line-height:1.7;margin-top:7px}.purchase-guide{display:grid;grid-template-columns:1fr 1.2fr;gap:24px;margin-top:24px;padding:26px;border-radius:12px;background:#edf4f2}.purchase-guide small{font-size:10px;letter-spacing:2px;color:#599283}.purchase-guide h3{margin:12px 0;font-size:20px}.purchase-guide p{font-size:12px;color:#758a89;margin:6px 0;line-height:1.7}.purchase-guide a{display:inline-block;margin-top:16px;color:#147d6b;font-size:13px}.purchase-guide ol{padding:0;margin:0;list-style:none;display:grid;gap:14px}.purchase-guide li{display:flex;gap:14px;align-items:center}.purchase-guide li>b{color:#80aaa0;font-size:22px;font-weight:400}.purchase-guide strong{font-size:13px}@media(max-width:1150px){.quick-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.profile-info p span{display:block;margin:6px 0 0}}
</style>
