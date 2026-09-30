<!--
  投标异议台账（抽屉）—— 项目台账 6771 的子功能入口（按钮权限点 bidding:objection:propose/reply/query）
  契约：后端变更通知-异议-2026-09-28（ObjectionController，前缀 /bidding/objection）
  展示口径：
    · objectionType 走字典（objection_type：PREQUAL_FILE 资格预审文件 / TENDER_FILE 招标文件），缺字典回显原码；
    · proposeTime/replyTime/createTime 为 epoch 毫秒 → fmtDateTime；
    · replyOverdue（待答复且提出后超 3 日）黄色 Tag（条例22条 3 日内答复）。
  操作：提出异议（投标方）/ 答复异议（招标方，幂等——已答复不可重复答复 1060118002）。
-->
<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';

import { message } from 'ant-design-vue';

import { useAccess } from '@vben/access';
import { useAccessStore } from '@vben/stores';

import {
  type BiddingObjectionApi,
  getBiddingObjectionPage,
  proposeBiddingObjection,
  replyBiddingObjection,
} from '#/api/bidding/objection';

import { fmtDateTime } from '../../certificate/format';
import { useCertMeta } from '../../certificate/useCertMeta';
import { crawlErrMsg } from '../../crawlExternal/shared';

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

const rows = ref<BiddingObjectionApi.Record[]>([]);
const total = ref(0);
const loading = ref(false);
const loadError = ref('');

function objectionErrorMessage(error: unknown): string {
  const raw = crawlErrMsg(error);
  const status = (error as { response?: { status?: number }; status?: number })?.response?.status
    ?? (error as { status?: number })?.status;
  if (status === 404 || /不存在|not found|404/i.test(raw)) {
    return '投标异议接口暂不可用：当前 bidding-server 或网关可能仍是旧版本，请重新构建并部署 bidding 服务。';
  }
  if (status === 403 || /无权限|禁止访问|forbidden|403/i.test(raw)) {
    return '当前账号没有投标异议查询权限，请联系管理员授权 bidding:objection:query。';
  }
  return raw || '投标异议台账加载失败，请检查 bidding 服务连接。';
}
const query = reactive<BiddingObjectionApi.PageParams>({ pageNo: 1, pageSize: 10 });

const dict = (t: string, c?: null | string): string => (c ? dictLabel(t, c) || c : '—');
const hasDict = (t: string): boolean => dictOptions(t).length > 0;

async function load() {
  loading.value = true;
  try {
    loadError.value = '';
    const page = await getBiddingObjectionPage({
      ...query,
      tenderProjectCode: props.tenderProjectCode,
      bidSectionCode: props.bidSectionCode,
    });
    rows.value = page.list ?? [];
    total.value = Number(page.total ?? 0);
  } catch (error: unknown) {
    rows.value = [];
    total.value = 0;
    loadError.value = objectionErrorMessage(error);
  } finally {
    loading.value = false;
  }
}

onMounted(load);

/* ===== 提出异议 ===== */
const proposeOpen = ref(false);
const proposing = ref(false);
const proposeForm = reactive({
  tenderProjectCode: '',
  bidSectionCode: '',
  objectionType: '',
  subjectId: undefined as undefined | string,
  content: '',
  remark: '',
});
function openPropose(): void {
  proposeForm.tenderProjectCode = props.tenderProjectCode ?? '';
  proposeForm.bidSectionCode = props.bidSectionCode ?? '';
  proposeForm.objectionType = '';
  proposeForm.subjectId = undefined;
  proposeForm.content = '';
  proposeForm.remark = '';
  proposeOpen.value = true;
}
async function submitPropose(): Promise<void> {
  if (!proposeForm.tenderProjectCode.trim() || !proposeForm.objectionType || !proposeForm.content.trim()) {
    message.warning('请填写招标项目编号、异议类型与异议内容');
    return;
  }
  proposing.value = true;
  try {
    await proposeBiddingObjection({
      tenderProjectCode: proposeForm.tenderProjectCode.trim(),
      bidSectionCode: proposeForm.bidSectionCode.trim() || undefined,
      objectionType: proposeForm.objectionType,
      subjectId: proposeForm.subjectId,
      content: proposeForm.content.trim(),
      remark: proposeForm.remark.trim() || undefined,
    });
    message.success('异议已提出');
    proposeOpen.value = false;
    void load();
  } catch (error: unknown) {
    message.warning((error as { message?: string }).message || String(error));
  } finally {
    proposing.value = false;
  }
}

/* ===== 答复异议 ===== */
const replyOpen = ref(false);
const replying = ref(false);
const replyTarget = ref<BiddingObjectionApi.Record>();
const replyContent = ref('');
function openReply(r: BiddingObjectionApi.Record): void {
  replyTarget.value = r;
  replyContent.value = '';
  replyOpen.value = true;
}
async function submitReply(): Promise<void> {
  if (!replyTarget.value || !replyContent.value.trim()) {
    message.warning('请填写答复内容');
    return;
  }
  replying.value = true;
  try {
    await replyBiddingObjection({
      objectionId: replyTarget.value.id,
      replyContent: replyContent.value.trim(),
    });
    message.success('已答复');
    replyOpen.value = false;
    void load();
  } catch (error: unknown) {
    message.warning((error as { message?: string }).message || String(error));
  } finally {
    replying.value = false;
  }
}

const columns = [
  { title: '异议内容', dataIndex: 'content', key: 'content', width: 220, ellipsis: true },
  { title: '类型', dataIndex: 'objectionType', key: 'objectionType', width: 110 },
  { title: '招标项目', dataIndex: 'tenderProjectCode', key: 'tenderProjectCode', width: 170, ellipsis: true },
  { title: '标段', dataIndex: 'bidSectionCode', key: 'bidSectionCode', width: 150, ellipsis: true },
  { title: '提出时间', dataIndex: 'proposeTime', key: 'proposeTime', width: 160 },
  { title: '状态', dataIndex: 'status', key: 'status', width: 100 },
  { title: '答复时间', dataIndex: 'replyTime', key: 'replyTime', width: 160 },
  { title: '答复内容', dataIndex: 'replyContent', key: 'replyContent', width: 200, ellipsis: true },
  { title: '答复超期', dataIndex: 'replyOverdue', key: 'replyOverdue', width: 100 },
  { title: '操作', key: 'action', width: 90, fixed: 'right' },
];
</script>

<template>
  <a-drawer
    :open="open"
    :title="'投标异议台账' + (props.tenderProjectCode ? ' · ' + props.tenderProjectCode : '')"
    :width="1250"
    destroy-on-close
    @close="emit('update:open', false)"
  >
    <div class="ct-page">
      <div class="ct-head">
        <div>
          <div class="ct-title">投标异议</div>
          <div class="ct-sub">
            提出时限：资格预审文件申请截止 2 日前 / 招标文件投标截止 10 日前（条例22条）；收到后 3 日内答复，超期黄色标记。
          </div>
        </div>
        <div class="ct-actions">
          <a-button v-if="can('bidding:objection:propose')" type="primary" @click="openPropose">提出异议</a-button>
          <a-button @click="load">刷新</a-button>
        </div>
      </div>

      <div class="ct-panel-b">
        <a-alert v-if="loadError" type="error" show-icon :message="loadError" class="mb-3" />
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
          :scroll="{ x: 1300 }"
          :row-key="(r: any) => r?.id"
          size="middle"
          @change="(p: any) => { query.pageNo = p.current; query.pageSize = p.pageSize; void load(); }"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'objectionType'">
              {{ hasDict('objection_type') ? dict('objection_type', record.objectionType) : dict('', record.objectionType) }}
            </template>
            <template v-else-if="column.key === 'proposeTime'">{{ record.proposeTime ? fmtDateTime(record.proposeTime) : '—' }}</template>
            <template v-else-if="column.key === 'replyTime'">{{ record.replyTime ? fmtDateTime(record.replyTime) : '—' }}</template>
            <template v-else-if="column.key === 'replyOverdue'">
              <a-tag v-if="record.replyOverdue" color="orange">超 3 日未答复</a-tag>
              <span v-else>—</span>
            </template>
            <template v-else-if="column.key === 'action'">
              <a
                v-if="can('bidding:objection:reply') && !record.replyTime"
                @click="openReply(record)"
              >
                答复
              </a>
            </template>
          </template>
          <template #emptyText><div class="ct-empty">暂无异议记录</div></template>
        </a-table>
      </div>
    </div>

    <!-- 提出异议 -->
    <a-modal
      v-model:open="proposeOpen"
      title="提出投标异议"
      :width="640"
      :confirm-loading="proposing"
      ok-text="提出"
      cancel-text="取消"
      destroy-on-close
      @ok="submitPropose"
    >
      <a-form layout="vertical">
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="招标项目编号（必填）">
              <a-input v-model:value="proposeForm.tenderProjectCode" placeholder="TENDER_PROJECT_CODE" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="标段编号">
              <a-input v-model:value="proposeForm.bidSectionCode" placeholder="可空" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="异议类型（必填）">
              <a-select v-model:value="proposeForm.objectionType" placeholder="选择类型" allow-clear>
                <a-select-option v-for="o in dictOptions('objection_type')" :key="o.value" :value="o.value">{{ o.label }}</a-select-option>
                <a-select-option value="PREQUAL_FILE">资格预审文件</a-select-option>
                <a-select-option value="TENDER_FILE">招标文件</a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="主体 ID">
              <a-input v-model:value="proposeForm.subjectId" placeholder="可空" />
            </a-form-item>
          </a-col>
          <a-col :span="24">
            <a-form-item label="异议内容（必填）">
              <a-textarea v-model:value="proposeForm.content" :rows="3" />
            </a-form-item>
          </a-col>
          <a-col :span="24">
            <a-form-item label="备注">
              <a-textarea v-model:value="proposeForm.remark" :rows="2" />
            </a-form-item>
          </a-col>
        </a-row>
      </a-form>
      <div class="ct-tip">超期提出（资格预审文件 2 日前 / 招标文件 10 日前）后端拒绝（1060118001）。</div>
    </a-modal>

    <!-- 答复异议 -->
    <a-modal
      v-model:open="replyOpen"
      :title="'答复异议' + (replyTarget ? '（' + replyTarget.id + '）' : '')"
      :width="560"
      :confirm-loading="replying"
      ok-text="答复"
      cancel-text="取消"
      destroy-on-close
      @ok="submitReply"
    >
      <a-form layout="vertical">
        <a-form-item label="原异议内容">
          <div class="ct-tip" style="white-space: pre-wrap">{{ replyTarget?.content }}</div>
        </a-form-item>
        <a-form-item label="答复内容（必填）">
          <a-textarea v-model:value="replyContent" :rows="4" />
        </a-form-item>
      </a-form>
      <div class="ct-tip">收到异议后 3 日内答复；已答复不可重复答复（1060118002）。</div>
    </a-modal>
  </a-drawer>
</template>
