<!--
  招投标前台 · 合规审查（个人工作台）
  业务：对「已投标」流程的标书做合规核验，展示审查结论（通过/不通过 + 意见）。
  口径（前端为主）：后端合规审查接口未上线，审查结论为前端本地标记（随流程数据存 localStorage）。
  依据：电子招标投标办法 第9/13条（部门规章）；招标投标法 第26条（法律）。
-->
<script lang="ts" setup>
import { computed, ref } from 'vue';
import { useRouter } from 'vue-router';

import { Button, Empty, message, Modal, Radio, Tag } from 'ant-design-vue';

import ModuleNav from './components/ModuleNav.vue';
import {
  COMPLIANCE_TEXT,
  STATE_COLOR,
  STATE_TEXT,
  useBiddingProcess,
  type PortalTrack,
} from './composables/useBiddingProcess';

const router = useRouter();
const { setCompliance, tracks } = useBiddingProcess();

const biddedTracks = computed(() => tracks.value.filter((t) => t.currentState === 'BIDDED'));

const COMPLIANCE_COLOR: Record<string, string> = {
  PENDING: 'orange',
  PASS: 'green',
  REJECT: 'red',
};

/* ============ 合规审查标记 ============ */
const open = ref(false);
const target = ref<PortalTrack | null>(null);
const result = ref<'PASS' | 'REJECT'>('PASS');
const remark = ref('');

function openReview(t: PortalTrack, preset: 'PASS' | 'REJECT'): void {
  target.value = t;
  result.value = preset;
  remark.value = '';
  open.value = true;
}
function submit(): void {
  const t = target.value;
  if (!t) return;
  if (result.value === 'REJECT' && !remark.value.trim()) {
    message.warning('审查不通过请填写意见');
    return;
  }
  setCompliance(t, result.value, remark.value.trim() || undefined);
  open.value = false;
  message.success(result.value === 'PASS' ? '已标记审查通过' : '已标记审查不通过');
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
    <ModuleNav active="compliance" />

    <div class="panel">
      <div class="head">
        <div>
          <span class="pt">合规审查</span>
          <span class="sub">对「已投标」流程的标书做合规核验并留痕（审查结论本地标记，后端合规接口上线后以服务端为准）</span>
        </div>
        <div class="head-actions">
          <Button size="small" @click="router.push('/portal/bidding')">返回公告大厅</Button>
          <Button size="small" @click="router.push('/portal/bidding/process')">去投标流程管理</Button>
        </div>
      </div>

      <Empty v-if="!biddedTracks.length" description="暂无已投标的标书可审查 — 上传标书后，这里会出现待审查记录" />

      <article v-for="t in biddedTracks" :key="t.id" class="item">
        <div class="item-top">
          <Tag :color="STATE_COLOR[t.currentState]">{{ STATE_TEXT[t.currentState] }}</Tag>
          <span class="name">{{ t.projectName }}</span>
          <span v-if="t.issuerName" class="issuer">{{ t.issuerName }}</span>
          <Tag :color="COMPLIANCE_COLOR[t.complianceStatus || 'PENDING'] || 'default'">{{ COMPLIANCE_TEXT[t.complianceStatus || 'PENDING'] }}</Tag>
          <span class="spacer"></span>
          <a class="act primary" @click="openReview(t, 'PASS')">审查通过</a>
          <a class="act danger" @click="openReview(t, 'REJECT')">审查不通过</a>
        </div>
        <div class="item-meta">
          <span class="doc">标书：{{ t.documentName || '—' }}<template v-if="t.documentSize">（{{ fmtSize(t.documentSize) }}）</template></span>
          <span>上传：{{ fmtT(t.biddedTime) }}</span>
          <span v-if="t.complianceAt">审查：{{ fmtT(t.complianceAt) }}</span>
          <span v-if="t.complianceRemark" class="remark">意见：{{ t.complianceRemark }}</span>
        </div>
      </article>
    </div>

    <!-- 合规审查标记弹窗 -->
    <Modal v-model:open="open" title="合规审查" :footer="null" width="480">
      <div class="space-y-3">
        <div class="text-xs text-muted-foreground">对「{{ target?.projectName }}」的标书标记审查结论。</div>
        <Radio.Group v-model:value="result" option-type="button" :options="[{ label: '审查通过', value: 'PASS' }, { label: '审查不通过', value: 'REJECT' }]" />
        <div>
          <div class="mb-1 text-xs font-medium text-muted-foreground">{{ result === 'REJECT' ? '不通过意见 *' : '审查意见（可选）' }}</div>
          <textarea v-model="remark" class="ta" rows="2" placeholder="如 符合招标文件要求 / 未提供社保记录"></textarea>
        </div>
        <div class="text-right">
          <Button @click="open = false">取消</Button>
          <Button class="ml-2" type="primary" @click="submit">确认</Button>
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
.item-meta .remark { color: hsl(var(--destructive)); }
.ta { width: 100%; border: 1px solid hsl(var(--border)); border-radius: 6px; padding: 8px 10px; font-size: 13px; outline: none; resize: vertical; }
</style>
