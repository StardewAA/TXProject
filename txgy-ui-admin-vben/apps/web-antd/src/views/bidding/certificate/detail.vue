<script setup lang="ts">
/* 证照台账 · 详情独立页（设计约束：详情用独立跳转页面，非抽屉）
 * 路由：/bidding/certificate/:id（只读）
 * 承接原抽屉 tabs：基本信息 / 文件版本 / 验真 / 变更历史；动作：编辑证照 → :id/edit、状态流转、发起流程。
 * 顶部「‹ 返回证照台账」= router.back()，回到列表（列表筛选/分页经 URL Query 保留）。 */
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { useAccess } from '@vben/access';
import { useAccessStore } from '@vben/stores';

import { type CertificateApi, getCertificate } from '#/api/bidding/certificate';

import { fmtDate, fmtDateTime } from './format';
import CertFiles from './modules/cert-files.vue';
import CertTimeline from './modules/cert-timeline.vue';
import CertVerify from './modules/cert-verify.vue';
import LifecycleApply from './modules/lifecycle-apply.vue';
import StatusModal from './modules/status-modal.vue';
import { useCertMeta } from './useCertMeta';

import './cert.css';

const route = useRoute();
const router = useRouter();
const certId = computed(() => String(route.params.id ?? ''));
const { dictLabel, dictTag } = useCertMeta();
const { hasAccessByCodes } = useAccess();
const accessStore = useAccessStore();

function can(code: string): boolean {
  const codes = accessStore.accessCodes ?? [];
  if (codes.length === 0) return true;
  return codes.includes('*') || codes.includes('*:*:*') || hasAccessByCodes([code]);
}

const loading = ref(false);
const error = ref('');
const detail = ref<CertificateApi.Certificate | null>(null);
/** 支持 ?tab=files 深链（列表行「文件」按钮直达文件版本页签）；非法值回落 basic */
const tab = ref(String(route.query.tab ?? 'basic'));
const maskNo = ref(false);

const statusOpen = ref(false);
const applyOpen = ref(false);

function maskCertNo(no?: string): string {
  if (!no) return '—';
  if (!maskNo.value || no.length <= 8) return no;
  return `${no.slice(0, 4)}${'*'.repeat(Math.min(6, no.length - 8))}${no.slice(-4)}`;
}
function validityText(r: CertificateApi.Certificate): string {
  if (!r.validFrom && !r.validTo) return '—';
  return `${fmtDate(r.validFrom)} ~ ${r.validTo ? fmtDate(r.validTo) : '长期'}`;
}
function isLongTerm(r: CertificateApi.Certificate): boolean {
  return !r.validTo;
}

async function load(): Promise<void> {
  if (!certId.value) return;
  loading.value = true;
  error.value = '';
  try {
    detail.value = await getCertificate(certId.value);
  } catch (e) {
    error.value = (e instanceof Error ? e.message : String(e)) || '证照详情读取失败';
  } finally {
    loading.value = false;
  }
}
onMounted(load);

function goBack(): void {
  router.back();
}
function goEdit(): void {
  void router.push(`/bidding/certificate/${certId.value}/edit`);
}
function onStatus(): void {
  if (detail.value) statusOpen.value = true;
}
function onApply(): void {
  if (detail.value) applyOpen.value = true;
}
async function refreshDetail(): Promise<void> {
  await load();
}
function onDone(): void {
  void load();
}
</script>

<template>
  <div class="bid-ind ct-page">
    <div class="ct-head">
      <div>
        <h2 class="ct-title">证照详情{{ detail?.certName ? ` · ${detail.certName}` : '' }}</h2>
        <p class="ct-sub">只读台账；编辑、状态流转、发起变更/续期/注销/年检走右侧动作。</p>
      </div>
      <div class="ct-actions">
        <a class="mr-3 text-sm text-blue-600" @click="goBack">‹ 返回证照台账</a>
        <a-button @click="load">刷新</a-button>
        <a-button v-if="can('bidding:certificate:update')" type="primary" @click="goEdit">编辑证照</a-button>
        <a-button v-if="can('bidding:certificate:update')" @click="onStatus">状态流转</a-button>
        <a-button
          v-if="can('bidding:certificate:lifecycle:apply')"
          type="primary"
          @click="onApply"
        >
          发起变更/续期/注销/年检
        </a-button>
      </div>
    </div>

    <a-alert v-if="error" type="error" show-icon class="mb-3" :message="error" />

    <a-spin :spinning="loading">
      <a-tabs v-if="detail" v-model:active-key="tab">
        <a-tab-pane key="basic" tab="基本信息">
          <div class="ct-panel">
            <div class="ct-panel-t">
              基本信息
              <span class="ct-panel-meta">编号掩码 <a-switch v-model:checked="maskNo" size="small" /></span>
            </div>
            <a-descriptions :column="2" size="small" bordered>
              <a-descriptions-item label="证照 ID"><span class="ct-id">#{{ detail.id }}</span></a-descriptions-item>
              <a-descriptions-item label="乐观锁版本">v{{ detail.version ?? '—' }}</a-descriptions-item>
              <a-descriptions-item label="证照名称">{{ detail.certName || '—' }}</a-descriptions-item>
              <a-descriptions-item label="证书编号">
                {{ maskCertNo(detail.certNo) }}
              </a-descriptions-item>
              <a-descriptions-item label="证照大类">{{ dictLabel('cert_type', detail.certType) }}</a-descriptions-item>
              <a-descriptions-item label="类型代码">{{ detail.certCategoryCode || '—' }}</a-descriptions-item>
              <a-descriptions-item label="等级">{{ detail.certLevel || '—' }}</a-descriptions-item>
              <a-descriptions-item label="台账状态">
                <a-tag :color="dictTag('cert_status', detail.certStatus).color">
                  {{ dictTag('cert_status', detail.certStatus).label }}
                </a-tag>
              </a-descriptions-item>
              <a-descriptions-item label="持证主体">{{ detail.holderName || '—' }}</a-descriptions-item>
              <a-descriptions-item label="主体类别">
                {{ detail.holderType ? dictLabel('holder_type', detail.holderType) : '—' }}
                <span v-if="detail.holderId" class="ct-id"> · 档案 #{{ detail.holderId }}</span>
              </a-descriptions-item>
              <a-descriptions-item label="颁发机构">{{ detail.issuingAuthority || '—' }}</a-descriptions-item>
              <a-descriptions-item label="机构代码">{{ detail.issuingAuthorityCode || '—' }}</a-descriptions-item>
              <a-descriptions-item label="颁发日期">{{ detail.issueDate || '—' }}</a-descriptions-item>
              <a-descriptions-item label="有效期">
                {{ validityText(detail) }}
                <a-tag v-if="isLongTerm(detail)" color="green">长期</a-tag>
              </a-descriptions-item>
              <a-descriptions-item label="电子证照标识">{{ detail.eCertIdent || '—' }}</a-descriptions-item>
              <a-descriptions-item label="数据来源">
                {{ detail.sourceChannel ? dictLabel('source_channel', detail.sourceChannel) : '—' }}
              </a-descriptions-item>
              <a-descriptions-item label="验真">
                <a-tag :color="dictTag('verification_status', detail.verificationStatus).color">
                  {{ dictTag('verification_status', detail.verificationStatus).label }}
                </a-tag>
                <div class="ct-panel-meta">{{ detail.lastVerifiedAt ? fmtDateTime(detail.lastVerifiedAt) : '未验真' }}</div>
              </a-descriptions-item>
              <a-descriptions-item label="许可/业务范围" :span="2">{{ detail.certScope || '—' }}</a-descriptions-item>
              <a-descriptions-item label="备注" :span="2">{{ detail.remark || '—' }}</a-descriptions-item>
              <a-descriptions-item label="建档时间">{{ fmtDateTime(detail.createTime) }}</a-descriptions-item>
              <a-descriptions-item label="更新时间">{{ fmtDateTime(detail.updateTime) }}</a-descriptions-item>
            </a-descriptions>
          </div>
        </a-tab-pane>
        <a-tab-pane key="files" tab="文件版本">
          <CertFiles :cert-id="detail.id" :can-write="can('bidding:certificate:file')" />
        </a-tab-pane>
        <a-tab-pane key="verify" tab="验真">
          <CertVerify :cert="detail" :can-verify="can('bidding:certificate:verify')" @changed="refreshDetail" />
        </a-tab-pane>
        <a-tab-pane key="timeline" tab="变更历史">
          <CertTimeline biz-domain="CERT" :biz-id="detail.id" />
        </a-tab-pane>
      </a-tabs>
      <div v-else-if="!loading" class="ct-empty">未取到证照详情</div>
    </a-spin>

    <StatusModal v-model:open="statusOpen" :cert="detail" @done="onDone" />
    <LifecycleApply v-model:open="applyOpen" :cert="detail" @applied="onDone" />
  </div>
</template>