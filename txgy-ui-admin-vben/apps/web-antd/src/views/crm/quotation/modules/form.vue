<script lang="ts" setup>
import type { CrmQuotationApi } from '#/api/crm/quotation';

import { computed, ref } from 'vue';

import { useVbenForm, useVbenModal } from '@vben/common-ui';
import { erpPriceMultiply } from '@vben/utils';

import { Button, message } from 'ant-design-vue';

import { BizTypeEnum } from '#/api/crm/permission';
import {
  createQuotation,
  getQuotation,
  updateQuotation,
} from '#/api/crm/quotation';
import { $t } from '#/locales';
import { ProductEditTable } from '#/views/crm/product/components';

import {
  cacheCustomerNameId,
  getWonCustomerId,
  QUOTATION_TYPE_PURCHASE,
  setKnownSupplierId,
  useFormSchema,
} from '../data';
import SmartParseModal from '../components/smart-parse-modal.vue';

const emit = defineEmits(['success']);
const formData = ref<CrmQuotationApi.Quotation>();
const smartParseRef = ref<InstanceType<typeof SmartParseModal> | null>(null);
const getTitle = computed(() => {
  return formData.value?.id
    ? $t('ui.actionTitle.edit', ['报价单'])
    : $t('ui.actionTitle.create', ['报价单']);
});

/** 更新产品列表 */
function handleUpdateProducts(products: any) {
  formData.value = modalApi.getData<CrmQuotationApi.Quotation>();
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

/** 一键识别解析完成后填充表单 */
async function handleParseSuccess(values: Partial<CrmQuotationApi.Quotation>) {
  formData.value = {
    ...(formData.value || {}),
    ...values,
  } as CrmQuotationApi.Quotation;
  const products = formData.value.products ?? [];
  const totalProductPrice = products.reduce(
    (sum, item) => sum + (item.totalPrice || 0),
    0,
  );
  const discountPercent = formData.value.discountPercent ?? 0;
  const discountPrice =
    erpPriceMultiply(totalProductPrice, discountPercent / 100) ?? 0;
  formData.value.totalProductPrice = totalProductPrice;
  formData.value.totalPrice = totalProductPrice - discountPrice;
  // 缓存解析出的客户名称与编号，便于提交时还原
  cacheCustomerNameId(formData.value.customerName, formData.value.customerId);
  await formApi.setValues(formData.value);
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
});

const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    const { valid } = await formApi.validate();
    if (!valid) {
      return;
    }
    modalApi.lock();
    // 提交表单
    const data = (await formApi.getValues()) as CrmQuotationApi.Quotation;
    data.products = formData.value?.products;
    if (Number(data.quotationType) === QUOTATION_TYPE_PURCHASE) {
      // 采购报价：往来单位即所选「上游供应商」，且不关联商机
      data.customerId = data.supplierId as number;
      data.customerName = undefined;
      data.businessId = undefined;
    } else {
      // 购销报价：匹配到赢单客户则关联编号；否则作为新客户名称，交由后端自动创建客户
      const customerName = (data.customerName ?? '').toString().trim();
      const matchedCustomerId = getWonCustomerId(customerName);
      if (matchedCustomerId) {
        data.customerId = matchedCustomerId;
        data.customerName = undefined;
      } else {
        data.customerId = undefined as any;
      }
      // 购销报价不使用供应商信息
      data.supplierContact = undefined;
      data.supplierPhone = undefined;
      data.supplierAddress = undefined;
    }
    // supplierId 仅为表单交互字段，不提交给后端
    delete (data as any).supplierId;
    try {
      await (formData.value?.id
        ? updateQuotation(data)
        : createQuotation(data));
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
    const data = modalApi.getData<CrmQuotationApi.Quotation>();
    if (!data || !data.id) {
      return;
    }
    modalApi.lock();
    try {
      formData.value = await getQuotation(data.id);
      // 缓存当前客户，避免编辑时把已有关联客户误判为新客户
      cacheCustomerNameId(formData.value.customerName, formData.value.customerId);
      // 采购报价：往来单位存于 customerId，回填到「供应商」表单项
      const editValues: Record<string, any> = { ...formData.value };
      if (Number(editValues.quotationType) === QUOTATION_TYPE_PURCHASE) {
        editValues.supplierId = formData.value.customerId;
      }
      // 先标记已知供应商，避免回显时用供应商档案覆盖报价单上已保存的联系人/电话/地址
      setKnownSupplierId(editValues.supplierId);
      // 设置到 values
      await formApi.setValues(editValues);
    } finally {
      modalApi.unlock();
    }
  },
});
</script>

<template>
  <Modal :title="getTitle" class="w-1/2">
    <div class="mb-4 flex justify-end">
      <Button type="primary" @click="smartParseRef?.open()">
        一键识别
      </Button>
    </div>
    <Form class="mx-4">
      <template #product="slotProps">
        <ProductEditTable
          v-bind="slotProps"
          class="w-full"
          :products="formData?.products ?? []"
          :biz-type="BizTypeEnum.CRM_QUOTATION"
          @update:products="handleUpdateProducts"
        />
      </template>
    </Form>
    <SmartParseModal ref="smartParseRef" @success="handleParseSuccess" />
  </Modal>
</template>
