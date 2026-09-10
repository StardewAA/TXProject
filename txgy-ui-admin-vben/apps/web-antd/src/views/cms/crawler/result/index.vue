<script lang="ts" setup>
import { computed, onMounted, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { Button, Input, Modal, Select, Space, Table, Tag, message } from 'ant-design-vue';

import { fetchCmsRows } from '../../content/adapter';

defineOptions({ name: 'CmsCrawlerResult'});

const keyword = ref('');
const statusFilter = ref('全部');
const rows = ref<any[]>([]);
const loading = ref(false);

const statusMeta: Record<string, string> = {
  已发布: 'success',
  待发布: 'processing',
  待审核: 'warning',
  已驳回: 'error',
  草稿: 'default',
};

async function loadRows() {
  loading.value = true;
  try {
    const all = await Promise.all([fetchCmsRows('policy'), fetchCmsRows('standard')]);
    rows.value = all.flat();
  } catch {
    message.error('数据加载失败，请检查后端服务');
  } finally {
    loading.value = false;
  }
}

onMounted(loadRows);

const tableColumns = [
  { dataIndex: 'title', key: 'title', title: '标题', width: 340 },
  { dataIndex: 'type', key: 'type', title: '类型', width: 120 },
  { dataIndex: 'status', key: 'status', title: '状态', width: 110 },
  { dataIndex: 'updatedAt', key: 'updatedAt', title: '更新时间', width: 160 },
  { key: 'action', title: '操作', width: 200 },
];

const statusOptions = computed(() => {
  const values = Array.from(new Set(rows.value.map((row) => row.status)));
  return [{ label: '全部状态', value: '全部' }, ...values.map((v) => ({ label: v, value: v }))];
});

const summary = computed(() => {
  const published = rows.value.filter((r) => r.status === '已发布').length;
  const pending = rows.value.filter((r) => r.status === '待审核').length;
  const draft = rows.value.filter((r) => r.status === '草稿').length;
  const rejected = rows.value.filter((r) => r.status === '已驳回').length;
  return [
    { label: '内容总量', value: rows.value.length },
    { label: '已发布', value: published },
    { label: '待审核', value: pending },
    { label: '草稿', value: draft },
    { label: '已驳回', value: rejected },
  ];
});

const filteredRows = computed(() =>
  rows.value.filter((row) => {
    const matchStatus = statusFilter.value === '全部' || row.status === statusFilter.value;
    const matchKeyword =
      !keyword.value ||
      (row.title && row.title.toLowerCase().includes(keyword.value.toLowerCase()));
    return matchStatus && matchKeyword;
  }),
);

function viewDetail(row: any) {
  Modal.info({
    title: row.title,
    width: 720,
    content: row.content || '暂无正文',
  });
}
</script>

<template>
  <Page>
    <div class="cms-content-page">
      <div class="cms-page-heading">
        <div>
          <p class="cms-eyebrow">CMS 内容状态管理</p>
          <h1>内容状态管理</h1>
        </div>
      </div>

      <div class="cms-stat-grid">
        <div v-for="item in summary" :key="item.label" class="cms-stat-item">
          <strong>{{ item.value }}</strong>
          <span>{{ item.label }}</span>
        </div>
      </div>

      <div class="cms-toolbar">
        <Input v-model:value="keyword" allow-clear class="cms-search" placeholder="搜索标题" />
        <Select v-model:value="statusFilter" :options="statusOptions" class="cms-status-filter" />
      </div>

      <Table
        :columns="tableColumns"
        :data-source="filteredRows"
        :loading="loading"
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
              <Button size="small" type="link" @click="viewDetail(record)">查看</Button>
            </Space>
          </template>
        </template>
      </Table>
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
  grid-template-columns: repeat(5, minmax(0, 1fr));
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
</style>
