<script lang="ts" setup>
import type { TrainingResourceApi } from '../../resource/api';

import { computed, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { Input, message, Select, Table, Tag } from 'ant-design-vue';

import {
  buildDocumentPreviewUrl,
  buildVideoPlayUrl,
  getDocumentPage,
  getVideoPage,
} from '../../resource/api';

defineOptions({ name: 'TrainingCourseResourcePicker' });

/** 已选资源（与课程媒资字段一一对应） */
export interface PickedResource {
  key: string;
  /** 媒资类型：1 文档 2 视频 */
  mediaType: number;
  name: string;
  url: string;
  /** 外部资源引用 ID（资源中心媒资 ID） */
  mediaRefId?: string;
  sizeBytes?: number;
  durationSeconds?: number;
}

/** 打开选择器时由课程表单透传的数据 */
interface PickerData {
  /** 当前要选择的资源类型：1 文档 / 2 视频 */
  mediaType: number;
  /** 已选中的资源（用于回显勾选态） */
  selected: PickedResource[];
  /** 确认回调 */
  onConfirm: (list: PickedResource[]) => void;
}

const STATUS_OPTIONS = [
  { label: '全部状态', value: '' },
  { label: '上线', value: 2 },
  { label: '草稿', value: 1 },
  { label: '下架', value: 3 },
  { label: '归档', value: 4 },
];

const STATUS_TEXT: Record<number, string> = {
  1: '草稿',
  2: '上线',
  3: '下架',
  4: '归档',
};

const STATUS_COLOR: Record<number, string> = {
  1: 'default',
  2: 'success',
  3: 'warning',
  4: 'default',
};

const mediaType = ref<number>(1);
const keyword = ref('');
const status = ref<number | ''>(2);
const loading = ref(false);
const rows = ref<any[]>([]);
const total = ref(0);
const selectedKeys = ref<number[]>([]);
const selectedRows = ref<any[]>([]);
let confirmCallback: ((list: PickedResource[]) => void) | undefined;

const isDoc = computed(() => mediaType.value === 1);

const columns = computed(() =>
  isDoc.value
    ? [
        { title: '文档名称', dataIndex: 'title', key: 'title', minWidth: 220, ellipsis: true },
        { title: '文件类型', dataIndex: 'fileType', key: 'fileType', width: 90 },
        { title: '页数', dataIndex: 'pageCount', key: 'pageCount', width: 70, align: 'center' },
        { title: '预估时长', dataIndex: 'estMinutes', key: 'estMinutes', width: 90, align: 'center' },
        { title: '状态', dataIndex: 'status', key: 'status', width: 80, align: 'center' },
      ]
    : [
        { title: '视频名称', dataIndex: 'title', key: 'title', minWidth: 220, ellipsis: true },
        { title: '类型', dataIndex: 'videoType', key: 'videoType', width: 90 },
        { title: '时长(秒)', dataIndex: 'durationSeconds', key: 'durationSeconds', width: 90, align: 'center' },
        { title: '清晰度', dataIndex: 'definition', key: 'definition', width: 90 },
        { title: '状态', dataIndex: 'status', key: 'status', width: 80, align: 'center' },
      ],
);

const getTitle = computed(() =>
  isDoc.value ? '选择文档资源（模块一：资源中心）' : '选择视频资源（模块一：资源中心）',
);

async function loadList() {
  loading.value = true;
  try {
    const params: Record<string, any> = {
      pageNo: 1,
      pageSize: 200,
      title: keyword.value || undefined,
      status: status.value === '' ? undefined : status.value,
    };
    const page = isDoc.value
      ? await getDocumentPage(params)
      : await getVideoPage(params);
    rows.value = page.list ?? [];
    total.value = page.total ?? rows.value.length;
  } finally {
    loading.value = false;
  }
}

/** 切换资源类型：清空勾选，按当前类型已选资源回填 */
function syncSelectedKeys(selected: PickedResource[]) {
  const current = selected.filter((item) => item.mediaType === mediaType.value);
  selectedKeys.value = current.map((item) => Number(item.mediaRefId)).filter((id) => !Number.isNaN(id));
  selectedRows.value = rows.value.filter((row) => selectedKeys.value.includes(row.id));
}

function handleRowSelectionChange(keys: (number | string)[]) {
  selectedKeys.value = keys.map((key) => Number(key));
  selectedRows.value = rows.value.filter((row) => selectedKeys.value.includes(row.id));
}

/** 跨页勾选时保留已选行：合并历史勾选（不在当前页的行保持选中） */
function handleSelect(record: any, selected: boolean) {
  if (selected) {
    if (!selectedRows.value.some((row) => row.id === record.id)) {
      selectedRows.value = [...selectedRows.value, record];
      selectedKeys.value = [...selectedKeys.value, record.id];
    }
  } else {
    selectedRows.value = selectedRows.value.filter((row) => row.id !== record.id);
    selectedKeys.value = selectedKeys.value.filter((key) => key !== record.id);
  }
}

function handleSelectAll(_selected: boolean, _rows: any[], changeRows: any[]) {
  // 同页全选/取消：逐行合并，保证跨页勾选不被覆盖
  changeRows.forEach((row) => {
    const picked = _selected;
    handleSelect(row, picked);
  });
}

function toPicked(row: any): PickedResource {
  const isDocRow = isDoc.value;
  return {
    key: `${isDocRow ? 1 : 2}-${row.id}`,
    mediaType: isDocRow ? 1 : 2,
    name: row.title || row.fileName || `资源 ${row.id}`,
    url: isDocRow ? buildDocumentPreviewUrl(row.id) : buildVideoPlayUrl(row.id),
    mediaRefId: String(row.id),
    sizeBytes: row.fileSize,
    durationSeconds: isDocRow ? undefined : row.durationSeconds,
  };
}

function handleConfirm() {
  if (selectedRows.value.length === 0) {
    message.warning('请至少勾选一条资源');
    return;
  }
  confirmCallback?.(selectedRows.value.map((row) => toPicked(row)));
  modalApi.close();
}

const [Modal, modalApi] = useVbenModal({
  onConfirm: handleConfirm,
  async onOpenChange(isOpen: boolean) {
    if (!isOpen) {
      keyword.value = '';
      rows.value = [];
      selectedKeys.value = [];
      selectedRows.value = [];
      confirmCallback = undefined;
      return;
    }
    const data = modalApi.getData<PickerData>();
    if (!data) {
      return;
    }
    mediaType.value = data.mediaType;
    status.value = 2;
    confirmCallback = data.onConfirm;
    await loadList();
    syncSelectedKeys(data.selected || []);
  },
});

/** 切换文档/视频页签 */
async function handleMediaTypeChange(value: number) {
  mediaType.value = value;
  selectedKeys.value = [];
  selectedRows.value = [];
  await loadList();
}
</script>

<template>
  <Modal :title="getTitle" class="w-[960px]">
    <div class="mx-4 flex flex-col gap-3">
      <div class="flex items-center gap-2">
        <Select
          :value="mediaType"
          class="!w-32"
          :options="[
            { label: '文档资源', value: 1 },
            { label: '视频资源', value: 2 },
          ]"
          @change="handleMediaTypeChange as any"
        />
        <Input
          v-model:value="keyword"
          class="!w-64"
          allow-clear
          placeholder="按名称搜索"
          @press-enter="loadList"
        />
        <Select v-model:value="status" class="!w-32" :options="STATUS_OPTIONS" />
        <a class="text-blue-500" @click="loadList">查询</a>
      </div>

      <Table
        row-key="id"
        size="small"
        bordered
        :columns="columns"
        :data-source="rows"
        :loading="loading"
        :pagination="{ pageSize: 10, total, showSizeChanger: false }"
        :row-selection="{
          selectedRowKeys: selectedKeys,
          onChange: handleRowSelectionChange,
          onSelect: handleSelect,
          onSelectAll: handleSelectAll,
        }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'status'">
            <Tag :color="STATUS_COLOR[record.status] || 'default'">
              {{ STATUS_TEXT[record.status] || '-' }}
            </Tag>
          </template>
          <template v-if="column.key === 'videoType'">
            {{ record.videoType === 1 ? '短视频' : '录播' }}
          </template>
          <template v-if="column.key === 'pageCount'">
            {{ record.pageCount ?? '-' }}
          </template>
          <template v-if="column.key === 'estMinutes'">
            {{ record.estMinutes != null ? `${record.estMinutes} 分钟` : '-' }}
          </template>
        </template>
      </Table>

      <div class="text-xs text-gray-400">
        已勾选 {{ selectedRows.length }} 条；支持跨页勾选，可同时为课程绑定多个文档与多个视频。
      </div>
    </div>
  </Modal>
</template>
