<script lang="ts" setup>
/**
 * 门户 · 审查历史（个人端）
 *
 * 模块拆分（用户 2026-09-17）：与「标书审查」（同目录 index.vue）风格一致的白卡片模块，
 * 只负责查看本人历史审查记录：
 *  - 已完成 → 「查看」跳转标书审查结论页（是否废标 + 废标原因）；
 *  - 失败   → 「重试」重新提交；
 *  - 队列/审查中 → 状态点自动轮询刷新；
 *  - 任意记录可删除。
 * 暴露 reload() 供父级在提交新审查后刷新列表。
 */
import { onBeforeUnmount, onMounted, reactive } from 'vue';
import { useRouter } from 'vue-router';

// 同 index.vue：Pagination 未全局注册，显式引入后 <a-pagination> 才会渲染（历史列表分页）
import { message, Modal, Pagination as APagination } from 'ant-design-vue';

import {
  type BidReviewApi,
  deleteMyReview,
  getMyReviewPage,
  retryMyReview,
} from '#/api/bidding/bidReview';
// 平台 Jackson 把 LocalDateTime 序列化为 epoch 毫秒，直接插值会渲染成 1789611535000 这种数字，
// 必须过统一的时间格式化工具（项目约定：`#/utils/datetime`）。
import { fmtDateTime } from '#/utils/datetime';

// 模块共享样式：--bid-* 令牌与共享类。令牌挂在 .module-page 等类名上，故根元素同时挂 module-page。
// 本页 2026-09-22 起不再自带卡片外壳（见下方 .brh-card 的注释），观感由外层那张大卡片提供。
import '../styles/bid-ui.css';

// 组件名用于 workbench.vue 的 <KeepAlive :include>：与「标书审查」互切时保留列表状态与轮询
defineOptions({ name: 'BidReviewPortalHistory' });

const router = useRouter();

const history = reactive({
  list: [] as BidReviewApi.Task[],
  loading: false,
  pageNo: 1,
  pageSize: 5,
  total: 0,
});

/** 搜索条件（页面拆分后历史页自带搜索；输入框统一白色，见 workbench 的 :deep 覆盖） */
const filters = reactive<{ taskName?: string; status?: string; verdict?: string }>({
  taskName: undefined,
  status: undefined,
  verdict: undefined,
});

const statusOptions = [
  { value: 'QUEUED', label: '排队中' },
  { value: 'RUNNING', label: '审查中' },
  { value: 'SUCCESS', label: '已完成' },
  { value: 'FAILED', label: '失败' },
  { value: 'CANCELED', label: '已取消' },
];

const verdictOptions = [
  { value: 'FAIL', label: '不通过（废标）' },
  { value: 'RISK', label: '有风险' },
  { value: 'PASS', label: '通过' },
];

let timer: any = null;

function statusText(status: string): string {
  const map: Record<string, string> = {
    QUEUED: '排队中',
    RUNNING: '审查中',
    SUCCESS: '已完成',
    FAILED: '失败',
    CANCELED: '已取消',
  };
  return map[status] ?? status;
}

function verdictText(verdict?: string): string {
  const map: Record<string, string> = { PASS: '通过', RISK: '有风险', FAIL: '不通过' };
  return map[verdict ?? ''] ?? '—';
}

/**
 * 斑马纹：偶数行（索引从 0 起，故取奇数）加极浅灰底。
 *
 * 用行类名而不是 CSS nth-child：antd v5 会在 tbody 顶部插一行隐藏的
 * `.ant-table-measure-row`，nth-child 的奇偶会被它错位，按数据索引算才稳。
 */
function rowClassName(_record: BidReviewApi.Task, index: number): string {
  return index % 2 === 1 ? 'brh-row-alt' : '';
}

async function load() {
  history.loading = true;
  try {
    const page = await getMyReviewPage({
      pageNo: history.pageNo,
      pageSize: history.pageSize,
      taskName: filters.taskName || undefined,
      status: (filters.status as any) || undefined,
      verdict: (filters.verdict as any) || undefined,
    } as BidReviewApi.TaskPageReq);
    history.list = page.list;
    history.total = Number(page.total ?? 0);
    schedulePoll();
  } finally {
    history.loading = false;
  }
}

/** 存在进行中的任务时每 5s 轻量刷新，全部结束自动停止 */
function schedulePoll() {
  if (timer) {
    clearInterval(timer);
    timer = null;
  }
  const active = history.list.some((t) => ['QUEUED', 'RUNNING'].includes(t.status));
  if (active) {
    timer = setInterval(load, 5000);
  }
}

/** 查询（回到第 1 页） */
function search() {
  history.pageNo = 1;
  void load();
}

function resetFilters() {
  filters.taskName = undefined;
  filters.status = undefined;
  filters.verdict = undefined;
  search();
}

/** 跳到「标书审查结论」页（是否废标 + 废标原因） */
function view(record: BidReviewApi.Task) {
  router.push({
    path: '/portal/bidding/bidReview/result',
    query: { taskId: String(record.id) },
  });
}

async function retry(id: number) {
  await retryMyReview(id);
  message.success('已重新提交');
  await load();
}

function remove(id: number) {
  Modal.confirm({
    title: '确认删除这条审查记录？',
    async onOk() {
      await deleteMyReview(id);
      message.success('已删除');
      await load();
    },
  });
}

onMounted(load);
onBeforeUnmount(() => {
  if (timer) {
    clearInterval(timer);
    timer = null;
  }
});

defineExpose({ reload: load });
</script>

<template>
  <div class="brh-card module-page">
    <div class="brh-head">
      <h1>审查历史</h1>
      <p>查看已提交的审查任务、结论与问题统计；新提交的审查会自动出现在这里。</p>
    </div>

    <!-- 搜索区（统一白色搜索框；状态/结论为下拉筛选；每个条件前带字段提示） -->
    <div class="brh-search">
      <div class="brh-field">
        <label class="brh-field__label" for="brh-name">任务名称</label>
        <a-input
          id="brh-name"
          v-model:value="filters.taskName"
          class="brh-search__input"
          allow-clear
          placeholder="请输入任务名称"
          @press-enter="search"
        />
      </div>
      <div class="brh-field">
        <label class="brh-field__label">状态</label>
        <a-select
          v-model:value="filters.status"
          class="brh-search__select"
          allow-clear
          placeholder="请选择"
          :options="statusOptions"
        />
      </div>
      <div class="brh-field">
        <label class="brh-field__label">结论</label>
        <a-select
          v-model:value="filters.verdict"
          class="brh-search__select"
          allow-clear
          placeholder="请选择"
          :options="verdictOptions"
        />
      </div>
      <a-button type="primary" @click="search">查询</a-button>
      <a-button @click="resetFilters">重置</a-button>
    </div>

    <div class="brh-table-head">
      <span>我的审查记录</span>
      <a @click="load">刷新</a>
    </div>
    <a-table
      size="small"
      row-key="id"
      :bordered="false"
      :data-source="history.list"
      :loading="history.loading"
      :pagination="false"
      :row-class-name="rowClassName"
    >
      <a-table-column title="任务名称" data-index="taskName" />
      <a-table-column title="状态" :width="120">
        <template #default="{ record }">
          <span
            :class="[
              'brh-dot',
              record.status === 'SUCCESS' ? 'ok' : record.status === 'FAILED' ? 'err' : 'run',
            ]"
          />
          {{ statusText(record.status) }}
        </template>
      </a-table-column>
      <a-table-column title="结论" :width="100">
        <template #default="{ record }">
          {{ verdictText(record.verdict) }}
        </template>
      </a-table-column>
      <a-table-column title="问题" :width="110">
        <template #default="{ record }">
          {{ record.issueCnt ?? 0 }}（高危 {{ record.highIssueCnt ?? 0 }}）
        </template>
      </a-table-column>
      <a-table-column title="提交时间" :width="170">
        <template #default="{ record }">{{ fmtDateTime(record.createTime) }}</template>
      </a-table-column>
      <a-table-column title="完成时间" :width="170">
        <template #default="{ record }">
          {{ record.finishedTime ? fmtDateTime(record.finishedTime) : '—' }}
        </template>
      </a-table-column>
      <a-table-column title="操作" :width="170">
        <template #default="{ record }">
          <a v-if="record.status === 'SUCCESS'" @click="view(record)">查看</a>
          <span v-if="record.status === 'FAILED'">
            <a @click="retry(record.id)">重试</a>
          </span>
          <a-divider type="vertical" />
          <a class="danger" @click="remove(record.id)">删除</a>
        </template>
      </a-table-column>
    </a-table>
    <a-pagination
      v-if="history.total > history.pageSize"
      v-model:current="history.pageNo"
      class="brh-pager"
      size="small"
      :page-size="history.pageSize"
      :total="history.total"
      @change="load"
    />
  </div>
</template>

<style scoped>
/* 与「标书审查」（bidReview/index.vue）同一套白卡片风格 */
.brh-search {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 14px;
  align-items: center;
  padding-bottom: 14px;
  margin-bottom: 4px;
  border-bottom: 1px solid var(--bid-border-soft);
}

/* 单个搜索条件：字段提示 + 控件（任务名称 / 状态 / 结论）
 *   —— 2026-09-22 用户口径「三个输入框/下拉框前面展示提示字段」。
 * 用 label 而不是只靠 placeholder：占位符一输入就消失，看不出这一格筛的是什么条件。 */
.brh-field {
  display: inline-flex;
  gap: 8px;
  align-items: center;
}

/* 字段提示加粗到 600：13px/400 与输入框里的文字同重，看着像控件自带的一部分 */
.brh-field__label {
  font-size: 13px;
  font-weight: 600;
  color: var(--bid-text-1);
  white-space: nowrap;
}

.brh-search__input {
  width: 240px;
}

.brh-search__select {
  width: 150px;
}

/* 搜索框统一白色（含带清除图标的 affix 包装与下拉框） */
.brh-search :deep(.ant-input),
.brh-search :deep(.ant-input-affix-wrapper),
.brh-search :deep(.ant-select:not(.ant-select-customize-input) .ant-select-selector) {
  background: #fff !important;
}

/* 输入文字压深、占位保持浅灰：门户布局（.portal-light）只锁自定义 CSS 变量，
 * 锁不住 antd 的文本令牌；全局主题偏暗时会出现「白底 + 浅字」。 */
.brh-search :deep(.ant-input),
.brh-search :deep(.ant-select-selection-item) {
  color: var(--bid-text-1);
}

.brh-search :deep(.ant-input)::placeholder,
.brh-search :deep(.ant-select-selection-placeholder) {
  color: var(--bid-text-4);
}

/* 2026-09-22：外层大卡片（.brw）本身就是页面卡片，本页不再自己套一张 ——
 *   去掉 max-width: 1000px 居中 / 白底 / 8px 圆角 / 描边 / 阴影（这些现在来自 .brw）。
 * 内边距留着并按同模块口径取值：横向 34px / 上 26px / 下 30px（与结论页 .brr 一致）——
 *   上游这一版把横向内缩放在这里，清零会让表格直接贴到卡片边缘；
 *   取 34px 而非上游原来的 36px，是为了与「标书审查」「审查结论」两页逐像素对齐。 */
.brh-card {
  padding: 26px 34px 30px;
}

.brh-head h1 {
  margin: 0 0 6px;
  font-size: 20px;
  font-weight: 600;
  color: #1a1a1a;
}

.brh-head p {
  margin: 0 0 26px;
  font-size: 13px;
  line-height: 1.7;
  color: #8c8c8c;
}

.brh-table-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 10px;
  margin-bottom: 10px;
  font-size: 14px;
  font-weight: 600;
  color: #1a1a1a;
  border-bottom: 1px solid #f0f0f0;
}

/* ============ 「我的审查记录」表格：白底深字 + 浅色横线 + 斑马纹 ============
 * 2026-09-22 用户口径「我的审查记录下面的黑色背景改为白色背景」。
 * 根因（2026-09-20 已定位）：门户布局 `.portal-light` 只锁自定义 CSS 变量，锁不住 antd 的
 *   设计令牌；全局主题偏暗时 a-table 自带底色取 colorBgContainer（深色），压在白色卡片上
 *   就是「我的审查记录」下方一整块黑底。同理表头竖线（th 的 ::before，取 colorBorderSecondary）、
 *   行分隔线（非 bordered 表格是给 td 画 border-top）在深色令牌下都是深色，
 *   压在白底上就是一条条黑线 —— 所以这三样必须一起压回浅色，只压底色会剩下黑线。
 * 这里把容器 / 表头 / 表体 / 空态一并压白。 */
.brh-card :deep(.ant-table),
.brh-card :deep(.ant-table-container),
.brh-card :deep(.ant-table-content),
.brh-card :deep(.ant-table-thead > tr > th),
.brh-card :deep(.ant-table-tbody > tr > td),
.brh-card :deep(.ant-table-tbody > tr.ant-table-placeholder > td),
.brh-card :deep(.ant-table-tbody > tr.ant-table-placeholder:hover > td) {
  color: var(--bid-text-1);
  background: #fff !important;
}

/* 去掉竖向分割线，只保留下面的横向浅色底线。
 * 要点：表头列之间的竖线不是 border，而是 ant-design-vue 4.x 给 th 加的 ::before 伪元素
 * （table/style/index.js，颜色取 colorBorderSecondary）；只写 border-right: 0 去不掉。 */
.brh-card :deep(.ant-table-thead > tr > th::before),
.brh-card :deep(.ant-table-thead > tr > td::before) {
  display: none !important;
}

/* 兜底：万一开了 bordered，竖向的 border-right 也一并去掉 */
.brh-card :deep(.ant-table-cell) {
  border-right: 0;
}

/* 横向浅色底线：表头底边、每行分隔线、末行底边统一成 --bid-border-soft
 * （与上方「我的审查记录」标题线、卡片头底线同一支浅灰）。 */
.brh-card :deep(.ant-table-thead > tr > th) {
  font-weight: 600;
  color: var(--bid-text-2);
  border-bottom: 1px solid var(--bid-border-soft) !important;
}

/* 行分隔线只画 td 的 border-top：若上下都画，相邻两行会叠成 2px，比表头那条明显更粗。
 * 末行再单独补一条底边收住表格下沿。 */
.brh-card :deep(.ant-table-tbody > tr > td) {
  border-top: 1px solid var(--bid-border-soft) !important;
  border-bottom: 0 !important;
}

.brh-card :deep(.ant-table-tbody > tr:last-child > td) {
  border-bottom: 1px solid var(--bid-border-soft) !important;
}

/* 首行不画上边线，否则会和表头底线叠成双线（antd 原本也是这么处理的） */
.brh-card :deep(.ant-table-tbody > tr:first-child > td),
.brh-card :deep(.ant-table-tbody > tr.ant-table-measure-row + tr > td) {
  border-top: 0 !important;
}

/* 隐藏的测量行不画任何线，避免表头下方多出一条 1px 的线 */
.brh-card :deep(.ant-table-tbody > tr.ant-table-measure-row > td) {
  border: 0 !important;
}

/* 斑马纹：偶数行极浅灰蓝底（类名由 script 里的 rowClassName 按数据索引给） */
.brh-card :deep(.ant-table-tbody > tr.brh-row-alt > td) {
  background: var(--bid-surface-2) !important;
}

/* 悬停再深一档，奇数行与斑马行都能看出反馈
 * （深色主题默认值是深色，不处理会盖出黑条）。
 * 用主色浅底而不是灰底：与斑马纹的色相差得开，一眼能分清「这行是我指着的那行」。 */
.brh-card :deep(.ant-table-tbody > tr:hover > td),
.brh-card :deep(.ant-table-tbody > tr > td.ant-table-cell-row-hover) {
  background: var(--bid-primary-soft) !important;
}

/* 空表提示文字保持浅灰 */
.brh-card :deep(.ant-empty-description) {
  color: var(--bid-text-3);
}

.brh-dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  margin-right: 6px;
  vertical-align: middle;
  border-radius: 50%;
}

.brh-dot.ok {
  background: #52c41a;
}

.brh-dot.err {
  background: #ff4d4f;
}

.brh-dot.run {
  background: #1677ff;
}

.brh-pager {
  margin-top: 12px;
  text-align: right;
}

/* 窄屏：横向内缩与「标书审查」「审查结论」两页一起收到 20px
 *   （约定见结论页 .brr 的媒体查询注释）。定稿版收在 .brh-head / .brh-body 上，
 *   上游这版内缩在本类上，故收这里。 */
@media (max-width: 720px) {
  .brh-card {
    padding-right: 20px;
    padding-left: 20px;
  }

  /* 窄屏：带字段提示的搜索条件各占一整行，提示与控件不再挤在一起 */
  .brh-field {
    flex: 1 1 100%;
  }
}
</style>
