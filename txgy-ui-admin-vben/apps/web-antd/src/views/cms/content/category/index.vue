<script lang="ts" setup>
import type { CmsCategory } from '#/api/cms/content-ext';

import {
  Button,
  Card,
  Form,
  FormItem,
  Input,
  InputNumber,
  message,
  Modal,
  Popconfirm,
  Select,
  Space,
  Switch,
  Table,
  Tag,
} from 'ant-design-vue';
import { onMounted, reactive, ref } from 'vue';

import { deleteCategory, getCategoryTree, saveCategory } from '#/api/cms/content-ext';

defineOptions({ name: 'CmsContentCategory' });

const loading = ref(false);
const tree = ref<CmsCategory[]>([]);
const modalVisible = ref(false);
const saving = ref(false);
const roleOptions = [
  { label: '超级管理员', value: 'super_admin' },
  { label: '普通角色', value: 'common' },
  { label: 'CRM 管理员', value: 'crm_admin' },
];
const contentTypeOptions = [
  { label: '标准规范', value: 'STANDARD' },
  { label: '材料参数', value: 'MATERIAL' },
  { label: '工艺案例', value: 'PROCESS' },
  { label: '定额资料', value: 'QUOTA' },
  { label: '政策法规', value: 'POLICY' },
  { label: '图文资讯', value: 'ARTICLE' },
];

const form = reactive({
  code: '',
  contentType: undefined as string | undefined,
  id: undefined as number | undefined,
  name: '',
  parentId: 0,
  remark: '',
  sort: 0,
  status: 0,
  visible: true,
  visibleRoleCodes: [] as string[],
});

const columns = [
  { dataIndex: 'name', key: 'name', title: '栏目名称', width: 240 },
  { dataIndex: 'code', key: 'code', title: '栏目编码', width: 160 },
  { dataIndex: 'contentType', key: 'contentType', title: '绑定内容类型', width: 140 },
  { dataIndex: 'sort', key: 'sort', title: '排序', width: 80 },
  { key: 'visible', title: '前台显示', width: 100 },
  { key: 'roles', title: '可见角色（栏目级权限）', width: 220 },
  { key: 'action', title: '操作', width: 220 },
];

async function load() {
  loading.value = true;
  try {
    tree.value = await getCategoryTree();
  } finally {
    loading.value = false;
  }
}

function resetForm() {
  Object.assign(form, {
    code: '',
    contentType: undefined,
    id: undefined,
    name: '',
    parentId: 0,
    remark: '',
    sort: 0,
    status: 0,
    visible: true,
    visibleRoleCodes: [],
  });
}

function openCreate(parent?: CmsCategory) {
  resetForm();
  form.parentId = parent ? parent.id : 0;
  modalVisible.value = true;
}

function openEdit(row: CmsCategory) {
  resetForm();
  Object.assign(form, {
    code: row.code,
    contentType: row.contentType,
    id: row.id,
    name: row.name,
    parentId: row.parentId,
    remark: row.remark,
    sort: row.sort,
    status: row.status,
    visible: row.visible,
    visibleRoleCodes: row.visibleRoleCodes
      ? row.visibleRoleCodes.split(',').filter(Boolean)
      : [],
  });
  modalVisible.value = true;
}

async function submit() {
  if (!form.name || !form.code) {
    message.warning('请填写栏目名称与编码');
    return;
  }
  saving.value = true;
  try {
    await saveCategory({ ...form });
    message.success('栏目已保存');
    modalVisible.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}

async function remove(id: number) {
  await deleteCategory(id);
  message.success('栏目已删除');
  await load();
}

onMounted(load);
</script>

<template>
  <div class="cms-category-page">
    <Card :bordered="false">
      <template #title>栏目树</template>
      <template #extra>
        <Space>
          <Button type="primary" @click="openCreate()">新增一级栏目</Button>
          <Button @click="load">刷新</Button>
        </Space>
      </template>
      <Table
        :columns="columns"
        :data-source="tree"
        :loading="loading"
        :pagination="false"
        :row-key="(row: CmsCategory) => row.id"
        default-expand-all-rows
        size="middle"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'visible'">
            <Tag :color="record.visible ? 'green' : 'default'">
              {{ record.visible ? '显示' : '隐藏' }}
            </Tag>
          </template>
          <template v-else-if="column.key === 'roles'">
            <template v-if="record.visibleRoleCodes">
              <Tag v-for="code in record.visibleRoleCodes.split(',')" :key="code">{{ code }}</Tag>
            </template>
            <Tag v-else color="blue">全部角色可见</Tag>
          </template>
          <template v-else-if="column.key === 'action'">
            <Space>
              <a @click="openCreate(record)">新增子栏目</a>
              <a @click="openEdit(record)">编辑</a>
              <Popconfirm title="确认删除该栏目？" @confirm="remove(record.id)">
                <a class="danger">删除</a>
              </Popconfirm>
            </Space>
          </template>
        </template>
      </Table>
    </Card>

    <Modal
      v-model:open="modalVisible"
      :confirm-loading="saving"
      :title="form.id ? '编辑栏目' : '新增栏目'"
      @ok="submit"
    >
      <Form layout="vertical">
        <FormItem label="栏目名称" required>
          <Input v-model:value="form.name" placeholder="如：金融政策" />
        </FormItem>
        <FormItem label="栏目编码" required>
          <Input v-model:value="form.code" placeholder="唯一编码，如 finance" />
        </FormItem>
        <FormItem label="绑定内容类型">
          <Select
            v-model:value="form.contentType"
            :options="contentTypeOptions"
            allow-clear
            placeholder="选择该栏目承载的内容类型"
          />
        </FormItem>
        <FormItem label="排序（越小越靠前）">
          <InputNumber v-model:value="form.sort" :min="0" style="width: 100%" />
        </FormItem>
        <FormItem label="前台显示">
          <Switch v-model:checked="form.visible" />
        </FormItem>
        <FormItem label="栏目级权限（可见角色，留空=全部角色可见）">
          <Select
            v-model:value="form.visibleRoleCodes"
            :options="roleOptions"
            mode="multiple"
            placeholder="仅选中角色可在栏目树中看到"
          />
        </FormItem>
        <FormItem label="备注">
          <Input v-model:value="form.remark" />
        </FormItem>
      </Form>
    </Modal>
  </div>
</template>

<style scoped>
.cms-category-page :deep(.danger) {
  color: #b03a2e;
}
</style>