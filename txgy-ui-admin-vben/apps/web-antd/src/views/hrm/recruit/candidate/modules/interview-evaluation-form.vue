<script lang="ts" setup>
import { ref } from 'vue';

import { useVbenForm, useVbenModal } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import { evaluateRecruitInterview } from '#/api/hrm/recruit/interview';

const emit = defineEmits(['success']);
const interviewId = ref(0);

const [Form, formApi] = useVbenForm({
  commonConfig: { componentProps: { class: 'w-full' }, labelWidth: 100 },
  layout: 'horizontal',
  schema: [
    {
      fieldName: 'decision',
      label: '评估决定',
      component: 'RadioGroup',
      rules: 'required',
      componentProps: {
        options: [
          { label: '进入下一轮', value: 'NEXT_ROUND' },
          { label: '终面通过', value: 'FINAL_PASS' },
          { label: '面试淘汰', value: 'FAIL' },
        ],
      },
    },
    {
      fieldName: 'evaluate',
      label: '面试评价',
      component: 'Textarea',
      rules: 'required',
      componentProps: { rows: 5, maxlength: 255, showCount: true },
    },
  ],
  showDefaultActions: false,
});

const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    const { valid } = await formApi.validate();
    if (!valid) return;
    modalApi.lock();
    try {
      const values = await formApi.getValues();
      await evaluateRecruitInterview({
        id: interviewId.value,
        decision: values.decision,
        evaluate: values.evaluate,
      });
      await modalApi.close();
      emit('success');
      message.success('面试评估已更新');
    } finally {
      modalApi.unlock();
    }
  },
  async onOpenChange(isOpen: boolean) {
    if (!isOpen) {
      interviewId.value = 0;
      return;
    }
    const data = modalApi.getData<{ id: number }>();
    if (!data?.id) return;
    interviewId.value = data.id;
    await formApi.setValues({ decision: undefined, evaluate: '' });
  },
});
</script>

<template>
  <Modal title="面试评估" class="w-[640px]">
    <Form class="mx-4" />
  </Modal>
</template>
