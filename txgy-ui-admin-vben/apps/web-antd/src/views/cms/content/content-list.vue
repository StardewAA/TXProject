<script lang="ts" setup>
import type { CmsContentConfig, CmsRecord } from './config';

import { computed, onMounted, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';

import {
  Button,
  Form,
  FormItem,
  Input,
  Modal,
  Select,
  Space,
  Spin,
  Table,
  Tag,
  message,
} from 'ant-design-vue';

import {
  createCmsRow,
  deleteCmsRow,
  fetchCmsDetail,
  fetchCmsRows,
  submitCmsAudit,
  updateCmsRow,
} from './adapter';

defineOptions({ name: 'CmsContentList' });

const props = defineProps<CmsContentConfig>();

const keyword = ref('');
const statusFilter = ref('全部');
const rows = ref<CmsRecord[]>([]);
const loading = ref(false);

const statusMeta: Record<string, string> = {
  已发布: 'success',
  待发布: 'processing',
  待审核: 'warning',
  已驳回: 'error',
  已下架: 'default',
  草稿: 'default',
};

// 清单D2⑤：草稿/已驳回/已下架可编辑删除提交审核；待审核/通过/已发布只可查看
function canEdit(status: string): boolean {
  return status === '草稿' || status === '已驳回' || status === '已下架';
}

async function loadRows() {
  loading.value = true;
  try {
    rows.value = await fetchCmsRows(props.apiType);
  } catch {
    message.error('数据加载失败，请检查后端服务');
  } finally {
    loading.value = false;
  }
}

onMounted(loadRows);

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
const fullVisible = ref(false);

function openFile(url: string) {
  if (!url) return;
  window.open(url.startsWith('http') ? url : `${location.origin}${url}`, '_blank');
}

/** 详情里可用的原件地址（标准库挂的是 coverImage，政策库挂的是 attachmentUrls） */
const documentUrl = computed(() => detailRow.value?.attachmentUrls?.[0] || '');

/**
 * 查看全文：有原文链接直接跳原文；没有链接则打开原件文档；
 * 两者都没有时打开正文全文弹窗。
 */
function viewFullText() {
  const row = detailRow.value;
  if (!row) return;
  if (row.sourceUrl) {
    openFile(row.sourceUrl);
    return;
  }
  if (documentUrl.value) {
    openFile(documentUrl.value);
    return;
  }
  if (row.content) {
    fullVisible.value = true;
    return;
  }
  message.info('该条目暂无原文链接或文档');
}

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
  Object.keys(formModel).forEach((key) => delete formModel[key]);
  Object.assign(formModel, { ...row });
  formVisible.value = true;
}

async function openDetail(row: CmsRecord) {
  detailVisible.value = true;
  fullVisible.value = false;
  detailRow.value = row;
  // 列表接口已排除 content，点查看时单独查详情拿全文
  const detail = await fetchCmsDetail(props.apiType, row.id!);
  if (detail) detailRow.value = detail;
}

function closeForm() {
  formVisible.value = false;
}

function validateForm(): string | null {
  for (const field of props.formFields) {
    const value = formModel[field.dataIndex];
    const empty =
      value === undefined || value === null || String(value).trim() === '';
    if (field.required && empty) {
      return `${field.label}不能为空`;
    }
    if (field.date && !empty && !/^\d{4}-\d{2}-\d{2}$/.test(String(value))) {
      return `${field.label}格式应为 2026-01-01`;
    }
  }
  return null;
}

async function saveRow() {
  const error = validateForm();
  if (error) {
    message.warning(error);
    return;
  }
  try {
    if (editingId.value) {
      await updateCmsRow(props.apiType, { ...formModel }, editingId.value);
    } else {
      await createCmsRow(props.apiType, { ...formModel });
    }
    message.success('保存成功');
    closeForm();
    await loadRows();
  } catch (error: any) {
    message.error(error?.msg || '保存失败，请检查必填项');
  }
}

async function submitAudit(row: CmsRecord) {
  try {
    await submitCmsAudit(props.apiType, row.id!);
    message.success('已提交审核');
    await loadRows();
  } catch (error: any) {
    message.error(error?.msg || '提交审核失败');
  }
}

function removeRow(row: CmsRecord) {
  Modal.confirm({
    content: `确认删除“${row.title || row.name || row.standardNo}”吗？`,
    title: '删除确认',
    async onOk() {
      try {
        await deleteCmsRow(props.apiType, row.id!);
        message.success('删除成功');
        await loadRows();
      } catch (error: any) {
        message.error(error?.msg || '删除失败');
      }
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

      <Spin :spinning="loading">
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
                <Button
                  v-if="canEdit(record.status)"
                  size="small"
                  type="link"
                  @click="openEdit(record)"
                >
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
                  v-if="canEdit(record.status)"
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
      </Spin>

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
        <div
          v-if="detailRow?.content || detailRow?.attachmentUrls?.length || detailRow?.sourceUrl"
          class="cms-detail-content"
        >
          <span>全文与原件</span>
          <div class="cms-doc-links">
            <Button size="small" type="primary" @click="viewFullText">
              查看全文
            </Button>
            <Button
              v-if="detailRow?.sourceUrl && documentUrl"
              size="small"
              @click="openFile(documentUrl)"
            >
              📎 查看文档原件（PDF）
            </Button>
            <Button
              v-if="detailRow?.content"
              size="small"
              ghost
              @click="fullVisible = true"
            >
              📄 查看正文内容
            </Button>
          </div>
        </div>
      </Modal>

      <Modal
        v-model:open="fullVisible"
        :footer="null"
        :title="`${detailRow?.title || pageTitle} · 文档内容`"
        :width="900"
      >
        <pre class="cms-fulltext">{{ detailRow?.content }}</pre>
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
                  :placeholder="field.placeholder"
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

.cms-detail-content {
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid #e5e7eb;
}

.cms-detail-content span {
  display: block;
  color: #6b7280;
  font-size: 13px;
}

.cms-detail-content pre {
  margin-top: 6px;
  padding: 12px;
  border: 1px solid #f3f4f6;
  border-radius: 6px;
  background: #f9fafb;
  color: #111827;
  font-family: inherit;
  font-size: 14px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

.cms-doc-links {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 8px;
}

.cms-fulltext {
  margin: 0;
  max-height: 70vh;
  overflow: auto;
  padding: 16px;
  border: 1px solid #f3f4f6;
  border-radius: 6px;
  background: #f9fafb;
  color: #111827;
  font-family: inherit;
  font-size: 14px;
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-word;
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
