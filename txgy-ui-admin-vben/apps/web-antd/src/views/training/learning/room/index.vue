<script lang="ts" setup>
import type { TrainingLearningApi } from '../api';

import { onMounted, reactive, ref } from 'vue';

import {
  Button,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Table,
  Tag,
  message,
} from 'ant-design-vue';

import { $t } from '#/locales';

import {
  createLiveRoom,
  deleteLiveRoom,
  getLiveRoomPage,
  updateLiveRoom,
} from '../api';

defineOptions({ name: 'TrainingLearningRoom' });

const ROOM_STATUS_OPTIONS = [
  { label: '未开始', value: 0 },
  { label: '直播中', value: 1 },
  { label: '已结束', value: 2 },
];
const roomStatusText = (status?: number) =>
  ROOM_STATUS_OPTIONS.find((item) => item.value === status)?.label ?? '-';
const roomStatusColor = (status?: number) =>
  status === 1 ? 'green' : status === 2 ? 'default' : 'blue';

const loading = ref(false);
const list = ref<TrainingLearningApi.LiveRoom[]>([]);
const total = ref(0);
const query = reactive({ pageNo: 1, pageSize: 10 });

async function load() {
  loading.value = true;
  try {
    const res = await getLiveRoomPage(query);
    list.value = res?.list ?? [];
    total.value = Number(res?.total ?? 0);
  } finally {
    loading.value = false;
  }
}

const modalVisible = ref(false);
const modalTitle = ref('');
const form = ref<TrainingLearningApi.LiveRoom>({ status: 0, sort: 0 });

function openCreate() {
  modalTitle.value = '新增直播房间';
  form.value = { status: 0, sort: 0 };
  modalVisible.value = true;
}

function openEdit(row: TrainingLearningApi.LiveRoom) {
  modalTitle.value = '编辑直播房间';
  form.value = { ...row };
  modalVisible.value = true;
}

async function handleSave() {
  if (!form.value.title?.trim()) {
    message.warning('请输入直播标题');
    return;
  }
  if (form.value.id) {
    await updateLiveRoom(form.value);
    message.success($t('ui.actionMessage.operationSuccess'));
  } else {
    await createLiveRoom(form.value);
    message.success($t('ui.actionMessage.createSuccess'));
  }
  modalVisible.value = false;
  load();
}

async function handleDelete(row: TrainingLearningApi.LiveRoom) {
  await deleteLiveRoom(row.id!);
  message.success($t('ui.actionMessage.deleteSuccess'));
  load();
}

onMounted(load);
</script>

<template>
  <div>
    <div class="mb-3 flex flex-wrap items-center gap-3">
      <Button type="primary" class="ml-auto" @click="openCreate">
        新增房间
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
        { title: '直播标题', dataIndex: 'title', key: 'title' },
        { title: '讲师', dataIndex: 'lecturerName', key: 'lecturerName', width: 120 },
        { title: '状态', key: 'status', width: 100, align: 'center' },
        { title: '计划开始', dataIndex: 'planStartAt', key: 'planStartAt', width: 180 },
        { title: '在线人数', dataIndex: 'viewerCount', key: 'viewerCount', width: 100, align: 'center' },
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
        <template v-if="column.key === 'status'">
          <Tag :color="roomStatusColor(record.status)">
            {{ roomStatusText(record.status) }}
          </Tag>
        </template>
        <template v-else-if="column.key === 'actions'">
          <Button type="link" size="small" @click="openEdit(record)">
            编辑
          </Button>
          <Popconfirm title="确认删除该直播房间？" @confirm="handleDelete(record)">
            <Button type="link" danger size="small">删除</Button>
          </Popconfirm>
        </template>
      </template>
    </Table>

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
          <div class="mb-1 text-sm text-gray-600">
            直播标题 <span class="text-red-500">*</span>
          </div>
          <Input v-model:value="form.title" :maxlength="100" />
        </div>
        <div class="grid grid-cols-2 gap-3">
          <div>
            <div class="mb-1 text-sm text-gray-600">讲师姓名</div>
            <Input v-model:value="form.lecturerName" :maxlength="30" />
          </div>
          <div>
            <div class="mb-1 text-sm text-gray-600">状态</div>
            <Select
              v-model:value="form.status"
              class="w-full"
              :options="ROOM_STATUS_OPTIONS"
            />
          </div>
        </div>
        <div class="grid grid-cols-2 gap-3">
          <div>
            <div class="mb-1 text-sm text-gray-600">计划开始时间</div>
            <Input
              v-model:value="form.planStartAt"
              placeholder="2026-09-10 14:00:00"
            />
          </div>
          <div>
            <div class="mb-1 text-sm text-gray-600">计划结束时间</div>
            <Input
              v-model:value="form.planEndAt"
              placeholder="2026-09-10 15:00:00"
            />
          </div>
        </div>
        <div>
          <div class="mb-1 text-sm text-gray-600">直播流地址</div>
          <Input v-model:value="form.streamUrl" placeholder="http://..." />
        </div>
        <div>
          <div class="mb-1 text-sm text-gray-600">回放地址</div>
          <Input v-model:value="form.replayVideoUrl" placeholder="http://..." />
        </div>
        <div class="grid grid-cols-2 gap-3">
          <div>
            <div class="mb-1 text-sm text-gray-600">课时（小时）</div>
            <InputNumber v-model:value="form.creditHours" class="w-full" />
          </div>
          <div>
            <div class="mb-1 text-sm text-gray-600">排序</div>
            <InputNumber v-model:value="form.sort" class="w-full" />
          </div>
        </div>
      </div>
    </Modal>
  </div>
</template>