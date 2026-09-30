<script lang="ts" setup>
import type { MesTmToolRepairApi } from '#/api/mes/tm/tool/repair';

import { computed, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { Button, message, Tabs, TabPane, Input, InputNumber, Popconfirm, Select, Table } from 'ant-design-vue';

import { useVbenForm } from '#/adapter/form';
import {
  createToolRepair,
  getToolRepair,
  updateToolRepair,
  getToolRepairLineListByRepairId,
  createToolRepairLine,
  updateToolRepairLine,
  deleteToolRepairLine,
} from '#/api/mes/tm/tool/repair';
import { getToolSimpleList } from '#/api/mes/tm/tool';
import type { MesTmToolApi } from '#/api/mes/tm/tool';
import { $t } from '#/locales';

export type FormType = 'create' | 'detail' | 'update';

const emit = defineEmits(['success']);
const formType = ref<FormType>('create');
const activeTab = ref('basic');
const lines = ref<MesTmToolRepairApi.ToolRepairLine[]>([]);
const toolOptions = ref<MesTmToolApi.Tool[]>([]);
const editingLine = ref<Partial<MesTmToolRepairApi.ToolRepairLine>>({});
const isEditing = ref(false);

const isDetail = computed(() => formType.value === 'detail');
const getTitle = computed(() => {
  if (formType.value === 'detail') return '查看工具维修单';
  return formType.value === 'update' ? '编辑工具维修单' : '新建工具维修单';
});

const [Form, formApi] = useVbenForm({
  commonConfig: {
    componentProps: { class: 'w-full' },
    formItemClass: 'col-span-1',
    labelWidth: 110,
  },
  wrapperClass: 'grid-cols-2',
  layout: 'horizontal',
  schema: [
    { fieldName: 'id', component: 'Input', dependencies: { triggerFields: [''], show: () => false } },
    {
      fieldName: 'code',
      label: '维修单编码',
      component: 'Input',
      rules: 'required',
      componentProps: { placeholder: '请输入维修单编码' },
    },
    {
      fieldName: 'name',
      label: '维修单名称',
      component: 'Input',
      rules: 'required',
      componentProps: { placeholder: '请输入维修单名称' },
    },
    {
      fieldName: 'repairDate',
      label: '维修日期',
      component: 'DatePicker',
      componentProps: { format: 'YYYY-MM-DD HH:mm:ss', showTime: true, valueFormat: 'x' },
    },
    {
      fieldName: 'status',
      label: '状态',
      component: 'Input',
      componentProps: { disabled: true },
    },
    {
      fieldName: 'repairResult',
      label: '维修结果',
      component: 'Textarea',
      formItemClass: 'col-span-2',
      componentProps: { placeholder: '请输入维修结果', rows: 2 },
    },
    {
      fieldName: 'remark',
      label: '备注',
      component: 'Textarea',
      formItemClass: 'col-span-2',
      componentProps: { placeholder: '请输入备注', rows: 3 },
    },
  ],
  showDefaultActions: false,
});

async function loadLines(repairId: number) {
  lines.value = await getToolRepairLineListByRepairId(repairId);
}

async function loadToolOptions() {
  toolOptions.value = await getToolSimpleList();
}

function handleAdd() { editingLine.value = { quantity: 1 }; isEditing.value = true; }
function handleEdit(line: MesTmToolRepairApi.ToolRepairLine) { editingLine.value = { ...line }; isEditing.value = true; }

async function handleSaveLine() {
  if (!editingLine.value.toolId) { message.warning('请选择工具'); return; }
  const tool = toolOptions.value.find(t => t.id === editingLine.value.toolId);
  if (tool) { editingLine.value.toolCode = tool.code; editingLine.value.toolName = tool.name; }
  if (editingLine.value.id) {
    await updateToolRepairLine(editingLine.value as MesTmToolRepairApi.ToolRepairLine);
  } else {
    await createToolRepairLine({ ...editingLine.value, repairId: formApi.form.values?.id } as MesTmToolRepairApi.ToolRepairLine);
  }
  isEditing.value = false; editingLine.value = {};
  loadLines(formApi.form.values?.id); message.success('保存成功');
}

async function handleDeleteLine(id: number) {
  await deleteToolRepairLine(id); loadLines(formApi.form.values?.id); message.success('删除成功');
}

function handleCancel() { isEditing.value = false; editingLine.value = {}; }
loadToolOptions();

const lineColumns = [
  { title: '工具编码', dataIndex: 'toolCode', width: 140 },
  { title: '工具名称', dataIndex: 'toolName', width: 160 },
  { title: '维修数量', dataIndex: 'quantity', width: 100 },
  { title: '维修结果', dataIndex: 'repairResult', width: 160 },
  { title: '备注', dataIndex: 'remark', width: 160 },
  { title: '操作', width: 180, fixed: 'right' as const },
];

const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    if (isDetail.value) { await modalApi.close(); return; }
    const { valid } = await formApi.validate(); if (!valid) return;
    modalApi.lock();
    const data = (await formApi.getValues()) as MesTmToolRepairApi.ToolRepair;
    try {
      await (data.id ? updateToolRepair(data) : createToolRepair(data));
      await modalApi.close(); emit('success'); message.success($t('ui.actionMessage.operationSuccess'));
    } finally { modalApi.unlock(); }
  },
  async onOpenChange(isOpen: boolean) {
    if (!isOpen) { lines.value = []; activeTab.value = 'basic'; return; }
    const data = modalApi.getData<{ formType: FormType; id?: number }>();
    formType.value = data.formType;
    formApi.setDisabled(formType.value === 'detail');
    modalApi.setState({ confirmText: '保存', showConfirmButton: formType.value !== 'detail' });
    if (data?.id) {
      modalApi.lock();
      try { const repair = await getToolRepair(data.id); await formApi.setValues(repair); await loadLines(data.id); } finally { modalApi.unlock(); }
    }
  },
});
</script>

<template>
  <Modal :title="getTitle" class="w-4/5">
    <Tabs v-model:activeKey="activeTab">
      <TabPane key="basic" tab="基本信息"><Form class="mx-4" /></TabPane>
      <TabPane key="lines" tab="维修明细">
        <div class="p-4">
          <div v-if="!isDetail" class="mb-4">
            <Button v-if="!isEditing" type="primary" @click="handleAdd">新增维修行</Button>
          </div>
          <div v-if="isEditing" class="mb-4 flex flex-wrap items-center gap-2 rounded bg-gray-50 p-3">
            <Select v-model:value="editingLine.toolId" placeholder="请选择工具" show-search
              :options="toolOptions.map(t => ({ label: `${t.code || ''} - ${t.name}`, value: t.id }))"
              style="width: 300px" :filter-option="(input, option) => option.label?.toLowerCase().includes(input.toLowerCase())" />
            <InputNumber v-model:value="editingLine.quantity" :min="1" placeholder="数量" style="width: 100px" />
            <Input v-model:value="editingLine.repairResult" placeholder="维修结果" style="width: 200px" />
            <Input v-model:value="editingLine.remark" placeholder="备注" style="width: 200px" />
            <Button type="primary" size="small" @click="handleSaveLine">保存</Button>
            <Button size="small" @click="handleCancel">取消</Button>
          </div>
          <Table :columns="lineColumns" :data-source="lines" :pagination="false" row-key="id" size="small" bordered>
            <template #bodyCell="{ column, record }">
              <template v-if="column.title === '操作' && !isDetail">
                <Button type="link" size="small" @click="handleEdit(record)">编辑</Button>
                <Popconfirm title="确定删除该行？" @confirm="handleDeleteLine(record.id)">
                  <Button type="link" size="small" danger>删除</Button>
                </Popconfirm>
              </template>
            </template>
          </Table>
        </div>
      </TabPane>
    </Tabs>
  </Modal>
</template>
