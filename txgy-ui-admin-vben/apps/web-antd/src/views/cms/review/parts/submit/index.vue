<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import {
  Alert,
  Button,
  Card,
  Checkbox,
  Input,
  Modal,
  Select,
  message,
} from 'ant-design-vue';

import type { BatchReviewItem, ReviewMetadata } from '#/api/cms/review';
import {
  batchUploadReviews,
  checkReviewDuplicate,
  getReview,
  submitText,
  uploadReview,
} from '#/api/cms/review';
import { parsePublicationUrl } from '#/api/cms/submission';
import CmsFilePicker from '../../../_shared/cms-file-picker.vue';
import CmsWangEditor from '../../../_shared/cms-wang-editor.vue';
import {
  editorHtmlToPlainText,
  plainTextToEditorHtml,
} from '../../../_shared/rich-text';
import '../../../_shared/cms-glass-theme.css';

defineOptions({ name: 'CmsReviewSubmit' });
withDefaults(defineProps<{ embedded?: boolean }>(), { embedded: false });

const router = useRouter();
const route = useRoute();
const metadata = reactive<ReviewMetadata>({
  title: '',
  documentNo: '',
  issuer: '',
  sourceUrl: '',
  kind: 'LAW',
  validity: 'UNKNOWN',
  effectiveDate: null,
  expiryDate: null,
  historical: false,
});
const text = ref('');
const file = ref<File>();
const mode = ref<'file' | 'text'>('text');
const parseWarning = ref('');
const busy = ref(false);
const batchOpen = ref(false);
const batchRows = ref<BatchReviewItem[]>([]);
const resubmitId = ref('');
const rejectionReason = ref('');

const kinds = [
  { value: 'LAW', label: '法律法规' },
  { value: 'STANDARD', label: '国家标准' },
  { value: 'POLICY', label: '政策文件' },
];
const validities = [
  { value: 'UNKNOWN', label: '待核实' },
  { value: 'EFFECTIVE', label: '现行有效' },
  { value: 'NOT_YET_EFFECTIVE', label: '尚未生效' },
  { value: 'REPEALED', label: '已废止' },
  { value: 'EXPIRED', label: '已失效' },
];

function cleanMetadata(value: ReviewMetadata): ReviewMetadata {
  return {
    ...value,
    title: value.title.trim(),
    documentNo: value.documentNo.trim(),
    issuer: value.issuer.trim(),
    sourceUrl: value.sourceUrl.trim(),
    effectiveDate: value.effectiveDate || null,
    expiryDate: value.expiryDate || null,
  };
}

function validMetadata(value: ReviewMetadata, sourceRequired: boolean) {
  if (
    !value.title.trim() ||
    (value.kind === 'STANDARD' && !value.documentNo.trim()) ||
    !value.issuer.trim() ||
    (sourceRequired && !value.sourceUrl.trim())
  ) {
    message.warning(
      sourceRequired
        ? '请填写标题、发布机关和来源链接；国家标准必须填写标准编号。'
        : '请填写标题和发布机关；国家标准必须填写标准编号。',
    );
    return false;
  }
  return true;
}

function standardNumber(filename: string) {
  const match = filename.match(
    /GB\s*[\/／∕]?\s*T?\s*(\d+(?:\.\d+)?)\s*[-—]\s*(\d{4})/i,
  );
  return match ? `GB/T ${match[1]}-${match[2]}` : '';
}

function chooseBatchFiles(event: Event) {
  const files = Array.from((event.target as HTMLInputElement).files || []);
  if (!files.length) return;
  if (
    files.length > 20 ||
    files.reduce((sum, current) => sum + current.size, 0) > 75 * 1024 * 1024
  ) {
    message.warning('一次最多20份，合计不超过75MB，每份不超过25MB。');
    return;
  }
  if (new Set(files.map((current) => current.name)).size !== files.length) {
    message.warning('文件名不能重复。');
    return;
  }
  const base = cleanMetadata(metadata);
  batchRows.value = files.map((current) => {
    const number = standardNumber(current.name);
    const title = current.name
      .replace(/\.(docx|pdf|txt)$/i, '')
      .replace(/\s*\(\d+\)$/, '')
      .replace(/^GB\s*[\/／∕]?\s*T?\s*\d+(?:\.\d+)?\s*[-—]\s*\d{4}\s*/i, '')
      .trim();
    return {
      file: current,
      metadata: {
        ...base,
        title: title || current.name,
        documentNo: number,
        kind: number ? 'STANDARD' : base.kind,
      },
    };
  });
}

async function parseUrl() {
  const requestedUrl = metadata.sourceUrl.trim();
  if (!requestedUrl) {
    message.warning('请输入政府网站 HTTPS 原文链接。');
    return;
  }
  busy.value = true;
  try {
    const result = await parsePublicationUrl(requestedUrl);
    Object.assign(metadata, result.metadata);
    metadata.sourceUrl = result.metadata.sourceUrl?.trim() || requestedUrl;
    text.value = plainTextToEditorHtml(result.text);
    mode.value = 'text';
    parseWarning.value = result.warning || '';
    message.success(
      result.llmExtracted
        ? '千问已提取元数据，请确认补全后提交。'
        : '网页已解析，请确认补全元数据。',
    );
  } finally {
    busy.value = false;
  }
}

async function submit() {
  if (!validMetadata(metadata, mode.value !== 'file' && !resubmitId.value))
    return;
  if (
    mode.value === 'file' &&
    (!file.value || file.value.size > 25 * 1024 * 1024)
  ) {
    message.warning('请选择不超过25MB的文档。');
    return;
  }
  const plainText = editorHtmlToPlainText(text.value);
  if (mode.value !== 'file' && !plainText) {
    message.warning('请填写完整正文。');
    return;
  }
  if (plainText.length > 500_000) {
    message.warning('正文最多 50 万字，请精简后再提交。');
    return;
  }
  busy.value = true;
  try {
    if (mode.value !== 'file' && !resubmitId.value) {
      const duplicate = await checkReviewDuplicate(
        cleanMetadata(metadata),
        plainText,
      );
      if (duplicate.duplicate) {
        message.error(`提交已阻止：${duplicate.reason || '内容重复'}`);
        return;
      }
    }
    const result =
      mode.value === 'file' && file.value
        ? await uploadReview(cleanMetadata(metadata), file.value)
        : await submitText(
            cleanMetadata(metadata),
            plainText,
            resubmitId.value || undefined,
          );
    message.success(
      resubmitId.value
        ? '修改内容已重新提交，并重新进入 AI 初审。'
        : '发布内容已提交，AI 初审完成后将进入待人工复核队列。',
    );
    router.push(`/cms/review/record?id=${result.id}`);
  } finally {
    busy.value = false;
  }
}

async function loadResubmission() {
  const id = typeof route.query.resubmit === 'string' ? route.query.resubmit : '';
  if (!/^[a-f0-9-]{36}$/.test(id)) return;
  busy.value = true;
  try {
    const detail = await getReview(id);
    if (detail.report.workflowStatus !== 'REJECTED') {
      message.warning('只有已被人工驳回的内容可以修改后重新提交。');
      return;
    }
    resubmitId.value = id;
    rejectionReason.value = detail.report.manualNote || '';
    Object.assign(metadata, detail.report.metadata);
    text.value = plainTextToEditorHtml(detail.text);
    file.value = undefined;
    mode.value = 'text';
  } finally {
    busy.value = false;
  }
}

onMounted(loadResubmission);

async function submitBatch() {
  if (!batchRows.value.length) {
    message.warning('请先选择待审文件。');
    return;
  }
  if (
    batchRows.value.some(
      ({ file: current, metadata: value }) =>
        current.size > 25 * 1024 * 1024 || !validMetadata(value, false),
    )
  )
    return;
  busy.value = true;
  try {
    const result = await batchUploadReviews(
      batchRows.value.map(({ file: current, metadata: value }) => ({
        file: current,
        metadata: cleanMetadata(value),
      })),
    );
    batchOpen.value = false;
    batchRows.value = [];
    message.success(
      `批次 ${result.batchId.slice(0, 8)} 已生成 ${result.total} 条审查记录。`,
    );
    if (result.items[0])
      router.push(`/cms/review/record?id=${result.items[0].report.id}`);
  } finally {
    busy.value = false;
  }
}
</script>

<template>
  <main class="cms-glass-page review-page">
    <header v-if="!embedded" class="cms-glass-hero">
      <h1>发布内容</h1>
      <p>录入待发布内容，提交后异步进行 AI 合规初审并进入人工复核。</p>
    </header>
    <Card title="发布内容">
      <Alert
        v-if="resubmitId"
        type="warning"
        show-icon
        :message="`正在修改被驳回的申请${rejectionReason ? `：${rejectionReason}` : ''}`"
        class="resubmit-alert"
      />
      <div class="fields">
        <label
          >文档标题<Input v-model:value="metadata.title" :maxlength="300"
        /></label>
        <label v-if="metadata.kind === 'STANDARD'"
          >标准编号（国标必填）<Input
            v-model:value="metadata.documentNo"
            :maxlength="100"
            placeholder="例如：GB/T 23257-2017"
        /></label>
        <label
          >发布机关<Input v-model:value="metadata.issuer" :maxlength="200"
        /></label>
        <label>
          官方原文链接（上传本地文件可不填）
          <div class="source-url-field">
            <Input
              v-model:value="metadata.sourceUrl"
              placeholder="https://…gov.cn/…"
              :maxlength="2000"
              @press-enter="parseUrl"
            />
            <Button :loading="busy" @click="parseUrl">智能解析</Button>
          </div>
          <small class="source-url-help">
            URL 解析需填写具体详情页；上传本地文件可留空，AI
            将标记来源待核验，由管理员补充官方依据后才能发布。
          </small>
        </label>
        <label
          >文档类型<Select v-model:value="metadata.kind" :options="kinds"
        /></label>
        <label
          >声明效力<Select
            v-model:value="metadata.validity"
            :options="validities"
        /></label>
        <label
          >实施日期<input v-model="metadata.effectiveDate" type="date"
        /></label>
      </div>
      <Checkbox v-model:checked="metadata.historical"
        >作为历史资料归档</Checkbox
      >
      <div class="body-input">
        <Select
          v-model:value="mode"
          :disabled="Boolean(resubmitId)"
          :options="[
            { value: 'text', label: '手动录入' },
            { value: 'file', label: '上传文件' },
          ]"
          aria-label="提交方式"
        />
        <Alert
          v-if="parseWarning"
          :message="parseWarning"
          show-icon
          type="warning"
        />
        <CmsWangEditor v-if="mode === 'text'" v-model="text" :height="360" />
        <CmsFilePicker v-else v-model="file" />
      </div>
      <div class="actions">
        <Button
          v-access:code="['cms:review:submit']"
          type="primary"
          :loading="busy"
          @click="submit"
          >{{ resubmitId ? '修改后重新提交' : '提交发布并送审' }}</Button
        >
        <Button
          v-access:code="['cms:review:submit']"
          :disabled="busy"
          @click="batchOpen = true"
          >批量送审</Button
        >
      </div>
    </Card>

    <Modal
      v-model:open="batchOpen"
      title="批量送审"
      :width="1000"
      :confirm-loading="busy"
      ok-text="提交整批审查"
      @ok="submitBatch"
    >
      <Alert
        type="info"
        show-icon
        message="一次最多20份、合计75MB、单份25MB。先校验整批清单，再用一个数据库事务保存。"
      />
      <div class="batch-picker">
        <input
          type="file"
          multiple
          accept=".txt,.docx,.pdf"
          aria-label="选择批量待审文件"
          @change="chooseBatchFiles"
        />
      </div>
      <section
        v-for="(row, index) in batchRows"
        :key="row.file.name"
        class="batch-row"
      >
        <div class="batch-heading">
          <strong>{{ index + 1 }}. {{ row.file.name }}</strong>
          <Button type="link" danger @click="batchRows.splice(index, 1)"
            >移除</Button
          >
        </div>
        <div class="fields">
          <label
            >文档标题<Input v-model:value="row.metadata.title" :maxlength="300"
          /></label>
          <label v-if="row.metadata.kind === 'STANDARD'"
            >标准编号（国标必填）<Input
              v-model:value="row.metadata.documentNo"
              :maxlength="100"
              placeholder="例如：GB/T 23257-2017"
          /></label>
          <label
            >发布机关<Input
              v-model:value="row.metadata.issuer"
              :maxlength="200"
          /></label>
          <label
            >官方原文链接（选填）<Input
              v-model:value="row.metadata.sourceUrl"
              :maxlength="2000"
          /></label>
          <label
            >文档类型<Select v-model:value="row.metadata.kind" :options="kinds"
          /></label>
          <label
            >声明效力<Select
              v-model:value="row.metadata.validity"
              :options="validities"
          /></label>
          <label
            >实施日期<input v-model="row.metadata.effectiveDate" type="date"
          /></label>
        </div>
        <Checkbox v-model:checked="row.metadata.historical"
          >作为历史资料归档</Checkbox
        >
      </section>
      <p v-if="!batchRows.length" class="empty">
        选择文件后会尝试从文件名识别国标编号；每份资料仍需分别确认标题、机关、官方链接和效力。
      </p>
    </Modal>
  </main>
</template>

<style scoped>
.review-page:not(.publication-panel) {
  padding: 6px;
}

.fields {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 15px 18px;
  margin-bottom: 18px;
}

label {
  display: grid;
  gap: 7px;
  font-size: 12px;
  color: #43536a;
}

.body-input {
  display: grid;
  gap: 12px;
  padding-top: 2px;
  margin: 18px 0;
}

.actions {
  display: flex;
  gap: 9px;
}

.source-url-field {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 9px;
}

.source-url-help {
  font-size: 11px;
  font-weight: 400;
  line-height: 1.55;
  color: #718096;
}

.batch-picker {
  padding: 14px;
  margin: 14px 0;
  background: rgb(239 247 255 / 58%);
  border: 1px dashed rgb(68 126 205 / 35%);
  border-radius: 12px;
}

.batch-row {
  padding: 16px 0;
  border-top: 1px solid rgb(123 148 183 / 17%);
}

.batch-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.empty {
  color: #94a3b8;
}

@media (max-width: 900px) {
  .fields {
    grid-template-columns: 1fr;
  }
}
</style>
