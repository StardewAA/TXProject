<script setup lang="ts">
import type { BatchReviewItem, ReviewDetail, ReviewMetadata, ReviewReport } from '#/api/cms/review';
import { computed, onMounted, reactive, ref } from 'vue';
import { Alert, Button, Card, Checkbox, Input, Modal, Select, Spin, Table, Tag, message } from 'ant-design-vue';
import { batchUploadReviews, getReview, listReviews, submitText, uploadReview, verifyReview } from '#/api/cms/review';

defineOptions({ name: 'CmsDocumentReview' });
const emptyMetadata = (): ReviewMetadata => ({ title: '', documentNo: '', issuer: '', sourceUrl: '', kind: 'LAW', validity: 'UNKNOWN', effectiveDate: null, expiryDate: null, historical: false });
const metadata = reactive(emptyMetadata());
const official = reactive(emptyMetadata());
const text = ref('');
const file = ref<File>();
const mode = ref('text');
const busy = ref(false);
const records = ref<ReviewReport[]>([]);
const detail = ref<ReviewDetail>();
const previousId = ref<string>();
const error = ref('');
const verifyOpen = ref(false);
const batchOpen = ref(false);
const batchRows = ref<BatchReviewItem[]>([]);
const officialText = ref('');
const validityUrl = ref('');
const note = ref('');
const confirmed = ref(false);
const report = computed(() => detail.value?.report);
const kinds = [{ value: 'LAW', label: '法律法规' }, { value: 'STANDARD', label: '国家标准' }, { value: 'POLICY', label: '政策文件' }];
const validities = [{ value: 'UNKNOWN', label: '待核实' }, { value: 'EFFECTIVE', label: '现行有效' }, { value: 'NOT_YET_EFFECTIVE', label: '尚未生效' }, { value: 'REPEALED', label: '已废止' }, { value: 'EXPIRED', label: '已失效' }];
const dimensionNames: Record<string, string> = { ENCODING: '乱码检测', AUTHENTICITY: '来源与法规效力', FORMAT: '规范性检查', QUALITY: '内容质量' };
const decisionNames = { PASS: '通过', WARNING: '警告通过', FAIL: '未通过' };
const columns = [{ title: '文档名称', key: 'title' }, { title: '审查结论', key: 'decision' }, { title: '去向', key: 'destination' }, { title: '提交时间', dataIndex: 'createdAt' }, { title: '操作', key: 'action' }];

async function refresh() {
  try { records.value = await listReviews(); error.value = ''; }
  catch { error.value = '无法读取审查记录，请检查服务、数据库迁移和账号权限。'; }
}
async function open(id: string) {
  busy.value = true;
  try { detail.value = await getReview(id); }
  finally { busy.value = false; }
}
function chooseFile(event: Event) {
  file.value = (event.target as HTMLInputElement).files?.[0];
}
function standardNumber(filename: string) {
  const match = filename.match(/GB\s*[\/／∕]?\s*T?\s*(\d+(?:\.\d+)?)\s*[-—]\s*(\d{4})/i);
  return match ? `GB/T ${match[1]}-${match[2]}` : '';
}
function chooseBatchFiles(event: Event) {
  const files = Array.from((event.target as HTMLInputElement).files || []);
  if (!files.length) return;
  if (files.length > 20 || files.reduce((sum, current) => sum + current.size, 0) > 75 * 1024 * 1024) {
    message.warning('一次最多20份，合计不超过75MB，每份不超过25MB。'); return;
  }
  if (new Set(files.map((current) => current.name)).size !== files.length) {
    message.warning('文件名不能重复。'); return;
  }
  const base = cleanMetadata(metadata);
  batchRows.value = files.map((current) => {
    const number = standardNumber(current.name);
    const title = current.name.replace(/\.(docx|pdf|txt)$/i, '').replace(/\s*\(\d+\)$/, '')
      .replace(/^GB\s*[\/／∕]?\s*T?\s*\d+(?:\.\d+)?\s*[-—]\s*\d{4}\s*/i, '').trim();
    return { file: current, metadata: { ...base, title: title || current.name, documentNo: number, kind: number ? 'STANDARD' : base.kind } };
  });
}
function cleanMetadata(value: ReviewMetadata): ReviewMetadata {
  return { ...value, title: value.title.trim(), documentNo: value.documentNo.trim(), issuer: value.issuer.trim(), sourceUrl: value.sourceUrl.trim(), effectiveDate: value.effectiveDate || null, expiryDate: value.expiryDate || null };
}
function validMetadata(value: ReviewMetadata) {
  if (!value.title.trim() || (value.kind === 'STANDARD' && !value.documentNo.trim()) || !value.issuer.trim() || !value.sourceUrl.trim()) {
    message.warning('请填写标题、发布机关和来源链接；国家标准必须填写标准编号。'); return false;
  }
  return true;
}
async function submit() {
  if (!validMetadata(metadata)) return;
  if (mode.value === 'file' && (!file.value || file.value.size > 25 * 1024 * 1024)) { message.warning('请选择不超过25MB的文档。'); return; }
  if (mode.value === 'text' && !text.value.trim()) { message.warning('请填写完整正文。'); return; }
  busy.value = true;
  try {
    const result = mode.value === 'file' && file.value
      ? await uploadReview(cleanMetadata(metadata), file.value)
      : await submitText(cleanMetadata(metadata), text.value, previousId.value);
    detail.value = await getReview(result.id);
    previousId.value = undefined;
    message.success('审查报告已保存；未通过文档保留在数据库中。');
    await refresh();
  } finally { busy.value = false; }
}
async function submitBatch() {
  if (!batchRows.value.length) { message.warning('请先选择待审文件。'); return; }
  if (batchRows.value.some(({ file, metadata: value }) => file.size > 25 * 1024 * 1024 || !validMetadata(value))) return;
  busy.value = true;
  try {
    const result = await batchUploadReviews(batchRows.value.map(({ file, metadata: value }) => ({ file, metadata: cleanMetadata(value) })));
    batchOpen.value = false;
    batchRows.value = [];
    if (result.items[0]) detail.value = await getReview(result.items[0].report.id);
    message.success(`批次 ${result.batchId.slice(0, 8)} 已生成 ${result.total} 条审查记录。`);
    await refresh();
  } finally { busy.value = false; }
}
function revise() {
  if (!detail.value) return;
  Object.assign(metadata, detail.value.report.metadata);
  text.value = detail.value.text;
  mode.value = 'text'; file.value = undefined;
  previousId.value = detail.value.report.id;
  message.info('已载入正文供修改，重新提交会保留原审查记录。');
}
function startVerify() {
  if (!report.value) return;
  Object.assign(official, report.value.metadata);
  officialText.value = ''; note.value = ''; validityUrl.value = ''; confirmed.value = false;
  verifyOpen.value = true;
}
async function verify() {
  if (!report.value || !validMetadata(official)) return;
  if (!confirmed.value || !officialText.value.trim() || !validityUrl.value.trim() || !note.value.trim()) {
    message.warning('请填写官方全文、效力依据和核验意见，并确认已完成对照。'); return;
  }
  busy.value = true;
  try {
    const result = await verifyReview(report.value.id, cleanMetadata(official), officialText.value, validityUrl.value.trim(), note.value.trim());
    detail.value = await getReview(result.id);
    verifyOpen.value = false;
    message.success(result.admitted ? '审查通过，文档和切片已写入知识库。' : '审查未通过，已返回数据库退回记录。');
    await refresh();
  } finally { busy.value = false; }
}
function downloadReport() {
  if (!detail.value) return;
  const url = URL.createObjectURL(new Blob([JSON.stringify(detail.value, null, 2)], { type: 'application/json' }));
  const anchor = document.createElement('a'); anchor.href = url; anchor.download = `review-${detail.value.report.id}.json`; anchor.click(); URL.revokeObjectURL(url);
}
onMounted(refresh);
</script>

<template>
  <main class="review-page">
    <header><h1>文档审查</h1><p>核查正文质量、来源和法规效力。通过后自动入知识库，未通过的文档保留在数据库中修改重提。</p></header>
    <Alert v-if="error" type="error" :message="error" show-icon />
    <Spin :spinning="busy">
      <div class="review-grid">
        <Card title="提交文档">
          <Alert v-if="previousId" type="info" message="正在修改退回文档；原记录会保留。" class="mb-4" />
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
            <Select v-model:value="mode" :options="[{ value: 'text', label: '粘贴正文' }, { value: 'file', label: '上传文件' }]" aria-label="提交方式" />
            <Input.TextArea v-if="mode === 'text'" v-model:value="text" :rows="10" :maxlength="500000" placeholder="粘贴完整原文，保留条号与段落。文档里的指令不会被执行。" />
            <div v-else><input type="file" accept=".txt,.docx,.pdf" aria-label="上传待审文档" @change="chooseFile" /><p>支持UTF-8 TXT、DOCX、PDF，每份最大25MB。扫描件需OCR校对后再提交。</p></div>
          </div>
          <div class="actions"><Button v-access:code="['cms:review:submit']" type="primary" :loading="busy" @click="submit">提交审查</Button><Button v-access:code="['cms:review:submit']" :disabled="busy" @click="batchOpen = true">批量送审</Button></div>
        </Card>
        <Card title="审查报告">
          <template v-if="report">
            <h2>{{ report.metadata.title }}</h2>
            <Tag :color="report.decision === 'FAIL' ? 'red' : report.decision === 'WARNING' ? 'orange' : 'green'">{{ decisionNames[report.decision] }}</Tag>
            <Tag>{{ report.admitted ? '已入知识库' : '数据库保留 · 待核验或退回修改' }}</Tag>
            <div class="scores"><div v-for="(value, key) in report.scores" :key="key"><span>{{ dimensionNames[key] }}</span><strong>{{ value }}</strong></div></div>
            <p>总分 {{ report.score }} / 100。存在阻断问题时，总分不能抵消失败结论。</p>
            <ul class="issues"><li v-for="(issue, index) in report.issues" :key="index"><Tag :color="issue.severity === 'ERROR' ? 'red' : 'orange'">{{ issue.severity === 'ERROR' ? '阻断' : '警告' }}</Tag><strong>{{ issue.location }} · {{ issue.message }}</strong><p>{{ issue.suggestion }}</p></li></ul>
            <p v-if="!report.issues.length">四项检查通过。</p>
            <p v-if="report.evidence">核验人：{{ report.evidence.reviewerId }} · 核验日期：{{ report.evidence.checkedOn }}<br />核验意见：{{ report.evidence.note }}<br />效力依据：{{ report.evidence.validitySourceUrl }}</p>
            <div class="actions"><Button v-if="!report.admitted" v-access:code="['cms:review:verify']" type="primary" @click="startVerify">登记官方核验依据</Button><Button v-access:code="['cms:review:submit']" @click="revise">修改并重新提交</Button><Button @click="downloadReport">下载报告</Button></div>
            <details v-if="detail?.chunks.length"><summary>查看知识库切片（{{ report.chunkCount }}）</summary><article v-for="chunk in detail.chunks" :key="chunk.ordinal"><h3>切片 {{ chunk.ordinal }}</h3><pre>{{ chunk.text }}</pre></article></details>
          </template>
          <div v-else class="empty">提交文档或从下方选择一条记录查看报告。<p>官网链接不是通过凭证，来源与效力需要授权核验人员对照官方原文确认。</p></div>
        </Card>
      </div>
    </Spin>
    <Card title="审查记录"><template #extra><Button :disabled="busy" @click="refresh">刷新</Button></template><Table :columns="columns" :data-source="records" row-key="id" :scroll="{ x: 800 }" :pagination="{ pageSize: 10 }"><template #bodyCell="{ column, record }"><template v-if="column.key === 'title'">{{ record.metadata.title }}</template><template v-else-if="column.key === 'decision'">{{ decisionNames[record.decision as keyof typeof decisionNames] }}</template><template v-else-if="column.key === 'destination'">{{ record.admitted ? '知识库' : '数据库待核验 / 退回' }}</template><Button v-else-if="column.key === 'action'" type="link" :disabled="busy" @click="open(record.id)">查看报告</Button></template></Table><p>显示最近50条记录。</p></Card>
    <Modal v-model:open="verifyOpen" title="官方来源与法规效力核验" :width="800" :confirm-loading="busy" ok-text="核验并重新审查" @ok="verify">
      <Alert type="info" show-icon message="请独立打开官方来源，核对原文、版本沿革及表格图片。通过或警告通过后将自动写入知识库。" />
      <div class="fields verification">
        <label>官方标题<Input v-model:value="official.title" :maxlength="300" /></label><label>官方文号<Input v-model:value="official.documentNo" :maxlength="100" /></label>
        <label>官方发布机关<Input v-model:value="official.issuer" :maxlength="200" /></label><label>官方原文链接<Input v-model:value="official.sourceUrl" :maxlength="2000" /></label>
        <label>官方文件类型<Select v-model:value="official.kind" :options="kinds" /></label><label>核实后的效力<Select v-model:value="official.validity" :options="validities" /></label>
        <label>实施日期<input v-model="official.effectiveDate" type="date" /></label><label>最后有效日期<input v-model="official.expiryDate" type="date" /></label>
      </div>
      <label>效力 / 版本沿革依据链接<Input v-model:value="validityUrl" :maxlength="2000" /></label>
      <label>经核对的官方完整正文<Input.TextArea v-model:value="officialText" :rows="8" :maxlength="500000" placeholder="与提交内容逐字对照；系统只统一换行符和首尾空白，不忽略正文差异。" /></label>
      <label>核验意见<Input.TextArea v-model:value="note" :rows="3" :maxlength="4000" /></label>
      <Checkbox v-model:checked="confirmed">已对照官方原文、版本沿革，并核查表格、图片及数值</Checkbox>
    </Modal>
    <Modal v-model:open="batchOpen" title="批量送审" :width="1000" :confirm-loading="busy" ok-text="提交整批审查" @ok="submitBatch">
      <Alert type="info" show-icon message="一次最多20份、合计75MB、单份25MB。先校验整批清单，再用一个数据库事务保存；解析失败会生成退回报告，不会进入知识库。" />
      <div class="batch-picker"><input type="file" multiple accept=".txt,.docx,.pdf" aria-label="选择批量待审文件" @change="chooseBatchFiles" /></div>
      <section v-for="(row, index) in batchRows" :key="row.file.name" class="batch-row">
        <div class="batch-heading"><strong>{{ index + 1 }}. {{ row.file.name }}</strong><Button type="link" danger @click="batchRows.splice(index, 1)">移除</Button></div>
        <div class="fields">
          <label>文档标题<Input v-model:value="row.metadata.title" :maxlength="300" /></label><label>文号 / 标准编号<Input v-model:value="row.metadata.documentNo" :maxlength="100" /></label>
          <label>发布机关<Input v-model:value="row.metadata.issuer" :maxlength="200" /></label><label>官方原文链接<Input v-model:value="row.metadata.sourceUrl" :maxlength="2000" /></label>
          <label>文档类型<Select v-model:value="row.metadata.kind" :options="kinds" /></label><label>声明效力<Select v-model:value="row.metadata.validity" :options="validities" /></label>
          <label>实施日期<input v-model="row.metadata.effectiveDate" type="date" /></label><label>最后有效日期<input v-model="row.metadata.expiryDate" type="date" /></label>
        </div>
        <Checkbox v-model:checked="row.metadata.historical">作为历史资料归档</Checkbox>
      </section>
      <p v-if="!batchRows.length" class="empty">选择文件后会尝试从文件名识别国标编号；每份资料仍需分别确认标题、机关、官方链接和效力。</p>
    </Modal>
  </main>
</template>

<style scoped>
.review-page { padding: 24px; display: grid; gap: 20px; }
h1 { font-size: 26px; font-weight: 650; margin-bottom: 8px; }
h2 { font-size: 18px; margin-bottom: 12px; }
p { color: #64748b; margin: 8px 0; line-height: 1.6; }
.review-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; align-items: start; }
.fields { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin-bottom: 16px; }
label { display: grid; gap: 6px; margin-bottom: 8px; }
input[type='date'] { width: 100%; border: 1px solid #d9d9d9; border-radius: 6px; min-height: 32px; padding: 4px 10px; background: transparent; }
.body-input { display: grid; gap: 12px; margin: 18px 0; }
.body-input > .ant-select { width: 150px; }
.scores { display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px; margin: 20px 0; }
.scores div { display: grid; gap: 8px; padding: 12px; border: 1px solid #e2e8f0; border-radius: 8px; }
.scores strong { font-size: 24px; }
.issues { padding: 0; list-style: none; max-height: 440px; overflow: auto; }
.issues li { padding: 12px 0; border-bottom: 1px solid #e2e8f0; }
.actions { display: flex; flex-wrap: wrap; gap: 8px; margin: 20px 0; }
.empty { padding: 32px 8px; color: #64748b; }
.verification { margin-top: 20px; }
.batch-picker { margin: 20px 0; }
.batch-row { margin: 12px 0; padding: 16px; border: 1px solid #e2e8f0; border-radius: 8px; }
.batch-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 12px; }
pre { white-space: pre-wrap; overflow-wrap: anywhere; background: #f8fafc; color: #334155; padding: 12px; }
details { margin-top: 20px; }
summary { cursor: pointer; }
@media (max-width: 1050px) { .review-grid { grid-template-columns: 1fr; } }
@media (max-width: 600px) { .review-page { padding: 12px; } .fields,.scores { grid-template-columns: 1fr; } }
</style>
