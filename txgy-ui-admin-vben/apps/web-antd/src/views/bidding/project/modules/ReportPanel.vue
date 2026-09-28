<!--
  定标书面报告台账（抽屉）—— 项目台账 6771 的子功能入口（按钮权限点 bidding:report:submit/query）
  契约：后端变更通知-定标书面报告-2026-09-28（ReportController，前缀 /bidding/report）
  展示口径：
    · awardTime = 定标时间（中标结果公告发布时间）；submitTime = 提交时间（epoch 毫秒 → fmtDateTime）；
    · reportOverdue（未提交且定标后超 15 日）黄色 Tag（招投标法47条）。
-->
<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';

import { message } from 'ant-design-vue';

import { useAccess } from '@vben/access';
import { useAccessStore } from '@vben/stores';

import {
  type BiddingReportApi,
  getBiddingReportPage,
  submitBiddingReport,
} from '#/api/bidding/report';

import { fmtDateTime } from '../../certificate/format';

const props = withDefaults(
  defineProps<{
    open: boolean;
    tenderProjectCode?: string;
    bidSectionCode?: string;
  }>(),
  { tenderProjectCode: undefined, bidSectionCode: undefined },
);
const emit = defineEmits<{ (e: 'update:open', v: boolean): void }>();

const accessStore = useAccessStore();
const { hasAccessByCodes } = useAccess();

function can(code: string): boolean {
  const codes = accessStore.accessCodes ?? [];
  if (codes.length === 0) return true;
  return codes.includes('*') || codes.includes('*:*:*') || hasAccessByCodes([code]);
}

const rows = ref<BiddingReportApi.Record[]>([]);
const total = ref(0);
const loading = ref(false);
const query = reactive<BiddingReportApi.PageParams>({ pageNo: 1, pageSize: 10 });

async function load() {
  loading.value = true;
  try {
    const page = await getBiddingReportPage({
      ...query,
      tenderProjectCode: props.tenderProjectCode,
      bidSectionCode: props.bidSectionCode,
    });
    rows.value = page.list ?? [];
    total.value = Number(page.total ?? 0);
  } catch {
    rows.value = [];
    total.value = 0;
  } finally {
    loading.value = false;
  }
}

onMounted(load);

/* ===== 提交 ===== */
const saveOpen = ref(false);
const saving = ref(false);
const saveForm = reactive({
  tenderProjectCode: '',
  bidSectionCode: '',
  reportCode: '',
  reportName: '',
  fileId: undefined as undefined | number,
  remark: '',
});
function openSave(): void {
  saveForm.tenderProjectCode = props.tenderProjectCode ?? '';
  saveForm.bidSectionCode = props.bidSectionCode ?? '';
  saveForm.reportCode = '';
  saveForm.reportName = '';
  saveForm.fileId = undefined;
  saveForm.remark = '';
  saveOpen.value = true;
}
async function submitSave(): Promise<void> {
  if (!saveForm.tenderProjectCode.trim() || !saveForm.reportCode.trim() || !saveForm.reportName.trim()) {
    message.warning('请填写招标项目编号、报告编号与报告名称');
    return;
  }
  saving.value = true;
  try {
    await submitBiddingReport({
      tenderProjectCode: saveForm.tenderProjectCode.trim(),
      bidSectionCode: saveForm.bidSectionCode.trim() || undefined,
      reportCode: saveForm.reportCode.trim(),
      reportName: saveForm.reportName.trim(),
      fileId: saveForm.fileId,
      remark: saveForm.remark.trim() || undefined,
    });
    message.success('报告已提交');
    saveOpen.value = false;
    void load();
  } catch (error: unknown) {
    message.warning((error as { message?: string }).message || String(error));
  } finally {
    saving.value = false;
  }
}

const columns = [
  { title: '报告编号', dataIndex: 'reportCode', key: 'reportCode', width: 170, ellipsis: true },
  { title: '报告名称', dataIndex: 'reportName', key: 'reportName', width: 200, ellipsis: true },
  { title: '招标项目', dataIndex: 'tenderProjectCode', key: 'tenderProjectCode', width: 170, ellipsis: true },
  { title: '标段', dataIndex: 'bidSectionCode', key: 'bidSectionCode', width: 150, ellipsis: true },
  { title: '定标时间', dataIndex: 'awardTime', key: 'awardTime', width: 160 },
  { title: '提交时间', dataIndex: 'submitTime', key: 'submitTime', width: 160 },
  { title: '超期未提交', dataIndex: 'reportOverdue', key: 'reportOverdue', width: 110 },
  { title: '状态', dataIndex: 'status', key: 'status', width: 100 },
  { title: '备注', dataIndex: 'remark', key: 'remark', ellipsis: true },
];
</script>

<template>
  <a-drawer
    :open="open"
    :title="'定标书面报告台账' + (props.tenderProjectCode ? ' · ' + props.tenderProjectCode : '')"
    :width="1100"
    destroy-on-close
    @close="emit('update:open', false)"
  >
    <div class="ct-page">
      <div class="ct-head">
        <div>
          <div class="ct-title">定标书面报告</div>
          <div class="ct-sub">
            确定中标人之日起 15 日内提交（招投标法47条）；定标时间取中标结果公告发布时间，超期未提交黄色标记。
          </div>
        </div>
        <div class="ct-actions">
          <a-button v-if="can('bidding:report:submit')" type="primary" @click="openSave">提交报告</a-button>
          <a-button @click="load">刷新</a-button>
        </div>
      </div>

      <div class="ct-panel-b">
        <a-table
          class="ct-table"
          :columns="columns"
          :data-source="rows"
          :loading="loading"
          :pagination="{
            current: query.pageNo,
            pageSize: query.pageSize,
            total,
            showSizeChanger: true,
            showTotal: (t: number) => '共 ' + t + ' 条',
          }"
          :scroll="{ x: 1250 }"
          :row-key="(r: any) => r?.id"
          size="middle"
          @change="(p: any) => { query.pageNo = p.current; query.pageSize = p.pageSize; void load(); }"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'awardTime'">{{ record.awardTime ? fmtDateTime(record.awardTime) : '—' }}</template>
            <template v-else-if="column.key === 'submitTime'">{{ record.submitTime ? fmtDateTime(record.submitTime) : '—' }}</template>
            <template v-else-if="column.key === 'reportOverdue'">
              <a-tag v-if="record.reportOverdue" color="orange">超 15 日未提交</a-tag>
              <span v-else>—</span>
            </template>
          </template>
          <template #emptyText><div class="ct-empty">暂无报告记录</div></template>
        </a-table>
      </div>
    </div>

    <!-- 提交报告 -->
    <a-modal
      v-model:open="saveOpen"
      title="提交定标书面报告"
      :width="640"
      :confirm-loading="saving"
      ok-text="提交"
      cancel-text="取消"
      destroy-on-close
      @ok="submitSave"
    >
      <a-form layout="vertical">
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="招标项目编号（必填）">
              <a-input v-model:value="saveForm.tenderProjectCode" placeholder="TENDER_PROJECT_CODE" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="标段编号">
              <a-input v-model:value="saveForm.bidSectionCode" placeholder="可空" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="报告编号（必填）">
              <a-input v-model:value="saveForm.reportCode" placeholder="报告编号" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="报告名称（必填）">
              <a-input v-model:value="saveForm.reportName" placeholder="报告名称" />
            </a-form-item>
          </a-col>
          <a-col :span="24">
            <a-form-item label="备注">
              <a-textarea v-model:value="saveForm.remark" :rows="2" />
            </a-form-item>
          </a-col>
        </a-row>
      </a-form>
      <div class="ct-tip">超期提交后端仅记 warn（不阻断，报告必须补交）；记录不存在抛 1060117000。</div>
    </a-modal>
  </a-drawer>
</template>
