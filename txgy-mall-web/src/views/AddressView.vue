<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useAccountMessageBox, useAccountOperation } from '@/composables/useAccountOperation'
const ElMessageBox = useAccountMessageBox()
const operation = useAccountOperation()

import { memberApi, systemApi, type AreaNode } from '@/api/mall'
import EmptyState from '@/components/EmptyState.vue'
import MemberPage from '@/components/MemberPage.vue'
import AddressMapDialog from '@/components/AddressMapDialog.vue'
import type { AddressMapSelection } from '@/components/AddressMapDialog.vue'
import type { Address } from '@/types/mall'
import { addressLocationText, addressTextChanged, hasAddressLocation, manualAddressPayload } from '@/utils/fulfillment'

const addresses = ref<Address[]>([])
const areaTree = ref<AreaNode[]>([])
const loading = ref(true)
const dialog = ref(false)
const saving = ref(false)
const mapDialog = ref(false)
const original = ref<Address | null>(null)
const clearLocation = ref(false)
const locationPicked = ref(false)
const form = reactive<Address>({ id: 0, name: '', mobile: '', areaId: undefined, detailAddress: '', defaultStatus: false })
const locationChanged = computed(() => hasAddressLocation(original.value) && addressTextChanged(form, original.value))
let editorGeneration = 0

async function load() {
  loading.value = true
  try { const result = await memberApi.getAddresses(); if (operation.current()) addresses.value = result }
  finally { if (operation.current()) loading.value = false }
}

function open(item?: Address) {
  operation.assert()
  editorGeneration += 1
  original.value = item ? { ...item } : null
  clearLocation.value = false
  locationPicked.value = false
  // Reset optional fields too: a new address must not inherit the last editor's coordinates.
  Object.assign(form, { id: 0, name: '', mobile: '', areaId: undefined, areaName: undefined, detailAddress: '', defaultStatus: false,
    longitude: null, latitude: null, coordSystem: null, locationSource: null, locationPrecision: null, locationConfirmedAt: null }, item || {})
  dialog.value = true
}

async function save() {
  if (!operation.current() || saving.value) return
  if (!form.name.trim() || !/^1\d{10}$/.test(form.mobile.trim()) || !form.areaId || !form.detailAddress.trim()) return ElMessage.warning('请完整填写收货地址')
  const generation = editorGeneration
  const current = () => operation.current() && generation === editorGeneration && dialog.value
  const payload = manualAddressPayload(form, locationPicked.value ? null : original.value, clearLocation.value)
  saving.value = true
  try {
    if (form.id) await memberApi.updateAddress({ ...payload, id: form.id })
    else await memberApi.createAddress(payload)
    if (!current()) return
    ElMessage.success('地址已保存，移动端刷新后同步显示')
    dialog.value = false
    await load()
  } finally { if (operation.current()) saving.value = false }
}

function applyMapSelection(selection: AddressMapSelection) {
  Object.assign(form, {
    ...(selection.areaId ? { areaId: selection.areaId } : {}),
    ...(selection.areaName ? { areaName: selection.areaName } : {}),
    ...(selection.formattedAddress ? { detailAddress: selection.formattedAddress } : {}),
    longitude: selection.longitude,
    latitude: selection.latitude,
    coordSystem: 'GCJ02',
    locationSource: 'MAP_PICKER',
    locationPrecision: selection.locationPrecision || 'ADDRESS',
    locationConfirmed: true,
  })
  clearLocation.value = false
  locationPicked.value = true
}

async function remove(id: string | number) {
  try { await ElMessageBox.confirm('确定删除这个收货地址吗？', '删除地址', { type: 'warning', confirmButtonText: '确定删除', cancelButtonText: '取消' }) } catch { return }
  await memberApi.deleteAddress(id)
  if (!operation.current()) return
  await load()
}

watch(dialog, (visible) => { if (!visible) { editorGeneration += 1; mapDialog.value = false } })

onMounted(async () => {
  const [, areas] = await Promise.allSettled([load(), systemApi.getAreaTree()])
  if (operation.current() && areas.status === 'fulfilled') areaTree.value = areas.value
})
</script>

<template>
  <MemberPage title="收货地址" description="此地址簿与移动端共用">
    <template #actions><el-button type="primary" @click="open()">新增地址</el-button></template>
    <div v-if="loading" class="loading-box"><el-skeleton :rows="6" animated /></div>
    <EmptyState v-else-if="!addresses.length" title="暂无收货地址" description="新增后即可在 PC 与移动端结算时使用" />
    <div v-else class="address-cards"><article v-for="item in addresses" :key="item.id"><header><strong>{{ item.name }}</strong><span>{{ item.mobile }}</span><i v-if="item.defaultStatus">默认</i></header><p>{{ item.areaName }} {{ item.detailAddress }}</p><small>{{ addressLocationText(item) }}</small><footer><button @click="open(item)">编辑</button><button @click="remove(item.id)">删除</button></footer></article></div>
    <el-dialog v-model="dialog" :title="form.id ? '编辑地址' : '新增地址'" width="560px">
      <el-form label-position="top">
        <div class="form-row">
          <el-form-item label="收货人"><el-input v-model="form.name" /></el-form-item>
          <el-form-item label="手机号"><el-input v-model="form.mobile" maxlength="11" /></el-form-item>
        </div>
        <el-form-item label="省/市/区"><el-cascader v-model="form.areaId" :options="areaTree" :props="{ value: 'id', label: 'name', emitPath: false }" filterable clearable /></el-form-item>
        <el-form-item label="详细地址"><el-input v-model="form.detailAddress" type="textarea" maxlength="120" /></el-form-item>
        <el-form-item label="地图点位（可选）">
          <div class="location-editor">
            <el-button @click="mapDialog = true">查看地图选点</el-button>
            <p>{{ addressLocationText(form) }}</p>
            <p v-if="locationChanged || clearLocation" class="location-warning">保存后会清除原地图点位，避免点位与文字地址不一致。不影响手工地址使用或历史订单。</p>
            <el-checkbox v-if="hasAddressLocation(form) && !locationChanged" v-model="clearLocation">清除地图点位，使用手工地址</el-checkbox>
            <small>地图未配置、加载失败或旧地址没有坐标，均可正常保存。</small>
          </div>
        </el-form-item>
        <el-checkbox v-model="form.defaultStatus">设为默认地址</el-checkbox>
      </el-form>
      <template #footer><el-button @click="dialog = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
    </el-dialog>
    <AddressMapDialog v-model="mapDialog" :location="form" :address-text="[form.areaName, form.detailAddress].filter(Boolean).join(' ')" @picked="applyMapSelection" />
  </MemberPage>
</template>

<style scoped lang="scss">
.address-cards { display: grid; grid-template-columns: repeat(2, 1fr); gap: 16px; }.address-cards article { padding: 22px; border: 1px solid var(--line); border-radius: 7px; }.address-cards header { display: flex; gap: 18px; align-items: center; }.address-cards i { padding: 3px 8px; background: #fff0e8; color: var(--brand); font-size: 12px; font-style: normal; }.address-cards p { min-height: 44px; color: var(--muted); line-height: 1.7; }.address-cards footer { text-align: right; }.address-cards button { margin-left: 16px; border: 0; background: transparent; color: var(--brand); }.form-row { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.address-cards small, .location-editor small { color: var(--muted); }.location-editor p { margin: 8px 0; line-height: 1.6; }.location-editor small { display: block; margin-top: 8px; }.location-warning { color: #a55a09; }
</style>
