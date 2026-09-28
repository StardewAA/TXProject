<script lang="ts" setup>
/* ============================================================================
  CA 签章台账（统计卡 + 台账列表 + 真实验签 + 红章文件预览/下载）。
  权限：bidding:ca:query | bidding:ca:verify。
  契约：GET /bidding/ca/seal/stats 统计 · /page 分页 · /get 详情 · POST /verify 真实验签。
  界面口径（用户视角）：列表只放业务关心的列（原文件名/签章单位/签章时间/红章文件/验签状态），
    摘要、签名值等证据链细节收进「证据链」弹窗；不再放"生成演示数据"这类开发者工具。
    「预览」走 fileId → /bidding/file/download（内联出口）；sealFileUrl 是签名下载地址，只用于「下载」。
============================================================================ */
import { onMounted, reactive, ref } from 'vue';

import { message } from 'ant-design-vue';

import { CaApi } from '#/api/bidding/ca';

import { fmtDateTime } from '../certificate/format';
import PdfPreview from '../vo/PdfPreview.vue';

import '../certificate/cert.css';

/* ============ 统计卡 ============ */
const stats = ref<CaApi.SealStats>({ total: 0, pass: 0, fail: 0, unverified: 0 });
const statsLoading = ref(false);

async function loadStats(): Promise<void> {
  statsLoading.value = true;
  try {
    const s = await CaApi.getSealStats();
    stats.value = s ?? {};
  } catch (e) {
    message.error('统计加载失败：' + ((e as Error).message || String(e)));
  } finally {
    statsLoading.value = false;
  }
}

/* ============ 台账分页 ============ */
const list = ref<CaApi.SealRecord[]>([]);
const total = ref(0);
const loading = ref(false);
const query = reactive({
  pageNo: 1,
  pageSize: 10,
  documentName: undefined as string | undefined,
  orgName: undefined as string | undefined,
  verifyStatus: undefined as string | undefined,
  trackId: undefined as string | undefined,
});

const VERIFY_STATUS_OPTIONS = [
  { label: '全部', value: undefined as string | undefined },
  { label: '未验签', value: 'UNVERIFIED' },
  { label: '验签通过', value: 'PASS' },
  { label: '验签不通过', value: 'FAIL' },
];

async function loadPage(): Promise<void> {
  loading.value = true;
  try {
    const page = await CaApi.getSealPage({
      pageNo: query.pageNo,
      pageSize: query.pageSize,
      documentName: query.documentName || undefined,
      orgName: query.orgName || undefined,
      verifyStatus: query.verifyStatus || undefined,
      trackId: query.trackId || undefined,
    });
    list.value = page?.list ?? [];
    total.value = page?.total ?? 0;
  } catch (e) {
    message.error('台账加载失败：' + ((e as Error).message || String(e)));
  } finally {
    loading.value = false;
  }
}

function search(): void {
  query.pageNo = 1;
  void loadPage();
}
function resetQuery(): void {
  query.documentName = undefined;
  query.orgName = undefined;
  query.verifyStatus = undefined;
  query.trackId = undefined;
  search();
}

/* ============ 验签 / 详情 ============ */
const verifyingId = ref<null | number | string>(null);
const detail = ref<CaApi.SealRecord | null>(null);
const detailOpen = ref(false);

async function doVerify(row: CaApi.SealRecord): Promise<void> {
  verifyingId.value = row.id ?? null;
  try {
    const rec = await CaApi.verifySeal(row.id as number | string);
    const pass = rec?.verifyStatus === 'PASS';
    message.success(pass ? '验签通过：SM2 签名有效（防篡改 + 身份绑定）' : '验签不通过：签名与文件指纹不匹配（篡改检出）');
    await loadStats();
    await loadPage();
  } catch (e) {
    message.error('验签失败：' + ((e as Error).message || String(e)));
  } finally {
    verifyingId.value = null;
  }
}

async function showDetail(row: CaApi.SealRecord): Promise<void> {
  try {
    detail.value = await CaApi.getSealDetail(row.id as number | string);
    detailOpen.value = true;
  } catch (e) {
    message.error('详情加载失败：' + ((e as Error).message || String(e)));
  }
}

/* ============ 红章文件预览 ============ */
// 预览必须走 fileId → /bidding/file/download（内联出口）；sealFileUrl 是**签名下载地址**，
// 塞进预览会让"点预览"直接变下载（本次一并改掉），它只用于 <a download>。
const previewOpen = ref(false);
const previewFileId = ref<number | string | undefined>(undefined);
const previewName = ref('');

function openSealPreview(record: CaApi.SealRecord): void {
  previewFileId.value = record.sealFileId;
  previewName.value = record.sealFileName || record.documentName || '红章文件.pdf';
  previewOpen.value = true;
}

/* ============ 展示辅助 ============ */
const typeText = (t?: string) => (t === 'pdf' ? 'PDF' : t === 'other' ? '其他' : t || '—');
const fmtTime = (v?: number | string) => (v === undefined || v === null || v === '' ? '—' : fmtDateTime(v));

/* ============ 表格列 ============ */
const columns = [
  { title: '原文件名', key: 'documentName', dataIndex: 'documentName', width: 260 },
  { title: '签章单位', key: 'orgName', dataIndex: 'orgName', width: 150 },
  { title: '签章时间', key: 'sealTime', dataIndex: 'sealTime', width: 150 },
  { title: '红章文件', key: 'sealFileUrl', width: 130 },
  { title: '验签状态', key: 'verifyStatus', width: 150 },
  { title: '创建时间', key: 'createTime', dataIndex: 'createTime', width: 150 },
  { title: '操作', key: 'actions', width: 150 },
];

onMounted(() => {
  void loadStats();
  void loadPage();
});
</script>

<template>
  <div class="bid-ind ct-page">
    <!-- 页头 -->
    <div class="ct-head">
      <div>
        <h1 class="ct-title">CA 签章台账</h1>
        <p class="ct-sub">
          文件电子签章证据链（bidding_ca_seal）：对上传文件做真实 SM2 签名 + PDF 红章/分离式签名并落库；
          每行可「验签」真实回写状态，红章 PDF 可直接预览/下载。测试配置（bidding.ca.sdk.enabled=false）
          由后端本地真实签名；真实 SDK 模式（=true）接入官方 CA 平台。
        </p>
      </div>
    </div>

    <!-- 统计卡（/stats） -->
    <div class="ct-kpis">
      <div class="ct-kpi k-blue"><span class="k-label">签章总数</span><span class="k-num">{{ stats.total ?? 0 }}</span></div>
      <div class="ct-kpi k-green"><span class="k-label">验签通过</span><span class="k-num">{{ stats.pass ?? 0 }}</span></div>
      <div class="ct-kpi k-red"><span class="k-label">验签不通过</span><span class="k-num">{{ stats.fail ?? 0 }}</span></div>
      <div class="ct-kpi k-orange"><span class="k-label">未验签</span><span class="k-num">{{ stats.unverified ?? 0 }}</span></div>
    </div>

    <!-- 筛选条 -->
    <div class="ct-filter">
      <a-input v-model:value="query.documentName" placeholder="原文件名（模糊）" allow-clear style="width: 220px" @press-enter="search" />
      <a-input v-model:value="query.orgName" placeholder="签章单位（模糊）" allow-clear style="width: 200px" @press-enter="search" />
      <a-input v-model:value="query.trackId" placeholder="投标流程跟踪 id" allow-clear style="width: 180px" @press-enter="search" />
      <a-select v-model:value="query.verifyStatus" style="width: 140px" @change="search">
        <a-select-option v-for="o in VERIFY_STATUS_OPTIONS" :key="o.value ?? 'all'" :value="o.value">{{ o.label }}</a-select-option>
      </a-select>
      <a-button type="primary" @click="search">查询</a-button>
      <a-button @click="resetQuery">重置</a-button>
    </div>

    <!-- 台账列表（/page） -->
    <a-table
      :columns="columns"
      :data-source="list"
      :loading="loading"
      :pagination="{
        current: query.pageNo,
        pageSize: query.pageSize,
        total,
        showSizeChanger: true,
        showTotal: (t: number) => `共 ${ t } 条`,
      }"
      row-key="id"
      size="middle"
      bordered
      @change="(p: any) => { query.pageNo = p.current ?? 1; query.pageSize = p.pageSize ?? 10; void loadPage() }"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'documentName'">
          <span :title="record.documentId">{{ record.documentName || '—' }}</span>
          <div class="ct-sub-meta">类型：{{ typeText(record.documentType) }}<template v-if="record.trackId"> · 跟踪ID：{{ record.trackId }}</template></div>
        </template>
        <template v-else-if="column.key === 'sealFileUrl'">
          <div v-if="record.sealFileId || record.sealFileUrl" class="ct-actions">
            <a-button v-if="record.sealFileId" size="small" type="link" @click="openSealPreview(record)">预览</a-button>
            <a v-if="record.sealFileUrl" :href="record.sealFileUrl" download>下载</a>
          </div>
          <span v-else>—</span>
        </template>
        <template v-else-if="column.key === 'verifyStatus'">
          <a-tag v-if="record.verifyStatus === 'PASS'" color="green">验签通过</a-tag>
          <a-tag v-else-if="record.verifyStatus === 'FAIL'" color="red">验签不通过</a-tag>
          <a-tag v-else color="orange">未验签</a-tag>
          <div v-if="record.verifyTime" class="ct-sub-meta">{{ fmtTime(record.verifyTime) }}</div>
        </template>
        <template v-else-if="column.key === 'actions'">
          <a-button size="small" type="link" :loading="verifyingId === record.id" @click="doVerify(record)">验签</a-button>
          <a-button size="small" type="link" @click="showDetail(record)">证据链</a-button>
        </template>
        <template v-else-if="column.key === 'createTime'">
          {{ fmtTime(record.createTime) }}
        </template>
        <template v-else-if="column.key === 'sealTime'">
          {{ fmtTime(record.sealTime) }}
        </template>
      </template>
    </a-table>

    <!-- 红章文件预览：fileId → 内联出口（不是签名下载地址） -->
    <PdfPreview
      v-model:open="previewOpen"
      :file-id="previewFileId"
      :file-name="previewName"
      title="红章文件预览"
    />

    <!-- 证据链详情（/get） -->
    <a-modal v-model:open="detailOpen" title="CA 签章证据链" :width="720" :footer="null">
      <a-descriptions v-if="detail" :column="1" size="small" bordered>
        <a-descriptions-item label="原文件名">{{ detail.documentName || '—' }}</a-descriptions-item>
        <a-descriptions-item label="签章单位">{{ detail.orgName || '—' }}</a-descriptions-item>
        <a-descriptions-item label="签章时间">{{ fmtTime(detail.sealTime) }}</a-descriptions-item>
        <a-descriptions-item label="CA 证书序列号">{{ detail.certSn || '—' }}</a-descriptions-item>
        <a-descriptions-item label="文档 SM3 指纹"><code class="ct-code">{{ detail.sm3Hash || '—' }}</code></a-descriptions-item>
        <a-descriptions-item label="SM2 签名值"><code class="ct-code">{{ detail.signature || '—' }}</code></a-descriptions-item>
        <a-descriptions-item label="SHA-256"><code class="ct-code">{{ detail.fileSha256 || '—' }}</code></a-descriptions-item>
        <a-descriptions-item label="验签状态">
          <a-tag v-if="detail.verifyStatus === 'PASS'" color="green">验签通过</a-tag>
          <a-tag v-else-if="detail.verifyStatus === 'FAIL'" color="red">验签不通过</a-tag>
          <a-tag v-else color="orange">未验签</a-tag>
        </a-descriptions-item>
        <a-descriptions-item v-if="detail.verifyTime" label="验签时间">{{ fmtTime(detail.verifyTime) }}</a-descriptions-item>
        <a-descriptions-item v-if="detail.trackId" label="投标流程跟踪">{{ detail.trackId }}</a-descriptions-item>
        <a-descriptions-item label="操作人ID">{{ detail.operatorUserId ?? '—' }}</a-descriptions-item>
        <a-descriptions-item v-if="detail.remark" label="备注">{{ detail.remark }}</a-descriptions-item>
      </a-descriptions>
    </a-modal>
  </div>
</template>

<style scoped>
.ct-filter { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin: 12px 0; }
.ct-sub-meta { font-size: 11.5px; color: var(--ind-weak-text, hsl(var(--muted-foreground))); margin-top: 2px; }
.ct-code { word-break: break-all; background: var(--ind-bone, hsl(var(--muted))); padding: 2px 6px; border-radius: 4px; font-size: 12px; }
</style>
