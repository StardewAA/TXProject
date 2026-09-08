<script lang="ts" setup>
import type { ProcessTrackApi } from '#/api/bidding/processTrack';

import { computed, onMounted, ref } from 'vue';

import { Button, Drawer, Empty, Input, message, Modal, Radio, Select, Space, Tag } from 'ant-design-vue';

import { fetchMyFollowPage } from '#/api/bidding/processTrack';

/* ============ 常量与状态字典 ============ */
const LS_KEY = 'txgy.bidding.processTrack.v1';
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
const followOptions = ref<Array<{ label: string; value: string; displayName?: string; issuerName?: string }>>([]);
const followLoaded = ref(false);

/* 详情抽屉 */
const detailOpen = ref(false);
const detailId = ref<string>();

/* 操作弹窗（统一：按 action 动态字段） */
type ActKind = 'register' | 'review' | 'upload' | 'cancel';
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
  localStorage.setItem(LS_KEY, JSON.stringify(tracks.value));
}
function load() {
  try {
    const raw = localStorage.getItem(LS_KEY);
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
function advance(
  t: ProcessTrackApi.Track,
  ev: ProcessTrackApi.EventCode,
  extra: { desc?: string; reviewResult?: string; docName?: string; docSize?: number },
): { ok: boolean; error?: string; to?: ProcessTrackApi.StateCode } {
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
    const extra: { desc?: string; reviewResult?: string; docName?: string; docSize?: number } = { desc: reason };
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
  <div class="p-3">
    <!-- 顶部 -->
    <div class="mb-3 flex items-center justify-between rounded-md border border-gray-200 bg-white px-3 py-2">
      <div class="flex items-center gap-3">
        <span class="text-sm font-semibold">投标流程跟踪</span>
        <Tag color="gold">手动版 · 无招标方接入</Tag>
        <span class="text-xs text-gray-400">v1=我的关注（服务端 user_id 隔离）+ 本地阶段演示（localStorage）：报名/审核/上传标书等动作仅本地标记，无后端写端点（bidding:process-track:* 待状态机上线，09 业务口径）</span>
      </div>
      <Space>
        <Button type="primary" @click="openCreate">+ 新建意向</Button>
      </Space>
    </div>

    <!-- 状态 tabs + 搜索 -->
    <div class="mb-3 flex items-center gap-3 rounded-md border border-gray-200 bg-white px-3 py-2">
      <Radio.Group
        v-model:value="stateFilter"
        option-type="button"
        :options="[
          { label: '全部（' + tracks.length + '）', value: 'ALL' },
          { label: '意向投标（' + (statCounts.INTENTION || 0) + '）', value: 'INTENTION' },
          { label: '资质审核（' + (statCounts.QUALIFICATION_REVIEW || 0) + '）', value: 'QUALIFICATION_REVIEW' },
          { label: '待投标（' + (statCounts.PENDING_BID || 0) + '）', value: 'PENDING_BID' },
          { label: '已投标（' + (statCounts.BIDDED || 0) + '）', value: 'BIDDED' },
          { label: '流程结束（' + (statCounts.TERMINATED || 0) + '）', value: 'TERMINATED' },
        ]"
      />
      <Input v-model:value="keyword" placeholder="搜索项目名 / 编号 / 发布单位" allow-clear style="width: 240px" />
    </div>

    <!-- 列表 -->
    <Empty v-if="!tracks.length" description="还没有投标流程 — 点右上「新建意向」建档（可从我的关注选择或手动填写）" />
    <div v-else-if="!filtered.length" class="rounded-md border border-dashed border-gray-200 bg-white p-6 text-center text-sm text-gray-400">
      当前筛选条件下无记录
    </div>
    <article
      v-for="t in filtered"
      :key="t.id"
      class="mb-2 rounded-md border border-gray-200 bg-white px-3 py-2"
    >
      <div class="flex items-center gap-3">
        <Tag :color="stateTag(t.currentState).color">{{ stateTag(t.currentState).text }}</Tag>
        <span class="text-sm font-medium text-gray-800">{{ t.projectName }}</span>
        <Tag v-if="t.projectCode" class="font-mono text-xs">{{ t.projectCode }}</Tag>
        <span v-if="t.issuerName" class="text-xs text-gray-400">{{ t.issuerName }}</span>
        <span class="flex-1" />
        <!-- 行操作：按状态 -->
        <template v-if="t.currentState === 'INTENTION'">
          <a class="text-xs text-blue-600" @click="openAct('register', t)">报名投标</a>
          <a class="text-xs text-red-500" @click="openAct('cancel', t)">取消投标</a>
        </template>
        <template v-else-if="t.currentState === 'QUALIFICATION_REVIEW'">
          <a class="text-xs text-green-600" @click="openAct('review', t, 'APPROVED')">审核通过</a>
          <a class="text-xs text-red-500" @click="openAct('review', t, 'REJECTED')">审核不通过</a>
        </template>
        <template v-else-if="t.currentState === 'PENDING_BID'">
          <a class="text-xs text-blue-600" @click="openAct('upload', t)">上传标书</a>
          <a class="text-xs text-red-500" @click="openAct('cancel', t)">取消投标</a>
        </template>
        <a class="text-xs text-blue-600" @click="detailId = t.id; detailOpen = true">详情</a>
        <a class="text-xs text-gray-400" @click="removeTrack(t)">删除</a>
      </div>
      <div class="mt-1 flex items-center gap-4 text-xs text-gray-400">
        <span>意向：{{ fmtT(t.intentionTime) }}</span>
        <span v-if="t.registerTime">报名：{{ fmtT(t.registerTime) }}</span>
        <span v-if="t.pendingBidTime">待投标：{{ fmtT(t.pendingBidTime) }}</span>
        <span v-if="t.biddedTime">已投标：{{ fmtT(t.biddedTime) }}</span>
        <span v-if="t.terminatedTime">结束：{{ fmtT(t.terminatedTime) }}</span>
        <span v-if="t.events.length" class="truncate text-gray-300">最近：{{ EVENT_TEXT[t.events[0]!.eventType] }}</span>
      </div>
      <div v-if="t.currentState === 'QUALIFICATION_REVIEW' && t.reviewReason" class="mt-1 text-xs text-amber-600">审核说明：{{ t.reviewReason }}</div>
      <div v-if="t.documentName" class="mt-1 text-xs text-gray-500">标书：{{ t.documentName }}{{ t.documentSize ? '（' + t.documentSize + ' KB）' : '' }}</div>
    </article>

    <!-- 详情抽屉 -->
    <Drawer v-model:open="detailOpen" :title="detail ? detail.projectName : '流程详情'" :width="560">
      <template v-if="detail">
        <div class="mb-2 text-xs text-gray-500">
          <span v-if="detail.projectCode" class="mr-3 font-mono">{{ detail.projectCode }}</span>
          <span v-if="detail.issuerName">{{ detail.issuerName }}</span>
          <span v-if="detail.remark" class="ml-3">备注：{{ detail.remark }}</span>
        </div>
        <!-- 流程步骤 -->
        <div class="mb-4 rounded-md border border-gray-100 p-3">
          <div class="mb-2 text-xs font-semibold text-gray-500">流程进度</div>
          <div class="flex items-start">
            <template v-for="(s, i) in STEP_ORDER" :key="s">
              <div class="flex flex-1 flex-col items-center text-center">
                <div
                  class="flex h-6 w-6 items-center justify-center rounded-full text-xs text-white"
                  :class="stepState(detail, i) === 'done' ? 'bg-green-500' : stepState(detail, i) === 'process' ? 'bg-blue-500' : 'bg-gray-200 text-gray-400'"
                >
                  {{ stepState(detail, i) === 'done' ? '✓' : i + 1 }}
                </div>
                <div class="mt-1 text-[11px]" :class="stepState(detail, i) === 'wait' ? 'text-gray-300' : 'text-gray-600'">{{ STATE_TEXT[s] }}</div>
                <div class="text-[10px] text-gray-300">
                  {{ fmtT(i === 0 ? detail.intentionTime : i === 1 ? detail.registerTime : i === 2 ? detail.pendingBidTime : detail.biddedTime) }}
                </div>
              </div>
              <div v-if="i < STEP_ORDER.length - 1" class="mt-3 h-0.5 flex-1 rounded" :class="stepState(detail, i) === 'done' ? 'bg-green-400' : 'bg-gray-200'"></div>
            </template>
          </div>
          <div v-if="detail.currentState === 'TERMINATED'" class="mt-2 text-center text-xs" :class="detail.reviewResult === 'REJECTED' ? 'text-red-500' : 'text-gray-400'">
            {{ detail.reviewResult === 'REJECTED' ? '审核不通过，流程结束' : '已取消投标' }}
            <span v-if="detail.reviewReason">：{{ detail.reviewReason }}</span>
          </div>
          <div v-else-if="detail.currentState === 'QUALIFICATION_REVIEW'" class="mt-2 text-center text-xs text-orange-500">资质审核中 — 请手动标记审核结果</div>
        </div>
        <!-- 操作历史 -->
        <div class="rounded-md border border-gray-100 p-3">
          <div class="mb-2 text-xs font-semibold text-gray-500">操作历史（事件留痕）</div>
          <div v-if="!detail.events.length" class="text-xs text-gray-300">暂无事件</div>
          <div v-for="(e, idx) in detail.events" :key="e.id" class="flex gap-3 py-1 text-xs">
            <div class="w-32 shrink-0 text-gray-400">{{ fmtT(e.occurredAt) }}</div>
            <div class="flex-1">
              <span class="text-gray-700">{{ EVENT_TEXT[e.eventType] || e.eventType }}</span>
              <span v-if="e.operationDesc" class="ml-2 text-gray-400">{{ e.operationDesc }}</span>
              <div v-if="idx === 0 && detail.events.length > 1" class="text-[10px] text-gray-200">↑ 最新</div>
            </div>
          </div>
        </div>
      </template>
    </Drawer>

    <!-- 新建意向弹窗 -->
    <Modal v-model:open="formOpen" title="新建意向（建档）" :footer="null" width="560">
      <div class="space-y-3">
        <div>
          <div class="mb-1 text-xs font-medium text-gray-600">从我的关注选择（可选，自动带入项目名/发布单位）</div>
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
            <div class="mb-1 text-xs font-medium text-gray-600">项目名称 *</div>
            <Input v-model:value="fProjectName" placeholder="如：XX 光伏 EPC 总承包" />
          </div>
          <div>
            <div class="mb-1 text-xs font-medium text-gray-600">项目编号（跟踪键）</div>
            <Input v-model:value="fProjectCode" placeholder="如 DYZB2025-11-G141" />
          </div>
        </div>
        <div>
          <div class="mb-1 text-xs font-medium text-gray-600">发布单位</div>
          <Input v-model:value="fIssuer" placeholder="手动填写（无招标方数据时）" />
        </div>
        <div>
          <div class="mb-1 text-xs font-medium text-gray-600">备注</div>
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
          <div class="text-xs font-medium text-gray-600">审核结果：</div>
          <Radio.Group v-model:value="actReviewResult" option-type="button" :options="[{ label: '审核通过', value: 'APPROVED' }, { label: '审核不通过', value: 'REJECTED' }]" />
        </div>
        <div v-if="actKind === 'upload'">
          <div class="mb-1 text-xs font-medium text-gray-600">标书文件名 *（招标方未接入，先手动登记；FileApi 就绪后改上传）</div>
          <Input v-model:value="actDocName" placeholder="如 XX 项目投标文件.pdf" />
        </div>
        <div>
          <div class="mb-1 text-xs font-medium text-gray-600">
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
