<script lang="ts" setup>
import type { FormType } from '../data';

import type { ErpSaleOrderApi } from '#/api/erp/sale/order';

import { computed, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { Button, message } from 'ant-design-vue';

import { useVbenForm } from '#/adapter/form';
import { getAccountSimpleList } from '#/api/erp/finance/account';
import {
  createSaleOrder,
  exportSaleOrderPdf,
  getSaleOrder,
  updateSaleOrder,
} from '#/api/erp/sale/order';
import { $t } from '#/locales';

import { assistantDemoData } from '../../../home/modules/assistant-demo-data';
import { useFormSchema } from '../data';
import ItemForm from './item-form.vue';

const emit = defineEmits(['success']);
const formData = ref<ErpSaleOrderApi.SaleOrder>();
const formType = ref<FormType>('create'); // 表单类型：'create' | 'edit' | 'detail'
const itemFormRef = ref<InstanceType<typeof ItemForm>>();

function getDemoSaleOrder(id: number): ErpSaleOrderApi.SaleOrder | undefined {
  const item = assistantDemoData.sales.find((order) => order.id === id);
  if (!item) return;
  return {
    id: item.id,
    no: item.no,
    customerId: item.customerId,
    customerName: item.customer,
    orderTime: new Date(item.orderTime),
    totalCount: item.quantity,
    totalPrice: item.amount,
    totalProductPrice: item.amount,
    status: item.statusCode,
    remark: '脱敏联调订单，仅用于 AI 查询和销售订单页面演示',
    outCount: 0,
    returnCount: 0,
    discountPercent: 0,
    discountPrice: 0,
    depositPrice: 0,
    productNames: item.product,
    creatorName: '脱敏演示账号',
    createTime: item.orderTime,
    items: [
      {
        id: item.id * 10,
        orderId: item.id,
        productId: item.productId,
        productName: item.product,
        productPrice: item.amount / item.quantity,
        totalProductPrice: item.amount,
        count: item.quantity,
        totalPrice: item.amount,
        taxPercent: 0,
        taxPrice: 0,
        productUnitName: item.unit,
      },
    ],
  };
}

const getTitle = computed(() => {
  if (formType.value === 'create') {
    return $t('ui.actionTitle.create', ['销售订单']);
  } else if (formType.value === 'edit') {
    return $t('ui.actionTitle.edit', ['销售订单']);
  } else {
    return '销售订单详情';
  }
});

const [Form, formApi] = useVbenForm({
  commonConfig: {
    componentProps: {
      class: 'w-full',
    },
    labelWidth: 120,
  },
  wrapperClass: 'grid-cols-3',
  layout: 'vertical',
  schema: useFormSchema(formType.value),
  showDefaultActions: false,
  handleValuesChange: (values, changedFields) => {
    // 目的：同步到 item-form 组件，触发整体的价格计算
    if (formData.value && changedFields.includes('discountPercent')) {
      formData.value.discountPercent = values.discountPercent;
    }
  },
});

/** 更新销售订单项 */
function handleUpdateItems(items: ErpSaleOrderApi.SaleOrderItem[]) {
  formData.value = modalApi.getData<ErpSaleOrderApi.SaleOrder>();
  formData.value.items = items;
  formApi.setValues({
    items,
  });
}

/** 更新优惠金额 */
function handleUpdateDiscountPrice(discountPrice: number) {
  formApi.setValues({
    discountPrice,
  });
}

/** 更新总金额 */
function handleUpdateTotalPrice(totalPrice: number) {
  formApi.setValues({
    totalPrice,
  });
}

/** 一键导出当前单据 PDF（无需在列表中勾选） */
async function handleExportPdf() {
  const id = formData.value?.id;
  if (!id) {
    message.warning('单据未加载，无法导出');
    return;
  }
  try {
    await exportSaleOrderPdf({ ids: id });
  } catch (error: any) {
    message.error(error?.message || '导出 PDF 失败');
  }
}

/** 创建或更新销售订单 */
const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    const { valid } = await formApi.validate();
    if (!valid) {
      return;
    }
    const itemFormInstance = Array.isArray(itemFormRef.value)
      ? itemFormRef.value[0]
      : itemFormRef.value;
    try {
      itemFormInstance.validate();
    } catch (error: any) {
      message.error(error.message || '子表单验证失败');
      return;
    }

    modalApi.lock();
    // 提交表单
    const data = (await formApi.getValues()) as ErpSaleOrderApi.SaleOrder;
    try {
      await (formType.value === 'create'
        ? createSaleOrder(data)
        : updateSaleOrder(data));
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
    const data = modalApi.getData<{ formType: FormType; id?: number }>();
    formType.value = data.formType;
    formApi.setDisabled(formType.value === 'detail');
    formApi.updateSchema(useFormSchema(formType.value));
    if (!data || !data.id) {
      // 新增时，默认选中账户
      const accountList = await getAccountSimpleList();
      const defaultAccount = accountList.find((item) => item.defaultStatus);
      if (defaultAccount) {
        await formApi.setValues({ accountId: defaultAccount.id });
      }
      return;
    }
    modalApi.lock();
    try {
      formData.value =
        data.formType === 'detail' && data.id >= 960_000
          ? getDemoSaleOrder(data.id)
          : await getSaleOrder(data.id);
      if (!formData.value) return;
      // 设置到 values
      await formApi.setValues({
        ...formData.value,
        orderTime:
          formData.value.orderTime instanceof Date
            ? formData.value.orderTime.getTime()
            : formData.value.orderTime,
      });
    } finally {
      modalApi.unlock();
    }
  },
});
</script>

<template>
  <Modal
    :title="getTitle"
    class="w-3/4"
    :show-confirm-button="formType !== 'detail'"
  >
    <Form class="mx-3">
      <template #items>
        <ItemForm
          ref="itemFormRef"
          :items="formData?.items ?? []"
          :disabled="formType === 'detail'"
          :discount-percent="formData?.discountPercent ?? 0"
          @update:items="handleUpdateItems"
          @update:discount-price="handleUpdateDiscountPrice"
          @update:total-price="handleUpdateTotalPrice"
        />
      </template>
      <template #exportPdf>
        <Button type="primary" @click="handleExportPdf">导出PDF</Button>
      </template>
    </Form>
  </Modal>
</template>
