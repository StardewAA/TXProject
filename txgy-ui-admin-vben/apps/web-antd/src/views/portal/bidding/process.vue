<!--
  招投标前台 · 投标流程管理（个人工作台）
  业务：关注项目 → 自动「意向投标」→ 报名投标 → 资质审核 →（审核通过）待投标 → 上传标书 → 已投标（流程结束）；
        审核不通过 / 取消 → 流程结束。
  口径（前端为主）：后端状态机未实现，阶段本地维护（localStorage 按 租户:用户 隔离）；
    「关注自动触发意向投标」= 加载时拉「我的关注」为新关注项补建意向流程；
    「上传标书」= 真实接口 POST /bidding/file/upload（返回 fileId，文件落 infra_file）。
  依据：电子招标投标办法 第9/13条（部门规章）；招标投标法 第26条（法律）。
-->
<script lang="ts" setup>
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import { Button, Empty, message, Modal, Radio, Tag } from 'ant-design-vue';

import { uploadBiddingFile } from '#/api/bidding/file';

import ModuleNav from './components/ModuleNav.vue';
import {
  STATE_COLOR,
  STATE_TEXT,
  useBiddingProcess,
  type PortalTrack,
  type ProcessState,
} from './composables/useBiddingProcess';

const router = useRouter();
const { advance, syncFromFollow, tracks } = useBiddingProcess();

const stateFilter = ref<'ALL' | ProcessState>('ALL');
const syncing = ref(false);

const FILTERS: Array<{ label: string; value: 'ALL' | ProcessState }> = [
  { label: '全部', value: 'ALL' },
  { label: '意向投标', value: 'INTENTION' },
  { label: '资质审核', value: 'QUALIFICATION_REVIEW' },
  { label: '待投标', value: 'PENDING_BID' },
  { label: '已投标', value: 'BIDDED' },
  { label: '流程结束', value: 'TERMINATED' },
];

const filtered = computed(() =>
  tracks.value.filter((t) => stateFilter.value === 'ALL' || t.currentState === stateFilter.value),
);
const statCounts = computed(() => {
  const c: Record<string, number> = {};
  for (const t of tracks.value) c[t.currentState] = (c[t.currentState] || 0) + 1;
  return c;
});

async function doSync(): Promise<void> {
  syncing.value = true;
  try {
    await syncFromFollow();
  } finally {
    syncing.value = false;
  }
}

/* ============ 报名 / 取消 ============ */
function registerBid(t: PortalTrack): void {
  Modal.confirm({
    title: '报名投标「' + t.projectName + '」？',
    content: '报名后进入「资质审核」，请手动标记审核结果。',
    okText: '报名投标',
    cancelText: '再想想',
    onOk: () => {
      const r = advance(t, 'REGISTER');
      if (!r.ok) message.warning(r.error || '操作不允许');
      else message.success('已报名，进入资质审核：' + t.projectName);
    },
  });
}
function cancelTrack(t: PortalTrack): void {
  Modal.confirm({
    title: '取消投标「' + t.projectName + '」？',
    content: '取消后流程结束，不可恢复。',
    okText: '取消投标',
    okButtonProps: { danger: true },
    cancelText: '保留',
    onOk: () => {
      advance(t, 'CANCEL', { cancelReason: '用户取消投标' });
      message.success('已取消投标，流程结束');
    },
  });
}

/* ============ 资质审核 ============ */
const reviewOpen = ref(false);
const reviewTarget = ref<PortalTrack | null>(null);
const reviewResult = ref<'APPROVED' | 'REJECTED'>('APPROVED');
const reviewReason = ref('');
const reviewSubmitting = ref(false);

function openReview(t: PortalTrack, preset: 'APPROVED' | 'REJECTED'): void {
  reviewTarget.value = t;
  reviewResult.value = preset;
  reviewReason.value = '';
  reviewOpen.value = true;
}
function submitReview(): void {
  const t = reviewTarget.value;
  if (!t) return;
  if (reviewResult.value === 'REJECTED' && !reviewReason.value.trim()) {
    message.warning('审核不通过请填写原因');
    return;
  }
  reviewSubmitting.value = true;
  try {
    const r = advance(t, reviewResult.value === 'APPROVED' ? 'APPROVE' : 'REJECT', {
      reviewResult: reviewResult.value,
      reviewReason: reviewReason.value.trim() || undefined,
    });
    if (!r.ok) {
      message.warning(r.error || '操作不允许');
      return;
    }
    reviewOpen.value = false;
    message.success(reviewResult.value === 'APPROVED' ? '审核通过，进入待投标' : '审核不通过，流程结束');
  } finally {
    reviewSubmitting.value = false;
  }
}

/* ============ 标书上传（真实接口） ============ */
const uploadOpen = ref(false);
const uploadTarget = ref<PortalTrack | null>(null);
const pickedFile = ref<File | null>(null);
const uploading = ref(false);

function openUpload(t: PortalTrack): void {
  uploadTarget.value = t;
  pickedFile.value = null;
  uploadOpen.value = true;
}
function onFilePicked(e: Event): void {
  const input = e.target as HTMLInputElement;
  pickedFile.value = input.files?.[0] ?? null;
}
async function doUpload(): Promise<void> {
  const t = uploadTarget.value;
  if (!t || !pickedFile.value) {
    message.warning('请先选择标书文件');
    return;
  }
  uploading.value = true;
  try {
    const resp = await uploadBiddingFile(pickedFile.value, 'bidding-document');
    const r = advance(t, 'UPLOAD_DOCUMENT', {
      docId: resp.fileId,
      docName: resp.name || pickedFile.value.name,
      docSize: resp.size,
    });
    if (!r.ok) {
      message.warning(r.error || '操作不允许');
      return;
    }
    uploadOpen.value = false;
    message.success('标书上传成功，流程已进入「已投标」并结束');
  } catch {
    /* 失败提示由请求层统一弹出 */
  } finally {
    uploading.value = false;
  }
}

/* ============ 展示工具 ============ */
const p2 = (n: number) => String(n).padStart(2, '0');
function fmtT(v?: number): string {
  if (!v) return '—';
  const d = new Date(v);
  if (Number.isNaN(+d)) return '—';
  return d.getFullYear() + '-' + p2(d.getMonth() + 1) + '-' + p2(d.getDate()) + ' ' + p2(d.getHours()) + ':' + p2(d.getMinutes());
}
function fmtSize(n?: number): string {
  if (n == null) return '';
  if (n >= 1024 * 1024) return (n / 1024 / 1024).toFixed(1) + ' MB';
  if (n >= 1024) return (n / 1024).toFixed(1) + ' KB';
  return n + ' B';
}

onMounted(() => {
  void doSync();
});
</script>

<template>
  <div class="bidding-page bid-ind">
    <ModuleNav active="process" />

    <div class="panel">
      <div class="head">
        <div>
          <span class="pt">投标流程管理</span>
          <span class="sub">关注自动建档意向 → 报名 → 资质审核 → 待投标 → 上传标书 → 已投标（结束）</span>
        </div>
        <div class="head-actions">
          <Button size="small" @click="router.push('/portal/bidding')">返回公告大厅</Button>
          <Button size="small" :loading="syncing" @click="doSync">同步关注</Button>
        </div>
      </div>

      <div class="filters">
        <span
          v-for="f in FILTERS"
          :key="f.value"
          class="chip"
          :class="{ on: stateFilter === f.value }"
          @click="stateFilter = f.value"
        >{{ f.label }}（{{ f.value === 'ALL' ? tracks.length : statCounts[f.value] || 0 }}）</span>
      </div>

      <Empty v-if="!tracks.length" description="还没有投标流程 — 到「公告大厅」关注感兴趣的项目后，会自动生成意向投标流程" />
      <div v-else-if="!filtered.length" class="empty">当前筛选条件下无流程</div>

      <article v-for="t in filtered" :key="t.id" class="item">
        <div class="item-top">
          <Tag :color="STATE_COLOR[t.currentState] || 'default'">{{ STATE_TEXT[t.currentState] || t.currentState }}</Tag>
          <span class="name">{{ t.projectName }}</span>
          <span v-if="t.issuerName" class="issuer">{{ t.issuerName }}</span>
          <span class="spacer"></span>
          <template v-if="t.currentState === 'INTENTION'">
            <a class="act" @click="registerBid(t)">报名投标</a>
            <a class="act danger" @click="cancelTrack(t)">取消</a>
          </template>
          <template v-else-if="t.currentState === 'QUALIFICATION_REVIEW'">
            <a class="act primary" @click="openReview(t, 'APPROVED')">审核通过</a>
            <a class="act danger" @click="openReview(t, 'REJECTED')">审核不通过</a>
            <a class="act danger" @click="cancelTrack(t)">取消</a>
          </template>
          <template v-else-if="t.currentState === 'PENDING_BID'">
            <a class="act primary" @click="openUpload(t)">上传标书</a>
            <a class="act danger" @click="cancelTrack(t)">取消</a>
          </template>
        </div>
        <div class="item-meta">
          <span>意向：{{ fmtT(t.intentionTime) }}</span>
          <span v-if="t.registerTime">报名：{{ fmtT(t.registerTime) }}</span>
          <span v-if="t.reviewTime">审核：{{ fmtT(t.reviewTime) }}</span>
          <span v-if="t.pendingBidTime">待投标：{{ fmtT(t.pendingBidTime) }}</span>
          <span v-if="t.biddedTime">已投标：{{ fmtT(t.biddedTime) }}</span>
          <span v-if="t.terminatedTime">结束：{{ fmtT(t.terminatedTime) }}</span>
          <span v-if="t.reviewReason" class="reason">{{ t.reviewResult === 'REJECTED' ? '不通过原因' : '审核说明' }}：{{ t.reviewReason }}</span>
          <span v-if="t.documentName" class="doc">标书：{{ t.documentName }}<template v-if="t.documentSize">（{{ fmtSize(t.documentSize) }}）</template></span>
          <span v-if="t.cancelReason" class="cancel">原因：{{ t.cancelReason }}</span>
        </div>
      </article>
    </div>

    <!-- 资质审核弹窗 -->
    <Modal v-model:open="reviewOpen" title="资质审核（手动标记）" :footer="null" width="480">
      <div class="space-y-3">
        <div class="text-xs text-muted-foreground">对「{{ reviewTarget?.projectName }}」标记资质审核结果。</div>
        <Radio.Group v-model:value="reviewResult" option-type="button" :options="[{ label: '审核通过', value: 'APPROVED' }, { label: '审核不通过', value: 'REJECTED' }]" />
        <div>
          <div class="mb-1 text-xs font-medium text-muted-foreground">{{ reviewResult === 'REJECTED' ? '不通过原因 *' : '审核说明（可选）' }}</div>
          <textarea v-model="reviewReason" class="ta" rows="2" placeholder="如 资料齐全，通过 / 社保记录缺失，不通过"></textarea>
        </div>
        <div class="text-right">
          <Button @click="reviewOpen = false">取消</Button>
          <Button class="ml-2" type="primary" :loading="reviewSubmitting" @click="submitReview">确认</Button>
        </div>
      </div>
    </Modal>

    <!-- 上传标书弹窗 -->
    <Modal v-model:open="uploadOpen" title="上传标书" :footer="null" width="480">
      <div class="space-y-3">
        <div class="text-xs text-muted-foreground">上传「{{ uploadTarget?.projectName }}」的投标文件（PDF/Word/压缩包等），上传后流程进入「已投标」并结束。</div>
        <input type="file" @change="onFilePicked" />
        <div v-if="pickedFile" class="text-xs text-muted-foreground">
          已选择：{{ pickedFile.name }}（{{ fmtSize(pickedFile.size) }}）
        </div>
        <div class="text-right">
          <Button @click="uploadOpen = false">取消</Button>
          <Button class="ml-2" type="primary" :loading="uploading" @click="doUpload">确认上传</Button>
        </div>
      </div>
    </Modal>
  </div>
</template>

<style scoped>
.bidding-page { max-width: 1080px; margin: 0 auto; padding: 18px 16px 40px; }
.panel { background: hsl(var(--card)); border: 1px solid hsl(var(--border)); border-radius: 10px; padding: 18px 20px; }
.head { display: flex; align-items: center; gap: 16px; margin-bottom: 14px; }
.head-actions { display: flex; align-items: center; gap: 8px; margin-left: auto; }
.pt { font-size: 18px; font-weight: 700; }
.sub { display: block; margin-top: 4px; font-size: 12px; color: hsl(var(--muted-foreground)); }
.filters { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 14px; }
.chip { border: 1px solid hsl(var(--border)); border-radius: 999px; padding: 4px 12px; font-size: 12px; color: hsl(var(--muted-foreground)); cursor: pointer; }
.chip.on { border-color: hsl(var(--primary)); color: hsl(var(--primary)); background: hsl(var(--primary) / 8%); }
.empty { padding: 40px 0; text-align: center; color: hsl(var(--muted-foreground)); font-size: 13px; }
.item { border: 1px solid hsl(var(--border)); border-radius: 8px; padding: 12px 14px; margin-bottom: 10px; }
.item-top { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.name { font-weight: 600; color: hsl(var(--foreground)); }
.issuer { font-size: 12px; color: hsl(var(--muted-foreground)); }
.spacer { flex: 1; }
.act { font-size: 12.5px; color: hsl(var(--primary)); cursor: pointer; }
.act.primary { font-weight: 700; }
.act.danger { color: hsl(var(--destructive)); }
.item-meta { display: flex; flex-wrap: wrap; gap: 4px 16px; margin-top: 8px; font-size: 12px; color: hsl(var(--muted-foreground)); }
.item-meta .doc { color: hsl(var(--foreground)); }
.item-meta .reason { color: hsl(var(--warning)); }
.item-meta .cancel { color: hsl(var(--destructive)); }
.ta { width: 100%; border: 1px solid hsl(var(--border)); border-radius: 6px; padding: 8px 10px; font-size: 13px; outline: none; resize: vertical; }
</style>
