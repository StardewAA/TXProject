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
  countDocumentPages,
  getResourceCategoryTree,
  updateDocument,
  uploadDocuments,
} from '../api';
import { DOC_BIZ_CATEGORY_OPTIONS } from '../constants';

defineOptions({ name: 'TrainingResourceDocForm' });

const emit = defineEmits(['success']);

const editing = ref(false);
const editingId = ref<number>();
const title = ref('');
const categoryId = ref<number | undefined>();
const bizCategory = ref('');
const estMinutes = ref<number | undefined>();
/** 支持一次性选择多个文件，逐个上传 */
const selectedFiles = ref<File[]>([]);
const uploading = ref(false);
/** 自动识别的文档总页数 */
const pageCount = ref<number | undefined>();
/** 页数是否精确识别 */
const pageCountPrecise = ref(true);
const counting = ref(false);

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
  const tree = await getResourceCategoryTree('doc');
  categoryOptions.value = buildCategoryTree(tree) || [];
}

const getTitle = computed(() => {
  return editing.value
    ? $t('ui.actionTitle.edit', ['文档'])
    : $t('ui.actionTitle.create', ['文档']);
});

/** 页数识别请求序号，避免连续选文件时旧响应覆盖新结果 */
let detectToken = 0;

/** 自动检索文档页数，并按「总页数 × 10 分钟/页」自动设置预估阅读时长 */
async function detectPageCount(file: File) {
  const token = ++detectToken;
  counting.value = true;
  try {
    const res = await countDocumentPages(file);
    if (token !== detectToken) {
      return;
    }
    pageCount.value = res.pageCount;
    pageCountPrecise.value = res.precise !== false;
    estMinutes.value = res.estMinutes;
    message.success(
      `已识别文档页数：${res.pageCount} 页，预估阅读时长 ${res.estMinutes} 分钟`,
    );
  } catch {
    if (token !== detectToken) {
      return;
    }
    pageCount.value = undefined;
    message.warning('文档页数识别失败，请手动填写预估阅读时长');
  } finally {
    if (token === detectToken) {
      counting.value = false;
    }
  }
}

/**
 * 选择文件（支持多选）：before-upload 的每个 file 都会带上本次批量选择的 fileList，
 * 取整个 fileList 累加去重，返回 false 阻止自动上传，由用户点击确定后统一提交。
 */
function pickFiles(file: File, fileList: File[]) {
  const list = fileList?.length ? fileList : [file];
  const oversize = list.filter((item) => item.size > 50 * 1024 * 1024);
  if (oversize.length) {
    message.error(`「${oversize[0]!.name}」超过 50MB，单个文档大小不能超过 50MB`);
  }
  const validList = list.filter((item) => item.size <= 50 * 1024 * 1024);
  if (!validList.length) {
    return false;
  }
  // 去重后合并到已选列表
  const merged = [...selectedFiles.value];
  validList.forEach((item) => {
    if (!merged.some((exist) => exist.name === item.name && exist.size === item.size)) {
      merged.push(item);
    }
  });
  selectedFiles.value = merged;
  pageCount.value = undefined;
  pageCountPrecise.value = true;
  estMinutes.value = undefined;
  // 仅单个文件时即时识别页数，多文件由后端按每个文档分别计算
  if (selectedFiles.value.length === 1) {
    detectPageCount(selectedFiles.value[0]!);
  }
  return false;
}

function removeFile(index: number) {
  selectedFiles.value = selectedFiles.value.filter((_, i) => i !== index);
  pageCount.value = undefined;
  pageCountPrecise.value = true;
  estMinutes.value = undefined;
  if (selectedFiles.value.length === 1) {
    detectPageCount(selectedFiles.value[0]!);
  }
}

function clearFile() {
  selectedFiles.value = [];
  pageCount.value = undefined;
  pageCountPrecise.value = true;
  estMinutes.value = undefined;
}

function formatSize(bytes: number) {
  if (bytes < 1024) {
    return `${bytes} B`;
  }
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`;
  }
  return `${(bytes / 1024 / 1024).toFixed(2)} MB`;
}

async function handleConfirm() {
  if (!title.value.trim()) {
    message.warning('请填写文档标题');
    return;
  }
  if (!editing.value && !selectedFiles.value.length) {
    message.warning(
      '请选择要上传的文档（pdf / doc / docx / wps / jpg / png），支持一次选择多个',
    );
    return;
  }
  uploading.value = true;
  try {
    const base = {
      title: title.value.trim(),
      categoryId: categoryId.value,
      bizCategory: bizCategory.value.trim() || undefined,
      estMinutes: estMinutes.value,
    };
    if (editing.value) {
      await updateDocument({ id: editingId.value, ...base });
      message.success($t('ui.actionMessage.operationSuccess'));
    } else {
      const data = new FormData();
      selectedFiles.value.forEach((item) => {
        data.append('files', item);
      });
      Object.entries(base).forEach(([key, value]) => {
        if (value !== undefined && value !== null && value !== '') {
          data.append(key, String(value));
        }
      });
      const ids = (await uploadDocuments(data)) ?? [];
      message.success(
        selectedFiles.value.length > 1
          ? `${ids.length} 个文档上传成功，已上线（标题自动追加序号区分）`
          : '文档上传成功，已上线',
      );
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
      estMinutes.value = undefined;
      selectedFiles.value = [];
      pageCount.value = undefined;
      pageCountPrecise.value = true;
      counting.value = false;
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
      estMinutes.value = data.estMinutes;
      pageCount.value = data.pageCount;
      pageCountPrecise.value = true;
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
        <div class="mb-1 text-sm text-gray-600">
          上传文件（支持一次选择多个，PDF / Word(doc,docx) / WPS(wps) / 图片
          单个 ≤50MB）
        </div>
        <Upload
          accept=".pdf,.doc,.docx,.wps,.jpg,.jpeg,.png"
          :multiple="true"
          :show-upload-list="false"
          :before-upload="pickFiles"
        >
          <Button type="primary" ghost>
            {{ selectedFiles.length ? '继续添加文件' : '选择文件' }}
          </Button>
        </Upload>
        <div v-if="selectedFiles.length" class="mt-2 flex flex-col gap-2">
          <div
            v-for="(item, index) in selectedFiles"
            :key="`${item.name}-${item.size}`"
            class="flex items-center gap-2"
          >
            <Tag color="blue">{{ item.name }}</Tag>
            <span class="text-xs text-gray-400">
              {{ formatSize(item.size) }}
            </span>
            <Button size="small" type="text" danger @click="removeFile(index)">
              移除
            </Button>
          </div>
          <div class="flex items-center gap-2">
            <span class="text-xs text-gray-500">
              已选择 {{ selectedFiles.length }} 个文件
            </span>
            <Button size="small" type="text" danger @click="clearFile">
              清空
            </Button>
          </div>
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
        <div class="mb-1 text-sm text-gray-600">业务分类</div>
        <Select
          v-model:value="bizCategory"
          class="w-full"
          :allow-clear="true"
          :options="DOC_BIZ_CATEGORY_OPTIONS"
          placeholder="请选择业务分类"
        />
      </div>

      <div>
        <div class="mb-1 text-sm text-gray-600">
          预估阅读时长（分钟）
          <span class="ml-1 text-xs text-gray-400">
            自动按「总页数 × 10 分钟/页」计算
          </span>
        </div>
        <InputNumber
          v-model:value="estMinutes"
          class="w-full"
          :min="0"
          :disabled="!editing && !!pageCount"
          :placeholder="editing ? '选填' : '选择文件后自动计算'"
        />
        <div v-if="counting" class="mt-1 text-xs text-gray-400">
          正在识别文档页数…
        </div>
        <div v-else-if="pageCount" class="mt-1 text-xs text-gray-400">
          已识别 {{ pageCount }} 页{{ pageCountPrecise ? '' : '（按内容估算）' }}，
          预估阅读时长 = {{ pageCount }} × 10 =
          {{ pageCount * 10 }} 分钟
        </div>
        <div v-else-if="!editing" class="mt-1 text-xs text-gray-400">
          {{
            selectedFiles.length > 1
              ? '多文件上传：每个文档的阅读时长由系统按各自页数自动计算'
              : '选择文件后将自动识别页数并计算阅读时长'
          }}
        </div>
      </div>
    </div>
  </Modal>
</template>
