<script setup lang="ts">
/* 项目台账（菜单 6771 / component bidding/project/index / componentName BiddingProject / 权限 bidding:project:query）
 * 树形菜单（2026-10-02 用户重定稿）：
 *   ◾ 四级一次性展示：项目 ▸ 招标项目 ▸ 标段 ▸ 公告（traceProject 一次取回全量，前端按 code 关联成树）
 *   ◾ 每行两个动作：详细（进实体详情页 看+编辑，附件在详情页）/ 更多 ▾（其余功能收进下拉：备案/回执/删除）
 *   ◾ 点行标题或箭头展开/收起下级；默认全部展开，一眼看到公告
 * 契约：项目域接口（api/bidding/project）+ 招标全流程状态机（契约 26/28，ProjectStatusEnum 5 态）
 * 口径：
 *   · 时间字段为 epoch 毫秒 → fmtDateTime；state 展示走 project_status 字典（禁止硬编码）；
 *   · trace 返回内部公告无 bidSectionCode（标段级精确归属待后端补齐，契约 2026-10-02）→
 *     内部公告当前挂招标项目层；外部公告按 bidSectionCodes 挂标段层。 */
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';

import { useAccess } from '@vben/access';
import { useAccessStore } from '@vben/stores';

import { message, Modal } from 'ant-design-vue';

import { FILING_STATUS_OPTIONS, type FilingApi, receiveFilingResult, submitFiling } from '#/api/bidding/filing';
import {
  deleteProject,
  deleteTenderProject,
  getProjectPage,
  traceProject,
  type ProjectApi,
} from '#/api/bidding/project';

import { useBiddingDraftStore } from '#/store/bidding-draft';

import { fmtDateTime } from '../certificate/format';
import { useCertMeta } from '../certificate/useCertMeta';
import { loadFlowDicts, statusColorOf, statusOptionsOf, statusTextOf } from '#/api/bidding/flow/labels';
import AttrFilter from '../components/AttrFilter.vue';
import DepositPanel from './modules/DepositPanel.vue';
import ContractPanel from './modules/ContractPanel.vue';
import ReportPanel from './modules/ReportPanel.vue';
import ObjectionPanel from './modules/ObjectionPanel.vue';
import TenderDocPanel from './modules/TenderDocPanel.vue';
import PrequalPanel from './modules/PrequalPanel.vue';
import CaSealPanel from './modules/CaSealPanel.vue';
import KeyPostPanel from './modules/KeyPostPanel.vue';

import '../certificate/cert.css';

const router = useRouter();
const draftStore = useBiddingDraftStore();

const { hasAccessByCodes } = useAccess();
const accessStore = useAccessStore();
const { dictOptions } = useCertMeta();

/** 权限判定：权限码未下发时放行（最终由后端 @PreAuthorize 裁决） */
function can(code: string): boolean {
  const codes = accessStore.accessCodes ?? [];
  if (codes.length === 0) return true;
  return codes.includes('*') || codes.includes('*:*:*') || hasAccessByCodes([code]);
}

const query = reactive<ProjectApi.PageParams>({
  pageNo: 1,
  pageSize: 500,
  projectCode: undefined,
  projectName: undefined,
  regionCode: undefined,
  industriesType: undefined,
  state: undefined,
});
const rows = ref<ProjectApi.Project[]>([]);
const total = ref(0);
const loading = ref(false);

/* ================= 树形菜单（四级） ================= */
type TreeKind = 'BID_SECTION' | 'NOTICE' | 'NOTICE_EXTERNAL' | 'PROJECT' | 'TENDER_PROJECT';

interface TreeRow {
  key: string;
  kind: TreeKind;
  code: string;
  title: string;
  sub: string;
  state?: string;
  open: boolean;
  children: TreeRow[];
  raw?: unknown;
  /** 内存草稿行（未落库）：详细 → create 态编辑 */
  isDraft?: boolean;
  /** 草稿/新建态跳转 query（mode=create + 上级编号） */
  createQuery?: Record<string, string>;
  /** 父节点（取上级名称/编号，供公告新建 query） */
  parent?: TreeRow;
}

const projectTree = ref<TreeRow[]>([]);

/** 把 trace 结果按 code 关联成 项目→招标项目→标段/公告 树 */
function buildTree(t: ProjectApi.Trace): TreeRow[] {
  const tps: TreeRow[] = (t.tenderProjects ?? []).map((tp) => ({
    key: `TP_${tp.tenderProjectCode}`,
    kind: 'TENDER_PROJECT',
    code: tp.tenderProjectCode ?? '',
    title: tp.tenderProjectName ?? tp.tenderProjectCode ?? '（未命名招标项目）',
    sub: tp.tenderProjectCode ?? '',
    open: true,
    children: [],
    raw: tp,
  }));
  const findTp = (code?: string) => tps.find((x) => code && x.code === code);
  // 标段挂招标项目；孤儿标段挂项目级
  for (const bs of t.sections ?? []) {
    const row: TreeRow = {
      key: `BS_${bs.bidSectionCode}`,
      kind: 'BID_SECTION',
      code: bs.bidSectionCode ?? '',
      title: bs.bidSectionName ?? bs.bidSectionCode ?? '（未命名标段）',
      sub: bs.bidSectionCode ?? '',
      open: true,
      children: [],
      raw: bs,
    };
    const host = findTp(bs.tenderProjectCode);
    if (host) host.children.push(row);
    else tps.push(row);
  }
  // 内部公告：数据层 bidding_notice 仅关联 project_code（无标段字段）→ 挂招标项目/项目层。
  // 详情接口 /bidding/notice/get 按雪花 id（19 位字符串）取 → code 必须用 id，不能用 noticeCode。
  for (const n of t.notices ?? []) {
    const row: TreeRow = {
      key: `N_${n.id ?? n.noticeCode}`,
      kind: 'NOTICE',
      code: String(n.id ?? n.noticeCode ?? ''),
      title: n.title ?? n.noticeCode ?? '（未命名公告）',
      sub: n.noticeTypeName ?? '',
      open: false,
      children: [],
      raw: n,
    };
    const host = findTp(n.tenderProjectCode);
    if (host) host.children.push(row);
    else tps.push(row);
  }
  // 外部公告：公告属于招标项目（可关联多标段）→ 挂招标项目层；无归属则挂项目层
  for (const en of t.externalNotices ?? []) {
    const row: TreeRow = {
      key: `NE_${en.id ?? en.sourceCode}`,
      kind: 'NOTICE_EXTERNAL',
      code: String(en.id ?? en.sourceCode ?? ''),
      title: en.title ?? en.sourceCode ?? '（外部公告）',
      sub: '外部' + (en.sourceName ? ' · ' + en.sourceName : ''),
      open: false,
      children: [],
      raw: en,
    };
    const host = findTp((en as unknown as { tenderProjectCode?: string }).tenderProjectCode);
    if (host) host.children.push(row); else tps.push(row);
  }
  return tps;
}

/** 分页拉全量项目（后端单页上限 200，循环取完） */
async function fetchAllProjects(): Promise<ProjectApi.Project[]> {
  const acc: ProjectApi.Project[] = [];
  const SIZE = 200;
  for (let pageNo = 1; ; pageNo += 1) {
    const page = await getProjectPage({ ...query, pageNo, pageSize: SIZE });
    const list = page.list ?? [];
    acc.push(...list);
    if (acc.length >= (page.total ?? 0) || list.length === 0) break;
  }
  return acc;
}

async function loadTree(): Promise<void> {
  loading.value = true;
  try {
    const all = await fetchAllProjects();
    rows.value = all;
    total.value = all.length;
    const nodes: TreeRow[] = [];
    for (const p of all) {
      const node: TreeRow = {
        key: `P_${p.projectCode}`,
        kind: 'PROJECT',
        code: p.projectCode ?? '',
        title: p.projectName ?? p.projectCode ?? '（未命名项目）',
        sub: p.projectCode ?? '',
        state: p.state,
        open: true,
        children: [],
        raw: p,
      };
      try {
        const t = await traceProject(p.projectCode ?? '');
        node.children = buildTree(t);
      } catch {
        /* 单项目追溯失败不阻断整体；行内无下级 */
      }
      nodes.push(node);
    }
    projectTree.value = nodes;
    for (const n of nodes) linkParents(n.children, n);
    attachDrafts();
    void setAll(true);
  } catch {
    projectTree.value = [];
    total.value = 0;
  } finally {
    loading.value = false;
  }
}

function load(): void {
  void loadTree();
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

function toggleOpen(r: TreeRow): void {
  r.open = !r.open;
}

function setAll(open: boolean): void {
  const walk = (list: TreeRow[]) => list.forEach((r) => { r.open = open; walk(r.children); });
  walk(projectTree.value);
}

/** 从树行跳详情页（页面阶梯，非弹窗）：entityDetail /bidding/entity/:type/:code */
function goDetail(kind: TreeKind, code: string): void {
  if (!code) { message.warning('缺少编码，无法进入详情'); return; }
  void router.push(`/bidding/entity/${kind}/${encodeURIComponent(code)}`);
}

/** 递归补 parent 链（供草稿新建取上级名称） */
function linkParents(list: TreeRow[], parent: TreeRow): void {
  for (const n of list) { n.parent = parent; linkParents(n.children, n); }
}

/** 把内存草稿挂进树（未落库草稿：建完后进 create 态编辑，保存落库→刷新成正式行；删除→移出内存） */
function attachDrafts(): void {
  for (const p of projectTree.value) {
    for (const d of draftStore.tenderProjectDrafts[p.code] ?? []) {
      if (p.children.some((c) => c.code === d.tempCode)) continue;
      p.children.push({
        key: `TPD_${d.tempCode}`,
        kind: 'TENDER_PROJECT',
        code: d.tempCode,
        title: d.name || d.fields.TENDER_PROJECT_NAME || '（待完善）',
        sub: '草稿',
        open: false,
        children: [],
        isDraft: true,
        createQuery: { mode: 'create', projectCode: p.code },
        parent: p,
      });
    }
    for (const tp of p.children.filter((c) => c.kind === 'TENDER_PROJECT' && !c.isDraft)) {
      for (const d of draftStore.bidSectionDrafts[tp.code] ?? []) {
        if (tp.children.some((c) => c.code === d.tempCode)) continue;
        tp.children.push({
          key: `BSD_${d.tempCode}`,
          kind: 'BID_SECTION',
          code: d.tempCode,
          title: d.name || d.fields.BID_SECTION_NAME || '（待完善）',
          sub: '草稿',
          open: false,
          children: [],
          isDraft: true,
          createQuery: { mode: 'create', tenderProjectCode: tp.code },
          parent: tp,
        });
      }
      // 公告草稿（父级 = 招标项目）：挂招标项目层，与正式公告并列
      for (const d of draftStore.noticeDrafts[tp.code] ?? []) {
        if (tp.children.some((c) => c.code === d.tempCode)) continue;
        tp.children.push({
          key: `ND_${d.tempCode}`,
          kind: 'NOTICE',
          code: d.tempCode,
          title: d.name || '（待完善）',
          sub: '草稿',
          open: false,
          children: [],
          isDraft: true,
          createQuery: {
            mode: 'create',
            projectCode: p.code,
            projectName: p.title,
            tenderProjectCode: tp.code,
            tenderProjectName: tp.title,
            bidSectionCode: '',
            bidSectionName: '',
          },
          parent: tp,
        });
      }
    }
  }
}

/* ================= 树外新建下级（进 create 态详情页编辑，保存后方才落库） ================= */
function handleCreateTender(p: TreeRow): void {
  const tempCode = `DRAFT_${Date.now()}`;
  draftStore.addTenderProjectDraft(p.code, {
    tempCode, parentCode: p.code, parentType: 'PROJECT', name: '', createdAt: String(Date.now()), fields: {},
  });
  void router.push({ path: `/bidding/entity/TENDER_PROJECT/${encodeURIComponent(tempCode)}`, query: { mode: 'create', projectCode: p.code } });
}

function handleCreateSection(tp: TreeRow): void {
  const tempCode = `DRAFT_${Date.now()}`;
  draftStore.addBidSectionDraft(tp.code, {
    tempCode, parentCode: tp.code, parentType: 'TENDER_PROJECT', name: '', createdAt: String(Date.now()), fields: {},
  });
  void router.push({ path: `/bidding/entity/BID_SECTION/${encodeURIComponent(tempCode)}`, query: { mode: 'create', tenderProjectCode: tp.code } });
}

/** 招标项目行「＋ 新建公告」：公告挂招标项目下（规范锚点 TENDER_PROJECT_CODE，可关联多标段） */
function handleCreateNotice(tp: TreeRow): void {
  const tempCode = `DRAFT_${Date.now()}`;
  const p = tp.parent; // 招标项目 → 项目
  draftStore.addNoticeDraft(tp.code, {
    tempCode, parentCode: tp.code, parentType: 'TENDER_PROJECT', name: '', createdAt: String(Date.now()), fields: {},
  });
  void router.push({
    path: `/bidding/entity/NOTICE/${encodeURIComponent(tempCode)}`,
    query: {
      mode: 'create',
      projectCode: p?.code ?? '',
      projectName: p?.title ?? '',
      tenderProjectCode: tp.code,
      tenderProjectName: tp.title,
      bidSectionCode: '',
      bidSectionName: '',
    },
  });
}

/** 行「详细」：草稿 → create 态；正式 → 详情页 */
function openDetail(r: TreeRow): void {
  if (r.isDraft) {
    void router.push({ path: `/bidding/entity/${r.kind}/${encodeURIComponent(r.code)}`, query: r.createQuery });
    return;
  }
  void goDetail(r.kind, r.code);
}

/** 公告行「详细」：草稿 → create 态；正式 → 公告正文/审核预览 */
function openNoticeDetail(n: TreeRow): void {
  if (n.isDraft) {
    void router.push({ path: `/bidding/entity/NOTICE/${encodeURIComponent(n.code)}`, query: n.createQuery });
    return;
  }
  void goNoticeDetail(n);
}

/** 删除树内草稿（零后端调用） */
function removeDraft(r: TreeRow): void {
  Modal.confirm({
    title: '确认删除该草稿？',
    content: '草稿未保存到系统，删除后不可恢复。',
    okText: '确认删除',
    okType: 'danger',
    onOk() {
      const parentCode = r.parent?.code ?? '';
      if (r.kind === 'TENDER_PROJECT') draftStore.removeTenderProjectDraft(parentCode, r.code);
      else if (r.kind === 'BID_SECTION') draftStore.removeBidSectionDraft(parentCode, r.code);
      else if (r.kind === 'NOTICE') draftStore.removeNoticeDraft(parentCode, r.code);
      void load();
      message.success('已删除');
    },
  });
}

/** 公告行详细：内部公告走 notice 详情（雪花 id），外部公告走审核预览（id） */
function goNoticeDetail(n: TreeRow): void {
  void goDetail(n.kind === 'NOTICE_EXTERNAL' ? 'NOTICE_EXTERNAL' : 'NOTICE', n.code);
}

/** 公告行「更多 → 公告台账」：进入公告/公示 发布工作台（台账 + 起草→发布→撤下，与公告详情页分离） */
function openNoticeLedger(): void {
  void router.push('/bidding/notice');
}

/* ================= 新建 / 草稿 ================= */
function handleCreateProject(): void {
  const tempCode = `DRAFT_${Date.now()}`;
  draftStore.addProjectDraft({
    tempCode,
    parentCode: '',
    parentType: 'PROJECT',
    name: '',
    createdAt: String(Date.now()),
    fields: {},
  });
  void router.push({ path: `/bidding/entity/PROJECT/${encodeURIComponent(tempCode)}`, query: { mode: 'create' } });
}

function removeProjectDraftRow(tempCode: string): void {
  Modal.confirm({
    title: '确认删除该草稿？',
    content: '草稿未保存到系统，删除后不可恢复。',
    okText: '确认删除',
    okType: 'danger',
    onOk() {
      draftStore.removeProjectDraft(tempCode);
      message.success('已删除');
    },
  });
}

/* ================= 项目备案：提交 / 回执登记 ================= */
const filingOpen = ref(false);
const filingSaving = ref(false);
const filingForm = reactive<FilingApi.SubmitReq>({ filingType: '', projectCode: '' });
const filingAttachmentText = ref('');

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
    receiptForm.receiptNo = resp?.receiptNo ?? '';
  } catch {
    /* 静默：重复提交由后端返回 FILING_ALREADY_SUBMITTED */
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
    /* 静默 */
  } finally {
    receiptSaving.value = false;
  }
}

/* ================= 删除（项目 / 招标项目 / 标段） ================= */
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

function confirmDeleteTender(tp: ProjectApi.TenderProject) {
  Modal.confirm({
    title: '删除招标项目',
    okText: '删除',
    content: `确认删除招标项目「${tp.tenderProjectName}（${tp.tenderProjectCode}）」？`,
    okType: 'danger',
    async onOk() {
      await deleteTenderProject(tp.id as number);
      message.success('已删除');
      void load();
    },
  });
}

onMounted(() => {
  void loadFlowDicts();
  void load();
});

/* 状态分布（全量） */
const kpi = computed(() => {
  const list = rows.value;
  const draft = list.filter((r) => (r.state ?? '') === 'DRAFT').length;
  const terminalSet = ['COMPLETED', 'CANCELLED'];
  const terminal = list.filter((r) => terminalSet.includes(r.state ?? '')).length;
  return { draft, terminal, active: list.length - draft - terminal };
});

/* ================= 子域台账抽屉 ================= */
const depositOpen = ref(false);
const contractOpen = ref(false);
const reportOpen = ref(false);
const objectionOpen = ref(false);
const tenderDocOpen = ref(false);
const prequalOpen = ref(false);
const prequalTenderCode = ref('');
const caSealOpen = ref(false);
const keyPostOpen = ref(false);

/** 资格预审（实体化入口，用户 2026-10-02）：从招标项目行「更多」打开，自动带入该招标项目上下文；
 *  只在该招标项目审查方式=资格预审(前审)时出现（qulificationCheckMode==='1'）。 */
function openPrequal(tp: TreeRow): void {
  prequalTenderCode.value = tp.code || '';
  prequalOpen.value = true;
}
</script>

<template>
  <div class="ct-page">
    <div class="ct-head">
      <div>
        <div class="ct-title">项目台账</div>
        <div class="ct-sub">多级菜单：项目 ▸ 招标项目 ▸ 标段 ▸ 公告；点行标题展开下级，「详细」进详情页，「更多」收其余操作</div>
      </div>
      <div class="ct-actions">
        <a-button v-if="can('bidding:project:create')" type="primary" @click="handleCreateProject">登记项目</a-button>
        <a-button @click="setAll(true)">全部展开</a-button>
        <a-button @click="setAll(false)">全部收起</a-button>
        <a-button @click="load">刷新</a-button>
        <!-- 流程子域台账：按钮权限点均挂本页 6771，按权限码显隐 -->
        <a-dropdown v-if="can('bidding:deposit:query') || can('bidding:contract:query') || can('bidding:report:query') || can('bidding:objection:query') || can('bidding:tender-doc:query') || can('bidding:ca:query')">
          <a-button>子域台账 ▾</a-button>
          <template #overlay>
            <div class="rounded-md border border-border bg-card py-1 shadow-lg">
              <div class="px-4 pt-2 pb-1 text-xs font-medium tracking-wide text-muted-foreground uppercase">流程子域台账</div>
              <div v-if="can('bidding:deposit:query')" class="cursor-pointer px-4 py-2 text-sm font-medium text-foreground hover:bg-accent" @click="depositOpen = true">投标保证金台账</div>
              <div v-if="can('bidding:contract:query')" class="cursor-pointer px-4 py-2 text-sm font-medium text-foreground hover:bg-accent" @click="contractOpen = true">工程合同台账</div>
              <div v-if="can('bidding:report:query')" class="cursor-pointer px-4 py-2 text-sm font-medium text-foreground hover:bg-accent" @click="reportOpen = true">定标书面报告台账</div>
              <div v-if="can('bidding:objection:query')" class="cursor-pointer px-4 py-2 text-sm font-medium text-foreground hover:bg-accent" @click="objectionOpen = true">投标异议台账</div>
              <div v-if="can('bidding:tender-doc:query')" class="cursor-pointer px-4 py-2 text-sm font-medium text-foreground hover:bg-accent" @click="tenderDocOpen = true">招标文件下载名单</div>
              <!-- 资格预审已收归招标项目行「更多」（实体化，2026-10-02），不再作为全局台账 -->
              <div v-if="can('bidding:ca:query')" class="cursor-pointer px-4 py-2 text-sm font-medium text-foreground hover:bg-accent" @click="caSealOpen = true">CA 签章台账</div>
              <div v-if="can('bidding:project:query')" class="cursor-pointer px-4 py-2 text-sm font-medium text-foreground hover:bg-accent" @click="keyPostOpen = true">项目关键岗位变更</div>
            </div>
          </template>
        </a-dropdown>
      </div>
    </div>

    <div class="ct-kpis">
      <div class="ct-kpi k-blue">
        <div class="k-label">项目总数</div>
        <div class="k-num">{{ total }}</div>
        <div class="k-note">按当前筛选条件</div>
      </div>
      <div class="ct-kpi k-blue">
        <div class="k-label">草稿</div>
        <div class="k-num">{{ kpi.draft }}</div>
        <div class="k-note">DRAFT（待提交审批）</div>
      </div>
      <div class="ct-kpi k-green">
        <div class="k-label">进行中</div>
        <div class="k-num">{{ kpi.active }}</div>
        <div class="k-note">审批中 / 已立项</div>
      </div>
      <div class="ct-kpi">
        <div class="k-label">终态</div>
        <div class="k-num">{{ kpi.terminal }}</div>
        <div class="k-note">已完成 / 已取消</div>
      </div>
    </div>

    <div class="ct-panel">
      <div class="mb-3 rounded-md border border-border bg-card px-3.5 pt-1.5 pb-2.5">
        <AttrFilter label="项目编号"><a-input v-model:value="query.projectCode" placeholder="项目编号" style="width: 190px" allow-clear @press-enter="load" /></AttrFilter>
        <AttrFilter label="项目名称（模糊）"><a-input v-model:value="query.projectName" placeholder="项目名称（模糊）" style="width: 220px" allow-clear @press-enter="load" /></AttrFilter>
        <AttrFilter label="地区编码"><a-input v-model:value="query.regionCode" placeholder="地区编码" style="width: 140px" allow-clear @press-enter="load" /></AttrFilter>
        <AttrFilter label="行业分类"><a-input v-model:value="query.industriesType" placeholder="行业分类" style="width: 140px" allow-clear @press-enter="load" /></AttrFilter>
        <AttrFilter v-model="query.state" label="状态" :options="statusOptionsOf('PROJECT')" @change="load" />
        <div class="mt-2 flex flex-wrap items-center gap-2">
          <a-button type="primary" @click="load">查询</a-button>
          <a-button @click="resetQuery">重置</a-button>
        </div>
      </div>

      <!-- 未落库草稿（内存）：树顶单独列出 -->
      <div v-if="draftStore.projectDrafts.length" class="ct-panel-b mb-3">
        <div v-for="d in draftStore.projectDrafts" :key="`draft_${d.tempCode}`" class="tree-row draft-row" style="padding-left: 8px">
          <a-tag class="draft-tag">草稿</a-tag>
          <span class="tree-title">{{ d.name || d.fields.PROJECT_NAME || '（待完善）' }}</span>
          <span class="tree-sub">{{ d.tempCode }}</span>
          <span class="tree-ops">
            <a class="text-blue-600" @click="goDetail('PROJECT', d.tempCode)">详细</a>
            <a class="text-red-600" @click="removeProjectDraftRow(d.tempCode)">删除</a>
          </span>
        </div>
      </div>

      <div class="ct-panel-b">
        <div v-if="loading" class="ct-empty">加载中…</div>
        <div v-else-if="projectTree.length === 0" class="ct-empty">暂无项目，点右上「登记项目」建档</div>

        <!-- L1 项目 -->
        <div v-for="p in projectTree" :key="p.key" class="tree-node">
          <div class="tree-row" style="padding-left: 8px">
            <span class="tree-sw" @click="toggleOpen(p)">{{ p.open ? '▾' : '▸' }}</span>
            <a-tag color="blue">项目</a-tag>
            <span class="tree-title" @click="toggleOpen(p)">{{ p.title }}</span>
            <span class="tree-sub">{{ p.sub }}<span v-if="p.state" class="tree-state"> · {{ statusTextOf('PROJECT', p.state) }}</span></span>
            <span class="tree-ops">
              <a class="text-blue-600" @click="goDetail('PROJECT', p.code)">详细</a>
              <a-dropdown>
                <a class="text-gray-500">更多 ▾</a>
                <template #overlay>
                  <div class="more-menu">
                    <div @click="handleCreateTender(p)">＋ 新建招标项目</div>
                    <div v-if="can('bidding:filing:create')" @click="openFiling(p.raw as any)">提交备案</div>
                    <div v-if="can('bidding:filing:update')" @click="openReceipt()">登记回执</div>
                    <div v-if="can('bidding:project:update')" class="more-danger" @click="confirmDelete(p.raw as any)">删除项目</div>
                  </div>
                </template>
              </a-dropdown>
            </span>
          </div>

          <!-- L2 招标项目 -->
          <div v-if="p.open" class="tree-children">
            <div v-for="tp in p.children.filter((c) => c.kind === 'TENDER_PROJECT')" :key="tp.key" class="tree-node">
              <div class="tree-row" style="padding-left: 40px">
                <span class="tree-sw" @click="toggleOpen(tp)">{{ tp.open ? '▾' : '▸' }}</span>
                <a-tag color="cyan">招标项目</a-tag>
                <span class="tree-title" @click="toggleOpen(tp)">{{ tp.title }}</span>
                <span class="tree-sub">{{ tp.sub }}</span>
                <span class="tree-ops">
                  <a class="text-blue-600" @click="openDetail(tp)">详细</a>
                  <a-dropdown>
                    <a class="text-gray-500">更多 ▾</a>
                    <template #overlay>
                      <div class="more-menu">
                        <div v-if="!tp.isDraft" @click="handleCreateSection(tp)">＋ 新建标段</div>
                        <div v-if="!tp.isDraft" @click="handleCreateNotice(tp)">＋ 新建公告</div>
                        <div v-if="!tp.isDraft && (tp.raw as any)?.qulificationCheckMode === '1'" @click="openPrequal(tp)">资格预审</div>
                        <div v-if="tp.isDraft" class="more-danger" @click="removeDraft(tp)">删除草稿</div>
                      </div>
                    </template>
                  </a-dropdown>
                </span>
              </div>

              <!-- L3 标段 -->
              <div v-if="tp.open" class="tree-children">
                <div v-for="bs in tp.children.filter((c) => c.kind === 'BID_SECTION')" :key="bs.key" class="tree-node">
                  <div class="tree-row" style="padding-left: 72px">
                    <span class="tree-sw" @click="toggleOpen(bs)">{{ bs.open ? '▾' : '▸' }}</span>
                    <a-tag>标段</a-tag>
                    <span class="tree-title" @click="toggleOpen(bs)">{{ bs.title }}</span>
                    <span class="tree-sub">{{ bs.sub }}</span>
                    <span class="tree-ops">
                      <a class="text-blue-600" @click="openDetail(bs)">详细</a>
                      <a-dropdown v-if="bs.isDraft">
                        <a class="text-gray-500">更多 ▾</a>
                        <template #overlay>
                          <div class="more-menu">
                            <div class="more-danger" @click="removeDraft(bs)">删除草稿</div>
                          </div>
                        </template>
                      </a-dropdown>
                    </span>
                  </div>
                </div>
                <!-- 招标项目层公告（内部公告无标段归属，挂本层；标段级精确关联待后端契约补齐） -->
                <div v-for="n in tp.children.filter((c) => c.kind === 'NOTICE')" :key="n.key" class="tree-row" style="padding-left: 72px">
                  <span class="tree-sw tree-sw-empty"></span>
                  <a-tag color="purple" :class="{ 'draft-tag': n.isDraft }">{{ n.isDraft ? '公告草稿' : '公告' }}</a-tag>
                  <span class="tree-title">{{ n.title }}</span>
                  <span class="tree-sub">{{ n.sub }}</span>
                  <span class="tree-ops">
                    <a class="text-blue-600" @click="openNoticeDetail(n)">详细</a>
                    <a-dropdown>
                      <a class="text-gray-500">更多 ▾</a>
                      <template #overlay>
                        <div class="more-menu">
                          <div @click="openNoticeLedger">公告台账</div>
                          <div v-if="n.isDraft" class="more-danger" @click="removeDraft(n)">删除草稿</div>
                        </div>
                      </template>
                    </a-dropdown>
                  </span>
                </div>
                <div v-if="tp.children.length === 0" class="tree-row tree-row-empty" style="padding-left: 40px">该招标项目暂无标段与公告</div>
              </div>
            </div>

            <!-- 孤儿标段（无招标项目归属，挂项目层；含其公告） -->
            <div v-for="bs in p.children.filter((c) => c.kind === 'BID_SECTION')" :key="bs.key" class="tree-node">
              <div class="tree-row" style="padding-left: 40px">
                <span class="tree-sw" @click="toggleOpen(bs)">{{ bs.open ? '▾' : '▸' }}</span>
                <a-tag>标段</a-tag>
                <span class="tree-title" @click="toggleOpen(bs)">{{ bs.title }}</span>
                <span class="tree-sub">{{ bs.sub }}</span>
                <span class="tree-ops">
                  <a class="text-blue-600" @click="openDetail(bs)">详细</a>
                  <a-dropdown v-if="bs.isDraft">
                    <a class="text-gray-500">更多 ▾</a>
                    <template #overlay>
                      <div class="more-menu">
                        <div class="more-danger" @click="removeDraft(bs)">删除草稿</div>
                      </div>
                    </template>
                  </a-dropdown>
                </span>
              </div>
            </div>

            <!-- 孤儿公告（内部公告无招标项目归属，挂项目层） -->
            <div v-for="n in p.children.filter((c) => c.kind === 'NOTICE')" :key="n.key" class="tree-row" style="padding-left: 40px">
              <span class="tree-sw tree-sw-empty"></span>
              <a-tag color="purple" :class="{ 'draft-tag': n.isDraft }">{{ n.isDraft ? '公告草稿' : '公告' }}</a-tag>
              <span class="tree-title">{{ n.title }}</span>
              <span class="tree-sub">{{ n.sub }}</span>
              <span class="tree-ops">
                <a class="text-blue-600" @click="openNoticeDetail(n)">详细</a>
                <a-dropdown>
                  <a class="text-gray-500">更多 ▾</a>
                  <template #overlay>
                    <div class="more-menu">
                      <div @click="openNoticeLedger">公告台账</div>
                      <div v-if="n.isDraft" class="more-danger" @click="removeDraft(n)">删除草稿</div>
                    </div>
                  </template>
                </a-dropdown>
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 提交备案 -->
    <a-modal v-model:open="filingOpen" title="提交项目备案" :width="820" :confirm-loading="filingSaving" ok-text="提交" cancel-text="取消" destroy-on-close @ok="submitFilingForm">
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
    <a-modal v-model:open="receiptOpen" title="登记备案回执" :width="620" :confirm-loading="receiptSaving" ok-text="登记" cancel-text="取消" destroy-on-close @ok="submitReceipt">
      <a-form layout="vertical">
        <a-form-item label="受理号（必填）">
          <a-input v-model:value="receiptForm.receiptNo" placeholder="备案提交后返回的受理号" />
        </a-form-item>
        <a-form-item label="回推结果（必填）">
          <a-select v-model:value="receiptForm.resultStatus" :options="FILING_STATUS_OPTIONS" placeholder="选择备案回推结果" />
        </a-form-item>
        <a-form-item label="意见">
          <a-textarea v-model:value="receiptForm.opinion" :rows="3" placeholder="审批意见（可选）" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 子域台账抽屉 -->
    <DepositPanel v-model:open="depositOpen" />
    <ContractPanel v-model:open="contractOpen" />
    <ReportPanel v-model:open="reportOpen" />
    <ObjectionPanel v-model:open="objectionOpen" />
    <TenderDocPanel v-model:open="tenderDocOpen" />
    <PrequalPanel v-model:open="prequalOpen" :tender-project-code="prequalTenderCode" />
    <CaSealPanel v-model:open="caSealOpen" />
    <KeyPostPanel v-model:open="keyPostOpen" />
  </div>
</template>

<style scoped>
/* 树行基本样式 */
.tree-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-top: 5px;
  padding-bottom: 5px;
  border-bottom: 1px solid #f0f0f0;
}
.tree-row:hover {
  background: #fafafa;
}
.tree-sw {
  width: 16px;
  text-align: center;
  cursor: pointer;
  color: #8c8c8c;
  user-select: none;
  flex-shrink: 0;
}
.tree-sw-empty {
  cursor: default;
}
.tree-title {
  font-weight: 500;
  cursor: pointer;
  color: #1f2937;
}
.tree-title:hover {
  color: #2563eb;
}
.tree-sub {
  color: #8c8c8c;
  font-size: 12px;
}
.tree-state {
  color: #8c8c8c;
}
.tree-ops {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 14px;
  white-space: nowrap;
  padding-right: 8px;
}
.tree-row-empty {
  color: #bfbfbf;
  font-size: 12px;
}
.more-menu > div {
  padding: 7px 18px;
  font-size: 13px;
  cursor: pointer;
  color: #1f2937;
}
.more-menu > div:hover {
  background: #f5f5f5;
}
.more-menu .more-danger {
  color: #c6362e;
}
.more-menu .more-muted {
  color: #bfbfbf;
  cursor: default;
}
.more-menu .more-muted:hover {
  background: transparent;
}
.tree-children {
  border-left: 2px solid #d9d9d9;
  margin-left: 14px;
  padding-left: 10px;
  border-radius: 4px;
}
/* 草稿 */
.draft-row {
  background: #fffdf5;
}
.draft-tag.ant-tag {
  color: #d97706;
  background: #fff7ed;
  border-color: #f59e0b;
}
</style>
