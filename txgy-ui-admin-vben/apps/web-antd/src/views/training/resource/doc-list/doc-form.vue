<script lang="ts" setup>
import type { TrainingResourceApi } from '../api';

import { computed, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import {
  Button,
  Input,
  InputNumber,
  message,
  Tag,
  TreeSelect,
  Upload,
} from 'ant-design-vue';

import { $t } from '#/locales';

import {
  getResourceCategoryTree,
  updateDocument,
  uploadDocument,
} from '../api';

defineOptions({ name: 'TrainingResourceDocForm' });

const emit = defineEmits(['success']);

const editing = ref(false);
const editingId = ref<number>();
const title = ref('');
const categoryId = ref<number | undefined>();
const bizCategory = ref('');
const tags = ref('');
const estMinutes = ref<number | undefined>();
const selectedFile = ref<File | null>(null);
const uploading = ref(false);

const categoryOptions = ref<any[]>([]);

async function loadCategories() {
  const tree = await getResourceCategoryTree('doc');
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

const getTitle = computed(() => {
  return editing.value
    ? $t('ui.actionTitle.edit', ['文档'])
    : $t('ui.actionTitle.create', ['文档']);
});

function pickFile(file: File) {
  if (file.size > 50 * 1024 * 1024) {
    message.error('文档大小不能超过 50MB');
    return false;
  }
  selectedFile.value = file;
  return false; // 阻止自动上传，由用户点击确认后统一提交
}

function clearFile() {
  selectedFile.value = null;
}

async function handleConfirm() {
  if (!title.value.trim()) {
    message.warning('请填写文档标题');
    return;
  }
  if (!editing.value && !selectedFile.value) {
    message.warning('请选择要上传的文档（pdf/jpg/jpeg/png）');
    return;
  }
  uploading.value = true;
  try {
    const base = {
      title: title.value.trim(),
      categoryId: categoryId.value,
      bizCategory: bizCategory.value.trim() || undefined,
      tags: tags.value.trim() || undefined,
      estMinutes: estMinutes.value,
    };
    if (editing.value) {
      await updateDocument({ id: editingId.value, ...base });
      message.success($t('ui.actionMessage.operationSuccess'));
    } else {
      const data = new FormData();
      data.append('file', selectedFile.value!);
      Object.entries(base).forEach(([key, value]) => {
        if (value !== undefined && value !== null && value !== '') {
          data.append(key, String(value));
        }
      });
      await uploadDocument(data);
      message.success('文档上传成功，已上线');
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
      bizCategory.value = '';
      tags.value = '';
      estMinutes.value = undefined;
      selectedFile.value = null;
      return;
    }
    loadCategories();
    const data = modalApi.getData<TrainingResourceApi.DocumentResource>();
    if (data?.id) {
      editing.value = true;
      editingId.value = data.id;
      title.value = data.title || '';
      categoryId.value = data.categoryId;
      bizCategory.value = data.bizCategory || '';
      tags.value = data.tags || '';
      estMinutes.value = data.estMinutes;
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
          文档标题 <span class="text-red-500">*</span>
        </div>
        <Input
          v-model:value="title"
          :maxlength="200"
          placeholder="请输入文档标题"
        />
      </div>

      <div v-if="!editing">
        <div class="mb-1 text-sm text-gray-600">上传文件（pdf/jpg/jpeg/png ≤50MB）</div>
        <Upload
          accept=".pdf,.jpg,.jpeg,.png"
          :show-upload-list="false"
          :before-upload="pickFile"
        >
          <Button type="primary" ghost>
            {{ selectedFile ? '重新选择文件' : '选择文件' }}
          </Button>
        </Upload>
        <div v-if="selectedFile" class="mt-2 flex items-center gap-2">
          <Tag color="blue">{{ selectedFile.name }}</Tag>
          <span class="text-xs text-gray-400">
            {{ (selectedFile.size / 1024).toFixed(1) }} KB
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
          placeholder="请选择文档分类"
          :tree-data="categoryOptions"
        />
      </div>

      <div>
        <div class="mb-1 text-sm text-gray-600">业务分类（行业新规/国标解读/防腐工艺…）</div>
        <Input
          v-model:value="bizCategory"
          :maxlength="50"
          placeholder="选填，如：国标解读"
        />
      </div>

      <div>
        <div class="mb-1 text-sm text-gray-600">标签</div>
        <Input
          v-model:value="tags"
          :maxlength="200"
          placeholder="多个标签用逗号分隔，如：高处作业,安全规程"
        />
      </div>

      <div>
        <div class="mb-1 text-sm text-gray-600">预估阅读时长（分钟，预留学时换算）</div>
        <InputNumber
          v-model:value="estMinutes"
          class="w-full"
          :min="0"
          placeholder="选填"
        />
      </div>
    </div>
  </Modal>
</template>
