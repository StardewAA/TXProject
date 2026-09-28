<script lang="ts" setup>
import { computed, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';
import { IconifyIcon } from '@vben/icons';

import { Button, message, Space, Spin, Table, Tag, Upload } from 'ant-design-vue';

import {
  importSupplierWord,
  type CrmCustomerApi,
} from '#/api/crm/customer';

const emit = defineEmits<{
  success: [];
}>();

const loading = ref(false);
const progress = ref('');
/** 最近一次导入结果 */
const result = ref<CrmCustomerApi.SupplierImportResult | null>(null);
/** 最近一次导入的文件（用户选择「更新已有供应商」时复用） */
const lastFile = ref<File | null>(null);
/** 是否已就「重复供应商」做出选择（选择后不再提示） */
const updatePromptHandled = ref(false);

/** 是否需要询问「是否更新已存在的供应商」 */
const showUpdatePrompt = computed(
  () =>
    !!result.value &&
    !updatePromptHandled.value &&
    (result.value.duplicateCount ?? 0) > 0,
);

const failureColumns = [
  { title: '行号', dataIndex: 'rowNo', width: 70 },
  { title: '供应商名称', dataIndex: 'name', width: 180 },
  { title: '原因', dataIndex: 'reason' },
  { title: '原始内容', dataIndex: 'rawContent', ellipsis: true },
];

function reset() {
  result.value = null;
  progress.value = '';
  lastFile.value = null;
  updatePromptHandled.value = false;
}

const [Modal, modalApi] = useVbenModal({
  onOpenChange(isOpen: boolean) {
    if (!isOpen) {
      reset();
    }
  },
});

defineExpose({
  open: () => modalApi.open(),
});

/** 上传文档：只支持 .docx（.doc 需先另存为 .docx） */
async function handleFile(file: File) {
  if (!(file.name || '').toLowerCase().endsWith('.docx')) {
    message.error('仅支持 .docx 格式的 Word 文档；旧版 .doc 请先用 Word 另存为 .docx');
    return false;
  }
  lastFile.value = file;
  await doImport(false);
  return false;
}

/**
 * 执行导入
 *
 * @param updateExisting true=同时更新已存在的供应商；false=只新增
 */
async function doImport(updateExisting: boolean) {
  if (!lastFile.value) {
    return;
  }
  loading.value = true;
  progress.value = updateExisting
    ? '正在更新已存在的供应商...'
    : '正在解析文档并导入...';
  try {
    const res = await importSupplierWord(lastFile.value, updateExisting);
    result.value = res;
    updatePromptHandled.value = updateExisting;
    if (updateExisting) {
      message.success(
        `处理完成：新增 ${res.successCount ?? 0} 条，更新 ${res.updateCount ?? 0} 条`,
      );
    } else {
      message.success(`导入完成：新增 ${res.successCount ?? 0} 条`);
    }
    if ((res.successCount ?? 0) > 0 || (res.updateCount ?? 0) > 0) {
      emit('success');
    }
  } catch (error: any) {
    message.error(error?.message ?? '导入失败，请检查文档后重试');
  } finally {
    loading.value = false;
    progress.value = '';
  }
}
</script>

<template>
  <Modal
    title="导入供应商（Word）"
    class="w-[760px]"
    :footer="false"
    :fullscreen-button="false"
  >
    <div class="mb-3 text-sm text-gray-500">
      支持 ERP 导出的 .docx 供应商文档：自动识别表格表头（供应商名称 / 联系人 /
      联系电话 / 地址 等），过滤空行、清洗多余空格与无效换行、按名称去重。
      导入**只做新增**，系统中已存在的供应商不会被直接覆盖。
    </div>

    <Spin :spinning="loading" :tip="progress || '正在处理...'">
      <Upload.Dragger
        name="file"
        accept=".docx"
        :multiple="false"
        :show-upload-list="false"
        :before-upload="handleFile"
      >
        <p class="ant-upload-drag-icon">
          <IconifyIcon
            icon="ant-design:file-word-outlined"
            :width="48"
            :height="48"
            class="mx-auto text-gray-400"
          />
        </p>
        <p class="ant-upload-text">点击或拖拽 .docx 供应商文档到此处</p>
        <p class="ant-upload-hint">
          单次导入一个文档；导入完成后会给出「新增 / 重复 / 解析失败」的汇总
        </p>
      </Upload.Dragger>
    </Spin>

    <template v-if="result">
      <!-- 汇总 -->
      <div class="mt-4 grid grid-cols-4 gap-2 text-center">
        <div class="rounded border border-gray-200 py-2">
          <div class="text-lg font-medium">{{ result.totalCount ?? 0 }}</div>
          <div class="text-xs text-gray-500">识别条目</div>
        </div>
        <div class="rounded border border-green-200 bg-green-50 py-2">
          <div class="text-lg font-medium text-green-600">
            {{ result.successCount ?? 0 }}
          </div>
          <div class="text-xs text-gray-500">新增成功</div>
        </div>
        <div class="rounded border border-orange-200 bg-orange-50 py-2">
          <div class="text-lg font-medium text-orange-600">
            {{ result.duplicateCount ?? 0 }}
          </div>
          <div class="text-xs text-gray-500">已存在（未覆盖）</div>
        </div>
        <div class="rounded border border-red-200 bg-red-50 py-2">
          <div class="text-lg font-medium text-red-500">
            {{ result.failCount ?? 0 }}
          </div>
          <div class="text-xs text-gray-500">解析失败</div>
        </div>
      </div>

      <!-- 重复供应商：询问是否更新 -->
      <div
        v-if="showUpdatePrompt"
        class="mt-3 rounded border border-orange-300 bg-orange-50 p-3"
      >
        <div class="text-sm">
          检测到
          <span class="font-medium">{{ result.duplicateCount }}</span>
          个供应商在系统中已存在，是否更新其信息？
        </div>
        <p class="mt-1 text-xs text-gray-500">
          更新只写入文档中有值的字段（联系人 / 电话 / 地址），
          不会覆盖其它已有信息，也不会改变已存在的客户类别。
        </p>
        <Space class="mt-2">
          <Button
            type="primary"
            size="small"
            :loading="loading"
            @click="doImport(true)"
          >
            更新已有供应商
          </Button>
          <Button size="small" @click="updatePromptHandled = true">
            不更新，仅保留新增
          </Button>
        </Space>
      </div>

      <!-- 更新结果 -->
      <div v-if="(result.updateCount ?? 0) > 0 && updatePromptHandled" class="mt-3 text-sm text-green-600">
        <Tag color="green">已更新</Tag>
        {{ result.updateCount }} 条：{{ (result.updateNames ?? []).join('、') }}
      </div>

      <!-- 解析失败明细 -->
      <div v-if="(result.failures?.length ?? 0) > 0" class="mt-4">
        <div class="mb-1 text-sm font-medium text-red-500">
          解析失败明细（{{ result.failures?.length }} 条）
        </div>
        <Table
          :columns="failureColumns as any"
          :data-source="result.failures"
          :pagination="false"
          :scroll="{ y: 160 }"
          size="small"
          row-key="rowNo"
        />
      </div>

      <!-- 已存在明细 -->
      <div v-if="(result.duplicates?.length ?? 0) > 0" class="mt-4">
        <div class="mb-1 text-sm font-medium text-orange-600">
          已存在的供应商（{{ result.duplicates?.length }} 条，未覆盖）
        </div>
        <Table
          :columns="failureColumns as any"
          :data-source="result.duplicates"
          :pagination="false"
          :scroll="{ y: 160 }"
          size="small"
          row-key="rowNo"
        />
      </div>
    </template>
  </Modal>
</template>
