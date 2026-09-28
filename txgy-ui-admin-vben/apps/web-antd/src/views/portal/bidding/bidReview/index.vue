<script lang="ts" setup>
/**
 * 门户 · 智慧标书审查（个人端）
 *
 * 设计口径（用户 2026-09-21 改版）：
 *  - 参考「智能商务标审查」示例风格：居中大标题（欢迎语）+ 通栏浅蓝虚线拖拽上传区（DOC/PDF 图标）+ 一个主按钮；
 *  - 取消「任务名称」手填：任务名称自动取标书文件名（去扩展名、截 20 字）；
 *  - 主线只有"传文件 → 开始审查 → 看结果"。
 *  - 模块拆分（用户 2026-09-17）：本组件只负责「发起审查」；历史记录拆到同目录 history.vue，
 *    由 compliance.vue 以两个风格一致的字模块并列展示，本组件提交任务后 emit('created') 通知历史刷新。
 *
 * 功能口径：个人端只负责上传文件和一键生成（后端 getLoginUserId 强制本人，入参不含 userId）。
 * 扫描件提醒：当前 AI 为纯文本模型，图片型 PDF 请改用文字版 PDF/Word。
 */
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';

// 本项目没有配置组件自动导入（unplugin-vue-components），模板里的 a-* 只有「全局注册过的」才解析得到：
// 全局注册来自 plugins/form-create（Alert/Collapse/Tag/Table…）+ @form-create/auto-import（Button/Input/Select/Upload…）。
// Drawer / Empty / Progress 不在其中，<a-drawer> 等会退化成未解析标签 → 「查看完整报告」抽屉打不开，
// 因此这里显式引入（script setup 的绑定能被 <a-xxx> 解析到，无需改模板）。
import { Drawer as ADrawer, Empty as AEmpty, message, Progress as AProgress } from 'ant-design-vue';

import {
  type BidReviewApi,
  cancelMyReview,
  createAndGenerate,
  getMyEnterprises,
  getMyReviewIssues,
  getMyReviewProgress,
  getMyReviewReport,
  getMyReviewReportDoc,
  getMyReviewTask,
  retryMyReview,
  downloadPlatformFile,
} from '#/api/bidding/bidReview';
import { uploadBiddingFile } from '#/api/bidding/file';

// 模块共享样式：--bid-* 令牌与共享类（.bid-glass-shell 等）。
// 令牌挂在 .hall / .module-page / .float-bar / .white-modal 这组类名上，故根元素同时挂 module-page。
// 本页 2026-09-22 起不再自带卡片外壳（见下方 .brp-card 的注释），观感由外层那张大卡片提供。
import '../styles/bid-ui.css';

// 组件名用于 workbench.vue 的 <KeepAlive :include>：审查未完成时切到「审查历史」再切回来，
// 不能重建页面（否则进度、当前任务、表单全部清空），详见 workbench.vue 注释。
defineOptions({ name: 'BidReviewPortalReview' });

interface PickedFile {
  fileId: number;
  name: string;
  size: number;
  url: string;
}

const uploading = ref<'bid' | 'tender' | ''>('');
const router = useRouter();
const generating = ref(false);
const currentTaskId = ref<null | number>(null);
const progress = ref<BidReviewApi.TaskProgress | null>(null);
const detail = ref<BidReviewApi.Task | null>(null);
const issues = ref<BidReviewApi.Issue[]>([]);
const report = ref<BidReviewApi.Report | null>(null);
const reportOpen = ref(false);
/** 报告 Word 文档（打开报告时生成/复用，供下载） */
const reportDoc = ref<BidReviewApi.ReportDoc | null>(null);
const docLoading = ref(false);

const form = reactive<{ name: string; bid?: PickedFile; tender?: PickedFile }>({
  name: '',
});

/**
 * 归属企业（2026-09-20）：个人端提交的审查记录要让企业端看得到。
 *
 * 白名单不是"所有企业"，而是**本人已被授权为经办人的企业**（后端 bidding_subject_user 判定），
 * 前端只负责展示；企业名称用系统租户列表按 tenantId 映射，不额外依赖别的模块。
 */
const enterpriseTenantId = ref<number | undefined>(undefined);
/**
 * 默认归属企业（用户 2026-09-22）：个人端**既不展示、也不让选**，系统静默默认延河热力集团有限公司。
 * 页面上不再出现任何企业相关的文案/控件，只有提交时把 tenantId 带给后端。
 */
const DEFAULT_ENTERPRISE_TENANT_ID = 2019;

async function loadEnterprises() {
  try {
    const mine = (await getMyEnterprises().catch(() => [])) ?? [];
    // 归属优先级：延河热力(2019) → 唯一/第一家被授权企业。
    // 为什么不留空：留空会让记录落在个人租户（tenant 0），企业端看不到，
    // 与「个人端提交要让企业端看到」的既定口径相反。
    const preferred =
      mine.find((item) => item.tenantId === DEFAULT_ENTERPRISE_TENANT_ID) ?? mine[0];
    enterpriseTenantId.value = preferred?.tenantId;
  } catch {
    enterpriseTenantId.value = undefined;
  }
}

/** 任务提交成功后通知父级（刷新「审查历史」模块） */
const emit = defineEmits<{ created: [taskId: number] }>();

let timer: any = null;

const running = computed(() =>
  progress.value ? ['QUEUED', 'RUNNING'].includes(progress.value.status) : false,
);
const stepText = computed(() => {
  const map: Record<string, string> = {
    PARSE: '正在解析标书文件…',
    RULE: '正在准备审查规则…',
    GENERATE: '正在逐条审查（AI 比对中）…',
    REPORT: '正在生成审查报告…',
  };
  return map[progress.value?.currentStep ?? ''] ?? '准备中…';
});
const statusLabel = computed(() => {
  const map: Record<string, string> = {
    QUEUED: '排队中',
    RUNNING: '审查中',
    SUCCESS: '已完成',
    FAILED: '已失败',
    CANCELED: '已取消',
  };
  return map[progress.value?.status ?? ''] ?? '';
});
const verdictText = computed(() => {
  const map: Record<string, string> = { PASS: '通过', RISK: '存在风险', FAIL: '不通过' };
  return map[progress.value?.verdict ?? ''] ?? '—';
});
const verdictClass = computed(() => {
  const v = progress.value?.verdict;
  return v === 'FAIL' ? 'fail' : v === 'RISK' ? 'risk' : 'pass';
});
const issueGroups = computed(() => {
  const groups: Record<string, BidReviewApi.Issue[]> = { HIGH: [], LOW: [], MEDIUM: [] };
  issues.value.forEach((i) => (groups[i.severity] ?? groups.LOW).push(i));
  return groups;
});
const totalIssues = computed(() => issues.value.length);

async function pick(file: File, type: 'bid' | 'tender') {
  uploading.value = type;
  try {
    const resp = await uploadBiddingFile(file, 'bidding/bid-review');
    if (!resp?.fileId) {
      message.error('上传成功但未取得 file_id，请重试');
      return false;
    }
    const picked: PickedFile = {
      fileId: resp.fileId,
      name: resp.name ?? file.name,
      size: resp.size ?? file.size,
      url: resp.url ?? '',
    };
    if (type === 'bid') {
      form.bid = picked;
      // 取消手填任务名称：自动取标书文件名（去扩展名，截 20 字）；重新上传时同步更新
      form.name = picked.name.replace(/\.[^.]+$/, '').slice(0, 20);
    } else {
      form.tender = picked;
    }
  } catch (error: any) {
    message.error(error?.message ?? '上传失败');
  } finally {
    uploading.value = '';
  }
  return false;
}

function removeFile(type: 'bid' | 'tender') {
  if (type === 'bid') form.bid = undefined;
  else form.tender = undefined;
}

function fmtSize(size?: number) {
  if (!size) return '';
  return size > 1024 * 1024 ? `${(size / 1024 / 1024).toFixed(1)} MB` : `${Math.round(size / 1024)} KB`;
}

async function start() {
  if (!form.bid) {
    message.warning('请上传投标文件（标书）');
    return;
  }
  generating.value = true;
  try {
    const taskId = await createAndGenerate({
      bidFileId: form.bid.fileId,
      bidFileUrl: form.bid.url,
      bidFileName: form.bid.name,
      tenderFileId: form.tender?.fileId,
      tenderFileUrl: form.tender?.url,
      tenderFileName: form.tender?.name,
      remark: form.name,
      enterpriseTenantId: enterpriseTenantId.value,
    });
    currentTaskId.value = taskId;
    issues.value = [];
    report.value = null;
    detail.value = null;
    progress.value = { id: taskId, progress: 0, status: 'QUEUED' };
    message.success('已开始审查，请在下方查看进度');
    watchProgress(taskId);
    emit('created', taskId);
  } catch (error: any) {
    message.error(error?.message ?? '提交失败');
  } finally {
    generating.value = false;
  }
}

function watchProgress(taskId: number) {
  stopWatch();
  timer = setInterval(async () => {
    try {
      const data = await getMyReviewProgress(taskId);
      progress.value = data;
      if (data.status === 'SUCCESS') {
        stopWatch();
        await loadResult(taskId);
        // 一键审查完成 → 进入「标书审查结论」页（是否废标 + 废标原因）
        router.push({
          path: '/portal/bidding/bidReview/result',
          query: { taskId: String(taskId) },
        });
        return;
      } else if (data.status === 'FAILED' || data.status === 'CANCELED') {
        stopWatch();
      }
    } catch {
      /* 轮询失败不打断页面 */
    }
  }, 1500);
}

function stopWatch() {
  if (timer) {
    clearInterval(timer);
    timer = null;
  }
}

async function loadResult(taskId: number) {
  detail.value = await getMyReviewTask(taskId).catch(() => null);
  issues.value = await getMyReviewIssues(taskId).catch(() => []);
  report.value = await getMyReviewReport(taskId).catch(() => null);
}

/** 打开完整报告：同时生成（或复用）Word 报告文档，给用户一个可下载/可打印的文件 */
async function openReport() {
  reportOpen.value = true;
  if (!currentTaskId.value) {
    return;
  }
  docLoading.value = true;
  try {
    reportDoc.value = await getMyReviewReportDoc(currentTaskId.value);
  } catch (error: any) {
    reportDoc.value = null;
    message.warning(error?.message ?? '报告文档生成失败，可在报告中查看内容');
  } finally {
    docLoading.value = false;
  }
}

function downloadReportDoc() {
  if (downloadPlatformFile(reportDoc.value?.url, reportDoc.value?.fileName)) {
    return;
  }
  if (currentTaskId.value) {
    void openReport();
  }
}

/** 跳到「标书审查结论」页（是否废标 + 废标原因） */
function viewVerdict() {
  if (!currentTaskId.value) {
    message.warning('请先完成一次审查');
    return;
  }
  router.push({
    path: '/portal/bidding/bidReview/result',
    query: { taskId: String(currentTaskId.value) },
  });
}

async function retry(id: number) {
  await retryMyReview(id);
  message.success('已重新提交');
  progress.value = { id, progress: 0, status: 'QUEUED' };
  watchProgress(id);
}

async function cancel(id: number) {
  await cancelMyReview(id);
  message.success('已取消');
  progress.value = await getMyReviewProgress(id);
}

onBeforeUnmount(stopWatch);
onMounted(loadEnterprises);
</script>

<template>
  <div class="brp module-page">
    <div class="brp-card">
      <!-- 新版风格（参考智能商务标审查示例）：居中欢迎大标题 + 通栏浅蓝虚线上传区 -->
      <div class="brp-hero">
        <h1 class="brp-hero__title">欢迎使用 <span class="hl">智慧标书审查</span> 规避投标风险</h1>
        <p class="brp-hero__sub">上传完整招标文件与投标文件，AI 将自动识别废标风险与潜在问题</p>
      </div>

      <div class="brp-form">
        <!-- 标书文件（必传）：任务名称自动取标书文件名，无需手填 -->
        <div class="brp-zone">
          <a-upload-dragger
            v-if="!form.bid"
            class="brp-drop"
            :before-upload="(f: File) => pick(f, 'bid')"
            :show-upload-list="false"
          >
            <div class="brp-zone__icons">
              <span class="file-ic doc">DOC</span>
              <span class="file-ic pdf">PDF</span>
            </div>
            <div class="brp-zone__main">*将标书文件拖到此处，或 <span>点击上传</span></div>
            <div class="brp-zone__hint">
              支持文件格式：DOC、DOCX、PDF（扫描件请改用文字版），单个文件不超过 30MB
            </div>
          </a-upload-dragger>
          <div v-else class="brp-file">
            <span class="brp-file__name">{{ form.bid.name }}</span>
            <span class="brp-file__size">{{ fmtSize(form.bid.size) }}</span>
            <a @click="removeFile('bid')">重新上传</a>
          </div>
        </div>

        <!-- 招标文件（可选）：风格与标书上传区一致 -->
        <div class="brp-zone">
          <a-upload-dragger
            v-if="!form.tender"
            class="brp-drop"
            :before-upload="(f: File) => pick(f, 'tender')"
            :show-upload-list="false"
          >
            <div class="brp-zone__icons">
              <span class="file-ic doc">DOC</span>
              <span class="file-ic pdf">PDF</span>
            </div>
            <div class="brp-zone__main">*将招标文件拖到此处，或 <span>点击上传</span></div>
            <div class="brp-zone__hint">支持文件格式：DOC、DOCX、PDF（扫描件请改用文字版），单个文件不超过 30MB</div>
          </a-upload-dragger>
          <div v-else class="brp-file">
            <span class="brp-file__name">{{ form.tender.name }}</span>
            <span class="brp-file__size">{{ fmtSize(form.tender.size) }}</span>
            <a @click="removeFile('tender')">重新上传</a>
          </div>
        </div>

        <div class="brp-actions">
          <a-button :loading="generating" size="large" type="primary" @click="start">开始审查</a-button>
          <span class="brp-actions__hint">审查为异步执行，通常 1~3 分钟，可离开页面稍后查看</span>
        </div>
      </div>

      <div v-if="progress" class="brp-result">
        <div class="brp-result__head">
          <span class="brp-result__title">审查进度</span>
          <a-tag
            :color="running ? 'processing' : progress.status === 'FAILED' ? 'error' : progress.status === 'SUCCESS' ? 'success' : 'default'"
          >
            {{ statusLabel }}
          </a-tag>
          <span class="brp-result__step">{{ running ? stepText : '' }}</span>
        </div>
        <a-progress
          :percent="progress.progress ?? 0"
          :show-info="false"
          :status="running ? 'active' : 'normal'"
        />
        <a-alert
          v-if="progress.status === 'FAILED'"
          type="error"
          show-icon
          style="margin-top: 12px"
          :message="progress.errorMsg || '审查失败'"
        >
          <template #action>
            <a-button size="small" @click="retry(progress.id)">重试</a-button>
          </template>
        </a-alert>
        <div v-if="running" class="brp-result__ops">
          <a @click="cancel(progress.id)">取消本次审查</a>
        </div>

        <template v-if="progress.status === 'SUCCESS'">
          <div class="brp-verdict" :class="verdictClass">
            <div class="brp-verdict__label">审查结论</div>
            <div class="brp-verdict__value">{{ verdictText }}</div>
            <div class="brp-verdict__meta">
              <span>规则 {{ detail?.ruleCnt ?? '—' }} 条</span>
              <span>问题 {{ totalIssues }} 条（高危 {{ progress.highIssueCnt ?? 0 }}）</span>
              <span v-if="detail?.scoreTotal">自评得分 {{ detail.scoreTotal }}</span>
            </div>
            <a-button type="link" @click="openReport">查看完整报告</a-button>
            <a-button type="link" @click="viewVerdict">是否废标</a-button>
          </div>

          <a-empty v-if="!totalIssues" description="未发现问题" />
          <div v-else class="brp-issues">
            <a-collapse ghost>
              <a-collapse-panel
                v-for="(list, level) in issueGroups"
                v-show="list.length"
                :key="level"
                :header="`${level === 'HIGH' ? '高危' : level === 'MEDIUM' ? '中危' : '低危'}问题（${list.length}）`"
              >
                <div v-for="issue in list" :key="issue.id" class="brp-issue">
                  <div class="brp-issue__title">{{ issue.issueTitle }}</div>
                  <div v-if="issue.issueDesc" class="brp-issue__line">说明：{{ issue.issueDesc }}</div>
                  <div v-if="issue.evidenceText" class="brp-issue__line">
                    证据（{{ issue.hitLocation || '未定位' }}）：{{ issue.evidenceText }}
                  </div>
                  <div v-else class="brp-issue__line">证据：标书没有对应内容</div>
                  <div class="brp-issue__line">建议：{{ issue.suggestion }}</div>
                </div>
              </a-collapse-panel>
            </a-collapse>
          </div>
        </template>
      </div>
    </div>

    <a-drawer v-model:open="reportOpen" title="智慧标书审查报告" width="820">
      <div v-if="report">
        <div class="brp-report__meta">
          <a-tag>{{ report.reportNo }}</a-tag>
          <a-tag>
            {{ report.verdict === 'FAIL' ? '不通过' : report.verdict === 'RISK' ? '存在风险' : '通过' }}
          </a-tag>
          <span class="brp-flex-spacer" />
          <a-button type="primary" size="small" :loading="docLoading" @click="downloadReportDoc">
            下载报告文档（Word）
          </a-button>
        </div>
        <div class="brp-report__body" v-html="report.htmlContent" />
        <div class="brp-report__footer">{{ report.disclaimer }}</div>
      </div>
    </a-drawer>
  </div>
</template>

<style scoped>
/* 浅灰底 + 单张白卡片；页面内部无侧边栏、无二级导航（参考 yzb.aiwork365.cn/duplicate-check 的简洁风格） */
.brp {
  min-height: 100%;
  /* 内边距清零（2026-09-22 恢复 2026-09-21 的口径）：原为 28px 16px 56px。
   *   · 上内边距会让本页首块比同排的「审查历史」低，在标签页之间来回切时顶边会跳；
   *   · 左右 16px / 下 56px 同理：窗口不够宽时本页会比「审查历史」窄 32px，来回切会看到内容变宽变窄。
   *   · 浅灰底（#f7f7f7）一并去掉：页面底衬现在是外层那张大卡片（.brw）。
   * 下边距改由 .brw-shell 的 margin-bottom: 56px 承担。 */
  padding: 0;
}

/* 2026-09-22：外层大卡片（.brw）本身就是页面卡片，本页不再自己套一张 ——
 *   去掉 max-width: 1000px 居中 / 白底 / 8px 圆角 / 描边 / 阴影（这些现在来自 .brw）。
 * 但**内边距留着**：上游这一版把横向内缩放在这里，清零会让正文直接贴到卡片边缘。
 *   （定稿版是各区块自己带 34px —— .brp-head / .brp-foot 那一套；上游这版没有那些区块，
 *     改成了居中的 .brp-hero 欢迎标题，所以内缩只能留在本类上。）
 * 取值与同模块的结论页 .brr 对齐（那一页未被本次合并影响）：横向 34px / 上 26px / 下 30px ——
 *   切标签时三页的首块顶边与左右边缘才落在同一根线上。
 * 类名保留：下面十几条 :deep 选择器都以它开头，改名不划算；它现在只是「带内边距的普通块」。 */
.brp-card {
  padding: 26px 34px 30px;
}

.brp-hero {
  padding: 6px 0 26px;
  text-align: center;
}

.brp-hero__title {
  margin: 0 0 10px;
  font-size: 26px;
  font-weight: 700;
  letter-spacing: 0.5px;
  color: #1a1a1a;
}

.brp-hero__title .hl {
  color: #1677ff;
}

.brp-hero__sub {
  margin: 0;
  font-size: 13.5px;
  color: #8c8c8c;
}

.brp-form {
  padding-bottom: 8px;
}

/* 通栏上传区：外层浅蓝虚线圆角框 + 内层浅蓝底拖拽面 */
.brp-zone {
  padding: 8px;
  margin-bottom: 16px;
  border: 1.5px dashed #c9d8ee;
  border-radius: 12px;
}

.brp-drop :deep(.ant-upload-drag) {
  padding: 30px 16px;
  background: #f7fbff;
  border: none;
  border-radius: 8px;
  transition: background 0.2s;
}

.brp-drop :deep(.ant-upload-drag:hover) {
  background: #f0f7ff;
}

.brp-zone__icons {
  display: flex;
  gap: 14px;
  justify-content: center;
  margin-bottom: 14px;
}

.file-ic {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 46px;
  height: 46px;
  font-size: 13px;
  font-weight: 700;
  color: #fff;
  border-radius: 9px;
}

.file-ic.doc {
  background: linear-gradient(180deg, #3f7bff, #2f6bff);
  box-shadow: 0 6px 14px rgb(47 107 255 / 25%);
}

.file-ic.pdf {
  background: linear-gradient(180deg, #8b6cf5, #7b5cf0);
  box-shadow: 0 6px 14px rgb(123 92 240 / 25%);
}

.brp-zone__main {
  font-size: 13.5px;
  color: #595959;
  text-align: center;
}

.brp-zone__main span {
  color: #1677ff;
}

.brp-zone__hint {
  max-width: 560px;
  margin: 8px auto 0;
  font-size: 12px;
  line-height: 1.8;
  color: #a6a6a6;
  text-align: center;
}

.brp-file {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 12px 14px;
  font-size: 13px;
  background: #fafafa;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
}

.brp-file__name {
  color: #262626;
  word-break: break-all;
}

.brp-file__size {
  color: #a6a6a6;
}

/* 所属企业区块已整体下线（2026-09-22）：页面不展示、不让选，系统静默默认延河热力集团有限公司 */

.brp-actions {
  display: flex;
  gap: 14px;
  align-items: center;
  justify-content: center;
  padding-top: 6px;
}

.brp-actions__hint {
  font-size: 12px;
  color: #a6a6a6;
}

.brp-result {
  padding-top: 24px;
  margin-top: 28px;
  border-top: 1px solid #f0f0f0;
}

.brp-result__head {
  display: flex;
  gap: 10px;
  align-items: center;
  margin-bottom: 10px;
}

/* ============ 审查进度状态标签（排队中 / 审查中 / 已完成 / 已失败 / 已取消）压回白底 ============
 * 2026-09-23 用户口径：「审查中」标签底色是黑的，不便阅读，改成白色。
 * 根因：门户布局 .portal-light 只锁自定义 CSS 变量，锁不住 antd 的设计令牌；而本应用全局跑的是
 *   theme.darkAlgorithm（app.vue），于是 <a-tag color="processing"> 的底色取 colorPrimaryBg
 *   （深色主题下是近黑的深蓝）、文字取 colorPrimaryText 也偏暗，压在这张白卡片上就是一块黑底。
 *   与「审查历史」表格黑底（history.vue 里那组 :deep 覆盖）是同一个病根。
 * 做法：底色一律压白，只保留各状态自己的**文字色与描边色** —— 颜色语义不变（审查中蓝 / 已完成绿 /
 *   已失败红 / 已取消灰），可读性靠白底 + 深字拿回来。
 * 两条必须写死的细节：
 *   1. 基础规则里先给一套「白底 + 深灰字 + 浅灰描边」兜底。因为 color="default" 这一档
 *      antd 没有对应的状态色规则，只压底色会让文字仍是深色主题的浅字 —— 白底白字同样看不见。
 *   2. !important 不能省：antd 的标签样式由 CSS-in-JS 在运行时注入，状态色类自带 background
 *      与 color，不抬权压不住（与 history.vue 表格那组的处理一致）。
 * 只作用于本页「审查进度」这一行，抽屉里的报告标签不受影响。 */
.brp-result__head :deep(.ant-tag) {
  color: #595959 !important;
  background: #fff !important;
  border-color: #d9d9d9 !important;
}

/* 排队中 / 审查中 —— 本页强调色（与 .brp-hero__title .hl 同值；标签保持 antd 浅底深字那一档的蓝） */
.brp-result__head :deep(.ant-tag-processing) {
  color: #1677ff !important;
  border-color: #91caff !important;
}

/* 已完成 */
.brp-result__head :deep(.ant-tag-success) {
  color: #389e0d !important;
  border-color: #b7eb8f !important;
}

/* 已失败 */
.brp-result__head :deep(.ant-tag-error) {
  color: #cf1322 !important;
  border-color: #ffa39e !important;
}

.brp-result__title {
  font-size: 14px;
  font-weight: 600;
  color: #1a1a1a;
}

.brp-result__step {
  font-size: 12px;
  color: #8c8c8c;
}

.brp-result__ops {
  margin-top: 8px;
  font-size: 13px;
}

.brp-verdict {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  align-items: baseline;
  padding: 16px 18px;
  margin-top: 16px;
  background: #fafafa;
  border-left: 3px solid #d9d9d9;
  border-radius: 4px;
}

.brp-verdict.pass {
  background: #f6ffed;
  border-left-color: #52c41a;
}

.brp-verdict.risk {
  background: #fffbe6;
  border-left-color: #faad14;
}

.brp-verdict.fail {
  background: #fff2f0;
  border-left-color: #ff4d4f;
}

.brp-verdict__label {
  font-size: 13px;
  color: #8c8c8c;
}

.brp-verdict__value {
  font-size: 18px;
  font-weight: 600;
  color: #1a1a1a;
}

.brp-verdict__meta {
  display: flex;
  gap: 16px;
  font-size: 12.5px;
  color: #595959;
}

.brp-issues {
  margin-top: 10px;
}

.brp-issue {
  padding: 10px 0;
  border-bottom: 1px dashed #f0f0f0;
}

.brp-issue__title {
  font-size: 13.5px;
  font-weight: 600;
  color: #262626;
}

.brp-issue__line {
  margin-top: 4px;
  font-size: 12.5px;
  line-height: 1.7;
  color: #595959;
  word-break: break-all;
}

.brp-report__meta {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 10px;
}

.brp-flex-spacer {
  flex: 1;
}

.brp-report__body {
  padding: 14px;
  overflow: auto;
  font-size: 13px;
  line-height: 1.8;
  background: #fafafa;
  border: 1px solid #f0f0f0;
}

.brp-report__footer {
  margin-top: 10px;
  font-size: 12px;
  color: #a6a6a6;
}

/* 窄屏：横向内缩与「审查历史」「审查结论」两页一起收到 20px ——
 *   这条约定写在结论页 .brr 的媒体查询注释里（「三页窄屏留白才一致」）。
 *   定稿版是各区块自己收（.brp-head / .brp-form / .brp-result），上游这版内缩在本类上，故收这里。 */
@media (max-width: 720px) {
  .brp-card {
    padding-right: 20px;
    padding-left: 20px;
  }
}
</style>
