<script lang="ts" setup>
import type { TrainingCourseApi } from '../api';

import { onMounted, reactive, ref } from 'vue';

import {
  Button,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Table,
  message,
} from 'ant-design-vue';

import { $t } from '#/locales';

import {
  createCourseCategory,
  deleteCourseCategory,
  getCourseCategoryPage,
  updateCourseCategory,
} from '../api';

defineOptions({ name: 'TrainingCourseCategory' });

const loading = ref(false);
const list = ref<TrainingCourseApi.CourseCategory[]>([]);
const total = ref(0);
const query = reactive({
  pageNo: 1,
  pageSize: 10,
  name: '',
});

async function load() {
  loading.value = true;
  try {
    const res = await getCourseCategoryPage({
      ...query,
      name: query.name || undefined,
    });
    list.value = res?.list ?? [];
    total.value = Number(res?.total ?? 0);
  } finally {
    loading.value = false;
  }
}

const modalVisible = ref(false);
const modalTitle = ref('');
const form = ref<TrainingCourseApi.CourseCategory>({ sort: 0 });

function openCreate() {
  modalTitle.value = '新增课程分类';
  form.value = { sort: 0 };
  modalVisible.value = true;
}

function openEdit(row: TrainingCourseApi.CourseCategory) {
  modalTitle.value = '编辑课程分类';
  form.value = { ...row };
  modalVisible.value = true;
}

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
        v-model:value="query.name"
        class="!w-56"
        allow-clear
        placeholder="分类名称"
        @press-enter="load"
      />
      <Button type="primary" ghost @click="load">查询</Button>
      <Button type="primary" class="ml-auto" @click="openCreate">
        新增分类
      </Button>
    </div>

    <Table
      row-key="id"
      :data-source="list"
      :loading="loading"
      :pagination="{
        current: query.pageNo,
        pageSize: query.pageSize,
        total: total,
        showSizeChanger: true,
        showTotal: (total: number) => `共 ${total} 条`,
      }"
      :columns="[
        { title: '分类名称', dataIndex: 'name', key: 'name' },
        { title: '分类编码', dataIndex: 'code', key: 'code', width: 200 },
        { title: '排序', dataIndex: 'sort', key: 'sort', width: 90, align: 'center' },
        { title: '备注', dataIndex: 'remark', key: 'remark' },
        { title: '操作', key: 'actions', width: 140, align: 'center' },
      ]"
      @change="
        (pager: any) => {
          query.pageNo = pager.current;
          query.pageSize = pager.pageSize;
          load();
        }
      "
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'actions'">
          <Button type="link" size="small" @click="openEdit(record)">编辑</Button>
          <Popconfirm
            title="已被课程使用的分类禁止删除，确认删除？"
            @confirm="handleDelete(record)"
          >
            <Button type="link" danger size="small">删除</Button>
          </Popconfirm>
        </template>
      </template>
    </Table>

    <div class="mt-2 text-xs text-gray-400">
      按方案文档 21，分类覆盖行业新规政策、国标规范解读、防腐工艺教程、保温施工工法、
      材料选型知识、生产操作规范、质检检验教程、安全环保实操、高危作业培训、招投标合规、
      项目管理、职业素养、持证上岗专项课程；已被课程引用的分类禁止删除。
    </div>

    <Modal
      v-model:open="modalVisible"
      :title="modalTitle"
      :width="480"
      ok-text="确定"
      cancel-text="取消"
      @ok="handleSave"
    >
      <div class="flex flex-col gap-4 py-2">
        <div>
          <div class="mb-1 text-sm text-gray-600">
            分类名称 <span class="text-red-500">*</span>
          </div>
          <Input v-model:value="form.name" :maxlength="50" placeholder="请输入分类名称" />
        </div>
        <div>
          <div class="mb-1 text-sm text-gray-600">分类编码</div>
          <Input v-model:value="form.code" :maxlength="50" placeholder="如：industry-policy" />
        </div>
        <div>
          <div class="mb-1 text-sm text-gray-600">排序（小值靠前）</div>
          <InputNumber v-model:value="form.sort" class="w-full" />
        </div>
        <div>
          <div class="mb-1 text-sm text-gray-600">备注</div>
          <Input v-model:value="form.remark" :maxlength="200" placeholder="可填写分类说明" />
        </div>
      </div>
    </Modal>
  </div>
</template>
