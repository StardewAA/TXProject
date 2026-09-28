<!--
  我的关注 · 订阅跟踪（notice-follow 域 · 登录态）
  数据源（01 §3 / 03 §4）：
   - 关注/取消：POST notice-follow/follow|unfollow（targetType+targetKey 幂等）；
   - 我的关注列表：GET notice-follow/item/page（followType 过滤：项目/招标项目/标段）；
   - 时间线：GET item/{itemId}/timeline（NOTICE_PUBLISHED/UPDATED/EXTERNAL_ACTIVE/STAGE_CHANGE）；
   - 已读：POST item/{itemId}/read（清未读，幂等）。
  口径（按用户裁定）：**公告级关注（ANNOUNCEMENT）已下线** —— 门户「关注」只保留「按编号/代码的项目跟踪」
  （PROJECT / TENDER_PROJECT / SECTION）：筛选器不再提供「公告」选项、列表兜底过滤 ANNOUNCEMENT 行；
  每行显式展示「跟踪编号」（= followKey，关注当时的编号快照），名称快照缺失时用维度名+编号兜底，不留空白。
  页面文案口径遵循 02 §5（维度/阶段/事件类型标签）。
-->
<script lang="ts" setup>
import type { FollowEvent, FollowItem, NoticeExternalItem } from './types';

import { computed, onMounted, reactive } from 'vue';
import { useRouter } from 'vue-router';

import {
  fetchFollowTimeline,
  fetchMyFollowItems,
  markFollowItemRead,
  openOrigUrl,
  unfollowTarget,
} from './api';
import { eventSourceText, eventTypeText, fmtDT, followTypeName, stageNameOf } from './format';

const router = useRouter();

/** 维度筛选：**不含公告级**（ANNOUNCEMENT 已下线，门户关注只保留按编号/代码的项目跟踪） */
const FILTERS: Array<{ label: string; value: string }> = [
  { label: '全部', value: '' },
  { label: '项目', value: 'PROJECT' },
  { label: '招标项目', value: 'TENDER_PROJECT' },
  { label: '标段', value: 'SECTION' },
];

const listState = reactive({
  filter: '',
  pageNo: 1,
  loading: false,
  errorMsg: '',
  rows: [] as FollowItem[],
  total: 0,
});
const timelineState = reactive({
  item: null as FollowItem | null,
  pageNo: 1,
  loading: false,
  errorMsg: '',
  events: [] as FollowEvent[],
  total: 0,
  busying: false,
});

const pageSize = 10;
const totalPages = computed(() =>
  timelineState.item ? Math.max(1, Math.ceil(timelineState.total / pageSize)) : 1,
);
const listTotalPages = computed(() => Math.max(1, Math.ceil(listState.total / pageSize)));

/** 跟踪编号（= 关注键 followKey）：维度行就是关注当时的编号/代码，是识别跟踪项的稳定标识 */
function followKeyOf(r: FollowItem): string {
  return String(r.followKey ?? '').trim();
}

/** 行主标题：displayName 为空、或与跟踪编号相同（维度行只落了编号快照）时，
 *  用「维度名 + 编号」兜底 —— 既不能只显示一串编号，更不能整格空白。 */
function titleOf(r: FollowItem): string {
  const name = String(r.displayName ?? '').trim();
  const key = followKeyOf(r);
  if (name && name !== key) return name;
  const typeName = followTypeName(r.followType) || '跟踪项';
  return key ? `${typeName} ${key}` : `未命名${typeName}`;
}

/** 跟踪编号展示兜底：编号缺失时给可读说明，不渲染空白 */
function followKeyText(r: FollowItem): string {
  return followKeyOf(r) || '（服务端未下发编号）';
}

/** 请求序号守卫：快速切筛选/翻页/换关注项时，过期响应不得覆盖最新状态（各自独立计数） */
let listSeq = 0;
let timelineSeq = 0;

/** 公告级关注行已不展示，但服务端 total 仍会把存量行计进去 → 用一次精确计数（pageSize=1 只取 total）
 *  从「全部」总数里扣除，避免「共 N 项」与实际行数不符、分页器多出空页；清库后计数为 0，等价于服务端原值。
 *  计数失败不阻塞列表：退回服务端总数（最坏只是把存量公告级行多算进总数）。 */
async function totalExcludingAnnouncements(serverTotal: number): Promise<number> {
  try {
    const res = await fetchMyFollowItems({ followType: 'ANNOUNCEMENT', pageNo: 1, pageSize: 1 });
    return Math.max(0, serverTotal - (res.total || 0));
  } catch {
    return serverTotal;
  }
}

async function loadList(): Promise<void> {
  const seq = ++listSeq;
  listState.loading = true;
  listState.errorMsg = '';
  try {
    const res = await fetchMyFollowItems({
      followType: listState.filter,
      pageNo: listState.pageNo,
      pageSize,
    });
    if (seq !== listSeq) return; // 过期响应：直接丢弃，不赋值
    // 公告级关注已下线：列表不再出现 ANNOUNCEMENT 行（存量行在此兜底过滤，后端零改动）
    listState.rows = (res.list || []).filter((r) => r.followType !== 'ANNOUNCEMENT');
    // 「全部」视图需要校正总数（选了具体维度时服务端已按 followType 过滤，无需校正）
    if (listState.filter === '') {
      const corrected = await totalExcludingAnnouncements(res.total);
      if (seq !== listSeq) return; // 校正期间已有新请求：过期，丢弃（与上面的守卫同口径）
      listState.total = corrected;
    } else {
      listState.total = res.total;
    }
  } catch (e) {
    if (seq !== listSeq) return; // 过期响应：错误也丢弃
    listState.errorMsg = e instanceof Error ? e.message : String(e);
    listState.rows = [];
    listState.total = 0;
  } finally {
    if (seq === listSeq) listState.loading = false;
  }
}

function pickFilter(v: string): void {
  listState.filter = v;
  listState.pageNo = 1;
  void loadList();
}

function goListPage(p: number): void {
  listState.pageNo = p;
  void loadList();
}

async function loadTimeline(): Promise<void> {
  const item = timelineState.item;
  if (!item) return;
  const seq = ++timelineSeq;
  timelineState.loading = true;
  timelineState.errorMsg = '';
  try {
    const res = await fetchFollowTimeline(item.id, { pageNo: timelineState.pageNo, pageSize });
    if (seq !== timelineSeq) return; // 过期响应：直接丢弃，不赋值
    timelineState.events = res.list;
    timelineState.total = res.total;
  } catch (e) {
    if (seq !== timelineSeq) return; // 过期响应：错误也丢弃
    timelineState.errorMsg = e instanceof Error ? e.message : String(e);
    timelineState.events = [];
    timelineState.total = 0;
  } finally {
    if (seq === timelineSeq) timelineState.loading = false;
  }
}

/** 打开关注项时间线：先拉事件（时间线加载成功后才视为“已查看”），成功后再标记已读（清未读）；时间线失败则不标读（未读保留） */
async function openItem(item: FollowItem): Promise<void> {
  timelineState.item = item;
  timelineState.pageNo = 1;
  timelineState.busying = true;
  resetFallback();
  try {
    await loadTimeline();
    // 时间线加载成功（errorMsg 为空）才标已读；失败保留未读，错误已由 loadTimeline 按页面现有风格提示
    if (!timelineState.errorMsg) {
      // P3 兜底（不阻塞已读标记）：PROJECT 维度无任何动态时，按项目编号回查已发布公告
      if (isProjectDimension(item) && !timelineState.events.length) {
        void loadProjectFallback(item);
      }
      try {
        await markFollowItemRead(item.id);
        // 刷新列表未读数（原行可能已变化）
        const cur = listState.rows.find((r) => String(r.id) === String(item.id));
        if (cur) cur.unreadCount = 0;
      } catch {
        /* 已读失败不阻塞查看（未读保留，可稍后手动点“已读”） */
      }
    }
  } finally {
    timelineState.busying = false;
  }
}

function goTimelinePage(p: number): void {
  timelineState.pageNo = p;
  void loadTimeline();
}

function backToList(): void {
  timelineState.item = null;
  timelineState.events = [];
  timelineState.total = 0;
  resetFallback();
  void loadList();
}

async function markReadFromList(item: FollowItem): Promise<void> {
  try {
    await markFollowItemRead(item.id);
    item.unreadCount = 0;
  } catch (e) {
    listState.errorMsg = e instanceof Error ? e.message : String(e);
  }
}

async function doUnfollow(item: FollowItem): Promise<void> {
  if (!item.followType || !item.followKey) return;
  if (!window.confirm(`确定取消关注「${titleOf(item)}」吗？`)) return;
  try {
    await unfollowTarget({ targetType: item.followType, targetKey: item.followKey });
    await loadList();
  } catch (e) {
    listState.errorMsg = e instanceof Error ? e.message : String(e);
  }
}

/** 时间线行是否展示“查看公告详情”（BR-3/BR-4：仅 EXTERNAL 且平台详情可达；INTERNAL 公开详情门户未接入，预留） */
function showEventDetail(event: FollowEvent): boolean {
  if (!event.noticeId || event.source !== 'EXTERNAL') return false;
  if (event.available === false) return false; // 公告已下线/归档：隐藏平台详情跳转
  return true;
}

/** 事件跳转：EXTERNAL 可达 → 本平台详情；不可达/INTERNAL → 有原网址走 go 中转（源站原文），否则纯文本 */
function openEvent(event: FollowEvent): void {
  if (event.source !== 'EXTERNAL') return;
  if (showEventDetail(event)) {
    router.push(`/portal/bidding/detail/${event.noticeId}`).catch(() => {
      /* 重复导航忽略 */
    });
    return;
  }
  openOrigUrl(event.origUrl);
}

/* ================= P3：维度关注「暂无匹配动态」可见信号 + PROJECT 维度历史公告兜底 =================
 * 背景（本机库 + 运行中网关实测 2026-09-22，证据见 docs/电子证照前前端契约/
 *   后端变更请求-关注扇出重放补偿与维度回填-2026-09-29.md §四）：
 *   · 维度关注在关注时**不回填历史公告**（FollowServiceImpl.seedInitialFeedback 对非 ANNOUNCEMENT 直接 return），
 *     且线上真实扇出为 0 条 → 维度关注的时间线从关注那一刻起就是空的，用户体感是“关注功能坏了”；
 *   · 公开分页 GET /notice-external/page **支持入参 projectCode**（实测生效，传不存在的值 total 归零），
 *     但 tenderProjectCode / bidSectionCode **被后端静默丢弃**（不报错、直接返回未过滤的 1637 条全量页）。
 *
 * 兜底口径（仅 PROJECT，不做 TENDER_PROJECT / SECTION）：
 *   · 只对确有查询能力的维度反查，其余维度**如实说明原因**，不拿全量数据冒充；
 *   · 反查结果**逐行回验** projectKey 是否等于关注键——关键防御：后端是“静默忽略未知入参”语义，
 *     一旦 projectCode 契约回退，不设防就会把 1600+ 条全量公告当成该项目公告展示（比不显示更糟）；
 *   · 兜底列表**不冒充“动态”**：关注前发布的标「关注前已发布」；关注后发布却没进动态的标
 *     「关注后发布 · 提醒可能遗漏」——这正是 P0/P1 扇出缺口的直接证据，对使用方如实可见、不掩盖。
 */
const FALLBACK_PAGE_SIZE = 10;

const fallbackState = reactive({
  loading: false,
  errorMsg: '',
  rows: [] as NoticeExternalItem[],
  /** 后端未按 projectCode 过滤（回验失败）→ 整块降级隐藏，绝不展示不可信列表 */
  unverified: false,
});
let fallbackSeq = 0;

function resetFallback(): void {
  fallbackSeq++;
  fallbackState.loading = false;
  fallbackState.errorMsg = '';
  fallbackState.rows = [];
  fallbackState.unverified = false;
}

/** 仅 PROJECT 维度可兜底（依据：project_code 实测可查询；招标项目/标段两维入参被静默丢弃） */
function isProjectDimension(item: FollowItem | null): boolean {
  return item?.followType === 'PROJECT' && !!String(item.followKey || '').trim();
}

/** 「暂无匹配动态」的原因文案：区分“公告还没动”与“来源根本没给编号、注定匹配不上” */
function noDynamicReason(item: FollowItem | null): string {
  switch (item?.followType) {
    case 'PROJECT': {
      return '该项目暂无变更动态。项目编号在外部公告中覆盖率约 87%，匹配不到通常是提醒链路尚未产生事件。';
    }
    case 'SECTION': {
      return '外部采集公告多数不含标段编号（实测覆盖率约 11%），多数公告无法与该标段关联，因此难以匹配到动态。';
    }
    case 'TENDER_PROJECT': {
      return '外部采集公告几乎不含招标项目编码（实测覆盖率约 2%），绝大多数公告无法与该招标项目关联，因此难以匹配到动态。';
    }
    default: {
      return '暂无匹配动态。';
    }
  }
}

/** 时间字段归一化为毫秒（后端 epoch 毫秒 / mock 字符串两种形态都吃） */
function toMs(v: null | number | string | undefined): number {
  if (v === null || v === undefined || v === '') return 0;
  if (typeof v === 'number') return v;
  const n = Number(v);
  if (Number.isFinite(n) && n > 0) return n;
  const t = Date.parse(String(v).replace(' ', 'T'));
  return Number.isNaN(t) ? 0 : t;
}

/** 兜底行的时间定性：关注前发布 / 关注后发布却没提醒（后者即扇出缺口的可见证据） */
function fallbackTiming(n: NoticeExternalItem): { cls: string; label: string } {
  const followAt = toMs(timelineState.item?.followTime);
  const pubAt = toMs(n.publishTime);
  if (!followAt || !pubAt) return { cls: '', label: '已发布' };
  if (pubAt < followAt) return { cls: '', label: '关注前已发布' };
  return { cls: 'warn', label: '关注后发布 · 提醒可能遗漏' };
}

/** 按项目编号反查已发布公告（公开只读接口；逐行回验 projectKey 防止入参被静默忽略） */
async function loadProjectFallback(item: FollowItem): Promise<void> {
  const code = String(item.followKey || '').trim();
  if (!code) return;
  const seq = ++fallbackSeq;
  fallbackState.loading = true;
  fallbackState.errorMsg = '';
  fallbackState.unverified = false;
  fallbackState.rows = [];
  try {
    const { fetchNoticePage } = await import('./api');
    const res = await fetchNoticePage({ pageNo: 1, pageSize: FALLBACK_PAGE_SIZE, projectCode: code });
    if (seq !== fallbackSeq) return; // 过期响应：直接丢弃
    const list = res.list || [];
    const verified = list.filter((n) => String(n.projectKey || '').trim() === code);
    if (list.length > 0 && verified.length === 0) {
      // 后端未按 projectCode 过滤（静默忽略入参）→ 不展示，避免把全量公告冒充本项目公告
      fallbackState.unverified = true;
      return;
    }
    fallbackState.rows = verified;
  } catch (e) {
    if (seq !== fallbackSeq) return;
    fallbackState.errorMsg = e instanceof Error ? e.message : String(e);
  } finally {
    if (seq === fallbackSeq) fallbackState.loading = false;
  }
}

/** 兜底行直达平台公告详情（字符串拼接而非模板串，避免与外层编辑脚本的转义冲突） */
function openFallbackNotice(n: NoticeExternalItem): void {
  router.push('/portal/bidding/detail/' + n.id).catch(() => {
    /* 重复导航忽略 */
  });
}

const pagerNums = (cur: number, last: number): ('…' | number)[] => {
  const nums: ('…' | number)[] = [];
  const push = (n: number) => nums.push(n);
  push(1);
  for (let i = Math.max(2, cur - 1); i <= Math.min(last - 1, cur + 1); i++) push(i);
  if (last > 1) push(last);
  const out: ('…' | number)[] = [];
  nums.forEach((n, i) => {
    const prev = nums[i - 1];
    if (typeof n === 'number' && typeof prev === 'number' && n - prev > 1) out.push('…');
    if (out[out.length - 1] !== n) out.push(n);
  });
  return out;
};

onMounted(() => {
  void loadList();
});
</script>

<template>
<!--
  门户 · 我的订阅/动态（关注项目的公告变更与审核动态）
  业务：登录人视角的“订阅动态”：我关注的外部公告/本平台公告有新发布/变更/审核通过事件时在此提醒，支持已读管理。
  理由：动态按登录自然人账号(user_id)隔离（A 方案：资格归企业、关注与跟踪归个人）；关注即可获提醒，不要求主体完善（需求 Task2/09 §2）。
    依据（法规/政府文件/法律）：
      · 招标公告和公示信息发布管理办法（发改委令第10号，部门规章/政府文件）第12/13条——免费、及时查阅完整公告公示信息；免费归类与检索、新公告醒目标识；
      · 电子招标投标办法（八部委令第20号，部门规章/政府文件）第9条——市场主体免费注册登录、获取依法公开的招标投标信息；
      · 招标投标法 第16条（法律）——公开招标应当发布招标公告（公告公开性的上位依据）。
    后端契约：09-投标流程跟踪前端交付-业务口径说明.md §8（事件 fanout NOTICE_PUBLISHED/UPDATED、EXTERNAL_ACTIVE → 关注者事件落库；时间线/已读）；
      外部公告-前端对接/后续后端配合说明.md；订阅/事件端点 T 轨按 getLoginUserId() 隔离。
-->

  <div class="sub-page bid-ind">
    <div class="top-bar">
      <button class="back" @click="router.push('/portal/bidding').catch(() => {})">‹ 返回公告大厅</button>
      <span class="sep">|</span>
      <span class="crumb">我的关注 · 订阅跟踪</span>
      <span class="crumb-sub">项目/招标项目/标段 · 新动态提醒（未读数角标，点开即已读）</span>
    </div>

    <!-- ============ 列表视图 ============ -->
    <template v-if="!timelineState.item">
      <div class="filter-row">
        <span
          v-for="f in FILTERS"
          :key="f.value"
          class="opt kbd-btn"
          :class="{ on: listState.filter === f.value }"
          role="button"
          tabindex="0"
          @click="pickFilter(f.value)"
          @keydown.enter="pickFilter(f.value)"
        >{{ f.label }}</span>
        <span class="sum">共 {{ listState.total }} 项关注</span>
      </div>

      <div v-if="listState.errorMsg" class="msg-box error">{{ listState.errorMsg }}</div>
      <div v-if="listState.loading" class="msg-box">加载中…</div>

      <div v-else-if="!listState.rows.length && !listState.errorMsg" class="empty-panel">
        <p>暂无关注内容{{ listState.filter ? '（当前筛选下）' : '' }}。</p>
        <p class="tip">在公告详情的「项目跟踪」里点「＋ 跟踪该项目」（或关注某个标段）后，新到的外部公告与动态会出现在这里并产生未读提醒；关注按编号/代码跟踪，不含公告级关注。</p>
        <button class="btn" @click="router.push('/portal/bidding').catch(() => {})">去公告大厅看看 →</button>
      </div>

      <div v-else class="card-list">
        <div class="frow head">
          <div class="f-name">关注内容（维度 / 阶段 / 最近动态）</div>
          <div class="f-unread">未读</div>
          <div class="f-act">操作</div>
        </div>
        <div
          v-for="r in listState.rows"
          :key="r.id"
          class="frow kbd-btn"
          role="button"
          tabindex="0"
          @click="openItem(r)"
          @keydown.enter="openItem(r)"
        >
          <div class="f-name">
            <div class="f-title">
              <span class="chip t">{{ followTypeName(r.followType) }}</span>
              <span class="chip s" v-if="stageNameOf(r.stageCode, r.stageName)">{{ stageNameOf(r.stageCode, r.stageName) }}</span>
              <span class="f-text" :class="{ unread: (r.unreadCount || 0) > 0 }">{{ titleOf(r) }}</span>
            </div>
            <div class="f-meta">
              <!-- 跟踪编号：关注当时的编号/代码快照（followKey）——名称快照可能缺失或与之相同，故显式成对展示 -->
              <span class="f-key"><b>跟踪编号</b>{{ followKeyText(r) }}</span>
              <span v-if="r.issuerName">{{ r.issuerName }}</span>
              <span v-if="fmtDT(r.followTime)">关注于 {{ fmtDT(r.followTime) }}</span>
              <span v-if="fmtDT(r.latestDynamicTime)">最近动态 {{ fmtDT(r.latestDynamicTime) }}</span>
              <span v-else class="no-dyn" :title="noDynamicReason(r)">暂无动态 ⓘ</span>
            </div>
          </div>
          <div class="f-unread">
            <span v-if="(r.unreadCount || 0) > 0" class="badge">{{ r.unreadCount }}</span>
            <span v-else class="none">—</span>
          </div>
          <div class="f-act" @click.stop>
            <button class="mini" @click="openItem(r)">动态</button>
            <button class="mini" :disabled="!r.unreadCount" @click="markReadFromList(r)">已读</button>
            <button class="mini danger" @click="doUnfollow(r)">取消关注</button>
          </div>
        </div>
      </div>

      <div class="pager" v-if="listTotalPages > 1">
        <span
class="pg kbd-btn" :class="{ off: listState.pageNo === 1 }" role="button" tabindex="0"
          @click="listState.pageNo > 1 && goListPage(listState.pageNo - 1)"
          @keydown.enter="listState.pageNo > 1 && goListPage(listState.pageNo - 1)"
>‹</span>
        <span
v-for="(p, i) in pagerNums(listState.pageNo, listTotalPages)" :key="i" class="pg kbd-btn"
          :class="{ on: p === listState.pageNo, dot: p === '…' }" role="button" tabindex="0"
          @click="typeof p === 'number' && goListPage(p)"
          @keydown.enter="typeof p === 'number' && goListPage(p)"
>{{ p }}</span>
        <span
class="pg kbd-btn" :class="{ off: listState.pageNo === listTotalPages }" role="button" tabindex="0"
          @click="listState.pageNo < listTotalPages && goListPage(listState.pageNo + 1)"
          @keydown.enter="listState.pageNo < listTotalPages && goListPage(listState.pageNo + 1)"
>›</span>
      </div>
    </template>

    <!-- ============ 时间线视图 ============ -->
    <template v-else>
      <div class="tl-head">
        <button class="back" @click="backToList()">‹ 返回关注列表</button>
        <div class="tl-title">
          <span class="chip t">{{ followTypeName(timelineState.item.followType) }}</span>
          <span class="tl-name">{{ titleOf(timelineState.item) }}</span>
          <span class="tl-meta">跟踪编号：{{ followKeyText(timelineState.item) }}</span>
          <span class="tl-meta" v-if="timelineState.item.issuerName">{{ timelineState.item.issuerName }}</span>
          <span class="tl-meta" v-if="stageNameOf(timelineState.item.stageCode, timelineState.item.stageName)">阶段：{{ stageNameOf(timelineState.item.stageCode, timelineState.item.stageName) }}</span>
          <span class="tl-meta">已读后未读清零（时间线加载成功后标记已读）</span>
        </div>
      </div>

      <div v-if="timelineState.loading" class="msg-box">加载中…</div>
      <div v-else-if="timelineState.errorMsg" class="msg-box error">{{ timelineState.errorMsg }}</div>

      <div v-else-if="!timelineState.events.length" class="empty-panel">
        <p>该关注项暂无动态。</p>
        <!-- P3 可见信号：不再只给一句“暂无动态”，而是按维度说清“为什么匹配不到” -->
        <p class="tip">{{ noDynamicReason(timelineState.item) }}</p>
        <p class="tip">外部公告审核生效（EXTERNAL_ACTIVE）、公告发布 / 更新、阶段流转等动态会出现在这里。</p>

        <!-- P3 兜底：PROJECT 维度按项目编号反查已发布公告（明确标注“非动态”与时间定性，不冒充提醒） -->
        <div v-if="isProjectDimension(timelineState.item)" class="fb-box">
          <div class="fb-head">
            <span class="fb-t">该关注项下已发布的公告</span>
            <span class="fb-sub">按项目编号 {{ timelineState.item?.followKey }} 反查公开公告库 · 非变更动态</span>
          </div>
          <div v-if="fallbackState.loading" class="fb-msg">查询中…</div>
          <div v-else-if="fallbackState.errorMsg" class="fb-msg err">兜底查询失败：{{ fallbackState.errorMsg }}</div>
          <div v-else-if="fallbackState.unverified" class="fb-msg err">
            公开接口未按项目编号过滤（返回结果与关注键不匹配），已隐藏列表，以免把全量公告误当成本项目公告。
          </div>
          <div v-else-if="!fallbackState.rows.length" class="fb-msg">
            公开公告库中还没有该编号下的公告（外部公告仅约 87% 携带项目编号）。
          </div>
          <template v-else>
            <div v-for="n in fallbackState.rows" :key="n.id" class="fb-row">
              <div class="fb-main">
                <div class="fb-title">{{ n.title }}</div>
                <div class="fb-meta">
                  <span class="chip" :class="fallbackTiming(n).cls">{{ fallbackTiming(n).label }}</span>
                  <span v-if="n.issuerName">{{ n.issuerName }}</span>
                  <span v-if="fmtDT(n.publishTime)">{{ fmtDT(n.publishTime) }}</span>
                  <span v-if="n.sourceName">{{ n.sourceName }}</span>
                </div>
              </div>
              <div class="fb-act">
                <button class="mini primary" @click="openFallbackNotice(n)">公告详情</button>
              </div>
            </div>
            <p class="fb-note">
              兜底列表由公开查询接口按项目编号反查得来，<b>不代表已产生提醒</b>；标注
              「关注后发布 · 提醒可能遗漏」的公告，说明提醒链路存在遗漏（已向后端提出重放 / 补偿需求）。
            </p>
          </template>
        </div>
      </div>

      <div v-else class="tl-list">
        <div v-for="e in timelineState.events" :key="e.id" class="tl-row" :class="{ unread: e.readFlag === false }">
          <div class="tl-main">
            <div class="tl-tags">
              <span class="chip">{{ eventSourceText(e.source) }}</span>
              <span class="chip b">{{ eventTypeText(e.eventType) }}</span>
              <span v-if="e.readFlag === false" class="chip unread-chip">未读</span>
              <span v-if="e.available === false" class="chip off" title="公告已下线/归档，仅可经源站原文查看（BR-4）">公告已下线</span>
              <span class="tl-time">{{ fmtDT(e.occurredAt) }}</span>
            </div>
            <div class="tl-text">{{ e.titleSnapshot || '（无标题快照）' }}</div>
          </div>
          <div class="tl-act">
            <button
              v-if="showEventDetail(e)"
              class="mini primary"
              @click="openEvent(e)"
            >
查看公告详情
</button>
            <button v-if="e.origUrl && e.source === 'EXTERNAL'" class="mini" @click="openOrigUrl(e.origUrl)">源站原文 ↗</button>
          </div>
        </div>
      </div>

      <div class="pager" v-if="totalPages > 1">
        <span
class="pg kbd-btn" :class="{ off: timelineState.pageNo === 1 }" role="button" tabindex="0"
          @click="timelineState.pageNo > 1 && goTimelinePage(timelineState.pageNo - 1)"
          @keydown.enter="timelineState.pageNo > 1 && goTimelinePage(timelineState.pageNo - 1)"
>‹</span>
        <span
v-for="(p, i) in pagerNums(timelineState.pageNo, totalPages)" :key="i" class="pg kbd-btn"
          :class="{ on: p === timelineState.pageNo, dot: p === '…' }" role="button" tabindex="0"
          @click="typeof p === 'number' && goTimelinePage(p)"
          @keydown.enter="typeof p === 'number' && goTimelinePage(p)"
>{{ p }}</span>
        <span
class="pg kbd-btn" :class="{ off: timelineState.pageNo === totalPages }" role="button" tabindex="0"
          @click="timelineState.pageNo < totalPages && goTimelinePage(timelineState.pageNo + 1)"
          @keydown.enter="timelineState.pageNo < totalPages && goTimelinePage(timelineState.pageNo + 1)"
>›</span>
      </div>
    </template>
  </div>
</template>

<style scoped>
.sub-page {
  background: hsl(var(--background-deep));
  min-height: 100%;
  padding: 16px 20px 30px;
}
.top-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}
.back {
  font-size: 13px;
  color: hsl(var(--primary));
  font-weight: 700;
  cursor: pointer;
  border: 1px solid hsl(var(--primary) / 30%);
  background: hsl(var(--primary) / 8%);
  border-radius: 4px;
  padding: 5px 12px;
}
.sep {
  color: hsl(var(--border));
}
.crumb {
  font-size: 15px;
  font-weight: 800;
  color: hsl(var(--foreground));
}
.crumb-sub {
  font-size: 12px;
  color: hsl(var(--muted-foreground));
}
.filter-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px 8px;
  background: hsl(var(--card));
  border: 1px solid hsl(var(--border));
  border-radius: 6px;
  padding: 8px 14px;
  margin-bottom: 10px;
}
.opt {
  font-size: 13px;
  color: hsl(var(--muted-foreground));
  padding: 2px 12px;
  cursor: pointer;
  border-radius: 3px;
}
.opt.on {
  background: hsl(var(--primary));
  color: hsl(var(--primary-foreground));
  font-weight: 600;
}
.sum {
  margin-left: auto;
  color: hsl(var(--muted-foreground));
  font-size: 12px;
}
.msg-box {
  padding: 30px 12px;
  text-align: center;
  color: hsl(var(--muted-foreground));
  font-size: 13px;
  background: hsl(var(--card));
  border: 1px solid hsl(var(--border));
  border-radius: 6px;
}
.msg-box.error {
  color: hsl(var(--destructive));
  background: hsl(var(--destructive) / 8%);
  border-color: hsl(var(--destructive) / 35%);
}
.empty-panel {
  background: hsl(var(--card));
  border: 1px solid hsl(var(--border));
  border-radius: 8px;
  padding: 46px 20px;
  text-align: center;
  color: hsl(var(--muted-foreground));
  font-size: 14px;
}
.empty-panel .tip {
  color: hsl(var(--muted-foreground));
  font-size: 12.5px;
  line-height: 1.8;
}
.btn {
  border-radius: 4px;
  padding: 8px 22px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  border: 1px solid transparent;
  background: hsl(var(--primary));
  color: hsl(var(--primary-foreground));
  margin-top: 8px;
}
.card-list {
  background: hsl(var(--card));
  border: 1px solid hsl(var(--border));
  border-radius: 6px;
  overflow: hidden;
}
.frow {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 70px 220px;
  gap: 10px;
  align-items: center;
  padding: 10px 14px;
  border-bottom: 1px solid hsl(var(--border));
  cursor: pointer;
}
.frow:hover {
  background: hsl(var(--primary) / 5%);
}
.frow.head {
  background: hsl(var(--background-deep));
  cursor: default;
  font-size: 12px;
  color: hsl(var(--muted-foreground));
  font-weight: 700;
}
.frow:last-child {
  border-bottom: 0;
}
.f-name {
  min-width: 0;
}
.f-title {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}
.f-text {
  font-size: 13.5px;
  font-weight: 700;
  color: hsl(var(--foreground));
  word-break: break-all;
}
.f-text.unread {
  color: hsl(var(--primary));
}
.f-meta {
  display: flex;
  gap: 14px;
  flex-wrap: wrap;
  color: hsl(var(--muted-foreground));
  font-size: 11.5px;
  margin-top: 3px;
}
/* 跟踪编号（= followKey）：识别跟踪项的稳定标识，用前景色压过普通 meta 的灰字 */
.f-key {
  color: hsl(var(--foreground));
  word-break: break-all;
}
.f-key b {
  font-weight: 400;
  color: hsl(var(--muted-foreground));
  margin-right: 4px;
}
.chip {
  display: inline-flex;
  padding: 0 7px;
  border-radius: 3px;
  font-size: 11px;
  line-height: 1.8;
  background: hsl(var(--muted) / 60%);
  color: hsl(var(--muted-foreground));
  white-space: nowrap;
}
.chip.t {
  background: hsl(var(--primary) / 8%);
  color: hsl(var(--primary));
}
.chip.s {
  background: hsl(var(--success) / 12%);
  color: var(--ind-ok);
}
.chip.b {
  background: hsl(var(--warning) / 12%);
  color: var(--ind-warn);
}
.chip.off {
  background: hsl(var(--destructive) / 8%);
  color: hsl(var(--destructive));
}
.f-unread {
  text-align: center;
}
.badge {
  display: inline-grid;
  place-items: center;
  min-width: 20px;
  height: 20px;
  padding: 0 6px;
  border-radius: 10px;
  background: hsl(var(--destructive));
  color: hsl(var(--primary-foreground));
  font-size: 12px;
  font-weight: 700;
}
.none {
  color: hsl(var(--muted-foreground));
}
.f-act {
  display: flex;
  gap: 6px;
  justify-content: flex-end;
}
.mini {
  font-size: 12px;
  color: hsl(var(--muted-foreground));
  border: 1px solid hsl(var(--border));
  background: hsl(var(--card));
  border-radius: 4px;
  padding: 3px 10px;
  cursor: pointer;
  white-space: nowrap;
}
.mini.primary {
  color: hsl(var(--primary));
  border-color: hsl(var(--primary) / 30%);
  background: hsl(var(--primary) / 8%);
  font-weight: 600;
}
.mini.danger {
  color: hsl(var(--destructive));
  border-color: hsl(var(--destructive) / 35%);
}
.mini:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.pager {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 5px;
  padding: 12px 2px 8px;
  color: hsl(var(--muted-foreground));
  font-size: 12px;
}
.pg {
  min-width: 26px;
  height: 26px;
  border-radius: 4px;
  border: 1px solid hsl(var(--border));
  background: hsl(var(--card));
  display: grid;
  place-items: center;
  font-size: 12px;
  color: hsl(var(--muted-foreground));
  cursor: pointer;
}
.pg.on {
  background: hsl(var(--primary));
  color: hsl(var(--primary-foreground));
  border-color: hsl(var(--primary));
}
.pg.off {
  opacity: 0.45;
  cursor: not-allowed;
}
.pg.dot {
  border: 0;
  background: transparent;
  cursor: default;
}
.tl-head {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  background: hsl(var(--card));
  border: 1px solid hsl(var(--border));
  border-radius: 6px;
  padding: 14px 16px;
  margin-bottom: 10px;
}
.tl-title {
  min-width: 0;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}
.tl-name {
  font-size: 15px;
  font-weight: 800;
  color: hsl(var(--foreground));
  word-break: break-all;
}
.tl-meta {
  color: hsl(var(--muted-foreground));
  font-size: 12px;
  margin-left: 4px;
}
.tl-list {
  background: hsl(var(--card));
  border: 1px solid hsl(var(--border));
  border-radius: 6px;
  overflow: hidden;
}
.tl-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 16px;
  border-bottom: 1px solid hsl(var(--border));
}
.tl-row:last-child {
  border-bottom: 0;
}
.tl-row.unread {
  background: hsl(var(--primary) / 5%);
}
.tl-tags {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}
.unread-chip {
  background: hsl(var(--destructive));
  color: hsl(var(--primary-foreground));
}
.tl-time {
  margin-left: 2px;
  color: hsl(var(--muted-foreground));
  font-size: 11.5px;
}
.tl-text {
  margin-top: 5px;
  font-size: 13.5px;
  color: hsl(var(--foreground));
  line-height: 1.7;
  word-break: break-all;
}
.tl-act {
  flex: none;
  display: flex;
  gap: 6px;
}
/* 键盘可达性：可点击 span/div（筛选 tab、列表行、分页）聚焦可见 */
.kbd-btn:focus-visible {
  outline: 2px solid hsl(var(--primary));
  outline-offset: 2px;
}
/* 小屏响应式：操作列去掉固定 220px，列表行/时间线纵向堆叠 */
@media (max-width: 768px) {
  .frow {
    grid-template-columns: minmax(0, 1fr);
    gap: 8px;
  }
  .frow.head {
    display: none;
  }
  .f-act {
    justify-content: flex-start;
    flex-wrap: wrap;
  }
  .tl-row {
    flex-direction: column;
    align-items: stretch;
  }
  .tl-act {
    flex-wrap: wrap;
  }
  .pager {
    justify-content: center;
    flex-wrap: wrap;
  }
}

/* ===== P3 兜底区块（空态面板内，左对齐并限宽；沿用页面既有 token，不新造色值） ===== */
.empty-panel .fb-box {
  margin: 18px auto 0;
  max-width: 760px;
  text-align: left;
  background: hsl(var(--background-deep));
  border: 1px solid hsl(var(--border));
  border-radius: 6px;
  padding: 12px 14px;
}
.fb-head {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 8px;
  padding-bottom: 8px;
  border-bottom: 1px solid hsl(var(--border));
  margin-bottom: 8px;
}
.fb-t {
  font-size: 13px;
  font-weight: 700;
  color: hsl(var(--foreground));
}
.fb-sub {
  font-size: 11.5px;
  color: hsl(var(--muted-foreground));
}
.fb-msg {
  font-size: 12.5px;
  color: hsl(var(--muted-foreground));
  line-height: 1.8;
  padding: 4px 0;
}
.fb-msg.err {
  color: hsl(var(--destructive));
}
.fb-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 0;
  border-bottom: 1px dashed hsl(var(--border));
}
.fb-row:last-of-type {
  border-bottom: 0;
}
.fb-main {
  min-width: 0;
}
.fb-title {
  font-size: 13px;
  font-weight: 600;
  color: hsl(var(--foreground));
  line-height: 1.6;
  word-break: break-all;
}
.fb-meta {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  align-items: center;
  color: hsl(var(--muted-foreground));
  font-size: 11.5px;
  margin-top: 3px;
}
.fb-act {
  flex: none;
}
.fb-note {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid hsl(var(--border));
  font-size: 11.5px;
  color: hsl(var(--muted-foreground));
  line-height: 1.9;
}
.fb-note b {
  color: var(--ind-warn);
}
.chip.warn {
  background: hsl(var(--warning) / 15%);
  color: var(--ind-warn);
}
.no-dyn {
  cursor: help;
}
</style>
