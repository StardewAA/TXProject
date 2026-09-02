<script lang="ts" setup>
import type { TrainingCourseApi } from '../api';

import { computed, onMounted, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import {
  Button,
  Input,
  InputNumber,
  message,
  Select,
  Table,
  Tag,
} from 'ant-design-vue';

import { $t } from '#/locales';

import {
  createCourseMedia,
  deleteCourseMedia,
  getCourseMediaList,
} from '../api';
import { COURSE_MEDIA_TYPE_OPTIONS, COURSE_MEDIA_TYPE_TEXT } from '../data';

defineOptions({ name: 'TrainingCourseMedia' });

const emit = defineEmits(['success']);
const courseId = ref<number>();
const list = ref<TrainingCourseApi.CourseMedia[]>([]);
const loading = ref(false);

// 新增媒资表单
const mediaType = ref<number>(1);
const name = ref<string>('');
const url = ref<string>('');
const sizeBytes = ref<number | undefined>();

const columns = [
  { title: '类型', dataIndex: 'mediaType', key: 'mediaType', width: 80 },
  { title: '名称', dataIndex: 'name', key: 'name', minWidth: 180 },
  { title: '地址', dataIndex: 'url', key: 'url', ellipsis: true },
  { title: '大小(字节)', dataIndex: 'sizeBytes', key: 'sizeBytes', width: 110 },
  { title: '操作', key: 'action', width: 80 },
];

const getTitle = computed(() => {
  return courseId.value ? `媒资绑定 - 课程 ${courseId.value}` : '媒资绑定';
});

const [Modal, modalApi] = useVbenModal({
  onOpenChange: (isOpen: boolean) => {
    if (isOpen) {
      loadList();
    }
  },
  onConfirm: async () => {
    await modalApi.close();
    emit('success');
  },
});

async function loadList() {
  const data = modalApi.getData<number>();
  if (!data) {
    return;
  }
  courseId.value = data;
  loading.value = true;
  try {
    list.value = await getCourseMediaList(data);
  } finally {
    loading.value = false;
  }
}

async function handleAdd() {
  if (!courseId.value) {
    return;
  }
  if (!name.value.trim() || !url.value.trim()) {
    message.warning('请填写媒资名称与访问地址');
    return;
  }
  await createCourseMedia({
    courseId: courseId.value,
    mediaType: mediaType.value,
    name: name.value.trim(),
    url: url.value.trim(),
    sizeBytes: sizeBytes.value,
  });
  message.success($t('ui.actionMessage.operationSuccess'));
  name.value = '';
  url.value = '';
  sizeBytes.value = undefined;
  await loadList();
  emit('success');
}

async function handleDelete(record: TrainingCourseApi.CourseMedia) {
  await deleteCourseMedia(record.id!);
  message.success($t('ui.actionMessage.deleteSuccess'));
  await loadList();
  emit('success');
}

onMounted(() => {
  loadList();
});
</script>

<template>
  <Modal :title="getTitle" class="w-2/3">
    <div class="mx-4 mb-4 flex items-center gap-2">
      <Select
        v-model:value="mediaType"
        class="!w-24"
        :options="COURSE_MEDIA_TYPE_OPTIONS"
      />
      <Input
        v-model:value="name"
        class="!w-52"
        placeholder="媒资名称，如：工艺规程.pdf"
      />
      <Input v-model:value="url" placeholder="媒资访问地址（资源中心打通前先填可访问 URL）" />
      <InputNumber v-model:value="sizeBytes" class="!w-32" placeholder="大小(字节，选填)" :min="0" />
      <Button type="primary" @click="handleAdd">添加</Button>
    </div>
    <Table
      class="mx-4"
      row-key="id"
      size="small"
      :columns="columns"
      :data-source="list"
      :loading="loading"
      :pagination="false"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'mediaType'">
          <Tag color="blue">{{ COURSE_MEDIA_TYPE_TEXT[record.mediaType] || record.mediaType }}</Tag>
        </template>
        <template v-if="column.key === 'action'">
          <Button type="link" danger size="small" @click="handleDelete(record)">
            删除
          </Button>
        </template>
      </template>
    </Table>
    <div class="mx-4 mt-2 text-xs text-gray-400">
      说明：仅【草稿】状态课程可增删媒资；课程内容类型（文档/视频/图文）随媒资自动汇总。
    </div>
  </Modal>
</template>
