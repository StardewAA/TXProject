<script setup lang="ts">
import { onMounted, ref } from 'vue';

import { Page } from '@vben/common-ui';
import {
  Alert,
  Button,
  Card,
  Modal,
  Spin,
  Table,
  Tabs,
  Tag,
  message,
} from 'ant-design-vue';

import type { CmsKnowledgeItem } from '#/api/cms/knowledge';
import {
  listCmsKnowledge,
  listCmsArchives,
  archiveCmsKnowledge,
  reindexCmsKnowledge,
} from '#/api/cms/knowledge';
import PublicationSubmit from '../review/parts/submit/index.vue';
import '../_shared/cms-glass-theme.css';

defineOptions({ name: 'CmsPublishManagement' });

const rows = ref<CmsKnowledgeItem[]>([]);
const archives = ref<CmsKnowledgeItem[]>([]);
const activeTab = ref('submit');
const loading = ref(false);
const operatingId = ref('');
const error = ref('');

const columns = [
  { title: '文档名称', dataIndex: 'title', key: 'title' },
  { title: '效力状态', dataIndex: 'validity', key: 'validity', width: 130 },
  { title: '切片数', dataIndex: 'chunkCount', key: 'chunkCount', width: 100 },
  { title: '发布时间', dataIndex: 'createdAt', key: 'createdAt', width: 190 },
  { title: '操作', key: 'action', width: 170 },
];

const validityNames: Record<string, string> = {
  EFFECTIVE: '现行有效',
  EXPIRED: '已失效',
  NOT_YET_EFFECTIVE: '尚未生效',
  REPEALED: '已废止',
  UNKNOWN: '待核实',
};

async function refresh() {
  loading.value = true;
  error.value = '';
  try {
    [rows.value, archives.value] = await Promise.all([
      listCmsKnowledge(),
      listCmsArchives(),
    ]);
  } catch (exception) {
    console.error(exception);
    error.value = '读取待发布知识库失败，请检查 CMS 服务和权限。';
  } finally {
    loading.value = false;
  }
}

function confirmArchive(row: Record<string, any>) {
  const item = row as CmsKnowledgeItem;
  Modal.confirm({
    title: '确认归档',
    content: `归档“${item.title}”后将移出主知识库检索，但审查与发布历史会保留。`,
    okText: '确认归档',
    okType: 'danger',
    async onOk() {
      operatingId.value = item.reviewId;
      try {
        await archiveCmsKnowledge(item.reviewId);
        message.success('已软删除并移入归档库，检索索引已移除。');
        await refresh();
      } finally {
        operatingId.value = '';
      }
    },
  });
}

async function reindex() {
  loading.value = true;
  try {
    const result = await reindexCmsKnowledge();
    message.success(
      `索引重建完成：关键词 ${result.keywordChunks} 条，向量 ${result.vectorChunks} 条。`,
    );
  } finally {
    loading.value = false;
  }
}

onMounted(refresh);
</script>

<template>
  <Page>
    <div class="cms-glass-page publish-page">
      <header class="cms-glass-hero">
        <div>
          <p>CMS 系统 · 发布管理</p>
          <h1>内容发布与归档</h1>
          <span>录入内容、查看已发布资料，并对历史内容进行可追溯归档。</span>
        </div>
        <div class="header-actions">
          <Button @click="refresh">刷新</Button>
          <Button
            v-access:code="['cms:knowledge:reindex']"
            :loading="loading"
            @click="reindex"
            >重建检索索引</Button
          >
        </div>
      </header>

      <Alert
        message="所有发布内容必须经过异步 AI 初审和管理员人工复核，人工批准后才进入检索知识库。"
        show-icon
        type="info"
      />
      <Alert v-if="error" :message="error" show-icon type="error" />

      <Tabs v-model:active-key="activeTab" type="card">
        <Tabs.TabPane key="submit" tab="发布内容">
          <PublicationSubmit embedded />
        </Tabs.TabPane>
        <Tabs.TabPane key="published" tab="已发布内容">
          <Card title="已发布内容">
            <Spin :spinning="loading">
              <Table
                :columns="columns"
                :data-source="rows"
                row-key="reviewId"
                :pagination="{ pageSize: 10 }"
              >
                <template #bodyCell="{ column, record }">
                  <template v-if="column.key === 'title'">
                    <strong>{{ record.title }}</strong>
                    <div>
                      <a
                        :href="record.sourceUrl"
                        rel="noopener noreferrer"
                        target="_blank"
                        >官方来源</a
                      >
                    </div>
                  </template>
                  <template v-else-if="column.key === 'validity'">
                    <Tag>{{
                      validityNames[record.validity] || record.validity
                    }}</Tag>
                  </template>
                  <template v-else-if="column.key === 'action'">
                    <Button
                      v-access:code="['cms:knowledge:delete']"
                      danger
                      type="link"
                      :loading="operatingId === record.reviewId"
                      @click="confirmArchive(record)"
                      >归档</Button
                    >
                  </template>
                </template>
              </Table>
            </Spin>
          </Card>
        </Tabs.TabPane>
        <Tabs.TabPane key="archive" tab="归档库">
          <Card title="归档内容（保留历史，不参与检索）">
            <Table
              :columns="columns.filter((item) => item.key !== 'action')"
              :data-source="archives"
              row-key="reviewId"
              :pagination="{ pageSize: 10 }"
            >
              <template #bodyCell="{ column, record }">
                <template v-if="column.key === 'title'"
                  ><strong>{{ record.title }}</strong></template
                >
                <template v-else-if="column.key === 'validity'"
                  ><Tag>{{
                    validityNames[record.validity] || record.validity
                  }}</Tag></template
                >
              </template>
            </Table>
          </Card>
        </Tabs.TabPane>
      </Tabs>
    </div>
  </Page>
</template>

<style scoped>
.header-actions {
  display: flex;
  flex-shrink: 0;
  gap: 9px;
}

@media (max-width: 720px) {
  .header-actions {
    width: 100%;
  }

  .header-actions :deep(.ant-btn) {
    flex: 1;
  }
}
</style>
