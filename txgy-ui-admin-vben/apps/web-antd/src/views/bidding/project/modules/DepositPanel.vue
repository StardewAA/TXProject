<!--
  投标保证金台账（抽屉）—— 项目台账 6771 的子功能入口（按钮权限点 bidding:deposit:save/query/refund）
  契约：后端变更通知-投标保证金-2026-09-28（BidDepositController，前缀 /bidding/deposit）
  展示口径（对齐仓库既有表格规范）：
    · 状态/缴纳方式/退还状态走字典（MARGIN_PAY_TYPE / DEPOSIT_STATUS / refund_status），缺字典回显原码；
    · 时间（depositTime/refundTime/createTime）为 epoch 毫秒 → 过 fmtDateTime；
    · 金额（amount）为分/元 decimal → 过 fmtMoney（千分位，空显示 —）；
    · 超期标记 refundOverdue 黄色 Tag（合同签订后 5 日内未退还，条例58条）。
-->
<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';

import { message, Modal } from 'ant-design-vue';

import { useAccess } from '@vben/access';
import { useAccessStore } from '@vben/stores';

import {
  type BidDepositApi,
  getBidDepositPage,
  refundBidDeposit,
  saveBidDeposit,
} from '#/api/bidding/deposit';

import { fmtDateTime } from '../../certificate/format';
import { useCertMeta } from '../../certificate/useCertMeta';

const props = withDefaults(
  defineProps<{
    open: boolean;
    /** 行内带入：招标项目编号（可为空，空则查全量台账） */
    tenderProjectCode?: string;
    bidSectionCode?: string;
  }>(),
  { tenderProjectCode: undefined, bidSectionCode: undefined },
);
const emit = defineEmits<{ (e: 'update:open', v: boolean): void }>();

const accessStore = useAccessStore();
const { dictLabel, dictOptions } = useCertMeta();

const { hasAccessByCodes } = useAccess();

/** 权限判定：权限码未下发时放行（最终由后端 @PreAuthorize 裁决），避免界面被锁死 */
function can(code: string): boolean {
  const codes = accessStore.accessCodes ?? [];
  if (codes.length === 0) return true;
  return codes.includes('*') || codes.includes('*:*:*') || hasAccessByCodes([code]);
}

const rows = ref<BidDepositApi.Record[]>([]);
const total = ref(0);
const loading = ref(false);
const query = reactive<BidDepositApi.PageParams>({ pageNo: 1, pageSize: 10 });

const dict = (t: string, c?: null | string): string => (c ? dictLabel(t, c) || c : '—');
const hasDict = (t: string): boolean => dictOptions(t).length > 0;

/** 金额千分位（元 → 展示；空/非法显示 —） */
function fmtMoney(v?: number | string): string {
  if (v === undefined || v === null || v === '') return '—';
  const n = Number(v);
  if (!Number.isFinite(n)) return String(v);
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

async function load() {
  loading.value = true;
  try {
    const page = await getBidDepositPage({
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

/* ===== 登记 ===== */
const saveOpen = ref(false);
const saving = ref(false);
const saveForm = reactive({
  bidSectionCode: '',
  amount: undefined as undefined | number,
  currencyCode: '156',
  payType: '',
  status: '',
  fileId: undefined as undefined | number,
  remark: '',
});
function openSave(): void {
  saveForm.bidSectionCode = props.bidSectionCode ?? '';
  saveForm.amount = undefined;
  saveForm.currencyCode = '156';
  saveForm.payType = '';
  saveForm.status = '';
  saveForm.fileId = undefined;
  saveForm.remark = '';
  saveOpen.value = true;
}
async function submitSave(): Promise<void> {
  if (!saveForm.bidSectionCode.trim()) {
    message.warning('请填写标段编号');
    return;
  }
  if (saveForm.amount === undefined || !(saveForm.amount > 0)) {
    message.warning('请填写保证金金额');
    return;
  }
  saving.value = true;
  try {
    await saveBidDeposit({
      bidSectionCode: saveForm.bidSectionCode.trim(),
      amount: saveForm.amount,
      currencyCode: saveForm.currencyCode.trim() || '156',
      payType: saveForm.payType || undefined,
      status: saveForm.status || undefined,
      fileId: saveForm.fileId,
      remark: saveForm.remark.trim() || undefined,
    });
    message.success('保证金已登记（同一标段同一主体重复登记返回既有记录）');
    saveOpen.value = false;
    void load();
  } catch (error: unknown) {
    message.warning((error as { message?: string }).message || String(error));
  } finally {
    saving.value = false;
  }
}

/* ===== 退还 ===== */
function confirmRefund(r: BidDepositApi.Record): void {
  Modal.confirm({
    title: '退还投标保证金',
    content: `确认退还「${r.bidSectionCode}」的保证金 ${fmtMoney(r.amount)} ？已退还记录不可重复退还。`,
    okText: '退还',
    cancelText: '取消',
    async onOk() {
      try {
        await refundBidDeposit({ depositId: r.id, remark: '管理端手动退还' });
        message.success('已退还');
        void load();
      } catch (error: unknown) {
        message.warning((error as { message?: string }).message || String(error));
        throw error;
      }
    },
  });
}

const columns = [
  { title: '标段编号', dataIndex: 'bidSectionCode', key: 'bidSectionCode', width: 170, ellipsis: true },
  { title: '招标项目', dataIndex: 'tenderProjectCode', key: 'tenderProjectCode', width: 170, ellipsis: true },
  { title: '金额（元）', dataIndex: 'amount', key: 'amount', width: 130 },
  { title: '缴纳方式', dataIndex: 'payType', key: 'payType', width: 110 },
  { title: '状态', dataIndex: 'status', key: 'status', width: 100 },
  { title: '缴纳时间', dataIndex: 'depositTime', key: 'depositTime', width: 160 },
  { title: '退还时间', dataIndex: 'refundTime', key: 'refundTime', width: 160 },
  { title: '退还状态', dataIndex: 'refundStatus', key: 'refundStatus', width: 110 },
  { title: '超期标记', dataIndex: 'refundOverdue', key: 'refundOverdue', width: 100 },
  { title: '备注', dataIndex: 'remark', key: 'remark', ellipsis: true },
  { title: '操作', key: 'action', width: 90, fixed: 'right' },
];

const scopeText = computed(() =>
  props.tenderProjectCode
    ? `项目 ${props.tenderProjectCode}${props.bidSectionCode ? ' · 标段 ' + props.bidSectionCode : ''}`
    : '全部项目',
);
</script>

<template>
  <a-drawer
    :open="open"
    :title="'投标保证金台账 · ' + scopeText"
    :width="1080"
    destroy-on-close
    @close="emit('update:open', false)"
  >
    <div class="ct-page">
      <div class="ct-head">
        <div>
          <div class="ct-title">投标保证金</div>
          <div class="ct-sub">
            登记时校验「金额 ≤ 标段估算价 × 2%」（条例第26条）；合同签订后 5 日内退还（条例第58条），超期黄色标记。
          </div>
        </div>
        <div class="ct-actions">
          <a-button v-if="can('bidding:deposit:save')" type="primary" @click="openSave">登记保证金</a-button>
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
          :scroll="{ x: 1180 }"
          :row-key="(r: any) => r?.id"
          size="middle"
          @change="(p: any) => { query.pageNo = p.current; query.pageSize = p.pageSize; void load(); }"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'amount'">{{ fmtMoney(record.amount) }}</template>
            <template v-else-if="column.key === 'payType'">
              {{ hasDict('MARGIN_PAY_TYPE') ? dict('MARGIN_PAY_TYPE', record.payType) : dict('', record.payType) }}
            </template>
            <template v-else-if="column.key === 'status'">
              {{ hasDict('DEPOSIT_STATUS') ? dict('DEPOSIT_STATUS', record.status) : dict('', record.status) }}
            </template>
            <template v-else-if="column.key === 'depositTime'">{{ record.depositTime ? fmtDateTime(record.depositTime) : '—' }}</template>
            <template v-else-if="column.key === 'refundTime'">{{ record.refundTime ? fmtDateTime(record.refundTime) : '—' }}</template>
            <template v-else-if="column.key === 'refundStatus'">
              {{ hasDict('DEPOSIT_REFUND_STATUS') ? dict('DEPOSIT_REFUND_STATUS', record.refundStatus) : dict('', record.refundStatus) }}
            </template>
            <template v-else-if="column.key === 'refundOverdue'">
              <a-tag v-if="record.refundOverdue" color="orange">已超 5 日</a-tag>
              <span v-else>—</span>
            </template>
            <template v-else-if="column.key === 'action'">
              <a v-if="can('bidding:deposit:refund')" @click="confirmRefund(record)">退还</a>
            </template>
          </template>
          <template #emptyText><div class="ct-empty">暂无保证金记录</div></template>
        </a-table>
      </div>
    </div>

    <!-- 登记保证金 -->
    <a-modal
      v-model:open="saveOpen"
      title="登记投标保证金"
      :width="560"
      :confirm-loading="saving"
      ok-text="登记"
      cancel-text="取消"
      destroy-on-close
      @ok="submitSave"
    >
      <a-form layout="vertical">
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="标段编号（必填）">
              <a-input v-model:value="saveForm.bidSectionCode" placeholder="BID_SECTION_CODE" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="金额（元，必填）">
              <a-input-number v-model:value="saveForm.amount" :min="0" :precision="2" style="width: 100%" placeholder="≤ 标段估算价 × 2%" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="币种">
              <a-input v-model:value="saveForm.currencyCode" placeholder="默认 156 人民币" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="缴纳方式">
              <a-select v-model:value="saveForm.payType" allow-clear placeholder="MARGIN_PAY_TYPE 字典">
                <a-select-option v-for="o in dictOptions('MARGIN_PAY_TYPE')" :key="o.value" :value="o.value">{{ o.label }}</a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :span="24">
            <a-form-item label="备注">
              <a-textarea v-model:value="saveForm.remark" :rows="2" />
            </a-form-item>
          </a-col>
        </a-row>
      </a-form>
      <div class="ct-tip">幂等：同一标段同一主体重复登记返回既有记录；超额（> 估算价 2%）后端拒绝（1060116001）。</div>
    </a-modal>
  </a-drawer>
</template>
