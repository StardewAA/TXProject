<script lang="ts" setup>
import type { HrmSalaryPieceRecordApi } from '#/api/hrm/salary/piece-record';

import { computed, onMounted, ref } from 'vue';

import { useAccess } from '@vben/access';
import { Page } from '@vben/common-ui';

import {
  Button,
  Card,
  DatePicker,
  Form,
  FormItem,
  Input,
  InputNumber,
  message,
  Modal,
  Popconfirm,
  Select,
  Table,
} from 'ant-design-vue';
import { getEmployeeSimplePage } from '#/api/hrm/employee';
import {
  deletePieceRecord,
  getPieceRecordPage,
  savePieceRecord,
} from '#/api/hrm/salary/piece-record';

defineOptions({ name: 'HrmSalaryPieceRecord' });

const { hasAccessByCodes } = useAccess();
const canEdit = () => hasAccessByCodes(['hrm:salary:piece-record:update']);

const loading = ref(false);
const list = ref<HrmSalaryPieceRecordApi.PieceRecord[]>([]);
const total = ref(0);
const employeeOptions = ref<Array<{ label: string; value: number }>>([]);

/** 查询条件：默认当月（不引入 dayjs，避免与 vben 的 dayjs 实例/locale 冲突导致 DatePicker 报错） */
const now = new Date();
const query = ref({
  pageNo: 1,
  pageSize: 10,
  year: now.getFullYear() as number | undefined,
  month: (now.getMonth() + 1) as number | undefined,
  employeeId: undefined as number | undefined,
  itemName: '',
});
/** 查询用月份（YYYY-MM 字符串） */
const queryMonth = ref<string>(
  `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`,
);

/** 弹窗表单 */
const modalOpen = ref(false);
const saving = ref(false);
const formRef = ref();
const formState = ref<HrmSalaryPieceRecordApi.PieceRecord>({});
/** 弹窗月份（YYYY-MM 字符串） */
const monthValue = ref<string>(queryMonth.value);

/** 实时预览：工程量 × 单价 */
const previewAmount = computed(() => {
  const quantity = Number(formState.value.quantity || 0);
  const unitPrice = Number(formState.value.unitPrice || 0);
  return (quantity * unitPrice).toFixed(2);
});

/** 当前页合计（便于 HR 核对） */
const pageTotal = computed(() =>
  list.value.reduce((sum, item) => sum + Number(item.amount || 0), 0).toFixed(2),
);

const rules = {
  employeeId: [{ required: true, message: '请选择员工', trigger: 'change' }],
  itemName: [{ required: true, message: '请填写工程量/工序名称', trigger: 'blur' }],
  quantity: [{ required: true, message: '请填写完成工程量', trigger: 'blur' }],
  unitPrice: [{ required: true, message: '请填写计件单价', trigger: 'blur' }],
};

async function getList() {
  loading.value = true;
  try {
    const page = await getPieceRecordPage({
      pageNo: query.value.pageNo,
      pageSize: query.value.pageSize,
      year: query.value.year,
      month: query.value.month,
      employeeId: query.value.employeeId,
      itemName: query.value.itemName || undefined,
    });
    list.value = page.list || [];
    total.value = page.total || 0;
  } finally {
    loading.value = false;
  }
}

async function loadEmployees() {
  // 后端限制每页最大 200 条
  const page = await getEmployeeSimplePage({ pageNo: 1, pageSize: 200 });
  employeeOptions.value = (page.list || []).map((item: any) => ({
    label: `${item.name}${item.jobNumber ? `（${item.jobNumber}）` : ''}`,
    value: item.id,
  }));
}

function openCreate() {
  formState.value = {};
  monthValue.value = queryMonth.value;
  modalOpen.value = true;
}

function openEdit(record: HrmSalaryPieceRecordApi.PieceRecord) {
  formState.value = { ...record };
  monthValue.value = `${record.year}-${String(record.month).padStart(2, '0')}`;
  modalOpen.value = true;
}

async function handleSubmit() {
  await formRef.value?.validate();
  const ym = monthValue.value;
  if (!ym) {
    message.warning('请选择计件月份');
    return;
  }
  saving.value = true;
  try {
    await savePieceRecord({
      ...formState.value,
      year: Number(ym.slice(0, 4)),
      month: Number(ym.slice(5, 7)),
    });
    message.success('已保存（计件金额由系统按 工程量 × 单价 计算）');
    modalOpen.value = false;
    await getList();
  } finally {
    saving.value = false;
  }
}

async function handleDelete(id?: number) {
  if (!id) return;
  await deletePieceRecord(id);
  message.success('已删除');
  await getList();
}

onMounted(async () => {
  await loadEmployees();
  await getList();
});
</script>

<template>
  <Page auto-content-height>
    <Card :bordered="false">
      <div class="mb-3 flex flex-wrap items-center gap-2">
        <Input
          v-model:value="query.itemName"
          allow-clear
          class="w-52"
          placeholder="工程量/工序名称"
        />
        <Select
          v-model:value="query.employeeId"
          :options="employeeOptions"
          allow-clear
          class="w-56"
          placeholder="选择员工"
          show-search
          :filter-option="
            (input: string, option: any) =>
              String(option.label).toLowerCase().includes(input.toLowerCase())
          "
        />
        <DatePicker
          v-model:value="queryMonth as any"
          picker="month"
          value-format="YYYY-MM"
          class="w-40"
          @change="
            (_: any, ds: any) => {
              const value = String(ds || '');
              if (value) {
                query.year = Number(value.slice(0, 4));
                query.month = Number(value.slice(5, 7));
              }
            }
          "
        />
        <Button type="primary" @click="getList">查询</Button>
        <span class="text-muted-foreground text-xs">
          计件工资 = 完成工程量 × 计件单价，核算工资表时自动带入「计件工资」项
        </span>
        <div class="ml-auto flex items-center gap-2">
          <span class="text-sm">
            本页合计：<b class="text-blue-600">{{ pageTotal }}</b> 元
          </span>
          <Button v-if="canEdit()" type="primary" @click="openCreate">
            新增计件明细
          </Button>
        </div>
      </div>

      <Table
        :columns="[
          { title: '员工', key: 'employee', width: 150 },
          { title: '工号', dataIndex: 'jobNumber', width: 120 },
          { title: '年月', key: 'ym', width: 100 },
          { title: '工程量/工序', dataIndex: 'itemName', width: 200 },
          { title: '单位', dataIndex: 'unit', width: 80 },
          { title: '完成工程量', dataIndex: 'quantity', width: 120 },
          { title: '计件单价', dataIndex: 'unitPrice', width: 110 },
          { title: '计件金额', dataIndex: 'amount', width: 120 },
          { title: '备注', dataIndex: 'remark' },
          { title: '操作', key: 'action', width: 130, fixed: 'right' },
        ]"
        :data-source="list"
        :loading="loading"
        :pagination="{
          current: query.pageNo,
          pageSize: query.pageSize,
          total,
          showSizeChanger: true,
          onChange: (page: number, size: number) => {
            query.pageNo = page;
            query.pageSize = size;
            getList();
          },
        }"
        :row-key="(r: HrmSalaryPieceRecordApi.PieceRecord) => String(r.id)"
        :scroll="{ x: 1300 }"
        bordered
        size="small"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'employee'">
            {{ record.employeeName || `员工${record.employeeId}` }}
          </template>
          <template v-else-if="column.key === 'ym'">
            {{ record.year }}-{{ String(record.month).padStart(2, '0') }}
          </template>
          <template v-else-if="column.key === 'action'">
            <template v-if="canEdit()">
              <Button type="link" @click="openEdit(record)">编辑</Button>
              <Popconfirm
                title="确认删除该计件明细吗？"
                @confirm="handleDelete(record.id)"
              >
                <Button danger type="link">删除</Button>
              </Popconfirm>
            </template>
            <span v-else>-</span>
          </template>
        </template>
      </Table>
    </Card>

    <Modal
      v-model:open="modalOpen"
      :confirm-loading="saving"
      :title="formState.id ? '编辑计件明细' : '新增计件明细'"
      width="620px"
      @ok="handleSubmit"
    >
      <Form
        ref="formRef"
        :label-col="{ span: 6 }"
        :model="formState"
        :rules="rules"
        class="mt-4"
      >
        <FormItem label="员工" name="employeeId">
          <Select
            v-model:value="formState.employeeId"
            :options="employeeOptions"
            allow-clear
            placeholder="请选择员工"
            show-search
          />
        </FormItem>
        <FormItem label="计件月份" required>
          <DatePicker
            v-model:value="monthValue as any"
            class="w-full"
            picker="month"
            value-format="YYYY-MM"
          />
        </FormItem>
        <FormItem label="工程量/工序" name="itemName">
          <Input
            v-model:value="formState.itemName"
            placeholder="如：管道保温-岩棉 / 设备除锈-喷砂"
          />
        </FormItem>
        <FormItem label="单位">
          <Input v-model:value="formState.unit" placeholder="如：㎡ / 吨 / 延米 / 件" />
        </FormItem>
        <FormItem label="完成工程量" name="quantity">
          <InputNumber
            v-model:value="formState.quantity as any"
            :min="0"
            class="w-full"
            placeholder="工程量"
          />
        </FormItem>
        <FormItem label="计件单价" name="unitPrice">
          <InputNumber
            v-model:value="formState.unitPrice as any"
            :min="0"
            class="w-full"
            placeholder="元"
          />
        </FormItem>
        <FormItem label="计件金额">
          <span class="text-blue-600 font-medium">{{ previewAmount }} 元</span>
          <span class="text-muted-foreground ml-2 text-xs">
            （系统按 工程量 × 单价 计算，保存后以此为准）
          </span>
        </FormItem>
        <FormItem label="备注">
          <Input.TextArea v-model:value="formState.remark" :rows="2" />
        </FormItem>
      </Form>
    </Modal>
  </Page>
</template>
