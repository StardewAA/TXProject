<script lang="ts" setup>
import type { TrainingResourceApi } from '../api';

import { computed, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import {
  Button,
  Input,
  InputNumber,
  message,
  Select,
  Tag,
  TreeSelect,
  Upload,
} from 'ant-design-vue';

import { $t } from '#/locales';

import {
  getResourceCategoryTree,
  updateVideo,
  uploadVideo,
} from '../api';

defineOptions({ name: 'TrainingResourceVideoForm' });

const emit = defineEmits(['success']);

const editing = ref(false);
const editingId = ref<number>();
const title = ref('');
const categoryId = ref<number | undefined>();
const coverUrl = ref('');
const tags = ref('');
const durationSeconds = ref<number | undefined>();
// 视频类型：1 短视频 2 录播
const videoType = ref<number>(2);
// 原始清晰度：2160P / 1080P / 720P / 480P / 360P
const definition = ref<string>('720P');
const VIDEO_TYPE_OPTIONS = [
  { label: '短视频', value: 1 },
  { label: '录播', value: 2 },
];
const DEFINITION_OPTIONS = [
  { label: '2160P（4K）', value: '2160P' },
  { label: '1080P（高清）', value: '1080P' },
  { label: '720P（标清）', value: '720P' },
  { label: '480P（流畅）', value: '480P' },
  { label: '360P（极速）', value: '360P' },
];
const selectedFile = ref<File | null>(null);
const uploading = ref(false);

const categoryOptions = ref<any[]>([]);

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

const getTitle = computed(() => {
  return editing.value
    ? $t('ui.actionTitle.edit', ['视频'])
    : $t('ui.actionTitle.create', ['视频']);
});

function pickFile(file: File) {
  if (file.size > 4096 * 1024 * 1024) {
    message.error('视频大小不能超过 4GB');
    return false;
  }
  selectedFile.value = file;
  return false; // 阻止自动上传，点击确定后统一提交
}

function clearFile() {
  selectedFile.value = null;
}

async function handleConfirm() {
  if (!title.value.trim()) {
    message.warning('请填写视频标题');
    return;
  }
  if (!editing.value && !selectedFile.value) {
    message.warning('请选择要上传的视频文件（mp4/mov）');
    return;
  }
  uploading.value = true;
  try {
    const base = {
      title: title.value.trim(),
      categoryId: categoryId.value,
      coverUrl: coverUrl.value.trim() || undefined,
      tags: tags.value.trim() || undefined,
      durationSeconds: durationSeconds.value,
      videoType: videoType.value,
      definition: definition.value,
    };
    if (editing.value) {
      await updateVideo({ id: editingId.value, ...base });
      message.success($t('ui.actionMessage.operationSuccess'));
    } else {
      const data = new FormData();
      data.append('file', selectedFile.value!);
      Object.entries(base).forEach(([key, value]) => {
        if (value !== undefined && value !== null && value !== '') {
          data.append(key, String(value));
        }
      });
      await uploadVideo(data);
      message.success('视频上传成功，已上线');
    }
    modalApi.close();
    emit('success');
  } finally {
    uploading.value = false;
  }
}

const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    await handleConfirm();
  },
  async onOpenChange(isOpen: boolean) {
    if (!isOpen) {
      editing.value = false;
      editingId.value = undefined;
      title.value = '';
      categoryId.value = undefined;
      coverUrl.value = '';
      tags.value = '';
      durationSeconds.value = undefined;
      videoType.value = 2;
      definition.value = '720P';
      selectedFile.value = null;
      return;
    }
    loadCategories();
    const data = modalApi.getData<TrainingResourceApi.VideoResource>();
    if (data?.id) {
      editing.value = true;
      editingId.value = data.id;
      title.value = data.title || '';
      categoryId.value = data.categoryId;
      coverUrl.value = data.coverUrl || '';
      tags.value = data.tags || '';
      durationSeconds.value = data.durationSeconds;
      videoType.value = data.videoType ?? 2;
      definition.value = data.definition ?? '720P';
    } else {
      editing.value = false;
    }
  },
});
</script>

<template>
  <Modal :title="getTitle" class="w-1/2">
    <div class="mx-4 flex flex-col gap-4">
      <div>
        <div class="mb-1 text-sm text-gray-600">
          视频标题 <span class="text-red-500">*</span>
        </div>
        <Input
          v-model:value="title"
          :maxlength="200"
          placeholder="请输入视频标题"
        />
      </div>

      <div class="grid grid-cols-2 gap-3">
        <div>
          <div class="mb-1 text-sm text-gray-600">视频类型</div>
          <Select
            v-model:value="videoType"
            class="w-full"
            :options="VIDEO_TYPE_OPTIONS"
            placeholder="请选择视频类型"
          />
        </div>
        <div>
          <div class="mb-1 text-sm text-gray-600">原始清晰度</div>
          <Select
            v-model:value="definition"
            class="w-full"
            :options="DEFINITION_OPTIONS"
            placeholder="请选择原始清晰度"
          />
        </div>
      </div>
      <div class="text-xs text-gray-400">
        系统将按原始清晰度生成可切换的降级清晰度（720P 原始时会提供 480P /
        360P 选项），转码完成后可在播放时选择更低清晰度以适应弱网环境。
      </div>

      <div v-if="!editing">
        <div class="mb-1 text-sm text-gray-600">
          上传视频文件（短视频 / 录播，MP4 ≤4GB）
        </div>
        <Upload
          accept=".mp4,.mov"
          :show-upload-list="false"
          :before-upload="pickFile"
        >
          <Button type="primary" ghost>
            {{ selectedFile ? '重新选择文件' : '选择文件' }}
          </Button>
        </Upload>
        <div v-if="selectedFile" class="mt-2 flex items-center gap-2">
          <Tag color="purple">{{ selectedFile.name }}</Tag>
          <span class="text-xs text-gray-400">
            {{ (selectedFile.size / 1024 / 1024).toFixed(1) }} MB
          </span>
          <Button size="small" type="text" danger @click="clearFile">移除</Button>
        </div>
      </div>

      <div>
        <div class="mb-1 text-sm text-gray-600">所属分类</div>
        <TreeSelect
          v-model:value="categoryId"
          class="w-full"
          :allow-clear="true"
          tree-default-expand-all
          placeholder="请选择视频分类"
          :tree-data="categoryOptions"
        />
      </div>

      <div>
        <div class="mb-1 text-sm text-gray-600">封面图地址</div>
        <Input
          v-model:value="coverUrl"
          :maxlength="500"
          placeholder="选填：封面图片 URL"
        />
      </div>

      <div class="flex gap-4">
        <div class="flex-1">
          <div class="mb-1 text-sm text-gray-600">视频时长（秒）</div>
          <InputNumber
            v-model:value="durationSeconds"
            class="w-full"
            :min="0"
            placeholder="选填，如 1800"
          />
        </div>
      </div>

      <div>
        <div class="mb-1 text-sm text-gray-600">标签</div>
        <Input
          v-model:value="tags"
          :maxlength="200"
          placeholder="多个标签用逗号分隔，如：高处作业,实操示范"
        />
      </div>
    </div>
  </Modal>
</template>
