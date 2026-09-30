<script lang="ts" setup>
import type { HrmEmployeeNoRuleApi } from '#/api/hrm/employee/no-rule';

import { h, onMounted, ref } from 'vue';

import { useAccess } from '@vben/access';
import { Page, useVbenForm, useVbenModal } from '@vben/common-ui';

import {
  Alert,
  Button,
  Card,
  message,
  Popconfirm,
  Table,
  Tag,
} from 'ant-design-vue';

import {
  deleteNoRule,
  getNoRuleList,
  previewNextJobNumber,
  saveNoRule,
} from '#/api/hrm/employee/no-rule';
import { formatHrmDateTime } from '#/views/hrm/utils/format';

defineOptions({ name: 'HrmEmployeeNoRule' });

const { hasAccessByCodes } = useAccess();
const canEdit = () => hasAccessByCodes(['hrm:employee-no-rule:update']);

const loading = ref(false);
const list = ref<HrmEmployeeNoRuleApi.NoRule[]>([]);
const nextJobNumber = ref<string>();

async function getList() {
  loading.value = true;
  try {
    list.value = await getNoRuleList();
    nextJobNumber.value = await previewNextJobNumber().catch(() => undefined);
  } finally {
    loading.value = false;
  }
}

const [Form, formApi] = useVbenForm({
  commonConfig: { labelWidth: 118, componentProps: { class: 'w-full' } },
  layout: 'horizontal',
  schema: [
    {
      fieldName: 'id',
      component: 'Input',
      dependencies: { triggerFields: [''], show: () => false },
    },
    {
      fieldName: 'name',
      label: '规则名称',
      component: 'Input',
      rules: 'required',
      componentProps: { placeholder: '如：秦川默认工号规则' },
    },
    {
      fieldName: 'tenantAbbr',
      label: '租户缩写',
      component: 'Input',
      rules: 'required',
      componentProps: { placeholder: '工号前缀，如 QC' },
    },
    {
      fieldName: 'prefixSeparator',
      label: '分隔符',
      component: 'Input',
      componentProps: { placeholder: '可留空，如 -' },
    },
    {
      fieldName: 'serialLength',
      label: '顺序号位数',
      component: 'InputNumber',
      rules: 'required',
      defaultValue: 6,
      componentProps: { min: 1, max: 12, class: 'w-full' },
      help: '不足位数左补零，例如 6 位 → QC000001',
    },
    {
      fieldName: 'resetPeriod',
      label: '重置周期',
      component: 'Select',
      defaultValue: 0,
      componentProps: {
        class: 'w-full',
        options: [
          { label: '不重置', value: 0 },
          { label: '按年重置（工号含年份）', value: 1 },
        ],
      },
    },
    {
      fieldName: 'allowManual',
      label: '允许人工填工号',
      component: 'Switch',
      defaultValue: false,
      help: '按当前口径建议关闭：工号统一由系统生成，避免人工造号冲突',
    },
    {
      fieldName: 'status',
      label: '状态',
      component: 'Select',
      defaultValue: 0,
      componentProps: {
        class: 'w-full',
        options: [
          { label: '启用', value: 0 },
          { label: '停用', value: 1 },
        ],
      },
      help: '同一租户只保留一条启用规则，启用本条会自动停用其它规则',
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

const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    const { valid } = await formApi.validate();
    if (!valid) return;
    modalApi.lock();
    try {
      await saveNoRule(await formApi.getValues());
      message.success('工号规则已保存');
      await modalApi.close();
      await getList();
    } finally {
      modalApi.unlock();
    }
  },
  async onOpenChange(isOpen: boolean) {
    if (!isOpen) return;
    const data = modalApi.getData<HrmEmployeeNoRuleApi.NoRule>();
    await formApi.resetForm();
    modalApi.setState({ title: data?.id ? '编辑工号规则' : '新增工号规则' });
    if (data?.id) {
      await formApi.setValues(data);
    }
  },
});

async function handleDelete(id?: number) {
  if (!id) return;
  await deleteNoRule(id);
  message.success('工号规则已删除');
  await getList();
}

onMounted(getList);
</script>

<template>
  <Page auto-content-height>
    <Card :bordered="false">
      <Alert
        class="mb-3"
        type="info"
        show-icon
        message="工号规则说明"
        description="工号 = 租户缩写 + 分隔符（可选）+ 年份（按年重置时）+ 顺序号（左补零）。租户内独立计数、永不复用；已删除档案占用的序号同样会被跳过，不会出现重号。"
      />
      <div class="mb-3 flex items-center justify-between">
        <div class="text-sm text-gray-600">
          下一个工号：
          <span class="font-medium text-blue-600">
            {{ nextJobNumber || '未配置启用规则' }}
          </span>
        </div>
        <Button
          v-if="canEdit()"
          type="primary"
          @click="modalApi.setData({}).open()"
        >
          新增规则
        </Button>
      </div>
      <Table
        :columns="[
          { title: '规则名称', dataIndex: 'name', width: 180 },
          { title: '租户缩写', dataIndex: 'tenantAbbr', width: 110 },
          { title: '分隔符', dataIndex: 'prefixSeparator', width: 90 },
          { title: '位数', dataIndex: 'serialLength', width: 80 },
          {
            title: '重置周期',
            dataIndex: 'resetPeriod',
            width: 130,
            customRender: ({ text }: any) =>
              text === 1 ? '按年重置' : '不重置',
          },
          {
            title: '允许人工填号',
            dataIndex: 'allowManual',
            width: 130,
            customRender: ({ text }: any) => (text ? '允许' : '禁止'),
          },
          {
            title: '状态',
            dataIndex: 'status',
            width: 90,
            customRender: ({ text }: any) =>
              h(Tag, { color: text === 0 ? 'success' : 'default' }, () =>
                text === 0 ? '启用' : '停用',
              ),
          },
          { title: '工号样例', dataIndex: 'sampleJobNumber', width: 130 },
          {
            title: '创建时间',
            dataIndex: 'createTime',
            width: 170,
            customRender: ({ text }: any) => formatHrmDateTime(text),
          },
          { title: '备注', dataIndex: 'remark' },
          {
            title: '操作',
            key: 'action',
            width: 130,
            fixed: 'right',
            customRender: ({ record }: any) =>
              canEdit()
                ? h('div', { class: 'flex gap-1' }, [
                    h(
                      Button,
                      {
                        size: 'small',
                        type: 'link',
                        onClick: () => modalApi.setData(record).open(),
                      },
                      () => '编辑',
                    ),
                    h(
                      Popconfirm,
                      {
                        title: '确认删除该工号规则吗？',
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
        :data-source="list"
        :loading="loading"
        :pagination="false"
        :row-key="(r: HrmEmployeeNoRuleApi.NoRule) => r.id"
        :scroll="{ x: 1600 }"
        bordered
        size="small"
      />
    </Card>
    <Modal class="w-[680px]">
      <Form class="mx-4" />
    </Modal>
  </Page>
</template>
