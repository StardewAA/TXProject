<script lang="ts" setup>
import type { HrmSandboxApi } from '#/api/hrm/sandbox';

import { computed, onMounted, ref } from 'vue';

import { useAccess } from '@vben/access';
import { confirm } from '@vben/common-ui';
import { formatDateTime } from '@vben/utils';

import { Button, Card, Empty, message, Spin, Tag } from 'ant-design-vue';

import {
  clearSandbox,
  generateSandboxDemo,
  getSandboxRecordList,
} from '#/api/hrm/sandbox';

/**
 * 沙箱演示数据面板（I-025）
 *
 * <p>实施/培训前点「生成演示数据」准备一套最小剧本（草稿职位 + 演示请假单），
 * 培训结束点「一键清场」把演示数据清干净，避免污染正式台账。</p>
 */
defineOptions({ name: 'HrmHomeSandboxPanel' });

const { hasAccessByCodes } = useAccess();
const canManage = computed(() => hasAccessByCodes(['hrm:employee:update']));

const loading = ref(false);
const generating = ref(false);
const clearing = ref(false);
const records = ref<HrmSandboxApi.SandboxRecord[]>([]);

const bizTypeMeta: Record<string, { color: string; label: string }> = {
  leave: { color: 'blue', label: '请假单' },
  post: { color: 'green', label: '招聘职位' },
};

async function load() {
  loading.value = true;
  try {
    records.value = (await getSandboxRecordList()) ?? [];
  } finally {
    loading.value = false;
  }
}

async function handleGenerate() {
  generating.value = true;
  try {
    const count = await generateSandboxDemo();
    message.success(`已生成 ${count} 条演示数据，并登记进沙箱清单`);
    await load();
  } finally {
    generating.value = false;
  }
}

async function handleClear() {
  await confirm({
    content: '确认清场吗？沙箱清单里的演示数据会被删除（软删除，可恢复）。',
    title: '清场确认',
  });
  clearing.value = true;
  try {
    const count = await clearSandbox();
    message.success(`已清场 ${count} 条演示数据`);
    await load();
  } finally {
    clearing.value = false;
  }
}

onMounted(() => {
  if (canManage.value) {
    load();
  }
});
</script>

<template>
  <Card v-if="canManage" title="沙箱演示数据">
    <Spin :spinning="loading">
      <div class="text-muted-foreground mb-3 text-xs">
        用于实施/培训演示：生成的最小剧本只包含「草稿职位」和「演示请假单」，不新建部门与后台账号；
        演示结束可一键清场。
      </div>
      <div class="mb-3 flex gap-2">
        <Button :loading="generating" type="primary" @click="handleGenerate">
          生成演示数据
        </Button>
        <Button
          danger
          :disabled="records.length === 0"
          :loading="clearing"
          @click="handleClear"
        >
          一键清场（{{ records.length }}）
        </Button>
      </div>
      <div v-if="records.length" class="flex max-h-[220px] flex-col gap-2 overflow-auto">
        <div
          v-for="record in records"
          :key="record.id"
          class="flex items-center gap-2 text-sm"
        >
          <Tag :color="bizTypeMeta[record.bizType || '']?.color">
            {{ bizTypeMeta[record.bizType || '']?.label || record.bizType }}
          </Tag>
          <span class="min-w-0 flex-1 truncate">{{ record.bizName }}</span>
          <span class="text-muted-foreground text-xs">
            {{ formatDateTime(record.createTime) }}
          </span>
        </div>
      </div>
      <Empty v-else description="暂无沙箱演示数据" />
    </Spin>
  </Card>
</template>
