<script lang="ts" setup>
/**
 * 管理端 · 标书审查 - 审查任务台账（菜单 60007100）
 *
 * 方案依据：《标书审查-管理端重新设计》§4.3.1。页面职责边界：
 *   · 只做「任务」这一件事：有哪些审查、跑到哪了、要不要重跑、能不能替人建单；
 *   · 概览统计归「报告与统计」页（改造前两页各查一次 /task/overview，数字会打架），
 *     这里只保留一行基于**当前筛选**的条数摘要；
 *   · 报告走公共组件 BidReviewReportDrawer（操作列「报告」入口），本页不再维护第二份实现；
 *   · 任务详情只展示「基本信息 + 结论」，问题清单与复核在「问题复核」页统一处理（2026-09-18 用户口径）。
 */
import { computed, onMounted, reactive, ref } from 'vue';

import { useAccess } from '@vben/access';
import { useAccessStore } from '@vben/stores';

import {
  Alert,
  Button,
  Card,
  DatePicker,
  Descriptions,
  DescriptionsItem,
  Drawer,
  Form,
  FormItem,
  Input,
  InputNumber,
  message,
  Modal,
  Select,
  Space,
  Spin,
  Switch,
  Table,
  Tag,
  Textarea,
  Upload,
} from 'ant-design-vue';

import {
  type BidReviewApi,
  cancelReviewTask,
  createReviewTask,
  getReviewTask,
  getReviewTaskPage,
  getRuleSetPage,
  retryReviewTask,
} from '#/api/bidding/bidReview';
import { uploadBiddingFile } from '#/api/bidding/file';
import { fmtDateTime } from '#/utils/datetime';

import BidReviewDictTag from '../components/BidReviewDictTag.vue';
import BidReviewReportDrawer from '../components/BidReviewReportDrawer.vue';
import { useBiddingDict } from '../composables/useBiddingDict';
import '../panel.css';

const { dictLabel, dictOptions } = useBiddingDict();

/** 权限判定：权限码未下发时放行（最终由后端 @PreAuthorize 裁决），与招投标其他页面同口径。 */
const { hasAccessByCodes } = useAccess();
const accessStore = useAccessStore();
function can(code: string): boolean {
  const codes = accessStore.accessCodes ?? [];
  if (codes.length === 0) return true;
  return codes.includes('*') || codes.includes('*:*:*') || hasAccessByCodes([code]);
}

/* ===================== 列表 ===================== */
const loading = ref(false);
const rows = ref<BidReviewApi.Task[]>([]);
const total = ref(0);

const query = reactive<BidReviewApi.TaskPageReq>({
  pageNo: 1,
  pageSize: 10,
  taskNo: undefined,
  taskName: undefined,
  sourceScene: undefined,
  status: undefined,
  verdict: undefined,
});
/** 日期区间单独持有：TaskPageReq 用的是 beginTime/endTime 两个字段 */
const createRange = ref<[string, string] | undefined>();

const summary = computed(() => `共 ${total.value} 个任务（按当前筛选）`);

async function loadTasks() {
  loading.value = true;
  try {
    const [beginTime, endTime] = createRange.value ?? [];
    const page = await getReviewTaskPage({ ...query, beginTime, endTime });
    rows.value = page.list ?? [];
    total.value = Number(page.total ?? 0);
  } finally {
    loading.value = false;
  }
}

function search() {
  query.pageNo = 1;
  void loadTasks();
}

function resetQuery() {
  query.taskNo = undefined;
  query.taskName = undefined;
  query.sourceScene = undefined;
  query.status = undefined;
  query.verdict = undefined;
  createRange.value = undefined;
  search();
}

/* ===================== 任务详情 ===================== */
const detailOpen = ref(false);
const detailLoading = ref(false);
const detail = ref<BidReviewApi.Task | null>(null);
const reportOpen = ref(false);
const reportTaskId = ref<null | number>(null);
const reportTaskName = ref('');

async function openDetail(record: BidReviewApi.Task) {
  detailOpen.value = true;
  detailLoading.value = true;
  detail.value = record;
  try {
    // 列表行不含 parseMode/scoreItems/rejectReasons 等字段，详情一律重新取全量
    detail.value = await getReviewTask(record.id);
  } finally {
    detailLoading.value = false;
  }
}

function openReport(record: BidReviewApi.Task) {
  reportTaskId.value = record.id;
  reportTaskName.value = record.taskName;
  reportOpen.value = true;
}

async function handleRetry(record: BidReviewApi.Task) {
  await retryReviewTask(record.id);
  message.success('已重新排队执行');
  await loadTasks();
}

async function handleCancel(record: BidReviewApi.Task) {
  await cancelReviewTask(record.id);
  message.success('已取消');
  await loadTasks();
}

/* ===================== 管理端建单 ===================== */
const createOpen = ref(false);
const creating = ref(false);
const uploading = ref<'bid' | 'tender' | ''>('');
const ruleSetOptions = ref<Array<{ label: string; value: number }>>([]);

const createForm = reactive<
  BidReviewApi.TaskCreateReq & { bidFileName?: string; tenderFileName?: string }
>({
  taskName: '',
  ownerUserId: undefined,
  ruleSetId: undefined,
  bidFileId: undefined as unknown as number,
  bidFileName: '',
  tenderFileId: undefined,
  tenderFileName: '',
  remark: '',
  generate: true,
});

function resetCreateForm() {
  createForm.taskName = '';
  createForm.ownerUserId = undefined;
  createForm.ruleSetId = undefined;
  createForm.bidFileId = undefined as unknown as number;
  createForm.bidFileUrl = undefined;
  createForm.bidFileName = '';
  createForm.tenderFileId = undefined;
  createForm.tenderFileUrl = undefined;
  createForm.tenderFileName = '';
  createForm.projectId = undefined;
  createForm.tenderProjectId = undefined;
  createForm.remark = '';
  createForm.generate = true;
}

async function openCreate() {
  resetCreateForm();
  createOpen.value = true;
  if (ruleSetOptions.value.length === 0) {
    try {
      const page = await getRuleSetPage({ pageNo: 1, pageSize: 50, enable: true });
      ruleSetOptions.value = (page.list ?? []).map((set) => ({
        label: `${set.ruleSetName}${set.defaultFlag ? '（平台默认）' : ''}`,
        value: set.id,
      }));
    } catch {
      ruleSetOptions.value = [];
    }
  }
}

async function pickFile(file: File, type: 'bid' | 'tender') {
  uploading.value = type;
  try {
    const resp = await uploadBiddingFile(file, 'bidding/bid-review');
    if (!resp?.fileId) {
      message.error('上传成功但未取得 file_id，请重试');
      return false;
    }
    if (type === 'bid') {
      createForm.bidFileId = resp.fileId;
      createForm.bidFileUrl = resp.url;
      createForm.bidFileName = resp.name ?? file.name;
      if (!createForm.taskName) {
        createForm.taskName = (resp.name ?? file.name).replace(/\.[^.]+$/, '').slice(0, 60);
      }
    } else {
      createForm.tenderFileId = resp.fileId;
      createForm.tenderFileUrl = resp.url;
      createForm.tenderFileName = resp.name ?? file.name;
    }
  } catch (error: any) {
    message.error(error?.message ?? '上传失败');
  } finally {
    uploading.value = '';
  }
  return false;
}

async function submitCreate() {
  if (!createForm.taskName?.trim()) {
    message.warning('请填写任务名称');
    return;
  }
  if (!createForm.bidFileId) {
    message.warning('请上传投标文件（标书）');
    return;
  }
  creating.value = true;
  try {
    await createReviewTask({ ...createForm });
    message.success(createForm.generate === false ? '已建单（未执行）' : '已建单并开始执行');
    createOpen.value = false;
    await loadTasks();
  } finally {
    creating.value = false;
  }
}

onMounted(loadTasks);
</script>

<template>
  <div class="br-page">
    <Card size="small" title="审查任务">
      <template #extra>
        <Space>
          <span class="br-sub">{{ summary }}</span>
          <Button size="small" @click="loadTasks">刷新</Button>
          <Button
            v-if="can('bidding:bid-review:create')"
            size="small"
            type="primary"
            @click="openCreate"
          >
            新建审查任务
          </Button>
        </Space>
      </template>

      <Form layout="inline" class="br-query">
        <FormItem label="任务编号">
          <Input v-model:value="query.taskNo" allow-clear placeholder="BR2026…" style="width: 170px" @press-enter="search" />
        </FormItem>
        <FormItem label="任务名称">
          <Input v-model:value="query.taskName" allow-clear placeholder="模糊匹配" style="width: 180px" @press-enter="search" />
        </FormItem>
        <FormItem label="来源">
          <Select
            v-model:value="query.sourceScene"
            allow-clear
            placeholder="全部"
            style="width: 140px"
            :options="dictOptions('bid_review_scene')"
          />
        </FormItem>
        <FormItem label="状态">
          <Select
            v-model:value="query.status"
            allow-clear
            placeholder="全部"
            style="width: 130px"
            :options="dictOptions('bid_review_status')"
          />
        </FormItem>
        <FormItem label="结论">
          <Select
            v-model:value="query.verdict"
            allow-clear
            placeholder="全部"
            style="width: 130px"
            :options="dictOptions('bid_review_verdict')"
          />
        </FormItem>
        <FormItem label="创建时间">
          <DatePicker.RangePicker
            v-model:value="createRange"
            style="width: 240px"
            value-format="YYYY-MM-DD HH:mm:ss"
            :show-time="{ format: 'HH:mm' }"
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
        :scroll="{ x: 1300 }"
        :pagination="{
          current: query.pageNo,
          pageSize: query.pageSize,
          total,
          showSizeChanger: true,
          showTotal: (t: number) => `共 ${t} 条`,
          onChange: (p: number, s: number) => ((query.pageNo = p), (query.pageSize = s), loadTasks()),
        }"
      >
        <a-table-column title="任务编号" :width="170">
          <template #default="{ record }">
            <div>{{ record.taskNo }}</div>
            <div class="br-sub">{{ fmtDateTime(record.createTime) }}</div>
          </template>
        </a-table-column>
        <a-table-column title="任务名称" :width="220">
          <template #default="{ record }">
            <div>{{ record.taskName }}</div>
            <div class="br-sub">{{ record.bidFileName }}</div>
          </template>
        </a-table-column>
        <a-table-column title="来源" :width="110">
          <template #default="{ record }">
            <BidReviewDictTag dict-type="bid_review_scene" :value="record.sourceScene" />
          </template>
        </a-table-column>
        <a-table-column title="提交人" :width="110">
          <template #default="{ record }">
            {{ record.ownerName || (record.ownerUserId ? `用户#${record.ownerUserId}` : '—') }}
          </template>
        </a-table-column>
        <a-table-column title="状态" :width="130">
          <template #default="{ record }">
            <BidReviewDictTag dict-type="bid_review_status" :value="record.status" />
            <div class="br-sub">
              {{ record.progress ?? 0 }}%
              <template v-if="record.currentStep"> · {{ dictLabel('bid_review_step', record.currentStep) }}</template>
            </div>
          </template>
        </a-table-column>
        <a-table-column title="是否废标" :width="100">
          <template #default="{ record }">
            <Tag v-if="record.verdict" :color="record.rejected ? 'red' : 'green'">
              {{ record.rejected ? '是' : '否' }}
            </Tag>
            <span v-else>—</span>
          </template>
        </a-table-column>
        <a-table-column title="结论" :width="100">
          <template #default="{ record }">
            <BidReviewDictTag dict-type="bid_review_verdict" :value="record.verdict" />
          </template>
        </a-table-column>
        <a-table-column title="问题（高危/总）" :width="120">
          <template #default="{ record }">
            <span :class="{ 'br-danger': (record.highIssueCnt ?? 0) > 0 }">{{ record.highIssueCnt ?? 0 }}</span>
            / {{ record.issueCnt ?? 0 }}
          </template>
        </a-table-column>
        <a-table-column title="耗时" :width="90">
          <template #default="{ record }">
            {{ record.costMs ? `${(record.costMs / 1000).toFixed(1)}s` : '—' }}
          </template>
        </a-table-column>
        <a-table-column title="操作" :width="220" fixed="right">
          <template #default="{ record }">
            <Space size="small">
              <a @click="openDetail(record)">详情</a>
              <a v-if="record.reportReady" @click="openReport(record)">报告</a>
              <a
                v-if="can('bidding:bid-review:update') && ['FAILED', 'SUCCESS'].includes(record.status)"
                @click="handleRetry(record)"
              >
                重跑
              </a>
              <a
                v-if="can('bidding:bid-review:update') && ['QUEUED', 'RUNNING'].includes(record.status)"
                class="br-danger"
                @click="handleCancel(record)"
              >
                取消
              </a>
            </Space>
          </template>
        </a-table-column>
      </Table>
    </Card>

    <!-- 任务详情：基本信息 + 结论卡（问题清单/复核已移出，见「问题复核」页） -->
    <Drawer v-model:open="detailOpen" :title="`任务详情 - ${detail?.taskNo ?? ''}`" width="1100">
      <Spin :spinning="detailLoading">
        <template v-if="detail">
          <Descriptions :column="3" size="small" bordered style="margin-bottom: 12px">
            <DescriptionsItem label="任务名称">{{ detail.taskName }}</DescriptionsItem>
            <DescriptionsItem label="来源">
              <BidReviewDictTag dict-type="bid_review_scene" :value="detail.sourceScene" />
            </DescriptionsItem>
            <DescriptionsItem label="提交人">
              {{ detail.ownerName || (detail.ownerUserId ? `用户#${detail.ownerUserId}` : '—') }}
            </DescriptionsItem>
            <DescriptionsItem label="状态">
              <BidReviewDictTag dict-type="bid_review_status" :value="detail.status" />
            </DescriptionsItem>
            <DescriptionsItem label="当前步骤">
              {{ detail.currentStep ? dictLabel('bid_review_step', detail.currentStep) : '—' }}
            </DescriptionsItem>
            <DescriptionsItem label="进度">{{ detail.progress ?? 0 }}%</DescriptionsItem>
            <DescriptionsItem label="规则集">{{ detail.ruleSetName || '—' }}</DescriptionsItem>
            <DescriptionsItem label="规则条数">{{ detail.ruleCnt ?? 0 }}</DescriptionsItem>
            <DescriptionsItem label="解析方式">
              {{ detail.parseMode === 'EMPTY' ? '无文字层（扫描件）' : detail.parseMode === 'TEXT' ? '文本层' : '—' }}
            </DescriptionsItem>
            <DescriptionsItem label="AI 是否调用">
              <Tag :color="detail.aiCalled ? 'purple' : 'default'">{{ detail.aiCalled ? '已调用' : '未调用' }}</Tag>
            </DescriptionsItem>
            <DescriptionsItem label="重试次数">{{ detail.retryCount ?? 0 }}</DescriptionsItem>
            <DescriptionsItem label="耗时">
              {{ detail.costMs ? `${(detail.costMs / 1000).toFixed(1)}s` : '—' }}
            </DescriptionsItem>
            <DescriptionsItem label="创建时间">{{ fmtDateTime(detail.createTime) }}</DescriptionsItem>
            <DescriptionsItem label="开始时间">{{ fmtDateTime(detail.startedTime) }}</DescriptionsItem>
            <DescriptionsItem label="完成时间">{{ fmtDateTime(detail.finishedTime) }}</DescriptionsItem>
            <DescriptionsItem label="标书文件">{{ detail.bidFileName || '—' }}</DescriptionsItem>
            <DescriptionsItem label="招标文件">{{ detail.tenderFileName || '未提供（仅按平台规则库自查）' }}</DescriptionsItem>
            <DescriptionsItem label="备注">{{ detail.remark || '—' }}</DescriptionsItem>
          </Descriptions>

          <div class="br-conclusion">
            <div class="br-conclusion__row">
              <span class="br-conclusion__label">是否废标</span>
              <Tag v-if="detail.verdict" :color="detail.rejected ? 'red' : 'green'">
                {{ detail.rejected ? '是（命中高危废标项）' : '否' }}
              </Tag>
              <span v-else class="br-sub">任务未出结论</span>
              <span class="br-conclusion__label" style="margin-left: 24px">结论</span>
              <BidReviewDictTag dict-type="bid_review_verdict" :value="detail.verdict" />
              <span class="br-conclusion__label" style="margin-left: 24px">自评得分</span>
              <span>{{ detail.scoreTotal ?? '—' }}</span>
            </div>
            <div class="br-conclusion__row">
              <span class="br-conclusion__label">废标原因</span>
              <ul v-if="detail.rejectReasons && detail.rejectReasons.length > 0" class="br-conclusion__list">
                <li v-for="reason in detail.rejectReasons" :key="reason">{{ reason }}</li>
              </ul>
              <span v-else class="br-sub">未命中废标项</span>
            </div>
            <div v-if="detail.scoreItems && detail.scoreItems.length > 0" class="br-conclusion__row">
              <span class="br-conclusion__label">分项得分</span>
              <Space wrap>
                <Tag v-for="item in detail.scoreItems" :key="item.category">
                  {{ dictLabel('bid_review_rule_category', item.category) }}：{{ item.score }}
                  <template v-if="item.scoreMax"> / {{ item.scoreMax }}</template>
                </Tag>
              </Space>
            </div>
            <Alert
              v-if="detail.errorMsg"
              type="error"
              show-icon
              style="margin-top: 8px"
              :message="`失败原因：${detail.errorMsg}`"
              :description="detail.errorCode ? `错误码：${detail.errorCode}` : undefined"
            />
          </div>
        </template>
      </Spin>
    </Drawer>

    <!-- 报告抽屉：任务页与报告页共用同一实现 -->
    <BidReviewReportDrawer
      v-model:open="reportOpen"
      :task-id="reportTaskId"
      :task-name="reportTaskName"
    />

    <!-- 管理端建单 -->
    <Modal
      v-model:open="createOpen"
      title="新建审查任务"
      width="720"
      :confirm-loading="creating"
      ok-text="建单"
      @ok="submitCreate"
    >
      <Form layout="vertical">
        <FormItem label="任务名称" required>
          <Input v-model:value="createForm.taskName" placeholder="默认取标书文件名" />
        </FormItem>
        <div class="br-form-row">
          <FormItem label="归属人（留空 = 建给当前账号）">
            <InputNumber
              v-model:value="createForm.ownerUserId"
              :min="1"
              placeholder="用户 id"
              style="width: 220px"
            />
          </FormItem>
          <FormItem label="规则集（留空 = 平台默认）">
            <Select
              v-model:value="createForm.ruleSetId"
              allow-clear
              placeholder="平台默认规则集"
              style="width: 300px"
              :options="ruleSetOptions"
            />
          </FormItem>
        </div>
        <FormItem label="投标文件（标书）" required>
          <Space>
            <Upload :show-upload-list="false" :before-upload="(file: File) => pickFile(file, 'bid')">
              <Button :loading="uploading === 'bid'">选择文件</Button>
            </Upload>
            <span class="br-sub">{{ createForm.bidFileName || '未选择' }}</span>
          </Space>
        </FormItem>
        <FormItem label="招标文件（可选，传了按本项目逐条比对）">
          <Space>
            <Upload :show-upload-list="false" :before-upload="(file: File) => pickFile(file, 'tender')">
              <Button :loading="uploading === 'tender'">选择文件</Button>
            </Upload>
            <span class="br-sub">{{ createForm.tenderFileName || '未选择' }}</span>
          </Space>
        </FormItem>
        <div class="br-form-row">
          <FormItem label="关联项目 id（可选）">
            <InputNumber v-model:value="createForm.projectId" :min="1" style="width: 180px" />
          </FormItem>
          <FormItem label="招标项目 id（可选）">
            <InputNumber v-model:value="createForm.tenderProjectId" :min="1" style="width: 180px" />
          </FormItem>
          <FormItem label="保存后立即执行">
            <Switch v-model:checked="createForm.generate" />
          </FormItem>
        </div>
        <FormItem label="备注">
          <Textarea v-model:value="createForm.remark" :rows="2" placeholder="补建原因等（可选）" />
        </FormItem>
      </Form>
    </Modal>
  </div>
</template>

<style scoped>
.br-danger {
  color: #ff4d4f;
}

.br-conclusion {
  padding: 12px;
  margin-bottom: 12px;
  background: #fafbfd;
  border: 1px solid #eef2f7;
  border-radius: 4px;
}

.br-conclusion__row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  margin-bottom: 6px;
  font-size: 13px;
}

.br-conclusion__label {
  min-width: 72px;
  color: #8a94a6;
}

.br-conclusion__list {
  margin: 0;
  padding-left: 18px;
  color: #cf1322;
}

.br-form-row {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
}
</style>
