<script lang="ts" setup>
import type { TrainingCourseApi } from '../api';

import { onMounted, ref } from 'vue';

import { Button, Popconfirm, Select, Table, Tag, message } from 'ant-design-vue';

import { getCoursePage, getCourseVersionList, publishCourseVersion } from '../api';
import { COURSE_STATUS_OPTIONS } from '../data';

defineOptions({ name: 'TrainingCourseVersion' });

const statusText = (status?: number) =>
  COURSE_STATUS_OPTIONS.find((item) => item.value === status)?.label ?? '-';

const courseOptions = ref<{ label: string; value: number }[]>([]);
const selectedCourseId = ref<number | undefined>();

const loading = ref(false);
const list = ref<TrainingCourseApi.Course[]>([]);

async function loadCourseOptions() {
  const res = await getCoursePage({ pageNo: 1, pageSize: 100 });
  courseOptions.value = (res?.list ?? []).map((item) => ({
    label: `${item.title ?? ''}（v${item.version ?? 1}${item.isCurrent ? ' · 当前' : ''}）`,
    value: item.id!,
  }));
}

async function loadVersions() {
  if (!selectedCourseId.value) {
    message.warning('请先选择课程');
    return;
  }
  loading.value = true;
  try {
    list.value = (await getCourseVersionList(selectedCourseId.value)) ?? [];
  } finally {
    loading.value = false;
  }
}

async function handlePublish() {
  if (!selectedCourseId.value) {
    message.warning('请先选择课程');
    return;
  }
  await publishCourseVersion(selectedCourseId.value);
  message.success('已发布新版本，旧版本保留且历史学习记录关联旧版本');
  await loadCourseOptions();
  await loadVersions();
}

onMounted(loadCourseOptions);
</script>

<template>
  <div>
    <div class="mb-3 flex flex-wrap items-center gap-3">
      <span class="text-sm text-gray-500">课程</span>
      <Select
        v-model:value="selectedCourseId"
        class="!w-96"
        show-search
        option-filter-prop="label"
        placeholder="请选择课程"
        :options="courseOptions"
        @change="loadVersions"
      />
      <Button type="primary" ghost :loading="loading" @click="loadVersions">
        查询版本
      </Button>
      <Popconfirm
        title="发布新版本后，当前版本将置为历史版本并生成新的草稿版本，确认继续？"
        @confirm="handlePublish"
      >
        <Button type="primary" class="ml-auto">发布新版本</Button>
      </Popconfirm>
    </div>

    <Table
      row-key="id"
      :data-source="list"
      :loading="loading"
      :pagination="false"
      :columns="[
        { title: '版本', dataIndex: 'version', key: 'version', width: 90, align: 'center' },
        { title: '课程标题', dataIndex: 'title', key: 'title' },
        { title: '课程编码', dataIndex: 'code', key: 'code', width: 150 },
        { title: '状态', key: 'status', width: 110, align: 'center' },
        { title: '是否当前版本', key: 'isCurrent', width: 130, align: 'center' },
        { title: '更新时间', dataIndex: 'updateTime', key: 'updateTime', width: 180 },
      ]"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'status'">
          <Tag :color="record.status === 3 ? 'green' : 'default'">
            {{ statusText(record.status) }}
          </Tag>
        </template>
        <template v-else-if="column.key === 'isCurrent'">
          <Tag :color="record.isCurrent ? 'blue' : 'default'">
            {{ record.isCurrent ? '当前版本' : '历史版本' }}
          </Tag>
        </template>
      </template>
    </Table>

    <div class="mt-2 text-xs text-gray-400">
      按方案文档 25：课程更新生成新版本记录，旧版本保留，历史学员学习记录继续关联旧版本，
      新版本用于后续新的培训班选用。<br />
      版本联动：新版本生成后为草稿状态，需在「课程管理」列表提交审核、经「审核管理」通过并上架；
      课程列表默认展示当前版本（可切换「全部版本 / 仅历史版本」查看新旧版本），
      审核管理的待审核队列同样显示课程版本号。
    </div>
  </div>
</template>
