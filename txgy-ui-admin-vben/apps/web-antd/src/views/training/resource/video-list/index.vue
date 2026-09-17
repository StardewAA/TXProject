<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { TrainingResourceApi } from '../api';

import { onMounted, onUnmounted, ref } from 'vue';

import { Page, useVbenModal } from '@vben/common-ui';

import {
  Button,
  InputNumber,
  message,
  Modal,
  Progress,
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
  getTranscodeCapability,
  getVideoDefinitionOptions,
  getVideoPage,
  getVideoTranscodeTasks,
  markVideoTranscodeSuccess,
  retryVideoTranscode,
  setVideoCreditHours,
} from '../api';
import {
  formatDuration,
  formatFileSize,
  RESOURCE_STATUS_OPTIONS,
  statusColor,
  statusText,
  transcodeColor,
  transcodeTaskColor,
  transcodeTaskText,
  transcodeText,
} from '../constants';
import VideoFormContent from './video-form.vue';

defineOptions({ name: 'TrainingResourceVideoList' });

// 分类 / 归档筛选
const categoryOptions = ref<any[]>([]);
const categoryId = ref<number | undefined>();
const includeArchived = ref(false);

function buildCategoryTree(nodes?: TrainingResourceApi.CategoryNode[]): any[] | undefined {
  if (!nodes?.length) {
    return undefined;
  }
  return nodes.map((node) => ({
    title: node.name,
    value: node.id,
    children: buildCategoryTree(node.children),
  }));
}

async function loadCategories() {
  const tree = await getResourceCategoryTree('video');
  categoryOptions.value = buildCategoryTree(tree) || [];
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
        width: 380,
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

// ========== 转码任务进度（异步上传后轮询）==========
const tasksVisible = ref(false);
const taskRow = ref<TrainingResourceApi.VideoResource>();
const taskList = ref<TrainingResourceApi.VideoTranscodeTask[]>([]);
const capability = ref<TrainingResourceApi.TranscodeCapability>();
let pollTimer: ReturnType<typeof setInterval> | undefined;

async function loadTasks(videoId: number) {
  try {
    taskList.value = (await getVideoTranscodeTasks(videoId)) ?? [];
  } catch {
    taskList.value = [];
  }
}

async function handleOpenTasks(row: TrainingResourceApi.VideoResource) {
  taskRow.value = row;
  tasksVisible.value = true;
  await loadTasks(row.id!);
}

async function handleRetryTranscode(row: TrainingResourceApi.VideoResource) {
  await retryVideoTranscode(row.id!);
  message.success('已重新提交转码任务');
  handleSearch();
  startPolling();
}

/** 异步转码期间轮询列表，全部进入终态后自动停止 */
function startPolling() {
  let times = 0;
  stopPolling();
  pollTimer = setInterval(async () => {
    times += 1;
    await gridApi.query();
    const rows =
      ((gridApi as any)?.grid?.getData?.() as
        | TrainingResourceApi.VideoResource[]
        | undefined) ?? [];
    const running = rows.some(
      (item) => item.transcodeStatus === 1 || item.transcodeStatus === 2,
    );
    if (!running || times >= 120) {
      stopPolling();
    }
  }, 5000);
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer);
    pollTimer = undefined;
  }
}

function handleUploadSuccess() {
  handleSearch();
  startPolling();
}

onMounted(() => {
  getTranscodeCapability()
    .then((res) => {
      capability.value = res;
    })
    .catch(() => undefined);
});
onUnmounted(stopPolling);

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
  actions.push({
    label: '转码详情',
    type: 'link',
    onClick: () => handleOpenTasks(row),
  });
  if (row.transcodeStatus !== 3) {
    actions.push({
      label: '重新转码',
      type: 'link',
      popConfirm: {
        title: '重新生成 360P / 480P / 720P 转码任务？',
        confirm: () => handleRetryTranscode(row),
      },
    });
  }
  if (row.transcodeStatus === 0 || row.transcodeStatus === 4) {
    actions.push({
      label: '标记转码',
      type: 'link',
      popConfirm: {
        title: '转码引擎不可用时，手动标记转码成功（仅占位）？',
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
    <VideoFormModal @success="handleUploadSuccess" />

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

    <Grid
      :table-title="`视频资源库（MP4/MOV ≤4GB，异步上传 + 自动转码 360P/480P/720P${capability?.ffmpegAvailable ? '' : '（当前环境未检测到 ffmpeg）'}）`"
    >
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
        <div class="flex flex-col gap-1">
          <Tag :color="transcodeColor(row.transcodeStatus) || 'default'">
            {{ transcodeText(row.transcodeStatus) }}
            <span v-if="row.transcodeStatus === 2">…</span>
          </Tag>
          <Button
            v-if="row.transcodeStatus === 1 || row.transcodeStatus === 2"
            size="small"
            type="link"
            class="!px-0 !text-xs"
            @click="handleOpenTasks(row)"
          >
            查看进度
          </Button>
        </div>
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

    <!-- 转码任务详情 -->
    <Modal
      v-model:open="tasksVisible"
      :title="`转码任务 - ${taskRow?.title || ''}`"
      width="640"
      :footer="null"
      destroy-on-close
    >
      <div
        v-if="capability && !capability.ffmpegAvailable"
        class="mb-3 rounded bg-yellow-50 px-3 py-2 text-xs text-yellow-700 dark:bg-yellow-950 dark:text-yellow-300"
      >
        当前运行环境未检测到 ffmpeg，无法执行转码；视频仍按原始清晰度播放。
      </div>
      <div v-if="!taskList.length" class="py-6 text-center text-sm text-gray-400">
        暂无转码任务（源清晰度不高于 360P 时无需生成转码任务）
      </div>
      <div v-else class="flex flex-col gap-3">
        <div
          v-for="item in taskList"
          :key="item.id"
          class="rounded border p-3"
        >
          <div class="mb-2 flex items-center justify-between">
            <div class="flex items-center gap-2">
              <Tag color="blue">{{ item.definition }}</Tag>
              <Tag :color="transcodeTaskColor(item.status) || 'default'">
                {{ transcodeTaskText(item.status) }}
              </Tag>
            </div>
            <span class="text-xs text-gray-400">
              {{ item.fileSize ? formatFileSize(item.fileSize) : '' }}
            </span>
          </div>
          <Progress
            :percent="item.progress ?? 0"
            size="small"
            :status="item.status === 3 ? 'exception' : undefined"
          />
          <div v-if="item.errorMsg" class="mt-2 text-xs text-red-500">
            {{ item.errorMsg }}
          </div>
        </div>
        <div class="flex justify-end gap-2">
          <Button size="small" @click="loadTasks(taskRow?.id ?? 0)">
            刷新
          </Button>
          <Button
            size="small"
            type="primary"
            @click="taskRow && handleRetryTranscode(taskRow)"
          >
            重新转码
          </Button>
        </div>
      </div>
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
