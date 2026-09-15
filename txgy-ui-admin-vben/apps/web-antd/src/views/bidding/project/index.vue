<script setup lang="ts">
/* 项目台账（菜单 6771 / component bidding/project/index / componentName BiddingProject / 权限 bidding:project:query）
 * 契约：项目域接口（api/bidding/project）
 * 口径：
 *   · 时间字段（createTime 等）为 **epoch 毫秒**（平台统一序列化）→ 一律过 fmtDateTime；
 *   · state 是模块内部管理态（ACTIVE/CLOSED），不是数据集字段，故本地用固定两档展示；
 *   · fields 为 ProjectVO 数据集全量字段（key=规范英文名），详情按原样列出，不硬编码字段名清单；
 *   · 追溯（trace）一次取回：项目 → 招标项目 → 标段 + 公告/外部公告/备案/关注/主体 + 统计。 */
import { computed, h, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';

import { useAccess } from '@vben/access';
import { useVbenModal } from '@vben/common-ui';
import { useAccessStore } from '@vben/stores';

import { message, Modal } from 'ant-design-vue';

import { FILING_STATUS_OPTIONS, type FilingApi, receiveFilingResult, submitFiling } from '#/api/bidding/filing';
import { fetchNoticeDetail, type NoticeApi } from '#/api/bidding/notice';
import {
  deleteProject,
  getProject,
  getProjectPage,
  type ProjectApi,
  saveProject,
  traceBidSection,
  traceProject,
  traceTenderProject,
} from '#/api/bidding/project';

import { fmtDateTime } from '../certificate/format';
import { useCertMeta } from '../certificate/useCertMeta';
import AttrFilter from '../components/AttrFilter.vue';
import CopyText from '../components/CopyText.vue';
import DetailModal from '../noticeExternal/modules/detail.vue';
import NoticeHtmlView from '../notice/NoticeHtmlView.vue';
import TraceLevelPanel, { type TraceLevel } from './TraceLevelPanel.vue';

import '../certificate/cert.css';

const router = useRouter();

const { hasAccessByCodes } = useAccess();
const accessStore = useAccessStore();
/* 字典：备案状态下拉等走字典（dictOptions——字典未下发时回显原码、空值给"—"）。
 * 追溯抽屉内状态/类型字典（dictLabel/dictTag）已随面板内容迁至 TraceLevelPanel.vue。 */
const { dictOptions } = useCertMeta();

/** 权限判定：权限码未下发时放行（最终由后端 @PreAuthorize 裁决），避免界面被锁死 */
function can(code: string): boolean {
  const codes = accessStore.accessCodes ?? [];
  if (codes.length === 0) return true;
  return codes.includes('*') || codes.includes('*:*:*') || hasAccessByCodes([code]);
}

const query = reactive<ProjectApi.PageParams>({
  pageNo: 1,
  pageSize: 10,
  projectCode: undefined,
  projectName: undefined,
  regionCode: undefined,
  industriesType: undefined,
  state: undefined,
});
const rows = ref<ProjectApi.Project[]>([]);
const total = ref(0);
const loading = ref(false);

async function load() {
  loading.value = true;
  try {
    const page = await getProjectPage({ ...query });
    rows.value = page.list ?? [];
    total.value = page.total ?? 0;
  } catch {
    rows.value = [];
    total.value = 0;
  } finally {
    loading.value = false;
  }
}

function resetQuery() {
  query.projectCode = undefined;
  query.projectName = undefined;
  query.regionCode = undefined;
  query.industriesType = undefined;
  query.state = undefined;
  query.pageNo = 1;
  void load();
}

/* ================= 项目备案：提交 / 回执登记（FilingController） ================= */
const filingOpen = ref(false);
const filingSaving = ref(false);
const filingForm = reactive<FilingApi.SubmitReq>({ filingType: '', projectCode: '' });
const filingAttachmentText = ref('');

/** 逗号/空格分隔的文件 id → number[]（后端为 Long 列表） */
function toFileIds(text: string) {
  const ids = text
    .split(/[,\s，]+/)
    .map((t) => t.trim())
    .filter(Boolean)
    .map((t) => Number(t))
    .filter((n) => Number.isFinite(n));
  return ids.length > 0 ? ids : undefined;
}

function openFiling(row: ProjectApi.Project) {
  Object.assign(filingForm, {
    attachmentFileIds: undefined,
    dataTimestamp: undefined,
    filingType: '',
    orgForm: undefined,
    projectCode: row.projectCode ?? '',
    submitTime: undefined,
    superviseDeptCode: undefined,
    superviseDeptName: undefined,
    tenderMode: undefined,
    tenderProjectCode: undefined,
    unifiedDealCode: undefined,
  });
  filingAttachmentText.value = '';
  filingOpen.value = true;
}

const receiptOpen = ref(false);
const receiptSaving = ref(false);
const receiptForm = reactive<FilingApi.ResultReq>({ receiptNo: '', resultStatus: '' });

async function submitFilingForm() {
  if (!filingForm.filingType.trim() || !filingForm.projectCode.trim()) {
    message.warning('备案类型与项目编号不能为空');
    return;
  }
  filingSaving.value = true;
  try {
    const resp = await submitFiling({ ...filingForm, attachmentFileIds: toFileIds(filingAttachmentText.value) });
    message.success(`备案已提交：受理号 ${resp?.receiptNo ?? '—'}｜状态 ${resp?.status ?? '—'}`);
    filingOpen.value = false;
    // 提交后把受理号带到回执登记，省一次手工誊抄
    receiptForm.receiptNo = resp?.receiptNo ?? '';
  } catch {
    // 静默：重复提交由后端返回 FILING_ALREADY_SUBMITTED（§0.4）
  } finally {
    filingSaving.value = false;
  }
}

function openReceipt() {
  Object.assign(receiptForm, { opinion: undefined, receiptNo: receiptForm.receiptNo ?? '', resultStatus: '' });
  receiptOpen.value = true;
}

async function submitReceipt() {
  if (!receiptForm.receiptNo.trim() || !receiptForm.resultStatus) {
    message.warning('受理号与回推结果不能为空');
    return;
  }
  receiptSaving.value = true;
  try {
    await receiveFilingResult({ ...receiptForm });
    message.success('备案回执已登记');
    receiptOpen.value = false;
  } catch {
    // 静默
  } finally {
    receiptSaving.value = false;
  }
}

onMounted(load);

const kpi = computed(() => {
  const list = rows.value;
  const active = list.filter((r) => (r.state ?? 'ACTIVE') === 'ACTIVE').length;
  return { active, closed: list.length - active };
});

const columns = [
  { title: '项目编号', customRender: ({ text }: { text?: null | number | string }) => h(CopyText, { text, label: '项目编号' }),  dataIndex: 'projectCode', key: 'projectCode', width: 190 },
  { title: '项目名称', dataIndex: 'projectName', key: 'projectName', width: 320 },
  { title: '地区', dataIndex: 'regionCode', key: 'regionCode', width: 110 },
  { title: '法定代表人', dataIndex: 'legalPerson', key: 'legalPerson', width: 130 },
  { title: '状态', dataIndex: 'state', key: 'state', width: 100 },
  { title: '登记时间', dataIndex: 'createTime', key: 'createTime', width: 170 },
  { title: '操作', key: 'action', width: 270, fixed: 'right' as const },
];

/* ===== 追溯抽屉栈（层叠下钻：L1 项目 → L2 招标项目 → L3 标段） =====
 * 定稿口径（2026-09-14）：抽屉保留并**可层叠拉出下级抽屉**（逐层内缩 + 去遮罩），
 * 同时行内「详情 ↗」打开**通用实体详情页**（/bidding/entity/:type/:code，登记/编辑/发布在那里做）。
 * 栈语义：push 一层、关闭只关当前层、上层保留；L3 标段层取 traceBidSection 的关联内部公告。 */
let levelSeq = 0;
const stack = ref<TraceLevel[]>([]);

async function fetchTraceFor(t: TraceLevel['queryType'], code: string) {
  if (t === 'PROJECT') return traceProject(code);
  if (t === 'TENDER_PROJECT') return traceTenderProject(code);
  return traceBidSection(code);
}
async function pushLevel(type: TraceLevel['queryType'], code: string, label: string): Promise<void> {
  const level: TraceLevel = {
    key: levelSeq++,
    queryType: type,
    queryCode: code,
    label,
    open: true,
    trace: null,
    loading: true,
  };
  stack.value.push(level);
  try {
    level.trace = await fetchTraceFor(type, code);
  } catch {
    level.trace = null;
  } finally {
    level.loading = false;
  }
}
/** 打开项目级追溯（列表行「追溯」） */
function openTrace(r: ProjectApi.Project): void {
  void pushLevel('PROJECT', r.projectCode, r.projectName || r.projectCode);
}
/** 抽屉行「拉出下级」 */
function onDrill(v: { code: string; label: string; type: 'BID_SECTION' | 'TENDER_PROJECT' }): void {
  void pushLevel(v.type, v.code, v.label);
}
/** 关闭当前层（含其上所有层）——只关自己、上层保留 = 栈语义 */
function closeLevel(key: number): void {
  const i = stack.value.findIndex((l) => l.key === key);
  if (i >= 0) {
    const l = stack.value[i];
    if (l) l.open = false;
    stack.value.splice(i); // 关闭本层及其上所有层
  }
}
function onDrawerUpdate(key: number, open: boolean): void {
  if (!open) closeLevel(key);
}

/* ===== 通用实体详情页（/bidding/entity/:type/:code，登记/编辑/发布在那里做，不限实体） ===== */
function openEntityDetail(v: { code: string; type: 'BID_SECTION' | 'PROJECT' | 'TENDER_PROJECT' }): void {
  void router.push(`/bidding/entity/${v.type}/${encodeURIComponent(v.code)}`);
}

/* ===== 公告详情弹窗（内部公告：附件 / 版式 PDF，NoticeHtmlView 渲染） ===== */
const noticeDetailOpen = ref(false);
const noticeDetailLoading = ref(false);
const noticeDetail = ref<NoticeApi.Detail | null>(null);
async function openNoticeDetail(row: ProjectApi.TraceNotice): Promise<void> {
  if (row.id === undefined) return;
  noticeDetailOpen.value = true;
  noticeDetailLoading.value = true;
  noticeDetail.value = null;
  try {
    noticeDetail.value = await fetchNoticeDetail(row.id);
  } catch {
    noticeDetail.value = null;
  } finally {
    noticeDetailLoading.value = false;
  }
}

/* ===== 外部公告预览（复用 noticeExternal DetailModal，需审核权限 bidding:notice-external:update） ===== */
const [ExternalDetail, externalDetailModalApi] = useVbenModal({
  connectedComponent: DetailModal,
  destroyOnClose: true,
});
function openExternalDetail(row: ProjectApi.TraceExternalNotice): void {
  if (!can('bidding:notice-external:update')) {
    message.warning('无外部公告审核权限（bidding:notice-external:update），无法预览');
    return;
  }
  externalDetailModalApi.setData(row as never).open();
}

/* ===== 新建 / 编辑 ===== */
const formOpen = ref(false);
const saving = ref(false);
const formRef = ref();
const form = reactive<ProjectApi.Project>({ projectCode: '', projectName: '' });
const isEdit = computed(() => !!form.id);

const rules = {
  projectCode: [{ required: true, message: '项目编号不能为空', trigger: 'blur' }],
  projectName: [{ required: true, message: '项目名称不能为空', trigger: 'blur' }],
};

async function onCreate() {
  Object.assign(form, {
    id: undefined,
    projectCode: '',
    projectName: '',
    regionCode: undefined,
    legalPerson: undefined,
    industriesType: undefined,
    fundSource: undefined,
    projectScale: undefined,
    approvalDeptName: undefined,
    approvalDocNo: undefined,
    state: 'ACTIVE',
    remark: undefined,
  });
  formOpen.value = true;
}

async function onEdit(r: ProjectApi.Project) {
  Object.assign(form, { ...r });
  // §2.4 同源口径：编辑前以详情为准，避免用列表行旧值覆盖
  if (r.id) {
    try {
      Object.assign(form, await getProject(r.id));
    } catch {
      /* 详情取不到时退回列表行数据 */
    }
  }
  formOpen.value = true;
}

async function submit() {
  try {
    await formRef.value?.validate();
  } catch {
    return;
  }
  saving.value = true;
  try {
    await saveProject({ ...form });
    message.success(isEdit.value ? '已保存' : '登记成功');
    formOpen.value = false;
    void load();
  } catch {
    // 失败原因由请求层统一 toast 后端 msg
  } finally {
    saving.value = false;
  }
}

function confirmDelete(r: ProjectApi.Project) {
  Modal.confirm({
    title: '删除项目',
    okText: '删除',
    content: `确认删除项目「${r.projectName}（${r.projectCode}）」？已关联招标项目/标段时后端会拒绝。`,
    okType: 'danger',
    async onOk() {
      await deleteProject(r.id as number);
      message.success('已删除');
      void load();
    },
  });
}
</script>

<template>
  <div class="ct-page">
    <div class="ct-head">
      <div>
        <div class="ct-title">项目台账</div>
        <div class="ct-sub">项目主档与数据集字段；追溯一次取回招标项目、标段与公告关联</div>
      </div>
      <div class="ct-actions">
        <a-button v-if="can('bidding:project:create')" type="primary" @click="onCreate">登记项目</a-button>
        <a-button @click="load">刷新</a-button>
      </div>
    </div>

    <div class="ct-kpis">
      <div class="ct-kpi k-blue">
        <div class="k-label">项目总数</div>
        <div class="k-num">{{ total }}</div>
        <div class="k-note">按当前筛选条件</div>
      </div>
      <div class="ct-kpi k-green">
        <div class="k-label">本页在建</div>
        <div class="k-num">{{ kpi.active }}</div>
        <div class="k-note">state=ACTIVE（内部管理态）</div>
      </div>
      <div class="ct-kpi">
        <div class="k-label">本页已关闭</div>
        <div class="k-num">{{ kpi.closed }}</div>
        <div class="k-note">state=CLOSED</div>
      </div>
    </div>

    <div class="ct-panel">
      <div class="mb-3 rounded-md border border-border bg-card px-3.5 pt-1.5 pb-2.5">
        <AttrFilter label="项目编号"><a-input v-model:value="query.projectCode" placeholder="项目编号" style="width: 190px" allow-clear @press-enter="load" /></AttrFilter>
        <AttrFilter label="项目名称（模糊）"><a-input v-model:value="query.projectName" placeholder="项目名称（模糊）" style="width: 220px" allow-clear @press-enter="load" /></AttrFilter>
        <AttrFilter label="地区编码"><a-input v-model:value="query.regionCode" placeholder="地区编码" style="width: 140px" allow-clear @press-enter="load" /></AttrFilter>
        <AttrFilter label="行业分类"><a-input v-model:value="query.industriesType" placeholder="行业分类" style="width: 140px" allow-clear @press-enter="load" /></AttrFilter>
        <AttrFilter
v-model="query.state" label="管理态" :options="[
            { label: '在建（ACTIVE）', value: 'ACTIVE' },
            { label: '已关闭（CLOSED）', value: 'CLOSED' },
          ]" @change="load"
/>
            <div class="mt-2 flex flex-wrap items-center gap-2"><a-button type="primary" @click="load">查询</a-button> <a-button @click="resetQuery">重置</a-button></div>
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
            showTotal: (t: number) => `共 ${t} 条`,
          }"
          :scroll="{ x: 1300 }"
          row-key="id"
          size="middle"
          @change="(p: any) => { query.pageNo = p.current; query.pageSize = p.pageSize; void load(); }"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'projectCode'">
              <div class="ct-id">{{ record.projectCode }}</div>
            </template>
            <template v-else-if="column.key === 'projectName'">{{ record.projectName }}</template>
            <template v-else-if="column.key === 'state'">
              <a-tag :color="(record.state ?? 'ACTIVE') === 'ACTIVE' ? 'green' : 'default'">
                {{ (record.state ?? 'ACTIVE') === 'ACTIVE' ? '在建' : '已关闭' }}
              </a-tag>
            </template>
            <template v-else-if="column.key === 'createTime'">{{ fmtDateTime(record.createTime) }}</template>
            <template v-else-if="column.key === 'action'">
              <a-space size="small">
                <a @click="openTrace(record)">追溯</a>
                <a class="text-blue-600" @click="openEntityDetail({ type: 'PROJECT', code: record.projectCode })">详情 ↗</a>
                <a v-if="can('bidding:filing:create')" @click="openFiling(record)">提交备案</a>
                <a v-if="can('bidding:filing:update')" @click="openReceipt()">登记回执</a>
                <a v-if="can('bidding:project:update')" @click="onEdit(record)">编辑</a>
                <!-- 权限码口径：后端删除端点（POST /bidding/project/delete）用的是 bidding:project:update，
                     按规范 §4.2 复用项目域既有权限点，无独立的 :delete 码；此处若写 :delete 则普通角色永远看不到删除入口 -->
                <a v-if="can('bidding:project:update')" style="color: #c6362e" @click="confirmDelete(record)">删除</a>
              </a-space>
            </template>
          </template>
          <template #emptyText><div class="ct-empty">暂无项目，点右上「登记项目」建档</div></template>
        </a-table>
      </div>
    </div>

    <!-- 追溯抽屉栈：层叠下钻（L1 项目 → L2 招标项目 → L3 标段），逐层内缩 24px + 去遮罩（有层级感）+ 栈语义（关只关当前层，上层保留） -->
    <a-drawer
      v-for="(level, i) in stack"
      :key="level.key"
      :open="level.open"
      class="bid-ind"
      :width="1000 - 24 * i"
      :mask="i === 0"
      :title="level.label || level.queryCode"
      @update:open="(v: boolean) => onDrawerUpdate(level.key, v)"
    >
      <TraceLevelPanel
        :level="level"
        :is-top="i === stack.length - 1"
        @drill="onDrill"
        @detail="openEntityDetail"
        @notice="openNoticeDetail"
        @external="openExternalDetail"
        @close="closeLevel(level.key)"
      />
    </a-drawer>

    <!-- 内部公告详情弹窗（NoticeHtmlView：版式 PDF / 附件 / 招标文件获取指引）。点击 L3 标段/任一层「关联公告」行弹出 -->
    <a-modal
      v-model:open="noticeDetailOpen"
      :title="noticeDetail?.title || '公告详情'"
      :width="900"
      :footer="null"
      destroy-on-close
    >
      <a-spin :spinning="noticeDetailLoading">
        <template v-if="noticeDetail">
          <div class="mb-3 flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
            <span v-if="noticeDetail.noticeCode" class="mono">{{ noticeDetail.noticeCode }}</span>
            <span v-if="noticeDetail.noticeTypeName">{{ noticeDetail.noticeTypeName }}</span>
            <span v-if="noticeDetail.status">{{ noticeDetail.status }}</span>
            <span v-if="noticeDetail.publisherName">{{ noticeDetail.publisherName }}</span>
            <span v-if="noticeDetail.publishTime">发布时间 {{ fmtDateTime(noticeDetail.publishTime) }}</span>
          </div>
          <div class="rounded-md border border-border p-3">
            <NoticeHtmlView :detail="noticeDetail" :show-head="false" />
          </div>
        </template>
        <div v-else-if="!noticeDetailLoading" class="ct-empty">未取到公告详情</div>
      </a-spin>
    </a-modal>

    <!-- 外部公告预览（复用 noticeExternal DetailModal：净化正文 / PDF / 附件病毒状态 / 前台观感；审核权限由 openExternalDetail 判权） -->
    <ExternalDetail />


    <!-- 提交备案（项目编号随行带入；filingType 为业务约定文本） -->
    <a-modal
      v-model:open="filingOpen"
      title="提交项目备案"
      :width="820"
      :confirm-loading="filingSaving"
      ok-text="提交"
      cancel-text="取消"
      destroy-on-close
      @ok="submitFilingForm"
    >
      <a-form layout="vertical">
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="备案类型（必填）">
              <a-input v-model:value="filingForm.filingType" placeholder="如：招标备案 / 中标备案（后端为自由文本）" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="项目编号（必填）">
              <a-input v-model:value="filingForm.projectCode" />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="招标项目编号"><a-input v-model:value="filingForm.tenderProjectCode" /></a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="统一交易码"><a-input v-model:value="filingForm.unifiedDealCode" /></a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="招标方式"><a-input v-model:value="filingForm.tenderMode" /></a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="组织形式"><a-input v-model:value="filingForm.orgForm" /></a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="监管部门编码"><a-input v-model:value="filingForm.superviseDeptCode" /></a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="监管部门名称"><a-input v-model:value="filingForm.superviseDeptName" /></a-form-item>
          </a-col>
          <a-col :span="24">
            <a-form-item label="附件文件 id（逗号分隔）">
              <a-input v-model:value="filingAttachmentText" placeholder="如：301,302" />
            </a-form-item>
          </a-col>
        </a-row>
      </a-form>
      <div class="ct-tip">
        幂等键为「备案类型 + 项目编号」，重复提交后端返回 FILING_ALREADY_SUBMITTED；提交时间由服务端取当前时间。
      </div>
    </a-modal>

    <!-- 备案回执登记 -->
    <a-modal
      v-model:open="receiptOpen"
      title="登记备案回执"
      :width="620"
      :confirm-loading="receiptSaving"
      ok-text="登记"
      cancel-text="取消"
      destroy-on-close
      @ok="submitReceipt"
    >
      <a-form layout="vertical">
        <a-form-item label="受理号（必填）">
          <a-input v-model:value="receiptForm.receiptNo" placeholder="提交备案时后端返回的 receiptNo" />
        </a-form-item>
        <a-form-item label="回推结果（必填）">
          <!-- 优先走字典 filing_status（可后台动态调整文案）；FILING_STATUS_OPTIONS 仅作字典尚未下发环境的兜底 -->
          <a-select
            v-model:value="receiptForm.resultStatus"
            :options="dictOptions('filing_status').length > 0 ? dictOptions('filing_status') : FILING_STATUS_OPTIONS"
            placeholder="请选择"
          />
        </a-form-item>
        <a-form-item label="受理意见">
          <a-textarea v-model:value="receiptForm.opinion" :rows="3" placeholder="如：材料齐全，予以受理" />
        </a-form-item>
      </a-form>
      <div class="ct-tip">回执状态码表逐字取自 FilingStatusEnum；登记后「追溯」里的备案一栏即为最新状态。</div>
    </a-modal>

    <!-- 登记 / 编辑 -->
    <a-modal
      v-model:open="formOpen"
      :title="isEdit ? `编辑项目 #${form.id ?? ''}` : '登记项目'"
      :width="820"
      :confirm-loading="saving"
      ok-text="保存"
      cancel-text="取消"
      destroy-on-close
      @ok="submit"
    >
      <a-form ref="formRef" :model="form" :rules="rules" layout="vertical">
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="项目编号" name="projectCode">
              <a-input v-model:value="form.projectCode" placeholder="唯一编号，用于与公告/标段关联" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="项目名称" name="projectName">
              <a-input v-model:value="form.projectName" />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="地区编码" name="regionCode"><a-input v-model:value="form.regionCode" /></a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="法人" name="legalPerson"><a-input v-model:value="form.legalPerson" /></a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="行业分类" name="industriesType"><a-input v-model:value="form.industriesType" /></a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="投资代码" name="investProjectCode">
              <a-input v-model:value="form.investProjectCode" />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="审批部门" name="approvalDeptName">
              <a-input v-model:value="form.approvalDeptName" />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="审批文号" name="approvalDocNo"><a-input v-model:value="form.approvalDocNo" /></a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="资金来源" name="fundSource"><a-input v-model:value="form.fundSource" /></a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="出资规模" name="contributionScale">
              <a-input v-model:value="form.contributionScale" />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="管理态" name="state">
              <a-select
                v-model:value="form.state"
                :options="[
                  { label: '在建（ACTIVE）', value: 'ACTIVE' },
                  { label: '已关闭（CLOSED）', value: 'CLOSED' },
                ]"
              />
            </a-form-item>
          </a-col>
          <a-col :span="24">
            <a-form-item label="项目规模" name="projectScale">
              <a-textarea v-model:value="form.projectScale" :rows="2" />
            </a-form-item>
          </a-col>
          <a-col :span="24">
            <a-form-item label="备注" name="remark"><a-textarea v-model:value="form.remark" :rows="2" /></a-form-item>
          </a-col>
        </a-row>
      </a-form>
    </a-modal>
  </div>
</template>
