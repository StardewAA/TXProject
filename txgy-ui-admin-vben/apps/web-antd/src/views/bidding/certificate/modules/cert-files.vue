<script setup lang="ts">
/* 证照文件版本（契约 §3.1 登记 / §3.2 列表）
 * · 前置：先经平台通用文件接口（本仓 /bidding/file/upload）拿到 fileId，再登记元数据 → file_id 契约
 * · 上传前校验取配置：cert.file.max.size.mb / cert.file.allow.formats（**不硬编码阈值与格式**）
 * · fileHash 建议传：浏览器算 SHA-256（非安全上下文时自动跳过，不阻断登记）
 * · 旧版本必须保留：本页只追加，不提供删除（契约 §3.1 业务目标） */
import { computed, onMounted, ref, watch } from 'vue';

import { message } from 'ant-design-vue';

import { type CertificateApi, getCertFileList, registerCertFile } from '#/api/bidding/certificate';
import { uploadBiddingFile } from '#/api/bidding/file';

import { useCertMeta } from '../useCertMeta';

const props = defineProps<{
  canWrite?: boolean;
  certId?: null | number;
}>();

const { configNumber, configValue, dictLabel, dictOptions } = useCertMeta();
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
    message.warning('请先选择文件类别（字典 file_category）');
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
  if (n < 1024) return `${n} B`;
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`;
  return `${(n / 1024 / 1024).toFixed(2)} MB`;
}

const columns = [
  { title: '类别', dataIndex: 'fileCategory', key: 'fileCategory', width: 110 },
  { title: '文件名', dataIndex: 'fileName', key: 'fileName', ellipsis: true },
  { title: '版本', dataIndex: 'version', key: 'version', width: 90 },
  { title: '格式', dataIndex: 'fileFormat', key: 'fileFormat', width: 80 },
  { title: '大小', dataIndex: 'fileSize', key: 'fileSize', width: 100 },
  { title: 'SHA-256', dataIndex: 'sha256', key: 'sha256', width: 150 },
  { title: '登记时间', dataIndex: 'createTime', key: 'createTime', width: 165 },
];
</script>

<template>
  <div class="ct-panel">
    <div class="ct-panel-h">
      <span class="ct-panel-t">文件版本</span>
      <span class="ct-panel-meta">旧版本永久保留，当前生效版本以标记识别</span>
      <div style="margin-left: auto; display: flex; gap: 8px; align-items: center">
        <a-select
          v-model:value="fileCategory"
          :options="categories"
          size="small"
          style="width: 150px"
          placeholder="文件类别"
          :disabled="categories.length === 0"
        />
        <a-upload
          v-if="canWrite !== false"
          :accept="accept"
          :before-upload="onPick"
          :disabled="uploading || !certId"
          :show-upload-list="false"
        >
          <a-button size="small" type="primary" :loading="uploading">登记文件版本</a-button>
        </a-upload>
      </div>
    </div>
    <div class="ct-panel-b">
      <div v-if="allowFormats.length || maxMb" class="ct-tip mb-2">
        允许格式：{{ allowFormats.length ? allowFormats.join(' / ') : '（未配置 cert.file.allow.formats）' }}；单文件上限
        {{ maxMb }} MB。阈值取自配置接口 §1.2。
      </div>
      <a-table
        class="ct-table"
        :columns="columns"
        :data-source="rows"
        :loading="loading"
        :pagination="false"
        row-key="id"
        size="small"
        :scroll="{ x: 900 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'fileCategory'">
            <a-tag>{{ dictLabel('file_category', record.fileCategory) }}</a-tag>
          </template>
          <template v-else-if="column.key === 'version'">
            <a-tag :color="record.isCurrent ? 'green' : 'default'">
              v{{ record.version }}{{ record.isCurrent ? ' · 当前' : '' }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'fileSize'">{{ sizeText(record.fileSize) }}</template>
          <template v-else-if="column.key === 'sha256'">
            <span class="ct-id">{{ record.sha256 ? `${record.sha256.slice(0, 12)}…` : '—' }}</span>
          </template>
        </template>
        <template #emptyText>
          <div class="ct-empty">暂无文件版本：先选类别，再点「登记文件版本」上传</div>
        </template>
      </a-table>
    </div>
  </div>
</template>
