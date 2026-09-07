<script lang="ts" setup>
import type { TrainingVrApi } from '../api';

import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { Button, Form, FormItem, Input, InputNumber, message, Modal, Select, Table, Tabs, Tag } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';

import {
  addProjectVersion,
  createProject,
  createTag,
  deleteProject,
  deleteTag,
  getProjectPage,
  getProjectVersionList,
  getTagPage,
  transitionProject,
  updateProject,
  updateTag,
} from '../api';
import {
  VR_LEVEL_TAG_OPTIONS,
  VR_PROCESS_TAG_PRESET,
  VR_TAG_TYPE_OPTIONS,
  VR_TAG_TYPE_TEXT,
  VR_TECH_TAG_PRESET,
  vrProjectStatusColor,
  vrProjectStatusText,
  vrVersionStatusText,
} from '../constants';

defineOptions({ name: 'TrainingVrProject' });

const activeTab = ref('project');

// ==================== 71.1 VR 实操项目资源 ====================

const [ProjectGrid, projectGridApi] = useVbenVxeGrid({
  formOptions: {
    schema: [
      {
        component: 'Input',
        componentProps: { allowClear: true, placeholder: '项目名称 / 编号' },
        fieldName: 'keyword',
        label: '关键字',
      },
      {
        component: 'Select',
        componentProps: {
          allowClear: true,
          options: [
            { label: '草稿', value: 0 },
            { label: '已上架', value: 1 },
            { label: '已下架', value: 2 },
            { label: '已归档', value: 3 },
          ],
        },
        fieldName: 'status',
        label: '状态',
      },
    ] satisfies VbenFormSchema[],
  },
  gridOptions: {
    columns: [
      { field: 'code', title: '项目编号', width: 110 },
      { field: 'name', title: 'VR 实操项目', minWidth: 200, showOverflow: true },
      { field: 'techTag', title: '工种', width: 100, slots: { default: 'techTag' } },
      { field: 'processTag', title: '工艺', width: 110, slots: { default: 'processTag' } },
      { field: 'levelTags', title: '技能等级', width: 150, slots: { default: 'levelTags' } },
      { field: 'durationHours', title: '学时(h)', width: 80, align: 'center' },
      { field: 'passScore', title: '合格线', width: 80, align: 'center' },
      { field: 'currentVersion', title: '当前版本', width: 100, slots: { default: 'version' } },
      { field: 'playCount', title: '实操人次', width: 90, align: 'center' },
      { field: 'status', title: '状态', width: 90, slots: { default: 'status' } },
      { title: '操作', width: 300, fixed: 'right', slots: { default: 'actions' } },
    ] as VxeTableGridOptions['columns'],
    height: 'auto',
    keepSource: true,
    proxyConfig: {
      ajax: {
        query: async ({ page }, formValues) =>
          await getProjectPage({
            pageNo: page.currentPage,
            pageSize: page.pageSize,
            ...formValues,
          }),
      },
    },
    rowConfig: { keyField: 'id', isHover: true },
    toolbarConfig: { refresh: true, search: true },
  },
});

const formOpen = ref(false);
const saving = ref(false);
const formData = ref<TrainingVrApi.VrProject>();
const form = reactive<Record<string, any>>({});

function openCreate() {
  formData.value = undefined;
  Object.keys(form).forEach((key) => delete form[key]);
  form.levelTags = [];
  formOpen.value = true;
}

function openEdit(row: TrainingVrApi.VrProject) {
  formData.value = row;
  Object.keys(form).forEach((key) => delete form[key]);
  Object.assign(form, row);
  form.levelTags = row.levelTags ? [...row.levelTags] : [];
  formOpen.value = true;
}

async function handleSave() {
  if (!form.name) {
    message.warning('请填写 VR 实操项目名称');
    return;
  }
  if (form.durationHours == null) {
    message.warning('请配置完成可获得学时');
    return;
  }
  if (form.passScore == null) {
    message.warning('请配置考核合格分数线');
    return;
  }
  saving.value = true;
  try {
    const payload: Partial<TrainingVrApi.VrProject> = {
      id: formData.value?.id,
      name: form.name,
      code: form.code || undefined,
      techTag: form.techTag || undefined,
      processTag: form.processTag || undefined,
      levelTags: form.levelTags || [],
      durationHours: form.durationHours,
      passScore: form.passScore,
      scopeDesc: form.scopeDesc || undefined,
    };
    if (formData.value?.id) {
      await updateProject(payload);
      message.success('保存成功');
    } else {
      const created = await createProject(payload);
      await addProjectVersion(created.id!, { releaseNote: '初始化版本' });
      message.success('创建成功，已初始化 v1.0.0 版本');
    }
    formOpen.value = false;
    projectGridApi.query();
  } finally {
    saving.value = false;
  }
}

async function handleTransition(row: TrainingVrApi.VrProject, action: 'publish' | 'off' | 'archive') {
  await transitionProject(row.id!, action);
  const text = action === 'publish' ? '已上架，可被任务引用' : action === 'off' ? '已下架' : '已归档，历史任务保留';
  message.success(text);
  projectGridApi.query();
}

async function handleDelete(row: TrainingVrApi.VrProject) {
  await deleteProject(row.id!);
  message.success('删除成功');
  projectGridApi.query();
}

// ========== 版本历史 / 新建版本 ==========
const versionOpen = ref(false);
const versionSaving = ref(false);
const versionList = ref<TrainingVrApi.VrProjectVersion[]>([]);
const versionProject = ref<TrainingVrApi.VrProject>();
const versionForm = reactive<Record<string, any>>({});

async function openVersion(row: TrainingVrApi.VrProject) {
  versionProject.value = row;
  versionList.value = await getProjectVersionList(row.id!);
  Object.keys(versionForm).forEach((key) => delete versionForm[key]);
  versionOpen.value = true;
}

async function handleAddVersion() {
  if (!versionProject.value) return;
  if (!versionForm.size) {
    message.warning('请填写资源包大小');
    return;
  }
  versionSaving.value = true;
  try {
    await addProjectVersion(versionProject.value.id!, {
      size: versionForm.size,
      releaseNote: versionForm.releaseNote || '',
    });
    message.success('新版本已创建并置为当前版本');
    versionList.value = await getProjectVersionList(versionProject.value.id!);
    Object.keys(versionForm).forEach((key) => delete versionForm[key]);
    projectGridApi.query();
  } finally {
    versionSaving.value = false;
  }
}

function buildProjectActions(row: TrainingVrApi.VrProject) {
  const actions: any[] = [];
  if (row.status === 1) {
    actions.push({
      label: '下架',
      type: 'link',
      icon: ACTION_ICON.VIEW,
      popConfirm: { title: '确认下架该项目？', confirm: () => handleTransition(row, 'off') },
    });
  } else if (row.status === 0 || row.status === 2) {
    actions.push({
      label: '上架',
      type: 'link',
      icon: ACTION_ICON.VIEW,
      popConfirm: { title: '上架后讲师/管理员可创建任务引用，确认？', confirm: () => handleTransition(row, 'publish') },
    });
  }
  if (row.status !== 3 && row.status !== 0) {
    actions.push({
      label: '归档',
      type: 'link',
      danger: true,
      popConfirm: { title: '归档后不允许新建任务引用，历史任务保留，确认？', confirm: () => handleTransition(row, 'archive') },
    });
  }
  actions.push({
    label: '版本历史',
    type: 'link',
    icon: ACTION_ICON.VIEW,
    onClick: () => openVersion(row),
  });
  if (row.status !== 3) {
    actions.push({
      label: '编辑',
      type: 'link',
      icon: ACTION_ICON.EDIT,
      onClick: () => openEdit(row),
    });
    if (row.status === 0) {
      actions.push({
        label: '删除',
        type: 'link',
        danger: true,
        icon: ACTION_ICON.DELETE,
        popConfirm: { title: '确认删除该草稿项目？', confirm: () => handleDelete(row) },
      });
    }
  }
  return actions;
}

// ==================== 71.2 分类标签维护 ====================

const [TagGrid, tagGridApi] = useVbenVxeGrid({
  formOptions: {
    schema: [
      {
        component: 'Input',
        componentProps: { allowClear: true, placeholder: '标签名称' },
        fieldName: 'keyword',
        label: '关键字',
      },
      {
        component: 'Select',
        componentProps: { allowClear: true, options: VR_TAG_TYPE_OPTIONS },
        fieldName: 'type',
        label: '标签类型',
      },
    ] satisfies VbenFormSchema[],
  },
  gridOptions: {
    columns: [
      { field: 'type', title: '标签类型', width: 120, slots: { default: 'tagType' } },
      { field: 'name', title: '标签名称', minWidth: 160 },
      { field: 'sort', title: '排序', width: 80, align: 'center' },
      { field: 'usageCount', title: '被引用项目数', width: 120, align: 'center' },
      { title: '操作', width: 140, fixed: 'right', slots: { default: 'tagActions' } },
    ] as VxeTableGridOptions['columns'],
    height: 'auto',
    keepSource: true,
    proxyConfig: {
      ajax: {
        query: async ({ page }, formValues) =>
          await getTagPage({ pageNo: page.currentPage, pageSize: page.pageSize, ...formValues }),
      },
    },
    rowConfig: { keyField: 'id', isHover: true },
    toolbarConfig: { refresh: true, search: true },
  },
});

const tagFormOpen = ref(false);
const tagSaving = ref(false);
const tagFormData = ref<TrainingVrApi.VrTag>();
const tagForm = reactive<Record<string, any>>({});

function openTagCreate() {
  tagFormData.value = undefined;
  Object.keys(tagForm).forEach((key) => delete tagForm[key]);
  tagForm.type = 2;
  tagFormOpen.value = true;
}

function openTagEdit(row: TrainingVrApi.VrTag) {
  tagFormData.value = row;
  Object.keys(tagForm).forEach((key) => delete tagForm[key]);
  Object.assign(tagForm, row);
  tagFormOpen.value = true;
}

async function handleTagSave() {
  if (!tagForm.name) {
    message.warning('请填写标签名称');
    return;
  }
  if (tagForm.type == null) {
    message.warning('请选择标签类型');
    return;
  }
  tagSaving.value = true;
  try {
    if (tagFormData.value?.id) {
      await updateTag({ id: tagFormData.value.id, name: tagForm.name, type: tagForm.type, sort: tagForm.sort });
    } else {
      await createTag({ name: tagForm.name, type: tagForm.type, sort: tagForm.sort || 99 });
    }
    message.success('保存成功');
    tagFormOpen.value = false;
    tagGridApi.query();
  } finally {
    tagSaving.value = false;
  }
}

async function handleTagDelete(row: TrainingVrApi.VrTag) {
  if ((row.usageCount ?? 0) > 0) {
    message.warning('该标签已被项目引用，无法删除');
    return;
  }
  await deleteTag(row.id!);
  message.success('删除成功');
  tagGridApi.query();
}
</script>

<template>
  <Page auto-content-height>
    <Tabs v-model:active-key="activeTab" type="line">
      <Tabs.TabPane key="project" tab="VR 实操项目资源">
        <ProjectGrid table-title="VR 实操项目（71 · 上传/版本管理/上下架归档）">
          <template #toolbar-tools>
            <TableAction
              :actions="[
                { label: '上传新项目', type: 'primary', icon: ACTION_ICON.ADD, onClick: openCreate },
              ]"
            />
          </template>
          <template #techTag="{ row }">{{ row.techTag || '-' }}</template>
          <template #processTag="{ row }">
            <Tag color="blue">{{ row.processTag || '-' }}</Tag>
          </template>
          <template #levelTags="{ row }">
            <template v-if="row.levelTags?.length">
              <Tag v-for="item in row.levelTags" :key="item" color="geekblue">
                {{ VR_LEVEL_TAG_OPTIONS.find((opt) => opt.value === item)?.label }}
              </Tag>
            </template>
            <span v-else>-</span>
          </template>
          <template #version="{ row }">
            <Tag color="purple">{{ row.currentVersion }}</Tag>
          </template>
          <template #status="{ row }">
            <Tag :color="vrProjectStatusColor(row.status) || 'default'">{{ vrProjectStatusText(row.status) }}</Tag>
          </template>
          <template #actions="{ row }">
            <TableAction :actions="buildProjectActions(row)" />
          </template>
        </ProjectGrid>
      </Tabs.TabPane>

      <Tabs.TabPane key="tag" tab="分类标签维护">
        <TagGrid table-title="VR 项目分类标签（学徒/初级技工/中级技工/高级技师 · 工种/工艺）">
          <template #toolbar-tools>
            <TableAction
              :actions="[{ label: '新增标签', type: 'primary', icon: ACTION_ICON.ADD, onClick: openTagCreate }]"
            />
          </template>
          <template #tagType="{ row }">
            <Tag :color="row.type === 1 ? 'magenta' : row.type === 2 ? 'cyan' : 'orange'">
              {{ VR_TAG_TYPE_TEXT[row.type] || '-' }}
            </Tag>
          </template>
          <template #tagActions="{ row }">
            <TableAction
              :actions="[
                { label: '编辑', type: 'link', icon: ACTION_ICON.EDIT, onClick: () => openTagEdit(row) },
                { label: '删除', type: 'link', danger: true, icon: ACTION_ICON.DELETE, popConfirm: { title: '确认删除该标签？', confirm: () => handleTagDelete(row) } },
              ]"
            />
          </template>
        </TagGrid>
      </Tabs.TabPane>
    </Tabs>

    <!-- 项目新增/编辑（含学时与评分规则配置） -->
    <Modal v-model:open="formOpen" :title="formData?.id ? '编辑 VR 实操项目' : '上传 VR 实操项目'" :confirm-loading="saving" width="640px" @ok="handleSave">
      <Form layout="vertical" class="pt-2">
        <div class="grid grid-cols-2 gap-x-4">
          <FormItem label="项目名称" required>
            <Input v-model:value="form.name" placeholder="如 喷砂除锈虚拟仿真实操" />
          </FormItem>
          <FormItem label="项目编号">
            <Input v-model:value="form.code" placeholder="留空自动生成，如 VR-FG-001" />
          </FormItem>
          <FormItem label="工种标签">
            <Select v-model:value="form.techTag" :options="VR_TECH_TAG_PRESET.map((item) => ({ label: item, value: item }))" placeholder="选择工种" allow-clear />
          </FormItem>
          <FormItem label="工艺标签">
            <Select v-model:value="form.processTag" :options="VR_PROCESS_TAG_PRESET.map((item) => ({ label: item, value: item }))" placeholder="选择工艺" allow-clear />
          </FormItem>
          <FormItem label="技能等级标签">
            <Select v-model:value="form.levelTags" :options="VR_LEVEL_TAG_OPTIONS" mode="multiple" placeholder="可多选" />
          </FormItem>
          <FormItem label="完成可获得学时(h)" required>
            <InputNumber v-model:value="form.durationHours" :min="0.5" :max="24" :step="0.5" class="!w-full" placeholder="如 2" />
          </FormItem>
        </div>
        <div class="grid grid-cols-2 gap-x-4">
          <FormItem label="考核合格分数线(分)" required>
            <InputNumber v-model:value="form.passScore" :min="0" :max="100" class="!w-full" placeholder="如 80" />
          </FormItem>
          <FormItem label="当前版本">
            <Input :value="formData?.currentVersion || 'v1.0.0'" disabled />
          </FormItem>
        </div>
        <FormItem label="适用岗位/工序说明">
          <Input.TextArea v-model:value="form.scopeDesc" :rows="3" placeholder="描述适用岗位、训练目标与关键操作点" />
        </FormItem>
      </Form>
    </Modal>

    <!-- 版本历史 / 新建版本 -->
    <Modal v-model:open="versionOpen" :title="`版本管理：${versionProject?.name || ''}`" width="640px" :footer="null">
      <div class="mb-2 text-sm text-gray-500">支持多版本维护：新版本上线后旧版本保留历史记录，当前版本可回看。</div>
      <Table
        :columns="[
          { title: '版本号', dataIndex: 'version', width: 110 },
          { title: '包大小', dataIndex: 'size', width: 90 },
          { title: '状态', dataIndex: 'status', width: 90 },
          { title: '发布说明', dataIndex: 'releaseNote', ellipsis: true },
          { title: '发布时间', dataIndex: 'releaseTime', width: 160 },
        ]"
        :data-source="versionList"
        :pagination="false"
        size="small"
        row-key="id"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'status'">
            <Tag :color="record.status === 2 ? 'success' : 'default'">{{ vrVersionStatusText(record.status) }}</Tag>
          </template>
          <template v-else-if="column.dataIndex === 'version'">
            <Tag color="purple">{{ record.version }}</Tag>
          </template>
        </template>
      </Table>
      <div class="mt-4 border-t pt-3">
        <div class="mb-2 font-medium text-gray-700">新建版本（发布后自动切换为当前版本）</div>
        <div class="grid grid-cols-2 gap-x-4">
          <FormItem label="资源包大小">
            <Input v-model:value="versionForm.size" placeholder="如 2.3 GB" />
          </FormItem>
          <FormItem label="发布说明">
            <Input v-model:value="versionForm.releaseNote" placeholder="本次版本变更内容" />
          </FormItem>
        </div>
        <div class="text-right">
          <Button type="primary" :loading="versionSaving" @click="handleAddVersion">创建新版本</Button>
        </div>
      </div>
    </Modal>

    <!-- 分类标签新增/编辑 -->
    <Modal v-model:open="tagFormOpen" :title="tagFormData?.id ? '编辑分类标签' : '新增分类标签'" :confirm-loading="tagSaving" @ok="handleTagSave">
      <Form layout="vertical" class="pt-2">
        <div class="grid grid-cols-2 gap-x-4">
          <FormItem label="标签类型" required>
            <Select v-model:value="tagForm.type" :options="VR_TAG_TYPE_OPTIONS" />
          </FormItem>
          <FormItem label="排序号">
            <InputNumber v-model:value="tagForm.sort" :min="0" class="!w-full" />
          </FormItem>
        </div>
        <FormItem label="标签名称" required>
          <Input v-model:value="tagForm.name" placeholder="如 电焊作业 / 聚乙烯包覆" />
        </FormItem>
      </Form>
    </Modal>
  </Page>
</template>
