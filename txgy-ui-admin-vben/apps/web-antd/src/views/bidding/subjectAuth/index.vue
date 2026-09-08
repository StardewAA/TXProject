<script lang="ts" setup>
import { SubjectApi } from '#/api/bidding/subject';

import { computed, onMounted, reactive, ref } from 'vue';

import { Button, Drawer, Input, message, Modal, Radio, Select, Space, Switch, Table, Tag } from 'ant-design-vue';

import {
  authorizeSubject,
  fetchSubjectAuthEligibility,
  fetchSubjectAuthPage,
  fetchSubjectUserPage,
  bindSubjectUser,
  unbindSubjectUser,
  updateSubjectAuthStatus,
} from '#/api/bidding/subject';
import { requestClient } from '#/api/request';

const createMessage = message;
const loading = ref(false);
const rows = ref<SubjectApi.AuthRow[]>([]);
const total = ref(0);
const pageNo = ref(1);
const pageSize = ref(10);
const filters = reactive<{ subjectType?: string; authStatus?: string; tenantName?: string }>({});

const p2 = (n: number) => String(n).padStart(2, '0');
const fmtT = (v?: number): string => {
  if (!v) return '—';
  const d = new Date(Number(v));
  if (Number.isNaN(+d)) return '—';
  return d.getFullYear() + '-' + p2(d.getMonth() + 1) + '-' + p2(d.getDate()) + ' ' + p2(d.getHours()) + ':' + p2(d.getMinutes());
};

async function load() {
  loading.value = true;
  try {
    const res = await fetchSubjectAuthPage({ pageNo: pageNo.value, pageSize: pageSize.value, ...filters });
    rows.value = res.list || [];
    total.value = Number(res.total || 0);
  } catch (e) {
    createMessage.error('加载失败：' + ((e as Error).message || String(e)));
  } finally {
    loading.value = false;
  }
}
const pages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)));
function goPrev() { if (pageNo.value > 1) { pageNo.value -= 1; load(); } }
function goNext() { if (pageNo.value < pages.value) { pageNo.value += 1; load(); } }
function onSearch() { pageNo.value = 1; load(); }

/* ============ 授权新主体 ============ */
const authOpen = ref(false);
const authSaving = ref(false);
const tenantOptions = ref<Array<{ label: string; value: number | string }>>([]);
const authForm = reactive<{ tenantId?: number | string; subjectType: string; remark?: string }>({ subjectType: 'BIDDER' });
async function loadTenants() {
  if (tenantOptions.value.length) return;
  try {
    const res = await requestClient.get<{ list?: Array<{ id: number | string; name?: string }> }>('/system/tenant/page', { params: { pageNo: 1, pageSize: 100 } });
    tenantOptions.value = (res.list || []).map((t) => ({ label: String(t.name || t.id), value: t.id }));
  } catch {
    tenantOptions.value = [];
  }
}
function openAuthorize() {
  authForm.tenantId = undefined;
  authForm.subjectType = 'BIDDER';
  authForm.remark = '';
  void loadTenants();
  authOpen.value = true;
}
async function doAuthorize() {
  if (authForm.tenantId === undefined || authForm.tenantId === null) { createMessage.warning('请选择企业租户'); return; }
  authSaving.value = true;
  try {
    const name = tenantOptions.value.find((t) => String(t.value) === String(authForm.tenantId))?.label;
    await authorizeSubject({ tenantId: authForm.tenantId, tenantName: name || undefined, subjectType: authForm.subjectType, remark: authForm.remark || undefined });
    createMessage.success('授权成功（重复启用会被后端拒绝；停用记录可复用启用）');
    authOpen.value = false;
    load();
  } catch (e) {
    createMessage.error('授权失败：' + ((e as Error).message || String(e)));
  } finally {
    authSaving.value = false;
  }
}

/* ============ 启停 ============ */
function toggleStatus(row: SubjectApi.AuthRow, checked: boolean) {
  const target = checked ? 'ENABLED' : 'DISABLED';
  const cur = row.authStatus;
  if (cur === target) return;
  Modal.confirm({
    title: (checked ? '启用' : '停用') + '授权',
    content: (checked ? '重新启用' : '停用/撤销授权') + '「' + row.tenantName + '」（' + SubjectApi.SUBJECT_TYPE_LABEL[row.subjectType || ''] || row.subjectType + '）？停用后将触发资格门拒绝其发布/备案。',
    okText: '确认',
    okButtonProps: checked ? {} : { danger: true },
    cancelText: '取消',
    onOk: async () => {
      try { await updateSubjectAuthStatus(row.id!, target); createMessage.success('已' + (checked ? '启用' : '停用')); load(); } catch (e) { createMessage.error((e as Error).message || String(e)); }
    },
  });
}

/* ============ 资格抽屉 ============ */
const elOpen = ref(false);
const elLoading = ref(false);
const elData = ref<SubjectApi.Eligibility>();
const elRow = ref<SubjectApi.AuthRow>();
const reasonLabel: Record<string, string> = { NOT_AUTHORIZED: '未授权', AUTH_DISABLED: '授权停用', PROFILE_MISSING: '档案缺失', PROFILE_INCOMPLETE: '档案不完善' };
async function openEligibility(row: SubjectApi.AuthRow) {
  elRow.value = row;
  elData.value = undefined;
  elOpen.value = true;
  elLoading.value = true;
  try {
    elData.value = await fetchSubjectAuthEligibility(row.tenantId!, row.subjectType || 'BIDDER');
  } catch (e) {
    createMessage.error('资格查询失败：' + ((e as Error).message || String(e)));
  } finally {
    elLoading.value = false;
  }
}

/* ============ 经办用户抽屉 ============ */
const userOpen = ref(false);
const userLoading = ref(false);
const users = ref<SubjectApi.UserRow[]>([]);
const userTotal = ref(0);
const userPageNo = ref(1);
const userPageSize = ref(8);
const userRow = ref<SubjectApi.AuthRow>();
async function loadUsers(page = userPageNo.value) {
  if (!userRow.value) return;
  userLoading.value = true;
  try {
    const res = await fetchSubjectUserPage({ pageNo: page, pageSize: userPageSize.value, tenantId: userRow.value.tenantId!, subjectType: userRow.value.subjectType || undefined });
    users.value = res.list || [];
    userTotal.value = Number(res.total || 0);
    userPageNo.value = page;
  } catch (e) {
    createMessage.error('经办用户加载失败：' + ((e as Error).message || String(e)));
  } finally {
    userLoading.value = false;
  }
}
function openUsers(row: SubjectApi.AuthRow) {
  userRow.value = row;
  users.value = [];
  userTotal.value = 0;
  userPageNo.value = 1;
  userOpen.value = true;
  void loadUsers(1);
}

/* ============ 绑定经办用户 ============ */
const bindOpen = ref(false);
const bindSaving = ref(false);
const sysUsers = ref<Array<{ label: string; value: number | string }>>([]);
const bindForm = reactive<{ userId?: number | string; userRole: string; remark?: string }>({ userRole: 'OPERATOR' });
async function loadSysUsers() {
  if (sysUsers.value.length) return;
  try {
    const res = await requestClient.get<{ list?: Array<{ id: number | string; nickname?: string; username?: string }> }>('/system/user/page', { params: { pageNo: 1, pageSize: 200 } });
    sysUsers.value = (res.list || []).map((u) => ({ label: (u.nickname || u.username || '') + ' (#' + String(u.id) + ')', value: u.id }));
  } catch {
    sysUsers.value = [];
  }
}
function openBind() {
  bindForm.userId = undefined;
  bindForm.userRole = 'OPERATOR';
  bindForm.remark = '';
  void loadSysUsers();
  bindOpen.value = true;
}
async function doBind() {
  if (!userRow.value) return;
  if (bindForm.userId === undefined || bindForm.userId === null) { createMessage.warning('请选择经办用户'); return; }
  bindSaving.value = true;
  try {
    await bindSubjectUser({ tenantId: userRow.value.tenantId!, subjectType: userRow.value.subjectType || 'BIDDER', userId: bindForm.userId, userRole: bindForm.userRole, remark: bindForm.remark || undefined });
    createMessage.success('已绑定（昵称快照由服务端回填）');
    bindOpen.value = false;
    void loadUsers(userPageNo.value);
  } catch (e) {
    createMessage.error('绑定失败：' + ((e as Error).message || String(e)));
  } finally {
    bindSaving.value = false;
  }
}
function doUnbind(u: SubjectApi.UserRow) {
  Modal.confirm({
    title: '解绑经办用户',
    content: '解绑「' + u.userNickname + '」（#' + String(u.userId) + '）？该账号将不再作为本主体经办用户。',
    okText: '解绑',
    okButtonProps: { danger: true },
    cancelText: '取消',
    onOk: async () => {
      try { await unbindSubjectUser(u.id!); createMessage.success('已解绑'); void loadUsers(userPageNo.value); } catch (e) { createMessage.error((e as Error).message || String(e)); }
    },
  });
}

/* ============ 经办人双权限装配视图（只读总览：企业主体 + 经办人，双身份独立判定；不做个人主体/个人授权） ============ */
interface DualEntPerm { authStatus?: string; eligible?: boolean; profileComplete?: boolean; }
interface DualRow { userId: number | string; userNickname: string; bidder?: { userRole?: string; enabled?: boolean }; tenderer?: { userRole?: string; enabled?: boolean }; }
const dualOpen = ref(false);
const dualLoading = ref(false);
const dualBase = ref<SubjectApi.AuthRow>();
const dualEnt = ref<{ bidder?: DualEntPerm; tenderer?: DualEntPerm }>({});
const dualUsers = ref<DualRow[]>([]);
const dualColumns = [
  { title: '经办人（共用基础信息）', key: 'user' },
  { title: '投标权限 · 经办（BIDDER）', key: 'bidder' },
  { title: '招标权限 · 经办（TENDERER）', key: 'tenderer' },
];
const dualRoleText = (r?: string): string => (r === 'CONTACT' ? '联系人' : r === 'OPERATOR' ? '操作人' : '—');
const dualPermText = (x?: { userRole?: string; enabled?: boolean }): string =>
  !x ? '未绑定' : dualRoleText(x.userRole) + ' · ' + (x.enabled === false ? '停用' : '启用');
const dualEntText = (x?: DualEntPerm): string => {
  if (!x) return '未授权';
  const auth = x.authStatus === 'ENABLED' ? '已授权' : x.authStatus === 'DISABLED' ? '已停用' : '未授权';
  const g = x.eligible ? '具备资格' : x.profileComplete === false ? '档案未完善' : '待核验';
  return auth + ' · ' + g;
};
async function openDual(row: SubjectApi.AuthRow) {
  dualBase.value = row;
  dualEnt.value = {};
  dualUsers.value = [];
  dualOpen.value = true;
  dualLoading.value = true;
  try {
    const [b, t, eb, et] = await Promise.all([
      fetchSubjectUserPage({ pageNo: 1, pageSize: 200, tenantId: row.tenantId!, subjectType: 'BIDDER' }),
      fetchSubjectUserPage({ pageNo: 1, pageSize: 200, tenantId: row.tenantId!, subjectType: 'TENDERER' }),
      fetchSubjectAuthEligibility(row.tenantId!, 'BIDDER').catch(() => undefined),
      fetchSubjectAuthEligibility(row.tenantId!, 'TENDERER').catch(() => undefined),
    ]);
    const map = new Map<string | number, DualRow>();
    const put = (list: Array<SubjectApi.UserRow>, key: 'bidder' | 'tenderer') => {
      for (const u of list) {
        const id = u.userId as string | number;
        let r = map.get(id);
        if (!r) { r = { userId: id, userNickname: u.userNickname || '#' + String(id) }; map.set(id, r); }
        r[key] = { userRole: u.userRole, enabled: u.enabled };
      }
    };
    put(b.list || [], 'bidder');
    put(t.list || [], 'tenderer');
    dualUsers.value = Array.from(map.values());
    dualEnt.value = { bidder: eb as DualEntPerm | undefined, tenderer: et as DualEntPerm | undefined };
  } catch (e) {
    createMessage.error('双权限装配加载失败：' + ((e as Error).message || String(e)));
  } finally {
    dualLoading.value = false;
  }
}

onMounted(load);
</script>

<template>
  <div class="bid-ind ind-page">
    <IndPageHead title="主体信息与授权管理 · 平台侧" mark="SUBJECT" sub="企业主体 = 投标/招标资格单元（招标投标法 25 条；A 方案：企业 + 多经办人）。授权是资格前提，授权后企业仍须完善档案方可投标/发布；每行可查看资格、经办用户与「双视角」人员装配。">
      <template #actions>
        <Button type="primary" size="large" @click="openAuthorize">＋ 授权主体</Button>
        <Button size="large" @click="load">刷新</Button>
      </template>
    </IndPageHead>
    <div class="ind-body">

    <div class="ind-filterbar">
      <Select v-model:value="filters.subjectType" placeholder="主体类型" allow-clear style="width: 130px">
        <Select.Option value="BIDDER">投标人</Select.Option>
        <Select.Option value="TENDERER">招标人</Select.Option>
      </Select>
      <Select v-model:value="filters.authStatus" placeholder="授权状态" allow-clear style="width: 130px">
        <Select.Option value="ENABLED">已启用</Select.Option>
        <Select.Option value="DISABLED">已停用</Select.Option>
      </Select>
      <Input v-model:value="filters.tenantName" placeholder="企业名称模糊检索" allow-clear style="width: 200px" @press-enter="onSearch" />
      <Button type="primary" ghost @click="onSearch">查询</Button>
    </div>

    <div class="rounded-md border border-gray-200 bg-white">
      <Table :data-source="rows" :loading="loading" :pagination="false" row-key="id" size="middle">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'tenant'">
            <div class="font-medium text-gray-800">{{ record.tenantName || '（无名称快照）' }}</div>
            <div class="font-mono text-xs text-gray-400">租户 #{{ record.tenantId }}</div>
          </template>
          <template v-else-if="column.key === 'type'">
            <Tag color="blue">{{ SubjectApi.SUBJECT_TYPE_LABEL[record.subjectType] || record.subjectType }}</Tag>
          </template>
          <template v-else-if="column.key === 'authStatus'">
            <Space size="small">
              <Tag :color="record.authStatus === 'ENABLED' ? 'green' : 'default'">{{ record.authStatus === 'ENABLED' ? '已授权' : '已停用' }}</Tag>
              <Switch
                :checked="record.authStatus === 'ENABLED'"
                size="small"
                checked-children="启用"
                un-checked-children="停用"
                @change="(c: unknown) => toggleStatus(record as SubjectApi.AuthRow, c === true)"
              />
            </Space>
          </template>
          <template v-else-if="column.key === 'profile'">
            <template v-if="record.profileExists">
              <Tag :color="record.profileComplete ? 'green' : 'orange'">{{ record.profileComplete ? '档案已完善' : '已授权未完善' }}</Tag>
              <div v-if="!record.profileComplete && record.missingRequiredNames?.length" class="text-xs text-gray-400">缺：{{ record.missingRequiredNames.join('、') }}</div>
            </template>
            <Tag v-else color="red">未建档</Tag>
          </template>
          <template v-else-if="column.key === 'time'">{{ fmtT(record.authorizeTime) }}</template>
          <template v-else-if="column.key === 'action'">
            <Space>
              <a class="text-xs text-blue-600" @click="openEligibility(record as SubjectApi.AuthRow)">资格</a>
              <a class="text-xs text-blue-600" @click="openUsers(record as SubjectApi.AuthRow)">经办用户</a>
              <a class="text-xs text-indigo-600" @click="openDual(record as SubjectApi.AuthRow)">双视角</a>
            </Space>
          </template>
        </template>
        <template #emptyText><div class="py-8 text-xs text-gray-300">尚无授权主体 — 点右上「授权主体」选择企业租户核发</div></template>
      </Table>
      <div class="flex items-center justify-end gap-2 px-3 py-2 text-xs text-gray-400">
        <span>共 {{ total }} 条</span>
        <Button size="small" :disabled="pageNo <= 1" @click="goPrev">上一页</Button>
        <span>{{ pageNo }} / {{ pages }}</span>
        <Button size="small" :disabled="pageNo >= pages" @click="goNext">下一页</Button>
      </div>
    </div>

    <!-- 授权弹窗 -->
    <Modal v-model:open="authOpen" title="授权企业主体（平台核发）" :footer="null" width="480">
      <div class="space-y-3">
        <div>
          <div class="mb-1 text-xs font-medium text-gray-600">企业租户 *</div>
          <Select v-model:value="authForm.tenantId" placeholder="选择被授权企业（system 租户）" style="width: 100%" :options="tenantOptions" show-search option-filter-prop="label" />
        </div>
        <div>
          <div class="mb-1 text-xs font-medium text-gray-600">主体类型 *</div>
          <Radio.Group v-model:value="authForm.subjectType" option-type="button" :options="[{ label: '投标人 BIDDER', value: 'BIDDER' }, { label: '招标人 TENDERER', value: 'TENDERER' }]" />
        </div>
        <div>
          <div class="mb-1 text-xs font-medium text-gray-600">备注</div>
          <Input.TextArea v-model:value="authForm.remark" :rows="2" />
        </div>
        <div class="text-right"><Space><Button @click="authOpen = false">取消</Button><Button type="primary" :loading="authSaving" @click="doAuthorize">授权</Button></Space></div>
      </div>
    </Modal>

    </div>

    <!-- 资格抽屉 -->
    <Drawer v-model:open="elOpen" :title="(elRow?.tenantName || '企业') + ' 资格状态'" :width="520">
      <SpinWrap :loading="elLoading">
        <div v-if="elData" class="space-y-2 text-xs">
          <div class="rounded-md border p-3" :class="elData.eligible ? 'border-green-200 bg-green-50' : 'border-orange-200 bg-orange-50'">
            <div class="text-sm font-semibold" :class="elData.eligible ? 'text-green-700' : 'text-orange-700'">{{ elData.eligible ? '具备资格' : '暂不具备资格' }}（{{ elData.subjectType === 'TENDERER' ? '招标人' : '投标人' }}）</div>
            <div class="mt-1 text-gray-600">{{ elData.message }}</div>
          </div>
          <div class="grid grid-cols-2 gap-2">
            <InfoCell label="授权状态" :value="elData.authStatus === 'ENABLED' ? '已启用' : elData.authStatus === 'DISABLED' ? '已停用' : '未授权'" />
            <InfoCell label="档案完善" :value="elData.profileComplete ? '是' : '否'" />
            <InfoCell label="授权时间" :value="fmtT(elData.authorizeTime)" />
            <InfoCell label="停用时间" :value="fmtT(elData.disableTime)" />
          </div>
          <div v-if="elData.reasonCodes?.length" class="rounded border border-red-100 bg-red-50 p-2">
            原因：{{ elData.reasonCodes.map((r) => reasonLabel[r] || r).join('、') }}
            <span v-if="elData.missingRequiredNames?.length" class="ml-2">缺失：{{ elData.missingRequiredNames.join('、') }}</span>
          </div>
          <div class="pt-1 text-gray-300">资格 = 平台授权（ENABLED） ∧ 档案必填齐（信息不完整无法投标/发布；关注与订阅不受影响）</div>
        </div>
        <div v-else-if="!elLoading" class="text-xs text-gray-300">查询无结果（后端 106 错误码会在此展示）</div>
      </SpinWrap>
    </Drawer>

    <!-- 经办用户抽屉 -->
    <Drawer v-model:open="userOpen" :title="(userRow?.tenantName || '企业') + ' · 经办用户'" :width="640">
      <SpinWrap :loading="userLoading">
        <div class="mb-3 flex items-center justify-between">
          <span class="text-xs text-gray-400">主体：{{ SubjectApi.SUBJECT_TYPE_LABEL[userRow?.subjectType || ''] || userRow?.subjectType }}（租户 #{{ userRow?.tenantId }}）</span>
          <Button type="primary" size="small" @click="openBind">+ 绑定经办用户</Button>
        </div>
        <Table :data-source="users" :pagination="false" row-key="id" size="small">
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'user'">
              <span class="text-gray-800">{{ record.userNickname }}</span>
              <span class="ml-1 font-mono text-xs text-gray-400">#{{ record.userId }}</span>
            </template>
            <template v-else-if="column.key === 'role'"><Tag>{{ record.userRole === 'CONTACT' ? '联系人' : '操作人' }}</Tag></template>
            <template v-else-if="column.key === 'enabled'">
              <Tag :color="record.enabled ? 'green' : 'default'">{{ record.enabled ? '启用' : '停用' }}</Tag>
            </template>
            <template v-else-if="column.key === 'op'">
              <a class="text-xs text-red-500" @click="doUnbind(record as SubjectApi.UserRow)">解绑</a>
            </template>
          </template>
          <template #emptyText><div class="py-6 text-xs text-gray-300">尚未绑定经办用户</div></template>
        </Table>
        <div class="mt-2 flex items-center justify-end gap-2 text-xs text-gray-400">
          <span>共 {{ userTotal }} 条</span>
          <Button size="small" :disabled="userPageNo <= 1" @click="loadUsers(userPageNo - 1)">上一页</Button>
          <Button size="small" :disabled="userPageNo * userPageSize >= userTotal" @click="loadUsers(userPageNo + 1)">下一页</Button>
        </div>
      </SpinWrap>
    </Drawer>

    <!-- 经办人双权限装配视图（只读总览） -->
    <Drawer v-model:open="dualOpen" :title="(dualBase?.tenantName || '企业') + ' · 经办人双权限装配视图'" :width="780">
      <SpinWrap :loading="dualLoading">
        <div class="mb-3 rounded-md border border-indigo-100 bg-indigo-50 px-3 py-2 text-xs text-indigo-800">
          企业身份授权独立且共档：投标权限（BIDDER）与招标权限（TENDERER）各自判定；人员仅为企业主体下经办人（共用企业档案），不构成个人主体。租户 #{{ dualBase?.tenantId }}
        </div>
        <div class="mb-3 flex flex-wrap gap-3 text-xs">
          <span class="rounded bg-white px-2 py-1 border border-gray-200">投标权限(企业)：<b>{{ dualEntText(dualEnt.bidder) }}</b></span>
          <span class="rounded bg-white px-2 py-1 border border-gray-200">招标权限(企业)：<b>{{ dualEntText(dualEnt.tenderer) }}</b></span>
        </div>
        <Table :data-source="dualUsers" :columns="dualColumns" :pagination="false" row-key="userId" size="small">
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'user'">
              <span class="text-gray-800">{{ record.userNickname }}</span>
              <span class="ml-1 font-mono text-xs text-gray-400">#{{ record.userId }}</span>
            </template>
            <template v-else-if="column.key === 'bidder'">
              <Tag v-if="record.bidder" :color="record.bidder.enabled === false ? 'orange' : 'blue'">{{ dualPermText(record.bidder) }}</Tag>
              <span v-else class="text-gray-300">{{ dualPermText(record.bidder) }}</span>
            </template>
            <template v-else-if="column.key === 'tenderer'">
              <Tag v-if="record.tenderer" :color="record.tenderer.enabled === false ? 'orange' : 'green'">{{ dualPermText(record.tenderer) }}</Tag>
              <span v-else class="text-gray-300">{{ dualPermText(record.tenderer) }}</span>
            </template>
          </template>
          <template #emptyText><div class="py-6 text-xs text-gray-300">该企业两类主体均未绑定经办人</div></template>
        </Table>
      </SpinWrap>
    </Drawer>

    <!-- 绑定弹窗 -->
    <Modal v-model:open="bindOpen" title="绑定经办用户" :footer="null" width="480">
      <div class="space-y-3">
        <div>
          <div class="mb-1 text-xs font-medium text-gray-600">经办用户 *（该主体企业租户下的账号）</div>
          <Select v-model:value="bindForm.userId" placeholder="选择系统用户" style="width: 100%" :options="sysUsers" show-search option-filter-prop="label" />
          <div class="mt-1 text-xs text-gray-300">当前列出的是本管理端可访问的系统用户；跨租户经办用户绑定由企业侧账号或后端 RPC 完成</div>
        </div>
        <div>
          <div class="mb-1 text-xs font-medium text-gray-600">经办角色</div>
          <Radio.Group v-model:value="bindForm.userRole" option-type="button" :options="[{ label: '操作人 OPERATOR', value: 'OPERATOR' }, { label: '联系人 CONTACT', value: 'CONTACT' }]" />
        </div>
        <div>
          <div class="mb-1 text-xs font-medium text-gray-600">备注</div>
          <Input.TextArea v-model:value="bindForm.remark" :rows="2" />
        </div>
        <div class="text-right"><Space><Button @click="bindOpen = false">取消</Button><Button type="primary" :loading="bindSaving" @click="doBind">绑定</Button></Space></div>
      </div>
    </Modal>
  </div>
</template>

<script lang="ts">
// 极简 Spin + 信息格组件（避免额外依赖）
import { defineComponent, h } from 'vue';
import { Spin } from 'ant-design-vue';

const SpinWrap = defineComponent({
  name: 'SpinWrap',
  props: { loading: Boolean },
  setup(props, { slots }) {
    return () => h(Spin, { spinning: props.loading }, () => slots.default?.());
  },
});
const InfoCell = defineComponent({
  name: 'InfoCell',
  props: { label: String, value: { type: String, default: '' } },
  setup(props) {
    return () => h('div', { class: 'rounded border border-gray-100 bg-gray-50 p-2' }, [
      h('div', { class: 'text-gray-400' }, props.label),
      h('div', { class: 'font-medium text-gray-700' }, props.value || '—'),
    ]);
  },
});
export default { components: { SpinWrap, InfoCell } };
</script>
