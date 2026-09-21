<script lang="ts" setup>
import type { TrainingResourceApi } from '../api';

import { computed, ref, watch } from 'vue';

import {
  Button,
  Empty,
  Modal,
  Select,
  Table,
  Tag,
  TreeSelect,
  Upload,
  message,
} from 'ant-design-vue';

import {
  getResourceCategoryTree,
  getResourceTagList,
  importQuestionsByAi,
  parseQuestionByAi,
} from '../api';

defineOptions({ name: 'TrainingResourceQuestionAiImport' });

const open = defineModel<boolean>('open', { default: false });
const emit = defineEmits(['success']);

const QTYPE_TEXT: Record<number, string> = {
  1: '单选',
  2: '多选',
  3: '判断',
  4: '实操问答',
  5: '案例分析',
  6: '填空',
  7: '简答',
};
const DIFFICULTY_TEXT: Record<number, string> = { 1: '易', 2: '中', 3: '难' };

const selectedFile = ref<File | null>(null);
const parsing = ref(false);
const importing = ref(false);
const result = ref<TrainingResourceApi.AiQuestionParseResult | null>(null);
const items = ref<TrainingResourceApi.AiQuestionItem[]>([]);

/** 所属分类（与新增试题一致：资源分类 question 类型，支持三级） */
const categoryId = ref<number | undefined>();
/** 标签（与新增试题一致：多选） */
const tagIds = ref<number[]>([]);

const parsed = computed(() => items.value.length > 0);

// ========== 分类 / 标签选项（与新增试题表单同源） ==========

const tagOptions = ref<any[]>([]);
async function loadTags() {
  const list = await getResourceTagList();
  tagOptions.value = list.map((item) => ({
    label: item.name,
    value: item.id,
  }));
}

const categoryOptions = ref<any[]>([]);
function buildCategoryTree(
  nodes?: TrainingResourceApi.CategoryNode[],
): any[] | undefined {
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
  const tree = await getResourceCategoryTree('question');
  categoryOptions.value = buildCategoryTree(tree) || [];
}

watch(open, (val) => {
  if (val) {
    loadTags();
    loadCategories();
  } else {
    // 关闭后重置
    selectedFile.value = null;
    result.value = null;
    items.value = [];
    categoryId.value = undefined;
    tagIds.value = [];
  }
});

function pickFile(file: File) {
  if (file.size > 20 * 1024 * 1024) {
    message.error('AI 导入文档大小不能超过 20MB');
    return false;
  }
  selectedFile.value = file;
  result.value = null;
  items.value = [];
  return false; // 阻止自动上传
}

function clearFile() {
  selectedFile.value = null;
  result.value = null;
  items.value = [];
}

async function handleParse() {
  if (!selectedFile.value) {
    message.warning('请先选择 PDF / Word / WPS 文档');
    return;
  }
  parsing.value = true;
  try {
    const res = await parseQuestionByAi(selectedFile.value);
    result.value = res ?? null;
    items.value = res?.items ?? [];
    if (res?.message) {
      message.info(res.message);
    }
  } finally {
    parsing.value = false;
  }
}

function handleRemoveItem(index: number) {
  items.value.splice(index, 1);
}

async function handleImport() {
  if (items.value.length === 0) {
    message.warning('暂无可导入的试题');
    return;
  }
  importing.value = true;
  try {
    const count = await importQuestionsByAi({
      items: items.value,
      categoryId: categoryId.value,
      tagIds: tagIds.value,
    });
    message.success(`成功导入 ${count ?? 0} 道试题`);
    open.value = false;
    emit('success');
  } finally {
    importing.value = false;
  }
}
</script>

<template>
  <Modal
    v-model:open="open"
    title="AI 智能导入试题"
    :width="900"
    :ok-text="`确认导入（${items.length}）`"
    cancel-text="取消"
    :confirm-loading="importing"
    @ok="handleImport"
  >
    <div class="flex flex-col gap-4">
      <div>
        <div class="mb-1 text-sm text-gray-600">
          上传试题文档（PDF / Word(doc,docx) / WPS(wps)，≤20MB）
        </div>
        <div class="flex flex-wrap items-center gap-2">
          <Upload
            accept=".pdf,.doc,.docx,.wps"
            :show-upload-list="false"
            :before-upload="pickFile"
          >
            <Button type="primary" ghost>
              {{ selectedFile ? '重新选择文件' : '选择文档' }}
            </Button>
          </Upload>
          <Tag v-if="selectedFile" color="blue">{{ selectedFile.name }}</Tag>
          <Button v-if="selectedFile" size="small" type="text" danger @click="clearFile">
            移除
          </Button>
          <Button
            type="primary"
            :loading="parsing"
            :disabled="!selectedFile"
            @click="handleParse"
          >
            AI 解析
          </Button>
        </div>
        <div class="mt-2 text-xs text-gray-400">
          系统将提取文档正文并交由 AI 大模型（DeepSeek / 豆包等）识别试题；
          未配置模型时自动按本地规则解析，导入前请核对结果。
        </div>
      </div>

      <!-- 整批统一：所属分类 + 标签（与新增试题一致） -->
      <div class="grid grid-cols-1 gap-4 rounded-md border border-gray-100 p-3 md:grid-cols-2">
        <div>
          <div class="mb-1 text-sm text-gray-600">
            所属分类（整份文件的试题统一归入该分类）
          </div>
          <TreeSelect
            v-model:value="categoryId"
            class="w-full"
            :allow-clear="true"
            tree-default-expand-all
            placeholder="请选择试题分类（与新增试题的分类一致）"
            :tree-data="categoryOptions"
          />
        </div>
        <div>
          <div class="mb-1 text-sm text-gray-600">
            标签（整份文件的试题统一打上该标签）
          </div>
          <Select
            v-model:value="tagIds"
            class="w-full"
            mode="multiple"
            :max-tag-count="3"
            placeholder="请选择标签（与新增试题的标签一致）"
            :options="tagOptions"
          />
        </div>
      </div>

      <div v-if="result" class="flex flex-wrap items-center gap-2">
        <Tag :color="result.aiEnabled ? 'green' : 'orange'">
          {{ result.aiEnabled ? 'AI 已生效' : 'AI 未配置（本地规则兜底）' }}
        </Tag>
        <Tag v-if="result.fallback" color="orange">兜底解析</Tag>
        <Tag v-if="result.fileType">文档类型：{{ result.fileType }}</Tag>
        <Tag v-if="result.textLength">提取文本：{{ result.textLength }} 字</Tag>
      </div>

      <Table
        v-if="parsed"
        row-key="stem"
        :data-source="items"
        :pagination="{ pageSize: 8 }"
        size="small"
        :columns="[
          { title: '题型', key: 'qtype', width: 100, align: 'center' },
          { title: '题干', key: 'stem' },
          { title: '答案', dataIndex: 'answer', key: 'answer', width: 90, align: 'center' },
          { title: '解析', dataIndex: 'analysis', key: 'analysis', width: 160 },
          { title: '难度', key: 'difficulty', width: 80, align: 'center' },
          { title: '操作', key: 'actions', width: 80, align: 'center' },
        ]"
      >
        <template #bodyCell="{ column, record, index }">
          <template v-if="column.key === 'qtype'">
            <Tag color="blue">{{ QTYPE_TEXT[record.qtype ?? 1] }}</Tag>
          </template>
          <template v-else-if="column.key === 'stem'">
            <div class="text-sm">{{ record.stem }}</div>
            <div
              v-if="record.options && record.options.length"
              class="mt-1 text-xs text-gray-400"
            >
              <span
                v-for="opt in record.options"
                :key="opt.key"
                class="mr-2"
              >
                {{ opt.key }}. {{ opt.text }}
              </span>
            </div>
          </template>
          <template v-else-if="column.key === 'difficulty'">
            {{ DIFFICULTY_TEXT[record.difficulty ?? 2] }}
          </template>
          <template v-else-if="column.key === 'actions'">
            <Button type="link" danger size="small" @click="handleRemoveItem(index)">
              移除
            </Button>
          </template>
        </template>
      </Table>
      <Empty v-else description="请选择文档并点击「AI 解析」" />
    </div>
  </Modal>
</template>
