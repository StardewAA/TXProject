<script lang="ts" setup>
import type { HrmEmployeeQuitHandoverApi } from '#/api/hrm/employee/quit-handover';

import { h, onMounted, ref } from 'vue';

import { useAccess } from '@vben/access';
import { useVbenForm, useVbenModal } from '@vben/common-ui';

import {
  Button,
  Card,
  Descriptions,
  message,
  Modal,
  Popconfirm,
  Select,
  Table,
  Tag,
} from 'ant-design-vue';

import { getEmployeeSimplePage } from '#/api/hrm/employee';
import {
  confirmQuitHandover,
  deleteQuitHandoverItem,
  getQuitHandoverProgress,
  HrmQuitHandoverItemStatus,
  HrmQuitHandoverItemStatusOptions,
  HrmQuitHandoverItemTypeOptions,
  saveQuitHandoverItem,
  settleQuitSalary,
  waiveQuitHandover,
} from '#/api/hrm/employee/quit-handover';
import { formatHrmDateTime } from '#/views/hrm/utils/format';

defineOptions({ name: 'HrmEmployeeQuitHandover' });

const props = defineProps<{ employeeId: number }>();
const emit = defineEmits(['refresh']);
const { hasAccessByCodes } = useAccess();

const loading = ref(false);
const progress = ref<HrmEmployeeQuitHandoverApi.HandoverProgress>();
const employeeOptions = ref<Array<{ label: string; value: number }>>([]);

const canEdit = () => hasAccessByCodes(['hrm:employee:update']);

async function getProgress() {
  loading.value = true;
  try {
    progress.value = await getQuitHandoverProgress(props.employeeId);
  } finally {
    loading.value = false;
  }
}

async function loadEmployees() {
  const page = await getEmployeeSimplePage({ pageNo: 1, pageSize: 200 });
  employeeOptions.value = (page.list || []).map((item: any) => ({
    label: `${item.name}${item.jobNumber ? `（${item.jobNumber}）` : ''}`,
    value: item.id,
  }));
}

/** 交接明细表单 */
const [ItemForm, itemFormApi] = useVbenForm({
  commonConfig: { labelWidth: 92, componentProps: { class: 'w-full' } },
  layout: 'horizontal',
  schema: [
    {
      fieldName: 'employeeId',
      component: 'Input',
      dependencies: { triggerFields: [''], show: () => false },
    },
    {
      fieldName: 'id',
      component: 'Input',
      dependencies: { triggerFields: [''], show: () => false },
    },
    {
      fieldName: 'itemType',
      label: '物品类型',
      component: 'Select',
      rules: 'required',
      defaultValue: 1,
      componentProps: {
        options: [...HrmQuitHandoverItemTypeOptions],
        class: 'w-full',
      },
    },
    {
      fieldName: 'itemName',
      label: '物品名称',
      component: 'Input',
      rules: 'required',
      componentProps: { placeholder: '如：电焊机 / 安全帽 / 门禁卡' },
    },
    {
      fieldName: 'quantity',
      label: '数量',
      component: 'InputNumber',
      defaultValue: 1,
      componentProps: { min: 0, class: 'w-full' },
    },
    {
      fieldName: 'unit',
      label: '单位',
      component: 'Input',
      componentProps: { placeholder: '如：台 / 个 / 套' },
    },
    {
      fieldName: 'status',
      label: '交接状态',
      component: 'Select',
      defaultValue: HrmQuitHandoverItemStatus.PENDING,
      componentProps: {
        options: [...HrmQuitHandoverItemStatusOptions],
        class: 'w-full',
      },
    },
    {
      fieldName: 'remark',
      label: '备注',
      component: 'Textarea',
      componentProps: { rows: 2 },
    },
  ],
  showDefaultActions: false,
  wrapperClass: 'grid-cols-1 md:grid-cols-2',
});

const [ItemModal, itemModalApi] = useVbenModal({
  async onConfirm() {
    const { valid } = await itemFormApi.validate();
    if (!valid) return;
    itemModalApi.lock();
    try {
      await saveQuitHandoverItem({
        ...(await itemFormApi.getValues()),
        employeeId: props.employeeId,
      });
      message.success('交接明细已保存');
      await itemModalApi.close();
      await getProgress();
      emit('refresh');
    } finally {
      itemModalApi.unlock();
    }
  },
  async onOpenChange(isOpen: boolean) {
    if (!isOpen) return;
    const data = itemModalApi.getData<HrmEmployeeQuitHandoverApi.HandoverItem>();
    await itemFormApi.resetForm();
    itemModalApi.setState({ title: data?.id ? '编辑交接明细' : '新增交接明细' });
    if (data?.id) {
      await itemFormApi.setValues({ ...data, employeeId: props.employeeId });
    }
  },
});

/** 无需交接原因 */
const [WaiveForm, waiveFormApi] = useVbenForm({
  commonConfig: { labelWidth: 0, componentProps: { class: 'w-full' } },
  layout: 'vertical',
  schema: [
    {
      fieldName: 'remark',
      label: '无需交接原因',
      component: 'Textarea',
      rules: 'required',
      componentProps: {
        rows: 3,
        placeholder: '如：非现场岗位，无公司工器具与证件',
      },
    },
  ],
  showDefaultActions: false,
});

const [WaiveModal, waiveModalApi] = useVbenModal({
  async onConfirm() {
    const { valid } = await waiveFormApi.validate();
    if (!valid) return;
    waiveModalApi.lock();
    try {
      const { remark } = await waiveFormApi.getValues();
      await waiveQuitHandover({ employeeId: props.employeeId, remark });
      message.success('已标记为无需交接');
      await waiveModalApi.close();
      await getProgress();
      emit('refresh');
    } finally {
      waiveModalApi.unlock();
    }
  },
  async onOpenChange(isOpen: boolean) {
    if (!isOpen) return;
    waiveModalApi.setState({ title: '标记为无需交接' });
    await waiveFormApi.resetForm();
  },
});

/** 工资结清确认：可同步调整计薪截止日 */
const [SettleForm, settleFormApi] = useVbenForm({
  commonConfig: { labelWidth: 92, componentProps: { class: 'w-full' } },
  layout: 'horizontal',
  schema: [
    {
      fieldName: 'salaryCutoffDate',
      label: '计薪截止日',
      component: 'DatePicker',
      componentProps: {
        format: 'YYYY-MM-DD',
        valueFormat: 'YYYY-MM-DD',
        class: 'w-full',
      },
      help: '工资算到该日期为止；默认等于离职生效日，可按班组结算周期提前封账',
    },
    {
      fieldName: 'salarySettleRemark',
      label: '结算说明',
      component: 'Textarea',
      componentProps: { rows: 3, placeholder: '如：含 10 月工资及借支冲抵，已由财务打款' },
    },
  ],
  showDefaultActions: false,
});

const [SettleModal, settleModalApi] = useVbenModal({
  async onConfirm() {
    const { valid } = await settleFormApi.validate();
    if (!valid) return;
    settleModalApi.lock();
    try {
      const values = await settleFormApi.getValues();
      await settleQuitSalary({
        employeeId: props.employeeId,
        salaryCutoffDate: values.salaryCutoffDate || undefined,
        salarySettleRemark: values.salarySettleRemark || undefined,
      });
      message.success('已确认工资结清');
      await settleModalApi.close();
      await getProgress();
      emit('refresh');
    } finally {
      settleModalApi.unlock();
    }
  },
  async onOpenChange(isOpen: boolean) {
    if (!isOpen) return;
    settleModalApi.setState({ title: '确认离职工资结清' });
    await settleFormApi.resetForm();
    await settleFormApi.setValues({
      salaryCutoffDate: progress.value?.salaryCutoffDate || undefined,
      salarySettleRemark: progress.value?.salarySettleRemark || undefined,
    });
  },
});

/** 确认交接（可选填交接人） */
async function handleConfirm() {
  let handoverToEmployeeId: number | undefined;
  Modal.confirm({
    title: '确认离职交接完成',
    content: () => {
      return h(
        'div',
        { class: 'flex flex-col gap-2' },
        [
          h(
            'div',
            { class: 'text-xs text-gray-500' },
            '确认后即视为交接结论成立，离职生效将以此为前置条件。',
          ),
          h(Select, {
            allowClear: true,
            options: employeeOptions.value,
            placeholder: '选择交接人（可选）',
            style: { width: '100%' },
            onChange: (value: number) => {
              handoverToEmployeeId = value;
            },
          }),
        ],
      );
    },
    async onOk() {
      await confirmQuitHandover({
        employeeId: props.employeeId,
        handoverToEmployeeId,
      });
      message.success('交接已确认');
      await getProgress();
      emit('refresh');
    },
  });
}

async function handleDelete(id?: number) {
  if (!id) return;
  await deleteQuitHandoverItem(id);
  message.success('交接明细已删除');
  await getProgress();
}

function statusColor(status?: number) {
  if (status === HrmQuitHandoverItemStatus.RETURNED) return 'success';
  if (status === HrmQuitHandoverItemStatus.LOST) return 'error';
  if (status === HrmQuitHandoverItemStatus.WAIVED) return 'default';
  return 'warning';
}

onMounted(async () => {
  await getProgress();
  if (progress.value?.hasQuitInfo) {
    await loadEmployees();
  }
});
defineExpose({ getProgress });
</script>

<template>
  <Card
    v-if="progress?.hasQuitInfo"
    title="离职交接"
    :loading="loading"
    :style="{ marginBottom: '15px' }"
  >
    <template #extra>
      <div v-if="canEdit()" class="flex gap-2">
        <Button size="small" type="primary" @click="itemModalApi.open()">
          新增明细
        </Button>
        <Button
          size="small"
          :disabled="progress?.confirmable === false"
          @click="handleConfirm"
        >
          确认交接
        </Button>
        <Button size="small" @click="waiveModalApi.open()">无需交接</Button>
        <Button
          size="small"
          :type="progress?.salarySettled ? 'default' : 'primary'"
          ghost
          @click="settleModalApi.open()"
        >
          {{ progress?.salarySettled ? '调整结算' : '确认工资结清' }}
        </Button>
      </div>
    </template>

    <Descriptions bordered :column="3" size="small">
      <Descriptions.Item label="交接状态">
        <Tag
          :color="
            progress?.handoverStatus === 2
              ? 'success'
              : progress?.handoverStatus === 3
                ? 'default'
                : 'warning'
          "
        >
          {{ progress?.handoverStatusName || '未开始' }}
        </Tag>
      </Descriptions.Item>
      <Descriptions.Item label="交接人">
        {{ progress?.handoverToEmployeeName || '-' }}
      </Descriptions.Item>
      <Descriptions.Item label="交接确认时间">
        {{ formatHrmDateTime(progress?.handoverConfirmTime) }}
      </Descriptions.Item>
      <Descriptions.Item label="待交回">
        {{ progress?.pendingItemCount ?? 0 }} 项
      </Descriptions.Item>
      <Descriptions.Item label="缺失未交回">
        {{ progress?.lostItemCount ?? 0 }} 项
      </Descriptions.Item>
      <Descriptions.Item label="计划离职时间">
        {{ formatHrmDateTime(progress?.planQuitTime) }}
      </Descriptions.Item>
      <Descriptions.Item label="交接说明" :span="3">
        {{ progress?.handoverRemark || '-' }}
      </Descriptions.Item>
      <Descriptions.Item label="计薪截止日">
        {{ progress?.salaryCutoffDate || '-' }}
      </Descriptions.Item>
      <Descriptions.Item label="薪资结算时间">
        {{ formatHrmDateTime(progress?.salarySettlementTime) }}
      </Descriptions.Item>
      <Descriptions.Item label="工资结清">
        <Tag :color="progress?.salarySettled ? 'success' : 'warning'">
          {{ progress?.salarySettled ? '已结清' : '待结清' }}
        </Tag>
      </Descriptions.Item>
      <Descriptions.Item label="结清时间">
        {{ formatHrmDateTime(progress?.salarySettleTime) }}
      </Descriptions.Item>
      <Descriptions.Item label="离职闭环">
        <Tag :color="progress?.closed ? 'success' : 'default'">
          {{ progress?.closed ? '已完成' : '未完成' }}
        </Tag>
      </Descriptions.Item>
      <Descriptions.Item label="结算说明" :span="3">
        {{ progress?.salarySettleRemark || '-' }}
      </Descriptions.Item>
    </Descriptions>

    <Table
      class="mt-3"
      :data-source="progress?.items || []"
      :pagination="false"
      :row-key="(r: HrmEmployeeQuitHandoverApi.HandoverItem) => r.id"
      bordered
      size="small"
      :columns="[
        { title: '物品类型', dataIndex: 'itemTypeName', width: 110 },
        { title: '物品名称', dataIndex: 'itemName', width: 180 },
        { title: '数量', dataIndex: 'quantity', width: 80 },
        { title: '单位', dataIndex: 'unit', width: 80 },
        {
          title: '交接状态',
          dataIndex: 'status',
          width: 120,
          customRender: ({ record }: any) =>
            h(
              Tag,
              { color: statusColor(record.status) },
              () => record.statusName || '-',
            ),
        },
        {
          title: '确认时间',
          dataIndex: 'confirmTime',
          width: 160,
          customRender: ({ text }: any) => formatHrmDateTime(text),
        },
        { title: '备注', dataIndex: 'remark' },
        {
          title: '操作',
          key: 'action',
          width: 130,
          customRender: ({ record }: any) =>
            canEdit()
              ? h('div', { class: 'flex gap-1' }, [
                  h(
                    Button,
                    {
                      size: 'small',
                      type: 'link',
                      onClick: () => itemModalApi.setData(record).open(),
                    },
                    () => '编辑',
                  ),
                  h(
                    Popconfirm,
                    {
                      title: '确认删除该交接明细吗？',
                      onConfirm: () => handleDelete(record.id),
                    },
                    {
                      default: () =>
                        h(
                          Button,
                          { size: 'small', type: 'link', danger: true },
                          () => '删除',
                        ),
                    },
                  ),
                ])
              : '-',
        },
      ]"
    />

    <ItemModal class="w-[620px]">
      <ItemForm class="mx-4" />
    </ItemModal>
    <WaiveModal class="w-[520px]">
      <WaiveForm class="mx-4" />
    </WaiveModal>
    <SettleModal class="w-[560px]">
      <SettleForm class="mx-4" />
    </SettleModal>
  </Card>
</template>
