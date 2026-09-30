<script lang="ts" setup>
import { onMounted, ref } from 'vue';

import { Button, Input, Modal, Table, Tag, message } from 'ant-design-vue';

import { $t } from '#/locales';

import { getLearningConfig, updateLearningConfig } from '../api';

defineOptions({ name: 'TrainingLearningConfig' });

const CONFIG_ITEMS = [
  { key: 'danmaku_enabled', label: '直播弹幕开关', hint: 'true / false' },
  { key: 'verify_interval_min', label: '人机验证间隔（分钟）', hint: '如 1' },
  { key: 'heartbeat_interval_seconds', label: '心跳间隔（秒）', hint: '如 5' },
  { key: 'max_delta_seconds', label: '单次心跳合法 delta 上限（秒）', hint: '如 15' },
  { key: 'session_timeout_seconds', label: '心跳超时自动结束（秒）', hint: '如 30' },
  { key: 'doc_min_stay_seconds', label: '文档单页最短停留（秒）', hint: '如 5' },
  { key: 'video_complete_ratio', label: '视频完成判定比例', hint: '如 0.9' },
  { key: 'doc_complete_ratio', label: '文档完成判定比例', hint: '如 0.8' },
  { key: 'min_valid_seconds_checkin', label: '打卡最低有效秒数', hint: '如 15' },
];

const configMap = ref<Record<string, string>>({});

async function load() {
  configMap.value = (await getLearningConfig()) ?? {};
}

const editVisible = ref(false);
const editForm = ref({ key: '', value: '' });

function openEdit(key: string) {
  editForm.value = { key, value: configMap.value[key] ?? '' };
  editVisible.value = true;
}

async function handleSave() {
  await updateLearningConfig(editForm.value);
  message.success($t('ui.actionMessage.operationSuccess'));
  editVisible.value = false;
  load();
}

onMounted(load);
</script>

<template>
  <div>
    <Table
      row-key="key"
      :data-source="CONFIG_ITEMS"
      :pagination="false"
      :columns="[
        { title: '配置项', dataIndex: 'label', key: 'label' },
        { title: '配置键', dataIndex: 'key', key: 'key', width: 240 },
        { title: '当前值', key: 'value', width: 160 },
        { title: '说明', dataIndex: 'hint', key: 'hint', width: 160 },
        { title: '操作', key: 'actions', width: 100, align: 'center' },
      ]"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'value'">
          <Tag color="geekblue">{{ configMap[record.key] ?? '-' }}</Tag>
        </template>
        <template v-else-if="column.key === 'actions'">
          <Button type="link" size="small" @click="openEdit(record.key)">
            修改
          </Button>
        </template>
      </template>
    </Table>

    <Modal
      v-model:open="editVisible"
      :title="`修改配置：${editForm.key}`"
      :width="480"
      ok-text="保存"
      cancel-text="取消"
      @ok="handleSave"
    >
      <div class="py-2">
        <div class="mb-1 text-sm text-gray-600">配置值</div>
        <Input v-model:value="editForm.value" placeholder="请输入配置值" />
      </div>
    </Modal>
  </div>
</template>