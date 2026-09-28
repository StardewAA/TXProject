<!--
  标书审查 · 审查报告抽屉（「审查任务」详情与「报告与统计」页共用）

  为什么抽组件：改造前 TaskPanel 与 ReportPanel 各写了一份报告抽屉
  （连样式类都是两套：.br-report__* 与 .brp-report__*），同一份 DOM 维护两遍。

  两个动作都"直接下载、不跳地址页"：
    · Word：后端回传 file_id 对应的下载地址 → downloadPlatformFile 取 /admin-api/ 同源路径 + <a download>；
    · HTML：后端 report/export 返回正文 HTML → 前端自己包成 Blob 落盘（避免打开需要鉴权的接口地址页）。
-->
<script lang="ts" setup>
import { ref, watch } from 'vue';

// Drawer / Empty / Spin 未全局注册（本项目没有组件自动导入），模板里的 <a-drawer>/<a-empty>/<a-spin>
// 必须在这里显式引入，否则报告抽屉渲染不出来（个人端 index.vue 同款问题，2026-09-21）
import {
  Button,
  Drawer as ADrawer,
  Empty as AEmpty,
  message,
  Space,
  Spin as ASpin,
  Tag,
} from 'ant-design-vue';

import {
  type BidReviewApi,
  downloadPlatformFile,
  exportReviewReportHtml,
  getReviewReport,
  getReviewReportDoc,
} from '#/api/bidding/bidReview';
import { fmtDateTime } from '#/utils/datetime';

import BidReviewDictTag from './BidReviewDictTag.vue';
import CaSignEntry, { type CaSignFile } from '../../ca/CaSignEntry.vue';

const props = defineProps<{
  open: boolean;
  taskId?: null | number | string;
  /** 抽屉标题里的任务名称（可选，纯展示） */
  taskName?: string;
}>();

const emit = defineEmits<{ 'update:open': [value: boolean] }>();

const loading = ref(false);
const docLoading = ref(false);
const exporting = ref(false);
const report = ref<BidReviewApi.Report | null>(null);
const doc = ref<BidReviewApi.ReportDoc | null>(null);

async function load(taskId: number | string) {
  loading.value = true;
  doc.value = null;
  try {
    report.value = await getReviewReport(taskId);
  } catch (error: any) {
    report.value = null;
    message.warning(error?.message ?? '报告尚未生成');
  } finally {
    loading.value = false;
  }
  // 正文拿到后再顺带准备 Word 文档（同一任务只生成一次）
  if (report.value) {
    docLoading.value = true;
    try {
      doc.value = await getReviewReportDoc(taskId);
    } catch {
      doc.value = null;
    } finally {
      docLoading.value = false;
    }
  }
}

watch(
  () => [props.open, props.taskId] as const,
  ([open, taskId]) => {
    if (open && taskId !== null && taskId !== undefined && taskId !== '') {
      void load(taskId);
    }
    if (!open) {
      report.value = null;
      doc.value = null;
    }
  },
  { immediate: true },
);

function downloadWord() {
  if (!downloadPlatformFile(doc.value?.url, doc.value?.fileName)) {
    message.warning('报告 Word 尚未生成，请稍后重试');
  }
}
/** 审查报告（Word 文档）→ 待签名文件（第40条(四)：资格审查报告/评标报告须电子签名并存档） */
function signFileOf(): CaSignFile | undefined {
  if (!doc.value?.url) return undefined;
  return { url: doc.value.url, fileName: doc.value.fileName || '审查报告', fileId: doc.value.fileId };
}
function onSigned(rec: { signatureValue?: string; digestHex?: string }): void {
  if (rec.signatureValue) message.success('审查报告 CA 签名完成：' + (rec.digestHex?.slice(0, 12) ?? '') + '…');
}

async function exportHtml() {
  if (!props.taskId) {
    return;
  }
  exporting.value = true;
  try {
    const html = (await exportReviewReportHtml(props.taskId)) ?? '';
    const blob = new Blob([html], { type: 'text/html;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `${report.value?.reportNo ?? '审查报告'}.html`;
    link.style.display = 'none';
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(url);
  } catch (error: any) {
    message.warning(error?.message ?? '导出失败');
  } finally {
    exporting.value = false;
  }
}
</script>

<template>
  <a-drawer
    :open="open"
    :title="`审查报告${taskName ? ` - ${taskName}` : ''}`"
    width="920"
    @close="emit('update:open', false)"
  >
    <a-spin :spinning="loading">
      <template v-if="report">
        <div class="br-report__meta">
          <Tag>{{ report.reportNo }}</Tag>
          <BidReviewDictTag dict-type="bid_review_verdict" :value="report.verdict" />
          <Tag v-if="report.scoreTotal != null" color="blue">自评 {{ report.scoreTotal }}</Tag>
          <Tag :color="report.aiGenerated ? 'purple' : 'default'">
            {{ report.aiGenerated ? 'AI 生成' : '规则匹配生成' }}
          </Tag>
          <span class="br-report__time">{{ fmtDateTime(report.createTime) }}</span>
          <span class="br-flex-spacer" />
          <Space>
            <Button
              size="small"
              :loading="exporting"
              @click="exportHtml"
            >
              导出 HTML
            </Button>
            <Button
              type="primary"
              size="small"
              :loading="docLoading"
              :disabled="!doc?.url"
              @click="downloadWord"
            >
              下载报告文档（Word）
            </Button>
            <CaSignEntry
              v-if="doc?.url"
              :file="signFileOf()"
              :biz-type="'REVIEW'"
              :biz-code="report.reportNo"
              label="CA 签名"
              @signed="onSigned"
            />
          </Space>
        </div>
        <div class="br-report__body" v-html="report.htmlContent" />
        <div class="br-report__footer">{{ report.disclaimer }}</div>
      </template>
      <a-empty v-else-if="!loading" description="报告尚未生成（任务未完成或生成失败）" />
    </a-spin>
  </a-drawer>
</template>

<style scoped>
.br-report__meta {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 10px;
}

.br-report__time {
  font-size: 12px;
  color: #8a94a6;
}

.br-flex-spacer {
  flex: 1;
}

.br-report__body {
  padding: 12px;
  overflow: auto;
  font-size: 13px;
  line-height: 1.8;
  background: #fafbfd;
  border: 1px solid #eef2f7;
}

.br-report__footer {
  margin-top: 8px;
  font-size: 12px;
  color: #8a94a6;
}
</style>
