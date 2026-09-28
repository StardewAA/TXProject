<script lang="ts" setup>
/**
 * 管理端 · 标书审查 - 报告与统计（菜单 60007300）
 *
 * 方案依据：《标书审查-管理端重新设计》§4.3.4。改造点：
 *   · 去掉改造前那份"重复的全量任务表 + 第二份报告抽屉"，改为「指标 + 分布 + 报告台账」；
 *   · 指标修正三处口径：AI 失败率空值显示「—」（原来渲染成 0.0%，看起来像"一切正常"）、
 *     把后端已返回却一直没用的"AI 调用(24h)"展示出来、"完成率"改名"成功占比"（分母含排队中任务）；
 *   · 报告台账走 GET /report/page（报告 + 所属任务的扁平行），补上改造前没有的「导出 HTML」。
 */
import { computed, onMounted, reactive, ref } from 'vue';

import {
  Button,
  Card,
  Empty,
  Form,
  FormItem,
  Input,
  message,
  Progress,
  Select,
  Space,
  Statistic,
  Table,
  Tag,
} from 'ant-design-vue';

import {
  type BidReviewApi,
  downloadPlatformFile,
  exportReviewReportHtml,
  getReviewOverview,
  getReviewReportDoc,
  getReviewReportPage,
} from '#/api/bidding/bidReview';
import { fmtDateTime } from '#/utils/datetime';

import BidReviewDictTag from '../components/BidReviewDictTag.vue';
import BidReviewReportDrawer from '../components/BidReviewReportDrawer.vue';
import { useBiddingDict } from '../composables/useBiddingDict';
import '../panel.css';

const { dictLabel, dictOptions } = useBiddingDict();

/* ===================== 指标 ===================== */
const overview = ref<BidReviewApi.Overview | null>(null);

/** 成功占比 = 成功 / 全部（含排队中/执行中），因此不叫"完成率" */
const successRate = computed(() => {
  const d = overview.value;
  if (!d?.totalCount) return '—';
  return `${(((d.successCount ?? 0) / d.totalCount) * 100).toFixed(1)}%`;
});

/** AI 失败率：无数据时显示「—」，绝不显示 0.0%（否则会被读成"AI 一切正常"） */
const aiFailureText = computed(() => {
  const rate = overview.value?.aiFailureRate;
  return rate == null ? '—' : `${(rate * 100).toFixed(1)}%`;
});

const verdictText = computed(() => {
  const d = overview.value;
  const total = d?.totalCount ?? 0;
  const map = new Map((d?.verdictStats ?? []).map((i) => [i.name, i.count ?? 0]));
  return { total, map };
});

const severityText = computed(() => {
  const map = new Map((overview.value?.severityStats ?? []).map((i) => [i.name, i.count ?? 0]));
  return map;
});

async function loadOverview() {
  try {
    overview.value = await getReviewOverview();
  } catch {
    overview.value = null;
  }
}

/* ===================== 报告台账 ===================== */
const loading = ref(false);
const rows = ref<BidReviewApi.ReportLedgerRow[]>([]);
const total = ref(0);
const query = reactive<BidReviewApi.ReportPageReq>({
  pageNo: 1,
  pageSize: 10,
  reportNo: undefined,
  verdict: undefined,
});

const reportOpen = ref(false);
const reportTaskId = ref<null | number>(null);
const reportTaskName = ref('');
const docLoadingId = ref<null | number>(null);
const exportingId = ref<null | number>(null);

async function loadReports() {
  loading.value = true;
  try {
    const page = await getReviewReportPage(query);
    rows.value = page.list ?? [];
    total.value = Number(page.total ?? 0);
  } finally {
    loading.value = false;
  }
}

function search() {
  query.pageNo = 1;
  void loadReports();
}

function resetQuery() {
  query.reportNo = undefined;
  query.verdict = undefined;
  search();
}

function viewReport(record: BidReviewApi.ReportLedgerRow) {
  reportTaskId.value = record.taskId;
  reportTaskName.value = record.taskName ?? record.taskNo ?? '';
  reportOpen.value = true;
}

/** 直接下载 Word：先向后端索取（同一任务只生成一次），再走同源 <a download> 落盘 */
async function downloadWord(record: BidReviewApi.ReportLedgerRow) {
  docLoadingId.value = record.id;
  try {
    const doc = await getReviewReportDoc(record.taskId);
    if (!downloadPlatformFile(doc?.url, doc?.fileName)) {
      message.warning('报告 Word 尚未生成，请稍后重试');
    }
  } catch (error: any) {
    message.warning(error?.message ?? '报告文档生成失败');
  } finally {
    docLoadingId.value = null;
  }
}

/** 导出 HTML：接口返回正文 HTML，前端包成 Blob 下载（不跳需要鉴权的地址页） */
async function exportHtml(record: BidReviewApi.ReportLedgerRow) {
  exportingId.value = record.id;
  try {
    const html = (await exportReviewReportHtml(record.taskId)) ?? '';
    const blob = new Blob([html], { type: 'text/html;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `${record.reportNo}.html`;
    link.style.display = 'none';
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(url);
  } catch (error: any) {
    message.warning(error?.message ?? '导出失败');
  } finally {
    exportingId.value = null;
  }
}

function refreshAll() {
  void loadOverview();
  void loadReports();
}

onMounted(() => {
  void loadOverview();
  void loadReports();
});
</script>

<template>
  <div class="br-page">
    <!-- 指标区 -->
    <div class="br-stats">
      <a-row :gutter="12">
        <a-col :span="3">
          <Statistic title="任务总数" :value="overview?.totalCount ?? 0" />
        </a-col>
        <a-col :span="3">
          <Statistic title="执行中" :value="overview?.runningCount ?? 0" />
        </a-col>
        <a-col :span="3">
          <Statistic title="已完成" :value="overview?.successCount ?? 0" />
        </a-col>
        <a-col :span="3">
          <Statistic title="失败" :value="overview?.failedCount ?? 0" />
        </a-col>
        <a-col :span="3">
          <Statistic title="成功占比" :value="successRate" />
        </a-col>
        <a-col :span="3">
          <Statistic title="AI 调用(24h)" :value="overview?.aiCallsLast24h ?? 0" />
        </a-col>
        <a-col :span="3">
          <Statistic title="AI 失败率(24h)" :value="aiFailureText" />
        </a-col>
        <a-col :span="3" class="br-stats__action">
          <Button size="small" @click="refreshAll">刷新统计</Button>
        </a-col>
      </a-row>
    </div>

    <!-- 分布区 -->
    <a-row :gutter="12">
      <a-col :span="8">
        <Card size="small" title="结论分布（是否废标）">
          <div v-for="item in overview?.verdictStats ?? []" :key="item.name" class="br-line">
            <span class="br-line__label">
              {{ dictLabel('bid_review_verdict', item.name) }}
              <template v-if="item.name === 'FAIL'">（废标）</template>
            </span>
            <Progress
              :percent="verdictText.total ? Math.round(((item.count ?? 0) / verdictText.total) * 100) : 0"
              size="small"
              :format="() => String(item.count ?? 0)"
            />
          </div>
        </Card>
      </a-col>
      <a-col :span="8">
        <Card size="small" title="问题级别分布">
          <div v-for="name in ['HIGH', 'MEDIUM', 'LOW']" :key="name" class="br-line">
            <span class="br-line__label">
              {{ dictLabel('bid_review_severity', name) }}
              <template v-if="name === 'HIGH'">（废标项）</template>
            </span>
            <Tag :color="name === 'HIGH' ? 'red' : name === 'MEDIUM' ? 'orange' : 'default'">
              {{ severityText.get(name) ?? 0 }}
            </Tag>
          </div>
        </Card>
      </a-col>
      <a-col :span="8">
        <Card size="small" title="高频问题 TOP10（按规则名称）">
          <Empty v-if="!overview?.topRules?.length" description="暂无数据" />
          <!-- 按规则名称聚合（后端 resolveRuleNames）；名称可能重名，key 用名称+序号 -->
          <div
            v-for="(item, index) in overview?.topRules ?? []"
            :key="`${item.name}-${index}`"
            class="br-line"
          >
            <span class="br-line__label">{{ item.name }}</span>
            <Tag>{{ item.count }}</Tag>
          </div>
        </Card>
      </a-col>
    </a-row>

    <!-- 报告台账 -->
    <Card size="small" title="报告台账" style="margin-top: 12px">
      <template #extra>
        <span class="br-sub">共 {{ total }} 份报告</span>
      </template>

      <Form layout="inline" class="br-query">
        <FormItem label="报告编号">
          <Input v-model:value="query.reportNo" allow-clear placeholder="BRR…" style="width: 180px" @press-enter="search" />
        </FormItem>
        <FormItem label="结论">
          <Select
            v-model:value="query.verdict"
            allow-clear
            placeholder="全部"
            style="width: 140px"
            :options="dictOptions('bid_review_verdict')"
          />
        </FormItem>
        <FormItem>
          <Space>
            <Button type="primary" @click="search">查询</Button>
            <Button @click="resetQuery">重置</Button>
          </Space>
        </FormItem>
      </Form>

      <Table
        size="small"
        row-key="id"
        :loading="loading"
        :data-source="rows"
        :scroll="{ x: 1200 }"
        :pagination="{
          current: query.pageNo,
          pageSize: query.pageSize,
          total,
          showSizeChanger: true,
          showTotal: (t: number) => `共 ${t} 条`,
          onChange: (p: number, s: number) => ((query.pageNo = p), (query.pageSize = s), loadReports()),
        }"
      >
        <a-table-column title="报告编号" :width="180">
          <template #default="{ record }">
            <div>{{ record.reportNo }}</div>
            <div class="br-sub">{{ fmtDateTime(record.createTime) }}</div>
          </template>
        </a-table-column>
        <a-table-column title="任务编号" :width="180">
          <template #default="{ record }">
            <div>{{ record.taskNo ?? `#${record.taskId}` }}</div>
            <div class="br-sub">{{ record.taskName }}</div>
          </template>
        </a-table-column>
        <a-table-column title="结论" :width="110">
          <template #default="{ record }">
            <BidReviewDictTag dict-type="bid_review_verdict" :value="record.verdict" />
          </template>
        </a-table-column>
        <a-table-column title="是否废标" :width="100">
          <template #default="{ record }">
            <Tag :color="record.rejected ? 'red' : 'green'">{{ record.rejected ? '是' : '否' }}</Tag>
          </template>
        </a-table-column>
        <a-table-column title="自评得分" :width="100">
          <template #default="{ record }">{{ record.scoreTotal ?? '—' }}</template>
        </a-table-column>
        <a-table-column title="生成方式" :width="110">
          <template #default="{ record }">
            <Tag :color="record.aiGenerated ? 'purple' : 'default'">
              {{ record.aiGenerated ? 'AI 生成' : '规则匹配' }}
            </Tag>
          </template>
        </a-table-column>
        <a-table-column title="Word 文档" :width="110">
          <template #default="{ record }">
            <Tag :color="record.docxReady ? 'green' : 'default'">
              {{ record.docxReady ? '已生成' : '待生成' }}
            </Tag>
          </template>
        </a-table-column>
        <a-table-column title="操作" :width="240" fixed="right">
          <template #default="{ record }">
            <Space size="small">
              <a @click="viewReport(record)">查看</a>
              <a :class="{ 'br-disabled': docLoadingId === record.id }" @click="downloadWord(record)">
                {{ docLoadingId === record.id ? '生成中…' : '下载 Word' }}
              </a>
              <a :class="{ 'br-disabled': exportingId === record.id }" @click="exportHtml(record)">
                {{ exportingId === record.id ? '导出中…' : '导出 HTML' }}
              </a>
            </Space>
          </template>
        </a-table-column>
      </Table>
    </Card>

    <BidReviewReportDrawer
      v-model:open="reportOpen"
      :task-id="reportTaskId"
      :task-name="reportTaskName"
    />
  </div>
</template>

<style scoped>
.br-stats__action {
  display: flex;
  align-items: center;
  justify-content: center;
}

.br-disabled {
  color: #bfbfbf;
  cursor: not-allowed;
}
</style>
