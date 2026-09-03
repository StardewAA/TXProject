<script lang="ts" setup>
import { computed, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { Button, Input, Space, Table, Tag, message } from 'ant-design-vue';

defineOptions({ name: 'CmsTablePage' });

const props = withDefaults(
  defineProps<{
    actions?: Array<{
      danger?: boolean;
      ifShow?: string[];
      label: string;
      type?: 'link' | 'primary' | 'default';
    }>;
    columns: Array<{ dataIndex: string; title: string; width?: number }>;
    eyebrow?: string;
    keywordSearch?: boolean;
    primaryLabel?: string;
    rows: Array<Record<string, any>>;
    showAction?: boolean;
    showStatus?: boolean;
    stats?: Array<{ label: string; value: number | string }>;
    statusField?: string;
    statusMeta?: Record<string, string>;
    title: string;
  }>(),
  {
    actions: () => [],
    keywordSearch: true,
    showAction: false,
    showStatus: false,
    stats: () => [],
    statusField: 'status',
    statusMeta: () => ({
      正常: 'success',
      启用: 'success',
      成功: 'success',
      已发布: 'success',
      已完成: 'success',
      待审核: 'warning',
      运行中: 'processing',
      停用: 'default',
      失败: 'error',
      异常: 'error',
      已驳回: 'error',
      草稿: 'default',
      通过: 'success',
      驳回: 'error',
      未发布: 'default',
      已下架: 'default',
      已关闭: 'default',
    }),
  },
);

const keyword = ref('');

const tableColumns = computed(() => [
  ...props.columns.map((column) => ({
    ...column,
    key: column.dataIndex,
  })),
  ...(props.showStatus
    ? [{ key: 'cms-status', title: '状态', width: 110 }]
    : []),
  ...(props.showAction
    ? [{ key: 'cms-action', title: '操作', width: 220 }]
    : []),
]);

const filteredRows = computed(() => {
  if (!props.keywordSearch || !keyword.value) {
    return props.rows;
  }
  const value = keyword.value.toLowerCase();
  return props.rows.filter((row) =>
    Object.values(row).some((item) =>
      String(item ?? '').toLowerCase().includes(value),
    ),
  );
});

function handleAction() {
  message.info('操作将在后端接口联调后开放');
}

function handlePrimary() {
  message.info('新增入口已就绪，等待接口联调');
}
</script>

<template>
  <Page>
    <div class="cms-page">
      <div class="cms-heading">
        <div>
          <p v-if="eyebrow" class="cms-eyebrow">{{ eyebrow }}</p>
          <h1>{{ title }}</h1>
        </div>
        <Button v-if="primaryLabel" type="primary" @click="handlePrimary">
          {{ primaryLabel }}
        </Button>
      </div>

      <div v-if="stats && stats.length" class="cms-stats">
        <div
          v-for="item in stats"
          :key="item.label"
          class="cms-stat-item"
        >
          <strong>{{ item.value }}</strong>
          <span>{{ item.label }}</span>
        </div>
      </div>

      <div v-if="keywordSearch || showStatus" class="cms-toolbar">
        <Input
          v-model:value="keyword"
          allow-clear
          class="cms-search"
          placeholder="输入关键词搜索"
        />
      </div>

      <Table
        :columns="tableColumns"
        :data-source="filteredRows"
        :pagination="{ pageSize: 10, showSizeChanger: true }"
        row-key="id"
        size="middle"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'cms-status'">
            <Tag :color="props.statusMeta[record[props.statusField]] || 'default'">
              {{ record[props.statusField] || '-' }}
            </Tag>
          </template>
          <template v-else-if="column.key === 'cms-action'">
            <Space :size="4">
              <Button
                v-for="action in actions"
                :key="action.label"
                :danger="action.danger"
                :type="action.type || 'link'"
                size="small"
                @click="handleAction"
              >
                {{ action.label }}
              </Button>
            </Space>
          </template>
        </template>
      </Table>
    </div>
  </Page>
</template>

<style scoped>
.cms-page {
  display: grid;
  gap: 16px;
}

.cms-heading {
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

.cms-stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.cms-stat-item {
  display: flex;
  min-height: 84px;
  padding: 14px 18px;
  border: 1px solid #e5e7eb;
  background: #fff;
  flex-direction: column;
  justify-content: center;
}

.cms-stat-item strong {
  color: #111827;
  font-size: 24px;
  line-height: 32px;
}

.cms-stat-item span {
  margin-top: 4px;
  color: #6b7280;
  font-size: 13px;
}

.cms-toolbar {
  display: flex;
}

.cms-search {
  width: min(340px, 100%);
}

@media (max-width: 900px) {
  .cms-heading {
    align-items: stretch;
    flex-direction: column;
  }

  .cms-stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
