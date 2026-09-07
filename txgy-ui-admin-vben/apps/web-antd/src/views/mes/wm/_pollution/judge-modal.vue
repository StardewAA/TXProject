<script lang="ts" setup>
import type { MesSetPollutionCheckApi } from '#/api/mes/safetyEnv/pollutionCheck';

import { useVbenModal } from '@vben/common-ui';

import { Button, Input, Modal as AModal, Popconfirm, Radio, Table, Tag, message } from 'ant-design-vue';
import { computed, ref } from 'vue';

import {
  createPollutionCheck,
  deletePollutionCheck,
  getPollutionCheckPage,
  reviewPollutionCheck,
} from '#/api/mes/safetyEnv/pollutionCheck';

import { AI_RESULT_MAP, REVIEW_RESULT_MAP, STAGE_MAP } from '../../safetyEnv/pollutionCheck/data';

/** 单据真实物料行（由调用方从单据行查询归一） */
interface DocLine {
  lineId?: number;
  itemCode?: string;
  itemName?: string;
  itemSpec?: string;
  batchNo?: string;
}

/** 打开时入参 */
interface JudgePayload {
  title?: string;
  stage: string;
  bizNo: string;
  lines: DocLine[];
}

/** 视图行：单据物料行 + 已绑定的污染判定记录(可能无) */
interface RowItem extends DocLine {
  rec?: MesSetPollutionCheckApi.PollutionCheck;
}

const emit = defineEmits<{ success: [] }>();

/** 弹窗内状态 */
const bizNo = ref('');
const stage = ref('');
const docTitle = ref('');
const lines = ref<DocLine[]>([]);
const records = ref<MesSetPollutionCheckApi.PollutionCheck[]>([]);
const loading = ref(false);

/** 复核弹窗状态 */
const reviewOpen = ref(false);
const reviewRecord = ref<MesSetPollutionCheckApi.PollutionCheck | null>(null);
const reviewResult = ref<string>();
const reviewLocation = ref('');
const reviewRemark = ref('');
const reviewSaving = ref(false);

/** 记录按 物料编码+批次 索引（同单同料多次只取最新） */
const recordMap = computed(() => {
  const map = new Map<string, MesSetPollutionCheckApi.PollutionCheck>();
  for (const rec of records.value) {
    const key = joinKey(rec.itemCode, rec.batchNo);
    if (!map.has(key)) {
      map.set(key, rec);
    }
  }
  return map;
});

const rows = computed<RowItem[]>(() =>
  lines.value.map((line) => ({ ...line, rec: recordMap.value.get(joinKey(line.itemCode, line.batchNo)) })),
);

const stageLabel = computed(() => STAGE_MAP[stage.value] ?? stage.value);
const modalTitle = computed(() => `${docTitle.value} · 环保判定`);
const summaryText = computed(() => {
  const total = rows.value.length;
  const reviewed = rows.value.filter((r) => r.rec?.reviewResult).length;
  const polluted = rows.value.filter((r) => r.rec?.reviewResult === 'POLLUTED').length;
  return total === 0 ? '该单暂无物料行，请先在单据中添加行' : `共 ${total} 个物料行 · 已复核 ${reviewed} · 其中有污染 ${polluted}`;
});

function joinKey(itemCode?: string, batchNo?: string) {
  return `${itemCode ?? ''}|${batchNo ?? ''}`;
}

/** 拉取本单已有判定 */
async function refresh() {
  if (!bizNo.value) {
    return;
  }
  loading.value = true;
  try {
    const result = await getPollutionCheckPage({ pageNo: 1, pageSize: 200, bizNo: bizNo.value });
    records.value = result.list ?? [];
  } finally {
    loading.value = false;
  }
}

/** 对某物料行发起判定：服务端自动 AI 初筛 → 待复核 */
async function handleJudge(line: DocLine) {
  const hide = message.loading(`正在对「${line.itemName}」AI 初筛…`, 0);
  try {
    await createPollutionCheck({
      stage: stage.value,
      bizNo: bizNo.value,
      batchNo: line.batchNo,
      itemCode: line.itemCode,
      itemName: line.itemName,
      itemSpec: line.itemSpec,
    });
    message.success('已生成判定记录（AI 初筛完成，待人工复核）');
    await refresh();
    const rec = recordMap.value.get(joinKey(line.itemCode, line.batchNo));
    if (rec) {
      openReview(rec);
    }
    emit('success');
  } finally {
    hide();
  }
}

/** 打开复核 */
function openReview(rec: MesSetPollutionCheckApi.PollutionCheck) {
  reviewRecord.value = rec;
  reviewResult.value = undefined;
  reviewLocation.value = rec.location ?? '';
  reviewRemark.value = '';
  reviewOpen.value = true;
}

/** 提交复核（终态二值；存储/处置留空由后端按环节×结论补默认） */
async function handleReviewOk() {
  if (!reviewRecord.value) {
    return;
  }
  if (!reviewResult.value) {
    message.warning('请选择人工复核结论（无污染 / 有污染）');
    return;
  }
  reviewSaving.value = true;
  try {
    await reviewPollutionCheck({
      id: reviewRecord.value.id!,
      reviewResult: reviewResult.value,
      location: reviewLocation.value || undefined,
      remark: reviewRemark.value || undefined,
    });
    message.success('复核完成');
    reviewOpen.value = false;
    await refresh();
    emit('success');
  } finally {
    reviewSaving.value = false;
  }
}

/** 删除待复核记录 */
async function handleDelete(rec: MesSetPollutionCheckApi.PollutionCheck) {
  await deletePollutionCheck(rec.id!);
  message.success('已删除该判定记录');
  await refresh();
  emit('success');
}

const [JudgeModal, modalApi] = useVbenModal({
  destroyOnClose: true,
  async onOpenChange(isOpen: boolean) {
    if (!isOpen) {
      return;
    }
    const data = modalApi.getData<JudgePayload>();
    docTitle.value = data.title ?? '';
    stage.value = data.stage;
    bizNo.value = data.bizNo;
    lines.value = data.lines ?? [];
    reviewOpen.value = false;
    await refresh();
  },
});

/** antd 表格列（内容用 #bodyCell 渲染） */
const columns = [
  { key: 'itemCode', title: '物料编码', dataIndex: 'itemCode', width: 140 },
  { key: 'itemName', title: '物料/产品名称', dataIndex: 'itemName', width: 180 },
  { key: 'itemSpec', title: '规格', dataIndex: 'itemSpec', width: 160, ellipsis: true },
  { key: 'batchNo', title: '批次', dataIndex: 'batchNo', width: 160 },
  { key: 'status', title: '环保判定状态', width: 260 },
  { key: 'action', title: '操作', width: 200 },
];
</script>

<template>
  <JudgeModal :title="modalTitle" class="w-[960px]">
    <div class="mx-1">
      <div class="mb-3 text-sm text-gray-500">
        <span>环节：{{ stageLabel }}（{{ bizNo }}）</span>
        <span class="mx-2">｜</span>
        <span>{{ summaryText }}</span>
      </div>
      <Table
        :columns="columns"
        :data-source="rows"
        :loading="loading"
        :pagination="false"
        :row-key="(_row: RowItem, index: number) => `${joinKey(_row.itemCode, _row.batchNo)}-${index}`"
        size="middle"
      >
        <template #bodyCell="{ column, record }">
          <!-- 判定状态 -->
          <template v-if="column.key === 'status'">
            <template v-if="!record.rec">
              <Tag>未判定</Tag>
            </template>
            <template v-else>
              <div class="flex flex-wrap items-center gap-1">
                <Tag
                  :color="AI_RESULT_MAP[record.rec.aiResult ?? '']?.color"
                  :title="`AI 依据：${record.rec.aiReason ?? '-'}`"
                >
                  AI·{{ AI_RESULT_MAP[record.rec.aiResult ?? '']?.text ?? record.rec.aiResult }}
                  {{ record.rec.aiConfidence != null ? `${record.rec.aiConfidence}%` : '' }}
                </Tag>
                <template v-if="record.rec.reviewResult">
                  <Tag :color="REVIEW_RESULT_MAP[record.rec.reviewResult]?.color">
                    人工·{{ REVIEW_RESULT_MAP[record.rec.reviewResult]?.text ?? record.rec.reviewResult }}
                  </Tag>
                  <Tag v-if="record.rec.marked" color="volcano">标记</Tag>
                </template>
                <template v-else>
                  <Tag color="warning">待复核</Tag>
                </template>
                <span class="text-xs text-gray-400" :title="record.rec.aiReason">
                  {{ record.rec.aiReason }}
                </span>
              </div>
            </template>
          </template>

          <!-- 操作 -->
          <template v-else-if="column.key === 'action'">
            <Button v-if="!record.rec" type="link" size="small" @click="handleJudge(record)">
              AI 初筛判定
            </Button>
            <template v-else-if="!record.rec.reviewResult">
              <Button type="link" size="small" @click="openReview(record.rec)">
                复核
              </Button>
              <Popconfirm
                title="确认删除该待复核判定记录？"
                ok-text="删除"
                cancel-text="取消"
                @confirm="handleDelete(record.rec)"
              >
                <Button type="link" size="small" danger>删除</Button>
              </Popconfirm>
            </template>
            <span v-else class="text-xs text-gray-400">
              已复核
              <span v-if="record.rec.location"> · {{ record.rec.location }}</span>
            </span>
          </template>
        </template>
        <template #emptyText>
          <span class="text-gray-400">暂无物料行</span>
        </template>
      </Table>
    </div>
  </JudgeModal>

  <!-- 人工复核 -->
  <AModal
    v-model:open="reviewOpen"
    :title="`人工复核 · ${reviewRecord?.itemName ?? ''}（${reviewRecord?.bizNo ?? ''}）`"
    :confirm-loading="reviewSaving"
    ok-text="提交复核"
    cancel-text="取消"
    @ok="handleReviewOk"
  >
    <template v-if="reviewRecord">
      <div class="mb-3 rounded-md bg-gray-50 p-3 text-sm">
        <div>记录：{{ reviewRecord.recordNo }}</div>
        <div>
          AI 初筛：
          <span :style="{ color: AI_RESULT_MAP[reviewRecord.aiResult ?? '']?.color }">
            {{ AI_RESULT_MAP[reviewRecord.aiResult ?? '']?.text ?? reviewRecord.aiResult }}
          </span>
          （置信度 {{ reviewRecord.aiConfidence ?? '-' }}%）
        </div>
        <div v-if="reviewRecord.aiReason">依据：{{ reviewRecord.aiReason }}</div>
        <div v-if="reviewRecord.suggestedStorage">AI 推荐存储：{{ reviewRecord.suggestedStorage }}</div>
      </div>
      <div class="space-y-3">
        <div>
          <div class="mb-1 text-sm">人工复核结论（终态：无污染 / 有污染）</div>
          <Radio.Group v-model:value="reviewResult">
            <Radio.Button value="CLEAN">无污染</Radio.Button>
            <Radio.Button value="POLLUTED">有污染</Radio.Button>
          </Radio.Group>
        </div>
        <div>
          <div class="mb-1 text-sm">去向/库位（留空按环节×结论取默认存储）</div>
          <Input v-model:value="reviewLocation" placeholder="如：危化品库-污染管控区" allow-clear />
        </div>
        <div>
          <div class="mb-1 text-sm">复核备注</div>
          <Input.TextArea v-model:value="reviewRemark" :rows="2" placeholder="可补充说明" />
        </div>
      </div>
    </template>
  </AModal>
</template>
