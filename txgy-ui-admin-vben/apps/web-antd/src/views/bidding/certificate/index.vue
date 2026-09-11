<script setup lang="ts">
/* 证照台账（契约 §2 全部 + §4 验真 + §5 预警补跑 + §3 文件版本 + §6.6 轨迹入口）
 * 权限：bidding:certificate:{query,create,update,verify,file,alert-run}、bidding:certificate:lifecycle:apply
 * 口径：状态/类型/来源等标签一律取字典；"即将到期"天数取配置 cert.expiring.window.days（不硬编码阈值） */
import { computed, onMounted, reactive, ref } from 'vue';

import { useAccess } from '@vben/access';
import { useAccessStore } from '@vben/stores';

import { message, Modal } from 'ant-design-vue';

import {
  certErrorMsg,
  type CertificateApi,
  getCertificate,
  getCertificatePage,
  runCertAlertDispatch,
  runCertAlertScan,
} from '#/api/bidding/certificate';

import CertFiles from './modules/cert-files.vue';
import CertForm from './modules/cert-form.vue';
import CertTimeline from './modules/cert-timeline.vue';
import CertVerify from './modules/cert-verify.vue';
import LifecycleApply from './modules/lifecycle-apply.vue';
import StatusModal from './modules/status-modal.vue';
import { useCertMeta } from './useCertMeta';

import './cert.css';

const { hasAccessByCodes } = useAccess();
const accessStore = useAccessStore();
const { configNumber, dictLabel, dictOptions, dictTag, metaLoaded, reloadMeta } = useCertMeta();

/** 权限判定：权限码未下发时放行（最终由后端 @PreAuthorize 裁决），避免界面被锁死 */
function can(code: string): boolean {
  const codes = accessStore.accessCodes ?? [];
  if (codes.length === 0) return true;
  return codes.includes('*') || codes.includes('*:*:*') || hasAccessByCodes([code]);
}

const query = reactive<CertificateApi.PageReq>({
  pageNo: 1,
  pageSize: 10,
  certName: undefined,
  certNo: undefined,
  certType: undefined,
  certStatus: undefined,
  holderName: undefined,
  verificationStatus: undefined,
});

const loading = ref(false);
const rows = ref<CertificateApi.Certificate[]>([]);
const total = ref(0);
const maskNo = ref(true);

const formOpen = ref(false);
const formRecord = ref<CertificateApi.Certificate | null>(null);
const statusOpen = ref(false);
const statusRecord = ref<CertificateApi.Certificate | null>(null);
const applyOpen = ref(false);
const applyRecord = ref<CertificateApi.Certificate | null>(null);

const drawerOpen = ref(false);
const detailLoading = ref(false);
const detail = ref<CertificateApi.Certificate | null>(null);
const tab = ref('basic');

const expiringDays = computed(() => configNumber('cert.expiring.window.days', 60));

async function load() {
  loading.value = true;
  try {
    const page = await getCertificatePage({ ...query });
    rows.value = page.list ?? [];
    total.value = page.total ?? 0;
  } catch {
    rows.value = [];
    total.value = 0;
  } finally {
    loading.value = false;
  }
}

function resetQuery() {
  query.certName = undefined;
  query.certNo = undefined;
  query.certType = undefined;
  query.certStatus = undefined;
  query.holderName = undefined;
  query.verificationStatus = undefined;
  query.pageNo = 1;
  void load();
}

onMounted(load);

/* ===== 展示派生（全部由数据 + 配置驱动） ===== */
function daysLeft(validTo?: null | string): null | number {
  if (!validTo) return null;
  const end = new Date(`${validTo}T23:59:59`).getTime();
  if (Number.isNaN(end)) return null;
  return Math.ceil((end - Date.now()) / 86_400_000);
}
function validityText(r: CertificateApi.Certificate): string {
  if (!r.validFrom && !r.validTo) return '—';
  return `${r.validFrom || '—'} ~ ${r.validTo || '长期'}`;
}
function validityTag(r: CertificateApi.Certificate): null | { color: string; label: string } {
  const d = daysLeft(r.validTo);
  if (d === null) return null; // 长期有效
  if (d < 0) return { color: 'red', label: `已过期 ${Math.abs(d)} 天` };
  if (d <= expiringDays.value) return { color: 'orange', label: `临期 ${d} 天` };
  return null;
}
function maskCertNo(no?: string): string {
  if (!no) return '—';
  if (!maskNo.value || no.length <= 8) return no;
  return `${no.slice(0, 4)}${'*'.repeat(Math.min(6, no.length - 8))}${no.slice(-4)}`;
}

const kpi = computed(() => {
  const list = rows.value;
  const expiring = list.filter((r) => {
    const d = daysLeft(r.validTo);
    return d !== null && d >= 0 && d <= expiringDays.value;
  }).length;
  const expired = list.filter((r) => {
    const d = daysLeft(r.validTo);
    return d !== null && d < 0;
  }).length;
  const longTerm = list.filter((r) => !r.validTo).length;
  const unverified = list.filter((r) => !r.lastVerifiedAt).length;
  return { expiring, expired, longTerm, unverified };
});

const columns = [
  { title: '证照', dataIndex: 'certName', key: 'certName', width: 260 },
  { title: '类型', dataIndex: 'certType', key: 'certType', width: 130 },
  { title: '持证主体', dataIndex: 'holderName', key: 'holderName', width: 180 },
  { title: '有效期', dataIndex: 'validTo', key: 'validity', width: 240 },
  { title: '状态', dataIndex: 'certStatus', key: 'certStatus', width: 120 },
  { title: '验真', dataIndex: 'verificationStatus', key: 'verificationStatus', width: 150 },
  { title: '来源', dataIndex: 'sourceChannel', key: 'sourceChannel', width: 100 },
  { title: '更新时间', dataIndex: 'updateTime', key: 'updateTime', width: 165 },
  { title: '操作', key: 'action', width: 230, fixed: 'right' },
];

/* ===== 行为 ===== */
function onCreate() {
  formRecord.value = null;
  formOpen.value = true;
}
function onEdit(r: CertificateApi.Certificate) {
  formRecord.value = r;
  formOpen.value = true;
}
function onStatus(r: CertificateApi.Certificate) {
  statusRecord.value = r;
  statusOpen.value = true;
}
function onApply(r: CertificateApi.Certificate) {
  applyRecord.value = r;
  applyOpen.value = true;
}

async function openDetail(r: CertificateApi.Certificate) {
  drawerOpen.value = true;
  tab.value = 'basic';
  detail.value = r;
  detailLoading.value = true;
  try {
    detail.value = await getCertificate(r.id);
  } catch {
    /* 用列表行兜底展示 */
  } finally {
    detailLoading.value = false;
  }
}
async function refreshDetail() {
  if (!detail.value?.id) return;
  try {
    detail.value = await getCertificate(detail.value.id);
  } catch {
    /* 忽略 */
  }
  void load();
}

function confirmScan() {
  Modal.confirm({
    title: '手动触发到期预警扫描',
    content: '平时由定时任务执行；此处用于补跑（同日幂等，不会产生重复预警）。',
    okText: '开始扫描',
    cancelText: '取消',
    async onOk() {
      try {
        const n = await runCertAlertScan();
        message.success(`扫描完成，本次生成预警 ${n} 条`);
      } catch (error: unknown) {
        message.warning(certErrorMsg(error));
      }
    },
  });
}
function confirmDispatch() {
  Modal.confirm({
    title: '手动派发通知发件箱',
    content: '把 PENDING/FAILED 到点的通知立刻投递（已发送记录不会重复发送）。',
    okText: '立即派发',
    cancelText: '取消',
    async onOk() {
      try {
        const n = await runCertAlertDispatch();
        message.success(`派发完成，共 ${n} 条`);
      } catch (error: unknown) {
        message.warning(certErrorMsg(error));
      }
    },
  });
}

function onSaved(id?: number) {
  void load();
  if (id && drawerOpen.value && detail.value?.id === id) void refreshDetail();
}

const typeFilters = computed(() => dictOptions('cert_type'));
const statusFilters = computed(() => dictOptions('cert_status'));
const verifyFilters = computed(() => dictOptions('verification_status'));
</script>

<template>
  <div class="bid-ind ct-page">
    <div class="ct-head">
      <div>
        <h2 class="ct-title">证照台账</h2>
        <p class="ct-sub">
          企业名下全部证照的统一台账：是否有效、是否即将到期、是否已验真一目了然；建档后可继续走「文件版本 →
          验真 → 变更/续期/注销年检」全生命周期。枚举与阈值均来自后台字典/配置，调整后无需前端发版。
        </p>
      </div>
      <div class="ct-actions">
        <a-button v-if="can('bidding:certificate:create')" type="primary" @click="onCreate">证照建档</a-button>
        <a-button v-if="can('bidding:certificate:alert-run')" @click="confirmScan">预警扫描</a-button>
        <a-button v-if="can('bidding:certificate:alert-run')" @click="confirmDispatch">派发通知</a-button>
        <a-button @click="load">刷新</a-button>
        <a-button v-if="!metaLoaded" type="link" @click="reloadMeta">重载字典/配置</a-button>
      </div>
    </div>

    <div class="ct-kpis">
      <div class="ct-kpi k-blue">
        <div class="k-label">台账总数</div>
        <div class="k-num">{{ total }}</div>
        <div class="k-note">按当前筛选条件</div>
      </div>
      <div class="ct-kpi k-orange">
        <div class="k-label">本页临期</div>
        <div class="k-num">{{ kpi.expiring }}</div>
        <div class="k-note">距到期 ≤ {{ expiringDays }} 天（配置）</div>
      </div>
      <div class="ct-kpi k-red">
        <div class="k-label">本页已过期</div>
        <div class="k-num">{{ kpi.expired }}</div>
        <div class="k-note">有效期止早于今天</div>
      </div>
      <div class="ct-kpi k-green">
        <div class="k-label">本页长期有效</div>
        <div class="k-num">{{ kpi.longTerm }}</div>
        <div class="k-note">无有效期止，不入到期扫描</div>
      </div>
      <div class="ct-kpi">
        <div class="k-label">本页未验真</div>
        <div class="k-num">{{ kpi.unverified }}</div>
        <div class="k-note">无最近验真时间</div>
      </div>
    </div>

    <div class="ct-panel">
      <div class="ct-filter">
        <a-input
          v-model:value="query.certName"
          placeholder="证照名称（模糊）"
          style="width: 190px"
          allow-clear
          @press-enter="load"
        />
        <a-input
          v-model:value="query.certNo"
          placeholder="证书编号（精确）"
          style="width: 180px"
          allow-clear
          @press-enter="load"
        />
        <a-select
          v-if="typeFilters.length > 0"
          v-model:value="query.certType"
          :options="typeFilters"
          placeholder="证照大类"
          style="width: 160px"
          allow-clear
          show-search
          option-filter-prop="label"
        />
        <a-input
          v-else
          v-model:value="query.certType"
          placeholder="证照大类 code"
          style="width: 160px"
          allow-clear
          @press-enter="load"
        />
        <a-select
          v-model:value="query.certStatus"
          :options="statusFilters"
          placeholder="台账状态"
          style="width: 150px"
          allow-clear
        />
        <a-input
          v-model:value="query.holderName"
          placeholder="持证主体（模糊）"
          style="width: 170px"
          allow-clear
          @press-enter="load"
        />
        <a-select
          v-model:value="query.verificationStatus"
          :options="verifyFilters"
          placeholder="验真状态"
          style="width: 150px"
          allow-clear
        />
        <a-button type="primary" @click="load">查询</a-button>
        <a-button @click="resetQuery">重置</a-button>
        <span class="ct-ml">
          编号掩码
          <a-switch v-model:checked="maskNo" size="small" />
        </span>
      </div>

      <div class="ct-panel-b">
        <a-table
          class="ct-table"
          :columns="columns"
          :data-source="rows"
          :loading="loading"
          :pagination="{
            current: query.pageNo,
            pageSize: query.pageSize,
            total,
            showSizeChanger: true,
            showTotal: (t: number) => `共 ${t} 条`,
          }"
          row-key="id"
          size="small"
          :scroll="{ x: 1600 }"
          @change="
            (p: any) => {
              query.pageNo = p.current;
              query.pageSize = p.pageSize;
              load();
            }
          "
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'certName'">
              <div style="font-weight: 700">{{ record.certName }}</div>
              <div class="ct-id">
                {{ maskCertNo(record.certNo) }} · #{{ record.id }}
              </div>
            </template>
            <template v-else-if="column.key === 'certType'">
              {{ dictLabel('cert_type', record.certType) }}
              <div v-if="record.certLevel" class="ct-panel-meta">{{ record.certLevel }}</div>
            </template>
            <template v-else-if="column.key === 'holderName'">
              {{ record.holderName || '—' }}
              <div class="ct-panel-meta">{{ record.holderType ? dictLabel('holder_type', record.holderType) : '' }}</div>
            </template>
            <template v-else-if="column.key === 'validity'">
              <div>{{ validityText(record) }}</div>
              <a-tag v-if="validityTag(record)" :color="validityTag(record)!.color">
                {{ validityTag(record)!.label }}
              </a-tag>
              <a-tag v-else-if="!record.validTo" color="green">长期</a-tag>
            </template>
            <template v-else-if="column.key === 'certStatus'">
              <a-tag :color="dictTag('cert_status', record.certStatus).color">
                {{ dictTag('cert_status', record.certStatus).label }}
              </a-tag>
            </template>
            <template v-else-if="column.key === 'verificationStatus'">
              <a-tag :color="dictTag('verification_status', record.verificationStatus).color">
                {{ dictTag('verification_status', record.verificationStatus).label }}
              </a-tag>
              <div class="ct-panel-meta">{{ record.lastVerifiedAt || '未验真' }}</div>
            </template>
            <template v-else-if="column.key === 'sourceChannel'">
              {{ record.sourceChannel ? dictLabel('source_channel', record.sourceChannel) : '—' }}
            </template>
            <template v-else-if="column.key === 'action'">
              <a-space size="small">
                <a @click="openDetail(record)">详情</a>
                <a v-if="can('bidding:certificate:update')" @click="onEdit(record)">编辑</a>
                <a v-if="can('bidding:certificate:update')" @click="onStatus(record)">状态</a>
                <a v-if="can('bidding:certificate:lifecycle:apply')" @click="onApply(record)">流程</a>
              </a-space>
            </template>
          </template>
          <template #emptyText>
            <div class="ct-empty">
              暂无证照数据<span v-if="!metaLoaded">（字典/配置尚未加载成功，可点右上角重载）</span>
            </div>
          </template>
        </a-table>
      </div>
    </div>

    <!-- 详情抽屉：基本信息 / 文件版本 / 验真 / 变更历史 -->
    <a-drawer
      v-model:open="drawerOpen"
      class="bid-ind"
      :width="940"
      :title="detail ? `证照详情 · ${detail.certName}` : '证照详情'"
      destroy-on-close
    >
      <a-spin :spinning="detailLoading">
        <a-tabs v-model:active-key="tab">
          <a-tab-pane key="basic" tab="基本信息">
            <a-descriptions :column="2" size="small" bordered>
              <a-descriptions-item label="证照 ID">
                <span class="ct-id">#{{ detail?.id }}</span>
              </a-descriptions-item>
              <a-descriptions-item label="乐观锁版本">v{{ detail?.version ?? '—' }}</a-descriptions-item>
              <a-descriptions-item label="证照名称">{{ detail?.certName || '—' }}</a-descriptions-item>
              <a-descriptions-item label="证书编号">
                {{ maskNo ? maskCertNo(detail?.certNo) : detail?.certNo || '—' }}
              </a-descriptions-item>
              <a-descriptions-item label="证照大类">{{ dictLabel('cert_type', detail?.certType) }}</a-descriptions-item>
              <a-descriptions-item label="类型代码">{{ detail?.certCategoryCode || '—' }}</a-descriptions-item>
              <a-descriptions-item label="等级">{{ detail?.certLevel || '—' }}</a-descriptions-item>
              <a-descriptions-item label="台账状态">
                <a-tag :color="dictTag('cert_status', detail?.certStatus).color">
                  {{ dictTag('cert_status', detail?.certStatus).label }}
                </a-tag>
              </a-descriptions-item>
              <a-descriptions-item label="持证主体">{{ detail?.holderName || '—' }}</a-descriptions-item>
              <a-descriptions-item label="主体类别">
                {{ detail?.holderType ? dictLabel('holder_type', detail.holderType) : '—' }}
                <span v-if="detail?.holderId" class="ct-id"> · 档案 #{{ detail.holderId }}</span>
              </a-descriptions-item>
              <a-descriptions-item label="颁发机构">{{ detail?.issuingAuthority || '—' }}</a-descriptions-item>
              <a-descriptions-item label="机构代码">{{ detail?.issuingAuthorityCode || '—' }}</a-descriptions-item>
              <a-descriptions-item label="颁发日期">{{ detail?.issueDate || '—' }}</a-descriptions-item>
              <a-descriptions-item label="有效期">
                {{ validityText(detail ?? ({} as CertificateApi.Certificate)) }}
                <a-tag v-if="detail && validityTag(detail)" :color="validityTag(detail)!.color">
                  {{ validityTag(detail)!.label }}
                </a-tag>
              </a-descriptions-item>
              <a-descriptions-item label="电子证照标识">{{ detail?.eCertIdent || '—' }}</a-descriptions-item>
              <a-descriptions-item label="数据来源">
                {{ detail?.sourceChannel ? dictLabel('source_channel', detail.sourceChannel) : '—' }}
              </a-descriptions-item>
              <a-descriptions-item label="许可/业务范围" :span="2">{{ detail?.certScope || '—' }}</a-descriptions-item>
              <a-descriptions-item label="备注" :span="2">{{ detail?.remark || '—' }}</a-descriptions-item>
              <a-descriptions-item label="建档时间">{{ detail?.createTime || '—' }}</a-descriptions-item>
              <a-descriptions-item label="更新时间">{{ detail?.updateTime || '—' }}</a-descriptions-item>
            </a-descriptions>
            <div class="mt-3 flex flex-wrap gap-2">
              <a-button
                v-if="can('bidding:certificate:update')"
                size="small"
                @click="detail && onEdit(detail)"
              >
                编辑证照
              </a-button>
              <a-button
                v-if="can('bidding:certificate:update')"
                size="small"
                @click="detail && onStatus(detail)"
              >
                状态流转
              </a-button>
              <a-button
                v-if="can('bidding:certificate:lifecycle:apply')"
                size="small"
                type="primary"
                @click="detail && onApply(detail)"
              >
                发起变更/续期/注销/年检
              </a-button>
            </div>
          </a-tab-pane>
          <a-tab-pane key="files" tab="文件版本">
            <CertFiles :cert-id="detail?.id ?? null" :can-write="can('bidding:certificate:file')" />
          </a-tab-pane>
          <a-tab-pane key="verify" tab="验真">
            <CertVerify :cert="detail" :can-verify="can('bidding:certificate:verify')" @changed="refreshDetail" />
          </a-tab-pane>
          <a-tab-pane key="timeline" tab="变更历史">
            <CertTimeline biz-domain="CERT" :biz-id="detail?.id ?? null" />
          </a-tab-pane>
        </a-tabs>
      </a-spin>
    </a-drawer>

    <CertForm v-model:open="formOpen" :record="formRecord" @saved="onSaved" />
    <StatusModal v-model:open="statusOpen" :cert="statusRecord" @done="onSaved(statusRecord?.id)" />
    <LifecycleApply v-model:open="applyOpen" :cert="applyRecord" @applied="onSaved" />
  </div>
</template>
