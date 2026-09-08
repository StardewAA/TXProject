<script lang="ts" setup>
import { SubjectApi } from '#/api/bidding/subject';

import { computed, onMounted, reactive, ref } from 'vue';

import { Button, Drawer, Input, message, Modal, Radio, Select, Space, Switch, Table, Tag } from 'ant-design-vue';

import {
  authorizeSubject,
  fetchSubjectAuthEligibility,
  fetchSubjectAuthPage,
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
    title: (checked ? '重新核发资格' : '拉黑（撤销资格）'),
    content: (checked ? '恢复授权（移出黑名单）' : '拉黑') + '「' + row.tenantName + '」（' + (SubjectApi.SUBJECT_TYPE_LABEL[row.subjectType || ''] || row.subjectType) + '）？拉黑后其发布/投标将被资格门拒绝（106_031）；留痕可查，可再授权。',
    okText: checked ? '核发' : '拉黑',
    okButtonProps: checked ? {} : { danger: true },
    cancelText: '取消',
    onOk: async () => {
      try { await updateSubjectAuthStatus(row.id!, target); createMessage.success('已' + (checked ? '恢复资格' : '拉黑')); load(); } catch (e) { createMessage.error((e as Error).message || String(e)); }
    },
  });
}

/* ============ 资格抽屉 ============ */
const elOpen = ref(false);
const elLoading = ref(false);
const elData = ref<SubjectApi.Eligibility>();
const elRow = ref<SubjectApi.AuthRow>();
const reasonLabel: Record<string, string> = { NOT_AUTHORIZED: '未授权', AUTH_DISABLED: '已拉黑', PROFILE_MISSING: '档案缺失', PROFILE_INCOMPLETE: '档案不完善' };
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

onMounted(load);
</script>

<template>
  <div class="bid-ind ind-page">
        <div class="ind-stripes"></div>
    <div class="ind-head">
      <div class="min-w-0">
        <div class="ind-title">主体信息与授权管理 · 平台侧 <span class="ind-mark">SUBJECT</span></div>
        <div class="ind-sub">平台对已入驻企业（基座租户）核发投标人/招标人主体资格；撤销资格即拉黑（停用留痕，可再授权）。授权后企业仍须完善档案方可投标/发布（资格门=授权∧档案完善）。经办账号由企业在自己档案下管理，平台不代管。</div>
      </div>
      <div class="ind-actions">
        <Button type="primary" size="large" @click="openAuthorize">＋ 授权主体</Button>
        <Button size="large" @click="load">刷新</Button>
      </div>
    </div>
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
              <Tag :color="record.authStatus === 'ENABLED' ? 'green' : 'red'">{{ record.authStatus === 'ENABLED' ? '已授权' : '已拉黑' }}</Tag>
              <Switch
                :checked="record.authStatus === 'ENABLED'"
                size="small"
                checked-children="授权"
                un-checked-children="拉黑"
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
            <a class="text-xs text-blue-600" @click="openEligibility(record as SubjectApi.AuthRow)">资格核验</a>
          </template>
        </template>
        <template #emptyText><div class="py-8 text-xs text-gray-300">尚无授权记录 — 点右上「授权主体」为已入驻企业（基座租户）核发主体资格</div></template>
      </Table>
      <div class="flex items-center justify-end gap-2 px-3 py-2 text-xs text-gray-400">
        <span>共 {{ total }} 条</span>
        <Button size="small" :disabled="pageNo <= 1" @click="goPrev">上一页</Button>
        <span>{{ pageNo }} / {{ pages }}</span>
        <Button size="small" :disabled="pageNo >= pages" @click="goNext">下一页</Button>
      </div>
    </div>

    <!-- 授权弹窗 -->
    <Modal v-model:open="authOpen" title="核发主体资格（企业=基座租户）" :footer="null" width="480">
      <div class="space-y-3">
        <div>
          <div class="mb-1 text-xs font-medium text-gray-600">被授权企业（基座租户）*</div>
          <Select v-model:value="authForm.tenantId" placeholder="从基座现有租户中选择" style="width: 100%" :options="tenantOptions" show-search option-filter-prop="label" />
        </div>
        <div>
          <div class="mb-1 text-xs font-medium text-gray-600">主体类型 *</div>
          <Radio.Group v-model:value="authForm.subjectType" option-type="button" :options="[{ label: '投标人 BIDDER', value: 'BIDDER' }, { label: '招标人 TENDERER', value: 'TENDERER' }]" />
        </div>
        <div>
          <div class="mb-1 text-xs font-medium text-gray-600">备注</div>
          <Input.TextArea v-model:value="authForm.remark" :rows="2" />
        </div>
        <div class="text-right"><Space><Button @click="authOpen = false">取消</Button><Button type="primary" :loading="authSaving" @click="doAuthorize">核发资格</Button></Space></div>
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
            <InfoCell label="授权状态" :value="elData.authStatus === 'ENABLED' ? '已授权' : elData.authStatus === 'DISABLED' ? '已拉黑' : '未授权'" />
            <InfoCell label="档案完善" :value="elData.profileComplete ? '是' : '否'" />
            <InfoCell label="授权时间" :value="fmtT(elData.authorizeTime)" />
            <InfoCell label="拉黑时间" :value="fmtT(elData.disableTime)" />
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
