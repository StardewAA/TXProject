<script lang="ts" setup>
import { computed, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { Button, Input, Space, Table, Tag, message } from 'ant-design-vue';

import './cms-glass-theme.css';

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
      String(item ?? '')
        .toLowerCase()
        .includes(value),
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
    <div class="cms-glass-page cms-page">
      <div class="cms-glass-hero cms-heading">
        <div>
          <p v-if="eyebrow" class="cms-eyebrow">{{ eyebrow }}</p>
          <h1>{{ title }}</h1>
        </div>
        <Button v-if="primaryLabel" type="primary" @click="handlePrimary">
          {{ primaryLabel }}
        </Button>
      </div>

      <div v-if="stats && stats.length" class="cms-stats">
        <div v-for="item in stats" :key="item.label" class="cms-stat-item">
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
            <Tag
              :color="props.statusMeta[record[props.statusField]] || 'default'"
            >
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
  gap: 16px;
  align-items: center;
  justify-content: space-between;
}

.cms-eyebrow {
  margin: 0 0 6px;
  font-size: 13px;
  font-weight: 600;
  color: #1d4ed8;
}

h1 {
  margin: 0;
  font-size: 24px;
  line-height: 32px;
  color: #111827;
}

.cms-stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.cms-stat-item {
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: center;
  min-height: 104px;
  padding: 18px 20px;
  overflow: hidden;
  background: rgb(255 255 255 / 66%);
  border: 1px solid rgb(255 255 255 / 78%);
  border-radius: 17px;
  box-shadow:
    0 14px 35px rgb(43 70 105 / 8%),
    inset 0 1px 0 rgb(255 255 255 / 88%);
  backdrop-filter: blur(18px);
}

.cms-stat-item strong {
  font-size: 26px;
  font-weight: 680;
  line-height: 32px;
  color: #1a3153;
}

.cms-stat-item span {
  margin-top: 4px;
  font-size: 12px;
  color: #7d8ba0;
}

.cms-toolbar {
  display: flex;
  padding: 14px;
  background: rgb(255 255 255 / 56%);
  border: 1px solid rgb(255 255 255 / 75%);
  border-radius: 15px;
  box-shadow: 0 12px 30px rgb(44 70 105 / 6%);
  backdrop-filter: blur(16px);
}

.cms-search {
  width: min(340px, 100%);
}

@media (max-width: 900px) {
  .cms-heading {
    flex-direction: column;
    align-items: stretch;
  }

  .cms-stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
