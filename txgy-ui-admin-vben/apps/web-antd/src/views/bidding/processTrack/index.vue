<!-- ============================================================================
  投标流程跟踪 = 管理端「全租户投标意向总览」（只读 · 真实接口）
  ---------------------------------------------------------------------------
  【数据来源（2026-09-28 切换）】后端状态机已落地，本页数据全部来自真实接口，页面不再持有
  任何本地演示数据（原 ./demoData.ts 已删除），也不再写 localStorage。用到的四个端点：
    · GET /bidding/process-track/page    总览分页（关键词/阶段/公司/负责人 过滤 + 分页）
    · GET /bidding/process-track/stats   KPI 统计（公司数/项目数/进行中/已投标/已结束/负责人数/按阶段分布）
    · GET /bidding/process-track/options 筛选项候选（负责人全量去重；公司候选仅超管下发）
    · GET /bidding/process-track/detail  行详情（总览项 + 事件史 events[]，只读）
  权限均为 bidding:process-track:query；封装见 src/api/bidding/processTrack/index.ts。
  【隔离】超管跨租户、租户管理员仅本公司——**由服务端租户拦截器强制**（tenantId 入参对租户视角无效），
  前端只做呈现，不重复过滤、也不构成权限边界。
  【口径】阶段沿用投标人意向状态机：
    意向投标 INTENTION → 资质审核 QUALIFICATION_REVIEW → 待投标 PENDING_BID → 已投标 BIDDED；
    审核不通过 / 取消投标 → 终态 流程结束 TERMINATED。
    状态中文名优先取后端 stateName、事件中文名优先取 eventName；STATE_TEXT / EVENT_TEXT 仅在
    后端未下发时兜底（bidding_process_state / bidding_process_event 字典注册后改用字典）。

  【平台 / 本公司视图】超管判定 = 角色码 super_admin 或权限码 bidding:process-track:overview
    （后端 BiddingProcessTrackController#isSuperAdmin 即按该权限码判定，见 09-18 契约 §6.2；
     该权限仅绑 super_admin，见 txgy-module-bidding/sql/history/2026-09-28-process-track-overview.sql）。
    平台视图展示「所属租户」列与租户筛选；本公司视角下列与筛选一并隐藏（也无意义）。

  【本页只读】不提供「新建意向 / 报名 / 取消 / 审核 / 上传标书 / 删除」入口：这些动作分属
    门户用户侧（create / register / upload / cancel）与平台侧审核（review），管理端只做盘点；
    事件史亦只读展示，页面无「手动推进状态」的能力。

  【门禁】状态/事件中文文案收口到本文件的具名常量（本文件在
    txgy-module-bidding/sql/tools/check-hardcoded-enums.js 的 KNOWN_HARDCODED 基线内，处数不增）。

  【入口补齐（2026-09-30）】列表行操作列与内联详情页头各补两个入口（原有「详情」保留，仍只读）：
    · 项目详情 —— 跳项目台账通用实体详情页 /bidding/entity/PROJECT/<projectCode>
      （静态路由 name=BiddingEntityDetail，见 src/router/routes/core.ts，与项目台账列表行「详情 ↗」同一 URL 口径）；
      该行无 projectCode（项目编号）时按钮置灰 + tooltip 说明，不发无意义的跳转。
    · 关联公告 —— modules/NoticeLinkModal.vue：GET /bidding/notice-external/page（公开分页，@PermitAll），
      按 项目编号 projectCode / 标段编号 followKey **单维**查询（三入参后端 AND，一次只传一个），
      行点击新窗口打开门户详情 /portal/bidding/detail/<id>。
============================================================================ -->
<script lang="ts" setup>
import type {
  ProcessTrackDetail,
  ProcessTrackEvent,
  ProcessTrackItem,
  ProcessTrackOptions,
  ProcessTrackPageParams,
  ProcessTrackStats,
} from '#/api/bidding/processTrack';

import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import { useAccessStore, useUserStore } from '@vben/stores';
import { formatDate } from '@vben/utils';

import {
  Button,
  Empty,
  Input,
  message,
  Select,
  Table,
  Tag,
  Tooltip,
} from 'ant-design-vue';

import {
  fetchProcessTrackDetail,
  fetchProcessTrackOptions,
  fetchProcessTrackPage,
  fetchProcessTrackStats,
} from '#/api/bidding/processTrack';

import NoticeLinkModal from './modules/NoticeLinkModal.vue';

/* ============ 平台 / 个人 / 本公司 视图 ============ */
const accessStore = useAccessStore();
const userStore = useUserStore();

/**
 * 平台（超管）视角判定 —— 以角色码 / 权限码为准，不靠通配符权限码。
 *
 * 后端依据（2026-09-28 核对代码，非沿用旧写法）：BiddingProcessTrackController#isSuperAdmin
 *   = 登录用户 scopes 含 `bidding:process-track:overview`；该权限码仅绑 super_admin（role_id=1），
 *   见 txgy-module-bidding/sql/history/2026-09-28-process-track-overview.sql。
 * 本部署细粒度权限为**去重菜单权限码**（全仓库 SQL 无 `*:*:*` 通配符），故先判角色码 super_admin，
 * 再判后端口径的权限码 bidding:process-track:overview；通配符判定保留为其他部署的兜底。
 *
 * 数据范围三档（用户裁定 2026-09-29）：
 *   · platform —— 超管 / bidding:process-track:overview：全部租户；
 *   · **self** —— 无角色、无权限码账号（门户个人用户）：**只看自己**（按登录人 userId）；
 *                前台是"只看自己"，**甚至不是看租户**，所以不能降级成"本公司视角"；
 *   · tenant   —— 其余管理端账号：本公司（后端租户拦截器强制）。
 * 保守性：角色与权限都为空时按 self 处理 —— 宁可少看，不可越权。
 * 作用仅限呈现（租户列 / 租户筛选的显示与否），数据范围由后端强制。
 */
const scope = computed<'platform' | 'self' | 'tenant'>(() => {
  const roles = userStore.userRoles ?? [];
  const codes = accessStore.accessCodes ?? [];
  if (roles.length === 0 && codes.length === 0) return 'self';
  const platform =
    roles.includes('super_admin') ||
    codes.includes('bidding:process-track:overview') ||
    codes.includes('*') ||
    codes.includes('*:*:*');
  return platform ? 'platform' : 'tenant';
});
const isPlatform = computed(() => scope.value === 'platform');
/** 个人视图（前台口径）：只看登录人自己 */
const isSelf = computed(() => scope.value === 'self');
/** 当前登录人 userId（个人视图的数据范围） */
const currentUserId = computed<null | number | string>(() => userStore.userInfo?.userId ?? null);

/** 当前登录租户（仅用于页头文案；数据范围由后端强制，前端不再据此过滤） */
const currentTenantId = computed<null | number>(() => accessStore.tenantId ?? null);

/* ============ 口径字典 ============ */
/** 阶段中文名兜底表：优先用后端 stateName，仅在其缺失时回落 */
const STATE_TEXT: Record<string, string> = {
  INTENTION: '意向投标',
  QUALIFICATION_REVIEW: '资质审核',
  PENDING_BID: '待投标',
  BIDDED: '已投标',
  TERMINATED: '流程结束',
};
/** 事件中文名兜底表：优先用后端 eventName，仅在其缺失时回落 */
const EVENT_TEXT: Record<string, string> = {
  CREATE: '新建意向（建档）',
  REGISTER: '报名投标',
  APPROVE: '审核通过',
  REJECT: '审核不通过',
  UPLOAD_DOCUMENT: '上传标书',
  CANCEL: '取消投标',
};
/** 阶段标签配色（纯色值，不含中文，不参与硬编码枚举门禁） */
const STATE_COLOR: Record<string, string> = {
  INTENTION: 'blue',
  QUALIFICATION_REVIEW: 'orange',
  PENDING_BID: 'cyan',
  BIDDED: 'success',
  TERMINATED: 'default',
};
/** 正向主链路（终态 TERMINATED 不在链上，单独出标签） */
const STEP_ORDER: string[] = [
  'INTENTION',
  'QUALIFICATION_REVIEW',
  'PENDING_BID',
  'BIDDED',
];

/** 阶段中文名：后端 stateName 优先 → 兜底表 → 原始码 */
function stateText(row: ProcessTrackItem): string {
  return row.stateName || STATE_TEXT[row.state ?? ''] || row.state || '—';
}
/** 阶段标签配色：未知码回落默认灰 */
function stateColor(state?: string): string {
  return STATE_COLOR[state ?? ''] ?? 'default';
}
/** 事件的状态流转文案：'资质审核 → 待投标'（状态码缺字典时回落原始码） */
function eventFlowText(e: ProcessTrackEvent): string {
  const from = STATE_TEXT[e.fromState ?? ''] ?? e.fromState ?? '—';
  const to = STATE_TEXT[e.toState ?? ''] ?? e.toState ?? '—';
  return from + ' → ' + to;
}
/** 阶段筛选下拉：由 STATE_TEXT 派生，避免文案在模板里再写一份（同源单点，改口径只改一处） */
const stateOptions = computed(() => [
  { label: '全部阶段', value: 'ALL' },
  ...STEP_ORDER.map((s) => ({ label: STATE_TEXT[s]!, value: s })),
  { label: STATE_TEXT.TERMINATED!, value: 'TERMINATED' },
]);

/* ============ KPI 统计（GET /bidding/process-track/stats） ============ */
const stats = ref<ProcessTrackStats>({});
const statsLoading = ref(false);

/** 统计为**后端全量口径**（不随筛选变化；租户视角后端自动限定本公司），勿在前端对当前页 list 做 reduce */
async function loadStats(): Promise<void> {
  statsLoading.value = true;
  try {
    stats.value = (await fetchProcessTrackStats()) ?? {};
  } catch (e) {
    message.error('统计指标加载失败：' + ((e as Error).message || String(e)));
  } finally {
    statsLoading.value = false;
  }
}

/** KPI 数值：未加载/加载失败显示 —，避免把占位的 0 当成真实值 */
const num = (v?: null | number) => (v === null || v === undefined ? '—' : String(v));

/* ============ 筛选项候选（GET /bidding/process-track/options） ============ */
const options = ref<ProcessTrackOptions>({});
const optionsLoading = ref(false);

async function loadOptions(): Promise<void> {
  optionsLoading.value = true;
  try {
    options.value = (await fetchProcessTrackOptions()) ?? {};
  } catch (e) {
    message.error('筛选项加载失败：' + ((e as Error).message || String(e)));
  } finally {
    optionsLoading.value = false;
  }
}

/** 公司候选（仅超管下发；本公司视角隐藏该下拉） */
const tenantOptions = computed(() => [
  { label: '全部公司', value: 'ALL' },
  ...(options.value.tenants ?? []).map((o) => ({
    label: o.label || '租户 ' + (o.value ?? ''),
    value: o.value ?? '',
  })),
]);
/** 负责人候选（value=ownerUserId；平台视图带上 extra 公司名，便于跨租户同名区分） */
const ownerOptions = computed(() => [
  { label: '全部负责人', value: 'ALL' },
  ...(options.value.owners ?? []).map((o) => ({
    label:
      (o.label || '用户 ' + (o.value ?? '')) +
      (isPlatform.value && o.extra ? ' · ' + o.extra : ''),
    value: o.value ?? '',
  })),
]);

/* ============ 列表与筛选（GET /bidding/process-track/page） ============ */
const rows = ref<ProcessTrackItem[]>([]);
const total = ref(0);
const loading = ref(false);
const pageNo = ref(1);
const pageSize = ref(10);

const keyword = ref('');
const stateFilter = ref('ALL');
/** 公司（租户）筛选：仅平台视图显示；租户视角后端强制本租户，参数无效 */
const tenantFilter = ref('ALL');
/** 负责人筛选：值为 ownerUserId（精确匹配） */
const ownerFilter = ref('ALL');

const hasFilter = computed(
  () =>
    Boolean(keyword.value.trim()) ||
    stateFilter.value !== 'ALL' ||
    tenantFilter.value !== 'ALL' ||
    ownerFilter.value !== 'ALL',
);

async function loadPage(): Promise<void> {
  loading.value = true;
  try {
    const params: ProcessTrackPageParams = {
      pageNo: pageNo.value,
      pageSize: pageSize.value,
      keyword: keyword.value.trim() || undefined,
      state: stateFilter.value === 'ALL' ? undefined : stateFilter.value,
      tenantId:
        isPlatform.value && tenantFilter.value !== 'ALL'
          ? tenantFilter.value
          : undefined,
      // 个人视图（前台）：强制只取登录人自己的流程，不受负责人筛选影响
      ownerUserId: isSelf.value
        ? (currentUserId.value ?? undefined)
        : (ownerFilter.value === 'ALL' ? undefined : ownerFilter.value),
    };
    const res = await fetchProcessTrackPage(params);
    rows.value = res?.list ?? [];
    total.value = res?.total ?? 0;
  } catch (e) {
    rows.value = [];
    total.value = 0;
    message.error('投标流程跟踪加载失败：' + ((e as Error).message || String(e)));
  } finally {
    loading.value = false;
  }
}

/** 查询：条件变化一律回到第 1 页 */
function search(): void {
  pageNo.value = 1;
  void loadPage();
}

/** 重置：清空全部筛选后重新查询 */
function resetQuery(): void {
  keyword.value = '';
  stateFilter.value = 'ALL';
  tenantFilter.value = 'ALL';
  ownerFilter.value = 'ALL';
  search();
}

/** 分页联动：antd Table 的 current / pageSize 回写后重新拉取 */
function onTableChange(p: { current?: number; pageSize?: number }): void {
  pageNo.value = p?.current ?? 1;
  pageSize.value = p?.pageSize ?? 10;
  void loadPage();
}

/* ============ 视角说明 ============ */
/** 页头一句话：明确当前能看到谁的数据（避免租户误以为「本页就是全平台」） */
const scopeText = computed(() => {
  if (isPlatform.value) {
    return '当前为平台（超管）视图：可查看全部租户 / 公司的投标流程。';
  }
  if (isSelf.value) {
    return '当前为个人视图：仅展示本人跟进的投标流程。';
  }
  return (
    '当前为本公司视图：仅可查看本公司（租户 ' +
    (currentTenantId.value ?? '未识别') +
    '）的投标流程。'
  );
});
/** 列表空态文案：区分「筛选后为空」与「视角内本就没有数据」 */
const emptyText = computed(() => {
  if (hasFilter.value) return '当前筛选条件下没有匹配的投标流程';
  if (isPlatform.value) return '暂无意向投标记录';
  if (isSelf.value) return '本人暂无意向投标流程（可在大厅对公告点「意向投标」建档）';
  if (currentTenantId.value === null) return '未能识别当前租户，无法展示本公司投标流程';
  return '本公司（租户 ' + currentTenantId.value + '）暂无意向投标记录';
});

/* ============ 流程进度（只读展示） ============ */
/** 已推进到的步骤下标：终态按**已发生的实际时间点**回推，避免「已投标后取消」显示成只到意向 */
function reachedIndex(row: ProcessTrackItem): number {
  if (row.state !== 'TERMINATED') {
    const i = STEP_ORDER.indexOf(row.state ?? '');
    return i < 0 ? 0 : i;
  }
  const times = [
    row.intentionTime,
    row.registerTime,
    row.reviewTime,
    row.biddedTime,
  ];
  for (let i = times.length - 1; i >= 0; i -= 1) {
    if (times[i]) return i;
  }
  return 0;
}
const isTerminated = (row: ProcessTrackItem) => row.state === 'TERMINATED';
function stepStateOf(
  row: ProcessTrackItem,
  i: number,
): 'done' | 'process' | 'stop' | 'wait' {
  const reached = reachedIndex(row);
  if (i < reached) return 'done';
  if (i === reached) return isTerminated(row) ? 'stop' : 'process';
  return 'wait';
}
function stepTimeOf(
  row: ProcessTrackItem,
  i: number,
): null | number | string | undefined {
  return [row.intentionTime, row.registerTime, row.reviewTime, row.biddedTime][i];
}
/** antd Table bodyCell 的 record 为 Record<string, any>，转成 ProcessTrackItem（模板调用处包一层） */
function asItem(r: Record<string, any>): ProcessTrackItem {
  return r as ProcessTrackItem;
}

/* ============ 时间与格式化 ============ */
/** epoch 毫秒 / 时间字符串 → 'YYYY-MM-DD HH:mm'（统一走 @vben/utils，勿直接渲染数字） */
function fmtT(v?: null | number | string): string {
  if (v === null || v === undefined || v === '') return '—';
  return formatDate(v, 'YYYY-MM-DD HH:mm') || '—';
}
/** 时间值 → 毫秒时间戳（兼容 epoch 毫秒 / 13 位数字串 / 'YYYY-MM-DD HH:mm:ss'；非法返回 0，仅用于排序） */
function tsOf(v?: null | number | string): number {
  if (v === null || v === undefined || v === '') return 0;
  if (typeof v === 'number') return v;
  const d = new Date(String(v).replace(' ', 'T'));
  return Number.isNaN(+d) ? 0 : +d;
}
/** 距投标截止的剩余天数（已截止 / 无截止返回 null） */
function daysLeft(deadline?: null | number): null | number {
  if (!deadline) return null;
  const diff = deadline - Date.now();
  if (diff <= 0) return null;
  return Math.ceil(diff / 86400000);
}
/** 距截止说明文案：'还剩 N 天' / '已截止' / '—'（无截止时间） */
function deadlineText(deadline?: null | number): string {
  if (!deadline) return '—';
  const left = daysLeft(deadline);
  return left ? '还剩 ' + left + ' 天' : '已截止';
}
/** 标书大小（字节 → KB/MB；无值返回空串，模板据此不渲染括号） */
function fmtSize(bytes?: number): string {
  if (!bytes || bytes <= 0) return '';
  if (bytes < 1024) return bytes + ' B';
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
  return (bytes / 1024 / 1024).toFixed(2) + ' MB';
}

/* ============ 详情大页面（GET /bidding/process-track/detail） ============ */
const detailOpen = ref(false);
const detailLoading = ref(false);
const detail = ref<null | ProcessTrackDetail>(null);
const detailItem = computed<null | ProcessTrackItem>(() => detail.value?.item ?? null);
/** 事件倒序（最新在上） */
const detailEvents = computed(() =>
  [...(detail.value?.events ?? [])].sort(
    (a, b) => tsOf(b.occurredAt) - tsOf(a.occurredAt),
  ),
);

/** 打开详情：先用列表行占位（避免空白闪烁），再用 /detail 的完整数据（含事件史）覆盖 */
async function openDetail(row: ProcessTrackItem) {
  detailOpen.value = true;
  detailLoading.value = true;
  detail.value = { item: row, events: [] };
  try {
    const res = await fetchProcessTrackDetail(row.id);
    detail.value = { item: res?.item ?? row, events: res?.events ?? [] };
  } catch (e) {
    message.error(
      '详情加载失败：' +
        ((e as Error).message || String(e)) +
        '（下方为列表行已有字段）',
    );
  } finally {
    detailLoading.value = false;
  }
}
/** 关闭详情大页面，回到列表总览 */
function closeDetail() {
  detailOpen.value = false;
  detail.value = null;
}

/* ============ 行 / 详情页头入口：项目详情 · 关联公告（只读，2026-09-30 补） ============ */
const router = useRouter();

/** 行的项目编号（口径：projectCode = 项目编号，followKey = 标段编号）；空串 = 该行无项目编号 */
function projectCodeOf(row: null | ProcessTrackItem): string {
  return (row?.projectCode ?? '').trim();
}

/**
 * 项目详情：跳项目台账**真实详情页**（静态路由 /bidding/entity/:type/:code，name=BiddingEntityDetail，
 * 见 src/router/routes/core.ts；与项目台账列表行「详情 ↗」同一 URL 口径），不是本页的内联大页面。
 * 无 projectCode 时按钮已置灰，这里再兜一层，避免拼出 /bidding/entity/PROJECT/ 这种缺参路径。
 */
function openProjectDetail(row: null | ProcessTrackItem): void {
  const code = projectCodeOf(row);
  if (!code) return;
  void router.push(`/bidding/entity/PROJECT/${encodeURIComponent(code)}`);
}

/** 关联公告：弹窗目标行 + 开关（取数在子组件内，按 projectCode / followKey 单维查询公开分页） */
const noticeOpen = ref(false);
const noticeRow = ref<null | ProcessTrackItem>(null);
function openNotice(row: ProcessTrackItem): void {
  noticeRow.value = row;
  noticeOpen.value = true;
}

/* ============ 表格列 ============ */
/** 「所属租户」列仅平台（超管）视图出现：本公司视角下整列都是同一租户，无信息量 */
const columns = computed(() => [
  { dataIndex: 'projectName', key: 'project', title: '意向投标项目' },
  ...(isPlatform.value
    ? [{ dataIndex: 'tenantName', key: 'tenant', title: '所属租户', width: 200 }]
    : []),
  { key: 'flow', title: '投标流程', width: 220 },
  { key: 'owner', title: '负责人', width: 170 },
  { key: 'deadline', title: '投标截止', width: 170 },
  { key: 'action', title: '操作', width: 190 },
]);

onMounted(() => {
  void loadPage();
  void loadStats();
  void loadOptions();
});
</script>

<template>
  <div class="bid-ind ind-page">
    <div class="ind-stripes"></div>

    <div class="ind-head">
      <div class="min-w-0">
        <div class="ind-title">
          投标流程跟踪 <span class="ind-mark">TRACK</span>
        </div>
        <div class="ind-sub">
          只读管理视图：按公司盘点各自跟进的意向投标项目，查看每个项目所处的投标流程
          （意向投标 → 资质审核 → 待投标 → 已投标 / 流程结束）与项目负责人。
          本页不提供「新建意向 / 报名 / 取消 / 审核 / 上传标书 / 删除」等操作——阶段推进由业务方在对应环节判定。
          数据来自投标流程总览接口（GET /bidding/process-track/page · stats · options · detail：
          超管跨租户，租户视角仅本公司，隔离由服务端强制）。
          {{ scopeText }}
        </div>
      </div>
      <div class="ind-actions">
        <Tag :color="isPlatform ? 'gold' : 'blue'">
          {{ isPlatform ? '平台视图 · 全租户' : '本公司视图' }}
        </Tag>
      </div>
    </div>

    <div class="ind-body">
      <!-- 总览指标（GET /stats：后端全量口径，不随筛选变化） -->
      <div class="ind-kpis" :class="{ 'pt-loading': statsLoading }">
        <div v-if="isPlatform" class="ind-kpi k-blue">
          <div class="k-label">租户 / 公司</div>
          <div class="k-num">{{ num(stats.tenantCount) }}</div>
          <div class="k-note">存在投标流程记录的公司数</div>
        </div>
        <div class="ind-kpi k-blue">
          <div class="k-label">意向投标项目</div>
          <div class="k-num">{{ num(stats.projectCount) }}</div>
          <div class="k-note">{{ isPlatform ? '全部公司合计' : '本公司合计' }}</div>
        </div>
        <div class="ind-kpi k-orange">
          <div class="k-label">流程进行中</div>
          <div class="k-num">{{ num(stats.ongoing) }}</div>
          <div class="k-note">意向投标 / 资质审核 / 待投标</div>
        </div>
        <div class="ind-kpi k-green">
          <div class="k-label">已投标</div>
          <div class="k-num">{{ num(stats.bidded) }}</div>
          <div class="k-note">已递交投标文件</div>
        </div>
        <div class="ind-kpi k-red">
          <div class="k-label">流程结束</div>
          <div class="k-num">{{ num(stats.terminated) }}</div>
          <div class="k-note">审核不通过 / 取消投标</div>
        </div>
        <div class="ind-kpi">
          <div class="k-label">负责人</div>
          <div class="k-num">{{ num(stats.ownerCount) }}</div>
          <div class="k-note">承担投标流程的自然人数</div>
        </div>
      </div>

      <!-- 筛选（服务端过滤：关键词 / 阶段 / 公司 / 负责人；候选来自 GET /options） -->
      <div class="ind-filterbar">
        <Input
          v-model:value="keyword"
          allow-clear
          placeholder="搜索公司 / 项目名称 / 项目编号 / 招标人 / 负责人"
          style="width: 320px"
          @press-enter="search"
        />
        <Select
          v-model:value="stateFilter"
          style="width: 170px"
          :options="stateOptions"
          @change="search"
        />
        <Select
          v-if="isPlatform"
          v-model:value="tenantFilter"
          style="width: 230px"
          :loading="optionsLoading"
          :options="tenantOptions"
          @change="search"
        />
        <Select
          v-model:value="ownerFilter"
          style="width: 190px"
          :loading="optionsLoading"
          :options="ownerOptions"
          @change="search"
        />
        <Button type="primary" :loading="loading" @click="search">查询</Button>
        <Button @click="resetQuery">重置</Button>
        <span class="pt-hit">共 {{ total }} 条记录</span>
      </div>

      <!-- 项目详情大页面（点击「详情」整页展开：总览项 + 事件史，只读） -->
      <section v-if="detailOpen && detailItem" class="pt-detail-page">
        <div class="pt-detail-head">
          <div class="pt-detail-title-row">
            <Tag class="pt-detail-state" :color="stateColor(detailItem.state)">
              {{ stateText(detailItem) }}
            </Tag>
            <h2 class="pt-detail-name">{{ detailItem.projectName || '—' }}</h2>
          </div>
          <div class="pt-detail-meta-line">
            <span v-if="detailItem.tenantName || detailItem.tenantId" class="pt-detail-company">
              {{ detailItem.tenantName || '—' }}（租户 {{ detailItem.tenantId || '—' }}）
            </span>
            <span v-if="detailLoading" class="pt-detail-company">详情加载中…</span>
          </div>
          <div class="pt-detail-tools">
            <!-- 项目详情：跳项目台账真实详情（无项目编号时置灰 + tooltip 说明） -->
            <Tooltip
              :title="
                projectCodeOf(detailItem)
                  ? ''
                  : '该行没有项目编号（projectCode），无法跳转项目台账详情'
              "
            >
              <span class="pt-act-wrap">
                <Button
                  size="small"
                  :disabled="!projectCodeOf(detailItem)"
                  @click="openProjectDetail(detailItem)"
                >
                  项目详情
                </Button>
              </span>
            </Tooltip>
            <!-- 关联公告：公开分页按项目编号 / 标段编号单维查询，行点击开新窗口门户详情 -->
            <Button size="small" @click="openNotice(detailItem)">关联公告</Button>
            <Button class="pt-detail-back" size="small" @click="closeDetail">← 返回列表</Button>
          </div>
        </div>

        <div class="pt-detail-grid">
          <!-- ① 项目与投标主体 -->
          <div class="pt-block">
            <div class="pt-block-t">项目与投标主体</div>
            <div class="ind-kv">
              <div class="kv"><span class="k">项目编号</span><span class="v">{{ detailItem.projectCode || '—' }}</span></div>
              <div class="kv"><span class="k">标段编号</span><span class="v">{{ detailItem.followKey || '—' }}</span></div>
              <div class="kv"><span class="k">招标人</span><span class="v">{{ detailItem.issuerName || '—' }}</span></div>
              <div class="kv"><span class="k">地区</span><span class="v">{{ detailItem.region || '—' }}</span></div>
              <div class="kv"><span class="k">预算金额</span><span class="v">{{ detailItem.amount || '—' }}</span></div>
              <div class="kv"><span class="k">投标截止</span><span class="v">{{ fmtT(detailItem.deadline) }}</span></div>
              <div class="kv">
                <span class="k">距截止</span>
                <span class="v">{{ deadlineText(detailItem.deadline) }}</span>
              </div>
              <div class="kv"><span class="k">所属租户</span><span class="v">{{ detailItem.tenantName || '—' }}（租户 {{ detailItem.tenantId || '—' }}）</span></div>
            </div>
          </div>

          <!-- ② 流程进度 -->
          <div class="pt-block">
            <div class="pt-block-t">流程进度</div>
            <div class="pt-steps-wide">
              <template v-for="(s, i) in STEP_ORDER" :key="s">
                <div class="pt-step-col">
                  <div class="pt-step-badge" :class="stepStateOf(detailItem, i)">
                    <span v-if="stepStateOf(detailItem, i) === 'done'">✓</span>
                    <span v-else>{{ i + 1 }}</span>
                  </div>
                  <div class="pt-step-t">{{ STATE_TEXT[s] }}</div>
                  <div class="pt-step-time">{{ fmtT(stepTimeOf(detailItem, i)) }}</div>
                </div>
                <div
                  v-if="i < STEP_ORDER.length - 1"
                  class="pt-step-bar"
                  :class="{ on: stepStateOf(detailItem, i) === 'done' }"
                ></div>
              </template>
            </div>
            <div v-if="isTerminated(detailItem)" class="pt-term-note">
              {{ detailItem.reviewResult === 'REJECTED' ? '审核不通过，流程结束' : '已取消投标，流程结束' }}
              ：{{ fmtT(detailItem.terminatedTime) }}
              <span v-if="detailItem.reviewReason">（{{ detailItem.reviewReason }}）</span>
            </div>
          </div>

          <!-- ③ 关键时间节点 -->
          <div class="pt-block">
            <div class="pt-block-t">关键时间节点</div>
            <div class="ind-kv">
              <div class="kv"><span class="k">意向登记</span><span class="v">{{ fmtT(detailItem.intentionTime) }}</span></div>
              <div class="kv"><span class="k">报名投标</span><span class="v">{{ fmtT(detailItem.registerTime) }}</span></div>
              <div class="kv"><span class="k">资质审核</span><span class="v">{{ fmtT(detailItem.reviewTime) }}</span></div>
              <div class="kv"><span class="k">递交标书</span><span class="v">{{ fmtT(detailItem.biddedTime) }}</span></div>
              <div class="kv"><span class="k">流程结束</span><span class="v">{{ fmtT(detailItem.terminatedTime) }}</span></div>
              <div class="kv">
                <span class="k">审核结论</span>
                <span class="v">{{ detailItem.reviewResult === 'APPROVED' ? '审核通过' : detailItem.reviewResult === 'REJECTED' ? '审核不通过' : '—' }}</span>
              </div>
            </div>
            <div v-if="detailItem.reviewReason" class="pt-line pt-line-weak" style="margin-top: 8px">审核意见：{{ detailItem.reviewReason }}</div>
          </div>

          <!-- ④ 项目负责人 -->
          <div class="pt-block">
            <div class="pt-block-t">项目负责人</div>
            <div class="pt-owner-card">
              <div class="pt-owner-name">{{ detailItem.ownerName || '—' }}</div>
              <div class="pt-owner-line">{{ detailItem.ownerPost || '—' }}</div>
              <div class="pt-owner-line">{{ detailItem.ownerPhone || '—' }}</div>
            </div>
          </div>

          <!-- ⑤ 标书与备注 -->
          <div v-if="detailItem.documentName || detailItem.remark" class="pt-block">
            <div class="pt-block-t">标书与备注</div>
            <div v-if="detailItem.documentName" class="pt-line">
              标书：{{ detailItem.documentName }}
              <span v-if="fmtSize(detailItem.documentSize)" class="pt-line-weak">（{{ fmtSize(detailItem.documentSize) }}）</span>
            </div>
            <div v-if="detailItem.remark" class="pt-line pt-line-weak">{{ detailItem.remark }}</div>
          </div>

          <!-- ⑥ 操作留痕（GET /detail 的 events[]，只读） -->
          <div class="pt-block">
            <div class="pt-block-t">操作留痕（事件史）</div>
            <div v-if="!detailEvents.length" class="pt-line pt-line-weak">
              {{ detailLoading ? '加载中…' : '暂无事件记录' }}
            </div>
            <div
              v-for="(e, i) in detailEvents"
              :key="`${e.eventType ?? ''}-${tsOf(e.occurredAt)}-${i}`"
              class="pt-ev"
            >
              <div class="pt-ev-time">{{ fmtT(e.occurredAt) }}</div>
              <div class="pt-ev-body">
                <span class="pt-ev-type">{{ e.eventName || EVENT_TEXT[e.eventType ?? ''] || e.eventType || '—' }}</span>
                <span v-if="e.operatorName" class="pt-ev-op">{{ e.operatorName }}</span>
                <span v-if="e.fromState || e.toState" class="pt-ev-op">{{ eventFlowText(e) }}</span>
                <div v-if="e.operationDesc" class="pt-ev-desc">{{ e.operationDesc }}</div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <!-- 流程列表（服务端分页；详情打开时隐藏列表） -->
      <div v-if="!detailOpen" class="ind-card">
        <div class="ind-card-b">
          <Table
            :columns="columns"
            :data-source="rows"
            :loading="loading"
            :pagination="{
              current: pageNo,
              pageSize,
              total,
              showSizeChanger: true,
              showTotal: (t: number) => `共 ${t} 条`,
            }"
            :scroll="{ x: isPlatform ? 1280 : 1080 }"
            row-key="id"
            size="small"
            @change="onTableChange"
          >
            <template #emptyText>
              <Empty :description="emptyText" />
            </template>

            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'project'">
                <div class="pt-proj-name">{{ record.projectName || '—' }}</div>
                <div class="pt-proj-sub">
                  <span class="font-mono">{{ record.projectCode || '—' }}</span>
                  <span class="pt-dot-sep">·</span>
                  <span>{{ record.issuerName || '—' }}</span>
                  <span class="pt-dot-sep">·</span>
                  <span>{{ record.region || '—' }}</span>
                  <span class="pt-dot-sep">·</span>
                  <span>{{ record.amount || '—' }}</span>
                </div>
              </template>

              <template v-else-if="column.key === 'tenant'">
                <div class="pt-tenant">{{ record.tenantName || '—' }}</div>
                <div class="pt-tenant-sub font-mono">租户 {{ record.tenantId || '—' }}</div>
              </template>

              <template v-else-if="column.key === 'flow'">
                <div class="pt-flow">
                  <Tag :color="stateColor(record.state)">{{ stateText(asItem(record)) }}</Tag>
                  <span class="pt-steps">
                    <template v-for="(s, i) in STEP_ORDER" :key="s">
                      <span
                        class="pt-step"
                        :class="stepStateOf(asItem(record), i)"
                        :title="`${STATE_TEXT[s]} ${fmtT(stepTimeOf(asItem(record), i))}`"
                      ></span>
                      <span v-if="i < STEP_ORDER.length - 1" class="pt-step-line"></span>
                    </template>
                  </span>
                </div>
                <div v-if="isTerminated(asItem(record))" class="pt-flow-note">
                  {{ record.reviewResult === 'REJECTED' ? '审核不通过' : '取消投标' }}后结束
                </div>
              </template>

              <template v-else-if="column.key === 'owner'">
                <div class="pt-owner">{{ record.ownerName || '—' }}</div>
                <div class="pt-owner-sub">
                  {{ [record.ownerPost, record.ownerPhone].filter(Boolean).join(' · ') || '—' }}
                </div>
              </template>

              <template v-else-if="column.key === 'deadline'">
                <div>{{ fmtT(record.deadline) }}</div>
                <div v-if="daysLeft(record.deadline)" class="pt-owner-sub">
                  距截止 {{ daysLeft(record.deadline) }} 天
                </div>
                <div v-else-if="record.deadline" class="pt-owner-sub">已截止</div>
              </template>

              <template v-else-if="column.key === 'action'">
                <div class="pt-acts">
                  <a class="cd-link" @click="openDetail(asItem(record))">详情</a>
                  <Tooltip
                    :title="
                      projectCodeOf(asItem(record))
                        ? ''
                        : '该行没有项目编号（projectCode），无法跳转项目台账详情'
                    "
                  >
                    <span class="pt-act-wrap">
                      <a
                        class="cd-link"
                        :class="{ 'pt-act-off': !projectCodeOf(asItem(record)) }"
                        @click="openProjectDetail(asItem(record))"
                      >
                        项目详情
                      </a>
                    </span>
                  </Tooltip>
                  <a class="cd-link" @click="openNotice(asItem(record))">关联公告</a>
                </div>
              </template>
            </template>
          </Table>
        </div>
      </div>
    </div>

    <!-- 关联公告（外部公告公开分页：按 项目编号 / 标段编号 单维查询；行点击新窗口打开门户详情） -->
    <NoticeLinkModal v-model:open="noticeOpen" :item="noticeRow" />
  </div>
</template>

<style scoped>
/* ---------- 项目详情大页面（整页展开，替代原抽屉） ---------- */
.pt-detail-page {
  position: relative;
  padding: 18px 20px 6px;
  margin-bottom: 16px;
  background: hsl(var(--card));
  border: 1px solid hsl(var(--border));
  border-radius: var(--radius);
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.06);
}
.pt-detail-head {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  flex-wrap: wrap;
  padding-bottom: 14px;
  margin-bottom: 14px;
  border-bottom: 1px solid hsl(var(--border));
}
.pt-detail-title-row {
  display: flex;
  gap: 10px;
  align-items: center;
  flex: 1 1 auto;
  min-width: 0;
}
.pt-detail-state {
  font-size: 12.5px;
}
.pt-detail-name {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  line-height: 1.4;
  color: hsl(var(--foreground));
}
.pt-detail-meta-line {
  display: flex;
  gap: 10px;
  align-items: center;
  width: 100%;
  font-size: 12.5px;
  color: hsl(var(--muted-foreground));
}
.pt-detail-back {
  flex: 0 0 auto;
}
/* 详情页头入口组：项目详情 / 关联公告 / 返回列表 */
.pt-detail-tools {
  display: flex;
  flex: 0 0 auto;
  gap: 8px;
  align-items: center;
}
.pt-detail-grid {
  display: grid;
  gap: 12px;
}
.pt-detail-grid .pt-block {
  margin-bottom: 0;
}

/* ---------- 总览指标加载态 ---------- */
.pt-loading {
  opacity: 0.6;
  transition: opacity 0.15s;
}

/* ---------- 筛选条补充 ---------- */
.pt-hit {
  margin-left: auto;
  font-size: 12.5px;
  color: hsl(var(--muted-foreground));
  white-space: nowrap;
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
.pt-tenant {
  font-size: 13px;
  font-weight: 600;
  line-height: 1.5;
  color: hsl(var(--foreground));
}
.pt-tenant-sub {
  margin-top: 2px;
  font-size: 12px;
  color: hsl(var(--muted-foreground));
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

/* ---------- 行操作：详情 / 项目详情 / 关联公告 ---------- */
.pt-acts {
  display: flex;
  gap: 10px;
  align-items: center;
}
/* tooltip 的悬停承接层：置灰的 <a>/<button> 自身不派发鼠标事件，
   必须让这层 span 顶上来，否则"为什么禁用"的说明永远弹不出来 */
.pt-act-wrap {
  display: inline-block;
}
.pt-act-wrap > .ant-btn[disabled] {
  pointer-events: none;
}
/* 无项目编号（projectCode）时的置灰态 */
.pt-act-off {
  color: hsl(var(--muted-foreground));
  cursor: not-allowed;
  opacity: 0.6;
  pointer-events: none;
}

/* ---------- 详情块 ---------- */
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
