<script setup lang="ts">
import type { MallBusinessMemberApi } from '#/api/mall/product/business-member';

import { computed, onUnmounted, reactive, ref, watch } from 'vue';

import { useAccess } from '@vben/access';
import { Page } from '@vben/common-ui';
import { useAccessStore, useUserStore } from '@vben/stores';

import {
  Alert,
  Button,
  Card,
  InputNumber,
  message,
  Modal,
  Select,
  SelectOption,
  Table,
  Tag,
} from 'ant-design-vue';

import {
  getBusinessMemberList,
  getBusinessScopeShops,
  saveBusinessMember,
} from '#/api/mall/product/business-member';

const QUERY = 'product:business-member:query';
const UPDATE = 'product:business-member:update';
const access = useAccessStore();
const user = useUserStore();
const { hasAccessByCodes } = useAccess();
const shops = ref<MallBusinessMemberApi.Shop[]>([]);
const members = ref<MallBusinessMemberApi.Member[]>([]);
const enterpriseId = ref<number>();
const busy = ref(false);
const error = ref('');
const opened = ref(false);
const editing = ref(false);
let generation = 0;
const form = reactive<MallBusinessMemberApi.SaveMember>({
  adminUserId: 0,
  enterpriseId: 0,
  scopeType: 2,
  shopIds: [],
  status: 0,
});

const crossTenant = computed(() => access.visitTenantId != null
  && String(access.visitTenantId) !== String(access.tenantId));
const canQuery = computed(() => !!access.accessToken
  && hasAccessByCodes([QUERY])
  && !crossTenant.value);
const canUpdate = computed(() => hasAccessByCodes([UPDATE]) && !crossTenant.value);
const contextKey = computed(() => [
  access.accessToken,
  access.tenantId,
  access.visitTenantId,
  access.accessCodes.join('|'),
  user.userInfo?.id,
].join(':'));
const blocked = computed(() => crossTenant.value
  ? '企业员工授权不支持跨租户代操作，请切回账号所属租户。'
  : !canQuery.value
    ? `当前账号缺少 ${QUERY} 权限，页面不会读取员工授权。`
    : '');
const enterprises = computed(() => {
  const seen = new Set<number>();
  return shops.value
    .filter((shop) => !seen.has(shop.enterpriseId) && seen.add(shop.enterpriseId))
    .map((shop) => ({ label: `企业 ${shop.enterpriseId}`, value: shop.enterpriseId }));
});
const scopedShops = computed(() => shops.value.filter(
  (shop) => shop.enterpriseId === enterpriseId.value,
));
const columns = [
  { dataIndex: 'adminUserId', key: 'adminUserId', title: '后台账号编号', width: 140 },
  { key: 'user', title: '员工', width: 220 },
  { key: 'scope', title: '店铺范围', width: 360 },
  { key: 'status', title: '状态', width: 100 },
  { key: 'action', title: '操作', width: 120 },
];

function clear() {
  generation += 1;
  shops.value = [];
  members.value = [];
  enterpriseId.value = undefined;
  error.value = '';
  opened.value = false;
}

async function loadMembers(ticket = generation) {
  if (!canQuery.value || !enterpriseId.value) return;
  const requestedEnterpriseId = enterpriseId.value;
  busy.value = true;
  error.value = '';
  try {
    const result = await getBusinessMemberList(requestedEnterpriseId);
    if (ticket !== generation || enterpriseId.value !== requestedEnterpriseId) return;
    members.value = result;
  } catch (cause) {
    if (ticket === generation) {
      console.error('[business-member] list failed', cause);
      members.value = [];
      error.value = '企业员工授权读取失败，请重试。';
    }
  } finally {
    if (ticket === generation) busy.value = false;
  }
}

async function initialize() {
  clear();
  if (!canQuery.value) return;
  const ticket = generation;
  busy.value = true;
  try {
    const result = await getBusinessScopeShops();
    if (ticket !== generation) return;
    shops.value = result.filter((shop) => shop.status === 0);
    enterpriseId.value = shops.value[0]?.enterpriseId;
    if (!enterpriseId.value) {
      error.value = '当前账号没有可管理的启用企业店铺。';
      return;
    }
    await loadMembers(ticket);
  } catch (cause) {
    if (ticket === generation) {
      console.error('[business-member] scope failed', cause);
      error.value = '企业经营范围读取失败，请联系租户管理员核对授权。';
    }
  } finally {
    if (ticket === generation) busy.value = false;
  }
}

function selectEnterprise() {
  members.value = [];
  void loadMembers();
}

function openCreate() {
  if (!enterpriseId.value || !canUpdate.value) return;
  editing.value = false;
  Object.assign(form, {
    adminUserId: 0,
    enterpriseId: enterpriseId.value,
    scopeType: 2,
    shopIds: [],
    status: 0,
  });
  opened.value = true;
}

function openEdit(row: Record<string, any>) {
  if (!canUpdate.value) return;
  editing.value = true;
  Object.assign(form, {
    adminUserId: Number(row.adminUserId),
    enterpriseId: Number(row.enterpriseId),
    scopeType: Number(row.scopeType) === 1 ? 1 : 2,
    shopIds: Array.isArray(row.shopIds) ? row.shopIds.map(Number) : [],
    status: Number(row.status) === 1 ? 1 : 0,
  });
  opened.value = true;
}

async function submit() {
  if (!canUpdate.value || busy.value || !Number.isSafeInteger(form.adminUserId) || form.adminUserId <= 0) {
    message.warning('请填写有效的本租户后台账号编号');
    return;
  }
  if (form.scopeType === 2 && form.status === 0 && form.shopIds.length === 0) {
    message.warning('指定店铺范围至少选择一家店铺');
    return;
  }
  const allowed = new Set(scopedShops.value.map((shop) => shop.id));
  if (form.shopIds.some((id) => !allowed.has(id))) {
    message.warning('店铺范围已变化，请刷新后重新选择');
    return;
  }
  busy.value = true;
  try {
    await saveBusinessMember({
      adminUserId: form.adminUserId,
      enterpriseId: form.enterpriseId,
      scopeType: form.scopeType,
      shopIds: form.scopeType === 1 || form.status === 1 ? [] : [...new Set(form.shopIds)],
      status: form.status,
    });
    message.success('企业员工经营范围已保存');
    opened.value = false;
    await loadMembers();
  } finally {
    busy.value = false;
  }
}

watch(contextKey, initialize, { immediate: true });
onUnmounted(() => { generation += 1; });
</script>

<template>
  <Page auto-content-height>
    <Alert
      class="mb-3"
      type="info"
      show-icon
      message="这里只维护后台账号的企业和店铺数据范围，不会自动授予商品、订单、售后、客服或结算按钮权限。"
      description="操作权限仍由后台角色与权限码单独控制；服务端会再次校验当前租户、企业管理员身份、目标账号和店铺归属。"
    />
    <Alert v-if="blocked" class="mb-3" type="warning" show-icon :message="blocked" />
    <Card v-else title="企业资料 · 员工经营授权">
      <div class="toolbar">
        <label>
          管理企业
          <Select v-model:value="enterpriseId" class="enterprise" :options="enterprises" @change="selectEnterprise" />
        </label>
        <Button :loading="busy" @click="loadMembers()">刷新</Button>
        <Button v-if="canUpdate" type="primary" :disabled="!enterpriseId" @click="openCreate">新增员工授权</Button>
      </div>
      <Alert v-if="error" class="mb-3" type="error" show-icon :message="error">
        <template #action><Button size="small" @click="initialize">重试</Button></template>
      </Alert>
      <Table :columns="columns" :data-source="members" :loading="busy" :pagination="false" row-key="id">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'user'"><strong>{{ record.nickname || `账号 ${record.adminUserId}` }}</strong><p class="muted">{{ record.mobile || '未填写手机号' }}</p></template>
          <template v-else-if="column.key === 'scope'">
            <Tag v-if="record.scopeType === 1" color="blue">企业全部店铺</Tag>
            <template v-else><Tag v-for="id in record.shopIds" :key="id">{{ scopedShops.find((shop) => shop.id === id)?.name || `店铺 ${id}` }}</Tag></template>
          </template>
          <Tag v-else-if="column.key === 'status'" :color="record.status === 0 ? 'success' : 'default'">{{ record.status === 0 ? '启用' : '停用' }}</Tag>
          <Button v-else-if="column.key === 'action' && canUpdate" type="link" @click="openEdit(record)">编辑范围</Button>
        </template>
      </Table>
    </Card>

    <Modal v-model:open="opened" :title="editing ? '编辑员工经营授权' : '新增员工经营授权'" :confirm-loading="busy" @ok="submit">
      <div class="form">
        <label>后台账号编号<InputNumber v-model:value="form.adminUserId" :disabled="editing" :min="1" :precision="0" placeholder="本租户 system_user 编号" /></label>
        <label>授权方式<Select v-model:value="form.scopeType"><SelectOption :value="1">企业全部启用店铺</SelectOption><SelectOption :value="2">指定店铺</SelectOption></Select></label>
        <label v-if="form.scopeType === 2 && form.status === 0">指定店铺<Select v-model:value="form.shopIds" mode="multiple" placeholder="选择可经营店铺"><SelectOption v-for="shop in scopedShops" :key="shop.id" :value="shop.id">{{ shop.name }}</SelectOption></Select></label>
        <label>授权状态<Select v-model:value="form.status"><SelectOption :value="0">启用</SelectOption><SelectOption :value="1">停用</SelectOption></Select></label>
      </div>
    </Modal>
  </Page>
</template>

<style scoped>
.toolbar { display: flex; flex-wrap: wrap; gap: 12px; align-items: end; margin-bottom: 16px; }
.toolbar label, .form label { display: grid; gap: 6px; color: #475569; }
.enterprise { width: 280px; }
.form { display: grid; gap: 16px; }
.form :deep(.ant-input-number), .form :deep(.ant-select) { width: 100%; }
.muted { margin: 4px 0 0; color: #64748b; font-size: 12px; }
</style>
