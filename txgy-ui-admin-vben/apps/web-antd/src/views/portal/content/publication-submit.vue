<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';

import {
  Alert,
  Button,
  Card,
  Checkbox,
  Input,
  Select,
  Spin,
  Table,
  Tag,
  message,
} from 'ant-design-vue';

import type { ReviewMetadata } from '#/api/cms/review';
import type { PublicationSubmission } from '#/api/cms/submission';
import {
  listMyPublicationSubmissions,
  submitPublicationFile,
  submitPublicationText,
} from '#/api/cms/submission';

const metadata = reactive<ReviewMetadata>({
  documentNo: '',
  effectiveDate: null,
  expiryDate: null,
  historical: false,
  issuer: '',
  kind: 'LAW',
  sourceUrl: '',
  title: '',
  validity: 'UNKNOWN',
});
const mode = ref<'file' | 'text'>('file');
const text = ref('');
const file = ref<File>();
const busy = ref(false);
const loading = ref(false);
const rows = ref<PublicationSubmission[]>([]);

const kindOptions = [
  { label: '法律法规', value: 'LAW' },
  { label: '国家标准', value: 'STANDARD' },
  { label: '政策文件', value: 'POLICY' },
];
const validityOptions = [
  { label: '待管理员核实', value: 'UNKNOWN' },
  { label: '现行有效', value: 'EFFECTIVE' },
  { label: '尚未生效', value: 'NOT_YET_EFFECTIVE' },
  { label: '已废止', value: 'REPEALED' },
  { label: '已失效', value: 'EXPIRED' },
];
const columns = [
  { title: '文档名称', key: 'title' },
  { title: '提交时间', key: 'createdAt', width: 190 },
  { title: '审查结果', key: 'review', width: 150 },
  { title: '发布状态', key: 'published', width: 140 },
];

function cleanMetadata(): ReviewMetadata {
  return {
    ...metadata,
    documentNo: metadata.documentNo.trim(),
    effectiveDate: metadata.effectiveDate || null,
    expiryDate: metadata.expiryDate || null,
    issuer: metadata.issuer.trim(),
    sourceUrl: metadata.sourceUrl.trim(),
    title: metadata.title.trim(),
  };
}

function chooseFile(event: Event) {
  file.value = (event.target as HTMLInputElement).files?.[0];
}

async function refresh() {
  loading.value = true;
  try {
    rows.value = await listMyPublicationSubmissions();
  } finally {
    loading.value = false;
  }
}

async function submit() {
  if (!metadata.title.trim() || !metadata.issuer.trim() || !metadata.sourceUrl.trim()) {
    message.warning('请填写标题、发布机关和官方原文链接。');
    return;
  }
  if (metadata.kind === 'STANDARD' && !metadata.documentNo.trim()) {
    message.warning('国家标准必须填写标准编号。');
    return;
  }
  if (mode.value === 'file' && !file.value) {
    message.warning('请选择待发布的 TXT、DOCX 或 PDF 文档。');
    return;
  }
  if (mode.value === 'text' && !text.value.trim()) {
    message.warning('请粘贴完整正文。');
    return;
  }
  busy.value = true;
  try {
    if (mode.value === 'file' && file.value) {
      await submitPublicationFile(cleanMetadata(), file.value);
    } else {
      await submitPublicationText(cleanMetadata(), text.value.trim());
    }
    message.success('发布申请已提交，通过管理员审查和发布后才会进入检索。');
    text.value = '';
    file.value = undefined;
    await refresh();
  } finally {
    busy.value = false;
  }
}

function reviewLabel(row: Record<string, any>) {
  const item = row as PublicationSubmission;
  if (item.report.admitted) return '审查通过';
  if (!item.report.evidence) return '待管理员审查';
  return '审查未通过';
}

function reviewColor(row: Record<string, any>) {
  const item = row as PublicationSubmission;
  if (item.report.admitted) return 'green';
  if (!item.report.evidence) return 'blue';
  return 'red';
}

onMounted(refresh);
</script>

<template>
  <div class="publication-panel">
    <Alert
      message="企业提交的内容是发布申请，不会直接对外发布。管理员完成来源、效力、规范性和质量审查后，才能执行发布。"
      show-icon
      type="info"
    />
    <Card title="提交发布申请">
      <div class="fields">
        <label>文档标题<Input v-model:value="metadata.title" :maxlength="300" /></label>
        <label>文号 / 标准编号<Input v-model:value="metadata.documentNo" :maxlength="100" /></label>
        <label>发布机关<Input v-model:value="metadata.issuer" :maxlength="200" /></label>
        <label>官方原文链接<Input v-model:value="metadata.sourceUrl" :maxlength="2000" placeholder="https://….gov.cn/…" /></label>
        <label>文档类型<Select v-model:value="metadata.kind" :options="kindOptions" /></label>
        <label>申报效力<Select v-model:value="metadata.validity" :options="validityOptions" /></label>
        <label>实施日期<input v-model="metadata.effectiveDate" type="date" /></label>
        <label>最后有效日期<input v-model="metadata.expiryDate" type="date" /></label>
      </div>
      <Checkbox v-model:checked="metadata.historical">作为历史资料申报</Checkbox>
      <div class="body-input">
        <Select
          v-model:value="mode"
          :options="[{ label: '上传文件', value: 'file' }, { label: '粘贴正文', value: 'text' }]"
        />
        <input v-if="mode === 'file'" type="file" accept=".txt,.docx,.pdf" @change="chooseFile" />
        <Input.TextArea v-else v-model:value="text" :maxlength="1000000" :rows="12" placeholder="粘贴完整原文，保留条号与段落。" />
      </div>
      <Button type="primary" :loading="busy" @click="submit">提交审查并申请发布</Button>
    </Card>

    <Card title="我的发布申请">
      <template #extra><Button :disabled="loading" @click="refresh">刷新</Button></template>
      <Spin :spinning="loading">
        <Table
          :columns="columns"
          :data-source="rows"
          :row-key="(row: PublicationSubmission) => row.report.id"
          :pagination="{ pageSize: 10 }"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'title'">{{ record.report.metadata.title }}</template>
            <template v-else-if="column.key === 'createdAt'">{{ record.report.createdAt }}</template>
            <template v-else-if="column.key === 'review'">
              <Tag :color="reviewColor(record)">{{ reviewLabel(record) }}</Tag>
            </template>
            <template v-else-if="column.key === 'published'">
              <Tag :color="record.published ? 'green' : 'default'">
                {{ record.published ? '已发布' : record.report.admitted ? '待管理员发布' : '未发布' }}
              </Tag>
            </template>
          </template>
        </Table>
      </Spin>
    </Card>
  </div>
</template>

<style scoped>
.publication-panel { display: grid; gap: 16px; }
.fields { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; margin-bottom: 14px; }
label { display: grid; gap: 6px; color: #334155; font-size: 13px; }
label > input { min-height: 32px; padding: 4px 10px; border: 1px solid #d9d9d9; border-radius: 6px; }
.body-input { display: grid; gap: 10px; margin: 16px 0; }
.body-input :deep(.ant-select) { width: 180px; }
@media (max-width: 800px) { .fields { grid-template-columns: 1fr; } }
</style>
