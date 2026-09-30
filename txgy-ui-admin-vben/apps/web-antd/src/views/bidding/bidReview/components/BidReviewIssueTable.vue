<!--
  标书审查 · 问题项表（任务详情与「问题复核」页共用）

  两种用法：
    · 任务详情：传 taskId，只看这个任务的问题清单（可复核）；
    · 问题复核：不传 taskId，跨任务看待复核问题（showTask 显示任务编号列）。
  筛选与分页一律交给后端（/issue/page 支持 taskId/severity/category/status/source/evidenceLack），
  不在前端做"拉全量再过滤"——那样分页与总数都会失真。
-->
<script lang="ts" setup>
import { computed, onMounted, reactive, ref } from 'vue';

import { Button, Empty, Input, message, Modal, Select, Space, Table, Textarea } from 'ant-design-vue';

import {
  type BidReviewApi,
  getReviewIssuePage,
  reviewIssue,
} from '#/api/bidding/bidReview';
import { fmtDateTime } from '#/utils/datetime';

import BidReviewDictTag from './BidReviewDictTag.vue';
import { useBiddingDict } from '../composables/useBiddingDict';

const props = withDefaults(
  defineProps<{
    /** 指定任务 = 单任务问题清单；不传 = 跨任务（问题复核页） */
    taskId?: null | number;
    /** 是否显示"任务编号"列（跨任务时必开） */
    showTask?: boolean;
    /** 是否显示筛选行 */
    showFilters?: boolean;
    /** 是否允许复核（由页面按 bidding:bid-review:audit 传入） */
    audit?: boolean;
    /** 初始复核状态筛选，如「问题复核」页默认 OPEN */
    defaultStatus?: string;
    /** 每页条数 */
    pageSize?: number;
    /** 任务下拉选项（跨任务页传入"任务编号+名称"列表；不传则退化为手填任务 id） */
    taskOptions?: Array<{ label: string; value: number }>;
  }>(),
  {
    taskId: null,
    showTask: false,
    showFilters: true,
    audit: false,
    defaultStatus: '',
    pageSize: 10,
    taskOptions: undefined,
  },
);

const emit = defineEmits<{ reviewed: [issue: BidReviewApi.Issue] }>();

const { dictLabel, dictOptions } = useBiddingDict();

const loading = ref(false);
const rows = ref<BidReviewApi.Issue[]>([]);
const total = ref(0);

const query = reactive<BidReviewApi.IssuePageReq>({
  pageNo: 1,
  pageSize: props.pageSize,
  taskId: props.taskId ?? undefined,
  status: props.defaultStatus || undefined,
  severity: undefined,
  category: undefined,
  source: undefined,
  evidenceLack: undefined,
});

const evidenceOptions = [
  { label: '证据不足（AI 未给出原文）', value: true },
  { label: '有原文证据', value: false },
];

/** 跨任务筛选用：任务筛选值（下拉或手填，查询时才落到 query.taskId，避免空串进后端） */
const taskFilter = ref<number | string | undefined>();

/* ===================== 复核弹窗 ===================== */
const reviewOpen = ref(false);
const reviewSaving = ref(false);
const reviewTarget = ref<BidReviewApi.Issue | null>(null);
const reviewForm = reactive<{ status: string; reviewRemark: string }>({
  status: 'CONFIRMED',
  reviewRemark: '',
});

const reviewTitle = computed(() => {
  const map: Record<string, string> = {
    CONFIRMED: '确认问题成立',
    IGNORED: '忽略（判定为误报）',
    FIXED: '标记已整改',
  };
  return map[reviewForm.status] ?? '复核问题';
});

async function load() {
  loading.value = true;
  try {
    const page = await getReviewIssuePage({
      ...query,
      taskId: props.taskId ?? query.taskId,
    });
    rows.value = page.list ?? [];
    total.value = Number(page.total ?? 0);
  } finally {
    loading.value = false;
  }
}

function search() {
  query.pageNo = 1;
  if (props.showTask) {
    const raw = taskFilter.value;
    const parsed = raw === undefined || raw === '' ? Number.NaN : Number(raw);
    query.taskId = Number.isNaN(parsed) ? undefined : parsed;
  }
  void load();
}

function reset() {
  query.severity = undefined;
  query.category = undefined;
  query.source = undefined;
  query.evidenceLack = undefined;
  if (props.showTask) {
    query.status = props.defaultStatus || undefined;
    query.taskId = undefined;
    taskFilter.value = undefined;
  }
  search();
}

function openReview(record: BidReviewApi.Issue, status: string) {
  reviewTarget.value = record;
  reviewForm.status = status;
  reviewForm.reviewRemark = status === 'IGNORED' ? '复核判定为误报' : '';
  reviewOpen.value = true;
}

async function submitReview() {
  if (!reviewTarget.value) {
    return;
  }
  if (reviewForm.status === 'IGNORED' && !reviewForm.reviewRemark.trim()) {
    message.warning('忽略必须填写理由（复核要留痕）');
    return;
  }
  reviewSaving.value = true;
  try {
    await reviewIssue({
      id: reviewTarget.value.id,
      status: reviewForm.status,
      reviewRemark: reviewForm.reviewRemark,
    });
    message.success('已复核，任务结论同步重算');
    reviewOpen.value = false;
    await load();
    emit('reviewed', reviewTarget.value);
  } finally {
    reviewSaving.value = false;
  }
}

onMounted(load);

defineExpose({ load, search });
</script>

<template>
  <div class="br-issue-table">
    <a-form v-if="showFilters" layout="inline" class="br-query">
      <a-form-item v-if="showTask" label="任务编号">
        <Select
          v-if="taskOptions && taskOptions.length > 0"
          v-model:value="taskFilter"
          allow-clear
          show-search
          option-filter-prop="label"
          placeholder="全部任务"
          style="width: 260px"
          :options="taskOptions"
        />
        <Input
          v-else
          v-model:value="taskFilter"
          allow-clear
          placeholder="任务 id（留空=全部任务）"
          style="width: 160px"
          @press-enter="search"
        />
      </a-form-item>
      <a-form-item label="级别">
        <Select
          v-model:value="query.severity"
          allow-clear
          placeholder="全部"
          style="width: 130px"
          :options="dictOptions('bid_review_severity')"
        />
      </a-form-item>
      <a-form-item label="类别">
        <Select
          v-model:value="query.category"
          allow-clear
          placeholder="全部"
          style="width: 150px"
          :options="dictOptions('bid_review_rule_category')"
        />
      </a-form-item>
      <a-form-item label="复核状态">
        <Select
          v-model:value="query.status"
          allow-clear
          placeholder="全部"
          style="width: 130px"
          :options="dictOptions('bid_review_issue_status')"
        />
      </a-form-item>
      <a-form-item label="来源">
        <Select
          v-model:value="query.source"
          allow-clear
          placeholder="全部"
          style="width: 130px"
          :options="dictOptions('bid_review_issue_source')"
        />
      </a-form-item>
      <a-form-item label="证据">
        <Select
          v-model:value="query.evidenceLack"
          allow-clear
          placeholder="全部"
          style="width: 200px"
          :options="evidenceOptions"
        />
      </a-form-item>
      <a-form-item>
        <Space>
          <Button type="primary" @click="search">查询</Button>
          <Button @click="reset">重置</Button>
        </Space>
      </a-form-item>
    </a-form>

    <Table
      size="small"
      row-key="id"
      :loading="loading"
      :data-source="rows"
      :scroll="{ x: 1400 }"
      :pagination="{
        current: query.pageNo,
        pageSize: query.pageSize,
        total,
        showSizeChanger: true,
        showTotal: (t: number) => `共 ${t} 条`,
        onChange: (p: number, s: number) => ((query.pageNo = p), (query.pageSize = s), load()),
      }"
    >
      <a-table-column v-if="showTask" title="任务编号" :width="180">
        <template #default="{ record }">
          <div>{{ record.taskNo ?? `#${record.taskId}` }}</div>
          <div class="br-issue-table__sub">{{ record.taskName }}</div>
        </template>
      </a-table-column>
      <a-table-column title="级别" :width="90">
        <template #default="{ record }">
          <BidReviewDictTag dict-type="bid_review_severity" :value="record.severity" />
        </template>
      </a-table-column>
      <a-table-column title="类别" :width="120">
        <template #default="{ record }">
          {{ dictLabel('bid_review_rule_category', record.category) }}
        </template>
      </a-table-column>
      <a-table-column title="问题" :width="220">
        <template #default="{ record }">
          <div>{{ record.issueTitle }}</div>
          <div class="br-issue-table__sub">{{ record.ruleCode }}</div>
        </template>
      </a-table-column>
      <a-table-column title="证据 / 定位" :width="320">
        <template #default="{ record }">
          <div class="br-issue-table__sub">{{ record.hitLocation || '未定位' }}</div>
          <div>{{ record.evidenceText || '标书没有对应内容' }}</div>
          <a-tag v-if="record.evidenceLack" color="orange" class="br-issue-table__flag">
            证据不足（按原级别计）
          </a-tag>
        </template>
      </a-table-column>
      <a-table-column title="整改建议" data-index="suggestion" :width="220" />
      <a-table-column title="来源" :width="110">
        <template #default="{ record }">
          {{ dictLabel('bid_review_issue_source', record.source) }}
        </template>
      </a-table-column>
      <a-table-column title="置信度" :width="90">
        <template #default="{ record }">
          {{ record.aiConfidence == null ? '—' : Number(record.aiConfidence).toFixed(2) }}
        </template>
      </a-table-column>
      <a-table-column title="复核状态" :width="110">
        <template #default="{ record }">
          <BidReviewDictTag dict-type="bid_review_issue_status" :value="record.status" />
        </template>
      </a-table-column>
      <a-table-column title="复核人 / 时间" :width="180">
        <template #default="{ record }">
          <div>{{ record.reviewerName || '—' }}</div>
          <div class="br-issue-table__sub">
            {{ record.reviewTime ? fmtDateTime(record.reviewTime) : '—' }}
          </div>
        </template>
      </a-table-column>
      <a-table-column v-if="audit" title="操作" :width="200" fixed="right">
        <template #default="{ record }">
          <Space size="small">
            <a @click="openReview(record, 'CONFIRMED')">确认</a>
            <a @click="openReview(record, 'IGNORED')">忽略</a>
            <a @click="openReview(record, 'FIXED')">已整改</a>
          </Space>
        </template>
      </a-table-column>
      <template #emptyText>
        <Empty :description="loading ? '加载中' : '暂无问题项'" />
      </template>
    </Table>

    <Modal
      v-model:open="reviewOpen"
      :title="reviewTitle"
      :confirm-loading="reviewSaving"
      ok-text="提交复核"
      @ok="submitReview"
    >
      <div class="br-review__issue">
        <span class="br-review__label">问题：</span>{{ reviewTarget?.issueTitle }}
      </div>
      <div class="br-review__issue">
        <span class="br-review__label">原文证据：</span>
        {{ reviewTarget?.evidenceText || '标书没有对应内容' }}
      </div>
      <div class="br-review__issue">
        <span class="br-review__label">口径提醒：</span>
        「忽略」表示判定为误报，该问题不再计入结论（高危被忽略后任务结论会自动重算）。
      </div>
      <Textarea
        v-model:value="reviewForm.reviewRemark"
        :rows="3"
        :placeholder="reviewForm.status === 'IGNORED' ? '请填写判定为误报的理由（必填）' : '复核意见（可选）'"
      />
    </Modal>
  </div>
</template>

<style scoped>
.br-issue-table__sub {
  font-size: 12px;
  color: #8a94a6;
}

.br-issue-table__flag {
  margin-top: 4px;
}

.br-review__issue {
  margin-bottom: 8px;
  font-size: 13px;
  line-height: 1.7;
  color: #434343;
}

.br-review__label {
  color: #8a94a6;
}
</style>
