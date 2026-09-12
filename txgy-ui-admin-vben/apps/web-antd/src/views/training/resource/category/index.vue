<script lang="ts" setup>
import type { TrainingResourceApi } from '../api';

import { computed, ref, watch } from 'vue';

import { Page } from '@vben/common-ui';

import {
  Button,
  Input,
  InputNumber,
  message,
  Modal,
  Popconfirm,
  Radio,
  Select,
  Table,
  Tabs,
  Tag,
} from 'ant-design-vue';

import { $t } from '#/locales';

import {
  createResourceCategory,
  createResourceTag,
  deleteResourceCategory,
  deleteResourceTag,
  getResourceCategoryTree,
  getResourceTagList,
  updateResourceCategory,
  updateResourceTag,
} from '../api';
import {
  CATEGORY_TYPE_OPTIONS,
  TAG_DIMENSION_OPTIONS,
} from '../constants';

defineOptions({ name: 'TrainingResourceCategory' });

// ==================== 分类 Tab ====================
// 默认展示「文档分类」
const categoryType = ref<string>('doc');
const categoryLoading = ref(false);
const categoryTree = ref<TrainingResourceApi.CategoryNode[]>([]);

const levelTagMap: Record<number, { color: string; text: string }> = {
  1: { color: 'geekblue', text: '一级' },
  2: { color: 'cyan', text: '二级' },
  3: { color: 'green', text: '三级' },
};

/** 递归平铺当前分类树，用于生成上级选项 */
function flatCategoryTree(
  nodes: TrainingResourceApi.CategoryNode[],
  level = 1,
): Array<{ level: number; node: TrainingResourceApi.CategoryNode }> {
  const result: Array<{ level: number; node: TrainingResourceApi.CategoryNode }> = [];
  for (const node of nodes) {
    result.push({ level, node });
    if (node.children?.length) {
      result.push(...flatCategoryTree(node.children, level + 1));
    }
  }
  return result;
}

async function loadCategoryTree() {
  categoryLoading.value = true;
  try {
    categoryTree.value =
      (await getResourceCategoryTree(categoryType.value)) || [];
  } finally {
    categoryLoading.value = false;
  }
}

watch(categoryType, () => loadCategoryTree());

// 分类编辑弹窗
const categoryModalVisible = ref(false);
const categoryModalTitle = ref('');
const categoryForm = ref({
  id: undefined as number | undefined,
  type: 'doc',
  name: '',
  parentId: 0,
  sort: 0,
});
const parentOptions = computed(() => {
  const options: any[] = [{ label: '（一级分类）', value: 0 }];
  flatCategoryTree(categoryTree.value).forEach(({ level, node }) => {
    // 三级节点不能再新增子分类
    if (level < 3) {
      options.push({ label: '　'.repeat(level - 1) + node.name, value: node.id });
    }
  });
  return options;
});

function handleCreateCategory(parentId = 0) {
  categoryModalTitle.value = parentId ? '新增子分类' : '新增一级分类';
  categoryForm.value = {
    id: undefined,
    type: categoryType.value,
    name: '',
    parentId,
    sort: 0,
  };
  categoryModalVisible.value = true;
}

function handleEditCategory(node: TrainingResourceApi.CategoryNode) {
  categoryModalTitle.value = '编辑分类';
  categoryForm.value = {
    id: node.id,
    type: categoryType.value,
    name: node.name || '',
    parentId: node.parentId || 0,
    sort: node.sort ?? 0,
  };
  categoryModalVisible.value = true;
}

async function handleSaveCategory() {
  const form = categoryForm.value;
  if (!form.name?.trim()) {
    message.warning('请输入分类名称');
    return;
  }
  if (form.id) {
    await updateResourceCategory({ ...form, name: form.name.trim() });
    message.success($t('ui.actionMessage.operationSuccess'));
  } else {
    await createResourceCategory({ ...form, name: form.name.trim() });
    message.success($t('ui.actionMessage.createSuccess'));
  }
  categoryModalVisible.value = false;
  loadCategoryTree();
}

async function handleDeleteCategory(node: TrainingResourceApi.CategoryNode) {
  await deleteResourceCategory(node.id!);
  message.success($t('ui.actionMessage.deleteSuccess'));
  loadCategoryTree();
}

// ==================== 标签 Tab ====================
const dimension = ref<string | undefined>();
const tagLoading = ref(false);
const tagList = ref<TrainingResourceApi.ResourceTag[]>([]);

async function loadTags() {
  tagLoading.value = true;
  try {
    tagList.value = (await getResourceTagList(dimension.value)) || [];
  } finally {
    tagLoading.value = false;
  }
}

watch(dimension, () => loadTags());

const dimensionTextMap: Record<string, string> = Object.fromEntries(
  TAG_DIMENSION_OPTIONS.map((item) => [item.value, item.label]),
);

// 标签编辑弹窗
const tagModalVisible = ref(false);
const tagModalTitle = ref('');
const tagForm = ref({
  id: undefined as number | undefined,
  name: '',
  dimension: 'work_type',
  sort: 0,
});

function handleCreateTag() {
  tagModalTitle.value = '新增标签';
  tagForm.value = {
    id: undefined,
    name: '',
    dimension: dimension.value || 'work_type',
    sort: 0,
  };
  tagModalVisible.value = true;
}

function handleEditTag(row: TrainingResourceApi.ResourceTag) {
  tagModalTitle.value = '编辑标签';
  tagForm.value = {
    id: row.id,
    name: row.name || '',
    dimension: row.dimension || 'work_type',
    sort: row.sort ?? 0,
  };
  tagModalVisible.value = true;
}

async function handleSaveTag() {
  if (!tagForm.value.name?.trim()) {
    message.warning('请输入标签名称');
    return;
  }
  if (tagForm.value.id) {
    await updateResourceTag({ ...tagForm.value, name: tagForm.value.name.trim() });
    message.success($t('ui.actionMessage.operationSuccess'));
  } else {
    await createResourceTag({ ...tagForm.value, name: tagForm.value.name.trim() });
    message.success($t('ui.actionMessage.createSuccess'));
  }
  tagModalVisible.value = false;
  loadTags();
}

async function handleDeleteTag(row: TrainingResourceApi.ResourceTag) {
  await deleteResourceTag(row.id!);
  message.success($t('ui.actionMessage.deleteSuccess'));
  loadTags();
}

loadCategoryTree();
loadTags();
</script>

<template>
  <Page auto-content-height>
    <Tabs type="card">
      <!-- ============ 资源分类 ============ -->
      <Tabs.TabPane key="category" tab="资源分类">
        <div class="mb-3 flex flex-wrap items-center gap-3">
          <span class="text-sm text-gray-500">分类类型</span>
          <Radio.Group
            v-model:value="categoryType"
            :options="CATEGORY_TYPE_OPTIONS"
            option-type="button"
            button-style="solid"
            @change="loadCategoryTree"
          />
          <Button
            type="primary"
            ghost
            class="ml-auto"
            @click="handleCreateCategory(0)"
          >
            新增一级分类
          </Button>
        </div>

        <Table
          row-key="id"
          :data-source="categoryTree"
          :loading="categoryLoading"
          :pagination="false"
          :columns="[
            { title: '分类名称', dataIndex: 'name', key: 'name' },
            { title: '层级', key: 'level', width: 100, align: 'center' },
            { title: '排序', dataIndex: 'sort', key: 'sort', width: 90, align: 'center' },
            { title: '操作', key: 'actions', width: 260 },
          ]"
          default-expand-all-rows
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'level'">
              <Tag :color="levelTagMap[record.level ?? 1]?.color">
                {{ levelTagMap[record.level ?? 1]?.text }}
              </Tag>
            </template>
            <template v-else-if="column.key === 'actions'">
              <Button
                v-if="record.level && record.level < 3"
                type="link"
                size="small"
                @click="handleCreateCategory(record.id)"
              >
                新增子分类
              </Button>
              <Button type="link" size="small" @click="handleEditCategory(record)">
                编辑
              </Button>
              <Popconfirm
                title="存在子分类或资源时无法删除，确认删除？"
                @confirm="handleDeleteCategory(record)"
              >
                <Button type="link" danger size="small">删除</Button>
              </Popconfirm>
            </template>
          </template>
        </Table>
      </Tabs.TabPane>

      <!-- ============ 标签管理 ============ -->
      <Tabs.TabPane key="tag" tab="标签管理">
        <div class="mb-3 flex flex-wrap items-center gap-3">
          <span class="text-sm text-gray-500">维度</span>
          <Select
            v-model:value="dimension"
            class="!w-40"
            :allow-clear="true"
            placeholder="全部维度"
            :options="TAG_DIMENSION_OPTIONS"
          />
          <Button type="primary" ghost class="ml-auto" @click="handleCreateTag">
            新增标签
          </Button>
        </div>

        <Table
          row-key="id"
          :data-source="tagList"
          :loading="tagLoading"
          :pagination="false"
          :columns="[
            { title: '标签名称', dataIndex: 'name', key: 'name' },
            { title: '维度', key: 'dimension', width: 120, align: 'center' },
            { title: '排序', dataIndex: 'sort', key: 'sort', width: 90, align: 'center' },
            { title: '创建时间', dataIndex: 'createTime', key: 'createTime', width: 190 },
            { title: '操作', key: 'actions', width: 160 },
          ]"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'dimension'">
              <Tag color="purple">
                {{ dimensionTextMap[record.dimension] || record.dimension }}
              </Tag>
            </template>
            <template v-else-if="column.key === 'actions'">
              <Button type="link" size="small" @click="handleEditTag(record)">
                编辑
              </Button>
              <Popconfirm
                title="已被试题引用时无法删除，确认删除？"
                @confirm="handleDeleteTag(record)"
              >
                <Button type="link" danger size="small">删除</Button>
              </Popconfirm>
            </template>
          </template>
        </Table>
      </Tabs.TabPane>
    </Tabs>

    <!-- 分类编辑弹窗 -->
    <Modal
      v-model:open="categoryModalVisible"
      :title="categoryModalTitle"
      :width="480"
      :ok-text="'确定'"
      :cancel-text="'取消'"
      @ok="handleSaveCategory"
    >
      <div class="flex flex-col gap-4 py-2">
        <div v-if="categoryForm.id">
          <div class="mb-1 text-sm text-gray-600">分类类型</div>
          <Tag color="geekblue">
            {{
              CATEGORY_TYPE_OPTIONS.find(
                (item) => item.value === categoryForm.type,
              )?.label
            }}
          </Tag>
        </div>
        <div>
          <div class="mb-1 text-sm text-gray-600">上级分类</div>
          <Select
            v-model:value="categoryForm.parentId"
            class="w-full"
            :options="parentOptions"
            :disabled="!!categoryForm.id"
          />
        </div>
        <div>
          <div class="mb-1 text-sm text-gray-600">
            分类名称 <span class="text-red-500">*</span>
          </div>
          <Input
            v-model:value="categoryForm.name"
            :maxlength="30"
            placeholder="请输入分类名称"
          />
        </div>
        <div>
          <div class="mb-1 text-sm text-gray-600">排序（小值靠前）</div>
          <InputNumber v-model:value="categoryForm.sort" class="w-full" />
        </div>
      </div>
    </Modal>

    <!-- 标签编辑弹窗 -->
    <Modal
      v-model:open="tagModalVisible"
      :title="tagModalTitle"
      :width="480"
      :ok-text="'确定'"
      :cancel-text="'取消'"
      @ok="handleSaveTag"
    >
      <div class="flex flex-col gap-4 py-2">
        <div>
          <div class="mb-1 text-sm text-gray-600">
            标签名称 <span class="text-red-500">*</span>
          </div>
          <Input
            v-model:value="tagForm.name"
            :maxlength="30"
            placeholder="请输入标签名称"
          />
        </div>
        <div>
          <div class="mb-1 text-sm text-gray-600">所属维度</div>
          <Select
            v-model:value="tagForm.dimension"
            class="w-full"
            :options="TAG_DIMENSION_OPTIONS"
            :disabled="!!tagForm.id"
          />
        </div>
        <div>
          <div class="mb-1 text-sm text-gray-600">排序（小值靠前）</div>
          <InputNumber v-model:value="tagForm.sort" class="w-full" />
        </div>
      </div>
    </Modal>
  </Page>
</template>
