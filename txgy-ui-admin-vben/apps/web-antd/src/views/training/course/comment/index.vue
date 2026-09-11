<script lang="ts" setup>
import type { TrainingCourseApi } from '../api';

import { onMounted, reactive, ref } from 'vue';

import {
  Button,
  Input,
  Modal,
  Popconfirm,
  Select,
  Table,
  Tag,
  message,
} from 'ant-design-vue';

import { deleteCourseComment, getCoursePage, getCourseCommentPage } from '../api';

defineOptions({ name: 'TrainingCourseComment' });

const STATUS_OPTIONS = [
  { label: '全部', value: undefined },
  { label: '正常', value: 1 },
  { label: '已删除', value: 2 },
];

const loading = ref(false);
const list = ref<TrainingCourseApi.CourseComment[]>([]);
const total = ref(0);
const query = reactive({
  pageNo: 1,
  pageSize: 10,
  courseId: undefined as number | undefined,
  status: undefined as number | undefined,
});
const courseOptions = ref<{ label: string; value: number }[]>([]);

async function loadCourseOptions() {
  const res = await getCoursePage({ pageNo: 1, pageSize: 100 });
  courseOptions.value = (res?.list ?? []).map((item) => ({
    label: item.title ?? `课程${item.id}`,
    value: item.id!,
  }));
}

async function load() {
  loading.value = true;
  try {
    const res = await getCourseCommentPage({ ...query });
    list.value = res?.list ?? [];
    total.value = Number(res?.total ?? 0);
  } finally {
    loading.value = false;
  }
}

const deleteVisible = ref(false);
const deleteForm = ref({ id: undefined as number | undefined, reason: '' });

function openDelete(row: TrainingCourseApi.CourseComment) {
  deleteForm.value = { id: row.id, reason: '' };
  deleteVisible.value = true;
}

async function handleDelete() {
  await deleteCourseComment(deleteForm.value.id!, deleteForm.value.reason || undefined);
  message.success('评论已删除（原文保留用于审计）');
  deleteVisible.value = false;
  load();
}

onMounted(() => {
  loadCourseOptions();
  load();
});
</script>

<template>
  <div>
    <div class="mb-3 flex flex-wrap items-center gap-3">
      <span class="text-sm text-gray-500">课程</span>
      <Select
        v-model:value="query.courseId"
        class="!w-64"
        allow-clear
        show-search
        option-filter-prop="label"
        placeholder="全部课程"
        :options="courseOptions"
        @change="load"
      />
      <span class="text-sm text-gray-500">状态</span>
      <Select
        v-model:value="query.status"
        class="!w-32"
        :options="STATUS_OPTIONS"
        @change="load"
      />
      <Button type="primary" ghost @click="load">查询</Button>
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
        { title: '课程', key: 'course', width: 220 },
        { title: '评论人', dataIndex: 'userName', key: 'userName', width: 130 },
        { title: '评论内容', dataIndex: 'content', key: 'content' },
        { title: '状态', key: 'status', width: 100, align: 'center' },
        { title: '删除人', dataIndex: 'deletedBy', key: 'deletedBy', width: 100, align: 'center' },
        { title: '删除时间', dataIndex: 'deletedAt', key: 'deletedAt', width: 180 },
        { title: '删除原因', dataIndex: 'deleteReason', key: 'deleteReason', width: 140 },
        { title: '评论时间', dataIndex: 'createTime', key: 'createTime', width: 180 },
        { title: '操作', key: 'actions', width: 100, align: 'center' },
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
        <template v-if="column.key === 'course'">
          {{ courseOptions.find((item) => item.value === record.courseId)?.label ?? record.courseId }}
        </template>
        <template v-else-if="column.key === 'status'">
          <Tag :color="record.status === 2 ? 'red' : 'green'">
            {{ record.status === 2 ? '已删除' : '正常' }}
          </Tag>
        </template>
        <template v-else-if="column.key === 'actions'">
          <Popconfirm
            v-if="record.status !== 2"
            title="确认删除该违规评论？删除后原文保留用于审计"
            @confirm="openDelete(record)"
          >
            <Button type="link" danger size="small">删除</Button>
          </Popconfirm>
          <span v-else class="text-xs text-gray-400">已处理</span>
        </template>
      </template>
    </Table>

    <div class="mt-2 text-xs text-gray-400">
      按方案文档 26：管理员删除违规评论会记录操作人、删除时间与删除原因，原始评论内容保留。
    </div>

    <Modal
      v-model:open="deleteVisible"
      title="删除评论"
      :width="480"
      ok-text="确认删除"
      cancel-text="取消"
      @ok="handleDelete"
    >
      <div class="py-2">
        <div class="mb-1 text-sm text-gray-600">删除原因</div>
        <Input
          v-model:value="deleteForm.reason"
          :maxlength="200"
          placeholder="如：违规内容"
        />
      </div>
    </Modal>
  </div>
</template>
