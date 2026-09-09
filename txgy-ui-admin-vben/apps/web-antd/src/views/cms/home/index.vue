<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { Alert, Button, Card, Input, Modal, Select, Spin, Table, Tag, message } from 'ant-design-vue';
import { Page } from '@vben/common-ui';

import type { AiKnowledgeKnowledgeApi } from '#/api/ai/knowledge/knowledge';
import type { AiKnowledgeDocumentApi } from '#/api/ai/knowledge/document';
import {
  getSimpleKnowledgeList,
} from '#/api/ai/knowledge/knowledge';
import {
  createKnowledgeSegment,
  deleteKnowledgeSegment,
  searchKnowledgeSegment,
  updateKnowledgeSegment,
} from '#/api/ai/knowledge/segment';
import { getKnowledgeDocumentPage } from '#/api/ai/knowledge/document';

defineOptions({ name: 'CmsHome' });

const router = useRouter();

interface KnowledgeHit {
  id: number;
  documentId: number;
  knowledgeId: number;
  content: string;
  contentLength?: number;
  tokens?: number;
  score?: number;
  documentName?: string;
  knowledgeName?: string;
}

const keyword = ref('');
const knowledgeOptions = ref<AiKnowledgeKnowledgeApi.Knowledge[]>([]);
const queryKnowledgeId = ref<number>();
const loading = ref(false);
const searching = ref(false);
const rows = ref<KnowledgeHit[]>([]);
const selectedRowKeys = ref<(number | string)[]>([]);
const error = ref('');
const editorOpen = ref(false);
const saving = ref(false);
const editorId = ref<number>();
const editorKnowledgeId = ref<number>();
const editorDocumentId = ref<number>();
const editorContent = ref('');
const documentOptions = ref<Array<{ label: string; value: number }>>([]);

const selectedKnowledgeOptions = computed(() =>
  knowledgeOptions.value.map((item) => ({
    label: item.name,
    value: item.id,
  })),
);

const selectedDocumentOptions = computed(() => documentOptions.value);

function knowledgeName(knowledgeId?: number) {
  return (
    knowledgeOptions.value.find((item) => item.id === knowledgeId)?.name ||
    `知识库 ${knowledgeId ?? ''}`
  );
}

async function loadKnowledgeOptions() {
  try {
    knowledgeOptions.value = await getSimpleKnowledgeList();
  } catch {
    knowledgeOptions.value = [
      { id: 7, name: '行业法律法规与劳动法知识库', description: '', embeddingModelId: 0, topK: 5, similarityThreshold: 0.5 },
      { id: 8, name: '金融政策知识库', description: '', embeddingModelId: 0, topK: 6, similarityThreshold: 0.45 },
    ];
  }
  if (!queryKnowledgeId.value && knowledgeOptions.value.length) {
    queryKnowledgeId.value = knowledgeOptions.value[0].id;
  }
}

async function searchOne(knowledgeId: number) {
  const result = await searchKnowledgeSegment({
    knowledgeId,
    content: keyword.value.trim(),
  });
  return ((result || []) as KnowledgeHit[]).map((item) => ({
    ...item,
    knowledgeName: knowledgeName(item.knowledgeId || knowledgeId),
  }));
}

async function runSearch() {
  if (!keyword.value.trim()) {
    message.warning('请输入检索关键词。');
    return;
  }
  if (!knowledgeOptions.value.length) {
    message.warning('知识库列表尚未加载，请先刷新页面。');
    return;
  }
  searching.value = true;
  error.value = '';
  try {
    const targets = queryKnowledgeId.value
      ? [queryKnowledgeId.value]
      : knowledgeOptions.value.map((item) => item.id);
    const results = await Promise.all(targets.map((id) => searchOne(id)));
    rows.value = results.flat().sort((a, b) => (b.score || 0) - (a.score || 0));
    selectedRowKeys.value = [];
    if (!rows.value.length) message.info('未检索到相关内容。');
  } catch (e) {
    error.value = 'AI 检索接口调用失败，请检查 AI 服务、知识库向量和权限。';
    console.error(e);
  } finally {
    searching.value = false;
  }
}

async function loadDocuments(knowledgeId?: number) {
  documentOptions.value = [];
  editorDocumentId.value = undefined;
  if (!knowledgeId) return;
  try {
    const page = await getKnowledgeDocumentPage({
      pageNo: 1,
      pageSize: 100,
      knowledgeId,
    });
    const list = (page as any).list || (page as any).records || [];
    documentOptions.value = (list as AiKnowledgeDocumentApi.KnowledgeDocument[])
      .filter((item) => item.status !== 1)
      .map((item) => ({ label: `${item.name} (#${item.id})`, value: item.id }));
  } catch {
    message.error('读取知识库文档失败。');
  }
}

async function openCreate() {
  router.push('/cms/audit/submit');
}

async function openEdit(row: KnowledgeHit) {
  editorId.value = row.id;
  editorKnowledgeId.value = row.knowledgeId;
  editorContent.value = row.content;
  await loadDocuments(row.knowledgeId);
  editorDocumentId.value = row.documentId;
  editorOpen.value = true;
}

async function saveSegment() {
  if (!editorDocumentId.value || !editorContent.value.trim()) {
    message.warning('请选择知识库文档并填写切片内容。');
    return;
  }
  saving.value = true;
  try {
    if (editorId.value) {
      await updateKnowledgeSegment({
        id: editorId.value,
        documentId: editorDocumentId.value,
        content: editorContent.value.trim(),
      });
    } else {
      await createKnowledgeSegment({
        documentId: editorDocumentId.value,
        content: editorContent.value.trim(),
      });
    }
    message.success(editorId.value ? '切片已更新。' : '切片已新增。');
    editorOpen.value = false;
    if (rows.value.length) await runSearch();
  } finally {
    saving.value = false;
  }
}

async function deleteRows(ids: (number | string)[]) {
  loading.value = true;
  try {
    for (const id of ids) {
      await deleteKnowledgeSegment(Number(id));
    }
    message.success(`已删除 ${ids.length} 条切片。`);
    selectedRowKeys.value = [];
    if (rows.value.length) await runSearch();
  } finally {
    loading.value = false;
  }
}

function handleBatchDelete() {
  if (!selectedRowKeys.value.length) {
    message.warning('请先勾选要删除的切片。');
    return;
  }
  Modal.confirm({
    title: '确认删除',
    content: `确定删除选中的 ${selectedRowKeys.value.length} 条切片吗？`,
    okType: 'danger',
    onOk: () => deleteRows(selectedRowKeys.value),
  });
}

function handleRowDelete(row: KnowledgeHit) {
  Modal.confirm({
    title: '确认删除',
    content: '确定删除这条切片吗？',
    okType: 'danger',
    onOk: () => deleteRows([row.id]),
  });
}

const columns = [
  { title: '所属知识库', dataIndex: 'knowledgeName', width: 200 },
  { title: '文档名称', dataIndex: 'documentName', width: 260, ellipsis: true },
  { title: '切片编号', dataIndex: 'id', width: 110 },
  { title: '切片内容', dataIndex: 'content', minWidth: 380, ellipsis: true },
  { title: 'Token', dataIndex: 'tokens', width: 90 },
  { title: '相似度', key: 'score', width: 110 },
  { title: '操作', key: 'action', width: 150 },
];

onMounted(loadKnowledgeOptions);
</script>

<template>
  <Page>
    <div class="cms-home">
      <section class="cms-search-hero">
        <div class="cms-search-heading">
          <p>CMS 智能检索工作台</p>
          <h1>基于 AI 知识库检索法规、政策与专业知识</h1>
        </div>
        <div class="cms-big-search">
          <Select
            v-model:value="queryKnowledgeId"
            :options="selectedKnowledgeOptions"
            class="cms-search-select"
            allow-clear
            placeholder="全部知识库"
          />
          <Input
            v-model:value="keyword"
            class="cms-search-input"
            placeholder="输入标准号、名称、编号或关键词"
            size="large"
            @press-enter="runSearch"
          />
          <Button type="primary" size="large" :loading="searching" @click="runSearch">
            检索
          </Button>
          <Button size="large" @click="openCreate">新增</Button>
          <Button size="large" danger :loading="loading" @click="handleBatchDelete">
            删除
          </Button>
        </div>
        <div class="search-hint">
          <span>检索调用 AI 知识库分段接口；未选择知识库时遍历全部知识库。</span>
        </div>
      </section>

      <Alert v-if="error" type="error" :message="error" show-icon class="block-alert" />
      <Card title="检索结果">
        <template #extra>
          <span>{{ rows.length }} 条</span>
        </template>
        <Spin :spinning="searching || loading">
          <Table
            :columns="columns"
            :data-source="rows"
            row-key="id"
            :pagination="{ pageSize: 10, showSizeChanger: true }"
            :row-selection="{
              selectedRowKeys,
              onChange: (keys: any[]) => (selectedRowKeys = keys),
            }"
            :locale="{ emptyText: '输入关键词后点击“检索”' }"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'score'">
                <Tag color="blue">{{ Number(record.score ?? 0).toFixed(3) }}</Tag>
              </template>
              <template v-else-if="column.key === 'action'">
                <Button type="link" @click="openEdit(record)">编辑</Button>
                <Button type="link" danger @click="handleRowDelete(record)">删除</Button>
              </template>
            </template>
          </Table>
        </Spin>
      </Card>
    </div>

    <Modal
      v-model:open="editorOpen"
      :title="editorId ? '编辑切片' : '新增切片'"
      :confirm-loading="saving"
      ok-text="保存"
      @ok="saveSegment"
    >
      <div class="fields">
        <label>知识库
          <Select
            v-model:value="editorKnowledgeId"
            :options="selectedKnowledgeOptions"
            :disabled="Boolean(editorId)"
            @change="loadDocuments(editorKnowledgeId)"
          />
        </label>
        <label>所属文档
          <Select
            v-model:value="editorDocumentId"
            :options="selectedDocumentOptions"
            placeholder="请选择文档"
          />
        </label>
      </div>
      <label>切片内容
        <Input.TextArea
          v-model:value="editorContent"
          :rows="10"
          :maxlength="5000"
          placeholder="请输入切片内容"
        />
      </label>
    </Modal>
  </Page>
</template>

<style scoped>
.cms-home { display: grid; gap: 16px; }
.cms-search-hero { padding: 26px 28px; border: 1px solid #e2e8f0; border-radius: 8px; background: #fff; }
.cms-search-heading p { margin: 0 0 6px; color: #1d4ed8; font-size: 13px; font-weight: 600; }
.cms-search-heading h1 { margin: 0; color: #0f172a; font-size: 24px; line-height: 32px; }
.cms-big-search { display: grid; grid-template-columns: 240px minmax(0, 1fr) auto auto auto; gap: 10px; margin-top: 18px; }
.search-hint { margin-top: 10px; color: #64748b; font-size: 13px; }
.block-alert { margin-bottom: 0; }
.fields { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; margin-bottom: 14px; }
label { display: grid; gap: 6px; color: #334155; font-size: 13px; margin-bottom: 12px; }
@media (max-width: 900px) {
  .cms-big-search { grid-template-columns: 1fr; }
  .fields { grid-template-columns: 1fr; }
}
</style>
