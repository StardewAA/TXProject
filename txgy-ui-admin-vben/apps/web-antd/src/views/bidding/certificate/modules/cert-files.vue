<script setup lang="ts">
/* 证照文件版本（契约 §3.1 登记 / §3.2 列表）
 * · 前置：先经平台通用文件接口（本仓 /bidding/file/upload）拿到 fileId，再登记元数据 → file_id 契约
 * · 上传前校验取配置：cert.file.max.size.mb / cert.file.allow.formats（**不硬编码阈值与格式**）
 * · fileHash 建议传：浏览器算 SHA-256（非安全上下文时自动跳过，不阻断登记）
 * · 旧版本必须保留：本页只追加，不提供删除（契约 §3.1 业务目标）
 * · 加注件（file_category=COPY）必填 4 个法定字段：copyCreationTime/copyCreator/copyCause/copyExpiringTime
 *   （契约 §3.1④）；其中两个时间为 LocalDateTime → 必须发 **epoch 毫秒**（后端统一序列化口径）。 */
import { computed, onMounted, reactive, ref, watch } from 'vue';

import { IconifyIcon } from '@vben/icons';

import { message, Modal } from 'ant-design-vue';

import {
  type CertificateApi,
  correctCertFile,
  getCertFileList,
  registerCertFile,
  voidCertFile,
} from '#/api/bidding/certificate';
import { buildBiddingFileDownloadUrl, uploadBiddingFile, withBiddingFileToken } from '#/api/bidding/file';
import { fmtBytes } from '#/utils/format';

import CaSignEntry, { type CaSignFile } from '../../ca/CaSignEntry.vue';
import PdfPreview from '../../vo/PdfPreview.vue';
import { dateTimeToMs, fmtDateTime } from '../format';
import { useCertMeta } from '../useCertMeta';

const props = defineProps<{
  canWrite?: boolean;
  certId?: null | number;
}>();

const { configNumber, configValue, dictLabel, dictOptions } = useCertMeta();
/** 加注件法定元数据（仅 file_category=COPY 时必填） */
const copyForm = ref<{
  copyCause?: string;
  copyCreationTime?: string;
  copyCreator?: string;
  copyExpiringTime?: string;
}>({ copyCause: undefined, copyCreationTime: undefined, copyCreator: undefined, copyExpiringTime: undefined });
const isCopy = computed(() => fileCategory.value === 'COPY');
/** 加注件有效期上限（配置 cert.annotation.max.days），并给出提示天数（cert.annotation.warn.days） */
const annotationMaxDays = computed(() => configNumber('cert.annotation.max.days', 0));
const annotationWarnDays = computed(() => configNumber('cert.annotation.warn.days', 0));
const copyExpiringDisabledDate = (d: any) => {
  if (!copyForm.value.copyCreationTime || annotationMaxDays.value <= 0) return false;
  const max = new Date(copyForm.value.copyCreationTime).getTime() + annotationMaxDays.value * 86_400_000;
  return d && d.valueOf() > max;
};
const loading = ref(false);
const uploading = ref(false);
const rows = ref<CertificateApi.CertFileVersion[]>([]);
const fileCategory = ref<string | undefined>(undefined);

const categories = computed(() => dictOptions('file_category'));
const maxMb = computed(() => configNumber('cert.file.max.size.mb', 50));
const allowFormats = computed(() => {
  const v = configValue<unknown>('cert.file.allow.formats', []);
  return Array.isArray(v) ? v.map((x) => String(x).toUpperCase()) : [];
});
const accept = computed(() => allowFormats.value.map((f) => `.${f.toLowerCase()}`).join(','));

async function load() {
  if (!props.certId) {
    rows.value = [];
    return;
  }
  loading.value = true;
  try {
    rows.value = await getCertFileList(props.certId);
  } catch {
    rows.value = [];
  } finally {
    loading.value = false;
  }
}
onMounted(load);
watch(() => props.certId, load);

/** 浏览器侧 SHA-256（crypto.subtle 仅安全上下文可用；不可用时返回空，不影响登记） */
async function sha256Of(file: File): Promise<string> {
  try {
    if (!globalThis.crypto?.subtle) return '';
    const buf = await file.arrayBuffer();
    const digest = await globalThis.crypto.subtle.digest('SHA-256', buf);
    return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, '0')).join('');
  } catch {
    return '';
  }
}

/** a-upload 的 before-upload：校验后走平台上传 + 版本登记，统一返回 false 由本页表格呈现 */
async function onPick(file: File) {
  if (!props.certId) {
    message.warning('请先选择证照');
    return false;
  }
  if (!fileCategory.value) {
    message.warning('请先选择文件类别（原件 / 加注件 / 变更页 / 延续页 / 电子证照）');
    return false;
  }
  if (allowFormats.value.length > 0) {
    const ext = (file.name.split('.').pop() ?? '').toUpperCase();
    if (!allowFormats.value.includes(ext)) {
      message.warning(`仅允许 ${allowFormats.value.join(' / ')}（配置 cert.file.allow.formats）`);
      return false;
    }
  }
  if (file.size > maxMb.value * 1024 * 1024) {
    message.warning(`单文件不得超过 ${maxMb.value} MB（配置 cert.file.max.size.mb）`);
    return false;
  }
  // §3.1④ 加注件 4 法定字段前置校验（缺一不可，避免后端登记出不合规数据）
  if (isCopy.value) {
    if (!copyForm.value.copyCreator?.trim()) {
      message.warning('加注件须填写加注人（copyCreator）');
      return false;
    }
    if (!copyForm.value.copyCause?.trim()) {
      message.warning('加注件须填写加注事由（copyCause）');
      return false;
    }
    if (!copyForm.value.copyCreationTime) {
      message.warning('加注件须填写加注时间（copyCreationTime）');
      return false;
    }
  }
  uploading.value = true;
  try {
    const uploaded = await uploadBiddingFile(file, 'bidding/certificate');
    const hash = await sha256Of(file);
    await registerCertFile({
      certId: props.certId,
      fileCategory: fileCategory.value,
      fileName: file.name,
      fileId: uploaded.fileId,
      fileHash: hash || undefined,
      fileFormat: (file.name.split('.').pop() ?? '').toUpperCase() || undefined,
      fileSize: file.size,
      // 加注件法定字段：时间转 epoch 毫秒（后端 LocalDateTime 入参口径）
      copyCreator: isCopy.value ? copyForm.value.copyCreator?.trim() : undefined,
      copyCause: isCopy.value ? copyForm.value.copyCause?.trim() : undefined,
      copyCreationTime: isCopy.value ? dateTimeToMs(copyForm.value.copyCreationTime) : undefined,
      copyExpiringTime: isCopy.value ? dateTimeToMs(copyForm.value.copyExpiringTime) : undefined,
    });
    message.success('文件版本已登记');
    await load();
  } catch {
    // 上传/登记失败的具体原因由请求层 toast 后端 msg（含 fileId 缺失的「文件注册失败」）
  } finally {
    uploading.value = false;
  }
  return false;
}

function sizeText(n?: number) {
  if (!n && n !== 0) return '—';
  return fmtBytes(n);
}

const columns = [
  { title: '类别', dataIndex: 'fileCategory', key: 'fileCategory', width: 110 },
  { title: '文件名', dataIndex: 'fileName', key: 'fileName', ellipsis: true },
  { title: '版本', dataIndex: 'version', key: 'version', width: 90 },
  { title: '格式', dataIndex: 'fileFormat', key: 'fileFormat', width: 80 },
  { title: '大小', dataIndex: 'fileSize', key: 'fileSize', width: 100 },
  { title: 'SHA-256', dataIndex: 'fileHash', key: 'sha256', width: 150 },
  { title: '登记时间', dataIndex: 'createTime', key: 'createTime', width: 165 },
  { title: '加注人', dataIndex: 'copyCreator', key: 'copyCreator', width: 110 },
  { title: '加注事由', dataIndex: 'copyCause', key: 'copyCause', ellipsis: true },
  { title: '加注时间', dataIndex: 'copyCreationTime', key: 'copyCreationTime', width: 155 },
  { title: '加注有效期至', dataIndex: 'copyExpiringTime', key: 'copyExpiringTime', width: 155 },
  // 预览 / 下载：契约《后端变更通知-电子证照域字典治理》§4.2/§六-4 —— downloadable 决定能否点
  { title: '预览', key: 'preview', width: 90, fixed: 'right' },
  { title: '下载', key: 'download', width: 90, fixed: 'right' },
  { title: '操作', key: 'action', width: 160, fixed: 'right' },
];

/* ===== 预览 / 下载（用户第 2 条：文件入口必须显眼）=====
 * 预览**一律走 PdfPreview（fileId → 业务受控出口 /bidding/file/download 内联）**，
 * 不再把签名下载地址塞 iframe / 新窗口 —— 那会变成"点了变下载"（用户 2026-09-28 口径）。
 * 下载命中同一出口；浏览器原生跳转不带 Authorization 头，故补 ?token=（见 withBiddingFileToken）。 */
const previewOpen = ref(false);
const previewRow = ref<CertificateApi.CertFileVersion | null>(null);

function openPreview(row: CertificateApi.CertFileVersion): void {
  previewRow.value = row;
  previewOpen.value = true;
}
/** 可预览/下载：downloadable=false（病毒未过 / 已作废）时禁用，不要点了才报错 */
function canOpen(row: CertificateApi.CertFileVersion): boolean {
  return row.downloadable === true && !!row.fileId;
}
function download(row: CertificateApi.CertFileVersion): void {
  if (!canOpen(row)) return;
  const a = document.createElement('a');
  a.href = withBiddingFileToken(buildBiddingFileDownloadUrl(row.fileId));
  a.download = row.fileName || `文件-${row.id}`;
  a.rel = 'noopener';
  document.body.append(a);
  a.click();
  a.remove();
}
/** 行内「更多」分发：低频动作（更正 / 作废）收进下拉，避免操作列再来一排按钮 */
function onRowAction(info: { key: number | string }, row: CertificateApi.CertFileVersion): void {
  if (info.key === 'correct') openCorrect(row);
  else if (info.key === 'void') confirmVoid(row);
}

/** 不可预览的原因（后端 downloadable=false：病毒未过 或 已作废）—— 用于禁用态悬浮提示 */
function notPreviewableReason(r: CertificateApi.CertFileVersion): string {
  if (r.voided) return '已作废，不可预览' + (r.voidReason ? '：' + r.voidReason : '');
  const v = r.virusStatus ?? r.virusScanStatus;
  if (v === 'INFECTED') return '病毒扫描未通过，禁止预览/下载';
  if (v === 'PENDING' || v === 'UNKNOWN') return '病毒扫描未完成，暂不可预览';
  return '暂不可预览';
}

/* ===== F2 作废/更正（后端变更通知-给前端.md §F2：作废=软删留痕；更正=备注 + 加注件 copy_* 四要素） ===== */
const voidOpen = ref(false);
const voidRow = ref<CertificateApi.CertFileVersion | null>(null);
const voidReason = ref('');
const voiding = ref(false);
function openVoid(row: CertificateApi.CertFileVersion): void {
  voidRow.value = row;
  voidReason.value = '';
  voidOpen.value = true;
}
async function doVoid(): Promise<void> {
  if (!voidRow.value) return;
  if (!voidReason.value.trim()) {
    message.warning('作废原因必填（作废是留痕操作，须写明原因）');
    return;
  }
  voiding.value = true;
  try {
    await voidCertFile({ fileVersionId: voidRow.value.id, reason: voidReason.value.trim() });
    message.success('文件版本已作废');
    voidOpen.value = false;
    await load();
  } catch {
    // 失败原因由请求层 toast 后端 msg
  } finally {
    voiding.value = false;
  }
}

const correctOpen = ref(false);
const correctRow = ref<CertificateApi.CertFileVersion | null>(null);
const correcting = ref(false);
const correctForm = reactive<{
  copyCause?: string;
  copyCreationTime?: string;
  copyCreator?: string;
  copyExpiringTime?: string;
  remark?: string;
}>({});
function openCorrect(row: CertificateApi.CertFileVersion): void {
  correctRow.value = row;
  correctForm.remark = row.remark ?? '';
  correctForm.copyCause = row.copyCause ?? '';
  correctForm.copyCreator = row.copyCreator ?? '';
  correctForm.copyCreationTime = row.copyCreationTime ? fmtDateTime(row.copyCreationTime) : undefined;
  correctForm.copyExpiringTime = row.copyExpiringTime ? fmtDateTime(row.copyExpiringTime) : undefined;
  correctOpen.value = true;
}
async function doCorrect(): Promise<void> {
  if (!correctRow.value) return;
  const isCopy = correctRow.value.fileCategory === 'COPY';
  const patch: CertificateApi.CertFileCorrectReq = {
    fileVersionId: correctRow.value.id,
    remark: correctForm.remark?.trim() || undefined,
    copyCreator: isCopy ? correctForm.copyCreator?.trim() || undefined : undefined,
    copyCause: isCopy ? correctForm.copyCause?.trim() || undefined : undefined,
    copyCreationTime: isCopy && correctForm.copyCreationTime ? dateTimeToMs(correctForm.copyCreationTime) : undefined,
    copyExpiringTime: isCopy && correctForm.copyExpiringTime ? dateTimeToMs(correctForm.copyExpiringTime) : undefined,
  };
  if (!patch.remark && !patch.copyCreator && !patch.copyCause && !patch.copyCreationTime && !patch.copyExpiringTime) {
    message.warning('至少填写一个可更正字段（备注或加注件要素）');
    return;
  }
  correcting.value = true;
  try {
    await correctCertFile(patch);
    message.success('元数据已更正');
    correctOpen.value = false;
    await load();
  } catch {
    // 失败原因由请求层 toast 后端 msg
  } finally {
    correcting.value = false;
  }
}
/** 文件版本 → 待签名文件（fileHash 即 SHA-256 摘要占位；url 无则走手动摘要扫码签名） */
function signFileOf(row: CertificateApi.CertFileVersion): CaSignFile {
  return { fileName: row.fileName, fileId: row.fileId, sha256: row.fileHash, size: row.fileSize };
}
function onSigned(rec: { digestHex?: string; signatureValue?: string; }): void {
  if (rec.signatureValue) message.success('CA 签名完成：' + (rec.digestHex?.slice(0, 12) ?? '') + '…');
}
function confirmVoid(row: CertificateApi.CertFileVersion): void {
  Modal.confirm({
    title: '确认作废该文件版本？',
    content: `「${row.fileName || row.fileHash || row.id}」将被软删留痕（版本链保留）；若为当前版本，该类别将失去当前版本，需重新登记。`,
    okText: '作废',
    okType: 'danger',
    cancelText: '取消',
    onOk: () => openVoid(row),
  });
}
</script>

<template>
  <div class="ct-panel">
    <div class="ct-panel-h">
      <span class="ct-panel-t">文件版本</span>
      <span class="ct-panel-meta">旧版本永久保留，当前生效版本以标记识别；每行可预览与下载</span>
      <!-- 文件入口必须显眼（用户第 2 条）：主按钮「上传文件」常驻面板右侧，不再是小号文字按钮 -->
      <div style="margin-left: auto; display: flex; gap: 8px; align-items: center">
        <a-select
          v-model:value="fileCategory"
          :options="categories"
          style="width: 170px"
          placeholder="先选文件类别"
          :disabled="categories.length === 0"
        />
        <a-upload
          v-if="canWrite !== false"
          :accept="accept"
          :before-upload="onPick"
          :disabled="uploading || !certId"
          :show-upload-list="false"
        >
          <a-button type="primary" :loading="uploading" :disabled="!certId">
            {{ uploading ? '上传中…' : '上传文件' }}
          </a-button>
        </a-upload>
      </div>
    </div>
    <div class="ct-panel-b">
      <div v-if="allowFormats.length || maxMb" class="ct-tip mb-2">
        允许格式：{{ allowFormats.length ? allowFormats.join(' / ') : '（未配置 cert.file.allow.formats）' }}；单文件上限
        {{ maxMb }} MB。阈值取自配置接口 §1.2。文件格式由系统自动校验，文件类别（原件 / 加注件 / 变更页 / 延续页 / 电子证照）需手动选择后再上传。
      </div>

      <!-- §3.1④ 加注件（file_category=COPY）法定元数据：必填，否则拒绝登记 -->
      <a-alert
        v-if="isCopy"
        class="mb-2"
        type="warning"
        show-icon
        message="已选择「加注件」：加注人、加注事由、加注时间必填（法定要素）"
        :description="annotationMaxDays > 0
          ? `加注有效期不得超过加注时间 + ${annotationMaxDays} 天（配置 cert.annotation.max.days）${annotationWarnDays > 0 ? `；剩余 ${annotationWarnDays} 天时开始提醒` : ''}`
          : '未取到 cert.annotation.max.days 配置，加注有效期不做上限校验'"
      />
      <a-form v-if="isCopy" layout="horizontal">
      <div class="ct-sheet mb-3">
        <div class="ct-sheet-group">加注件法定要素<span class="ct-sg-note">加注人 / 事由 / 时间，必填</span></div>
        <div class="ct-sheet-row">
          <div class="ct-sheet-cell">
            <a-form-item label="加注人">
              <a-input v-model:value="copyForm.copyCreator" placeholder="加注人姓名/机构" />
            </a-form-item>
          </div>
          <div class="ct-sheet-cell">
            <a-form-item label="加注事由">
              <a-input v-model:value="copyForm.copyCause" placeholder="如：遗失补办、信息变更" />
            </a-form-item>
          </div>
          <div class="ct-sheet-cell">
            <a-form-item label="加注时间">
              <a-date-picker
                v-model:value="copyForm.copyCreationTime"
                class="w-full"
                show-time
                value-format="YYYY-MM-DD HH:mm:ss"
                placeholder="选择加注时间"
              />
            </a-form-item>
          </div>
        </div>
        <div class="ct-sheet-row">
          <div class="ct-sheet-cell ct-sheet-wide">
            <a-form-item label="加注有效期至">
              <a-date-picker
                v-model:value="copyForm.copyExpiringTime"
                class="w-full"
                show-time
                value-format="YYYY-MM-DD HH:mm:ss"
                :disabled-date="copyExpiringDisabledDate"
                placeholder="可空"
              />
            </a-form-item>
          </div>
        </div>
      </div>
      </a-form>
      <a-table
        class="ct-table"
        :columns="columns"
        :data-source="rows"
        :loading="loading"
        :pagination="false"
        row-key="id"
        size="small"
        :scroll="{ x: 1080 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'fileCategory'">
            <!-- 后端已下发中文名（字典治理 §4.2），优先直接用；字典不可用时回退字典/原值 -->
            <a-tag>{{ record.fileCategoryName || dictLabel('file_category', record.fileCategory) }}</a-tag>
          </template>
          <template v-else-if="column.key === 'version'">
            <a-tag :color="record.isCurrent ? 'green' : 'default'">
              v{{ record.version }}{{ record.isCurrent ? ' · 当前' : '' }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'fileSize'">{{ sizeText(record.fileSize) }}</template>
          <template v-else-if="column.key === 'createTime'">{{ fmtDateTime(record.createTime) }}</template>
          <template v-else-if="column.key === 'copyCreationTime'">
            {{ fmtDateTime(record.copyCreationTime) }}
          </template>
          <template v-else-if="column.key === 'copyExpiringTime'">
            {{ fmtDateTime(record.copyExpiringTime) }}
          </template>
          <template v-else-if="column.key === 'copyCause'">{{ record.copyCause || '—' }}</template>
          <template v-else-if="column.key === 'copyCreator'">{{ record.copyCreator || '—' }}</template>
          <template v-else-if="column.key === 'sha256'">
            <span class="ct-id">{{ record.fileHash ? `${record.fileHash.slice(0, 12)}…` : '—' }}</span>
          </template>
          <template v-else-if="column.key === 'preview'">
            <!-- 内联预览：fileId → PdfPreview（业务受控出口 /bidding/file/download，非签名下载地址） -->
            <a v-if="canOpen(record)" @click="openPreview(record)">预览</a>
            <a-tooltip v-else :title="notPreviewableReason(record)">
              <span class="ct-disabled">预览</span>
            </a-tooltip>
          </template>
          <template v-else-if="column.key === 'download'">
            <a v-if="canOpen(record)" @click="download(record)">下载</a>
            <a-tooltip v-else :title="notPreviewableReason(record)">
              <span class="ct-disabled">下载</span>
            </a-tooltip>
          </template>
          <template v-else-if="column.key === 'action'">
            <!-- 操作列收敛：CA 签名留明面，低频的更正 / 作废进「更多」（用户第 3 条） -->
            <CaSignEntry :file="signFileOf(record)" biz-type="CERT" :biz-code="record.fileName" @signed="onSigned" />
            <template v-if="canWrite !== false">
              <a-dropdown>
                <a class="ml-2" @click.prevent>
                  更多
                  <IconifyIcon icon="lucide:chevron-down" class="inline-block align-middle" />
                </a>
                <template #overlay>
                  <a-menu @click="(info: { key: number | string }) => onRowAction(info, record)">
                    <a-menu-item key="correct">更正</a-menu-item>
                    <a-menu-item key="void" danger>作废</a-menu-item>
                  </a-menu>
                </template>
              </a-dropdown>
            </template>
            <span v-else class="ml-2 text-xs text-muted-foreground">只读</span>
          </template>
        </template>
        <template #emptyText>
          <div class="ct-empty">暂无文件版本：先在右上选择文件类别，再点「上传文件」</div>
        </template>
      </a-table>
    </div>

    <!-- 内联预览（fileId → 业务受控出口）；不用签名下载地址塞 iframe，避免"点了变下载" -->
    <PdfPreview
      v-model:open="previewOpen"
      :file-id="previewRow?.fileId"
      :file-name="previewRow?.fileName"
      :title="previewRow ? `文件预览 · ${previewRow.fileName}` : '文件预览'"
    />

    <!-- F2 作废：软删留痕，作废原因必填（后端变更通知-给前端.md §F2） -->
    <a-modal
      v-model:open="voidOpen"
      title="作废文件版本"
      :confirm-loading="voiding"
      ok-text="作废"
      ok-type="danger"
      cancel-text="取消"
      @ok="doVoid"
    >
      <p class="mb-2 text-sm text-muted-foreground">
        作废为软删留痕操作（版本链保留）；若为当前版本，该类别将失去当前版本，需重新登记。
      </p>
      <a-textarea v-model:value="voidReason" :rows="3" placeholder="作废原因（必填，留痕用）" />
    </a-modal>

    <!-- F2 更正：只允许更正展示/留痕类元数据（备注 + 加注件 copy_* 四要素；至少传一个） -->
    <a-modal
      v-model:open="correctOpen"
      title="更正文件版本元数据"
      :confirm-loading="correcting"
      ok-text="保存更正"
      cancel-text="取消"
      @ok="doCorrect"
    >
      <a-form layout="vertical">
        <a-form-item label="备注">
          <a-textarea v-model:value="correctForm.remark" :rows="2" placeholder="展示/留痕类备注（可空：不填则不改）" />
        </a-form-item>
        <template v-if="correctRow?.fileCategory === 'COPY'">
          <div class="ct-tip mb-2">加注件（COPY）可更正 4 个法定要素（GB/T 36903 5.4）；只填要改的字段。</div>
          <a-form-item label="加注人">
            <a-input v-model:value="correctForm.copyCreator" placeholder="加注人姓名/机构" />
          </a-form-item>
          <a-form-item label="加注事由">
            <a-input v-model:value="correctForm.copyCause" placeholder="如：遗失补办、信息变更" />
          </a-form-item>
          <a-form-item label="加注时间">
            <a-date-picker
              v-model:value="correctForm.copyCreationTime"
              class="w-full"
              show-time
              value-format="YYYY-MM-DD HH:mm:ss"
              placeholder="选择加注时间"
            />
          </a-form-item>
          <a-form-item label="加注有效期至">
            <a-date-picker
              v-model:value="correctForm.copyExpiringTime"
              class="w-full"
              show-time
              value-format="YYYY-MM-DD HH:mm:ss"
              placeholder="可空"
            />
          </a-form-item>
        </template>
      </a-form>
    </a-modal>
  </div>
</template>
