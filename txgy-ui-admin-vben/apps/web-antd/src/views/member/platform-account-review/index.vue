<script setup lang="ts">
import type { MemberPlatformAccountReviewApi } from '#/api/member/platform-account-review';

import { computed, onMounted, reactive, ref } from 'vue';

import { useAccess } from '@vben/access';
import { Page } from '@vben/common-ui';
import { useAccessStore } from '@vben/stores';

import {
  Alert,
  Button,
  Card,
  Form,
  FormItem,
  Input,
  message,
  Modal,
  Pagination,
  Select,
  SelectOption,
  Space,
  Table,
  Tag,
} from 'ant-design-vue';

import {
  getPlatformAccountLinkPage,
  mergePlatformAccountLinks,
} from '#/api/member/platform-account-review';

const QUERY_PERMISSION = 'member:platform-account:query';
const REVIEW_PERMISSION = 'member:platform-account:review';
const statusOptions = [
  { label: '身份冲突', value: 'IDENTITY_CONFLICT' },
  { label: '凭据冲突', value: 'CREDENTIAL_CONFLICT' },
  { label: '已关联', value: 'READY' },
  { label: '待迁移', value: 'PENDING' },
];
const statusLabels: Record<string, string> = Object.fromEntries(
  statusOptions.map((item) => [item.value, item.label]),
);
const statusColors: Record<string, string> = {
  CREDENTIAL_CONFLICT: 'volcano',
  IDENTITY_CONFLICT: 'red',
  PENDING: 'default',
  READY: 'success',
};
const columns = [
  { dataIndex: 'linkId', title: '链接 ID', width: 180 },
  { key: 'identity', title: '隔离身份', width: 220 },
  { dataIndex: 'sourceTenantId', title: '来源租户', width: 110 },
  { dataIndex: 'legacyMemberId', title: '旧会员 ID', width: 170 },
  { key: 'platformAccount', title: '平台账号关联', width: 230 },
  { key: 'status', title: '链接状态', width: 130 },
  { dataIndex: 'conflictReason', title: '冲突原因', width: 260 },
  { key: 'review', title: '人工审核', width: 260 },
  { dataIndex: 'updateTime', title: '更新时间', width: 180 },
];

const accessStore = useAccessStore();
const { hasAccessByCodes } = useAccess();
const rows = ref<MemberPlatformAccountReviewApi.LinkRow[]>([]);
const selectedLinkIds = ref<MemberPlatformAccountReviewApi.Id[]>([]);
const pageNo = ref(1);
const pageSize = ref(20);
const total = ref(0);
const loading = ref(false);
const mergeOpen = ref(false);
const mergeLoading = ref(false);
const filters = reactive({
  legacyMemberId: '',
  linkStatus: 'IDENTITY_CONFLICT',
  platformAccountId: '',
  reviewNo: '',
  sourceTenantId: '',
});
const mergeForm = reactive({
  reviewNo: '',
  reviewNote: '',
  targetPlatformAccountId: '',
});

const effectiveTenantId = computed(
  () => accessStore.visitTenantId ?? accessStore.tenantId,
);
const isPlatformTenant = computed(
  () => String(effectiveTenantId.value ?? '') === '1',
);
const canQuery = computed(
  () =>
    isPlatformTenant.value && hasAccessByCodes([QUERY_PERMISSION]),
);
const canReview = computed(
  () =>
    isPlatformTenant.value && hasAccessByCodes([REVIEW_PERMISSION]),
);
const blockedMessage = computed(() => {
  if (!isPlatformTenant.value) {
    return '平台账号人工审核仅允许租户 1 的平台管理员操作，请切回平台租户。';
  }
  if (!hasAccessByCodes([QUERY_PERMISSION])) {
    return `当前账号缺少 ${QUERY_PERMISSION} 权限，页面不会读取任何隔离身份。`;
  }
  return '';
});

function optionalId(value: string) {
  const result = value.trim();
  return result || undefined;
}

async function loadPage() {
  if (!canQuery.value) return;
  loading.value = true;
  try {
    const result = await getPlatformAccountLinkPage({
      legacyMemberId: optionalId(filters.legacyMemberId),
      linkStatus: filters.linkStatus || undefined,
      pageNo: pageNo.value,
      pageSize: pageSize.value,
      platformAccountId: optionalId(filters.platformAccountId),
      reviewNo: filters.reviewNo.trim() || undefined,
      sourceTenantId: optionalId(filters.sourceTenantId),
    });
    rows.value = result.list;
    total.value = result.total;
    selectedLinkIds.value = [];
  } catch (error) {
    console.error('[platform-account-review] page failed', error);
    rows.value = [];
    total.value = 0;
    message.error('平台账号链接读取失败，请检查权限或稍后重试');
  } finally {
    loading.value = false;
  }
}

function query() {
  pageNo.value = 1;
  void loadPage();
}

function reset() {
  Object.assign(filters, {
    legacyMemberId: '',
    linkStatus: 'IDENTITY_CONFLICT',
    platformAccountId: '',
    reviewNo: '',
    sourceTenantId: '',
  });
  query();
}

function onPageChange(current: number, size: number) {
  pageNo.value = current;
  pageSize.value = size;
  void loadPage();
}

function onSelectionChange(keys: MemberPlatformAccountReviewApi.Id[]) {
  selectedLinkIds.value = keys;
}

function openMerge() {
  if (!canReview.value) {
    message.error(`当前账号缺少 ${REVIEW_PERMISSION} 权限`);
    return;
  }
  if (selectedLinkIds.value.length === 0) {
    message.warning('请先选择至少一条待人工核验的冲突链接');
    return;
  }
  Object.assign(mergeForm, {
    reviewNo: '',
    reviewNote: '',
    targetPlatformAccountId: '',
  });
  mergeOpen.value = true;
}

async function submitMerge() {
  const reviewNo = mergeForm.reviewNo.trim();
  const reviewNote = mergeForm.reviewNote.trim();
  const targetPlatformAccountId = mergeForm.targetPlatformAccountId.trim();
  if (!/^[A-Za-z0-9][A-Za-z0-9._-]{5,63}$/.test(reviewNo)) {
    message.warning('审核单号需为 6 至 64 位字母、数字、点、下划线或短横线');
    return;
  }
  if (!/^\d+$/.test(targetPlatformAccountId)) {
    message.warning('请填写有效的目标平台账号 ID');
    return;
  }
  if (reviewNote.length < 10 || reviewNote.length > 500) {
    message.warning('人工核验依据摘要需为 10 至 500 个字符');
    return;
  }
  mergeLoading.value = true;
  try {
    const result = await mergePlatformAccountLinks({
      linkIds: selectedLinkIds.value,
      reviewNo,
      reviewNote,
      targetPlatformAccountId,
    });
    message.success(
      result.idempotentReplay
        ? `审核单 ${result.reviewNo} 已处理，本次为幂等回放`
        : `人工归并完成，共 ${result.readyLinkCount} 条有效链接`,
    );
    mergeOpen.value = false;
    await loadPage();
  } catch (error) {
    console.error('[platform-account-review] merge failed', error);
  } finally {
    mergeLoading.value = false;
  }
}

onMounted(() => {
  void loadPage();
});
</script>

<template>
  <Page auto-content-height>
    <Alert
      class="mb-3"
      show-icon
      type="warning"
      message="仅凭同手机号、登录名或历史密码散列不得认定为同一自然人。必须先完成线下身份核验，再使用唯一审核单号归并；租户 3000 的链接禁止转为 READY。"
    />
    <Alert
      v-if="blockedMessage"
      class="mb-3"
      show-icon
      type="error"
      :message="blockedMessage"
    />
    <Card v-else title="平台账号冲突人工审核">
      <div class="filters">
        <label>
          链接状态
          <Select v-model:value="filters.linkStatus" allow-clear placeholder="全部状态">
            <SelectOption
              v-for="item in statusOptions"
              :key="item.value"
              :value="item.value"
            >
              {{ item.label }}
            </SelectOption>
          </Select>
        </label>
        <label>
          来源租户 ID
          <Input v-model:value="filters.sourceTenantId" allow-clear placeholder="精确查询" />
        </label>
        <label>
          平台账号 ID
          <Input v-model:value="filters.platformAccountId" allow-clear placeholder="精确查询" />
        </label>
        <label>
          旧会员 ID
          <Input v-model:value="filters.legacyMemberId" allow-clear placeholder="精确查询" />
        </label>
        <label>
          审核单号
          <Input v-model:value="filters.reviewNo" allow-clear placeholder="精确查询" />
        </label>
        <Space class="actions">
          <Button type="primary" :loading="loading" @click="query">查询</Button>
          <Button @click="reset">重置</Button>
          <Button
            danger
            :disabled="!canReview || selectedLinkIds.length === 0"
            @click="openMerge"
          >
            人工确认并归并（{{ selectedLinkIds.length }}）
          </Button>
        </Space>
      </div>

      <Table
        :columns="columns"
        :data-source="rows"
        :loading="loading"
        :pagination="false"
        :row-selection="{
          selectedRowKeys: selectedLinkIds,
          onChange: onSelectionChange,
          getCheckboxProps: (record: MemberPlatformAccountReviewApi.LinkRow) => ({
            disabled: !['IDENTITY_CONFLICT', 'CREDENTIAL_CONFLICT'].includes(record.linkStatus),
          }),
        }"
        row-key="linkId"
        :scroll="{ x: 1800 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'identity'">
            <div>{{ record.maskedLoginName || '未设置登录名' }}</div>
            <div class="muted">{{ record.maskedMobile || '未设置手机号' }}</div>
          </template>
          <template v-else-if="column.key === 'platformAccount'">
            <div>{{ record.platformAccountId || '未关联（隔离）' }}</div>
            <div v-if="record.platformNo" class="muted">{{ record.platformNo }}</div>
          </template>
          <Tag
            v-else-if="column.key === 'status'"
            :color="statusColors[record.linkStatus] || 'default'"
          >
            {{ statusLabels[record.linkStatus] || record.linkStatus }}
          </Tag>
          <template v-else-if="column.key === 'review'">
            <div>{{ record.identityReviewNo || '未人工审核' }}</div>
            <div v-if="record.identityVerifiedBy" class="muted">
              {{ record.identityVerifiedBy }} · {{ record.identityVerifiedAt || '-' }}
            </div>
          </template>
        </template>
      </Table>
      <Pagination
        v-model:current="pageNo"
        v-model:page-size="pageSize"
        class="pager"
        :total="total"
        :show-size-changer="true"
        :page-size-options="['10', '20', '50']"
        @change="onPageChange"
        @show-size-change="onPageChange"
      />
    </Card>

    <Modal
      v-model:open="mergeOpen"
      title="人工确认平台身份归并"
      :confirm-loading="mergeLoading"
      :mask-closable="false"
      ok-text="确认归并"
      @ok="submitMerge"
    >
      <Alert
        class="mb-4"
        show-icon
        type="error"
        :message="`将 ${selectedLinkIds.length} 条隔离链接归到一个已有且安全的目标平台账号。提交后不可在此页面撤销。`"
      />
      <Form layout="vertical">
        <FormItem label="目标平台账号 ID" required>
          <Input
            v-model:value="mergeForm.targetPlatformAccountId"
            placeholder="填写已存在且具有 READY 主链接的平台账号 ID"
          />
        </FormItem>
        <FormItem label="线下人工审核单号" required>
          <Input v-model:value="mergeForm.reviewNo" placeholder="例如 IDR-20260921-0001" />
        </FormItem>
        <FormItem label="人工核验依据摘要" required>
          <Input.TextArea
            v-model:value="mergeForm.reviewNote"
            :maxlength="500"
            :rows="4"
            show-count
            placeholder="填写核验材料、复核过程及结论；不得填写密码、验证码或其他秘密凭据"
          />
        </FormItem>
      </Form>
    </Modal>
  </Page>
</template>

<style scoped>
.filters {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
  align-items: end;
  margin-bottom: 16px;
}

.filters label {
  display: grid;
  gap: 6px;
  color: #475569;
}

.actions {
  grid-column: 1 / -1;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.muted {
  margin-top: 3px;
  color: #64748b;
  font-size: 12px;
}
</style>
