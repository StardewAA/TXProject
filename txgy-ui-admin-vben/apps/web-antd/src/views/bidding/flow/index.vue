<script lang="ts" setup>
import type { AttachmentRecord } from '../vo/attachmentStages';

/* ============================================================================
  招标全流程 · 审批工作台（菜单 60005604 / component bidding/flow/index / 权限 bidding:flow:query|transition|approval）
  【2026-09-28 拆分】本页原挂「投标流程跟踪」菜单（6748）实为政府审核工作台，按用户裁定拆出：
    审批工作台另起菜单项（本页，挂 招标全流程 6800 目录下）；「投标流程跟踪」菜单恢复为投标意向总览（processTrack/index.vue，全租户投标流程管理）。
  契约：txgy-module-bidding flow 域（FlowTransitionController + GovernmentApprovalController）：
    · GET  /bidding/flow/next-actions  当前状态可执行动作（label 已由后端给中文）
    · POST /bidding/flow/transition    状态流转执行（bizType + bizCode + event + remark；无 id 直填）
    · GET  /bidding/flow/timers        时间驱动调度记录（WAITING/DONE/SKIPPED）
    · GET  /bidding/flow/logs          流转留痕（操作人/IP/说明/时间）
    · POST /bidding/flow/approval      政府审批回调（预留 stub）
  设计口径（用户拍板）：
    · 不让用户填 id：实体编号一律从 项目/招标项目/标段 列表下拉选择（show-search），带出 bizCode；
    · 可枚举全部下拉 + 中文映射：bizType/状态/事件/阶段/调度状态/审批动作 均走 labels.ts，不手敲；
    · JSON 立起来：timers/logs 以结构化表格呈现（非平铺 JSON）；
    · 表格模仿现有招投标页面（cert.css ct-* 外壳 + a-table，工业线稿 bid-ind 基座）；
    · 提示/暗示信息明确：动作时间驱动标注、权限不足提示、stub 预留提示、状态描述展示。
  法规依据（docs/法规依据/）：
    · 《电子招标投标办法》第三章 电子招标 第十七条（公告载明平台访问方式）、第十八条（文件加载下载）、
      第二十二条（澄清/修改醒目公告并通知下载者）；
    · 第五章 电子开标、评标和中标 第二十九~三十条（按招标文件确定时间公开开标、解密后公布）、
      第三十三条（在线评标保密环境）、第三十五条（候选人公示与结果公布）、第三十六条（中标通知书/未中标结果通知/合同签订）；
    · 第四十九条（如实记录每一操作环节的时间、网络地址与工作人员——流转留痕 logs 即落地本条）。
============================================================================ */
import { computed, h, onMounted, reactive, ref } from 'vue';

import { useAccess } from '@vben/access';
import { useAccessStore } from '@vben/stores';

import { Button, message, Modal, Select, Space, Tag } from 'ant-design-vue';

import {
  type FlowApi,
  flowApproval,
  flowTransition,
  getFlowLogs,
  getFlowNextActions,
  getFlowTimers,
} from '#/api/bidding/flow';
import {
  APPROVAL_ACTION_TEXT,
  APPROVAL_BIZ_TYPE_TEXT,
  BIZ_TYPE_TEXT,
  eventTextOf,
  isTerminalState,
  loadFlowDicts,
  phaseOf,
  statusColorOf,
  statusDescOf,
  statusTextOf,
  TIMER_STATUS_COLOR,
  TIMER_STATUS_TEXT,
} from '#/api/bidding/flow/labels';
import {
  getBidSectionByCode,
  getBidSectionPage,
  getProjectByCode,
  getProjectPage,
  getTenderProjectByCode,
  getTenderProjectPage,
} from '#/api/bidding/project';

import { fmtDateTime } from '../certificate/format';
import AttachmentList from '../vo/AttachmentList.vue';
import { loadAttachments } from '../vo/attachmentStore';
import { ENTITY_DEFS, type EntityDef, recordToFields } from '../vo/entityForms';
import { VO_SPEC } from '../vo/voSpec.gen';

import '../certificate/cert.css';

/* ============ 权限 ============ */
const { hasAccessByCodes } = useAccess();
const accessStore = useAccessStore();
function can(code: string): boolean {
  const codes = accessStore.accessCodes ?? [];
  if (codes.length === 0) return true;
  return codes.includes('*') || codes.includes('*:*:*') || hasAccessByCodes([code]);
}

/* ============ 实体选择（不让用户填 id：一律下拉） ============ */
const BIZ_OPTIONS: Array<{ label: string; value: FlowApi.BizType }> = [
  { label: '项目', value: 'PROJECT' },
  { label: '招标项目', value: 'TENDER_PROJECT' },
  { label: '标段', value: 'BID_SECTION' },
];

/** 危险/终态动作（红色按钮）：终止流标 / 取消 / 撤回 / 过期 */
const DANGER_EVENTS = ['TERMINATE', 'CANCEL', 'WITHDRAW', 'EXPIRE'];

const cur = reactive<{ bizCode?: string; bizType: FlowApi.BizType; label?: string }>({
  bizType: 'PROJECT',
  bizCode: undefined,
  label: undefined,
});
/** 选中实体在台账中的生命周期状态（契约 26：PROJECT/TENDER_PROJECT/BID_SECTION 均走新状态机；logs 为空时的当前状态兜底） */
const selectedState = ref<string | undefined>(undefined);

/* ============ 审批任务 · 平铺列表（2026-10-01 v2：查询表单 + 三类型混排）
 * 编号口径：内部编号（项目 projectCode / 招标项目 tenderProjectCode / 标段 bidSectionCode）；
 * 项目代码 = 投资项目统一代码 investProjectCode（仅项目行有真值；后端分页暂不过滤 → 本地过滤，见契约 B5）。 */
type TaskRow = {
  bizCode: string;
  bizType: FlowApi.BizType;
  createTime?: number | string;
  /** 项目行=投资项目统一代码；招标/标段行=关联项目/招标项目编号（仅展示，不参与项目代码过滤） */
  investProjectCode?: string;
  name: string;
  state?: string;
};
const queryForm = reactive<{ bizType: 'ALL' | FlowApi.BizType; code: string; investCode: string; name: string }>({
  bizType: 'ALL',
  code: '',
  investCode: '',
  name: '',
});
const BIZ_TYPE_OPTIONS: Array<{ label: string; value: 'ALL' | FlowApi.BizType }> = [
  { label: '全部', value: 'ALL' },
  ...BIZ_OPTIONS,
];
const allRows = ref<TaskRow[]>([]);
const taskLoading = ref(false);
const taskTotal = ref(0);
const taskPage = reactive({ pageNo: 1, pageSize: 10 });
const pagedRows = computed<TaskRow[]>(() => {
  const s = (taskPage.pageNo - 1) * taskPage.pageSize;
  return allRows.value.slice(s, s + taskPage.pageSize);
});

/** 行构造器：分页结果 → TaskRow（generic 防类型打架） */
const mkRows = <T,>(p: Promise<{ list?: T[] }>, m: (r: T) => TaskRow): Promise<TaskRow[]> =>
  p.then((r) => (r.list ?? []).map(m));

/** 查询：三类型（或单类型）并发拉取 → 合并 → 项目代码本地过滤 → 创建时间倒序 */
async function search(): Promise<void> {
  taskLoading.value = true;
  try {
    const code = queryForm.code.trim() || undefined;
    const name = queryForm.name.trim() || undefined;
    const wantProject = queryForm.bizType === 'ALL' || queryForm.bizType === 'PROJECT';
    const wantTender = queryForm.bizType === 'ALL' || queryForm.bizType === 'TENDER_PROJECT';
    const wantSection = queryForm.bizType === 'ALL' || queryForm.bizType === 'BID_SECTION';
    const [ps, ts, bs] = await Promise.all([
      wantProject
        ? mkRows(
            getProjectPage({ pageNo: 1, pageSize: 100, projectCode: code, projectName: name }),
            (r) => ({ bizType: 'PROJECT', bizCode: r.projectCode ?? '', name: r.projectName || r.projectCode || '', state: r.state, investProjectCode: r.investProjectCode, createTime: r.createTime }),
          )
        : Promise.resolve<TaskRow[]>([]),
      wantTender
        ? mkRows(
            getTenderProjectPage({ pageNo: 1, pageSize: 100, tenderProjectCode: code, tenderProjectName: name }),
            (r) => ({ bizType: 'TENDER_PROJECT', bizCode: r.tenderProjectCode ?? '', name: r.tenderProjectName || r.tenderProjectCode || '', state: r.state, investProjectCode: r.projectCode, createTime: r.createTime }),
          )
        : Promise.resolve<TaskRow[]>([]),
      wantSection
        ? mkRows(
            getBidSectionPage({ pageNo: 1, pageSize: 100, bidSectionCode: code, bidSectionName: name }),
            (r) => ({ bizType: 'BID_SECTION', bizCode: r.bidSectionCode ?? '', name: r.bidSectionName || r.bidSectionCode || '', state: r.state, investProjectCode: r.tenderProjectCode, createTime: r.createTime }),
          )
        : Promise.resolve<TaskRow[]>([]),
    ]);
    let rows = [...ps, ...ts, ...bs];
    const ic = queryForm.investCode.trim();
    if (ic) rows = rows.filter((r) => (r.investProjectCode ?? '').includes(ic));
    rows.sort((a, b) => String(b.createTime ?? '').localeCompare(String(a.createTime ?? '')));
    allRows.value = rows;
    taskTotal.value = rows.length;
    taskPage.pageNo = 1;
  } catch {
    allRows.value = [];
    taskTotal.value = 0;
  } finally {
    taskLoading.value = false;
  }
}
function resetSearch(): void {
  queryForm.code = '';
  queryForm.investCode = '';
  queryForm.name = '';
  void search();
}
/** 行 → 选中为当前审批对象（复用下方 状态/动作/时间轴/留痕/审批 面板） */
function openTask(row: TaskRow): void {
  cur.bizType = row.bizType;
  cur.bizCode = row.bizCode;
  cur.label = row.name;
  selectedState.value = row.state;
  void loadFlow();
  void loadEntityDetail();
  document.querySelector('#flow-detail')?.scrollIntoView({ block: 'start', behavior: 'smooth' });
}
/** 清空选中，回到任务列表 */
function backToList(): void {
  cur.bizCode = undefined;
  cur.label = undefined;
  selectedState.value = undefined;
  resetFlow();
  entityDetail.value = null;
  attachments.value = [];
  document.querySelector('#flow-tasks')?.scrollIntoView({ block: 'start', behavior: 'smooth' });
}

/** 平铺列表列（状态/阶段走 labels 映射，不手敲中文） */
const taskColumns = [
  {
    title: '类型',
    key: 'type',
    width: 110,
    customRender: ({ record }: { record: TaskRow }) =>
      h(Tag, { color: record.bizType === 'PROJECT' ? 'blue' : record.bizType === 'TENDER_PROJECT' ? 'purple' : 'cyan' }, () => BIZ_TYPE_TEXT[record.bizType] ?? record.bizType),
  },
  { title: '内部编号', dataIndex: 'bizCode', key: 'bizCode', width: 250, customRender: ({ text }: { text: string }) => h('span', { class: 'font-mono' }, text) },
  { title: '名称', dataIndex: 'name', key: 'name', ellipsis: true },
  {
    title: '项目代码（投资项目统一代码）',
    key: 'invest',
    width: 230,
    customRender: ({ record }: { record: TaskRow }) => (record.bizType === 'PROJECT' ? record.investProjectCode || '—' : '—'),
  },
  {
    title: '当前状态',
    key: 'state',
    width: 150,
    customRender: ({ record }: { record: TaskRow }) =>
      record.state ? h(Tag, { color: statusColorOf(record.bizType, record.state) }, () => statusTextOf(record.bizType, record.state)) : '—',
  },
  {
    title: '阶段',
    key: 'phase',
    width: 120,
    customRender: ({ record }: { record: TaskRow }) => (record.state ? phaseOf(record.bizType, record.state) || '—' : '—'),
  },
  { title: '创建时间', dataIndex: 'createTime', key: 'createTime', width: 170, customRender: ({ text }: { text?: null | number | string }) => (text ? fmtDateTime(text) : '—') },
  { title: '操作', key: 'action', width: 110, customRender: ({ record }: { record: TaskRow }) => h(Button, { type: 'link', size: 'small', onClick: () => openTask(record) }, () => '查看流转') },
];

/* ============ 流程数据 ============ */
const actions = ref<FlowApi.NextAction[]>([]);
const timers = ref<FlowApi.TimerRecord[]>([]);
const logs = ref<FlowApi.LogRecord[]>([]);
const flowLoading = ref(false);

function resetFlow() {
  actions.value = [];
  timers.value = [];
  logs.value = [];
}

/** 并行加载：可执行动作 + 调度记录 + 流转留痕 */
async function loadFlow() {
  if (!cur.bizCode) return;
  flowLoading.value = true;
  try {
    const [a, t, l] = await Promise.all([
      getFlowNextActions({ bizType: cur.bizType, bizCode: cur.bizCode }),
      getFlowTimers({ bizType: cur.bizType, bizCode: cur.bizCode }),
      getFlowLogs({ bizType: cur.bizType, bizCode: cur.bizCode }),
    ]);
    actions.value = Array.isArray(a) ? a : [];
    timers.value = Array.isArray(t) ? t : [];
    logs.value = Array.isArray(l) ? l : [];
  } catch {
    actions.value = [];
    timers.value = [];
    logs.value = [];
  } finally {
    flowLoading.value = false;
  }
}

/* ============ 审批对象全景（完整数据集 + 附件：审批前先看清对象全貌） ============ */
const entityDetail = ref<null | Record<string, unknown>>(null);
const detailLoading = ref(false);
/** 当前实体的数据集定义（ENTITY_DEFS 分组：项目 / 招标项目 / 标段） */
const entityDef = computed<EntityDef | null>(() => {
  if (cur.bizType === 'PROJECT') return ENTITY_DEFS.project;
  if (cur.bizType === 'TENDER_PROJECT') return ENTITY_DEFS.tenderProject;
  if (cur.bizType === 'BID_SECTION') return ENTITY_DEFS.bidSection;
  return null;
});
/** 完整数据集（规范码 → 值）：fields 全量 + 主列覆盖（与实体详情页同口径） */
const entityFields = computed<Record<string, string>>(() =>
  recordToFields(entityDef.value, entityDetail.value),
);
/** 已挂附件（本地留存 + 规范标识号；后端附件接口就绪后改按实体读取） */
const attachments = ref<AttachmentRecord[]>([]);
/** 规范字段码 → 中文标签（VO_SPEC 唯一真源；未知码原样展示） */
function fieldLabel(def: EntityDef | null, code: string): string {
  const ds = def ? VO_SPEC[def.vo] : undefined;
  const f = ds?.fields.find((x) => x.code === code);
  return f?.label ?? code;
}
/** 选中实体后并行取回完整主档（getXxxByCode 含 fields 数据集）+ 附件 */
async function loadEntityDetail() {
  entityDetail.value = null;
  attachments.value = [];
  if (!cur.bizCode) return;
  detailLoading.value = true;
  try {
    let rec: null | Record<string, unknown> = null;
    if (cur.bizType === 'PROJECT') rec = await getProjectByCode(cur.bizCode);
    else if (cur.bizType === 'TENDER_PROJECT') rec = await getTenderProjectByCode(cur.bizCode);
    else rec = await getBidSectionByCode(cur.bizCode);
    entityDetail.value = rec as null | Record<string, unknown>;
    attachments.value = loadAttachments(cur.bizType, cur.bizCode);
  } catch {
    entityDetail.value = null;
    attachments.value = [];
  } finally {
    detailLoading.value = false;
  }
}

/* ============ 状态流转执行 ============ */
const actOpen = ref(false);
const actSaving = ref(false);
const actEvent = ref('');
const actLabel = ref('');
const actToState = ref('');
const actRemark = ref('');

function openTransition(a: FlowApi.NextAction) {
  actEvent.value = a.event;
  actLabel.value = a.label;
  actToState.value = a.toState;
  actRemark.value = '';
  actOpen.value = true;
}

async function doTransition() {
  if (!cur.bizCode || !actEvent.value) return;
  actSaving.value = true;
  try {
    const resp = await flowTransition({
      bizType: cur.bizType,
      bizCode: cur.bizCode,
      event: actEvent.value,
      remark: actRemark.value.trim() || undefined,
    });
    message.success(
      `流转成功：${actLabel.value} —— ${statusTextOf(cur.bizType, resp?.fromState)} → ${statusTextOf(cur.bizType, resp?.toState)}`,
    );
    actOpen.value = false;
    await loadFlow();
  } catch {
    // 失败原因由请求层统一 toast 后端 msg（如 FLOW_EVENT_INVALID 状态不匹配）
  } finally {
    actSaving.value = false;
  }
}

/* ============ 政府审批（预留 stub；契约 27：PROJECT 项目立项 / TENDER_PROJECT 招标方案 / TERMINATION 终止 / ANNOUNCEMENT 公告审核） ============ */
const apOpen = ref(false);
const apSaving = ref(false);
const apAction = ref<'APPROVE' | 'REJECT'>('APPROVE');
const apRemark = ref('');
const apBizType = ref('PROJECT');
const AP_BIZ_OPTIONS = [
  { label: '项目立项审批', value: 'PROJECT' },
  { label: '招标方案审批', value: 'TENDER_PROJECT' },
  { label: '终止/流标审批', value: 'TERMINATION' },
  { label: '公告审核', value: 'ANNOUNCEMENT' },
];
/** 审批对象类型默认值：按当前实体推导（标段无立项/方案审批，归入终止/流标审批） */
function defaultApprovalBizType(b: FlowApi.BizType): string {
  if (b === 'TENDER_PROJECT') return 'TENDER_PROJECT';
  if (b === 'BID_SECTION') return 'TERMINATION';
  return 'PROJECT';
}
function openApproval(action: 'APPROVE' | 'REJECT') {
  apAction.value = action;
  apBizType.value = defaultApprovalBizType(cur.bizType);
  apRemark.value = '';
  apOpen.value = true;
}

async function doApproval() {
  if (!cur.bizCode) return;
  apSaving.value = true;
  try {
    const resp = await flowApproval({
      bizType: apBizType.value,
      bizId: cur.bizCode,
      action: apAction.value,
      operator: undefined,
      remark: apRemark.value.trim() || undefined,
    });
    message.success(
      `审批回调：${APPROVAL_ACTION_TEXT[apAction.value] ?? apAction.value}${resp?.stub ? '（预留 stub 模拟，未推进状态）' : ''} ${resp?.message ?? ''}`,
    );
    apOpen.value = false;
    // stub 不落库不推进：状态推进仍需在「可执行动作」里手动执行对应流转事件
  } catch {
    // 静默
  } finally {
    apSaving.value = false;
  }
}

/* ============ 展示工具 ============ */
const logsCols = [
  { title: '时间', dataIndex: 'createTime', key: 'createTime', width: 170, customRender: ({ text }: { text?: null | number | string }) => fmtDateTime(text) },
  { title: '事件', dataIndex: 'event', key: 'event', width: 190, customRender: ({ text }: { text?: null | string }) => eventTextOf(text) },
  { title: '状态变化', key: 'trans', width: 180, customRender: ({ record }: { record: FlowApi.LogRecord }) =>
    statusTextOf(cur.bizType, record.fromState) + ' → ' + statusTextOf(cur.bizType, record.toState) },
  { title: '操作人', dataIndex: 'operator', key: 'operator', width: 130, customRender: ({ text }: { text?: null | string }) => text || '—' },
  { title: '操作 IP', dataIndex: 'operatorIp', key: 'operatorIp', width: 140, customRender: ({ text }: { text?: null | string }) => text || '—' },
  { title: '说明/意见', dataIndex: 'remark', key: 'remark', ellipsis: true, customRender: ({ text }: { text?: null | string }) => text || '—' },
];

const timersCols = [
  { title: '时间轴事件', dataIndex: 'event', key: 'event', width: 200, customRender: ({ text }: { text?: null | string }) => eventTextOf(text) },
  { title: '触发时间', dataIndex: 'triggerTime', key: 'triggerTime', width: 170, customRender: ({ text }: { text?: null | number | string }) => fmtDateTime(text) },
  { title: '状态', dataIndex: 'status', key: 'status', width: 110, customRender: ({ text }: { text?: null | string }) =>
    text ? h(Tag, { color: TIMER_STATUS_COLOR[text] ?? 'default' }, () => TIMER_STATUS_TEXT[text] ?? text) : '—' },
  { title: '实际执行时间', dataIndex: 'executedTime', key: 'executedTime', width: 170, customRender: ({ text }: { text?: null | number | string }) => fmtDateTime(text) },
  { title: '来源说明', dataIndex: 'remark', key: 'remark', ellipsis: true, customRender: ({ text }: { text?: null | string }) => text || '—' },
];

/** FlowLogRespVO 无 id 字段，row-key 用「createTime + 行号」复合键保证唯一 */
const logRowKey = (r: FlowApi.LogRecord, i?: number): string =>
  (r.createTime ? String(r.createTime) : '') + ':' + (i ?? 0);

const currentStateText = computed(() => statusTextOf(cur.bizType, latestStateOf()));
function latestStateOf(): string | undefined {
  // 最近一条留痕的 toState 即当前状态（后端 logs 时间倒序，第一条最新）；
  // 无流转记录时回退到台账管理态 state（页面初始化前为空 → 显示“—”）
  const l = logs.value[0];
  return l?.toState || selectedState.value;
}

onMounted(() => {
  // 状态字典（契约 26 §8 / 28 §八-7）：四类状态字典来自 /bidding/meta/dicts（label 即中文/tagType 配色/terminal 终态），
  // 字典不可用时 labels.ts 静态中文兜底，不白屏
  void loadFlowDicts();
  void search();
});
</script>

<template>
  <div class="bid-ind ind-page">
    <div class="ind-stripes"></div>
    <div class="ind-head">
      <div class="min-w-0">
        <div class="ind-title">招标全流程 · 审批工作台 <span class="ind-mark">FLOW</span></div>
        <div class="ind-sub">
          审批任务平铺（项目 / 招标项目 / 标段混排）：内部编号查询 + 项目代码（投资项目统一代码）查询 + 名称筛选；
          状态流转、时间驱动调度、流转留痕均以服务端为准。依据《电子招标投标办法》（八部委令 20 号）第三、五章。
        </div>
      </div>
      <div class="ind-actions">
        <Button type="primary" :loading="taskLoading" @click="search">刷新</Button>
      </div>
    </div>

    <div class="ind-body">
      <!-- 审批任务（平铺）：查询表单 + 三类型混排（2026-10-01 v2） -->
      <div class="ct-panel mb-3" id="flow-tasks">
        <div class="ct-panel-h">
          <span class="ct-panel-t">审批任务</span>
          <span class="ct-panel-meta">
            平铺 项目 / 招标项目 / 标段；编号 = 内部编号（不混入项目代码）；项目代码 = 投资项目统一代码（仅项目维度过滤）
          </span>
        </div>
        <div class="ct-panel-b">
          <div class="mb-3 flex flex-wrap items-center gap-2">
            <Select v-model:value="queryForm.bizType" style="width: 150px" :options="BIZ_TYPE_OPTIONS" placeholder="实体类型" />
            <a-input v-model:value="queryForm.code" allow-clear style="width: 230px" placeholder="内部编号（项目 / 招标项目 / 标段编号）" @press-enter="search" />
            <a-input v-model:value="queryForm.investCode" allow-clear style="width: 240px" placeholder="项目代码（投资项目统一代码，仅项目生效）" @press-enter="search" />
            <a-input v-model:value="queryForm.name" allow-clear style="width: 200px" placeholder="名称关键字" @press-enter="search" />
            <Button type="primary" :loading="taskLoading" @click="search">查询</Button>
            <Button @click="resetSearch">重置</Button>
            <span class="pt-hit">
              共 {{ taskTotal }} 条
              <span v-if="queryForm.investCode.trim()" class="text-xs text-muted-foreground/70">（项目代码为本地过滤）</span>
            </span>
          </div>
          <a-table
            class="ct-table"
            :columns="taskColumns"
            :data-source="pagedRows"
            :loading="taskLoading"
            :pagination="{
              current: taskPage.pageNo,
              pageSize: taskPage.pageSize,
              total: taskTotal,
              showSizeChanger: true,
              showTotal: (t: number) => `共 ${t} 条`,
            }"
            size="small"
            row-key="bizCode"
            @change="(p: any) => { taskPage.pageNo = p.current ?? 1; taskPage.pageSize = p.pageSize ?? 10 }"
          >
            <template #emptyText>
              <div class="ct-empty">暂无匹配的审批任务（编号/名称可在项目、招标项目、标段台账登记后回到本页查询）</div>
            </template>
          </a-table>
        </div>
      </div>

      <!-- 当前审批对象（v2：来自平铺列表行「查看流转」；含返回列表） -->
      <div class="ct-panel mb-3" v-if="cur.bizCode" id="flow-detail">
        <div class="ct-panel-h">
          <span class="ct-panel-t">当前审批对象</span>
          <span class="ct-panel-meta">{{ BIZ_TYPE_TEXT[cur.bizType] ?? cur.bizType }} · {{ cur.label ?? cur.bizCode }}</span>
        </div>
        <div class="ct-panel-b flex flex-wrap items-center gap-2">
          <span class="text-sm font-semibold text-muted-foreground">已选对象：</span>
          <Tag :color="statusColorOf(cur.bizType, latestStateOf())">{{ currentStateText }}</Tag>
          <span class="font-mono text-sm">{{ cur.bizCode }}</span>
          <span class="text-xs text-muted-foreground/70">{{ cur.label }}</span>
          <Button size="small" style="margin-left: auto" @click="backToList">返回任务列表</Button>
        </div>
      </div>

      <!-- 当前状态 + 可执行动作 -->
      <div class="ct-panel mb-3" v-if="cur.bizCode">
        <div class="ct-panel-h">
          <span class="ct-panel-t">当前状态与可执行动作</span>
          <span class="ct-panel-meta">动作来自服务端状态机（next-actions），仅列出当前状态允许的操作</span>
        </div>
        <div class="ct-panel-b">
          <div class="mb-2 flex flex-wrap items-center gap-2">
            <span class="text-sm font-semibold text-muted-foreground">当前状态：</span>
            <Tag :color="statusColorOf(cur.bizType, latestStateOf())">
              {{ currentStateText }}
            </Tag>
            <Tag v-if="isTerminalState(cur.bizType, latestStateOf())" color="red">终态 · 不可流转</Tag>
            <span v-if="phaseOf(cur.bizType, latestStateOf())" class="text-xs text-muted-foreground">
              阶段：{{ phaseOf(cur.bizType, latestStateOf()) }}
            </span>
            <span v-if="statusDescOf(cur.bizType, latestStateOf())" class="text-xs text-muted-foreground/70">
              —— {{ statusDescOf(cur.bizType, latestStateOf()) }}
            </span>
          </div>
          <div class="flex flex-wrap items-center gap-2">
            <template v-if="actions.length">
              <Button
                v-for="a in actions"
                :key="a.event"
                type="primary"
                :danger="DANGER_EVENTS.includes(a.event)"
                :title="`流转后进入：${ statusTextOf(cur.bizType, a.toState)}`"
                @click="openTransition(a)"
              >
                {{ a.label }} → {{ statusTextOf(cur.bizType, a.toState) }}
              </Button>
            </template>
            <span v-else-if="flowLoading" class="text-xs text-muted-foreground">动作加载中…</span>
            <span v-else class="text-xs text-muted-foreground/70">当前状态无可执行动作（终态或等待时间驱动事件）</span>
          </div>
        </div>
      </div>

      <!-- 审批对象全景（完整数据集 + 附件：审批前先看清对象全貌，不再只看一行编号） -->
      <div class="ct-panel mb-3" v-if="cur.bizCode">
        <div class="ct-panel-h">
          <span class="ct-panel-t">审批对象全景</span>
          <span class="ct-panel-meta">
            数据集按《数据规范 V2.0》分组完整展示（与实体详情页同口径）；审批结论请基于下方全貌作出
          </span>
        </div>
        <div class="ct-panel-b">
          <div class="mb-3 flex flex-wrap items-center gap-2">
            <span class="text-sm font-semibold text-muted-foreground">审批对象：</span>
            <Tag :color="statusColorOf(cur.bizType, latestStateOf())">{{ currentStateText }}</Tag>
            <span class="text-sm">{{ cur.label ?? cur.bizCode }}</span>
          </div>
          <a-spin :spinning="detailLoading">
            <template v-if="entityDef && entityDetail">
              <div v-for="g in entityDef.groups" :key="g.title" class="mb-4">
                <div class="mb-1.5 text-xs font-semibold text-muted-foreground">{{ g.title }}</div>
                <div class="ind-kv">
                  <div v-for="c in g.codes" :key="c" class="kv">
                    <span class="k">{{ fieldLabel(entityDef, c) }}</span>
                    <span class="v">{{ entityFields[c] ?? '—' }}</span>
                  </div>
                </div>
              </div>
            </template>
            <div v-else-if="!detailLoading" class="text-xs text-muted-foreground/70">
              未获取到实体主档（{{ cur.bizCode }}）—— 该编号在台账中不存在，或数据集字段为空
            </div>
          </a-spin>
          <div class="mt-3 border-t border-border pt-3">
            <AttachmentList
              :list="attachments"
              :biz-type="cur.bizType"
              :biz-code="cur.bizCode"
              compact
            />
          </div>
        </div>
      </div>

      <!-- 时间驱动调度记录（JSON 立起来：结构化表格） -->
      <div class="ct-panel mb-3" v-if="cur.bizCode">
        <div class="ct-panel-h">
          <span class="ct-panel-t">时间驱动调度记录</span>
          <span class="ct-panel-meta">
            触发时间取自公告权威数据集（DOC_GET_START_TIME 文件获取开始 → 投标开始；BID_DOC_REFER_END_TIME / BID_END_TIME → 投标截止），公告登记即注册；
            到点由定时任务自动触发（<a-tag color="orange">待触发</a-tag> → <a-tag color="green">已执行</a-tag> / <a-tag color="default">已跳过</a-tag>），手动执行受 manual-advance-enabled 开关控制
          </span>
        </div>
        <div class="ct-panel-b">
          <a-table
            class="ct-table"
            :columns="timersCols"
            :data-source="timers"
            :loading="flowLoading"
            :pagination="false"
            size="small"
            row-key="id"
          >
            <template #emptyText><div class="ct-empty">暂无时间调度记录（该实体的公告未配置投标起止时间，或调度已执行完成）</div></template>
          </a-table>
        </div>
      </div>

      <!-- 流转留痕（JSON 立起来：结构化表格 + 法条依据） -->
      <div class="ct-panel mb-3" v-if="cur.bizCode">
        <div class="ct-panel-h">
          <span class="ct-panel-t">流转留痕</span>
          <span class="ct-panel-meta">
            《电子招标投标办法》第四十九条：如实记录每一操作环节的时间、网络地址和工作人员 —— 时间倒序，最新在前
          </span>
        </div>
        <div class="ct-panel-b">
          <a-table
            class="ct-table"
            :columns="logsCols"
            :data-source="logs"
            :loading="flowLoading"
            :pagination="false"
            size="small"
            :row-key="logRowKey"
          >
            <template #emptyText><div class="ct-empty">暂无流转留痕（尚未发生任何流转）</div></template>
          </a-table>
        </div>
      </div>

      <!-- 政府审批（预留 stub；契约 27） -->
      <div class="ct-panel" v-if="cur.bizCode && can('bidding:flow:approval')">
        <div class="ct-panel-h">
          <span class="ct-panel-t">政府审批（预留接口）</span>
          <span class="ct-panel-meta">
            四个审批点：项目立项 / 招标方案 / 终止流标 / 公告审核；当前 stub 模拟（直接返回结论，不落库、不推进状态）——
            审批「通过」后需在「可执行动作」里手动执行对应流转事件（契约 27 §5/§6）
          </span>
        </div>
        <div class="ct-panel-b flex flex-wrap items-center gap-2">
          <Select
            v-model:value="apBizType"
            style="width: 180px"
            :options="AP_BIZ_OPTIONS"
            placeholder="审批对象类型"
          />
          <span class="text-xs text-muted-foreground/70">审批对象：{{ BIZ_TYPE_TEXT[cur.bizType] }}「{{ cur.label ?? cur.bizCode }}」（编号带出）</span>
        </div>
        <div class="ct-panel-b pt-0">
          <Space>
            <Button @click="openApproval('APPROVE')">发起审批（通过）</Button>
            <Button danger @click="openApproval('REJECT')">发起审批（驳回）</Button>
          </Space>
        </div>
      </div>
    </div>

    <!-- 流转确认弹窗 -->
    <Modal
      v-model:open="actOpen"
      :width="560"
      :title="`执行流转：${ actLabel}`"
      :confirm-loading="actSaving"
      ok-text="确认流转"
      cancel-text="取消"
      @ok="doTransition"
    >
      <div class="space-y-3">
        <div class="text-sm">
          将执行事件 <b class="font-mono">{{ actEvent }}</b>（{{ actLabel }}），流转后状态进入
          <Tag :color="statusColorOf(cur.bizType, actToState)">{{ statusTextOf(cur.bizType, actToState) }}</Tag>
        </div>
        <div>
          <div class="mb-1 text-xs font-medium text-muted-foreground">流转说明 / 审批意见（选填，留痕展示）</div>
          <a-textarea
            v-model:value="actRemark"
            :rows="2"
            placeholder="如：资料齐全同意流转 / 异议成立组织复核（按《招标投标法实施条例》第五十四条）"
          />
        </div>
        <div class="text-xs text-muted-foreground/70">说明将写入流转留痕（操作人/IP/时间由服务端记录）</div>
      </div>
    </Modal>

    <!-- 政府审批弹窗 -->
    <Modal
      v-model:open="apOpen"
      :width="720"
      :title="`政府审批（${ apAction === 'APPROVE' ? '通过' : '驳回' }）`"
      :confirm-loading="apSaving"
      ok-text="提交"
      cancel-text="取消"
      @ok="doApproval"
    >
      <div class="space-y-3">
        <div class="rounded border border-border bg-muted/30 p-3">
          <div class="mb-2 text-xs font-semibold text-muted-foreground">审批对象摘要</div>
          <div class="grid grid-cols-2 gap-x-6 gap-y-1.5 text-sm">
            <div class="flex items-baseline gap-2">
              <span class="shrink-0 text-xs text-muted-foreground">审批点</span>
              <span class="font-medium">{{ APPROVAL_BIZ_TYPE_TEXT[apBizType] ?? apBizType }}</span>
            </div>
            <div class="flex items-baseline gap-2">
              <span class="shrink-0 text-xs text-muted-foreground">业务类型</span>
              <span class="font-medium">{{ BIZ_TYPE_TEXT[cur.bizType] ?? cur.bizType }}</span>
            </div>
            <div v-if="cur.label" class="flex items-baseline gap-2 col-span-2">
              <span class="shrink-0 text-xs text-muted-foreground">审批对象</span>
              <span class="font-medium">{{ cur.label }}</span>
            </div>
            <div v-if="cur.bizCode" class="flex items-baseline gap-2 col-span-2">
              <span class="shrink-0 text-xs text-muted-foreground">对象编号</span>
              <span class="font-mono">{{ cur.bizCode }}</span>
            </div>
            <div class="flex items-baseline gap-2">
              <span class="shrink-0 text-xs text-muted-foreground">当前状态</span>
              <Tag :color="statusColorOf(cur.bizType, latestStateOf())">{{ currentStateText }}</Tag>
            </div>
            <div class="flex items-baseline gap-2">
              <span class="shrink-0 text-xs text-muted-foreground">流程阶段</span>
              <span>{{ phaseOf(cur.bizType, latestStateOf()) }}</span>
            </div>
            <div
              v-if="statusDescOf(cur.bizType, latestStateOf())"
              class="flex items-baseline gap-2 col-span-2"
            >
              <span class="shrink-0 text-xs text-muted-foreground">状态说明</span>
              <span class="text-muted-foreground">{{ statusDescOf(cur.bizType, latestStateOf()) }}</span>
            </div>
          </div>
        </div>
        <div class="text-sm text-muted-foreground/70">
          审批对象完整数据集与已挂附件见上方「审批对象全景」面板；本弹窗仅作结论确认。预留 stub 仅返回审批结论
          （不落库、不推进状态）；「通过」后请回到「可执行动作」执行对应流转事件
          （如 项目立项审批通过后执行「审批通过」）。
        </div>
        <div>
          <div class="mb-1 text-xs font-medium text-muted-foreground">审批意见</div>
          <a-textarea v-model:value="apRemark" :rows="3" placeholder="如：同意立项 / 驳回修改" />
        </div>
      </div>
    </Modal>
  </div>
</template>

<style scoped>
/* ---------- 演示数据提示 ---------- */
.pt-demo-tip {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  padding: 10px 14px;
  margin-bottom: 14px;
  font-size: 12.5px;
  line-height: 1.6;
  color: hsl(var(--muted-foreground));
  background: var(--ind-warn-bg);
  border: 1px solid var(--ind-warn-border);
  border-radius: var(--radius);
}
.pt-demo-badge {
  flex: 0 0 auto;
  padding: 1px 8px;
  font-size: 11.5px;
  font-weight: 600;
  color: var(--ind-warn);
  background: hsl(var(--card));
  border: 1px solid var(--ind-warn-border);
  border-radius: 4px;
}

/* ---------- 筛选条补充 ---------- */
.pt-hit {
  margin-left: auto;
  font-size: 12.5px;
  color: hsl(var(--muted-foreground));
  white-space: nowrap;
}

/* ---------- 公司卡 ---------- */
.pt-company-h {
  cursor: pointer;
  user-select: none;
  flex-wrap: wrap;
  row-gap: 6px;
}
.pt-company-h:hover {
  background: hsl(var(--accent));
}
.pt-caret {
  flex: 0 0 auto;
  font-size: 10px;
  color: hsl(var(--muted-foreground));
  transition: transform 0.15s;
}
.pt-caret.on {
  transform: rotate(90deg);
}
.pt-credit {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12px;
  color: hsl(var(--muted-foreground));
}
.pt-company-tags {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}

/* ---------- 表格单元 ---------- */
.pt-proj-name {
  font-size: 13px;
  font-weight: 600;
  line-height: 1.5;
  color: hsl(var(--foreground));
}
.pt-proj-sub {
  margin-top: 2px;
  font-size: 12px;
  line-height: 1.6;
  color: hsl(var(--muted-foreground));
}
.pt-dot-sep {
  margin: 0 5px;
  opacity: 0.5;
}
.pt-flow {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}
.pt-steps {
  display: inline-flex;
  align-items: center;
}
.pt-step {
  display: inline-block;
  width: 8px;
  height: 8px;
  background: hsl(var(--muted));
  border-radius: 50%;
}
.pt-step.done {
  background: hsl(var(--success));
}
.pt-step.process {
  background: hsl(var(--primary));
}
.pt-step.stop {
  background: hsl(var(--destructive));
}
.pt-step-line {
  display: inline-block;
  width: 12px;
  height: 2px;
  background: hsl(var(--border));
}
.pt-flow-note {
  margin-top: 3px;
  font-size: 11.5px;
  color: hsl(var(--muted-foreground));
}
.pt-owner {
  font-size: 13px;
  font-weight: 600;
  color: hsl(var(--foreground));
}
.pt-owner-sub {
  margin-top: 2px;
  font-size: 12px;
  color: hsl(var(--muted-foreground));
}

/* ---------- 详情抽屉 ---------- */
.pt-detail-meta {
  display: flex;
  gap: 10px;
  align-items: center;
  margin-bottom: 12px;
  font-size: 12.5px;
  color: hsl(var(--muted-foreground));
}
.pt-block {
  padding: 12px 14px;
  margin-bottom: 12px;
  border: 1px solid hsl(var(--border));
  border-radius: var(--radius);
}
.pt-block-t {
  margin-bottom: 10px;
  font-size: 12.5px;
  font-weight: 600;
  color: hsl(var(--foreground));
}
.pt-owner-card {
  display: flex;
  gap: 14px;
  align-items: baseline;
  flex-wrap: wrap;
}
.pt-owner-name {
  font-size: 15px;
  font-weight: 600;
  color: hsl(var(--foreground));
}
.pt-owner-line {
  font-size: 12.5px;
  color: hsl(var(--muted-foreground));
}
.pt-line {
  font-size: 13px;
  line-height: 1.7;
  color: hsl(var(--foreground));
  word-break: break-all;
}
.pt-line-weak {
  color: hsl(var(--muted-foreground));
}
.pt-steps-wide {
  display: flex;
  align-items: flex-start;
}
.pt-step-col {
  flex: 1;
  text-align: center;
}
.pt-step-badge {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  margin: 0 auto;
  font-size: 12px;
  color: hsl(var(--muted-foreground));
  background: hsl(var(--muted));
  border-radius: 50%;
}
.pt-step-badge.done {
  color: #fff;
  background: hsl(var(--success));
}
.pt-step-badge.process {
  color: #fff;
  background: hsl(var(--primary));
}
.pt-step-badge.stop {
  color: #fff;
  background: hsl(var(--destructive));
}
.pt-step-t {
  margin-top: 5px;
  font-size: 12.5px;
  color: hsl(var(--muted-foreground));
}
.pt-step-time {
  margin-top: 1px;
  font-size: 11.5px;
  color: hsl(var(--muted-foreground) / 80%);
}
.pt-step-bar {
  flex: 1;
  height: 2px;
  margin-top: 12px;
  background: hsl(var(--muted));
  border-radius: 1px;
}
.pt-step-bar.on {
  background: hsl(var(--success));
}
.pt-term-note {
  margin-top: 10px;
  font-size: 12px;
  line-height: 1.7;
  color: hsl(var(--destructive));
}
.pt-ev {
  display: flex;
  gap: 12px;
  padding: 6px 0;
  border-bottom: 1px solid hsl(var(--border));
}
.pt-ev:last-child {
  border-bottom: none;
}
.pt-ev-time {
  flex: 0 0 118px;
  font-size: 12px;
  color: hsl(var(--muted-foreground));
  font-variant-numeric: tabular-nums;
}
.pt-ev-body {
  flex: 1;
  min-width: 0;
  font-size: 12.5px;
}
.pt-ev-type {
  font-weight: 600;
  color: hsl(var(--foreground));
}
.pt-ev-op {
  margin-left: 8px;
  color: hsl(var(--muted-foreground));
}
.pt-ev-desc {
  margin-top: 2px;
  color: hsl(var(--muted-foreground));
}
</style>
