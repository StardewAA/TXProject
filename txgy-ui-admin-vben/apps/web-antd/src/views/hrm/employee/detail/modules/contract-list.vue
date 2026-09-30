<script lang="ts" setup>
import type { HrmEmployeeContractApi } from '#/api/hrm/employee/contract';

import { nextTick, onMounted, ref } from 'vue';

import { useAccess } from '@vben/access';
import { confirm } from '@vben/common-ui';
import { DICT_TYPE } from '@vben/constants';
import { downloadFileFromUrl, getFileNameFromUrl } from '@vben/utils';

import {
  Button,
  Dropdown,
  InputNumber,
  message,
  Modal,
  Select,
  Table,
  Tag,
  Timeline,
} from 'ant-design-vue';
import { getContractTemplateList } from '#/api/hrm/contract/template';

import {
  deleteEmployeeContract,
  exportEmployeeContract,
  generatePendingEmployeeContract,
  getEmployeeContractFileList,
  previewEmployeeContract,
  renewEmployeeContract,
  getEmployeeContractList,
} from '#/api/hrm/employee/contract';
import { DictTag } from '#/components/dict-tag';
import { $t } from '#/locales';
import {
  formatHrmDateTime,
  formatHrmEmployeeContractStatus,
  formatHrmEmployeeContractType,
} from '#/views/hrm/utils/format';

import ContractForm from './contract-form.vue';

/** 合同在线预览（docx → HTML 就地渲染，避免浏览器把 docx 直接下载） */
const previewOpen = ref(false);
const previewLoading = ref(false);
const previewError = ref('');
/** 预览来源说明：签署件 / 模板稿，便于 HR 判断看到的是哪一份 */
const previewSource = ref('');
const docxContainer = ref<HTMLDivElement>();
/**
 * PDF / 图片的 blob: 预览地址
 *
 * 文件服务对任何文件都返回 `Content-Disposition: attachment`，直接用原地址渲染浏览器必然弹下载；
 * 取成 Blob 后用 blob: 地址渲染即可就地显示（blob 不受该响应头影响）。
 */
const previewBlobUrl = ref('');
const previewIsImage = ref(false);
/** 合同文件台账：contractId -> [{fileType, version, fileName, fileUrl}]（生成稿 / 签署件） */
const ledgerFiles = ref<Record<string, any[]>>({});

/** 版本溯源弹窗（B6）：生成稿 V1（模板 X）→ 签署件 V1（签署记录 Y） */
const timelineOpen = ref(false);
const timelineItems = ref<
  Array<{ label: string; time: string; url: string; color: string }>
>([]);

function openTimeline(record: any) {
  const files = ledgerFiles.value[String(record?.id)] ?? [];
  timelineItems.value = files.map((f) => ({
    label:
      `${f.fileType === 2 ? '签署件' : '系统生成稿'} V${f.version ?? 1}` +
      (f.fileType === 2 && f.signRecordId
        ? `（签署记录 #${f.signRecordId}）`
        : f.templateId
          ? `（模板 #${f.templateId}）`
          : '') +
      ` · ${f.fileName ?? ''}`,
    time: String(f.createTime ?? '').replace('T', ' ').slice(0, 19),
    url: f.fileUrl,
    color: f.fileType === 2 ? 'green' : 'blue',
  }));
  timelineOpen.value = true;
}

/** 操作列「更多」菜单：生成待签合同 / 版本溯源 */
function handleMoreAction(record: any, key: string) {
  if (key === 'generate') {
    void handleGenerate(record);
    return;
  }
  if (key === 'timeline') {
    openTimeline(record);
  }
}
/** 正在生成待签合同的合同编号 */
const generatingId = ref<number>();
/** 正在续签的合同编号 */
const renewingId = ref<number>();

/**
 * 合同续签：按原到期日的次日开新周期（沿用原期限），
 * 后端会自动生成待签合同文件与「待签」签署记录，签署件上传后即完成续签闭环。
 */
/** 续签弹窗：可选合同模板 + 续签年限 */
const renewOpen = ref(false);
const renewForm = ref<{ contractId?: number; templateId?: number; termYears?: number }>({});
const templateOptions = ref<Array<{ label: string; value: number }>>([]);

async function loadTemplateOptions() {
  try {
    const list: any[] = (await getContractTemplateList()) ?? [];
    templateOptions.value = list
      .filter((item) => item?.id)
      .map((item) => ({
        label: item.templateName ?? item.name ?? `模板 #${item.id}`,
        value: item.id,
      }));
  } catch {
    templateOptions.value = [];
  }
}

/** 打开续签弹窗：默认沿用原合同模板与期限 */
async function handleRenew(record: any) {
  if (!record?.id) return;
  renewForm.value = {
    contractId: record.id,
    templateId: record.templateId,
    termYears: record.term && record.term > 0 ? record.term : 1,
  };
  await loadTemplateOptions();
  renewOpen.value = true;
}

async function submitRenew() {
  const { contractId, templateId, termYears } = renewForm.value;
  if (!contractId || !templateId) {
    message.warning('请选择续签使用的合同模板');
    return;
  }
  renewingId.value = contractId;
  try {
    await renewEmployeeContract(contractId, termYears, templateId);
    message.success('续签成功：已按所选模板与年限生成新合同与待签记录');
    renewOpen.value = false;
    await Promise.all([getList(), loadLedgerFiles()]);
  } catch {
    message.error('续签失败，请确认模板与用工主体信息已配置');
  } finally {
    renewingId.value = undefined;
  }
}

/** 加载各合同的文件台账（生成稿 / 签署件分类展示的数据源） */
async function loadLedgerFiles() {
  const map: Record<string, any[]> = {};
  await Promise.all(
    (list.value ?? []).map(async (contract: any) => {
      if (!contract?.id) return;
      try {
        map[String(contract.id)] =
          (await getEmployeeContractFileList(contract.id)) ?? [];
      } catch {
        map[String(contract.id)] = [];
      }
    }),
  );
  ledgerFiles.value = map;
}

/** 生成待签合同：渲染归档 + 自动登记待签签署记录 */
async function handleGenerate(record: any) {
  if (!record?.id) return;
  generatingId.value = record.id;
  try {
    await generatePendingEmployeeContract(record.id);
    message.success('已生成待签合同，并自动登记「待签」签署记录');
    await Promise.all([getList(), loadLedgerFiles()]);
  } catch {
    message.error('生成失败，请先确认已配置合同模板与用工主体信息');
  } finally {
    generatingId.value = undefined;
  }
}

/** 合并展示：文件台账（生成稿/签署件）优先，历史 fileUrls 作为兜底 */
function filesOf(record: any) {
  const ledger = ledgerFiles.value[String(record?.id)] ?? [];
  if (ledger.length > 0) {
    return ledger.map((f) => ({
      url: f.fileUrl as string,
      name: (f.fileName as string) || getFileNameFromUrl(f.fileUrl),
      typeLabel: f.fileType === 2 ? '签署件' : '系统生成稿',
      signed: f.fileType === 2,
    }));
  }
  return (record?.fileUrls ?? []).map((url: string) => ({
    url,
    name: getFileNameFromUrl(url),
    typeLabel: '历史归档',
    signed: true,
  }));
}

const props = defineProps<{ employeeId: number }>();
const { hasAccessByCodes } = useAccess();
const loading = ref(false);
const list = ref<HrmEmployeeContractApi.EmployeeContract[]>([]);
const formRef = ref<InstanceType<typeof ContractForm>>();

async function getList() {
  loading.value = true;
  try {
    list.value = await getEmployeeContractList(props.employeeId);
    await loadLedgerFiles();
  } finally {
    loading.value = false;
  }
}

async function handleDelete(id?: number) {
  if (!id) return;
  try {
    await confirm($t('ui.actionMessage.deleteConfirm'));
    await deleteEmployeeContract(id);
    message.success($t('ui.actionMessage.deleteSuccess'));
    await getList();
  } catch {}
}

async function handleExport(id?: number) {
  if (!id) return;
  try {
    await exportEmployeeContract(id);
    message.success('合同文件已生成，请查看浏览器下载');
  } catch {
    message.error('合同导出失败，请先检查是否已配置用工主体信息与合同模板');
  }
}

/**
 * 附件 URL 归一化
 *
 * 此前 `window.open(相对路径)` 在同源部署下可用、跨域部署会打开空白/404（附件打不开）。
 * 这里统一拼成**绝对地址**，既能在新窗口在线预览，也能被下载按钮复用。
 */
function resolveFileUrl(url: string) {
  if (!url) return '';
  if (/^https?:\/\//i.test(url)) return url;
  const base = (import.meta.env.VITE_GLOB_API_URL as string) || '/admin-api';
  const path = url.startsWith('/') ? url : `${base}/${url}`;
  return new URL(path, window.location.origin).href;
}

/**
 * 文件「查看」
 *
 * - PDF / 图片：新窗口就地预览（浏览器原生支持）
 * - Word(docx)：**弹窗内渲染**（浏览器不会就地渲染 docx，直接打开会变成下载）
 */
async function handlePreviewAttachment(url: string) {
  if (!url) return;
  releasePreviewBlob();
  previewOpen.value = true;
  previewLoading.value = true;
  previewError.value = '';
  previewSource.value = '合同文件预览';
  previewIsImage.value = false;
  await nextTick();
  try {
    const res = await fetch(resolveFileUrl(url));
    if (!res.ok) {
      throw new Error(`HTTP ${res.status}`);
    }
    const blob = await res.blob();
    const isPdf = /pdf/i.test(url) || blob.type.includes('pdf');
    const isImage =
      blob.type.startsWith('image/') ||
      /\.(png|jpe?g|gif|webp|bmp)(\?|$)/i.test(url);
    // PDF / 图片：Blob 地址 + 弹窗内渲染（不受 Content-Disposition: attachment 影响）
    if (isPdf || isImage) {
      previewBlobUrl.value = URL.createObjectURL(blob);
      previewIsImage.value = isImage;
      previewLoading.value = false;
      return;
    }
    // 其它（docx 等）：前端渲染成 HTML
    const arrayBuffer = await blob.arrayBuffer();
    await nextTick();
    const el = docxContainer.value;
    if (!el) return;
    el.innerHTML = '';
    const mammoth = (await import('mammoth/mammoth.browser')).default;
    const result = await mammoth.convertToHtml({ arrayBuffer });
    el.innerHTML = `<div class="docx-doc-page">${
      result.value || '<p>文档内容为空</p>'
    }</div>`;
  } catch (error) {
    previewError.value = `文件预览失败：${
      error instanceof Error ? error.message : String(error)
    }`;
  } finally {
    previewLoading.value = false;
  }
}

/** 下载：显式点击才下载，避免“点一下就下载”的体验 */
function handleDownloadAttachment(url: string) {
  if (!url) return;
  downloadFileFromUrl({ source: resolveFileUrl(url), fileName: getFileNameFromUrl(url) });
}

/** 释放上一次预览产生的 blob 地址，避免内存泄漏 */
function releasePreviewBlob() {
  if (previewBlobUrl.value) {
    URL.revokeObjectURL(previewBlobUrl.value);
    previewBlobUrl.value = '';
  }
  previewIsImage.value = false;
}

/**
 * 在线查看合同
 *
 * 口径（QA 修复）：**已签署的合同要看签署件**——签署记录里归档的签署件会合并进合同的 fileUrls，
 * 因此优先预览 fileUrls 里的第一份文件；只有尚未归档签署件（未签/未上传）时，
 * 才退回「按模板 + 员工/公司信息实时渲染」的预览接口，避免给 HR 看一份“没有签字”的合同。
 */
async function handlePreviewContract(record?: HrmEmployeeContractApi.EmployeeContract) {
  const id = record?.id;
  if (!id) return;
  const signedFile =
    Array.isArray(record?.fileUrls) && record.fileUrls.length > 0
      ? record.fileUrls[0]
      : '';
  // 签署件是 PDF/图片时浏览器可直接就地预览，无需转 HTML
  if (signedFile && /\.(pdf|png|jpe?g|gif|webp)(\?|$)/i.test(signedFile)) {
    // 统一走 Blob 渲染（文件服务返回 attachment，直接开 URL 会弹下载）
    await handlePreviewAttachment(signedFile);
    return;
  }
  previewOpen.value = true;
  previewLoading.value = true;
  previewError.value = '';
  releasePreviewBlob();
  previewSource.value = signedFile ? '合同签署件（已签署归档）' : '合同模板预览（未归档签署件）';
  await nextTick();
  try {
    let arrayBuffer: ArrayBuffer;
    if (signedFile) {
      // 归档签署件走文件服务地址，直接取流
      const res = await fetch(resolveFileUrl(signedFile));
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      arrayBuffer = await res.arrayBuffer();
    } else {
      const blob = (await previewEmployeeContract(id)) as unknown as Blob;
      arrayBuffer = await (blob instanceof Blob
        ? blob.arrayBuffer()
        : new Response(blob as any).arrayBuffer());
    }
    await nextTick();
    const el = docxContainer.value;
    if (!el) return;
    el.innerHTML = '';
    // 按需加载 mammoth：避免把 docx 解析库打进主页 chunk（一旦解析库加载失败会连累整个员工档案页）
    const mammoth = (await import('mammoth/mammoth.browser')).default;
    const result = await mammoth.convertToHtml({ arrayBuffer });
    el.innerHTML = `<div class="docx-doc-page">${
      result.value || '<p>文档内容为空</p>'
    }</div>`;
  } catch (error) {
    previewError.value = `合同预览失败：${
      error instanceof Error ? error.message : String(error)
    }（如为渲染配置缺失，请检查用工主体信息与合同模板）`;
    console.error('[contract preview]', error);
  } finally {
    previewLoading.value = false;
  }
}

onMounted(getList);
</script>
<template>
  <div>
    <div
      v-if="hasAccessByCodes(['hrm:employee:update'])"
      class="mb-3 flex justify-end"
    >
      <Button type="primary" @click="formRef?.open(employeeId)">新增</Button>
    </div>
    <Table
      bordered
      size="small"
      :loading="loading"
      :data-source="list"
      :pagination="false"
      :row-key="(r) => r.id"
      :scroll="{ x: 1600 }"
      :columns="[
        { title: '合同编号', dataIndex: 'no', width: 150 },
        {
          title: '合同类型',
          dataIndex: 'type',
          width: 110,
          customRender: ({ record }) =>
            formatHrmEmployeeContractType(record.type),
        },
        {
          title: '开始日期',
          dataIndex: 'startTime',
          width: 120,
          customRender: ({ text }) => formatHrmDateTime(text),
        },
        {
          title: '结束日期',
          dataIndex: 'endTime',
          width: 120,
          customRender: ({ text }) => formatHrmDateTime(text),
        },
        {
          title: '期限',
          key: 'term',
          width: 90,
        },
        {
          title: '合同状态',
          key: 'status',
          width: 110,
        },
        {
          title: '签署状态',
          key: 'signStatus',
          width: 100,
        },
        { title: '签约公司', dataIndex: 'signCompany', width: 150 },
        {
          title: '签订日期',
          dataIndex: 'signTime',
          width: 120,
          customRender: ({ text }) => formatHrmDateTime(text),
        },
        {
          title: '到期提醒',
          key: 'expireRemind',
          width: 100,
        },
        { title: '备注', dataIndex: 'remark', width: 160 },
        { title: '附件', key: 'files', width: 300 },
        { title: '操作', key: 'action', fixed: 'right', width: 210 },
      ]"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'term'">
          {{ record.term != null ? `${record.term} 年` : '-' }}
        </template>
        <template v-else-if="column.key === 'status'">
          {{ formatHrmEmployeeContractStatus(record.status) }}
        </template>
        <template v-else-if="column.key === 'signStatus'">
          <Tag
            v-if="record.signStatus !== null && record.signStatus !== undefined"
            :color="
              record.signStatus === 1
                ? 'success'
                : record.signStatus === 0
                  ? 'processing'
                  : 'default'
            "
          >
            {{ record.signStatusName || '-' }}
          </Tag>
          <span v-else>-</span>
        </template>
        <template v-else-if="column.key === 'expireRemind'">
          <DictTag
            v-if="record.expireRemind != null"
            :type="DICT_TYPE.INFRA_BOOLEAN_STRING"
            :value="record.expireRemind"
          />
          <span v-else>-</span>
        </template>
        <template v-else-if="column.key === 'files'">
          <div v-if="filesOf(record).length" class="flex flex-col items-start">
            <div
              v-for="file in filesOf(record)"
              :key="file.url"
              class="mb-1 flex w-full flex-col items-start gap-0.5"
            >
              <div class="flex max-w-full items-center gap-1">
                <Tag :color="file.signed ? 'green' : 'blue'" class="!mr-0">
                  {{ file.typeLabel }}
                </Tag>
                <span
                  class="truncate text-sm"
                  :title="file.name"
                  style="max-width: 190px"
                >
                  {{ file.name }}
                </span>
              </div>
              <div class="flex items-center gap-2">
                <Button
                  type="link"
                  size="small"
                  class="!h-5 !px-0"
                  @click="handlePreviewAttachment(file.url)"
                >
                  查看
                </Button>
                <Button
                  type="link"
                  size="small"
                  class="!h-5 !px-0"
                  @click="handleDownloadAttachment(file.url)"
                >
                  下载
                </Button>
              </div>
            </div>
          </div>
          <span v-else>-</span>
        </template>
        <template v-else-if="column.key === 'action'">
          <!-- 合同的查看/下载统一放到「附件」列（生成稿 / 签署件 各自可看可下），
               这里只保留流程类动作，避免操作列拥挤 -->
          <Button
            v-if="hasAccessByCodes(['hrm:employee:update'])"
            type="link"
            class="!px-2"
            :loading="renewingId === record.id"
            @click="handleRenew(record)"
          >
            续签
          </Button>
          <Button
            v-if="hasAccessByCodes(['hrm:employee:delete'])"
            danger
            type="link"
            @click="handleDelete(record.id)"
          >
            删除
          </Button>
        </template>
      </template>
    </Table>
    <ContractForm ref="formRef" @success="getList" />

    <!-- 合同在线预览：docx 就地渲染为 HTML，右侧仍可点「导出合同」下载 -->
    <Modal
      v-model:open="previewOpen"
      :footer="null"
      :title="`合同在线预览 · ${previewSource}`"
      width="900px"
    >
      <div v-if="previewLoading" class="py-16 text-center text-muted-foreground">
        正在渲染合同…
      </div>
      <div v-else-if="previewError" class="py-16 text-center text-red-500">
        {{ previewError }}
      </div>
      <img
        v-if="previewBlobUrl && previewIsImage"
        :src="previewBlobUrl"
        class="bg-card mx-auto max-h-[72vh] rounded"
        alt="文件预览"
      />
      <iframe
        v-else-if="previewBlobUrl"
        :src="previewBlobUrl"
        class="bg-card h-[72vh] w-full rounded border-0"
        title="文件预览"
      />
      <div
        v-show="!previewLoading && !previewError && !previewBlobUrl"
        ref="docxContainer"
        class="docx-preview-box bg-card max-h-[72vh] overflow-auto rounded p-4"
      />
    </Modal>

    <!-- 版本溯源（B6）：这份合同用哪版模板生成、哪条签署记录签的，一目了然 -->
    <Modal
      v-model:open="timelineOpen"
      :footer="null"
      title="合同版本溯源"
      width="700px"
    >
      <Timeline v-if="timelineItems.length" class="mt-4">
        <Timeline.Item
          v-for="item in timelineItems"
          :key="item.url"
          :color="item.color"
        >
          <div class="text-sm font-medium">{{ item.label }}</div>
          <div class="text-muted-foreground mt-1 text-xs">{{ item.time }}</div>
          <Button
            type="link"
            size="small"
            class="!h-auto !px-0"
            @click="handlePreviewAttachment(item.url)"
          >
            查看
          </Button>
        </Timeline.Item>
      </Timeline>
      <div v-else class="py-10 text-center text-muted-foreground">
        该合同暂无生成稿/签署件台账记录（可点「生成待签合同」创建）
      </div>
    </Modal>

    <!-- 续签：选择合同模板 + 续签年限 -->
    <Modal
      v-model:open="renewOpen"
      :confirm-loading="!!renewingId"
      title="合同续签"
      width="520px"
      @ok="submitRenew"
    >
      <div class="mb-3">
        <div class="mb-1 text-sm">续签使用的合同模板</div>
        <Select
          v-model:value="renewForm.templateId"
          :options="templateOptions"
          placeholder="请选择合同模板（决定合同类型与正文）"
          style="width: 100%"
        />
      </div>
      <div>
        <div class="mb-1 text-sm">续签年限（年）</div>
        <InputNumber
          v-model:value="renewForm.termYears"
          :max="30"
          :min="1"
          style="width: 100%"
        />
        <div class="text-muted-foreground mt-1 text-xs">
          新合同周期从「原合同到期日次日」开始计算；无固定期限类模板可不设年限（沿用原合同期限）
        </div>
      </div>
    </Modal>
  </div>
</template>
