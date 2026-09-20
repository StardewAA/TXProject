<script lang="ts" setup>
import type { VbenFormSchema } from '#/adapter/form';

import { onMounted, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { Button, message } from 'ant-design-vue';

import { DICT_TYPE } from '@vben/constants';
import { getDictOptions } from '@vben/hooks';

import { useVbenForm as useForm } from '#/adapter/form';
import {
  getAfterSaleConfig,
  updateAfterSaleConfig,
} from '#/api/crm/after-sale/config';
import { $t } from '#/locales';

const submitting = ref(false);

const schema: VbenFormSchema[] = [
  {
    component: 'Input',
    fieldName: 'id',
    dependencies: {
      triggerFields: [''],
      show: () => false,
    },
  },
  {
    component: 'InputNumber',
    fieldName: 'warrantyWarnDays',
    label: '质保到期提前提醒天数',
    rules: 'required',
    componentProps: {
      class: '!w-full',
      min: 7,
      max: 180,
      precision: 0,
      placeholder: '范围 7-180',
    },
  },
  {
    component: 'Switch',
    fieldName: 'autoVisitEnabled',
    label: '自动生成回访计划',
    componentProps: {
      checkedChildren: '开启',
      unCheckedChildren: '关闭',
    },
  },
  {
    component: 'Select',
    fieldName: 'autoVisitCycleType',
    label: '自动回访周期类型',
    componentProps: {
      options: getDictOptions(DICT_TYPE.CRM_AFTER_SALE_VISIT_CYCLE, 'number'),
      placeholder: '请选择自动回访周期类型',
      allowClear: true,
    },
  },
  {
    component: 'InputNumber',
    fieldName: 'autoVisitCycleValue',
    label: '自动回访周期值',
    componentProps: {
      class: '!w-full',
      min: 0,
      precision: 0,
      placeholder: '周期倍数；到期前重点回访时为到期前天数',
    },
  },
  {
    component: 'InputNumber',
    fieldName: 'taskRemindDays',
    label: '任务临期提醒提前天数',
    rules: 'required',
    componentProps: {
      class: '!w-full',
      min: 0,
      precision: 0,
      placeholder: '请输入任务临期提醒提前天数',
    },
  },
  {
    component: 'InputNumber',
    fieldName: 'taskOverdueDays',
    label: '任务逾期判定天数（窗口期天数）',
    rules: 'required',
    componentProps: {
      class: '!w-full',
      min: 0,
      precision: 0,
      placeholder: '窗口期截止日期 = 计划回访日期 + 本值，超出即判定逾期',
    },
  },
  {
    component: 'Switch',
    fieldName: 'allowEarlyVisit',
    label: '允许提前提交回访',
    componentProps: {
      checkedChildren: '允许',
      unCheckedChildren: '不允许',
    },
  },
  {
    component: 'InputNumber',
    fieldName: 'ticketOverdueDays',
    label: '工单超时预警天数',
    rules: 'required',
    componentProps: {
      class: '!w-full',
      min: 1,
      precision: 0,
      placeholder: '请输入工单超时未处理预警天数',
    },
  },
];

const [Form, formApi] = useForm({
  commonConfig: {
    componentProps: {
      class: 'w-full',
    },
  },
  layout: 'horizontal',
  wrapperClass: 'grid-cols-2',
  schema,
  showDefaultActions: false,
});

/** 加载售后配置 */
async function loadConfig() {
  const data = await getAfterSaleConfig();
  if (data) {
    await formApi.setValues(data);
  }
}

/** 保存售后配置 */
async function handleSubmit() {
  const { valid } = await formApi.validate();
  if (!valid) {
    return;
  }
  submitting.value = true;
  try {
    await updateAfterSaleConfig(await formApi.getValues());
    message.success($t('ui.actionMessage.operationSuccess'));
  } finally {
    submitting.value = false;
  }
}

onMounted(() => {
  loadConfig();
});
</script>

<template>
  <Page auto-content-height title="售后设置">
    <div class="p-4">
      <Form />
      <div class="mt-4">
        <Button
          v-access="['crm:after-sale-config:update']"
          type="primary"
          :loading="submitting"
          @click="handleSubmit"
        >
          保存
        </Button>
      </div>
    </div>
  </Page>
</template>
