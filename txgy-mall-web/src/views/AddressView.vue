<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import { memberApi, systemApi, type AreaNode } from '@/api/mall'
import EmptyState from '@/components/EmptyState.vue'
import MemberPage from '@/components/MemberPage.vue'
import type { Address } from '@/types/mall'

const addresses = ref<Address[]>([])
const areaTree = ref<AreaNode[]>([])
const loading = ref(true)
const dialog = ref(false)
const saving = ref(false)
const form = reactive<Address>({ id: 0, name: '', mobile: '', areaId: undefined, detailAddress: '', defaultStatus: false })

async function load() {
  loading.value = true
  try { addresses.value = await memberApi.getAddresses() } finally { loading.value = false }
}

function open(item?: Address) {
  Object.assign(form, item || { id: 0, name: '', mobile: '', areaId: undefined, detailAddress: '', defaultStatus: false })
  dialog.value = true
}

async function save() {
  if (!form.name || !/^1\d{10}$/.test(form.mobile) || !form.areaId || !form.detailAddress) return ElMessage.warning('请完整填写收货地址')
  saving.value = true
  try {
    if (form.id) await memberApi.updateAddress(form)
    else await memberApi.createAddress({ name: form.name, mobile: form.mobile, areaId: form.areaId, detailAddress: form.detailAddress, defaultStatus: Boolean(form.defaultStatus) })
    ElMessage.success('地址已保存，移动端刷新后同步显示')
    dialog.value = false
    await load()
  } finally { saving.value = false }
}

async function remove(id: string | number) {
  await ElMessageBox.confirm('确定删除这个收货地址吗？', '删除地址', { type: 'warning', confirmButtonText: '确定删除', cancelButtonText: '取消' })
  await memberApi.deleteAddress(id)
  await load()
}

onMounted(async () => {
  const [, areas] = await Promise.allSettled([load(), systemApi.getAreaTree()])
  if (areas.status === 'fulfilled') areaTree.value = areas.value
})
</script>

<template>
  <MemberPage title="收货地址" description="此地址簿与移动端共用">
    <template #actions><el-button type="primary" @click="open()">新增地址</el-button></template>
    <div v-if="loading" class="loading-box"><el-skeleton :rows="6" animated /></div>
    <EmptyState v-else-if="!addresses.length" title="暂无收货地址" description="新增后即可在 PC 与移动端结算时使用" />
    <div v-else class="address-cards"><article v-for="item in addresses" :key="item.id"><header><strong>{{ item.name }}</strong><span>{{ item.mobile }}</span><i v-if="item.defaultStatus">默认</i></header><p>{{ item.areaName }} {{ item.detailAddress }}</p><footer><button @click="open(item)">编辑</button><button @click="remove(item.id)">删除</button></footer></article></div>
    <el-dialog v-model="dialog" :title="form.id ? '编辑地址' : '新增地址'" width="560px">
      <el-form label-position="top"><div class="form-row"><el-form-item label="收货人"><el-input v-model="form.name" /></el-form-item><el-form-item label="手机号"><el-input v-model="form.mobile" maxlength="11" /></el-form-item></div><el-form-item label="省/市/区"><el-cascader v-model="form.areaId" :options="areaTree" :props="{ value: 'id', label: 'name', emitPath: false }" filterable clearable /></el-form-item><el-form-item label="详细地址"><el-input v-model="form.detailAddress" type="textarea" maxlength="120" /></el-form-item><el-checkbox v-model="form.defaultStatus">设为默认地址</el-checkbox></el-form>
      <template #footer><el-button @click="dialog = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
    </el-dialog>
  </MemberPage>
</template>

<style scoped lang="scss">
.address-cards { display: grid; grid-template-columns: repeat(2, 1fr); gap: 16px; }.address-cards article { padding: 22px; border: 1px solid var(--line); border-radius: 7px; }.address-cards header { display: flex; gap: 18px; align-items: center; }.address-cards i { padding: 3px 8px; background: #fff0e8; color: var(--brand); font-size: 12px; font-style: normal; }.address-cards p { min-height: 44px; color: var(--muted); line-height: 1.7; }.address-cards footer { text-align: right; }.address-cards button { margin-left: 16px; border: 0; background: transparent; color: var(--brand); }.form-row { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
</style>
