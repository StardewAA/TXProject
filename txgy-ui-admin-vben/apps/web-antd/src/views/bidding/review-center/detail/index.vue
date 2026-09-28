<script setup lang="ts">
/* 平台审查任务详情页 —— 独立路由 /bidding/review-center/detail/:id（hideInMenu）
 * 2026-09-28 整改：原来「详情」是抽屉，与用户要求的"独立详情页 + 分区"不一致。
 *   分区：基本信息 / 关联材料（能取到文件时的预览与下载）/ 审查记录。
 * 文件口径：证照类任务（bizType=CERTIFICATE）按 bizId 取证照文件版本，预览一律 url/fileId → PdfPreview 内联出口。
 * 权限：审查操作仅超管视角可见（bidding:review:approve / reject）；租户只读。 */
import { computed, h, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { useAccess } from '@vben/access';
import { IconifyIcon } from '@vben/icons';
import { useAccessStore } from '@vben/stores';

import { message, Modal } from 'ant-design-vue';

import { type CertificateApi, getCertFileList, getCertificate } from '#/api/bidding/certificate';
import { withBiddingFileToken } from '#/api/bidding/file';
import {
  approveReviewTask,
  getReviewTaskDetail,
  rejectReviewTask,
  type ReviewTaskApi,
} from '#/api/bidding/review-task';

import { fmtDateTime } from '../../certificate/format';
import { useCertMeta } from '../../certificate/useCertMeta';
import { type FileVersionRow } from '../../components/fileVersion';
import FileVersionTable from '../../components/FileVersionTable.vue';
import PdfPreview from '../../vo/PdfPreview.vue';

const route = useRoute();
const router = useRouter();
const { hasAccessByCodes } = useAccess();
const accessStore = useAccessStore();
const { dictLabel, dictTag } = useCertMeta();

const isPlatform = computed(() => {
  const codes = accessStore.accessCodes ?? [];
  if (codes.length === 0) return false;
  return (
    codes.includes('*') ||
    codes.includes('*:*:*') ||
    hasAccessByCodes(['bidding:review:approve']) ||
    hasAccessByCodes(['bidding:review:reject'])
  );
});

const REVIEW_PENDING = '审查任务接口待后端实现（见契约缺口 A1~A4）';
function reviewErrorText(error: unknown): string {
  const e = error as { code?: number; message?: string; msg?: string; response?: { data?: { code?: number }; status?: number }; status?: number };
  const status = e?.status ?? e?.response?.status;
  const code = e?.code ?? e?.response?.data?.code;
  if (status === 404 || code === 404) return REVIEW_PENDING;
  return String(e?.msg ?? e?.message ?? '加载失败');
}

const taskId = computed(() => String(route.params.id ?? ''));
const loading = ref(false);
const notice = ref('');
const detail = ref<null | ReviewTaskApi.Detail>(null);
const tab = ref('basic');

/** 关联对象（证照类任务）：证照基本信息 + 文件版本 */
const relatedCert = ref<CertificateApi.Certificate | null>(null);
const certFiles = ref<CertificateApi.CertFileVersion[]>([]);
const fileRows = computed<FileVersionRow[]>(() =>
  certFiles.value.map((f) => {
    // downloadable=false（病毒未过 / 已作废）时**不能**再按 fileId 预览，否则绕过后端禁用位
    const usable = f.downloadable !== false;
    return {
      fileId: usable ? f.fileId : undefined,
      name: f.fileName || `文件 #${f.fileId}`,
      categoryLabel: f.fileCategoryName || f.fileCategory,
      uploadTime: f.createTime,
      url: usable && f.previewUrl ? withBiddingFileToken(f.previewUrl) : undefined,
      note: f.voided ? '已作废，不可预览/下载' : f.isCurrent ? '当前生效版本' : undefined,
    };
  }),
);

const preview = ref<{ name: string; open: boolean; url: string }>({ name: '', open: false, url: '' });
function openPreview(row: FileVersionRow): void {
  preview.value = { name: row.name, url: row.url ?? '', open: true };
}

function statusTag(value?: string) {
  const tag = dictTag('review_task_status', value);
  const color =
    value === 'PENDING' ? 'orange' : value === 'APPROVED' ? 'green' : value === 'REJECTED' ? 'red' : tag.color;
  return { ...tag, color };
}
function bizTypeLabel(value?: string): string {
  return dictTag('review_biz_type', value).label;
}

async function load(): Promise<void> {
  if (!taskId.value) return;
  loading.value = true;
  notice.value = '';
  try {
    const d = await getReviewTaskDetail(taskId.value);
    detail.value = d;
    // 证照类任务：把关联证照与它的文件版本带出来（审查人要能直接看到文件，而不是只看到编号）
    if (d?.bizType === 'CERTIFICATE' && d.bizId) {
      const [cert, files] = await Promise.all([
        getCertificate(d.bizId).catch(() => null),
        getCertFileList(d.bizId).catch(() => [] as CertificateApi.CertFileVersion[]),
      ]);
      relatedCert.value = cert;
      certFiles.value = files;
    } else {
      relatedCert.value = null;
      certFiles.value = [];
    }
  } catch (error: unknown) {
    detail.value = null;
    notice.value = reviewErrorText(error);
  } finally {
    loading.value = false;
  }
}

function confirmApprove(): void {
  const t = detail.value;
  if (!t) return;
  if (t.version === undefined || t.version === null) {
    message.warning('该任务缺少 version（乐观锁），请刷新后重试');
    return;
  }
  Modal.confirm({
    title: `审查通过 #${t.id}`,
    okText: '通过',
    content: '通过后该证照/资质方可被其他接口引用；请确认已核对企业录入信息。',
    async onOk() {
      try {
        await approveReviewTask({ taskId: t.id, version: t.version as number });
        message.success('已通过审查，结果已回传企业');
        await load();
      } catch (error: unknown) {
        message.error(reviewErrorText(error));
        throw new Error('approve failed');
      }
    },
  });
}
function confirmReject(): void {
  const t = detail.value;
  if (!t) return;
  if (t.version === undefined || t.version === null) {
    message.warning('该任务缺少 version（乐观锁），请刷新后重试');
    return;
  }
  let reason = '';
  Modal.confirm({
    title: `审查驳回 #${t.id}`,
    okText: '驳回',
    okType: 'danger',
    content: () =>
      h('textarea', {
        class: 'ant-input',
        placeholder: '驳回原因（必填，将展示给提交企业）',
        rows: 3,
        style: 'width:100%',
        onInput: (event: { target: { value: string } }) => {
          reason = event.target.value;
        },
      }),
    async onOk() {
      if (!reason.trim()) {
        message.warning('请填写驳回原因');
        throw new Error('reason required');
      }
      try {
        await rejectReviewTask({ taskId: t.id, version: t.version as number, reason: reason.trim() });
        message.success('已驳回，原因已留痕并展示给企业');
        await load();
      } catch (error: unknown) {
        message.error(reviewErrorText(error));
        throw new Error('reject failed');
      }
    },
  });
}

function goBack(): void {
  // 用浏览器历史返回，避免硬编码菜单路径（菜单 path 随部署而变，写死会跳 404）
  if (window.history.length > 1) router.back();
  else void router.push('/');
}
onMounted(load);
</script>

<template>
  <div class="bid-ind ct-page">
    <div class="ct-head">
      <div>
        <h2 class="ct-title">审查任务详情 · #{{ taskId }}</h2>
        <p class="ct-sub">基本信息 / 关联材料 / 审查记录 三个分区；审查通过或驳回均留痕并对企业可见。</p>
      </div>
      <div class="ct-actions">
        <a-button @click="goBack">
          <IconifyIcon icon="lucide:arrow-left" class="mr-1 inline-block align-middle" />
          返回平台审查中心
        </a-button>
        <a-button :loading="loading" @click="load">刷新</a-button>
      </div>
    </div>

    <a-alert v-if="notice" :message="notice" type="warning" show-icon class="mb-3" />

    <a-spin :spinning="loading">
      <a-tabs v-model:active-key="tab">
        <a-tab-pane key="basic" tab="基本信息">
          <div class="ct-panel">
            <div class="ct-panel-h">
              <span class="ct-panel-t">任务信息</span>
              <span class="ct-panel-meta">{{ isPlatform ? '超管审查视角' : '租户只读视角' }}</span>
            </div>
            <div class="ct-panel-b">
              <a-descriptions :column="3" size="small" bordered>
                <a-descriptions-item label="任务ID">{{ detail?.id ?? taskId }}</a-descriptions-item>
                <a-descriptions-item label="任务状态">
                  <a-tag :color="statusTag(detail?.status).color">{{ statusTag(detail?.status).label }}</a-tag>
                </a-descriptions-item>
                <a-descriptions-item label="业务类型">{{ bizTypeLabel(detail?.bizType) }}</a-descriptions-item>
                <a-descriptions-item label="提交企业">{{ detail?.entName || '—' }}</a-descriptions-item>
                <a-descriptions-item label="统一社会信用代码">{{ detail?.entUscc || '—' }}</a-descriptions-item>
                <a-descriptions-item label="关联编号">{{ detail?.refNo || '—' }}</a-descriptions-item>
                <a-descriptions-item label="提交时间">
                  {{ detail?.submitTime ? fmtDateTime(detail.submitTime) : '—' }}
                </a-descriptions-item>
                <a-descriptions-item label="录入备注" :span="2">{{ detail?.remark || '—' }}</a-descriptions-item>
              </a-descriptions>
              <div class="ct-tip">
                企业录入证照/资质后自动生成审查任务；通过后该对象方可被其他业务引用，驳回原因会原样回传企业。
              </div>
            </div>
          </div>
        </a-tab-pane>

        <a-tab-pane key="files" tab="关联材料">
          <div class="ct-panel">
            <div class="ct-panel-h">
              <span class="ct-panel-t">关联对象</span>
              <span class="ct-panel-meta">审查对象与其文件版本（可预览 / 下载）</span>
            </div>
            <div class="ct-panel-b">
              <template v-if="relatedCert">
                <a-descriptions :column="3" size="small" bordered class="mb-3">
                  <a-descriptions-item label="证照名称">{{ relatedCert.certName || '—' }}</a-descriptions-item>
                  <a-descriptions-item label="证照编号">{{ relatedCert.certNo || '—' }}</a-descriptions-item>
                  <a-descriptions-item label="持证人">{{ relatedCert.holderName || '—' }}</a-descriptions-item>
                  <a-descriptions-item label="有效期">
                    {{ relatedCert.validFrom || '—' }} ~ {{ relatedCert.validTo || '长期' }}
                  </a-descriptions-item>
                  <a-descriptions-item label="台账状态">{{ relatedCert.certStatusName || dictLabel('cert_status', relatedCert.certStatus) }}</a-descriptions-item>
                  <a-descriptions-item label="验真状态">{{ relatedCert.verificationStatusName || '—' }}</a-descriptions-item>
                </a-descriptions>
                <FileVersionTable
                  :rows="fileRows"
                  empty-text="该证照尚未登记文件版本：请在证照台账上传后重新提交审查"
                  @preview="openPreview"
                />
              </template>
              <div v-else class="ct-empty">
                {{
                  detail?.bizType === 'CERTIFICATE'
                    ? '未取到关联证照或该任务尚无关联文件；可在证照台账查看原件'
                    : '该业务类型暂未开放文件清单接口，请在对应台账（企业资质 / 员工证书）中查看原件'
                }}
              </div>
            </div>
          </div>
        </a-tab-pane>

        <a-tab-pane key="review" tab="审查记录">
          <div class="ct-panel">
            <div class="ct-panel-h">
              <span class="ct-panel-t">审查留痕</span>
              <span class="ct-panel-meta">谁在什么时候批的、为什么驳回</span>
              <div v-if="isPlatform && detail?.status === 'PENDING'" style="margin-left: auto; display: flex; gap: 8px">
                <a-button type="primary" @click="confirmApprove">审查通过</a-button>
                <a-button danger @click="confirmReject">审查驳回</a-button>
              </div>
            </div>
            <div class="ct-panel-b">
              <a-descriptions :column="2" size="small" bordered>
                <a-descriptions-item label="当前状态">
                  <a-tag :color="statusTag(detail?.status).color">{{ statusTag(detail?.status).label }}</a-tag>
                </a-descriptions-item>
                <a-descriptions-item label="提交时间">
                  {{ detail?.submitTime ? fmtDateTime(detail.submitTime) : '—' }}
                </a-descriptions-item>
                <a-descriptions-item label="审查人">{{ detail?.reviewer || '—' }}</a-descriptions-item>
                <a-descriptions-item label="审查时间">
                  {{ detail?.reviewTime ? fmtDateTime(detail.reviewTime) : '—' }}
                </a-descriptions-item>
                <a-descriptions-item label="驳回原因" :span="2">{{ detail?.reason || '—' }}</a-descriptions-item>
              </a-descriptions>
              <div v-if="detail?.status === 'APPROVED'" class="ct-tip">已通过：该证照/资质可被其他接口引用。</div>
              <div v-else-if="detail?.status === 'REJECTED'" class="ct-tip">
                已驳回：请按驳回原因整改后重新提交（将生成新的审查任务）。
              </div>
              <div v-else-if="detail?.status === 'PENDING' && !isPlatform" class="ct-tip">待平台审查中；审查结果会在此展示。</div>
            </div>
          </div>
        </a-tab-pane>
      </a-tabs>
    </a-spin>

    <PdfPreview
      v-model:open="preview.open"
      :url="preview.url"
      :file-name="preview.name"
      :title="preview.name || '文件预览'"
    />
  </div>
</template>
