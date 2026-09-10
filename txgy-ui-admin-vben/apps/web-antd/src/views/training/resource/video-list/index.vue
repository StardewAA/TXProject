<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { TrainingResourceApi } from '../api';

import { ref } from 'vue';

import { Page, useVbenModal } from '@vben/common-ui';

import {
  Button,
  InputNumber,
  message,
  Modal,
  Switch,
  Tag,
  TreeSelect,
} from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import { $t } from '#/locales';

import {
  buildVideoPlayUrl,
  changeVideoStatus,
  deleteVideo,
  getResourceCategoryTree,
  getVideoDefinitionOptions,
  getVideoPage,
  markVideoTranscodeSuccess,
  setVideoCreditHours,
} from '../api';
import {
  formatDuration,
  formatFileSize,
  RESOURCE_STATUS_OPTIONS,
  statusColor,
  statusText,
  transcodeColor,
  transcodeText,
} from '../constants';
import VideoFormContent from './video-form.vue';

defineOptions({ name: 'TrainingResourceVideoList' });

// 分类 / 归档筛选
const categoryOptions = ref<any[]>([]);
const categoryId = ref<number | undefined>();
const includeArchived = ref(false);

async function loadCategories() {
  const tree = await getResourceCategoryTree('video');
  categoryOptions.value = tree.map((node) => {
    const children = (node.children || []).map((child) => ({
      title: child.name,
      value: child.id,
    }));
    return {
      title: node.name,
      value: node.id,
      children: children.length ? children : undefined,
    };
  });
}

function handleSearch() {
  gridApi.query();
}

const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: {
    schema: [
      {
        fieldName: 'keyword',
        label: '标题',
        component: 'Input',
        componentProps: {
          allowClear: true,
          placeholder: '请输入视频标题',
        },
      },
      {
        fieldName: 'status',
        label: '状态',
        component: 'Select',
        componentProps: {
          allowClear: true,
          placeholder: '全部状态',
          options: RESOURCE_STATUS_OPTIONS,
        },
      },
    ],
  },
  gridOptions: {
    columns: [
      { field: 'title', title: '标题', minWidth: 220, showOverflow: true },
      { field: 'fileType', title: '格式', width: 80, align: 'center' },
      {
        field: 'fileSize',
        title: '大小',
        width: 110,
        formatter: ({ cellValue }) => formatFileSize(cellValue),
      },
      {
        field: 'durationSeconds',
        title: '时长',
        width: 100,
        formatter: ({ cellValue }) => formatDuration(cellValue),
      },
      { field: 'creditHours', title: '学时(时)', width: 90, align: 'center' },
      {
        field: 'transcodeStatus',
        title: '转码',
        width: 100,
        slots: { default: 'transcode' },
      },
      { field: 'viewCount', title: '播放数', width: 90, align: 'center' },
      {
        field: 'status',
        title: '状态',
        width: 90,
        slots: { default: 'status' },
      },
      { field: 'uploaderName', title: '上传人', width: 110 },
      {
        field: 'createTime',
        title: '上传时间',
        width: 170,
        formatter: 'formatDateTime',
      },
      {
        title: '操作',
        width: 300,
        fixed: 'right',
        slots: { default: 'actions' },
      },
    ],
    height: 'auto',
    keepSource: true,
    proxyConfig: {
      ajax: {
        query: async ({ page }, formValues) => {
          return await getVideoPage({
            pageNo: page.currentPage,
            pageSize: page.pageSize,
            categoryId: categoryId.value,
            includeArchived: includeArchived.value,
            ...formValues,
          });
        },
      },
    },
    rowConfig: { keyField: 'id', isHover: true },
    toolbarConfig: { refresh: true, search: true },
  } as VxeTableGridOptions<TrainingResourceApi.VideoResource>,
});

// ========== 上传 / 编辑弹窗 ==========
const [VideoFormModal, videoFormModal] = useVbenModal({
  connectedComponent: VideoFormContent,
  destroyOnClose: true,
});

function handleUpload() {
  videoFormModal.setData(null).open();
}

function handleEdit(row: TrainingResourceApi.VideoResource) {
  videoFormModal.setData(row).open();
}

// ========== 播放弹窗（支持清晰度切换）==========
const playVisible = ref(false);
const playUrl = ref('');
const playingRow = ref<TrainingResourceApi.VideoResource>();
const definitionOptions = ref<TrainingResourceApi.VideoDefinitionOption[]>([]);
const currentDefinition = ref<string>('');

async function handlePlay(row: TrainingResourceApi.VideoResource) {
  playingRow.value = row;
  playVisible.value = true;
  definitionOptions.value = [];
  try {
    definitionOptions.value = (await getVideoDefinitionOptions(row.id!)) ?? [];
  } catch {
    definitionOptions.value = [];
  }
  const preferred =
    definitionOptions.value.find((item) => item.original) ??
    definitionOptions.value[0];
  currentDefinition.value = preferred?.definition ?? '';
  playUrl.value = preferred?.url ?? buildVideoPlayUrl(row.id!);
}

function switchDefinition(item: TrainingResourceApi.VideoDefinitionOption) {
  currentDefinition.value = item.definition ?? '';
  playUrl.value = item.url ?? '';
}

// ========== 学时配置弹窗 ==========
const creditVisible = ref(false);
const creditRow = ref<TrainingResourceApi.VideoResource>();
const creditInput = ref<number | undefined>(0);

function handleOpenCredit(row: TrainingResourceApi.VideoResource) {
  creditRow.value = row;
  creditInput.value = row.creditHours ?? undefined;
  creditVisible.value = true;
}

async function handleSaveCredit() {
  const value = Number(creditInput.value);
  if (!creditRow.value?.id || !(value > 0)) {
    message.warning('请输入大于 0 的学时值');
    return;
  }
  await setVideoCreditHours(creditRow.value.id, value);
  message.success('学时配置成功');
  creditVisible.value = false;
  handleSearch();
}

// ========== 转码标记 ==========
async function handleMarkTranscode(row: TrainingResourceApi.VideoResource) {
  await markVideoTranscodeSuccess(row.id!);
  message.success('已标记转码成功');
  handleSearch();
}

// ========== 状态 / 删除 ==========
async function handleStatus(row: TrainingResourceApi.VideoResource, action: string, tip: string) {
  await changeVideoStatus(row.id!, action);
  message.success(tip);
  handleSearch();
}

async function handleDelete(row: TrainingResourceApi.VideoResource) {
  await deleteVideo(row.id!);
  message.success($t('ui.actionMessage.deleteSuccess'));
  handleSearch();
}

function buildActions(row: TrainingResourceApi.VideoResource) {
  const actions: any[] = [
    {
      label: '播放',
      type: 'link',
      icon: ACTION_ICON.VIEW,
      onClick: () => handlePlay(row),
    },
    {
      label: '编辑',
      type: 'link',
      icon: ACTION_ICON.EDIT,
      onClick: () => handleEdit(row),
    },
    {
      label: '学时',
      type: 'link',
      onClick: () => handleOpenCredit(row),
    },
  ];
  if (row.transcodeStatus !== 3) {
    actions.push({
      label: '标记转码',
      type: 'link',
      popConfirm: {
        title: '当前无转码引擎，手动标记转码成功？',
        confirm: () => handleMarkTranscode(row),
      },
    });
  }
  const status = row.status;
  if (status !== 4) {
    if (status === 1 || status === 3) {
      actions.push({
        label: '上线',
        type: 'link',
        popConfirm: {
          title: '确认上线该视频？',
          confirm: () => handleStatus(row, 'ONLINE', '视频已上线'),
        },
      });
    }
    if (status === 1 || status === 2) {
      actions.push({
        label: '下架',
        type: 'link',
        danger: true,
        popConfirm: {
          title: '确认下架该视频？',
          confirm: () => handleStatus(row, 'OFFLINE', '视频已下架'),
        },
      });
    }
    actions.push({
      label: '归档',
      type: 'link',
      popConfirm: {
        title: '归档后不在默认列表展示，确认归档？',
        confirm: () => handleStatus(row, 'ARCHIVE', '视频已归档'),
      },
    });
  }
  actions.push({
    label: '删除',
    type: 'link',
    danger: true,
    icon: ACTION_ICON.DELETE,
    popConfirm: {
      title: '被课程引用时无法删除，确认删除？',
      confirm: () => handleDelete(row),
    },
  });
  return actions;
}

loadCategories();
</script>

<template>
  <Page auto-content-height>
    <VideoFormModal @success="handleSearch" />

    <!-- 分类 / 归档 筛选 -->
    <div class="mb-3 flex flex-wrap items-center gap-3">
      <TreeSelect
        v-model:value="categoryId"
        class="!w-56"
        :allow-clear="true"
        tree-default-expand-all
        placeholder="全部视频分类"
        :tree-data="categoryOptions"
        @change="handleSearch"
      />
      <span class="text-sm text-gray-500">包含归档</span>
      <Switch v-model:checked="includeArchived" @change="handleSearch" />
    </div>

    <Grid table-title="视频资源库（MP4/MOV ≤2GB，上传即上线）">
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: $t('ui.actionTitle.create', ['视频']),
              type: 'primary',
              icon: ACTION_ICON.ADD,
              onClick: handleUpload,
            },
          ]"
        />
      </template>

      <template #fileType="{ row }">
        <Tag color="purple">{{ (row.fileType || '').toUpperCase() }}</Tag>
      </template>
      <template #transcode="{ row }">
        <Tag :color="transcodeColor(row.transcodeStatus) || 'default'">
          {{ transcodeText(row.transcodeStatus) }}
        </Tag>
      </template>
      <template #status="{ row }">
        <Tag :color="statusColor(row.status) || 'default'">
          {{ statusText(row.status) }}
        </Tag>
      </template>
      <template #actions="{ row }">
        <TableAction :actions="buildActions(row)" />
      </template>
    </Grid>

    <!-- 播放弹窗 -->
    <Modal
      v-model:open="playVisible"
      :title="`视频播放 - ${playingRow?.title || ''}`"
      :width="820"
      :footer="null"
      destroy-on-close
    >
      <div v-if="definitionOptions.length" class="mb-3 flex flex-wrap items-center gap-2">
        <span class="text-sm text-gray-500">清晰度</span>
        <Button
          v-for="item in definitionOptions"
          :key="item.definition"
          size="small"
          :type="currentDefinition === item.definition ? 'primary' : 'default'"
          @click="switchDefinition(item)"
        >
          {{ item.definition }}
          <span v-if="!item.transcoded" class="text-xs opacity-70">(未转码)</span>
        </Button>
        <span class="ml-2 text-xs text-gray-400">
          网络不稳定时可选择更低清晰度观看（未转码的清晰度暂按原始视频播放）
        </span>
      </div>
      <video
        v-if="playUrl"
        :src="playUrl"
        class="w-full"
        controls
        autoplay
      />
    </Modal>

    <!-- 学时配置弹窗 -->
    <Modal
      v-model:open="creditVisible"
      :title="`配置学时 - ${creditRow?.title || ''}`"
      width="480"
      :ok-text="'确定'"
      :cancel-text="'取消'"
      @ok="handleSaveCredit"
    >
      <div class="flex flex-col gap-2 py-2">
        <span class="text-sm text-gray-600">
          学时值（小时，大于 0，可填小数如 0.5 / 1 / 1.5）
        </span>
        <InputNumber
          v-model:value="creditInput"
          class="w-full"
          :min="0"
          :precision="2"
          placeholder="请输入学时值"
        />
      </div>
    </Modal>
  </Page>
</template>
