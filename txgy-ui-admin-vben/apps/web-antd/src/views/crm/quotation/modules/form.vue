<script lang="ts" setup>
import type { CrmQuotationApi } from '#/api/crm/quotation';

import { computed, ref } from 'vue';

import { useVbenForm, useVbenModal } from '@vben/common-ui';
import { erpPriceMultiply } from '@vben/utils';

import { message } from 'ant-design-vue';

import { BizTypeEnum } from '#/api/crm/permission';
import { getProductSimpleList } from '#/api/crm/product';
import {
  createQuotation,
  getQuotation,
  updateQuotation,
} from '#/api/crm/quotation';
import { $t } from '#/locales';
import { dateToTs } from '#/utils/crm/clue-parse';
import { ProductEditTable } from '#/views/crm/product/components';

import ClueQuickParse from '../../clue/modules/ClueQuickParse.vue';
import {
  cacheCustomerNameId,
  getWonCustomerId,
  QUOTATION_TYPE_PURCHASE,
  setKnownSupplierId,
  useFormSchema,
} from '../data';

const emit = defineEmits(['success']);
const formData = ref<CrmQuotationApi.Quotation>();
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

/**
 * 一键识别结果回填：把粘贴的文本 / 名片图片 / 报价单文件里识别到的信息带入表单。
 * 与线索、客户、合同等模块共用同一套「一键识别」面板与回填思路。
 */
async function onQuickParse(payload: { fields: any }) {
  const fields = payload.fields ?? {};
  const apply: Record<string, any> = {};
  // 报价单主信息
  if (fields.name) apply.name = fields.name;
  if (fields.customerName) {
    apply.customerName = fields.customerName;
    // 缓存客户名称与编号，便于提交时还原为已有客户，避免重复建档
    cacheCustomerNameId(fields.customerName, fields.customerId);
  }
  if (fields.businessId) apply.businessId = fields.businessId;
  if (fields.ownerUserId) apply.ownerUserId = fields.ownerUserId;
  // 报价日期 / 有效期至（DatePicker valueFormat:'x'）需转毫秒时间戳，兼容多种日期写法
  const orderTs = dateToTs(fields.orderDate);
  if (orderTs != null) apply.orderDate = orderTs;
  const validTs = dateToTs(fields.validUntil);
  if (validTs != null) apply.validUntil = validTs;
  if (fields.discountPercent != null) {
    apply.discountPercent = fields.discountPercent;
  }
  if (fields.remark) apply.remark = fields.remark;
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
      const count = p.count ?? 1;
      return {
        id: i + 1,
        productId: hit?.id,
        productNo: p.no ?? hit?.no,
        productUnit: p.unit ?? hit?.unit,
        productPrice: hit?.price,
        quotationPrice: price,
        sellingPrice: price,
        count,
        totalPrice: p.totalPrice ?? (price ? price * count : 0),
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
    <ClueQuickParse mode="quotation" class="mx-4" @apply="onQuickParse" />
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
  </Modal>
</template>
