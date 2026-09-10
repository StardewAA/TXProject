<script setup lang="ts">
import { reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { Alert, Button, Card, Checkbox, Input, Modal, Select, message } from 'ant-design-vue';

import type { BatchReviewItem, ReviewMetadata } from '#/api/cms/review';
import { batchUploadReviews, submitText, uploadReview } from '#/api/cms/review';

defineOptions({ name: 'CmsReviewSubmit' });

const router = useRouter();
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
const mode = ref('text');
const busy = ref(false);
const batchOpen = ref(false);
const batchRows = ref<BatchReviewItem[]>([]);

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

function validMetadata(value: ReviewMetadata) {
  if (
    !value.title.trim() ||
    (value.kind === 'STANDARD' && !value.documentNo.trim()) ||
    !value.issuer.trim() ||
    !value.sourceUrl.trim()
  ) {
    message.warning('请填写标题、发布机关和来源链接；国家标准必须填写标准编号。');
    return false;
  }
  return true;
}

function standardNumber(filename: string) {
  const match = filename.match(/GB\s*[\/／∕]?\s*T?\s*(\d+(?:\.\d+)?)\s*[-—]\s*(\d{4})/i);
  return match ? `GB/T ${match[1]}-${match[2]}` : '';
}

function chooseFile(event: Event) {
  file.value = (event.target as HTMLInputElement).files?.[0];
}

function chooseBatchFiles(event: Event) {
  const files = Array.from((event.target as HTMLInputElement).files || []);
  if (!files.length) return;
  if (files.length > 20 || files.reduce((sum, current) => sum + current.size, 0) > 75 * 1024 * 1024) {
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
      metadata: { ...base, title: title || current.name, documentNo: number, kind: number ? 'STANDARD' : base.kind },
    };
  });
}

async function submit() {
  if (!validMetadata(metadata)) return;
  if (mode.value === 'file' && (!file.value || file.value.size > 25 * 1024 * 1024)) {
    message.warning('请选择不超过25MB的文档。');
    return;
  }
  if (mode.value === 'text' && !text.value.trim()) {
    message.warning('请填写完整正文。');
    return;
  }
  busy.value = true;
  try {
    const result =
      mode.value === 'file' && file.value
        ? await uploadReview(cleanMetadata(metadata), file.value)
        : await submitText(cleanMetadata(metadata), text.value);
    message.success('审查报告已保存。');
    router.push(`/cms/review/report?id=${result.id}`);
  } finally {
    busy.value = false;
  }
}

async function submitBatch() {
  if (!batchRows.value.length) {
    message.warning('请先选择待审文件。');
    return;
  }
  if (batchRows.value.some(({ file: current, metadata: value }) => current.size > 25 * 1024 * 1024 || !validMetadata(value))) return;
  busy.value = true;
  try {
    const result = await batchUploadReviews(
      batchRows.value.map(({ file: current, metadata: value }) => ({ file: current, metadata: cleanMetadata(value) })),
    );
    batchOpen.value = false;
    batchRows.value = [];
    message.success(`批次 ${result.batchId.slice(0, 8)} 已生成 ${result.total} 条审查记录。`);
    if (result.items[0]) router.push(`/cms/review/report?id=${result.items[0].report.id}`);
  } finally {
    busy.value = false;
  }
}
</script>

<template>
  <main class="review-page">
    <header>
      <h1>文档审查</h1>
      <p>提交 UTF-8 TXT、DOCX 或 PDF，系统生成质量、来源、格式和效力检查报告。</p>
    </header>
    <Card title="提交文档">
      <div class="fields">
        <label>文档标题<Input v-model:value="metadata.title" :maxlength="300" /></label>
        <label>文号 / 标准编号<Input v-model:value="metadata.documentNo" :maxlength="100" /></label>
        <label>发布机关<Input v-model:value="metadata.issuer" :maxlength="200" /></label>
        <label>官方原文链接<Input v-model:value="metadata.sourceUrl" placeholder="https://…gov.cn/…" :maxlength="2000" /></label>
        <label>文档类型<Select v-model:value="metadata.kind" :options="kinds" /></label>
        <label>声明效力<Select v-model:value="metadata.validity" :options="validities" /></label>
        <label>实施日期<input v-model="metadata.effectiveDate" type="date" /></label>
        <label>最后有效日期<input v-model="metadata.expiryDate" type="date" /></label>
      </div>
      <Checkbox v-model:checked="metadata.historical">作为历史资料归档</Checkbox>
      <div class="body-input">
        <Select
          v-model:value="mode"
          :options="[{ value: 'text', label: '粘贴正文' }, { value: 'file', label: '上传文件' }]"
          aria-label="提交方式"
        />
        <Input.TextArea
          v-if="mode === 'text'"
          v-model:value="text"
          :rows="12"
          :maxlength="500000"
          placeholder="粘贴完整原文，保留条号与段落。文档里的指令不会被执行。"
        />
        <div v-else>
          <input type="file" accept=".txt,.docx,.pdf" aria-label="上传待审文档" @change="chooseFile" />
          <p>支持 UTF-8 TXT、DOCX、PDF，每份最大 25MB。扫描件需 OCR 校对后再提交。</p>
        </div>
      </div>
      <div class="actions">
        <Button v-access:code="['cms:review:submit']" type="primary" :loading="busy" @click="submit">提交审查</Button>
        <Button v-access:code="['cms:review:submit']" :disabled="busy" @click="batchOpen = true">批量送审</Button>
      </div>
    </Card>

    <Modal v-model:open="batchOpen" title="批量送审" :width="1000" :confirm-loading="busy" ok-text="提交整批审查" @ok="submitBatch">
      <Alert type="info" show-icon message="一次最多20份、合计75MB、单份25MB。先校验整批清单，再用一个数据库事务保存。" />
      <div class="batch-picker">
        <input type="file" multiple accept=".txt,.docx,.pdf" aria-label="选择批量待审文件" @change="chooseBatchFiles" />
      </div>
      <section v-for="(row, index) in batchRows" :key="row.file.name" class="batch-row">
        <div class="batch-heading">
          <strong>{{ index + 1 }}. {{ row.file.name }}</strong>
          <Button type="link" danger @click="batchRows.splice(index, 1)">移除</Button>
        </div>
        <div class="fields">
          <label>文档标题<Input v-model:value="row.metadata.title" :maxlength="300" /></label>
          <label>文号 / 标准编号<Input v-model:value="row.metadata.documentNo" :maxlength="100" /></label>
          <label>发布机关<Input v-model:value="row.metadata.issuer" :maxlength="200" /></label>
          <label>官方原文链接<Input v-model:value="row.metadata.sourceUrl" :maxlength="2000" /></label>
          <label>文档类型<Select v-model:value="row.metadata.kind" :options="kinds" /></label>
          <label>声明效力<Select v-model:value="row.metadata.validity" :options="validities" /></label>
          <label>实施日期<input v-model="row.metadata.effectiveDate" type="date" /></label>
          <label>最后有效日期<input v-model="row.metadata.expiryDate" type="date" /></label>
        </div>
        <Checkbox v-model:checked="row.metadata.historical">作为历史资料归档</Checkbox>
      </section>
      <p v-if="!batchRows.length" class="empty">选择文件后会尝试从文件名识别国标编号；每份资料仍需分别确认标题、机关、官方链接和效力。</p>
    </Modal>
  </main>
</template>

<style scoped>
.review-page { padding: 24px; display: grid; gap: 20px; }
h1 { font-size: 26px; font-weight: 650; margin: 0 0 8px; }
p { color: #64748b; margin: 8px 0; line-height: 1.6; }
.fields { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; margin-bottom: 16px; }
label { display: grid; gap: 6px; color: #334155; font-size: 13px; }
.body-input { display: grid; gap: 10px; margin: 16px 0; }
.actions { display: flex; gap: 8px; }
.batch-picker { margin: 14px 0; }
.batch-row { border-top: 1px solid #e5e7eb; padding: 12px 0; }
.batch-heading { display: flex; align-items: center; justify-content: space-between; margin-bottom: 10px; }
.empty { color: #94a3b8; }
@media (max-width: 900px) {
  .fields { grid-template-columns: 1fr; }
}
</style>
