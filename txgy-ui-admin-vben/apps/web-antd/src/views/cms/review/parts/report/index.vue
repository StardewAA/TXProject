<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { Alert, Button, Card, Checkbox, Input, Modal, Select, Spin, Tag, message } from 'ant-design-vue';

import type { ReviewDetail, ReviewMetadata } from '#/api/cms/review';
import { getReview, verifyReview } from '#/api/cms/review';

defineOptions({ name: 'CmsReviewReport' });

const route = useRoute();
const router = useRouter();
const busy = ref(false);
const detail = ref<ReviewDetail>();
const error = ref('');
const verifyOpen = ref(false);
const official = reactive<ReviewMetadata>({
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
const officialText = ref('');
const validityUrl = ref('');
const note = ref('');
const confirmed = ref(false);
const report = computed(() => detail.value?.report);
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
const dimensionNames: Record<string, string> = {
  ENCODING: '乱码检测',
  AUTHENTICITY: '来源与法规效力',
  FORMAT: '规范性检查',
  QUALITY: '内容质量',
};
const decisionNames = { PASS: '通过', WARNING: '警告通过', FAIL: '未通过' };

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

async function load(id?: string) {
  if (!id) {
    detail.value = undefined;
    return;
  }
  busy.value = true;
  error.value = '';
  try {
    detail.value = await getReview(id);
  } catch {
    detail.value = undefined;
    error.value = '无法读取审查报告，请检查服务、数据库迁移和账号权限。';
  } finally {
    busy.value = false;
  }
}

function startVerify() {
  if (!report.value) return;
  Object.assign(official, report.value.metadata);
  officialText.value = '';
  note.value = '';
  validityUrl.value = '';
  confirmed.value = false;
  verifyOpen.value = true;
}

function validMetadata(value: ReviewMetadata) {
  return Boolean(
    value.title.trim() &&
      (value.kind !== 'STANDARD' || value.documentNo.trim()) &&
      value.issuer.trim() &&
      value.sourceUrl.trim(),
  );
}

async function verify() {
  if (!report.value || !validMetadata(official)) {
    message.warning('请填写官方标题、发布机关和来源链接；国家标准必须填写标准编号。');
    return;
  }
  if (!confirmed.value || !officialText.value.trim() || !validityUrl.value.trim() || !note.value.trim()) {
    message.warning('请填写官方全文、效力依据和核验意见，并确认已完成对照。');
    return;
  }
  busy.value = true;
  try {
    const result = await verifyReview(
      report.value.id,
      cleanMetadata(official),
      officialText.value,
      validityUrl.value.trim(),
      note.value.trim(),
    );
    verifyOpen.value = false;
    message.success(result.admitted ? '审查通过，文档已入库并等待管理员发布。' : '审查未通过，已返回数据库退回记录。');
    await load(String(route.query.id || report.value.id));
  } finally {
    busy.value = false;
  }
}

function downloadReport() {
  if (!detail.value) return;
  const url = URL.createObjectURL(
    new Blob([JSON.stringify(detail.value, null, 2)], { type: 'application/json' }),
  );
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = `review-${detail.value.report.id}.json`;
  anchor.click();
  URL.revokeObjectURL(url);
}

watch(
  () => route.query.id,
  (value) => load(value ? String(value) : undefined),
  { immediate: true },
);
</script>

<template>
  <main class="review-page">
    <header>
      <h1>审查报告</h1>
      <p>查看单项文档的完整审查结论、问题明细与知识库切片。</p>
    </header>
    <div class="toolbar">
      <Button @click="router.push('/cms/review/record')">返回审查记录</Button>
      <Button type="primary" ghost @click="router.push('/cms/review/submit')">提交新文档</Button>
    </div>
    <Alert v-if="error" type="error" :message="error" show-icon />
    <Spin :spinning="busy">
      <Card v-if="report" title="审查报告">
        <template #extra>
          <Button @click="downloadReport">下载报告</Button>
          <Button class="ml-2" @click="router.push(`/cms/review/report?id=${report.id}`)">刷新</Button>
        </template>
        <h2>{{ report.metadata.title }}</h2>
        <div class="tags">
          <Tag :color="report.decision === 'FAIL' ? 'red' : report.decision === 'WARNING' ? 'orange' : 'green'">
            {{ decisionNames[report.decision as keyof typeof decisionNames] }}
          </Tag>
          <Tag>{{ report.admitted ? '审查通过 · 待发布' : '数据库保留 · 待核验或退回修改' }}</Tag>
          <Tag v-if="report.metadata.kind === 'LAW'">法律法规</Tag>
          <Tag v-else-if="report.metadata.kind === 'STANDARD'">国家标准</Tag>
          <Tag v-else>政策文件</Tag>
        </div>
        <dl class="meta">
          <div><dt>文号 / 标准编号</dt><dd>{{ report.metadata.documentNo || '-' }}</dd></div>
          <div><dt>发布机关</dt><dd>{{ report.metadata.issuer }}</dd></div>
          <div><dt>官方链接</dt><dd>{{ report.metadata.sourceUrl }}</dd></div>
          <div><dt>声明效力</dt><dd>{{ report.metadata.validity }}</dd></div>
          <div><dt>实施日期</dt><dd>{{ report.metadata.effectiveDate || '-' }}</dd></div>
          <div><dt>最后有效日期</dt><dd>{{ report.metadata.expiryDate || '-' }}</dd></div>
          <div><dt>提交时间</dt><dd>{{ report.createdAt }}</dd></div>
          <div><dt>总分</dt><dd>{{ report.score }} / 100</dd></div>
        </dl>
        <div class="scores">
          <div v-for="(value, key) in report.scores" :key="key" class="score">
            <span>{{ dimensionNames[key] }}</span>
            <strong>{{ value }}</strong>
          </div>
        </div>
        <ul class="issues">
          <li v-for="(issue, index) in report.issues" :key="index">
            <Tag :color="issue.severity === 'ERROR' ? 'red' : 'orange'">
              {{ issue.severity === 'ERROR' ? '阻断' : '警告' }}
            </Tag>
            <strong>{{ issue.location }} · {{ issue.message }}</strong>
            <p>{{ issue.suggestion }}</p>
          </li>
        </ul>
        <p v-if="!report.issues.length">四项检查通过。</p>
        <div v-if="report.evidence" class="evidence">
          <p>核验人：{{ report.evidence.reviewerId }} · 核验日期：{{ report.evidence.checkedOn }}</p>
          <p>核验意见：{{ report.evidence.note }}</p>
          <p>效力依据：{{ report.evidence.validitySourceUrl }}</p>
        </div>
        <div class="actions">
          <Button
            v-if="!report.admitted"
            v-access:code="['cms:review:verify']"
            type="primary"
            @click="startVerify"
          >
            登记官方核验依据
          </Button>
          <Button v-if="!report.admitted" @click="router.push(`/cms/review/submit`)">修改并重新提交</Button>
        </div>
        <details v-if="detail?.chunks.length" class="chunks">
          <summary>查看知识库切片（{{ detail.chunks.length }}）</summary>
          <article v-for="chunk in detail.chunks" :key="chunk.ordinal">
            <h3>切片 {{ chunk.ordinal }}</h3>
            <pre>{{ chunk.text }}</pre>
          </article>
        </details>
      </Card>
      <Card v-else class="empty-card" title="审查报告">
        <div class="empty">
          <p>请先从“审查记录”选择一条记录，或提交一份新文档后查看报告。</p>
          <Button type="primary" @click="router.push('/cms/review/record')">去审查记录</Button>
        </div>
      </Card>
    </Spin>

    <Modal v-model:open="verifyOpen" title="官方来源与法规效力核验" :width="800" :confirm-loading="busy" ok-text="核验并重新审查" @ok="verify">
      <Alert type="info" show-icon message="请独立打开官方来源，核对原文、版本沿革及表格图片。通过或警告通过后将写入待发布知识库。" />
      <div class="fields">
        <label>官方标题<Input v-model:value="official.title" :maxlength="300" /></label>
        <label>官方文号<Input v-model:value="official.documentNo" :maxlength="100" /></label>
        <label>官方发布机关<Input v-model:value="official.issuer" :maxlength="200" /></label>
        <label>官方原文链接<Input v-model:value="official.sourceUrl" :maxlength="2000" /></label>
        <label>官方文件类型<Select v-model:value="official.kind" :options="kinds" /></label>
        <label>核实后的效力<Select v-model:value="official.validity" :options="validities" /></label>
        <label>实施日期<input v-model="official.effectiveDate" type="date" /></label>
        <label>最后有效日期<input v-model="official.expiryDate" type="date" /></label>
      </div>
      <label>效力 / 版本沿革依据链接<Input v-model:value="validityUrl" :maxlength="2000" /></label>
      <label>经核对的官方完整正文<Input.TextArea v-model:value="officialText" :rows="8" :maxlength="500000" placeholder="与提交内容逐字对照；系统只统一换行符和首尾空白，不忽略正文差异。" /></label>
      <label>核验意见<Input.TextArea v-model:value="note" :rows="3" :maxlength="4000" /></label>
      <Checkbox v-model:checked="confirmed">已对照官方原文、版本沿革，并核查表格、图片及数值</Checkbox>
    </Modal>
  </main>
</template>

<style scoped>
.review-page { padding: 24px; display: grid; gap: 20px; }
h1 { font-size: 26px; font-weight: 650; margin: 0 0 8px; }
h2 { font-size: 18px; margin: 0 0 12px; }
p { color: #64748b; margin: 8px 0; line-height: 1.6; }
.toolbar { display: flex; gap: 8px; }
.tags { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 14px; }
.meta { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px 18px; margin: 0 0 14px; }
.meta div { display: grid; grid-template-columns: 110px 1fr; gap: 8px; }
dt { color: #64748b; font-size: 12px; }
dd { color: #0f172a; margin: 0; overflow-wrap: anywhere; }
.scores { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 10px; margin-bottom: 16px; }
.score { padding: 12px; border: 1px solid #e2e8f0; display: grid; gap: 4px; }
.score strong { font-size: 22px; color: #0f172a; }
.score span { color: #64748b; font-size: 12px; }
.issues { list-style: none; margin: 0 0 14px; padding: 0; display: grid; gap: 8px; }
.issues li { border: 1px solid #e2e8f0; padding: 10px 12px; display: grid; gap: 4px; }
.evidence { background: #f8fafc; padding: 10px 12px; border-radius: 6px; margin-bottom: 14px; }
.actions { display: flex; gap: 8px; margin-bottom: 12px; }
.chunks summary { cursor: pointer; color: #1d4ed8; font-weight: 600; }
.chunks pre { white-space: pre-wrap; background: #f8fafc; padding: 12px; border-radius: 6px; }
.empty-card .empty { padding: 40px 0; text-align: center; }
.fields { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; margin: 14px 0; }
label { display: grid; gap: 6px; color: #334155; font-size: 13px; }
.ml-2 { margin-left: 8px; }
@media (max-width: 900px) {
  .meta, .fields { grid-template-columns: 1fr; }
  .scores { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
</style>
