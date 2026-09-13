<!--
  我的关注 · 订阅跟踪（notice-follow 域 · 登录态）
  数据源（01 §3 / 03 §4）：
   - 关注/取消：POST notice-follow/follow|unfollow（targetType+targetKey 幂等）；
   - 我的关注列表：GET notice-follow/item/page（followType 过滤：公告/项目/招标项目/标段）；
   - 时间线：GET item/{itemId}/timeline（NOTICE_PUBLISHED/UPDATED/EXTERNAL_ACTIVE/STAGE_CHANGE）；
   - 已读：POST item/{itemId}/read（清未读，幂等）。
  说明：公告级关注（ANNOUNCEMENT）同样在列表展示（“我关注的公告”）；公告行 displayName 在后端
  扇出前暂存 targetKey，前端在 api 层用公开 detail 回查补全标题/发布单位（见 docs 后端说明 N2）。
  页面文案口径遵循 02 §5（维度/阶段/事件类型标签）。
-->
<script lang="ts" setup>
import type { FollowEvent, FollowItem } from './types';

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

const FILTERS: Array<{ label: string; value: string }> = [
  { label: '全部', value: '' },
  { label: '公告', value: 'ANNOUNCEMENT' },
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

/** 公告级关注行补名：displayName 仍为数字 id（后端扇出前暂存 targetKey）→ 用公开 detail 回查补全 */
async function enrichAnnouncementRows(rows: FollowItem[]): Promise<void> {
  const { fetchNoticeDetail } = await import('./api');
  await Promise.all(
    rows
      .filter(
        (r) =>
          r.followType === 'ANNOUNCEMENT' &&
          r.followKey &&
          /^\d+$/.test(String(r.followKey)) &&
          (!r.displayName || String(r.displayName) === String(r.followKey)),
      )
      .map(async (r) => {
        try {
          const d = await fetchNoticeDetail(String(r.followKey));
          r.displayName = d.title || r.displayName;
          r.issuerName = d.issuerName || r.issuerName;
        } catch {
          /* 已下架/非公开：保留原样 */
        }
      }),
  );
}

/** 请求序号守卫：快速切筛选/翻页/换关注项时，过期响应不得覆盖最新状态（各自独立计数） */
let listSeq = 0;
let timelineSeq = 0;

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
    listState.rows = res.list;
    listState.total = res.total;
    await enrichAnnouncementRows(listState.rows);
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
  try {
    await loadTimeline();
    // 时间线加载成功（errorMsg 为空）才标已读；失败保留未读，错误已由 loadTimeline 按页面现有风格提示
    if (!timelineState.errorMsg) {
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

/** 公告级关注行直达平台公告详情（available!==false 时） */
function goDetail(item: FollowItem): void {
  if (
    item.followType !== 'ANNOUNCEMENT' ||
    !item.followKey ||
    /^\d+$/.test(String(item.followKey)) === false
  ) {
    return;
  }
  router
    .push(`/portal/bidding/detail/${item.followKey}`)
    .catch(() => {
      /* 重复导航忽略 */
    });
}

async function doUnfollow(item: FollowItem): Promise<void> {
  if (!item.followType || !item.followKey) return;
  if (!window.confirm(`确定取消关注「${item.displayName || item.followKey}」吗？`)) return;
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
      <span class="crumb-sub">公告/项目/招标项目/标段 · 新动态提醒（未读数角标，点开即已读）</span>
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
        <p class="tip">在公告大厅/公告详情点击“关注”，或关注某个项目后，新到的外部公告与动态会出现在这里并产生未读提醒。</p>
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
              <span v-if="r.followType === 'ANNOUNCEMENT' && r.available === false" class="chip off" title="该公告已下线/归档，平台详情不可达">公告已下线</span>
              <span class="f-text" :class="{ unread: (r.unreadCount || 0) > 0 }">{{ r.displayName || r.followKey }}</span>
            </div>
            <div class="f-meta">
              <span v-if="r.issuerName">{{ r.issuerName }}</span>
              <span v-if="fmtDT(r.followTime)">关注于 {{ fmtDT(r.followTime) }}</span>
              <span v-if="fmtDT(r.latestDynamicTime)">最近动态 {{ fmtDT(r.latestDynamicTime) }}</span>
              <span v-else>暂无动态</span>
            </div>
          </div>
          <div class="f-unread">
            <span v-if="(r.unreadCount || 0) > 0" class="badge">{{ r.unreadCount }}</span>
            <span v-else class="none">—</span>
          </div>
          <div class="f-act" @click.stop>
            <button
              v-if="r.followType === 'ANNOUNCEMENT' && r.available !== false && r.followKey && /^\d+$/.test(String(r.followKey))"
              class="mini primary"
              @click="goDetail(r)"
            >
公告详情
</button>
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
          <span class="tl-name">{{ timelineState.item.displayName || timelineState.item.followKey }}</span>
          <span class="tl-meta" v-if="timelineState.item.issuerName">{{ timelineState.item.issuerName }}</span>
          <span class="tl-meta" v-if="stageNameOf(timelineState.item.stageCode, timelineState.item.stageName)">阶段：{{ stageNameOf(timelineState.item.stageCode, timelineState.item.stageName) }}</span>
          <span class="tl-meta">已读后未读清零（时间线加载成功后标记已读）</span>
        </div>
      </div>

      <div v-if="timelineState.loading" class="msg-box">加载中…</div>
      <div v-else-if="timelineState.errorMsg" class="msg-box error">{{ timelineState.errorMsg }}</div>

      <div v-else-if="!timelineState.events.length" class="empty-panel">
        <p>该关注项暂无动态。</p>
        <p class="tip">外部公告审核生效（EXTERNAL_ACTIVE）、公告发布/更新、阶段流转等动态会出现在这里。</p>
        <button
          v-if="timelineState.item && timelineState.item.followType === 'ANNOUNCEMENT' && timelineState.item.available !== false && timelineState.item.followKey && /^\d+$/.test(String(timelineState.item.followKey))"
          class="btn"
          @click="goDetail(timelineState.item)"
        >
查看公告详情 →
</button>
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
  background: #f3f5f7;
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
  color: #17539a;
  font-weight: 700;
  cursor: pointer;
  border: 1px solid #c4d7ec;
  background: #eef4fb;
  border-radius: 4px;
  padding: 5px 12px;
}
.sep {
  color: #c6cdd5;
}
.crumb {
  font-size: 15px;
  font-weight: 800;
  color: #1e2832;
}
.crumb-sub {
  font-size: 12px;
  color: #5f6b7a;
}
.filter-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px 8px;
  background: #fff;
  border: 1px solid #d9dfe5;
  border-radius: 6px;
  padding: 8px 14px;
  margin-bottom: 10px;
}
.opt {
  font-size: 13px;
  color: #44505c;
  padding: 2px 12px;
  cursor: pointer;
  border-radius: 3px;
}
.opt.on {
  background: #17539a;
  color: #fff;
  font-weight: 600;
}
.sum {
  margin-left: auto;
  color: #5f6b7a;
  font-size: 12px;
}
.msg-box {
  padding: 30px 12px;
  text-align: center;
  color: #5f6b7a;
  font-size: 13px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
}
.msg-box.error {
  color: #b42318;
  background: #fef4f2;
  border-color: #f3c9c3;
}
.empty-panel {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 46px 20px;
  text-align: center;
  color: #44505c;
  font-size: 14px;
}
.empty-panel .tip {
  color: #5f6b7a;
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
  background: #17539a;
  color: #fff;
  margin-top: 8px;
}
.card-list {
  background: #fff;
  border: 1px solid #e0e4e9;
  border-radius: 6px;
  overflow: hidden;
}
.frow {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 70px 220px;
  gap: 10px;
  align-items: center;
  padding: 10px 14px;
  border-bottom: 1px solid #eceff3;
  cursor: pointer;
}
.frow:hover {
  background: #f5f8fc;
}
.frow.head {
  background: #f3f5f8;
  cursor: default;
  font-size: 12px;
  color: #5d6a77;
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
  color: #1e2832;
  word-break: break-all;
}
.f-text.unread {
  color: #17539a;
}
.f-meta {
  display: flex;
  gap: 14px;
  flex-wrap: wrap;
  color: #5f6b7a;
  font-size: 11.5px;
  margin-top: 3px;
}
.chip {
  display: inline-flex;
  padding: 0 7px;
  border-radius: 3px;
  font-size: 11px;
  line-height: 1.8;
  background: #eef1f4;
  color: #44505c;
  white-space: nowrap;
}
.chip.t {
  background: #e6eef8;
  color: #174a86;
}
.chip.s {
  background: #e5f2ea;
  color: #176b44;
}
.chip.b {
  background: #fdf0e5;
  color: #9a4508;
}
.chip.off {
  background: #fdf0ef;
  color: #b42318;
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
  background: #d92d20;
  color: #fff;
  font-size: 12px;
  font-weight: 700;
}
.none {
  color: #5f6b7a;
}
.f-act {
  display: flex;
  gap: 6px;
  justify-content: flex-end;
}
.mini {
  font-size: 12px;
  color: #44505c;
  border: 1px solid #d3dae1;
  background: #fff;
  border-radius: 4px;
  padding: 3px 10px;
  cursor: pointer;
  white-space: nowrap;
}
.mini.primary {
  color: #17539a;
  border-color: #c4d7ec;
  background: #eef4fb;
  font-weight: 600;
}
.mini.danger {
  color: #b42318;
  border-color: #efc7c2;
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
  color: #5f6b7a;
  font-size: 12px;
}
.pg {
  min-width: 26px;
  height: 26px;
  border-radius: 4px;
  border: 1px solid #d3dae1;
  background: #fff;
  display: grid;
  place-items: center;
  font-size: 12px;
  color: #44505c;
  cursor: pointer;
}
.pg.on {
  background: #17539a;
  color: #fff;
  border-color: #17539a;
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
  background: #fff;
  border: 1px solid #e0e4e9;
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
  color: #1e2832;
  word-break: break-all;
}
.tl-meta {
  color: #5f6b7a;
  font-size: 12px;
  margin-left: 4px;
}
.tl-list {
  background: #fff;
  border: 1px solid #e0e4e9;
  border-radius: 6px;
  overflow: hidden;
}
.tl-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 16px;
  border-bottom: 1px solid #eceff3;
}
.tl-row:last-child {
  border-bottom: 0;
}
.tl-row.unread {
  background: #f3f7fd;
}
.tl-tags {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}
.unread-chip {
  background: #d92d20;
  color: #fff;
}
.tl-time {
  margin-left: 2px;
  color: #5f6b7a;
  font-size: 11.5px;
}
.tl-text {
  margin-top: 5px;
  font-size: 13.5px;
  color: #1e2832;
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
  outline: 2px solid #17539a;
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
</style>
