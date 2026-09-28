<script lang="ts" setup>
import type { TrainingPlanApi } from '../api';

import { computed, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import { useVbenForm } from '#/adapter/form';
import { $t } from '#/locales';

import { getCoursePage } from '../../course/api';
import { createClass, getClass, updateClass } from '../api';
import { buildTargetCondition, parseTargetCondition } from '../data';
import { useFormSchema } from './form-data';

defineOptions({ name: 'TrainingPlanForm' });

const emit = defineEmits(['success']);
const formData = ref<TrainingPlanApi.Class>();
const courseOptions = ref<{ label: string; value: number }[]>([]);

const getTitle = computed(() => {
  return formData.value?.id
    ? $t('ui.actionTitle.edit', ['培训班'])
    : $t('ui.actionTitle.create', ['培训班']);
});

const [Form, formApi] = useVbenForm({
  commonConfig: {
    componentProps: {
      class: 'w-full',
    },
    labelWidth: 130,
  },
  layout: 'vertical',
  schema: useFormSchema(courseOptions.value),
  showDefaultActions: false,
});

/** 加载已上架课程下拉 */
async function loadCourseOptions() {
  const data = await getCoursePage({
    pageNo: 1,
    pageSize: 200,
    isCurrent: true,
    status: 3,
  });
  courseOptions.value = (data?.list ?? []).map((item) => ({
    value: item.id!,
    label: `${item.code ? `[${item.code}] ` : ''}${item.title}`,
  }));
  formApi.updateSchema([
    {
      fieldName: 'courseIds',
      componentProps: { options: courseOptions.value },
    },
  ]);
}

const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    const { valid } = await formApi.validate();
    if (!valid) {
      return;
    }
    modalApi.lock();
    try {
      const raw = (await formApi.getValues()) as Record<string, any>;
      const data: Partial<TrainingPlanApi.Class> = {
        id: formData.value?.id,
        name: raw.name,
        type: raw.type,
        projectId: raw.projectId || undefined,
        projectName: raw.projectName || undefined,
        courseIds: raw.courseIds,
        targetCondition: buildTargetCondition(raw),
        targetPosts: Array.isArray(raw.targetPostsText)
          ? JSON.stringify(raw.targetPostsText)
          : undefined,
        startTime: raw.startTime || undefined,
        deadline: raw.deadline || undefined,
        remark: raw.remark || undefined,
      };
      await (data.id ? updateClass(data) : createClass(data));
      await modalApi.close();
      emit('success');
      message.success($t('ui.actionMessage.operationSuccess'));
    } finally {
      modalApi.unlock();
    }
  },
  async onOpenChange(isOpen: boolean) {
    if (!isOpen) {
      formData.value = undefined;
      return;
    }
    await loadCourseOptions();
    const data = modalApi.getData<TrainingPlanApi.Class>();
    if (!data || !data.id) {
      formData.value = data;
      return;
    }
    modalApi.lock();
    try {
      const detail = await getClass(data.id);
      formData.value = detail;
      let targetPostsText: string[] = [];
      if (detail.targetPosts) {
        try {
          const parsed = JSON.parse(detail.targetPosts);
          targetPostsText = Array.isArray(parsed) ? parsed : [];
        } catch {
          targetPostsText = [];
        }
      }
      const scope = parseTargetCondition(detail.targetCondition);
      await formApi.setValues({
        ...detail,
        targetPostsText,
        courseIds: detail.courseIds,
        scopeDeptIds: scope.deptIds,
        scopeIncludeChildDept: scope.includeChildDept,
        scopePostIds: scope.postIds,
      });
    } finally {
      modalApi.unlock();
    }
  },
});
</script>

<template>
  <Modal :title="getTitle" class="w-2/3">
    <Form class="mx-4" />
  </Modal>
</template>
