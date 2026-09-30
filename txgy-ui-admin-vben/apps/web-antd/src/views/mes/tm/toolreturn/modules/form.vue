<script lang="ts" setup>
import type { MesTmToolReturnApi } from '#/api/mes/tm/tool/return';

import { computed, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { Button, message, Tabs, TabPane, Input, InputNumber, Popconfirm, Select, Table } from 'ant-design-vue';

import { useVbenForm } from '#/adapter/form';
import {
  createToolReturn,
  getToolReturn,
  updateToolReturn,
  getToolReturnLineListByReturnId,
  createToolReturnLine,
  updateToolReturnLine,
  deleteToolReturnLine,
} from '#/api/mes/tm/tool/return';
import { getToolSimpleList } from '#/api/mes/tm/tool';
import type { MesTmToolApi } from '#/api/mes/tm/tool';
import { $t } from '#/locales';

export type FormType = 'create' | 'detail' | 'update';

const emit = defineEmits(['success']);
const formType = ref<FormType>('create');
const activeTab = ref('basic');
const lines = ref<MesTmToolReturnApi.ToolReturnLine[]>([]);
const toolOptions = ref<MesTmToolApi.Tool[]>([]);
const editingLine = ref<Partial<MesTmToolReturnApi.ToolReturnLine>>({});
const isEditing = ref(false);

const isDetail = computed(() => formType.value === 'detail');
const getTitle = computed(() => {
  if (formType.value === 'detail') return '查看工具归还单';
  return formType.value === 'update' ? '编辑工具归还单' : '新建工具归还单';
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
      label: '归还单编码',
      component: 'Input',
      rules: 'required',
      componentProps: { placeholder: '请输入归还单编码' },
    },
    {
      fieldName: 'name',
      label: '归还单名称',
      component: 'Input',
      rules: 'required',
      componentProps: { placeholder: '请输入归还单名称' },
    },
    {
      fieldName: 'workstationId',
      label: '工位',
      component: 'ApiSelect',
      componentProps: {
        api: () => import('#/api/mes/md/workstation').then(m => m.getWorkstationSimpleList()),
        labelField: 'name',
        valueField: 'id',
        placeholder: '请选择工位',
      },
    },
    {
      fieldName: 'returnDate',
      label: '归还日期',
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
      fieldName: 'remark',
      label: '备注',
      component: 'Textarea',
      formItemClass: 'col-span-2',
      componentProps: { placeholder: '请输入备注', rows: 3 },
    },
  ],
  showDefaultActions: false,
});

async function loadLines(returnId: number) {
  lines.value = await getToolReturnLineListByReturnId(returnId);
}

async function loadToolOptions() {
  toolOptions.value = await getToolSimpleList();
}

function handleAdd() {
  editingLine.value = { quantity: 1 };
  isEditing.value = true;
}

function handleEdit(line: MesTmToolReturnApi.ToolReturnLine) {
  editingLine.value = { ...line };
  isEditing.value = true;
}

async function handleSaveLine() {
  if (!editingLine.value.toolId) { message.warning('请选择工具'); return; }
  if (!editingLine.value.quantity || editingLine.value.quantity < 1) { message.warning('请输入有效数量'); return; }
  const tool = toolOptions.value.find(t => t.id === editingLine.value.toolId);
  if (tool) { editingLine.value.toolCode = tool.code; editingLine.value.toolName = tool.name; }
  if (editingLine.value.id) {
    await updateToolReturnLine(editingLine.value as MesTmToolReturnApi.ToolReturnLine);
  } else {
    await createToolReturnLine({ ...editingLine.value, returnId: formApi.form.values?.id } as MesTmToolReturnApi.ToolReturnLine);
  }
  isEditing.value = false; editingLine.value = {};
  emit('success'); loadLines(formApi.form.values?.id); message.success('保存成功');
}

async function handleDeleteLine(id: number) {
  await deleteToolReturnLine(id); loadLines(formApi.form.values?.id); message.success('删除成功');
}

function handleCancel() { isEditing.value = false; editingLine.value = {}; }

loadToolOptions();

const lineColumns = [
  { title: '工具编码', dataIndex: 'toolCode', width: 140 },
  { title: '工具名称', dataIndex: 'toolName', width: 160 },
  { title: '归还数量', dataIndex: 'quantity', width: 100 },
  { title: '备注', dataIndex: 'remark', width: 160 },
  { title: '操作', width: 180, fixed: 'right' as const },
];

const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    if (isDetail.value) { await modalApi.close(); return; }
    const { valid } = await formApi.validate(); if (!valid) return;
    modalApi.lock();
    const data = (await formApi.getValues()) as MesTmToolReturnApi.ToolReturn;
    try {
      await (data.id ? updateToolReturn(data) : createToolReturn(data));
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
      try { const ret = await getToolReturn(data.id); await formApi.setValues(ret); await loadLines(data.id); } finally { modalApi.unlock(); }
    }
  },
});
</script>

<template>
  <Modal :title="getTitle" class="w-4/5">
    <Tabs v-model:activeKey="activeTab">
      <TabPane key="basic" tab="基本信息"><Form class="mx-4" /></TabPane>
      <TabPane key="lines" tab="归还明细">
        <div class="p-4">
          <div v-if="!isDetail" class="mb-4">
            <Button v-if="!isEditing" type="primary" @click="handleAdd">新增归还行</Button>
          </div>
          <div v-if="isEditing" class="mb-4 flex items-center gap-2 rounded bg-gray-50 p-3">
            <Select v-model:value="editingLine.toolId" placeholder="请选择工具" show-search
              :options="toolOptions.map(t => ({ label: `${t.code || ''} - ${t.name}`, value: t.id }))"
              style="width: 300px" :filter-option="(input, option) => option.label?.toLowerCase().includes(input.toLowerCase())" />
            <InputNumber v-model:value="editingLine.quantity" :min="1" placeholder="数量" style="width: 100px" />
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
