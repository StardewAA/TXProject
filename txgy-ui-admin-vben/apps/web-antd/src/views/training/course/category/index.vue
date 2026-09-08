<script lang="ts" setup>
import type { TrainingCourseApi } from '../api';

import { computed, onMounted, ref } from 'vue';

import {
  Button,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Switch,
  Table,
  Tag,
  message,
} from 'ant-design-vue';

import { $t } from '#/locales';

import {
  createCourseCategory,
  deleteCourseCategory,
  getCourseCategoryTree,
  updateCourseCategory,
} from '../api';

defineOptions({ name: 'TrainingCourseCategory' });

const loading = ref(false);
const tree = ref<TrainingCourseApi.CourseCategory[]>([]);

const levelTagMap: Record<number, { color: string; text: string }> = {
  1: { color: 'blue', text: '域' },
  2: { color: 'cyan', text: '组' },
  3: { color: 'green', text: '课' },
};

async function load() {
  loading.value = true;
  try {
    tree.value = (await getCourseCategoryTree()) ?? [];
  } finally {
    loading.value = false;
  }
}

const keyword = ref('');

function matchesKeyword(node: TrainingCourseApi.CourseCategory): boolean {
  const kw = (keyword.value || '').trim().toLowerCase();
  if (!kw) return true;
  return (
    (node.name ?? '').toLowerCase().includes(kw) ||
    (node.code ?? '').toLowerCase().includes(kw)
  );
}

/** 关键字过滤：命中的节点整枝保留，未命中的若有命中子孙则保留祖先链 */
function filterTree(nodes: TrainingCourseApi.CourseCategory[]): TrainingCourseApi.CourseCategory[] {
  const result: TrainingCourseApi.CourseCategory[] = [];
  for (const node of nodes) {
    if (matchesKeyword(node)) {
      result.push(node);
    } else {
      const children = filterTree(node.children ?? []);
      if (children.length > 0) {
        result.push({ ...node, children });
      }
    }
  }
  return result;
}

const displayTree = computed(() => filterTree(tree.value));

const modalVisible = ref(false);
const modalTitle = ref('');
const form = ref<TrainingCourseApi.CourseCategory>({});

function openCreate() {
  modalTitle.value = '新增课程分类';
  form.value = { parentId: 0, sort: 0, status: 'active', isRequired: false };
  modalVisible.value = true;
}

function openEdit(row: TrainingCourseApi.CourseCategory) {
  modalTitle.value = '编辑课程分类';
  form.value = {
    ...row,
    children: undefined,
  };
  modalVisible.value = true;
}

function findNode(nodes: TrainingCourseApi.CourseCategory[], id: number): TrainingCourseApi.CourseCategory | undefined {
  for (const node of nodes) {
    if (node.id === id) return node;
    const found = findNode(node.children ?? [], id);
    if (found) return found;
  }
  return undefined;
}

function collectSubtreeIds(node: TrainingCourseApi.CourseCategory, out: Set<number>) {
  out.add(node.id!);
  for (const child of node.children ?? []) {
    collectSubtreeIds(child, out);
  }
}

/** 父级候选：顶级域 + 域/组节点；编辑时排除自身及其子孙（避免成环） */
const parentOptions = computed(() => {
  const blocked = new Set<number>();
  if (form.value.id) {
    const self = findNode(tree.value, form.value.id);
    if (self) collectSubtreeIds(self, blocked);
  }
  const options: { label: string; value: number }[] = [
    { label: '— 顶级域（一级）—', value: 0 },
  ];
  const walk = (nodes: TrainingCourseApi.CourseCategory[], prefix: string) => {
    for (const node of nodes) {
      const level = node.level ?? 1;
      if (level < 3 && !blocked.has(node.id!)) {
        const label = prefix ? `${prefix} / ${node.name}` : (node.name ?? '');
        options.push({ label, value: node.id! });
        walk(node.children ?? [], label);
      } else {
        walk(node.children ?? [], prefix ? `${prefix} / ${node.name}` : (node.name ?? ''));
      }
    }
  };
  walk(tree.value, '');
  return options;
});

const nextLevelText = computed(() => {
  const parentId = form.value.parentId;
  if (!parentId) {
    return '将创建为第 1 级（域）';
  }
  const parent = findNode(tree.value, Number(parentId));
  const level = Math.min((parent?.level ?? 1) + 1, 3);
  const map = levelTagMap[level];
  return `将创建为第 ${level} 级（${map.text}）`;
});

async function handleSave() {
  if (!form.value.name?.trim()) {
    message.warning('请输入分类名称');
    return;
  }
  if (form.value.id) {
    await updateCourseCategory(form.value);
    message.success($t('ui.actionMessage.operationSuccess'));
  } else {
    await createCourseCategory(form.value);
    message.success($t('ui.actionMessage.createSuccess'));
  }
  modalVisible.value = false;
  load();
}

async function handleDelete(row: TrainingCourseApi.CourseCategory) {
  await deleteCourseCategory(row.id!);
  message.success($t('ui.actionMessage.deleteSuccess'));
  load();
}

onMounted(load);
</script>

<template>
  <div>
    <div class="mb-3 flex flex-wrap items-center gap-3">
      <Input
        v-model:value="keyword"
        class="!w-72"
        allow-clear
        placeholder="按名称 / 编码（如 A、防腐、A0101）过滤"
      />
      <span v-if="keyword.trim()" class="text-sm text-gray-400">
        匹配 {{ displayTree.reduce((s, n) => s + 1 + (n.children?.length ?? 0), 0) }} 条
      </span>
      <Button class="ml-auto" type="primary" ghost @click="load">刷新</Button>
      <Button type="primary" @click="openCreate">新增分类</Button>
    </div>

    <Table
      row-key="id"
      :data-source="displayTree"
      :loading="loading"
      :pagination="false"
      :default-expand-all-rows="true"
      size="middle"
      :columns="[
        { title: '分类名称', dataIndex: 'name', key: 'name' },
        { title: '层级', dataIndex: 'level', key: 'level', width: 70, align: 'center' },
        { title: '编码', dataIndex: 'code', key: 'code', width: 110 },
        { title: '业务类别', dataIndex: 'bizCategory', key: 'bizCategory', width: 150, ellipsis: true },
        { title: '标准学时', dataIndex: 'stdHours', key: 'stdHours', width: 90, align: 'center' },
        { title: '必修', dataIndex: 'isRequired', key: 'isRequired', width: 70, align: 'center' },
        { title: '排序', dataIndex: 'sort', key: 'sort', width: 70, align: 'center' },
        { title: '状态', dataIndex: 'status', key: 'status', width: 80, align: 'center' },
        { title: '操作', key: 'actions', width: 130, align: 'center' },
      ]"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'name'">
          <span class="font-medium">
            {{ record.name }}
          </span>
        </template>
        <template v-else-if="column.key === 'level'">
          <Tag :color="levelTagMap[record.level ?? 1]?.color">
            {{ levelTagMap[record.level ?? 1]?.text }}
          </Tag>
        </template>
        <template v-else-if="column.key === 'code'">
          <span class="font-mono text-gray-600">{{ record.code }}</span>
        </template>
        <template v-else-if="column.key === 'bizCategory'">
          {{ record.bizCategory || '-' }}
        </template>
        <template v-else-if="column.key === 'stdHours'">
          {{ record.stdHours ?? '-' }}
        </template>
        <template v-else-if="column.key === 'isRequired'">
          <Tag v-if="record.isRequired" color="red">必修</Tag>
          <Tag v-else color="default">选修</Tag>
        </template>
        <template v-else-if="column.key === 'sort'">
          {{ record.sort }}
        </template>
        <template v-else-if="column.key === 'status'">
          <Tag :color="record.status === 'active' ? 'green' : 'red'">
            {{ record.status === 'active' ? '启用' : '停用' }}
          </Tag>
        </template>
        <template v-else-if="column.key === 'actions'">
          <Button type="link" size="small" @click="openEdit(record)">编辑</Button>
          <Popconfirm
            :title="record.children?.length ? '该分类下有子分类，无法删除' : '确认删除该分类？'"
            :disabled="(record.children?.length ?? 0) > 0"
            @confirm="handleDelete(record)"
          >
            <Button
              type="link"
              danger
              size="small"
              :disabled="(record.children?.length ?? 0) > 0"
            >
              删除
            </Button>
          </Popconfirm>
        </template>
      </template>
    </Table>

    <div class="mt-2 text-xs text-gray-400">
      依据《云培训学院-课程分类表》维护 8 域 / 33 组 / 112 课三级分类（A 通用基础与安全、B 防腐涂装、C
      保温绝热与保冷、D 内衬与管杆修复、E 检测与理化试验、F 材料与采购、G 施工管理与项目交付、H 数字化与平台工具）。
      课程挂接课级分类；标准学时与必修标记供学时统计 / 学习计划 / 报表关联。存在子分类或已被课程引用的分类禁止删除。
    </div>

    <Modal
      v-model:open="modalVisible"
      :title="modalTitle"
      :width="560"
      ok-text="确定"
      cancel-text="取消"
      @ok="handleSave"
    >
      <div class="flex flex-col gap-4 py-2">
        <div>
          <div class="mb-1 text-sm text-gray-600">父级分类</div>
          <Select
            v-model:value="form.parentId"
            class="w-full"
            :options="parentOptions"
            show-search
            option-filter-prop="label"
            placeholder="选择父级（不选则创建一级域）"
          />
          <div class="mt-1 text-xs text-gray-400">{{ nextLevelText }}</div>
        </div>
        <div>
          <div class="mb-1 text-sm text-gray-600">
            分类名称 <span class="text-red-500">*</span>
          </div>
          <Input v-model:value="form.name" :maxlength="50" placeholder="请输入分类名称" />
        </div>
        <div>
          <div class="mb-1 text-sm text-gray-600">分类编码</div>
          <Input
            v-model:value="form.code"
            :maxlength="50"
            placeholder="如：A、A01、A0101"
          />
        </div>
        <div class="flex gap-4">
          <div class="flex-1">
            <div class="mb-1 text-sm text-gray-600">标准学时（课程级）</div>
            <InputNumber v-model:value="form.stdHours" class="w-full" :min="0" />
          </div>
          <div class="flex-1">
            <div class="mb-1 text-sm text-gray-600">排序（小值靠前）</div>
            <InputNumber v-model:value="form.sort" class="w-full" />
          </div>
        </div>
        <div class="flex gap-4">
          <div class="flex-1">
            <div class="mb-1 text-sm text-gray-600">是否必修</div>
            <Switch
              v-model:checked="form.isRequired"
              checked-children="必修"
              un-checked-children="选修"
            />
          </div>
          <div class="flex-1">
            <div class="mb-1 text-sm text-gray-600">状态</div>
            <Select
              v-model:value="form.status"
              class="w-full"
              :options="[
                { label: '启用', value: 'active' },
                { label: '停用', value: 'inactive' },
              ]"
            />
          </div>
        </div>
        <div>
          <div class="mb-1 text-sm text-gray-600">业务类别（域级，如：全部（必修）/ 一、二、三…）</div>
          <Input
            v-model:value="form.bizCategory"
            :maxlength="200"
            placeholder="适用工程队编号或必修范围，供资源打标/报表关联"
          />
        </div>
        <div>
          <div class="mb-1 text-sm text-gray-600">备注</div>
          <Input v-model:value="form.remark" :maxlength="200" placeholder="可填写分类说明" />
        </div>
      </div>
    </Modal>
  </div>
</template>
