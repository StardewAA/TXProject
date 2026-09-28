<!--
  资格预审（抽屉）—— 项目台账 6771 的子功能入口（按钮权限点 bidding:prequal:save/publish/close/query/apply/review）
  契约：后端变更通知-获取文件与资格预审-2026-09-28（PrequalController，前缀 /bidding/prequal）
  结构：Tab①资格预审列表（招标方：创建/发布/关闭）→ Tab②申请列表（投标方：提交申请；审查方：通过/不通过）。
  展示口径：状态走字典（prequal_status / prequal_apply_status），缺字典回显原码；时间 epoch 毫秒 → fmtDateTime。
-->
<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';

import { message } from 'ant-design-vue';

import { useAccess } from '@vben/access';
import { useAccessStore } from '@vben/stores';

import {
  type PrequalApi,
  applyPrequal,
  closePrequal,
  getPrequalApplyList,
  getPrequalPage,
  listPrequalCommittee,
  publishPrequal,
  reviewPrequal,
  savePrequal,
  savePrequalCommittee,
} from '#/api/bidding/prequal';

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

const dict = (t: string, c?: null | string): string => (c ? dictLabel(t, c) || c : '—');
const hasDict = (t: string): boolean => dictOptions(t).length > 0;

/* ===== Tab① 资格预审列表 ===== */
const rows = ref<PrequalApi.Record[]>([]);
const total = ref(0);
const loading = ref(false);
const query = reactive<PrequalApi.PageParams>({ pageNo: 1, pageSize: 10 });

async function load() {
  loading.value = true;
  try {
    const page = await getPrequalPage({
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

/* ===== 创建/修改（save） ===== */
const saveOpen = ref(false);
const saving = ref(false);
const saveForm = reactive({
  id: undefined as undefined | string,
  tenderProjectCode: '',
  bidSectionCode: '',
  title: '',
  noticeCode: '',
  fileId: undefined as undefined | number,
  applyStartTime: undefined as undefined | string,
  applyDeadline: undefined as undefined | string,
  remark: '',
});
function openCreate(): void {
  saveForm.id = undefined;
  saveForm.tenderProjectCode = props.tenderProjectCode ?? '';
  saveForm.bidSectionCode = props.bidSectionCode ?? '';
  saveForm.title = '';
  saveForm.noticeCode = '';
  saveForm.fileId = undefined;
  saveForm.applyStartTime = undefined;
  saveForm.applyDeadline = undefined;
  saveForm.remark = '';
  saveOpen.value = true;
}
function openEdit(r: PrequalApi.Record): void {
  saveForm.id = r.id;
  saveForm.tenderProjectCode = r.tenderProjectCode;
  saveForm.bidSectionCode = r.bidSectionCode ?? '';
  saveForm.title = r.title;
  saveForm.noticeCode = r.noticeCode ?? '';
  saveForm.fileId = r.fileId;
  saveForm.applyStartTime = r.applyStartTime;
  saveForm.applyDeadline = r.applyDeadline;
  saveForm.remark = r.remark ?? '';
  saveOpen.value = true;
}
async function submitSave(): Promise<void> {
  if (!saveForm.tenderProjectCode.trim() || !saveForm.title.trim()) {
    message.warning('请填写招标项目编号与预审标题');
    return;
  }
  saving.value = true;
  try {
    await savePrequal({
      id: saveForm.id,
      tenderProjectCode: saveForm.tenderProjectCode.trim(),
      bidSectionCode: saveForm.bidSectionCode.trim() || undefined,
      title: saveForm.title.trim(),
      noticeCode: saveForm.noticeCode.trim() || undefined,
      fileId: saveForm.fileId,
      applyStartTime: saveForm.applyStartTime,
      applyDeadline: saveForm.applyDeadline,
      remark: saveForm.remark.trim() || undefined,
    });
    message.success(saveForm.id ? '已更新' : '已创建（草稿）');
    saveOpen.value = false;
    void load();
  } catch (error: unknown) {
    message.warning((error as { message?: string }).message || String(error));
  } finally {
    saving.value = false;
  }
}

/* ===== 发布 / 关闭 ===== */
async function confirmPublish(r: PrequalApi.Record): Promise<void> {
  try {
    await publishPrequal(r.id);
    message.success('已发布（校验申请期 ≥ 5 日，条例17条）');
    void load();
  } catch (error: unknown) {
    message.warning((error as { message?: string }).message || String(error));
  }
}
async function confirmClose(r: PrequalApi.Record): Promise<void> {
  try {
    await closePrequal(r.id);
    message.success('已关闭');
    void load();
  } catch (error: unknown) {
    message.warning((error as { message?: string }).message || String(error));
  }
}

const prequalColumns = [
  { title: '预审编号', dataIndex: 'prequalCode', key: 'prequalCode', width: 150, ellipsis: true },
  { title: '标题', dataIndex: 'title', key: 'title', width: 200, ellipsis: true },
  { title: '招标项目', dataIndex: 'tenderProjectCode', key: 'tenderProjectCode', width: 170, ellipsis: true },
  { title: '标段', dataIndex: 'bidSectionCode', key: 'bidSectionCode', width: 150, ellipsis: true },
  { title: '申请开始', dataIndex: 'applyStartTime', key: 'applyStartTime', width: 160 },
  { title: '申请截止', dataIndex: 'applyDeadline', key: 'applyDeadline', width: 160 },
  { title: '状态', dataIndex: 'status', key: 'status', width: 110 },
  { title: '备注', dataIndex: 'remark', key: 'remark', ellipsis: true },
  { title: '操作', key: 'action', width: 180, fixed: 'right' },
];

/* ===== 资格审查委员会（后端变更通知-资格审查委员会-2026-09-28） =====
 * 法规：条例第18条（国有资金项目强制组建）、第37条（5 人以上单数、技术/经济专家≥2/3）。
 * 权限：bidding:prequal:committee（save）；list 复用 bidding:prequal:query。
 * 审查前置：国有资金（fund_source∈01/02/03）项目未组建委员会 → review 返回 1060115008；组成不合规 1060115009。 */
const committeeOpen = ref(false);
const committeePrequalId = ref('');
const committeeLoading = ref(false);
const committeeSaving = ref(false);
const committeeRows = ref<PrequalApi.CommitteeMember[]>([]);
const MEMBER_CATEGORY_OPTIONS = [
  { label: '招标人代表', value: 'TENDERER_REP' },
  { label: '技术专家', value: 'TECH_EXPERT' },
  { label: '经济专家', value: 'ECON_EXPERT' },
];
const MEMBER_ROLE_OPTIONS = [
  { label: '组长', value: 'LEADER' },
  { label: '成员', value: 'MEMBER' },
];
const newMember = reactive({
  memberName: '',
  memberCategory: undefined as undefined | string,
  memberRole: undefined as undefined | string,
  memberOrg: '',
  memberTitle: '',
  sort: undefined as undefined | number,
  remark: '',
});
async function openCommittee(r: PrequalApi.Record): Promise<void> {
  committeePrequalId.value = r.id;
  committeeRows.value = [];
  committeeOpen.value = true;
  committeeLoading.value = true;
  try {
    committeeRows.value = await listPrequalCommittee(r.id);
  } catch {
    committeeRows.value = [];
  } finally {
    committeeLoading.value = false;
  }
}
function addCommitteeMember(): void {
  if (!newMember.memberName.trim() || !newMember.memberCategory) {
    message.warning('成员姓名与类别必填');
    return;
  }
  committeeRows.value.push({
    memberName: newMember.memberName.trim(),
    memberCategory: newMember.memberCategory,
    memberRole: newMember.memberRole,
    memberOrg: newMember.memberOrg.trim() || undefined,
    memberTitle: newMember.memberTitle.trim() || undefined,
    sort: newMember.sort,
    remark: newMember.remark.trim() || undefined,
  });
  newMember.memberName = '';
  newMember.memberCategory = undefined;
  newMember.memberRole = undefined;
  newMember.memberOrg = '';
  newMember.memberTitle = '';
  newMember.sort = undefined;
  newMember.remark = '';
}
function removeCommitteeMember(index: number): void {
  committeeRows.value.splice(index, 1);
}
async function submitCommittee(): Promise<void> {
  if (!committeePrequalId.value) return;
  if (committeeRows.value.length === 0) {
    message.warning('请至少添加 1 名成员');
    return;
  }
  committeeSaving.value = true;
  try {
    await savePrequalCommittee({ prequalId: committeePrequalId.value, members: committeeRows.value });
    message.success('委员会已保存（整单覆盖）');
    committeeOpen.value = false;
  } catch (error: unknown) {
    const msg = (error as { message?: string }).message || String(error);
    // 组成校验失败（条例37条）：5 人以上单数、技术/经济专家≥2/3
    if (/1060115009|组成|单数|2\/3/.test(msg)) {
      message.warning('委员会组成不合规：成员须 5 人以上单数，技术/经济专家不少于 2/3（条例37条）');
    } else {
      message.warning(msg);
    }
  } finally {
    committeeSaving.value = false;
  }
}

/* ===== Tab② 申请列表（用户 2026-10-02：自动带入，不再手输资格预审 ID） ===== */
const activeTab = ref('prequal');
const applyRows = ref<PrequalApi.ApplyRecord[]>([]);
const applyLoading = ref(false);
const applyPrequalId = ref('');
const applyPrequalTitle = ref('');
/** 从 Tab① 某条资格预审进入其申请列表（自动带入上下文） */
function openApplies(r: PrequalApi.Record): void {
  applyPrequalId.value = r.id;
  applyPrequalTitle.value = r.title || r.prequalCode || '';
  activeTab.value = 'apply';
  void loadApplies();
}
function backToPrequal(): void {
  activeTab.value = 'prequal';
}
async function loadApplies(): Promise<void> {
  if (!applyPrequalId.value) {
    message.warning('请先在「资格预审列表」选择一条资格预审');
    return;
  }
  applyLoading.value = true;
  try {
    applyRows.value = await getPrequalApplyList(applyPrequalId.value);
  } catch {
    applyRows.value = [];
  } finally {
    applyLoading.value = false;
  }
}

/* ===== 提交申请 ===== */
const applyOpen = ref(false);
const applying = ref(false);
const applyForm = reactive({
  prequalId: '',
  subjectId: undefined as undefined | string,
  fileId: undefined as undefined | number,
  remark: '',
});
function openApply(): void {
  applyForm.prequalId = applyPrequalId.value;
  applyForm.subjectId = undefined;
  applyForm.fileId = undefined;
  applyForm.remark = '';
  applyOpen.value = true;
}
async function submitApply(): Promise<void> {
  if (!applyForm.prequalId.trim()) {
    message.warning('请填写资格预审 ID');
    return;
  }
  applying.value = true;
  try {
    await applyPrequal({
      prequalId: applyForm.prequalId.trim(),
      subjectId: applyForm.subjectId,
      fileId: applyForm.fileId,
      remark: applyForm.remark.trim() || undefined,
    });
    message.success('申请已提交（同一主体幂等）');
    applyOpen.value = false;
    void loadApplies();
  } catch (error: unknown) {
    message.warning((error as { message?: string }).message || String(error));
  } finally {
    applying.value = false;
  }
}

/* ===== 审查（通过/不通过） ===== */
function confirmReview(r: PrequalApi.ApplyRecord, passed: boolean): void {
  void (async () => {
    try {
      await reviewPrequal({ applyId: r.id, passed });
      message.success(passed ? '已通过' : '已不通过');
      void loadApplies();
    } catch (error: unknown) {
      const msg = (error as { message?: string }).message || String(error);
      // 资格审查委员会前置校验（条例第18/37条）：未组建 → 1060115008；组成不合规 → 1060115009
      if (/1060115008|未组建|委员会/.test(msg)) {
        message.warning('该项目为国有资金项目，审查前必须先组建资格审查委员会（条例第18条）——请到「资格预审列表」该行的「委员会」入口组建（5 人以上单数，技术/经济专家≥2/3）');
      } else if (/1060115009|组成|单数|2\/3/.test(msg)) {
        message.warning('委员会组成不合规：成员须 5 人以上单数，技术/经济专家不少于 2/3（条例37条），请调整后重试');
      } else {
        message.warning(msg);
      }
    }
  })();
}

const applyColumns = [
  { title: '申请 ID', dataIndex: 'id', key: 'id', width: 100 },
  { title: '预审', dataIndex: 'prequalCode', key: 'prequalCode', width: 140, ellipsis: true },
  { title: '招标项目', dataIndex: 'tenderProjectCode', key: 'tenderProjectCode', width: 170, ellipsis: true },
  { title: '主体 ID', dataIndex: 'subjectId', key: 'subjectId', width: 110 },
  { title: '申请时间', dataIndex: 'applyTime', key: 'applyTime', width: 160 },
  { title: '状态', dataIndex: 'status', key: 'status', width: 100 },
  { title: '审查结果', dataIndex: 'reviewResult', key: 'reviewResult', width: 100 },
  { title: '审查理由', dataIndex: 'reviewReason', key: 'reviewReason', width: 160, ellipsis: true },
  { title: '审查时间', dataIndex: 'reviewTime', key: 'reviewTime', width: 160 },
  { title: '操作', key: 'action', width: 150, fixed: 'right' },
];
</script>

<template>
  <a-drawer
    :open="open"
    :title="'资格预审' + (props.tenderProjectCode ? ' · ' + props.tenderProjectCode : '')"
    :width="1280"
    destroy-on-close
    @close="emit('update:open', false)"
  >
    <div class="ct-page">
      <a-tabs v-model:activeKey="activeTab">
        <!-- Tab① 预审列表 -->
        <a-tab-pane key="prequal" tab="资格预审列表">
          <div class="ct-head">
            <div>
              <div class="ct-sub">招标方发起资格预审；发布校验申请期 ≥ 5 日（条例17条）；通过者 &lt; 3 家告警应重新招标（条例19条）。</div>
            </div>
            <div class="ct-actions">
              <a-button v-if="can('bidding:prequal:save')" type="primary" @click="openCreate">创建资格预审</a-button>
              <a-button @click="load">刷新</a-button>
            </div>
          </div>
          <div class="ct-panel-b">
            <a-table
              class="ct-table"
              :columns="prequalColumns"
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
                <template v-if="column.key === 'applyStartTime'">{{ record.applyStartTime ? fmtDateTime(record.applyStartTime) : '—' }}</template>
                <template v-else-if="column.key === 'applyDeadline'">{{ record.applyDeadline ? fmtDateTime(record.applyDeadline) : '—' }}</template>
                <template v-else-if="column.key === 'status'">
                  {{ hasDict('prequal_status') ? dict('prequal_status', record.status) : dict('', record.status) }}
                </template>
                <template v-else-if="column.key === 'action'">
                  <a-space size="small">
                    <a v-if="can('bidding:prequal:save')" @click="openEdit(record)">编辑</a>
                    <a v-if="can('bidding:prequal:publish') && record.status === 'DRAFT'" @click="confirmPublish(record)">发布</a>
                    <a v-if="can('bidding:prequal:close') && record.status === 'PUBLISHED'" @click="confirmClose(record)">关闭</a>
                    <a v-if="record.status === 'PUBLISHED' && can('bidding:prequal:apply')" class="text-blue-600" @click="openApplies(record)">提交申请</a>
                    <a v-if="can('bidding:prequal:committee')" class="text-blue-600" @click="openCommittee(record)">委员会</a>
                  </a-space>
                </template>
              </template>
              <template #emptyText><div class="ct-empty">暂无资格预审</div></template>
            </a-table>
          </div>
        </a-tab-pane>

        <!-- Tab② 申请列表（自动带入：从 Tab① 选择资格预审进入，不再手输 ID） -->
        <a-tab-pane key="apply" tab="资格预审申请">
          <div class="ct-head">
            <div class="ct-actions" style="width: 100%">
              <span v-if="applyPrequalId" class="ct-current-pre">
                <span class="ct-sub">当前预审：</span><b>{{ applyPrequalTitle }}</b>
                <span class="ct-pre-id">{{ applyPrequalId }}</span>
                <a class="text-blue-600" @click="backToPrequal">← 回列表选</a>
              </span>
              <span v-else class="ct-sub">请先在「资格预审列表」选择一条已发布的资格预审进入</span>
              <a-button type="primary" :disabled="!applyPrequalId" @click="loadApplies">刷新申请</a-button>
              <a-button v-if="can('bidding:prequal:apply')" :disabled="!applyPrequalId" @click="openApply">提交申请</a-button>
            </div>
          </div>
          <div class="ct-panel-b">
            <a-table
              class="ct-table"
              :columns="applyColumns"
              :data-source="applyRows"
              :loading="applyLoading"
              :pagination="false"
              :scroll="{ x: 1400 }"
              :row-key="(r: any) => r?.id"
              size="middle"
            >
              <template #bodyCell="{ column, record }">
                <template v-if="column.key === 'applyTime'">{{ record.applyTime ? fmtDateTime(record.applyTime) : '—' }}</template>
                <template v-else-if="column.key === 'reviewTime'">{{ record.reviewTime ? fmtDateTime(record.reviewTime) : '—' }}</template>
                <template v-else-if="column.key === 'status'">
                  {{ hasDict('prequal_apply_status') ? dict('prequal_apply_status', record.status) : dict('', record.status) }}
                </template>
                <template v-else-if="column.key === 'action'">
                  <a-space size="small" v-if="can('bidding:prequal:review') && !record.reviewTime">
                    <a class="text-green-600" @click="confirmReview(record, true)">通过</a>
                    <a class="text-destructive" @click="confirmReview(record, false)">不通过</a>
                  </a-space>
                  <span v-else class="text-muted-foreground">—</span>
                </template>
              </template>
              <template #emptyText><div class="ct-empty">输入预审 ID 查询申请；暂无记录</div></template>
            </a-table>
          </div>
        </a-tab-pane>
      </a-tabs>
    </div>

    <!-- 创建/修改预审 -->
    <a-modal
      v-model:open="saveOpen"
      :title="saveForm.id ? '编辑资格预审 #' + saveForm.id : '创建资格预审'"
      :width="720"
      :confirm-loading="saving"
      ok-text="保存"
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
            <a-form-item label="预审标题（必填）">
              <a-input v-model:value="saveForm.title" placeholder="预审标题" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="公告编号">
              <a-input v-model:value="saveForm.noticeCode" placeholder="可空" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="申请开始时间">
              <a-date-picker v-model:value="saveForm.applyStartTime" value-format="x" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="申请截止时间">
              <a-date-picker v-model:value="saveForm.applyDeadline" value-format="x" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="24">
            <a-form-item label="备注">
              <a-textarea v-model:value="saveForm.remark" :rows="2" />
            </a-form-item>
          </a-col>
        </a-row>
      </a-form>
      <div class="ct-tip">发布时强校验「申请开始 → 申请截止 ≥ 5 日」（条例17条，1060114002）。</div>
    </a-modal>

    <!-- 资格审查委员会（条例第18条：国有资金项目强制组建；第37条：5 人以上单数、技术/经济专家≥2/3） -->
    <a-modal
      v-model:open="committeeOpen"
      :title="'资格审查委员会 · 预审 #' + committeePrequalId"
      :width="900"
      :confirm-loading="committeeSaving"
      ok-text="保存委员会"
      cancel-text="取消"
      destroy-on-close
      @ok="submitCommittee"
    >
      <div class="ct-tip mb-2">国有资金项目（资金来源 01/02/03）审查前置：未组建委员会禁止审查（条例18条，1060115008）。保存为整单覆盖。</div>
      <a-table
        class="ct-table"
        :columns="[
          { title: '成员姓名', dataIndex: 'memberName', key: 'memberName', width: 110 },
          { title: '类别', dataIndex: 'memberCategory', key: 'memberCategory', width: 110 },
          { title: '角色', dataIndex: 'memberRole', key: 'memberRole', width: 80 },
          { title: '单位', dataIndex: 'memberOrg', key: 'memberOrg', width: 140, ellipsis: true },
          { title: '职称', dataIndex: 'memberTitle', key: 'memberTitle', width: 120, ellipsis: true },
          { title: '排序', dataIndex: 'sort', key: 'sort', width: 60 },
          { title: '操作', key: 'action', width: 60 },
        ]"
        :data-source="committeeRows"
        :loading="committeeLoading"
        :pagination="false"
        :scroll="{ x: 860 }"
        :row-key="(_r: any, i: number) => i"
        size="small"
      >
        <template #bodyCell="{ column, record, index }">
          <template v-if="column.key === 'memberCategory'">
            {{ (MEMBER_CATEGORY_OPTIONS.find((o) => o.value === record.memberCategory) || {}).label || record.memberCategory }}
          </template>
          <template v-else-if="column.key === 'memberRole'">
            {{ (MEMBER_ROLE_OPTIONS.find((o) => o.value === record.memberRole) || {}).label || record.memberRole || '—' }}
          </template>
          <template v-else-if="column.key === 'action'">
            <a class="text-destructive" @click="removeCommitteeMember(index)">移除</a>
          </template>
        </template>
        <template #emptyText><div class="ct-empty">暂无成员，请在下方添加</div></template>
      </a-table>
      <a-divider style="margin: 12px 0" />
      <div class="ct-sub mb-1">添加成员</div>
      <a-row :gutter="8">
        <a-col :span="5">
          <a-input v-model:value="newMember.memberName" placeholder="姓名（必填）" />
        </a-col>
        <a-col :span="5">
          <a-select v-model:value="newMember.memberCategory" :options="MEMBER_CATEGORY_OPTIONS" placeholder="类别（必填）" style="width: 100%" />
        </a-col>
        <a-col :span="4">
          <a-select v-model:value="newMember.memberRole" :options="MEMBER_ROLE_OPTIONS" placeholder="角色" allow-clear style="width: 100%" />
        </a-col>
        <a-col :span="5">
          <a-input v-model:value="newMember.memberOrg" placeholder="单位" />
        </a-col>
        <a-col :span="5">
          <a-input v-model:value="newMember.memberTitle" placeholder="职称" />
        </a-col>
      </a-row>
      <a-row :gutter="8" class="mt-1">
        <a-col :span="5">
          <a-input-number v-model:value="newMember.sort" placeholder="排序" style="width: 100%" />
        </a-col>
        <a-col :span="14">
          <a-input v-model:value="newMember.remark" placeholder="备注" />
        </a-col>
        <a-col :span="5">
          <a-button type="primary" block @click="addCommitteeMember">添加成员</a-button>
        </a-col>
      </a-row>
    </a-modal>

    <!-- 提交申请 -->
    <a-modal
      v-model:open="applyOpen"
      title="提交资格预审申请"
      :width="560"
      :confirm-loading="applying"
      ok-text="提交"
      cancel-text="取消"
      destroy-on-close
      @ok="submitApply"
    >
      <a-form layout="vertical">
        <a-form-item label="资格预审 ID（必填）">
          <a-input v-model:value="applyForm.prequalId" placeholder="预审 ID" />
        </a-form-item>
        <a-form-item label="主体 ID">
          <a-input v-model:value="applyForm.subjectId" placeholder="可空" />
        </a-form-item>
        <a-form-item label="备注">
          <a-textarea v-model:value="applyForm.remark" :rows="2" />
        </a-form-item>
      </a-form>
      <div class="ct-tip">申请截止前可提交（超时 1060114003）；同一主体幂等。</div>
    </a-modal>
  </a-drawer>
</template>
