<!-- ============================================================================
  投标流程跟踪 v1 = “我的关注/意向”视图（以当前登录人关注条目为数据源，本地阶段演示）
  业务（09-投标流程跟踪前端交付-业务口径说明）：跟踪对象 = 投标机会公告；登录人“关注”产生意向。
    按自然人账号 user_id 各自维护（A 方案：资格归企业、操作与跟踪归个人）；不做企业级合并。
    阶段字典：INTENTION → QUALIFICATION_REVIEW → PENDING_BID → BIDDED / TERMINATED。
  现状/理由：后端状态机尚未实现（报名/审核/上传标书等写端点不存在，bidding:process-track:* 待上线），
    v1 阶段保存在 localStorage（键按 租户:用户 维度隔离：txgy.bidding.processTrack.v1.{tenantId}:{userId}），
    真实数据仅 fetchMyFollowPage 拉“我的关注”。修复记录：早期版本用无维度全局键（txgy.bidding.processTrack.v1），
    同浏览器切换账号会读到他人（如超管演示）的本地留档；现按 accessStore.tenantId + userStore.userInfo.userId 隔离，旧全局键数据不再读取。
    页面动作按钮为占位：点击提示“该能力待上线”，不得静默失败或伪造服务端状态（09 §6/§8）。
  依据（法规，含文件类型）：
    · 电子招标投标办法 第9条（部门规章/政府文件）——市场主体免费注册登录获取依法公开信息（关注/浏览前置）；
    · 电子招标投标办法 第13条（部门规章/政府文件）——可靠身份识别与权限控制（按登录人隔离跟踪数据）；
    · 招标投标法 第26条（法律）——投标资格条件（意向→报名→资格核验的跟进脉络，正式评审在后端状态机/招标方）。
  后端契约：09-投标流程跟踪前端交付-业务口径说明.md §2-§6/§8；
    唯一真实接口 fetchMyFollowPage → GET /bidding/notice-follow/item/page（bidding:notice-external:query，user 隔离）；
    关注/取消在公告详情页 follow/unfollow（bidding:notice-external:update）；Track/TrackEvent 为本地模型无后端端点。
============================================================================ -->
<script lang="ts" setup>
import type { ProcessTrackApi } from '#/api/bidding/processTrack';

import { computed, onMounted, ref } from 'vue';

import { useAccessStore, useUserStore } from '@vben/stores';

import { Button, Drawer, Empty, Input, message, Modal, Radio, Select, Space, Tag } from 'ant-design-vue';

import { fetchMyFollowPage } from '#/api/bidding/processTrack';

/* ============ 常量与状态字典 ============ */
/* 本地留档键：按 租户:登录用户 维度隔离，避免同浏览器跨账号串数据（修复记录：
 * 早前无维度全局键致延河热力账号读到 admin 演示数据；旧键 txgy.bidding.processTrack.v1 不再读取）。
 * 真实数据源为服务端“我的关注”（user_id 隔离），本地仅演示留档。 */
const LS_KEY_BASE = 'txgy.bidding.processTrack.v1';
function lsKey(): string {
  const accessStore = useAccessStore();
  const userStore = useUserStore();
  const tenant = accessStore.tenantId ?? 'anon';
  const userId = userStore.userInfo?.userId ?? 'anon';
  return LS_KEY_BASE + '.' + tenant + ':' + userId;
}
const STATE_TEXT: Record<string, string> = {
  INTENTION: '意向投标',
  QUALIFICATION_REVIEW: '资质审核',
  PENDING_BID: '待投标',
  BIDDED: '已投标',
  TERMINATED: '流程结束',
};
const STATE_COLOR: Record<string, string> = {
  INTENTION: 'blue',
  QUALIFICATION_REVIEW: 'orange',
  PENDING_BID: 'purple',
  BIDDED: 'green',
  TERMINATED: 'default',
};
const EVENT_TEXT: Record<string, string> = {
  CREATE: '新建意向（建档）',
  REGISTER: '报名投标',
  APPROVE: '审核通过',
  REJECT: '审核不通过',
  UPLOAD_DOCUMENT: '上传标书',
  CANCEL: '取消投标',
};
const STEP_ORDER = ['INTENTION', 'QUALIFICATION_REVIEW', 'PENDING_BID', 'BIDDED'];

/* ============ 状态 ============ */
const tracks = ref<ProcessTrackApi.Track[]>([]);
const stateFilter = ref<'ALL' | ProcessTrackApi.StateCode>('ALL');
const keyword = ref('');
const followOptions = ref<Array<{ displayName?: string; issuerName?: string; label: string; value: string; }>>([]);
const followLoaded = ref(false);

/* 详情抽屉 */
const detailOpen = ref(false);
const detailId = ref<string>();

/* 操作弹窗（统一：按 action 动态字段） */
type ActKind = 'cancel' | 'register' | 'review' | 'upload';
const actOpen = ref(false);
const actKind = ref<ActKind>('register');
const actTrackId = ref('');
const actReviewResult = ref<'APPROVED' | 'REJECTED'>('APPROVED');
const actReason = ref('');
const actDocName = ref('');
const actDocSize = ref<number | undefined>(undefined);
const actSubmitting = ref(false);

const statCounts = computed(() => {
  const c: Record<string, number> = {};
  for (const t of tracks.value) c[t.currentState] = (c[t.currentState] || 0) + 1;
  return c;
});
const filtered = computed(() => {
  const k = keyword.value.trim();
  return tracks.value.filter((t) => {
    if (stateFilter.value !== 'ALL' && t.currentState !== stateFilter.value) return false;
    if (!k) return true;
    return [t.projectName, t.projectCode, t.issuerName].filter(Boolean).some((s) => String(s).includes(k));
  });
});
const detail = computed(() => tracks.value.find((t) => t.id === detailId.value));

/* ============ 持久化 ============ */
function save() {
  localStorage.setItem(lsKey(), JSON.stringify(tracks.value));
}
function load() {
  try {
    const raw = localStorage.getItem(lsKey());
    tracks.value = raw ? (JSON.parse(raw) as ProcessTrackApi.Track[]) : [];
  } catch {
    tracks.value = [];
  }
}

/* ============ 展示工具 ============ */
const p2 = (n: number) => String(n).padStart(2, '0');
const fmtT = (v?: number): string => {
  if (!v) return '—';
  const d = new Date(v);
  if (Number.isNaN(+d)) return '—';
  return d.getFullYear() + '-' + p2(d.getMonth() + 1) + '-' + p2(d.getDate()) + ' ' + p2(d.getHours()) + ':' + p2(d.getMinutes());
};
const stepIndexOf = (s: string) => (s === 'TERMINATED' ? STEP_ORDER.length : STEP_ORDER.indexOf(s));
function stepState(t: ProcessTrackApi.Track, i: number): 'done' | 'process' | 'wait' {
  const cur = stepIndexOf(t.currentState);
  if (i < cur) return 'done';
  if (i === cur && t.currentState !== 'TERMINATED') return 'process';
  return 'wait';
}
const stateTag = (s: string) => ({ text: STATE_TEXT[s] || s, color: STATE_COLOR[s] || 'default' });

/* ============ 状态机推进（轻量守卫，事件留痕） ============ */
/* 本地阶段推进（报名/审核/上传标书/取消）：仅写 localStorage，无后端写端点。
 * 契约：09 §4/§6——后端状态机未上线，按钮为占位演示；正式接口上线后以服务端为准。 */
function advance(
  t: ProcessTrackApi.Track,
  ev: ProcessTrackApi.EventCode,
  extra: { desc?: string; docName?: string; docSize?: number; reviewResult?: string; },
): { error?: string; ok: boolean; to?: ProcessTrackApi.StateCode } {
  const cur = t.currentState;
  const m: Record<string, Record<string, ProcessTrackApi.StateCode>> = {
    INTENTION: { REGISTER: 'QUALIFICATION_REVIEW', CANCEL: 'TERMINATED' },
    QUALIFICATION_REVIEW: { APPROVE: 'PENDING_BID', REJECT: 'TERMINATED' },
    PENDING_BID: { UPLOAD_DOCUMENT: 'BIDDED', CANCEL: 'TERMINATED' },
  };
  const to = m[cur] ? m[cur][ev] : undefined;
  if (!to) return { ok: false, error: '当前状态（' + STATE_TEXT[cur] + '）不允许该操作' };
  const now = Date.now();
  t.previousState = cur;
  t.currentState = to;
  if (ev === 'REGISTER') t.registerTime = now;
  if (ev === 'APPROVE' || ev === 'REJECT') {
    t.reviewTime = now;
    t.reviewResult = extra.reviewResult;
    t.reviewReason = extra.desc || '';
  }
  if (ev === 'UPLOAD_DOCUMENT') {
    t.biddedTime = now;
    t.documentName = extra.docName;
    t.documentSize = extra.docSize;
  }
  if (to === 'TERMINATED') t.terminatedTime = now;
  t.events.unshift({
    id: 'EV' + String(now) + String(Math.floor(Math.random() * 1e4)),
    eventType: ev,
    fromState: cur,
    toState: to,
    operatorName: '当前登录用户（手动）',
    operationDesc: extra.desc,
    occurredAt: now,
  });
  return { ok: true, to };
}

/* ============ 建档（新建意向；可从“我的关注”带入） ============ */
const formOpen = ref(false);
const fProjectName = ref('');
const fProjectCode = ref('');
const fIssuer = ref('');
const fFollowKey = ref<string | undefined>(undefined);
const fRemark = ref('');

/* 拉“我的关注”作下拉候选：真实接口 fetchMyFollowPage（user_id 隔离，勿传他人 userId）。
 * 契约：09 §2/§8 item/page 后端取 getLoginUserId；关注权限 bidding:notice-external:query。 */
async function loadFollowOptions() {
  if (followLoaded.value) return;
  try {
    const res = (await fetchMyFollowPage({ pageNo: 1, pageSize: 100 })) as unknown as {
      list?: ProcessTrackApi.FollowItem[];
    };
    followOptions.value = (res.list || [])
      .filter((f) => f.followKey)
      .map((f) => ({
        label: (f.displayName || f.followKey) + (f.issuerName ? ' · ' + f.issuerName : ''),
        value: String(f.followKey),
        displayName: f.displayName || String(f.followKey),
        issuerName: f.issuerName,
      }));
  } catch {
    followOptions.value = [];
  }
  followLoaded.value = true;
}
function onFollowPicked(
  key?: string,
  opt?: { displayName?: string; issuerName?: string },
) {
  if (!key) return;
  if (!fProjectName.value.trim() && opt?.displayName) fProjectName.value = opt.displayName;
  if (!fIssuer.value.trim() && opt?.issuerName) fIssuer.value = opt.issuerName;
}
function openCreate() {
  fProjectName.value = '';
  fProjectCode.value = '';
  fIssuer.value = '';
  fFollowKey.value = undefined;
  fRemark.value = '';
  void loadFollowOptions();
  formOpen.value = true;
}
function submitCreate() {
  if (!fProjectName.value.trim()) {
    message.warning('请填写项目名称');
    return;
  }
  const now = Date.now();
  const t: ProcessTrackApi.Track = {
    id: 'PT' + String(now),
    currentState: 'INTENTION',
    projectName: fProjectName.value.trim(),
    projectCode: fProjectCode.value.trim() || undefined,
    issuerName: fIssuer.value.trim() || undefined,
    followKey: fFollowKey.value,
    followType: fFollowKey.value ? 'PROJECT' : undefined,
    remark: fRemark.value.trim() || undefined,
    intentionTime: now,
    createTime: now,
    events: [
      {
        id: 'EV' + String(now),
        eventType: 'CREATE',
        toState: 'INTENTION',
        operatorName: '当前登录用户（手动）',
        operationDesc: fRemark.value.trim() || '建档',
        occurredAt: now,
      },
    ],
  };
  tracks.value.unshift(t);
  save();
  formOpen.value = false;
  message.success('已建档：' + t.projectName + '（意向投标）');
}

/* ============ 操作弹窗 ============ */
function openAct(kind: ActKind, t: ProcessTrackApi.Track, presetReview?: 'APPROVED' | 'REJECTED') {
  actKind.value = kind;
  actTrackId.value = t.id;
  actReviewResult.value = presetReview || 'APPROVED';
  actReason.value = '';
  actDocName.value = '';
  actDocSize.value = undefined;
  actOpen.value = true;
}
const actTitle = computed(() => {
  const map: Record<ActKind, string> = {
    register: '报名投标',
    review: '资质审核（手动标记）',
    upload: '上传标书',
    cancel: '取消投标',
  };
  return map[actKind.value];
});
/* 本地动作执行（审核结果/登记标书/取消）：执行后本地推进并提示“（本地演示…）”口径。
 * 契约：09 §6/§8 占位动作提示“该能力待上线”，不得伪造服务端状态。 */
function doAct() {
  const t = tracks.value.find((x) => x.id === actTrackId.value);
  if (!t) return;
  const reason = actReason.value.trim();
  if (actKind.value === 'cancel' && !reason) {
    message.warning('请填写取消原因');
    return;
  }
  if (actKind.value === 'review' && actReviewResult.value === 'REJECTED' && !reason) {
    message.warning('审核不通过请填写原因');
    return;
  }
  if (actKind.value === 'upload' && !actDocName.value.trim()) {
    message.warning('请填写标书文件名');
    return;
  }
  actSubmitting.value = true;
  try {
    let ev: ProcessTrackApi.EventCode;
    const extra: { desc?: string; docName?: string; docSize?: number; reviewResult?: string; } = { desc: reason };
    if (actKind.value === 'register') ev = 'REGISTER';
    else if (actKind.value === 'review') {
      ev = actReviewResult.value === 'APPROVED' ? 'APPROVE' : 'REJECT';
      extra.reviewResult = actReviewResult.value;
      extra.desc = reason;
    } else if (actKind.value === 'upload') {
      ev = 'UPLOAD_DOCUMENT';
      extra.docName = actDocName.value.trim();
      extra.docSize = actDocSize.value;
      extra.desc = reason;
    } else {
      ev = 'CANCEL';
    }
    const r = advance(t, ev, extra);
    if (!r.ok) {
      message.warning(r.error || '操作不允许');
      return;
    }
    save();
    actOpen.value = false;
    message.success(EVENT_TEXT[ev] + '（本地演示）：' + t.projectName + ' —— 后端状态机上线后以服务端数据为准');
  } finally {
    actSubmitting.value = false;
  }
}

/* ============ 删除（仅未产生投标动作的历史） ============ */
function removeTrack(t: ProcessTrackApi.Track) {
  Modal.confirm({
    title: '删除流程「' + t.projectName + '」？',
    content: '演示阶段删除仅移除本地记录。',
    okText: '删除',
    okButtonProps: { danger: true },
    cancelText: '取消',
    onOk: () => {
      tracks.value = tracks.value.filter((x) => x.id !== t.id);
      save();
      message.success('已删除（本地演示）');
    },
  });
}

onMounted(() => {
  load();
});
</script>

<template>
  <div class="bid-ind ind-page">
        <div class="ind-stripes"></div>
    <div class="ind-head">
      <div class="min-w-0">
        <div class="ind-title">投标流程跟踪 · 我的投标工作台 <span class="ind-mark">TRACK</span></div>
        <div class="ind-sub">v1 = 我的关注（服务端按登录人 user_id 隔离）+ 本地阶段演示（localStorage）。意向投标 → 资质审核 → 待投标 → 已投标 / 流程结束；报名/审核/上传标书等动作仅本地标记，后端状态机（bidding:process-track:*）上线后以服务端为准。</div>
      </div>
      <div class="ind-actions">
        <Button type="primary" size="large" @click="openCreate">＋ 新建意向</Button>
        <Button size="large" @click="load">刷新</Button>
      </div>
    </div>
    <div class="ind-body">
<div class="ind-filterbar">
      <Radio.Group
        v-model:value="stateFilter"
        option-type="button"
        :options="[
          { label: `全部（${ tracks.length }）`, value: 'ALL' },
          { label: `意向投标（${ statCounts.INTENTION || 0 }）`, value: 'INTENTION' },
          { label: `资质审核（${ statCounts.QUALIFICATION_REVIEW || 0 }）`, value: 'QUALIFICATION_REVIEW' },
          { label: `待投标（${ statCounts.PENDING_BID || 0 }）`, value: 'PENDING_BID' },
          { label: `已投标（${ statCounts.BIDDED || 0 }）`, value: 'BIDDED' },
          { label: `流程结束（${ statCounts.TERMINATED || 0 }）`, value: 'TERMINATED' },
        ]"
      />
      <Input v-model:value="keyword" placeholder="搜索项目名 / 编号 / 发布单位" allow-clear style="width: 240px" />
    </div>

    <!-- 列表 -->
    <Empty v-if="!tracks.length" description="还没有投标流程 — 点右上「新建意向」建档（可从我的关注选择或手动填写）" />
    <div v-else-if="!filtered.length" class="rounded-md border border-dashed border-border bg-card p-6 text-center text-sm text-muted-foreground">
      当前筛选条件下无记录
    </div>
    <article
      v-for="t in filtered"
      :key="t.id"
      class="ind-item"
    >
      <div class="flex items-center gap-3">
        <Tag :color="stateTag(t.currentState).color">{{ stateTag(t.currentState).text }}</Tag>
        <span class="it-title">{{ t.projectName }}</span>
        <Tag v-if="t.projectCode" class="font-mono text-xs">{{ t.projectCode }}</Tag>
        <span v-if="t.issuerName" class="text-xs text-muted-foreground">{{ t.issuerName }}</span>
        <span class="flex-1"></span>
        <!-- 行操作：按状态 -->
        <template v-if="t.currentState === 'INTENTION'">
          <a class="text-xs text-primary" @click="openAct('register', t)">报名投标</a>
          <a class="text-xs text-destructive" @click="openAct('cancel', t)">取消投标</a>
        </template>
        <template v-else-if="t.currentState === 'QUALIFICATION_REVIEW'">
          <a class="text-xs text-emerald-600 dark:text-emerald-400" @click="openAct('review', t, 'APPROVED')">审核通过</a>
          <a class="text-xs text-destructive" @click="openAct('review', t, 'REJECTED')">审核不通过</a>
        </template>
        <template v-else-if="t.currentState === 'PENDING_BID'">
          <a class="text-xs text-primary" @click="openAct('upload', t)">上传标书</a>
          <a class="text-xs text-destructive" @click="openAct('cancel', t)">取消投标</a>
        </template>
        <a class="text-xs text-primary" @click="detailId = t.id; detailOpen = true">详情</a>
        <a class="text-xs text-muted-foreground" @click="removeTrack(t)">删除</a>
      </div>
      <div class="it-sub mt-2 flex flex-wrap items-center gap-x-5 gap-y-1">
        <span>意向：{{ fmtT(t.intentionTime) }}</span>
        <span v-if="t.registerTime">报名：{{ fmtT(t.registerTime) }}</span>
        <span v-if="t.pendingBidTime">待投标：{{ fmtT(t.pendingBidTime) }}</span>
        <span v-if="t.biddedTime">已投标：{{ fmtT(t.biddedTime) }}</span>
        <span v-if="t.terminatedTime">结束：{{ fmtT(t.terminatedTime) }}</span>
        <span v-if="t.events.length" class="truncate text-muted-foreground/60">最近：{{ EVENT_TEXT[t.events[0]!.eventType] }}</span>
      </div>
      <div v-if="t.currentState === 'QUALIFICATION_REVIEW' && t.reviewReason" class="mt-1 text-xs text-amber-600">审核说明：{{ t.reviewReason }}</div>
      <div v-if="t.documentName" class="mt-1 text-xs text-muted-foreground">标书：{{ t.documentName }}{{ t.documentSize ? `（${ t.documentSize } KB）` : '' }}</div>
    </article>
</div>

    <!-- 详情抽屉 -->
    <Drawer v-model:open="detailOpen" :title="detail ? detail.projectName : '流程详情'" :width="560">
      <template v-if="detail">
        <div class="mb-2 text-xs text-muted-foreground">
          <span v-if="detail.projectCode" class="mr-3 font-mono">{{ detail.projectCode }}</span>
          <span v-if="detail.issuerName">{{ detail.issuerName }}</span>
          <span v-if="detail.remark" class="ml-3">备注：{{ detail.remark }}</span>
        </div>
        <!-- 流程步骤 -->
        <div class="mb-4 rounded-md border border-border p-3">
          <div class="mb-2 text-xs font-semibold text-muted-foreground">流程进度</div>
          <div class="flex items-start">
            <template v-for="(s, i) in STEP_ORDER" :key="s">
              <div class="flex flex-1 flex-col items-center text-center">
                <div
                  class="flex h-6 w-6 items-center justify-center rounded-full text-xs text-white"
                  :class="stepState(detail, i) === 'done' ? 'bg-emerald-500' : stepState(detail, i) === 'process' ? 'bg-primary' : 'bg-muted text-muted-foreground'"
                >
                  {{ stepState(detail, i) === 'done' ? '✓' : i + 1 }}
                </div>
                <div class="mt-1 text-xs" :class="stepState(detail, i) === 'wait' ? 'text-muted-foreground/60' : 'text-muted-foreground'">{{ STATE_TEXT[s] }}</div>
                <div class="mt-0.5 text-[11.5px] text-muted-foreground">
                  {{ fmtT(i === 0 ? detail.intentionTime : i === 1 ? detail.registerTime : i === 2 ? detail.pendingBidTime : detail.biddedTime) }}
                </div>
              </div>
              <div v-if="i < STEP_ORDER.length - 1" class="mt-3 h-0.5 flex-1 rounded" :class="stepState(detail, i) === 'done' ? 'bg-emerald-500' : 'bg-muted'"></div>
            </template>
          </div>
          <div v-if="detail.currentState === 'TERMINATED'" class="mt-2 text-center text-xs" :class="detail.reviewResult === 'REJECTED' ? 'text-destructive' : 'text-muted-foreground'">
            {{ detail.reviewResult === 'REJECTED' ? '审核不通过，流程结束' : '已取消投标' }}
            <span v-if="detail.reviewReason">：{{ detail.reviewReason }}</span>
          </div>
          <div v-else-if="detail.currentState === 'QUALIFICATION_REVIEW'" class="mt-2 text-center text-xs text-amber-600 dark:text-amber-400">资质审核中 — 请手动标记审核结果</div>
        </div>
        <!-- 操作历史 -->
        <div class="rounded-md border border-border p-3">
          <div class="mb-2 text-xs font-semibold text-muted-foreground">操作历史（事件留痕）</div>
          <div v-if="!detail.events.length" class="text-xs text-muted-foreground/60">暂无事件</div>
          <div v-for="(e, idx) in detail.events" :key="e.id" class="flex gap-3 py-1 text-xs">
            <div class="w-32 shrink-0 text-muted-foreground">{{ fmtT(e.occurredAt) }}</div>
            <div class="flex-1">
              <span class="text-foreground">{{ EVENT_TEXT[e.eventType] || e.eventType }}</span>
              <span v-if="e.operationDesc" class="ml-2 text-muted-foreground">{{ e.operationDesc }}</span>
              <div v-if="idx === 0 && detail.events.length > 1" class="text-[10px] text-muted-foreground/60">↑ 最新</div>
            </div>
          </div>
        </div>
      </template>
    </Drawer>

    <!-- 新建意向弹窗 -->
    <Modal v-model:open="formOpen" title="新建意向（建档）" :footer="null" width="560">
      <div class="space-y-3">
        <div>
          <div class="mb-1 text-xs font-medium text-muted-foreground">从我的关注选择（可选，自动带入项目名/发布单位）</div>
          <Select
            v-model:value="fFollowKey"
            placeholder="暂无关注时可跳过，直接手动填写下方信息"
            allow-clear
            show-search
            option-filter-prop="label"
            style="width: 100%"
            :options="followOptions"
            @change="(val: unknown, opt: unknown) => onFollowPicked(String(val ?? ''), (opt as { displayName?: string; issuerName?: string }) || {})"
          />
        </div>
        <div class="grid grid-cols-2 gap-3">
          <div>
            <div class="mb-1 text-xs font-medium text-muted-foreground">项目名称 *</div>
            <Input v-model:value="fProjectName" placeholder="如：XX 光伏 EPC 总承包" />
          </div>
          <div>
            <div class="mb-1 text-xs font-medium text-muted-foreground">项目编号（跟踪键）</div>
            <Input v-model:value="fProjectCode" placeholder="如 DYZB2025-11-G141" />
          </div>
        </div>
        <div>
          <div class="mb-1 text-xs font-medium text-muted-foreground">发布单位</div>
          <Input v-model:value="fIssuer" placeholder="手动填写（无招标方数据时）" />
        </div>
        <div>
          <div class="mb-1 text-xs font-medium text-muted-foreground">备注</div>
          <Input.TextArea v-model:value="fRemark" :rows="2" placeholder="意向说明（可选）" />
        </div>
        <div class="text-right">
          <Space>
            <Button @click="formOpen = false">取消</Button>
            <Button type="primary" @click="submitCreate">建档并进入意向投标</Button>
          </Space>
        </div>
      </div>
    </Modal>

    <!-- 统一操作弹窗 -->
    <Modal v-model:open="actOpen" :title="actTitle" :footer="null" width="480">
      <div class="space-y-3">
        <div v-if="actKind === 'review'" class="flex items-center gap-3">
          <div class="text-xs font-medium text-muted-foreground">审核结果：</div>
          <Radio.Group v-model:value="actReviewResult" option-type="button" :options="[{ label: '审核通过', value: 'APPROVED' }, { label: '审核不通过', value: 'REJECTED' }]" />
        </div>
        <div v-if="actKind === 'upload'">
          <div class="mb-1 text-xs font-medium text-muted-foreground">标书文件名 *（本事件仅登记文件名，不含文件本体）</div>
          <Input v-model:value="actDocName" placeholder="如 XX 项目投标文件.pdf" />
        </div>
        <div>
          <div class="mb-1 text-xs font-medium text-muted-foreground">
            {{ actKind === 'register' ? '备注' : actKind === 'upload' ? '备注' : actKind === 'cancel' ? '取消原因 *' : '审核说明 *' }}
          </div>
          <Input.TextArea v-model:value="actReason" :rows="2" :placeholder="actKind === 'cancel' ? '如 资质不满足/预算调整' : '如 资料齐全，通过 / 社保记录缺失，不通过'" />
        </div>
        <div class="text-right">
          <Space>
            <Button @click="actOpen = false">取消</Button>
            <Button type="primary" :loading="actSubmitting" @click="doAct">确认{{ EVENT_TEXT[actKind === 'review' ? (actReviewResult === 'APPROVED' ? 'APPROVE' : 'REJECT') : actKind === 'register' ? 'REGISTER' : actKind === 'upload' ? 'UPLOAD_DOCUMENT' : 'CANCEL'] }}</Button>
          </Space>
        </div>
      </div>
    </Modal>
  </div>
</template>
