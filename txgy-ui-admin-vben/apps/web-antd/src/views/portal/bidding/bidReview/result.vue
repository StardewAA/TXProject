<script lang="ts" setup>
/**
 * 门户 · 标书审查结论（废标判定）—— 新建页面。
 *
 * 需求（用户 2026-09-17）：点「一键审查」后，给出**是否废标**的判断，以及**废标原因**。
 * 口径（与后端一致）：
 *   - 命中「高危」规则（且未被复核忽略）→ **废标**（存在废标风险），原因就是这些高危问题；
 *   - 只有中/低危问题 → 不废标（存在风险，建议整改）；
 *   - 无问题 → 不废标（通过）。
 * 证据文案：AI 在标书里找不到对应内容时，证据统一显示「标书没有对应内容」。
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';

// 同 index.vue：Empty / Spin 未全局注册，必须显式引入，否则 <a-empty>/<a-spin> 渲染不出来
import { Empty as AEmpty, message, Spin as ASpin } from 'ant-design-vue';

import { IconifyIcon } from '@vben/icons';

import {
  type BidReviewApi,
  downloadPlatformFile,
  getMyReviewIssues,
  getMyReviewReportDoc,
  getMyReviewTask,
  retryMyReview,
} from '#/api/bidding/bidReview';

// 2026-09-21 视觉改版：与「标书审查」表单页/「审查历史」同一套令牌与卡片规格（styles/bid-ui.css）。
import '../styles/bid-ui.css';

const route = useRoute();

const taskId = computed(() => Number(route.query.taskId ?? 0));
const loading = ref(true);
const task = ref<BidReviewApi.Task | null>(null);
const issues = ref<BidReviewApi.Issue[]>([]);
const docLoading = ref(false);
const retrying = ref(false);
const docUrl = ref('');
const docFileName = ref('');

const rejected = computed(() => Boolean(task.value?.rejected));
/** 排队中 / 审查中：重跑期间 verdict 与问题清单已被清空，结论区要换成「审查中」提示 */
const running = computed(() => ['QUEUED', 'RUNNING'].includes(task.value?.status ?? ''));
const highIssues = computed(() => issues.value.filter((i) => i.severity === 'HIGH' && i.status !== 'IGNORED'));
const mediumIssues = computed(() => issues.value.filter((i) => i.severity === 'MEDIUM' && i.status !== 'IGNORED'));
const lowIssues = computed(() => issues.value.filter((i) => i.severity === 'LOW' && i.status !== 'IGNORED'));
const rejectReasons = computed(() => task.value?.rejectReasons ?? highIssues.value.map((i) => i.issueTitle));

/**
 * 结论横幅左侧那枚图标跟着状态走 —— 与顶部「合规审查」的盾牌图标同一套符号：
 *   废标风险 = 盾牌带感叹号、有中危但未命中废标项 = 空盾、无问题 = 打勾盾、重跑中 = 转圈。
 * 图标只做状态的视觉注脚，结论本身仍然写在大字里，不靠图形表意。
 *
 * 图标名只用长期存在、从未被上游改过名的 lucide 图标（shield-check / shield-alert / shield /
 * refresh-cw）。iconify 的 lucide 图标是整包离线注入的（apps/web-antd/src/offline-icons.ts），
 * 名字写错不会报错、只会渲染成空白，所以这里刻意避开 loader-circle 这类后来才改名的图标。
 */
const verdictIcon = computed(() => {
  if (running.value) return 'lucide:refresh-cw';
  if (rejected.value) return 'lucide:shield-alert';
  return mediumIssues.value.length ? 'lucide:shield' : 'lucide:shield-check';
});

let timer: any = null;

/** 审查中每 5s 拉一次，跑到终态自动停 */
function schedulePoll() {
  if (timer) {
    clearInterval(timer);
    timer = null;
  }
  if (running.value) {
    timer = setInterval(load, 5000);
  }
}

async function load() {
  loading.value = true;
  try {
    task.value = await getMyReviewTask(taskId.value);
    issues.value = await getMyReviewIssues(taskId.value);
    if (running.value) {
      // 重跑中不取报告文档：该接口会「生成」Word（同一任务只生成一次），
      // 每 5s 轮询调它会反复触发生成；而且此时报告文件指针已被后端清空。
      docUrl.value = '';
      docFileName.value = '';
    } else {
      try {
        const doc = await getMyReviewReportDoc(taskId.value);
        docUrl.value = doc?.url ?? '';
        docFileName.value = doc?.fileName ?? '审查报告.docx';
      } catch {
        docUrl.value = '';
        docFileName.value = '';
      }
    }
  } catch (error: any) {
    message.error(error?.message ?? '加载审查结论失败');
  } finally {
    loading.value = false;
    schedulePoll();
  }
}

function downloadDoc() {
  if (!downloadPlatformFile(docUrl.value, docFileName.value)) {
    message.warning('报告文档尚未生成，请稍后重试');
  }
}

/**
 * 取后端返回的错误提示。
 *
 * 口径：requestClient 在「HTTP 200 + 业务 code ≠ 0」以及 HTTP 报错两种情况下，
 * 抛出的都不是标准 Error，而是 axios 的响应对象（见 preset-interceptors.ts），
 * 因此 error.message 取不到东西，真实提示在 error.response.data.msg。
 */
function readErrMsg(error: any): string | undefined {
  return (
    error?.response?.data?.msg ?? error?.data?.msg ?? error?.message ?? undefined
  );
}

/**
 * 重新审查：对当前任务重跑一次审查流水线。
 *
 * 之前这里写的是 `router.push('/portal/bidding/compliance')`——那个页面是另一套
 * 「本地流程状态机版」合规审查页（只列 currentState=BIDDED 的流程条目），与标书审查
 * 的个人任务不互通，所以点进来永远是空态（用户 2026-09-20 反馈「跳到空白页」）。
 *
 * 后端口径（BidReviewTaskServiceImpl#doRetry）：只要任务不是「审查中」、且重试次数未超 3 次，
 * 就清空上次的问题清单/报告文件指针、把状态重置为「排队中」并重新执行流水线，因此
 * 对「已完成」的任务同样可以重跑。
 *
 * 重跑期间 verdict / 问题清单会被清空，若直接刷新本页会显示成「否（未发现废标项）」，属于误导；
 * 所以本页对「排队中 / 审查中」单独走一个「审查中」提示态（见 running），并每 5s 自动刷新，
 * 跑完自动出新的结论。也不能跳「审查历史」去看：那边按 id 倒序分页，重跑沿用旧 id，翻不到第一页。
 */
async function reReview() {
  if (!taskId.value) {
    return;
  }
  retrying.value = true;
  try {
    await retryMyReview(taskId.value);
    message.success('已重新提交审查，正在排队');
    // 重新拉一次：状态会变成「排队中」，页面切到审查中态并开始轮询
    await load();
  } catch (error: any) {
    // 注意：业务失败时 requestClient 抛的是「axios 响应对象」而不是 Error
    // （packages/effects/request/src/request-client/preset-interceptors.ts 里是
    //  `throw Object.assign({}, response, { response })`），所以 error.message 是 undefined，
    // 后端的真实提示在 error.response.data.msg。之前只取 error.message → 只剩一句
    // 「重新审查提交失败」，用户无法分辨是「任务执行中」还是「重试次数已达上限」。
    // 另外 retry 接口带 X-Silent-Error，全局也不会弹错，这里必须自己提示。
    message.error(readErrMsg(error) ?? '重新审查提交失败');
  } finally {
    retrying.value = false;
  }
}

function evidenceOf(issue: BidReviewApi.Issue) {
  return issue.evidenceText || '标书没有对应内容';
}

onMounted(load);
onBeforeUnmount(() => {
  if (timer) {
    clearInterval(timer);
    timer = null;
  }
});
</script>

<template>
  <div class="brr module-page">
    <a-spin :spinning="loading">
      <!-- 重新审查进行中：结论区让位给「审查中」提示（重跑会先清空 verdict 与问题清单，
          此时照常渲染会显示成「否 —— 未发现废标项」，属于误导） -->
      <div v-if="running" class="brr-verdict is-running">
        <span class="brr-verdict__badge">
          <IconifyIcon :icon="verdictIcon" />
        </span>
        <div class="brr-verdict__text">
          <div class="brr-verdict__label">审查状态</div>
          <div class="brr-verdict__value">
            {{ task?.status === 'QUEUED' ? '排队中' : '审查中' }}…
          </div>
          <div class="brr-verdict__sub">
            正在重新审查这份标书，通常 1~3 分钟；完成后本页会自动刷新出新的结论，无需手动操作。
          </div>
        </div>
      </div>

      <template v-else>
      <!-- 结论横幅：本页的主角。左侧盾牌图标跟状态走，右侧是「是否废标」的大字结论 -->
      <div class="brr-verdict" :class="rejected ? 'is-reject' : 'is-pass'">
        <span class="brr-verdict__badge">
          <IconifyIcon :icon="verdictIcon" />
        </span>
        <div class="brr-verdict__text">
          <div class="brr-verdict__label">是否废标</div>
          <div class="brr-verdict__value">
            {{ rejected ? '是 —— 存在废标风险' : '否' }}
          </div>
          <div class="brr-verdict__sub">
            <template v-if="rejected">
              命中 {{ highIssues.length }} 项高危要求（废标项），需按下方原因整改后重新提交
            </template>
            <template v-else-if="mediumIssues.length">
              未命中废标项，但存在 {{ mediumIssues.length }} 项中风险问题，建议整改
            </template>
            <template v-else>未发现废标项与明显风险</template>
          </div>
        </div>
        <div class="brr-verdict__meta">
          <span>任务：{{ task?.taskNo }}</span>
          <span>标书：{{ task?.bidFileName || '—' }}</span>
          <span>规则：{{ task?.ruleCnt ?? 0 }} 条</span>
          <span>高危 {{ highIssues.length }} / 中危 {{ mediumIssues.length }} / 低危 {{ lowIssues.length }}</span>
          <span v-if="task?.scoreTotal">自评得分 {{ task.scoreTotal }}</span>
        </div>
      </div>

      <!-- 废标原因 -->
      <div class="brr-card">
        <div class="brr-card__title">
          废标原因（高危项{{ rejectReasons.length }} 条）
        </div>
        <a-empty v-if="!highIssues.length" description="未命中废标项" />
        <div v-for="(issue, index) in highIssues" :key="issue.id" class="brr-issue">
          <div class="brr-issue__title">
            <span class="brr-badge">废标 {{ index + 1 }}</span>
            {{ issue.issueTitle }}
            <span v-if="issue.ruleCode" class="brr-issue__code">{{ issue.ruleCode }}</span>
          </div>
          <div v-if="issue.issueDesc" class="brr-issue__row"><b>原因：</b>{{ issue.issueDesc }}</div>
          <div class="brr-issue__row">
            <b>原文证据（{{ issue.hitLocation || '未标注位置' }}）：</b>{{ evidenceOf(issue) }}
          </div>
          <div class="brr-issue__row"><b>整改建议：</b>{{ issue.suggestion }}</div>
          <div class="brr-issue__row brr-issue__row--muted">复核状态：{{ issue.status }}</div>
        </div>
      </div>

      <!-- 其他风险 -->
      <div v-if="mediumIssues.length || lowIssues.length" class="brr-card">
        <div class="brr-card__title">其他风险（不构成废标）</div>
        <div v-for="issue in [...mediumIssues, ...lowIssues]" :key="issue.id" class="brr-issue brr-issue--slim">
          <div class="brr-issue__title">
            <span class="brr-badge" :class="issue.severity === 'MEDIUM' ? 'warn' : 'low'">
              {{ issue.severity === 'MEDIUM' ? '中危' : '低危' }}
            </span>
            {{ issue.issueTitle }}
          </div>
          <div v-if="issue.issueDesc" class="brr-issue__row"><b>原因：</b>{{ issue.issueDesc }}</div>
          <div class="brr-issue__row">
            <b>原文证据（{{ issue.hitLocation || '未标注位置' }}）：</b>{{ evidenceOf(issue) }}
          </div>
          <div class="brr-issue__row"><b>整改建议：</b>{{ issue.suggestion }}</div>
        </div>
      </div>
      </template>

      <!-- 操作 -->
      <div class="brr-actions">
        <a-button
          v-if="!running"
          class="brr-btn brr-btn--primary"
          type="primary"
          :loading="docLoading"
          @click="downloadDoc"
        >
          <IconifyIcon v-if="!docLoading" class="brr-btn__icon" icon="lucide:download" />
          下载审查报告（Word）
        </a-button>
        <a-button class="brr-btn" :disabled="running" :loading="retrying" @click="reReview">
          <IconifyIcon v-if="!retrying" class="brr-btn__icon" icon="lucide:rotate-ccw" />
          重新审查
        </a-button>
      </div>
    </a-spin>
  </div>
</template>

<style scoped>
.brr {
  /* 2026-09-21 用户口径：本页尺寸要与合规审查的大卡片一致 ——
   *   去掉 max-width: 1080px 与 margin: 0 auto（原来比另两页的 1000px 还宽 80px、
   *   且三页宽度各不一样），内容与大卡片同宽铺满；
   *   同时**补回自己的内边距**：原来上下左右的呼吸由外层 .brw-main 的 padding: 20px 提供，
   *   而那个浅灰面板已取消（大卡片即页面卡片）。这里取 26px 34px 30px ——
   *   横向 34px 与「智慧标书审查」(.brp-head/.brp-form) 和「审查历史」(.brh-head/.brh-body)
   *   的 34px 内缩对齐，上 26px 与那两页页头的顶部留白同值。 */
  padding: 26px 34px 30px;
}

/* ---- 结论横幅：整页的主角（其余卡片刻意安静） ----
 * 白底 + 一道暖金顶边（和表单页/历史页的卡片同款），状态色只走左边那条 5px 竖线，
 * 不再整块铺状态底色 —— 大字结论本身已经很响，底色再铺一层就吵了。 */
.brr-verdict {
  display: flex;
  flex-wrap: wrap;
  gap: 0 16px;
  align-items: flex-start;
  padding: 22px 26px;
  margin-bottom: 14px;
  background: var(--bid-surface);
  border: 1px solid var(--bid-border);
  border-top: 3px solid var(--bid-accent);
  border-left: 5px solid var(--bid-border);
  border-radius: var(--bid-radius-lg);
  box-shadow: var(--bid-shadow-md);
}

.brr-verdict.is-reject {
  border-left-color: var(--bid-red);
}

.brr-verdict.is-pass {
  border-left-color: var(--bid-green);
}

/* 重新审查进行中（排队中 / 审查中） */
.brr-verdict.is-running {
  border-left-color: var(--bid-primary-500);
}

/* 状态图标：方形圆角徽标，底色是状态色的浅底 */
.brr-verdict__badge {
  display: inline-flex;
  flex: 0 0 46px;
  align-items: center;
  justify-content: center;
  width: 46px;
  height: 46px;
  font-size: 24px;
  color: var(--bid-text-3);
  background: var(--bid-gray-soft);
  border-radius: var(--bid-radius);
}

.brr-verdict.is-reject .brr-verdict__badge {
  color: var(--bid-red);
  background: var(--bid-red-soft);
}

.brr-verdict.is-pass .brr-verdict__badge {
  color: var(--bid-green);
  background: var(--bid-green-soft);
}

.brr-verdict.is-running .brr-verdict__badge {
  color: var(--bid-primary-500);
  background: var(--bid-primary-soft);
}

/* 重跑中的转圈图标：唯一一处非用户触发的动效，用来说明「确实在跑」 */
.brr-verdict.is-running .brr-verdict__badge svg {
  animation: brr-spin 1.4s linear infinite;
}

@keyframes brr-spin {
  to {
    transform: rotate(360deg);
  }
}

.brr-verdict__text {
  flex: 1;
  min-width: 0;
}

.brr-verdict__label {
  font-size: 13px;
  color: var(--bid-text-3);
}

.brr-verdict__value {
  margin: 4px 0 6px;
  font-size: 28px;
  font-weight: 700;
  letter-spacing: 0.5px;
  color: var(--bid-text-1);
}

.brr-verdict.is-reject .brr-verdict__value {
  color: var(--bid-red);
}

.brr-verdict.is-pass .brr-verdict__value {
  color: var(--bid-green);
}

.brr-verdict.is-running .brr-verdict__value {
  color: var(--bid-primary-500);
}

.brr-verdict__sub {
  font-size: 13.5px;
  line-height: 1.75;
  color: var(--bid-text-2);
}

/* meta 行：flex-basis 100% 把它挤到图标/结论的下一行通栏，并用一条浅线隔开 */
.brr-verdict__meta {
  display: flex;
  flex: 1 0 100%;
  flex-wrap: wrap;
  gap: 8px 18px;
  padding-top: 13px;
  margin-top: 15px;
  font-size: 12.5px;
  color: var(--bid-text-2);
  border-top: 1px solid var(--bid-border-soft);
}

/* ---- 问题清单卡片 ---- */
.brr-card {
  padding: 18px 22px;
  margin-bottom: 14px;
  background: var(--bid-surface);
  border: 1px solid var(--bid-border);
  border-radius: var(--bid-radius-lg);
  box-shadow: var(--bid-shadow-sm);
}

.brr-card__title {
  padding-bottom: 10px;
  margin-bottom: 6px;
  font-size: 15px;
  font-weight: 600;
  color: var(--bid-text-1);
  border-bottom: 1px solid var(--bid-border-soft);
}

.brr-issue {
  padding: 12px 0;
  border-bottom: 1px dashed var(--bid-border-soft);
}

.brr-issue:last-child {
  border-bottom: none;
}

.brr-issue--slim {
  padding: 10px 0;
}

.brr-issue__title {
  font-size: 14px;
  font-weight: 600;
  color: var(--bid-text-1);
}

.brr-issue__code {
  margin-left: 8px;
  font-size: 12px;
  font-weight: 400;
  color: var(--bid-text-3);
}

.brr-issue__row {
  margin-top: 5px;
  font-size: 13px;
  line-height: 1.75;
  color: var(--bid-text-2);
  word-break: break-all;
}

.brr-issue__row--muted {
  color: var(--bid-text-3);
}

.brr-badge {
  display: inline-block;
  padding: 1px 8px;
  margin-right: 8px;
  font-size: 12px;
  font-weight: 400;
  color: #fff;
  background: var(--bid-red);
  border-radius: var(--bid-radius-pill);
}

.brr-badge.warn {
  background: var(--bid-orange);
}

.brr-badge.low {
  background: var(--bid-gray);
}

.brr-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  margin-top: 18px;
}

.brr-btn {
  height: 38px;
  padding: 0 20px;
  border-radius: var(--bid-radius-sm);
}

.brr-btn--primary.ant-btn-primary {
  background: linear-gradient(135deg, var(--bid-primary-500) 0%, var(--bid-primary) 100%);
  border-color: transparent;
  box-shadow: 0 2px 6px rgb(14 58 120 / 22%);
}

.brr-btn--primary.ant-btn-primary:hover:not(:disabled) {
  transform: translateY(-1px);
  filter: brightness(1.06);
  box-shadow: 0 6px 16px rgb(14 58 120 / 28%);
}

.brr-btn__icon {
  margin-right: 7px;
  font-size: 15px;
}

/* 审查中时「重新审查」是禁用态。门户锁不住 antd 令牌，深色主题下按钮禁用样式取的是
 *   colorTextDisabled ≈ rgba(255,255,255,.25)（近白 → 白卡片上等于看不见字）
 *   colorBorder ≈ #424242（深色描边 → 只剩一个黑框）
 *   colorBgContainerDisabled ≈ rgba(255,255,255,.08)（近透明 → 没有底色）
 * 三者叠起来就成了「一个看不见字的方框」（用户 2026-09-20 反馈）。
 * 这里按用户要求把文字压成黑色，描边/底色改浅灰，仍保留不可点的观感。 */
.brr-actions :deep(.ant-btn:disabled),
.brr-actions :deep(.ant-btn-disabled) {
  color: #1a1a1a !important;
  background: #f5f5f5 !important;
  border-color: #d9d9d9 !important;
}

/* 窄屏：图标徽标与结论改纵向堆叠，按钮铺满一行 */
@media (max-width: 720px) {
  /* 横向内缩跟着「智慧标书审查」「审查历史」两页一起收到 20px
     （它们各自的媒体查询里也是 20px），三页窄屏留白才一致 */
  .brr {
    padding-right: 20px;
    padding-left: 20px;
  }

  .brr-verdict {
    padding: 18px 20px;
  }

  .brr-actions {
    gap: 10px;
  }

  .brr-btn {
    flex: 1 1 100%;
  }
}

/* 尊重系统的「减少动态效果」：转圈图标与按钮位移一并停掉 */
@media (prefers-reduced-motion: reduce) {
  .brr-verdict.is-running .brr-verdict__badge svg {
    animation: none;
  }

  .brr-btn--primary.ant-btn-primary:hover:not(:disabled) {
    transform: none;
  }
}
</style>
