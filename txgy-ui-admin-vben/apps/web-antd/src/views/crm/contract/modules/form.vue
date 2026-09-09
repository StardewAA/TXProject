<script lang="ts" setup>
import ClueQuickParse from '../../clue/modules/ClueQuickParse.vue';
import { dateToTs } from '#/utils/crm/clue-parse';
import { useUserStore } from '@vben/stores';
import type { CrmContractApi } from '#/api/crm/contract';

import { computed, ref } from 'vue';

import { useVbenForm, useVbenModal } from '@vben/common-ui';
import { erpPriceMultiply } from '@vben/utils';

import { message } from 'ant-design-vue';

import {
  createContract,
  getContract,
  updateContract,
} from '#/api/crm/contract';
import { BizTypeEnum } from '#/api/crm/permission';
import { getProductSimpleList } from '#/api/crm/product';
import { $t } from '#/locales';
import { ProductEditTable } from '#/views/crm/product/components';

import { useFormSchema } from '../data';

const emit = defineEmits(['success']);
const formData = ref<CrmContractApi.Contract>();
const getTitle = computed(() => {
  return formData.value?.id
    ? $t('ui.actionTitle.edit', ['合同'])
    : $t('ui.actionTitle.create', ['合同']);
});

/** 更新产品列表 */
function handleUpdateProducts(products: any) {
  formData.value = modalApi.getData<CrmContractApi.Contract>();
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
});

/** 识别结果回填：把名片/文本中的公司与联系方式带入新增表单 */
async function onQuickParse(payload: { fields: any }) {
  const fields = payload.fields ?? {};
  const apply: Record<string, any> = {};
  // 合同主信息
  if (fields.name) apply.name = fields.name;
  if (fields.customerId) apply.customerId = fields.customerId;
  if (fields.businessId) apply.businessId = fields.businessId;
  if (fields.ownerUserId) apply.ownerUserId = fields.ownerUserId;
  if (fields.signUserId) apply.signUserId = fields.signUserId;
  // 合同三个日期(DatePicker valueFormat:'x')需转毫秒时间戳，兼容多种日期写法
  const orderTs = dateToTs(fields.orderDate);
  if (orderTs != null) apply.orderDate = orderTs;
  const startTs = dateToTs(fields.startTime);
  if (startTs != null) apply.startTime = startTs;
  const endTs = dateToTs(fields.endTime);
  if (endTs != null) apply.endTime = endTs;
  if (fields.discountPercent != null) {
    apply.discountPercent = fields.discountPercent;
  }
  if (fields.totalPrice != null) apply.totalPrice = fields.totalPrice;
  if (fields.remark) apply.remark = fields.remark;
  // 签约人：合同无独立字段，写入备注
  const extra: string[] = [];
  if (fields.signContactName) extra.push(`客户签约：${fields.signContactName}`);
  if (fields.signUserName) extra.push(`我方签约：${fields.signUserName}`);
  if (extra.length > 0) {
    apply.remark = [apply.remark, ...extra].filter(Boolean).join('\n');
  }
  await formApi.setValues(apply);

  // 产品明细：匹配系统产品后回填到产品表，并计算合计金额
  if (Array.isArray(fields.products) && fields.products.length > 0) {
    let productOptions: any[] = [];
    try {
      productOptions = await getProductSimpleList();
    } catch {
      productOptions = [];
    }
    const rows = fields.products.map((p: any, i: number) => {
      const hit = productOptions.find(
        (x: any) => (p.no && x.no === p.no) || (p.name && x.name === p.name),
      );
      const price = p.price ?? hit?.price;
      return {
        id: i + 1,
        productId: hit?.id,
        productName: p.name,
        productNo: p.no,
        productUnit: p.unit,
        productPrice: price,
        sellingPrice: price,
        contractPrice: price,
        count: p.count ?? 1,
        totalPrice: p.totalPrice ?? 0,
      };
    });
    formData.value = { ...(formData.value ?? {}), products: rows } as any;
    const totalProductPrice = rows.reduce(
      (sum: number, r: any) => sum + (Number(r.totalPrice) || 0),
      0,
    );
    if (totalProductPrice > 0) {
      apply.totalProductPrice = totalProductPrice;
      const dp = Number(apply.discountPercent ?? 0);
      apply.totalPrice =
        Math.round(totalProductPrice * (1 - dp / 100) * 100) / 100;
      await formApi.setValues(apply);
    }
  }
}

const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    const { valid } = await formApi.validate();
    if (!valid) {
      return;
    }
    modalApi.lock();
    // 提交表单
    const data = (await formApi.getValues()) as CrmContractApi.Contract;
    data.products = formData.value?.products;
    try {
      await (formData.value?.id ? updateContract(data) : createContract(data));
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
    const data = modalApi.getData<CrmContractApi.Contract>();
    if (!data || !data.id) {
      return;
    }
    modalApi.lock();
    try {
      formData.value = await getContract(data.id);
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
    <ClueQuickParse mode="contract" class="mx-4" @apply="onQuickParse" />
    <Form class="mx-4">
      <template #product="slotProps">
        <ProductEditTable
          v-bind="slotProps"
          class="w-full"
          :products="formData?.products ?? []"
          :biz-type="BizTypeEnum.CRM_CONTRACT"
          @update:products="handleUpdateProducts"
        />
      </template>
    </Form>
  </Modal>
</template>
