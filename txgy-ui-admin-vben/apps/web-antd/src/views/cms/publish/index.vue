<script setup lang="ts">
import { onMounted, ref } from 'vue';

import { Page } from '@vben/common-ui';
import { Alert, Button, Card, Modal, Spin, Table, Tag, message } from 'ant-design-vue';

import type { CmsKnowledgeItem } from '#/api/cms/knowledge';
import {
  listCmsKnowledge,
  publishCmsKnowledge,
  reindexCmsKnowledge,
  unpublishCmsKnowledge,
} from '#/api/cms/knowledge';

defineOptions({ name: 'CmsPublishManagement' });

const rows = ref<CmsKnowledgeItem[]>([]);
const loading = ref(false);
const operatingId = ref('');
const error = ref('');

const columns = [
  { title: '文档名称', dataIndex: 'title', key: 'title' },
  { title: '效力状态', dataIndex: 'validity', key: 'validity', width: 130 },
  { title: '切片数', dataIndex: 'chunkCount', key: 'chunkCount', width: 100 },
  { title: '发布状态', dataIndex: 'published', key: 'published', width: 120 },
  { title: '审查入库时间', dataIndex: 'createdAt', key: 'createdAt', width: 190 },
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
    rows.value = await listCmsKnowledge();
  } catch (exception) {
    console.error(exception);
    error.value = '读取待发布知识库失败，请检查 CMS 服务和权限。';
  } finally {
    loading.value = false;
  }
}

async function publish(row: Record<string, any>) {
  const item = row as CmsKnowledgeItem;
  operatingId.value = item.reviewId;
  try {
    const result = await publishCmsKnowledge(item.reviewId);
    message.success(`发布成功，已写入 ${result.keywordChunks} 个可检索片段。`);
    await refresh();
  } finally {
    operatingId.value = '';
  }
}

function confirmUnpublish(row: Record<string, any>) {
  const item = row as CmsKnowledgeItem;
  Modal.confirm({
    title: '确认下架',
    content: `下架“${item.title}”后，企业用户将无法检索到该内容。`,
    okText: '确认下架',
    okType: 'danger',
    async onOk() {
      operatingId.value = item.reviewId;
      try {
        await unpublishCmsKnowledge(item.reviewId);
        message.success('已下架，并从关键词与向量索引中移除。');
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
    message.success(`索引重建完成：关键词 ${result.keywordChunks} 条，向量 ${result.vectorChunks} 条。`);
  } finally {
    loading.value = false;
  }
}

onMounted(refresh);
</script>

<template>
  <Page>
    <div class="publish-page">
      <header>
        <div>
          <p>CMS 系统 · 发布管理</p>
          <h1>审查通过内容发布</h1>
          <span>列表仅包含已通过官方来源与效力核验的文档。</span>
        </div>
        <div class="header-actions">
          <Button @click="refresh">刷新</Button>
          <Button v-access:code="['cms:knowledge:reindex']" :loading="loading" @click="reindex">重建检索索引</Button>
        </div>
      </header>

      <Alert
        message="未通过审查的申请不会进入本列表，也无法绕过审查直接发布。"
        show-icon
        type="info"
      />
      <Alert v-if="error" :message="error" show-icon type="error" />

      <Card title="待发布与已发布内容">
        <Spin :spinning="loading">
          <Table :columns="columns" :data-source="rows" row-key="reviewId" :pagination="{ pageSize: 10 }">
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'title'">
                <strong>{{ record.title }}</strong>
                <div><a :href="record.sourceUrl" rel="noopener noreferrer" target="_blank">官方来源</a></div>
              </template>
              <template v-else-if="column.key === 'validity'">
                <Tag>{{ validityNames[record.validity] || record.validity }}</Tag>
              </template>
              <template v-else-if="column.key === 'published'">
                <Tag :color="record.published ? 'green' : 'orange'">{{ record.published ? '已发布' : '待发布' }}</Tag>
              </template>
              <template v-else-if="column.key === 'action'">
                <Button
                  v-if="!record.published"
                  v-access:code="['cms:knowledge:publish']"
                  type="link"
                  :loading="operatingId === record.reviewId"
                  @click="publish(record)"
                >发布</Button>
                <Button
                  v-else
                  v-access:code="['cms:knowledge:delete']"
                  danger
                  type="link"
                  :loading="operatingId === record.reviewId"
                  @click="confirmUnpublish(record)"
                >下架</Button>
              </template>
            </template>
          </Table>
        </Spin>
      </Card>
    </div>
  </Page>
</template>

<style scoped>
.publish-page { display: grid; gap: 16px; }
header { display: flex; align-items: flex-start; justify-content: space-between; gap: 18px; }
header p { margin: 0 0 6px; color: #2563eb; font-size: 13px; font-weight: 700; }
header h1 { margin: 0; color: #0f172a; font-size: 28px; }
header span { display: block; margin-top: 8px; color: #64748b; }
.header-actions { display: flex; gap: 8px; }
</style>
