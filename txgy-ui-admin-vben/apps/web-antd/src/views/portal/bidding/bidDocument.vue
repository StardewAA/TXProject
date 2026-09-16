<!--
  招投标前台 · 标书制作（个人工作台）
  业务：面向「待投标」流程编制/上传投标文件（真实接口 POST /bidding/file/upload），并展示已上传标书。
  口径（前端为主）：流程数据来自共享 useBiddingProcess（localStorage 本地状态机，与投标流程管理同源）。
  依据：电子招标投标办法 第13条（部门规章）；招标投标法 第26条（法律）。
-->
<script lang="ts" setup>
import { computed, ref } from 'vue';
import { useRouter } from 'vue-router';

import { Button, Empty, message, Modal, Tag } from 'ant-design-vue';

import { uploadBiddingFile } from '#/api/bidding/file';

import ModuleNav from './components/ModuleNav.vue';
import { STATE_COLOR, STATE_TEXT, useBiddingProcess, type PortalTrack } from './composables/useBiddingProcess';

const router = useRouter();
const { advance, tracks } = useBiddingProcess();

const pendingTracks = computed(() => tracks.value.filter((t) => t.currentState === 'PENDING_BID'));
const biddedTracks = computed(() => tracks.value.filter((t) => t.currentState === 'BIDDED'));

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

function fmtSize(n?: number): string {
  if (n == null) return '';
  if (n >= 1024 * 1024) return (n / 1024 / 1024).toFixed(1) + ' MB';
  if (n >= 1024) return (n / 1024).toFixed(1) + ' KB';
  return n + ' B';
}
const p2 = (n: number) => String(n).padStart(2, '0');
function fmtT(v?: number): string {
  if (!v) return '—';
  const d = new Date(v);
  if (Number.isNaN(+d)) return '—';
  return d.getFullYear() + '-' + p2(d.getMonth() + 1) + '-' + p2(d.getDate()) + ' ' + p2(d.getHours()) + ':' + p2(d.getMinutes());
}
</script>

<template>
  <div class="bidding-page bid-ind">
    <ModuleNav active="bid-document" />

    <div class="panel">
      <div class="head">
        <div>
          <span class="pt">标书制作</span>
          <span class="sub">面向「待投标」项目编制并上传投标文件；上传成功后流程进入「已投标」</span>
        </div>
        <div class="head-actions">
          <Button size="small" @click="router.push('/portal/bidding')">返回公告大厅</Button>
          <Button size="small" @click="router.push('/portal/bidding/process')">去投标流程管理</Button>
        </div>
      </div>

      <Empty v-if="!pendingTracks.length && !biddedTracks.length" description="暂无待编制或已上传的标书 — 到「公告大厅」关注项目并走完报名/资质审核后，会出现在这里" />

      <template v-else>
        <!-- 待编制标书（待投标） -->
        <section v-if="pendingTracks.length">
          <div class="sec-title">待编制标书（{{ pendingTracks.length }}）</div>
          <article v-for="t in pendingTracks" :key="t.id" class="item">
            <div class="item-top">
              <Tag :color="STATE_COLOR[t.currentState]">{{ STATE_TEXT[t.currentState] }}</Tag>
              <span class="name">{{ t.projectName }}</span>
              <span v-if="t.issuerName" class="issuer">{{ t.issuerName }}</span>
              <span class="spacer"></span>
              <a class="act primary" @click="openUpload(t)">上传标书</a>
            </div>
            <div class="item-meta">
              <span>待投标：{{ fmtT(t.pendingBidTime) }}</span>
            </div>
          </article>
        </section>

        <!-- 已上传标书（已投标） -->
        <section v-if="biddedTracks.length">
          <div class="sec-title">已上传标书（{{ biddedTracks.length }}）</div>
          <article v-for="t in biddedTracks" :key="t.id" class="item">
            <div class="item-top">
              <Tag :color="STATE_COLOR[t.currentState]">{{ STATE_TEXT[t.currentState] }}</Tag>
              <span class="name">{{ t.projectName }}</span>
              <span v-if="t.issuerName" class="issuer">{{ t.issuerName }}</span>
            </div>
            <div class="item-meta">
              <span class="doc">标书：{{ t.documentName || '—' }}<template v-if="t.documentSize">（{{ fmtSize(t.documentSize) }}）</template></span>
              <span>上传：{{ fmtT(t.biddedTime) }}</span>
            </div>
          </article>
        </section>
      </template>
    </div>

    <!-- 上传标书弹窗 -->
    <Modal v-model:open="uploadOpen" title="上传标书" :footer="null" width="480">
      <div class="space-y-3">
        <div class="text-xs text-muted-foreground">上传「{{ uploadTarget?.projectName }}」的投标文件，上传后流程进入「已投标」并结束。</div>
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
.sec-title { margin: 14px 0 8px; font-size: 13.5px; font-weight: 700; color: hsl(var(--foreground)); }
.item { border: 1px solid hsl(var(--border)); border-radius: 8px; padding: 12px 14px; margin-bottom: 10px; }
.item-top { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.name { font-weight: 600; color: hsl(var(--foreground)); }
.issuer { font-size: 12px; color: hsl(var(--muted-foreground)); }
.spacer { flex: 1; }
.act { font-size: 12.5px; color: hsl(var(--primary)); cursor: pointer; }
.act.primary { font-weight: 700; }
.item-meta { display: flex; flex-wrap: wrap; gap: 4px 16px; margin-top: 8px; font-size: 12px; color: hsl(var(--muted-foreground)); }
.item-meta .doc { color: hsl(var(--foreground)); }
</style>
