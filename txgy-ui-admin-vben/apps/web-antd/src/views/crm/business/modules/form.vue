<script lang="ts" setup>
import ClueQuickParse from '../../clue/modules/ClueQuickParse.vue';
import { dateToTs } from '#/utils/crm/clue-parse';
import { useUserStore } from '@vben/stores';
import type { CrmBusinessApi } from '#/api/crm/business';

import { computed, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';
import { erpPriceMultiply } from '@vben/utils';

import { message } from 'ant-design-vue';

import { useVbenForm } from '#/adapter/form';
import {
  createBusiness,
  getBusiness,
  updateBusiness,
  updateBusinessStatus,
} from '#/api/crm/business';
import { getBusinessStatusSimpleList } from '#/api/crm/business/status';
import { BizTypeEnum } from '#/api/crm/permission';
import { $t } from '#/locales';
import { ProductEditTable } from '#/views/crm/product/components';

import CustomerInfoCard from '#/views/crm/components/customer-info-card.vue';

import { useFormSchema } from '../data';

const emit = defineEmits(['success']);
const formData = ref<CrmBusinessApi.Business>();
/** 当前选中的客户编号：用于展示"客户信息速览"卡片 */
const currentCustomerId = ref<number>();
const getTitle = computed(() => {
  return formData.value?.id
    ? $t('ui.actionTitle.edit', ['商机'])
    : $t('ui.actionTitle.create', ['商机']);
});

function handleUpdateProducts(products: any) {
  formData.value = modalApi.getData<CrmBusinessApi.Business>();
  formData.value!.products = products;
  if (formData.value) {
    const totalProductPrice =
      formData.value.products?.reduce(
        (prev, curr) => prev + curr.totalPrice,
        0,
      ) ?? 0;
    const discountPercent = formData.value.discountPercent;
    const discountPrice =
      discountPercent === null
        ? 0
        : erpPriceMultiply(totalProductPrice, discountPercent / 100);
    const totalPrice = totalProductPrice - (discountPrice ?? 0);
    formData.value!.totalProductPrice = totalProductPrice;
    formData.value!.totalPrice = totalPrice;
    formApi.setValues(formData.value!);
  }
}

const [Form, formApi] = useVbenForm({
  commonConfig: {
    componentProps: {
      class: 'w-full',
    },
    labelWidth: 120,
  },
  wrapperClass: 'grid-cols-3',
  layout: 'vertical',
  schema: useFormSchema(),
  showDefaultActions: false,
  // 选中客户后展示"客户信息速览"卡片（一键引用）
  handleValuesChange(values) {
    currentCustomerId.value = values.customerId;
  },
});

/** 识别结果回填：把名片/文本中的公司与联系方式带入新增表单 */
async function onQuickParse(payload: { fields: any }) {
  const fields = payload.fields ?? {};
  const apply: Record<string, any> = {};
  if (fields.name) apply.name = fields.name;
  if (fields.customerId) apply.customerId = fields.customerId;
  if (fields.statusTypeId) apply.statusTypeId = fields.statusTypeId;
  // 预计成交日期字段为 dealTime，DatePicker 需要毫秒时间戳
  const dealTs = dateToTs(fields.dealTime ?? fields.endDate);
  if (dealTs != null) apply.dealTime = dealTs;
  if (fields.ownerUserId) apply.ownerUserId = fields.ownerUserId;
  if (fields.remark) apply.remark = fields.remark;
  await formApi.setValues(apply);
}

const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    const { valid } = await formApi.validate();
    if (!valid) {
      return;
    }
    modalApi.lock();
    // 提交表单
    const data = (await formApi.getValues()) as CrmBusinessApi.Business;
    data.products = formData.value?.products;
    try {
      if (formData.value?.id) {
        await updateBusiness(data);
      } else {
        const id = await createBusiness(data);
        // 新增时若手动选择了商机阶段，且与默认阶段不同，则同步更新
        if (data.statusId) {
          const list = await getBusinessStatusSimpleList(data.statusTypeId);
          if (list.length > 0 && list[0]?.id !== data.statusId) {
            await updateBusinessStatus({ id, statusId: data.statusId });
          }
        }
      }
      // 关闭并提示
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
    // 加载数据
    const data = modalApi.getData<CrmBusinessApi.Business>();
    if (!data || !data.id) {
      return;
    }
    modalApi.lock();
    try {
      formData.value = data.id ? await getBusiness(data.id) : data;
      // 设置到 values
      await formApi.setValues(formData.value);
    } finally {
      modalApi.unlock();
    }
  },
});
</script>

<template>
  <Modal :title="getTitle" class="w-1/2">
    <ClueQuickParse mode="business" class="mx-4" @apply="onQuickParse" />
    <CustomerInfoCard :customer-id="currentCustomerId" />
    <Form class="mx-4">
      <template #product="slotProps">
        <ProductEditTable
          v-bind="slotProps"
          class="w-full"
          :products="formData?.products ?? []"
          :biz-type="BizTypeEnum.CRM_BUSINESS"
          @update:products="handleUpdateProducts"
        />
      </template>
    </Form>
  </Modal>
</template>
