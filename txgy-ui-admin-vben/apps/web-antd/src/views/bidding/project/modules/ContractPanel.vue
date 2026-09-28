<!--
  工程合同台账（抽屉）—— 项目台账 6771 的子功能入口（按钮权限点 bidding:contract:save/query）
  契约：后端变更通知-合同签署-2026-09-28（ContractController，前缀 /bidding/contract）
  展示口径（对齐仓库既有表格规范）：
    · 状态走字典（contract_status），缺字典回显原码；
    · 时间（winNoticeTime/signTime/createTime）为 epoch 毫秒 → 过 fmtDateTime；
    · 金额（contractPrice/performanceBond）过 fmtMoney（千分位）。
  校验（后端强校验，前端提示文案）：履约保证金 ≤ 合同价 × 10%（条例57条，1060103002）；
    签约 ≤ 中标通知书发出 30 日（招投标法46条，1060103003）。
-->
<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';

import { message } from 'ant-design-vue';

import { useAccess } from '@vben/access';
import { useAccessStore } from '@vben/stores';

import {
  type BiddingContractApi,
  getBiddingContractPage,
  saveBiddingContract,
} from '#/api/bidding/contract';

import { fmtDateTime } from '../../certificate/format';
import { useCertMeta } from '../../certificate/useCertMeta';

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
const { dictLabel, dictOptions } = useCertMeta();

function can(code: string): boolean {
  const codes = accessStore.accessCodes ?? [];
  if (codes.length === 0) return true;
  return codes.includes('*') || codes.includes('*:*:*') || hasAccessByCodes([code]);
}

const rows = ref<BiddingContractApi.Record[]>([]);
const total = ref(0);
const loading = ref(false);
const query = reactive<BiddingContractApi.PageParams>({ pageNo: 1, pageSize: 10 });

const dict = (t: string, c?: null | string): string => (c ? dictLabel(t, c) || c : '—');
const hasDict = (t: string): boolean => dictOptions(t).length > 0;

function fmtMoney(v?: number | string): string {
  if (v === undefined || v === null || v === '') return '—';
  const n = Number(v);
  if (!Number.isFinite(n)) return String(v);
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

async function load() {
  loading.value = true;
  try {
    const page = await getBiddingContractPage({
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
  tenderProjectCode: '',
  bidSectionCode: '',
  contractCode: '',
  contractName: '',
  contractPrice: undefined as undefined | number,
  performanceBond: undefined as undefined | number,
  winNoticeTime: undefined as undefined | string,
  signTime: undefined as undefined | string,
  fileId: undefined as undefined | number,
  status: '',
  remark: '',
});
function openSave(): void {
  saveForm.tenderProjectCode = props.tenderProjectCode ?? '';
  saveForm.bidSectionCode = props.bidSectionCode ?? '';
  saveForm.contractCode = '';
  saveForm.contractName = '';
  saveForm.contractPrice = undefined;
  saveForm.performanceBond = undefined;
  saveForm.winNoticeTime = undefined;
  saveForm.signTime = undefined;
  saveForm.fileId = undefined;
  saveForm.status = '';
  saveForm.remark = '';
  saveOpen.value = true;
}
async function submitSave(): Promise<void> {
  if (!saveForm.tenderProjectCode.trim() || !saveForm.contractCode.trim() || !saveForm.contractName.trim()) {
    message.warning('请填写招标项目编号、合同编号与合同名称');
    return;
  }
  if (saveForm.contractPrice === undefined || !(saveForm.contractPrice >= 0)) {
    message.warning('请填写合同价');
    return;
  }
  saving.value = true;
  try {
    await saveBiddingContract({
      tenderProjectCode: saveForm.tenderProjectCode.trim(),
      bidSectionCode: saveForm.bidSectionCode.trim() || undefined,
      contractCode: saveForm.contractCode.trim(),
      contractName: saveForm.contractName.trim(),
      contractPrice: saveForm.contractPrice,
      performanceBond: saveForm.performanceBond,
      winNoticeTime: saveForm.winNoticeTime,
      signTime: saveForm.signTime,
      fileId: saveForm.fileId,
      status: saveForm.status || undefined,
      remark: saveForm.remark.trim() || undefined,
    });
    message.success('合同已登记');
    saveOpen.value = false;
    void load();
  } catch (error: unknown) {
    message.warning((error as { message?: string }).message || String(error));
  } finally {
    saving.value = false;
  }
}

const columns = [
  { title: '合同编号', dataIndex: 'contractCode', key: 'contractCode', width: 170, ellipsis: true },
  { title: '合同名称', dataIndex: 'contractName', key: 'contractName', width: 200, ellipsis: true },
  { title: '招标项目', dataIndex: 'tenderProjectCode', key: 'tenderProjectCode', width: 170, ellipsis: true },
  { title: '标段', dataIndex: 'bidSectionCode', key: 'bidSectionCode', width: 150, ellipsis: true },
  { title: '合同价（元）', dataIndex: 'contractPrice', key: 'contractPrice', width: 140 },
  { title: '履约保证金（元）', dataIndex: 'performanceBond', key: 'performanceBond', width: 150 },
  { title: '中标通知时间', dataIndex: 'winNoticeTime', key: 'winNoticeTime', width: 160 },
  { title: '签订时间', dataIndex: 'signTime', key: 'signTime', width: 160 },
  { title: '状态', dataIndex: 'status', key: 'status', width: 100 },
  { title: '备注', dataIndex: 'remark', key: 'remark', ellipsis: true },
];

const scopeText = computed(() =>
  props.tenderProjectCode
    ? '项目 ' + props.tenderProjectCode + (props.bidSectionCode ? ' · 标段 ' + props.bidSectionCode : '')
    : '全部项目',
);
</script>

<template>
  <a-drawer
    :open="open"
    :title="'工程合同台账 · ' + scopeText"
    :width="1180"
    destroy-on-close
    @close="emit('update:open', false)"
  >
    <div class="ct-page">
      <div class="ct-head">
        <div>
          <div class="ct-title">工程合同</div>
          <div class="ct-sub">
            登记时校验「履约保证金 ≤ 合同价 × 10%」（条例第57条）与「签约 ≤ 中标通知书发出 30 日」（招投标法第46条）。
          </div>
        </div>
        <div class="ct-actions">
          <a-button v-if="can('bidding:contract:save')" type="primary" @click="openSave">登记合同</a-button>
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
          :scroll="{ x: 1500 }"
          :row-key="(r: any) => r?.id"
          size="middle"
          @change="(p: any) => { query.pageNo = p.current; query.pageSize = p.pageSize; void load(); }"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'contractPrice'">{{ fmtMoney(record.contractPrice) }}</template>
            <template v-else-if="column.key === 'performanceBond'">{{ fmtMoney(record.performanceBond) }}</template>
            <template v-else-if="column.key === 'winNoticeTime'">{{ record.winNoticeTime ? fmtDateTime(record.winNoticeTime) : '—' }}</template>
            <template v-else-if="column.key === 'signTime'">{{ record.signTime ? fmtDateTime(record.signTime) : '—' }}</template>
            <template v-else-if="column.key === 'status'">
              {{ hasDict('contract_status') ? dict('contract_status', record.status) : dict('', record.status) }}
            </template>
          </template>
          <template #emptyText><div class="ct-empty">暂无合同记录</div></template>
        </a-table>
      </div>
    </div>

    <!-- 登记合同 -->
    <a-modal
      v-model:open="saveOpen"
      title="登记工程合同"
      :width="720"
      :confirm-loading="saving"
      ok-text="登记"
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
              <a-input v-model:value="saveForm.bidSectionCode" placeholder="BID_SECTION_CODE（可空）" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="合同编号（必填）">
              <a-input v-model:value="saveForm.contractCode" placeholder="合同编号" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="合同名称（必填）">
              <a-input v-model:value="saveForm.contractName" placeholder="合同名称" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="合同价（元，必填）">
              <a-input-number v-model:value="saveForm.contractPrice" :min="0" :precision="2" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="履约保证金（元）">
              <a-input-number v-model:value="saveForm.performanceBond" :min="0" :precision="2" style="width: 100%" placeholder="≤ 合同价 × 10%" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="中标通知书发出时间">
              <a-date-picker v-model:value="saveForm.winNoticeTime" value-format="x" style="width: 100%" placeholder="选时间" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="签订时间">
              <a-date-picker v-model:value="saveForm.signTime" value-format="x" style="width: 100%" placeholder="选时间" />
            </a-form-item>
          </a-col>
          <a-col :span="24">
            <a-form-item label="备注">
              <a-textarea v-model:value="saveForm.remark" :rows="2" />
            </a-form-item>
          </a-col>
        </a-row>
      </a-form>
      <div class="ct-tip">后端强校验：履约保证金 &gt; 合同价 10% 抛 1060103002；签订时间 − 中标通知时间 &gt; 30 日抛 1060103003（缺失字段容错跳过）。</div>
    </a-modal>
  </a-drawer>
</template>
