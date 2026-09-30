<script lang="ts" setup>
import type { HrmContractSignRecordApi } from '#/api/hrm/contract/sign-record';
import type { TableColumnsType } from 'ant-design-vue';
import type { Rule } from 'ant-design-vue/es/form';

import { onMounted, reactive, ref } from 'vue';

import { useAccess } from '@vben/access';
import {
  downloadFileFromUrl,
  formatDate,
  getFileNameFromUrl,
} from '@vben/utils';

import {
  Button,
  Card,
  DatePicker,
  Form,
  FormItem,
  Input,
  message,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
} from 'ant-design-vue';

import {
  createContractSignRecord,
  deleteContractSignRecord,
  getContractSignRecordPage,
  updateContractSignRecord,
  urgeContractSignRecords,
} from '#/api/hrm/contract/sign-record';
import { getEmployeeContractList } from '#/api/hrm/employee/contract';
import { getContractStatisticsSummary } from '#/api/hrm/employee/contract';
import { getEmployeeSimplePage } from '#/api/hrm/employee';
import { exportHrmTable } from '#/views/hrm/utils/export-table';
import { FileUpload } from '#/components/upload';
import { useHrmCommonStore } from '#/views/hrm/store/common';

defineOptions({ name: 'HrmContractSignRecord' });

const { hasAccessByCodes } = useAccess();
// 用工主体列表走公共 store 缓存（F-06）
const hrmCommonStore = useHrmCommonStore();
const canCreate = hasAccessByCodes(['hrm:contract:sign:create']);
const canUpdate = hasAccessByCodes(['hrm:contract:sign:update']);
const canDelete = hasAccessByCodes(['hrm:contract:sign:delete']);
const canUrge = hasAccessByCodes(['hrm:contract:sign:urge']);
const exportLoading = ref(false);

const STATUS_OPTIONS = [
  { label: '待签', value: 0 },
  { label: '已签', value: 1 },
  { label: '已作废', value: 2 },
];
const WAY_OPTIONS = [
  { label: '线下签署', value: 1 },
  { label: '电子签章', value: 2 },
];
const statusLabelMap = new Map(STATUS_OPTIONS.map((item) => [item.value, item.label]));
const statusColorMap = new Map([
  [0, 'orange'],
  [1, 'green'],
  [2, 'default'],
]);
const wayLabelMap = new Map(WAY_OPTIONS.map((item) => [item.value, item.label]));

const loading = ref(false);
const saving = ref(false);
const list = ref<HrmContractSignRecordApi.ContractSignRecord[]>([]);
const total = ref(0);
const employeeOptions = ref<Array<{ label: string; value: number }>>([]);
const employerOptions = ref<Array<{ label: string; value: number }>>([]);
const contractOptions = ref<Array<{ label: string; value: number }>>([]);
const selectedRowKeys = ref<number[]>([]);
const modalOpen = ref(false);
const isEdit = ref(false);
const formRef = ref();
const formState = reactive<Record<string, any>>({});
const uploadUrls = ref<string[]>([]);

const queryParams = reactive<{
  contractNo?: string;
  employeeId?: number;
  pageNo: number;
  pageSize: number;
  signWay?: number;
  status?: number;
}>({ pageNo: 1, pageSize: 10 });

const columns: TableColumnsType = [
  { title: '合同编号', dataIndex: 'contractNo', width: 200 },
  { title: '员工姓名', dataIndex: 'employeeName', width: 100 },
  { title: '工号', dataIndex: 'jobNumber', width: 110 },
  { title: '签署用工主体', dataIndex: 'signEmployerName', width: 200 },
  { title: '签署人', dataIndex: 'signPerson', width: 100 },
  { title: '签署方式', dataIndex: 'signWay', width: 100 },
  { title: '签署时间', dataIndex: 'signTime', width: 170 },
  { title: '状态', dataIndex: 'status', width: 90 },
  { title: '催办次数', dataIndex: 'urgeCount', width: 90 },
  { title: '签署件', dataIndex: 'fileUrls', width: 120 },
  { title: '操作', dataIndex: 'action', width: 180, fixed: 'right' },
];

const rules: Record<string, Rule[]> = {
  contractId: [{ required: true, message: '请选择合同', trigger: 'change' }],
};

async function getList() {
  loading.value = true;
  try {
    const result = await getContractSignRecordPage({ ...queryParams });
    list.value = result.list ?? [];
    total.value = result.total ?? 0;
  } finally {
    loading.value = false;
  }
}

/**
 * 导出当前筛选条件下的合同签署记录（Q-021 / I-013）
 *
 * 签署台账此前只能翻页核对，导出后可直接与纸质/电子签署件对账。
 */
async function handleExport() {
  exportLoading.value = true;
  try {
    const count =
      await exportHrmTable<HrmContractSignRecordApi.ContractSignRecord>({
        columns: [
          { title: '合同编号', value: (row) => row.contractNo },
          { title: '员工姓名', value: (row) => row.employeeName },
          { title: '工号', value: (row) => row.jobNumber },
          { title: '签署用工主体', value: (row) => row.signEmployerName },
          { title: '签署人', value: (row) => row.signPerson },
          {
            title: '签署方式',
            value: (row) => (row.signWay === 2 ? '电子签章' : '线下签署'),
          },
          { title: '签署时间', value: (row) => formatDate(row.signTime) },
          {
            title: '状态',
            value: (row) =>
              row.status === 1 ? '已签' : row.status === 2 ? '已作废' : '待签',
          },
          { title: '催办次数', value: (row) => row.urgeCount },
          { title: '备注', value: (row) => row.remark },
        ],
        fetchPage: (pageNo, pageSize) =>
          getContractSignRecordPage({ ...queryParams, pageNo, pageSize }),
        fileName: '合同签署记录',
      });
    message.success(`已导出 ${count} 条签署记录`);
  } finally {
    exportLoading.value = false;
  }
}

async function getEmployeeOptions(search?: string) {
  const result = await getEmployeeSimplePage({
    pageNo: 1,
    pageSize: 50,
    search,
  } as any);
  employeeOptions.value = (result.list ?? []).map((item: any) => ({
    label: `${item.name ?? ''} ${item.jobNumber ?? ''}`.trim(),
    value: item.id as number,
  }));
}

async function getEmployerOptions() {
  const profiles = (await hrmCommonStore.getCompanyProfiles()) ?? [];
  employerOptions.value = profiles
    .filter((item) => item.id !== undefined)
    .map((item) => ({ label: item.name as string, value: item.id as number }));
}

async function loadContractOptions(employeeId?: number) {
  contractOptions.value = [];
  if (!employeeId) return;
  const contracts = (await getEmployeeContractList(employeeId)) ?? [];
  contractOptions.value = contracts
    .filter((item) => item.id !== undefined)
    .map((item) => ({
      label: `${item.no ?? '未编号'}（${item.startTime ? '已设起止' : '未设起止'}）`,
      value: item.id as number,
    }));
}

function handleEmployeeChange(employeeId: number) {
  loadContractOptions(employeeId);
}

function resetForm() {
  Object.keys(formState).forEach((key) => delete formState[key]);
  Object.assign(formState, { status: 0, signWay: 1 });
  uploadUrls.value = [];
}

function handleCreate() {
  isEdit.value = false;
  resetForm();
  contractOptions.value = [];
  modalOpen.value = true;
}

function handleEdit(record: HrmContractSignRecordApi.ContractSignRecord) {
  isEdit.value = true;
  Object.keys(formState).forEach((key) => delete formState[key]);
  Object.assign(formState, { ...record });
  uploadUrls.value = record.fileUrls ?? [];
  loadContractOptions(record.employeeId);
  modalOpen.value = true;
}

async function handleSubmit() {
  await formRef.value?.validate();
  if (formState.status === 1 && uploadUrls.value.length === 0) {
    message.warning('签署状态为「已签」时必须上传签署件');
    return;
  }
  saving.value = true;
  try {
    const payload = { ...formState, fileUrls: uploadUrls.value };
    if (isEdit.value) {
      await updateContractSignRecord(payload);
    } else {
      await createContractSignRecord(payload);
    }
    message.success('保存成功');
    modalOpen.value = false;
    await getList();
  } finally {
    saving.value = false;
  }
}

async function handleUrge(ids: number[]) {
  if (ids.length === 0) {
    message.warning('请先勾选待签署的记录');
    return;
  }
  const count = await urgeContractSignRecords(ids);
  message.success(`已催办 ${count} 条`);
  selectedRowKeys.value = [];
  await getList();
}

/** 合同看板数字（B3）：待签署 / 已签署 / 30 天内到期 */
const summary = ref({ expireIn30DaysCount: 0, pendingSignCount: 0, signedCount: 0 });
async function loadSummary() {
  try {
    const data = await getContractStatisticsSummary();
    if (data) summary.value = data;
  } catch {
    // 看板失败不影响主列表
  }
}

async function handleDelete(id?: number) {
  if (!id) return;
  await deleteContractSignRecord(id);
  message.success('删除成功');
  await getList();
}

function handleDownloadFile(url: string) {
  downloadFileFromUrl({ source: url, fileName: getFileNameFromUrl(url) });
}

/**
 * 签署时间格式化：后端返回毫秒时间戳，直接渲染会显示成 1790146669000
 * 这种没人看得懂的数字；未签署（null / 0 / 1970 纪元值）统一显示 '-'。
 */
function formatSignTime(value?: null | number | string) {
  if (value === null || value === undefined || value === '') {
    return '-';
  }
  const timestamp = Number(value);
  if (Number.isFinite(timestamp) && timestamp <= 0) {
    return '-';
  }
  const text = formatDate(value, 'YYYY-MM-DD HH:mm');
  return !text || text.startsWith('1970-01-01') ? '-' : text;
}

function handleReset() {
  Object.assign(queryParams, {
    pageNo: 1,
    pageSize: 10,
    contractNo: undefined,
    employeeId: undefined,
    signWay: undefined,
    status: undefined,
  });
  getList();
}

function handleTableChange(pagination: any) {
  queryParams.pageNo = pagination.current;
  queryParams.pageSize = pagination.pageSize;
  getList();
}

onMounted(async () => {
  await Promise.all([
    getList(),
    getEmployeeOptions(),
    getEmployerOptions(),
    loadSummary(),
  ]);
});
</script>

<template>
  <Card title="合同签署记录">
    <!-- 合同看板（B3）：待签署 / 已签署 / 30 天内到期，点击可快速定位 -->
    <div class="mb-4 grid grid-cols-1 gap-3 md:grid-cols-3">
      <Card size="small" class="border-l-4 border-l-orange-400">
        <div class="text-muted-foreground text-sm">待签署</div>
        <div class="mt-1 text-2xl font-semibold text-orange-500">
          {{ summary.pendingSignCount }}
        </div>
      </Card>
      <Card size="small" class="border-l-4 border-l-green-500">
        <div class="text-muted-foreground text-sm">已签署</div>
        <div class="mt-1 text-2xl font-semibold text-green-600">
          {{ summary.signedCount }}
        </div>
      </Card>
      <Card size="small" class="border-l-4 border-l-red-400">
        <div class="text-muted-foreground text-sm">30 天内到期</div>
        <div class="mt-1 text-2xl font-semibold text-red-500">
          {{ summary.expireIn30DaysCount }}
        </div>
      </Card>
    </div>
    <Form layout="inline" :style="{ marginBottom: '12px', rowGap: '8px' }">
      <FormItem label="合同编号">
        <Input
          v-model:value="queryParams.contractNo"
          allow-clear
          placeholder="如 HT-OFF"
          @press-enter="getList"
        />
      </FormItem>
      <FormItem label="员工">
        <Select
          v-model:value="queryParams.employeeId"
          :allow-clear="true"
          :filter-option="false"
          :options="employeeOptions"
          placeholder="选择员工"
          show-search
          style="width: 200px"
          @search="getEmployeeOptions"
        />
      </FormItem>
      <FormItem label="状态">
        <Select
          v-model:value="queryParams.status"
          :allow-clear="true"
          :options="STATUS_OPTIONS"
          placeholder="全部"
          style="width: 120px"
        />
      </FormItem>
      <FormItem label="签署方式">
        <Select
          v-model:value="queryParams.signWay"
          :allow-clear="true"
          :options="WAY_OPTIONS"
          placeholder="全部"
          style="width: 140px"
        />
      </FormItem>
      <FormItem>
        <Space>
          <Button type="primary" @click="getList">查询</Button>
          <Button @click="handleReset">重置</Button>
          <Button type="primary" :disabled="!canCreate" @click="handleCreate">
            新增签署记录
          </Button>
          <Button
            :disabled="!canUrge || selectedRowKeys.length === 0"
            @click="handleUrge(selectedRowKeys)"
          >
            批量催办
          </Button>
          <Button :loading="exportLoading" @click="handleExport">
            导出签署记录
          </Button>
        </Space>
      </FormItem>
    </Form>

    <Table
      :columns="columns"
      :data-source="list"
      :loading="loading"
      :pagination="{
        current: queryParams.pageNo,
        pageSize: queryParams.pageSize,
        total,
        showSizeChanger: true,
      }"
      :row-selection="{ selectedRowKeys, onChange: (keys: any) => (selectedRowKeys = keys) }"
      :scroll="{ x: 1500 }"
      row-key="id"
      size="small"
      @change="handleTableChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'signWay'">
          {{ wayLabelMap.get(record.signWay as number) ?? '-' }}
        </template>
        <template v-else-if="column.dataIndex === 'signTime'">
          {{ formatSignTime(record.signTime) }}
        </template>
        <template v-else-if="column.dataIndex === 'status'">
          <Tag :color="statusColorMap.get(record.status as number)">
            {{ statusLabelMap.get(record.status as number) ?? '-' }}
          </Tag>
        </template>
        <template v-else-if="column.dataIndex === 'fileUrls'">
          <Space v-if="record.fileUrls && record.fileUrls.length > 0" wrap>
            <Button
              v-for="(url, index) in record.fileUrls"
              :key="url"
              type="link"
              size="small"
              @click="handleDownloadFile(url)"
            >
              附件{{ Number(index) + 1 }}
            </Button>
          </Space>
          <span v-else>-</span>
        </template>
        <template v-else-if="column.dataIndex === 'action'">
          <Space>
            <Button
              type="link"
              size="small"
              :disabled="!canUpdate"
              @click="handleEdit(record)"
            >
              编辑
            </Button>
            <Button
              type="link"
              size="small"
              :disabled="!canUrge || record.status !== 0"
              @click="handleUrge([record.id as number])"
            >
              催办
            </Button>
            <Popconfirm
              title="确认删除该签署记录？"
              :disabled="!canDelete"
              @confirm="handleDelete(record.id)"
            >
              <Button type="link" size="small" danger :disabled="!canDelete">
                删除
              </Button>
            </Popconfirm>
          </Space>
        </template>
      </template>
    </Table>

    <Modal
      v-model:open="modalOpen"
      :title="isEdit ? '编辑签署记录' : '新增签署记录'"
      :confirm-loading="saving"
      width="680px"
      @ok="handleSubmit"
    >
      <Form ref="formRef" :model="formState" :rules="rules" :label-col="{ span: 6 }">
        <FormItem label="员工">
          <Select
            v-model:value="formState.employeeId"
            :filter-option="false"
            :options="employeeOptions"
            :disabled="isEdit"
            placeholder="先选择员工，再选择其合同"
            show-search
            @change="(value) => handleEmployeeChange(Number(value))"
            @search="getEmployeeOptions"
          />
        </FormItem>
        <FormItem label="合同" name="contractId">
          <Select
            v-model:value="formState.contractId"
            :options="contractOptions"
            placeholder="请选择该员工名下的合同"
          />
        </FormItem>
        <FormItem label="签署用工主体">
          <Select
            v-model:value="formState.signEmployerId"
            :allow-clear="true"
            :options="employerOptions"
            placeholder="留空则沿用合同签约主体"
          />
        </FormItem>
        <FormItem label="签署人">
          <Input v-model:value="formState.signPerson" placeholder="乙方签署人姓名" />
        </FormItem>
        <FormItem label="签署方式">
          <Select v-model:value="formState.signWay" :options="WAY_OPTIONS" />
        </FormItem>
        <FormItem label="签署时间">
          <DatePicker
            v-model:value="formState.signTime"
            show-time
            style="width: 100%"
            value-format="YYYY-MM-DD HH:mm:ss"
          />
        </FormItem>
        <FormItem label="签署状态">
          <Select v-model:value="formState.status" :options="STATUS_OPTIONS" />
        </FormItem>
        <FormItem label="签署件">
          <FileUpload v-model="uploadUrls" :max-number="10" />
        </FormItem>
        <FormItem label="备注">
          <Input v-model:value="formState.remark" />
        </FormItem>
      </Form>
    </Modal>
  </Card>
</template>
