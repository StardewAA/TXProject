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
  checkPublicationDuplicate,
  getMyPublicationSubmission,
  listMyPublicationSubmissions,
  parsePublicationUrl,
  resubmitPublicationText,
  submitPublicationFile,
  submitPublicationText,
} from '#/api/cms/submission';
import CmsFilePicker from '../../cms/_shared/cms-file-picker.vue';
import CmsWangEditor from '../../cms/_shared/cms-wang-editor.vue';
import {
  editorHtmlToPlainText,
  plainTextToEditorHtml,
} from '../../cms/_shared/rich-text';
import '../../cms/_shared/cms-glass-theme.css';

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
const parseWarning = ref('');
const text = ref('');
const file = ref<File>();
const busy = ref(false);
const loading = ref(false);
const rows = ref<PublicationSubmission[]>([]);
const resubmitId = ref('');
const rejectionReason = ref('');

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
  { title: '操作', key: 'action', width: 130 },
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

async function refresh() {
  loading.value = true;
  try {
    rows.value = await listMyPublicationSubmissions();
  } finally {
    loading.value = false;
  }
}

async function parseUrl() {
  const requestedUrl = metadata.sourceUrl.trim();
  if (!requestedUrl) {
    message.warning('请输入政府网站 HTTPS 原文链接。');
    return;
  }
  busy.value = true;
  try {
    const result = await parsePublicationUrl(requestedUrl, true);
    Object.assign(metadata, result.metadata);
    metadata.sourceUrl = result.metadata.sourceUrl?.trim() || requestedUrl;
    text.value = plainTextToEditorHtml(result.text);
    mode.value = 'text';
    parseWarning.value = result.warning || '';
    message.success(
      result.llmExtracted
        ? '元数据已智能提取，请确认补全。'
        : '网页正文已解析，请确认补全元数据。',
    );
  } finally {
    busy.value = false;
  }
}

async function submit() {
  const sourceRequired = mode.value !== 'file' && !resubmitId.value;
  if (
    !metadata.title.trim() ||
    !metadata.issuer.trim() ||
    (sourceRequired && !metadata.sourceUrl.trim())
  ) {
    message.warning(
      sourceRequired
        ? '请填写标题、发布机关和官方原文链接。'
        : '请填写标题和发布机关。',
    );
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
  const plainText = editorHtmlToPlainText(text.value);
  if (mode.value !== 'file' && !plainText) {
    message.warning('请粘贴完整正文。');
    return;
  }
  if (plainText.length > 500_000) {
    message.warning('正文最多 50 万字，请精简后再提交。');
    return;
  }
  busy.value = true;
  try {
    if (resubmitId.value) {
      await resubmitPublicationText(
        resubmitId.value,
        cleanMetadata(),
        plainText,
      );
    } else if (mode.value === 'file' && file.value) {
      await submitPublicationFile(cleanMetadata(), file.value);
    } else {
      const duplicate = await checkPublicationDuplicate(
        cleanMetadata(),
        plainText,
      );
      if (duplicate.duplicate) {
        message.error(`提交已阻止：${duplicate.reason || '内容重复'}`);
        return;
      }
      await submitPublicationText(cleanMetadata(), plainText);
    }
    message.success(
      resubmitId.value
        ? '修改内容已重新提交，并重新进入 AI 初审。'
        : '发布申请已提交，AI 初审后将进入管理员人工复核。',
    );
    resubmitId.value = '';
    rejectionReason.value = '';
    text.value = '';
    file.value = undefined;
    await refresh();
  } finally {
    busy.value = false;
  }
}

async function startResubmit(row: Record<string, any>) {
  const item = row as PublicationSubmission;
  busy.value = true;
  try {
    const detail = await getMyPublicationSubmission(item.report.id);
    if (detail.report.workflowStatus !== 'REJECTED') {
      message.warning('只有已被人工驳回的申请可以修改后重新提交。');
      return;
    }
    resubmitId.value = detail.report.id;
    rejectionReason.value = detail.report.manualNote || '';
    Object.assign(metadata, detail.report.metadata);
    text.value = plainTextToEditorHtml(detail.text);
    file.value = undefined;
    mode.value = 'text';
    window.scrollTo({ behavior: 'smooth', top: 0 });
  } finally {
    busy.value = false;
  }
}

function cancelResubmit() {
  resubmitId.value = '';
  rejectionReason.value = '';
  text.value = '';
}

function reviewLabel(row: Record<string, any>) {
  const item = row as PublicationSubmission;
  return (
    (
      {
        AI_REVIEWING: 'AI 初审中',
        PENDING_MANUAL: '待人工复核',
        APPROVED: '人工通过',
        REJECTED: '人工驳回',
      } as Record<string, string>
    )[item.report.workflowStatus] || item.report.workflowStatus
  );
}

function reviewColor(row: Record<string, any>) {
  const item = row as PublicationSubmission;
  if (item.report.workflowStatus === 'APPROVED') return 'green';
  if (item.report.workflowStatus === 'REJECTED') return 'red';
  return item.report.workflowStatus === 'PENDING_MANUAL' ? 'orange' : 'blue';
}

onMounted(refresh);
</script>

<template>
  <div class="cms-glass-page publication-panel">
    <Alert
      message="企业提交的内容是发布申请，不会直接对外发布。管理员完成来源、效力、规范性和质量审查后，才能执行发布。"
      show-icon
      type="info"
    />
    <Card title="提交发布申请">
      <Alert
        v-if="resubmitId"
        type="warning"
        show-icon
        :message="`正在修改被驳回的申请${rejectionReason ? `：${rejectionReason}` : ''}`"
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
              :maxlength="2000"
              placeholder="https://….gov.cn/…"
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
          >文档类型<Select v-model:value="metadata.kind" :options="kindOptions"
        /></label>
        <label
          >申报效力<Select
            v-model:value="metadata.validity"
            :options="validityOptions"
        /></label>
        <label
          >实施日期<input v-model="metadata.effectiveDate" type="date"
        /></label>
      </div>
      <Checkbox v-model:checked="metadata.historical"
        >作为历史资料申报</Checkbox
      >
      <div class="body-input">
        <Select
          v-model:value="mode"
          :disabled="Boolean(resubmitId)"
          :options="[
            { label: '上传文件', value: 'file' },
            { label: '手动录入', value: 'text' },
          ]"
        />
        <CmsFilePicker v-if="mode === 'file'" v-model="file" />
        <Alert
          v-if="parseWarning"
          :message="parseWarning"
          show-icon
          type="warning"
        />
        <CmsWangEditor v-if="mode === 'text'" v-model="text" :height="360" />
      </div>
      <div class="submit-actions">
        <Button type="primary" :loading="busy" @click="submit">
          {{ resubmitId ? '修改后重新提交' : '提交审查并申请发布' }}
        </Button>
        <Button v-if="resubmitId" :disabled="busy" @click="cancelResubmit">
          取消修改
        </Button>
      </div>
    </Card>

    <Card title="我的发布申请">
      <template #extra
        ><Button :disabled="loading" @click="refresh">刷新</Button></template
      >
      <Spin :spinning="loading">
        <Table
          :columns="columns"
          :data-source="rows"
          :row-key="(row: PublicationSubmission) => row.report.id"
          :pagination="{ pageSize: 10 }"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'title'">{{
              record.report.metadata.title
            }}</template>
            <template v-else-if="column.key === 'createdAt'">{{
              record.report.createdAt
            }}</template>
            <template v-else-if="column.key === 'review'">
              <Tag :color="reviewColor(record)">{{ reviewLabel(record) }}</Tag>
            </template>
            <template v-else-if="column.key === 'published'">
              <Tag :color="record.published ? 'green' : 'default'">
                {{
                  record.published
                    ? '已发布'
                    : record.report.workflowStatus === 'REJECTED'
                      ? '已驳回'
                      : '未发布'
                }}
              </Tag>
            </template>
            <template v-else-if="column.key === 'action'">
              <Button
                v-if="record.report.workflowStatus === 'REJECTED'"
                type="link"
                :disabled="busy"
                @click="startResubmit(record)"
              >
                修改后重提
              </Button>
              <span v-else>-</span>
            </template>
          </template>
        </Table>
      </Spin>
    </Card>
  </div>
</template>

<style scoped>
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
  color: #44546b;
}

label > input {
  min-height: 34px;
  padding: 4px 10px;
}

.body-input {
  display: grid;
  gap: 12px;
  padding-top: 3px;
  margin: 18px 0;
}

.submit-actions {
  display: flex;
  gap: 10px;
}

.body-input :deep(.ant-select) {
  width: 180px;
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

@media (max-width: 800px) {
  .fields {
    grid-template-columns: 1fr;
  }
}
</style>
