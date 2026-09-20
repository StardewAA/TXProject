<script lang="ts" setup>
import type { CrmAfterSaleVisitRecordApi } from '#/api/crm/after-sale/record';
import type { CrmAfterSaleVisitTaskApi } from '#/api/crm/after-sale/task';

import { onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { Page, useVbenModal } from '@vben/common-ui';
import { useTabs } from '@vben/hooks';

import { Card, Empty } from 'ant-design-vue';

import { getAfterSaleVisitRecordByTaskId } from '#/api/crm/after-sale/record';
import { getAfterSaleVisitTask } from '#/api/crm/after-sale/task';
import { useDescription } from '#/components/description';
import { ACTION_ICON, TableAction } from '#/components/table-action';
import { $t } from '#/locales';

import Form from '../modules/form.vue';
import { useDetailSchema, useRecordSchema } from './data';

const props = defineProps<{ id?: number }>();

const route = useRoute();
const router = useRouter();
const tabs = useTabs();

const loading = ref(false); // 加载中
const taskId = ref(0); // 回访任务编号
const task = ref<CrmAfterSaleVisitTaskApi.VisitTask>({}); // 任务详情
const record = ref<CrmAfterSaleVisitRecordApi.VisitRecord>(); // 关联回访记录（可为空）

const [Descriptions] = useDescription({
  bordered: false,
  column: 3,
  schema: useDetailSchema(),
});

const [RecordDescriptions] = useDescription({
  bordered: false,
  column: 2,
  schema: useRecordSchema(),
});

const [FormModal, formModalApi] = useVbenModal({
  connectedComponent: Form,
  destroyOnClose: true,
});

/** 加载回访任务详情（含关联回访记录，一条任务最多一条记录） */
async function loadDetail() {
  loading.value = true;
  try {
    task.value = await getAfterSaleVisitTask(taskId.value);
    record.value = (await getAfterSaleVisitRecordByTaskId(taskId.value)) ?? undefined;
  } finally {
    loading.value = false;
  }
}

/** 返回回访任务列表 */
function handleBack() {
  tabs.closeCurrentTab();
  router.push('/crm/after-sale/task');
}

/** 编辑回访任务 */
function handleEdit() {
  formModalApi.setData({ id: taskId.value }).open();
}

onMounted(() => {
  taskId.value = Number(props.id || route.params.id);
  loadDetail();
});
</script>

<template>
  <Page
    auto-content-height
    :title="`回访任务详情：${task?.warrantyNo ?? ''} 第 ${task?.bizNo ?? '-'} 次`"
    :loading="loading"
  >
    <FormModal @success="loadDetail" />
    <template #extra>
      <TableAction
        :actions="[
          {
            label: '返回',
            type: 'default',
            icon: 'lucide:arrow-left',
            onClick: handleBack,
          },
          {
            label: $t('ui.actionTitle.edit'),
            type: 'primary',
            icon: ACTION_ICON.EDIT,
            auth: ['crm:after-sale-visit-task:update'],
            // 已完成的任务不允许编辑
            ifShow: task.status !== 2,
            onClick: handleEdit,
          },
        ]"
      />
    </template>
    <Card class="min-h-[10%]">
      <Descriptions :data="task" />
    </Card>
    <Card class="mt-4 min-h-[40%]">
      <template #title>关联回访记录</template>
      <RecordDescriptions v-if="record" :data="record" />
      <Empty v-else description="暂无回访记录（提交回访记录后自动生成）" />
    </Card>
  </Page>
</template>
