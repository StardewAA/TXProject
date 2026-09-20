<script lang="ts" setup>
import type { FormType } from '../data';

import type { ErpPurchaseOrderApi } from '#/api/erp/purchase/order';

import { computed, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { Button, message } from 'ant-design-vue';

import { useVbenForm } from '#/adapter/form';
import { getAccountSimpleList } from '#/api/erp/finance/account';
import { getProductSimpleList } from '#/api/erp/product/product';
import {
  createPurchaseOrder,
  exportPurchaseOrderPdf,
  getPurchaseOrder,
  updatePurchaseOrder,
} from '#/api/erp/purchase/order';
import { getSupplierSimpleList } from '#/api/erp/purchase/supplier';
import { $t } from '#/locales';

import { useFormSchema } from '../data';
import PurchaseOrderItemForm from './item-form.vue';

const emit = defineEmits(['success']);
const formData = ref<ErpPurchaseOrderApi.PurchaseOrder>({
  discountPercent: 0,
  items: [],
});
const formType = ref<FormType>('create'); // 表单类型：'create' | 'edit' | 'detail'
const itemFormRef = ref<InstanceType<typeof PurchaseOrderItemForm>>();

const getTitle = computed(() => {
  if (formType.value === 'create') {
    return $t('ui.actionTitle.create', ['采购订单']);
  } else if (formType.value === 'edit') {
    return $t('ui.actionTitle.edit', ['采购订单']);
  } else {
    return '采购订单详情';
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

/** 更新采购订单项 */
function handleUpdateItems(items: ErpPurchaseOrderApi.PurchaseOrderItem[]) {
  // modalApi.getData() contains modal parameters, not the order form model.
  formData.value = {
    ...(formData.value ?? {}),
    items,
  };
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
    await exportPurchaseOrderPdf({ ids: id });
  } catch (error: any) {
    message.error(error?.message || '导出 PDF 失败');
  }
}

/** 创建或更新采购订单 */
const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    const { valid } = await formApi.validate();
    if (!valid) {
      return;
    }
    const itemFormInstance = Array.isArray(itemFormRef.value)
      ? itemFormRef.value[0]
      : itemFormRef.value;
    if (!itemFormInstance) {
      message.error('采购订单明细尚未加载完成，请稍后重试');
      return;
    }
    try {
      itemFormInstance.validate();
    } catch (error: any) {
      message.error(error.message || '子表单验证失败');
      return;
    }

    modalApi.lock();
    // 提交表单
    const data =
      (await formApi.getValues()) as ErpPurchaseOrderApi.PurchaseOrder;
    data.items = formData.value?.items?.map((item) => ({
      ...item,
      // 解决新增采购订单报错
      id: undefined,
    }));
    // 将文件数组转换为字符串
    if (data.fileUrl && Array.isArray(data.fileUrl)) {
      data.fileUrl = data.fileUrl.length > 0 ? data.fileUrl[0] : '';
    }
    try {
      await (formType.value === 'create'
        ? createPurchaseOrder(data)
        : updatePurchaseOrder(data));
      // 关闭并提示
      await modalApi.close();
      emit('success');
      message.success($t('ui.actionMessage.operationSuccess'));
    } catch (error: any) {
      // requestClient may reject with either the response body or an Axios error.
      // Always surface the backend business message instead of the generic 500 text.
      const errorData = error?.response?.data ?? error?.data ?? error;
      message.error(
        errorData?.msg || errorData?.message || error?.message || '采购订单保存失败',
      );
    } finally {
      modalApi.unlock();
    }
  },
  async onOpenChange(isOpen: boolean) {
    if (!isOpen) {
      formData.value = {
        discountPercent: 0,
        items: [],
      };
      return;
    }
    // 加载数据
    const data = modalApi.getData<{ formType: FormType; id?: number }>();
    formType.value = data.formType;
    formApi.setDisabled(formType.value === 'detail');
    formApi.updateSchema(useFormSchema(formType.value));
    if (!data || !data.id) {
      formData.value = {
        discountPercent: 0,
        items: [],
      };
      // 新增时检查基础资料，避免空下拉框最终只显示“系统异常”。
      try {
        const [accountList, supplierList, productList] = await Promise.all([
          getAccountSimpleList(),
          getSupplierSimpleList(),
          getProductSimpleList(),
        ]);
        if (supplierList.length === 0 || productList.length === 0) {
          message.warning('请先维护供应商和启用的产品，再新增采购订单');
        }
        const defaultAccount = accountList.find((item) => item.defaultStatus);
        if (defaultAccount) {
          await formApi.setValues({ accountId: defaultAccount.id });
        }
      } catch (error: any) {
        const errorData = error?.response?.data ?? error?.data ?? error;
        message.error(
          errorData?.msg ||
            errorData?.message ||
            error?.message ||
            '基础资料加载失败，请检查接口服务后重试',
        );
      }
      return;
    }
    modalApi.lock();
    try {
      formData.value = await getPurchaseOrder(data.id);
      // 设置到 values
      await formApi.setValues(formData.value);
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
        <PurchaseOrderItemForm
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
