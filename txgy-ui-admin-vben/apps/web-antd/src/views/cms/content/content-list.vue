<script lang="ts" setup>
import type { CmsContentConfig, CmsRecord } from './config';

import { computed, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';

import {
  Button,
  Form,
  FormItem,
  Input,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  message,
} from 'ant-design-vue';

defineOptions({ name: 'CmsContentList' });

const props = defineProps<CmsContentConfig>();

const keyword = ref('');
const statusFilter = ref('全部');
const rows = ref<CmsRecord[]>(props.records.map((item) => ({ ...item })));

const statusMeta: Record<string, string> = {
  已发布: 'success',
  待审核: 'warning',
  已驳回: 'error',
  草稿: 'default',
};

const tableColumns = computed(() => [
  ...props.columns.map((column) => ({
    ...column,
    key: column.dataIndex,
  })),
  { key: 'status', title: '状态', width: 100 },
  { key: 'action', title: '操作', width: 220 },
]);

const statusOptions = computed(() => {
  const values = Array.from(new Set(rows.value.map((row) => row.status)));
  return values.map((value) => ({ label: value, value }));
});

const summary = computed(() => {
  const published = rows.value.filter((row) => row.status === '已发布').length;
  const pending = rows.value.filter((row) => row.status === '待审核').length;
  const draft = rows.value.filter((row) => row.status === '草稿').length;
  return [
    { label: '内容总量', value: rows.value.length },
    { label: '已发布', value: published },
    { label: '待审核', value: pending },
    { label: '草稿', value: draft },
  ];
});

const filteredRows = computed(() =>
  rows.value.filter((row) => {
    const matchedStatus =
      statusFilter.value === '全部' || row.status === statusFilter.value;
    const matchedKeyword =
      !keyword.value ||
      Object.values(row).some((value) =>
        String(value).toLowerCase().includes(keyword.value.toLowerCase()),
      );
    return matchedStatus && matchedKeyword;
  }),
);

const formVisible = ref(false);
const formTitle = ref('');
const editingId = ref<number | null>(null);
const formModel = reactive<Record<string, any>>({});

const detailVisible = ref(false);
const detailRow = ref<CmsRecord | null>(null);

function openCreate() {
  editingId.value = null;
  formTitle.value = `新增${props.moduleName}内容`;
  formModel.status = '草稿';
  Object.keys(formModel).forEach((key) => {
    if (key !== 'status') {
      delete formModel[key];
    }
  });
  formVisible.value = true;
}

function openEdit(row: CmsRecord) {
  editingId.value = row.id ?? null;
  formTitle.value = `编辑${props.moduleName}内容`;
  Object.assign(formModel, { ...row });
  formVisible.value = true;
}

function openDetail(row: CmsRecord) {
  detailRow.value = row;
  detailVisible.value = true;
}

function closeForm() {
  formVisible.value = false;
}

function saveRow() {
  const row = { ...formModel };
  if (editingId.value) {
    const index = rows.value.findIndex((item) => item.id === editingId.value);
    if (index >= 0) {
      rows.value[index] = { ...row, id: editingId.value };
    }
  } else {
    const maxId = rows.value.reduce((max, item) => Math.max(max, item.id ?? 0), 0);
    rows.value.push({ ...row, id: maxId + 1 });
  }
  message.success('保存成功');
  closeForm();
}

function submitAudit(row: CmsRecord) {
  const target = rows.value.find((item) => item.id === row.id);
  if (target) {
    target.status = '待审核';
    message.success('已提交审核');
  }
}

function removeRow(row: CmsRecord) {
  Modal.confirm({
    content: `确认删除“${row.title || row.name || row.standardNo}”吗？`,
    title: '删除确认',
    async onOk() {
      rows.value = rows.value.filter((item) => item.id !== row.id);
      message.success('删除成功');
    },
  });
}

</script>

<template>
  <Page>
    <div class="cms-content-page">
      <div class="cms-page-heading">
        <div>
          <p class="cms-eyebrow">{{ moduleName }}</p>
          <h1>{{ pageTitle }}</h1>
        </div>
        <Button type="primary" @click="openCreate">新增内容</Button>
      </div>

      <div class="cms-stat-grid">
        <div v-for="item in summary" :key="item.label" class="cms-stat-item">
          <strong>{{ item.value }}</strong>
          <span>{{ item.label }}</span>
        </div>
      </div>

      <div class="cms-toolbar">
        <Input
          v-model:value="keyword"
          allow-clear
          class="cms-search"
          placeholder="搜索名称、编号或关键词"
        />
        <Select
          v-model:value="statusFilter"
          :options="[
            { label: '全部状态', value: '全部' },
            ...statusOptions,
          ]"
          class="cms-status-filter"
        />
      </div>

      <Table
        :columns="tableColumns"
        :data-source="filteredRows"
        :pagination="{ pageSize: 8, showSizeChanger: true }"
        row-key="id"
        size="middle"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'status'">
            <Tag :color="statusMeta[record.status] || 'default'">
              {{ record.status }}
            </Tag>
          </template>
          <template v-else-if="column.key === 'action'">
            <Space :size="4">
              <Button size="small" type="link" @click="openDetail(record)">
                查看
              </Button>
              <Button size="small" type="link" @click="openEdit(record)">
                编辑
              </Button>
              <Button
                v-if="record.status === '草稿' || record.status === '已驳回'"
                size="small"
                type="link"
                @click="submitAudit(record)"
              >
                提交审核
              </Button>
              <Button
                size="small"
                danger
                type="link"
                @click="removeRow(record)"
              >
                删除
              </Button>
            </Space>
          </template>
        </template>
      </Table>

      <Modal
        v-model:open="detailVisible"
        :footer="null"
        :title="`${pageTitle}详情`"
        width="720px"
      >
        <div v-if="detailRow" class="cms-detail-grid">
          <div
            v-for="column in [...columns, { dataIndex: 'status', title: '状态' }]"
            :key="column.dataIndex"
            class="cms-detail-item"
          >
            <span>{{ column.title }}</span>
            <strong>{{ detailRow[column.dataIndex] ?? '-' }}</strong>
          </div>
        </div>
      </Modal>

      <Modal
        v-model:open="formVisible"
        :title="formTitle"
        :width="680"
        @ok="saveRow"
        @cancel="closeForm"
      >
        <Form layout="vertical">
          <div class="cms-form-grid">
            <div v-for="field in formFields" :key="field.dataIndex">
              <FormItem :label="field.label">
                <component
                  :is="field.options ? Select : field.multiline ? Input.TextArea : Input"
                  v-model:value="formModel[field.dataIndex]"
                  :allow-clear="Boolean(field.options)"
                  :options="field.options"
                  :rows="field.multiline ? 3 : undefined"
                />
              </FormItem>
            </div>
          </div>
        </Form>
      </Modal>
    </div>
  </Page>
</template>

<style scoped>
.cms-content-page {
  display: grid;
  gap: 16px;
}

.cms-page-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
}

.cms-eyebrow {
  margin: 0 0 6px;
  color: #1d4ed8;
  font-size: 13px;
  font-weight: 600;
}

h1 {
  margin: 0;
  color: #111827;
  font-size: 24px;
  line-height: 32px;
}

.cms-stat-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.cms-stat-item {
  display: flex;
  min-height: 88px;
  padding: 16px 20px;
  border: 1px solid #e5e7eb;
  background: #fff;
  flex-direction: column;
  justify-content: center;
}

.cms-stat-item strong {
  color: #111827;
  font-size: 26px;
  line-height: 34px;
}

.cms-stat-item span {
  margin-top: 4px;
  color: #6b7280;
  font-size: 13px;
}

.cms-toolbar {
  display: flex;
  gap: 12px;
}

.cms-search {
  width: min(360px, 100%);
}

.cms-status-filter {
  width: 160px;
}

.cms-detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0;
}

.cms-detail-item {
  padding: 14px 0;
  border-bottom: 1px solid #f3f4f6;
}

.cms-detail-item span {
  display: block;
  color: #6b7280;
  font-size: 13px;
}

.cms-detail-item strong {
  display: block;
  margin-top: 4px;
  color: #111827;
  font-size: 14px;
  font-weight: 500;
}

.cms-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: 16px;
}

@media (max-width: 900px) {
  .cms-stat-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .cms-form-grid {
    grid-template-columns: 1fr;
  }

  .cms-page-heading,
  .cms-toolbar {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
