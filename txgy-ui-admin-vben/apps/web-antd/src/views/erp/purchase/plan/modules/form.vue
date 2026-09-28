<script lang="ts" setup>
import type { ErpProductApi } from '#/api/erp/product/product';
import type { ErpPurchasePlanApi } from '#/api/erp/purchase/plan';

import { computed, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import { useVbenForm } from '#/adapter/form';
import { getProductSimpleList } from '#/api/erp/product/product';
import {
  createPurchasePlan,
  getPurchasePlan,
  updatePurchasePlan,
} from '#/api/erp/purchase/plan';
import { $t } from '#/locales';

import { useFormSchema } from '../data';

const emit = defineEmits(['success']);
const formData = ref<ErpPurchasePlanApi.PurchasePlan>();
const products = ref<ErpProductApi.Product[]>([]);
/** 采购计划明细，随表头一起提交 */
const items = ref<ErpPurchasePlanApi.Item[]>([]);
const getTitle = computed(() => {
  return formData.value?.id
    ? $t('ui.actionTitle.edit', ['采购计划'])
    : $t('ui.actionTitle.create', ['采购计划']);
});

const [Form, formApi] = useVbenForm({
  commonConfig: {
    componentProps: {
      class: 'w-full',
    },
    labelWidth: 100,
  },
  wrapperClass: 'grid-cols-3',
  layout: 'horizontal',
  schema: useFormSchema(),
  showDefaultActions: false,
});

/** 添加一行明细 */
function addItem() {
  items.value.push({
    productId: undefined,
    productUnitId: undefined,
    count: 1,
    targetPrice: 0,
    amount: 0,
    requiredDate: undefined,
    remark: '',
  });
}

/** 删除一行明细 */
function removeItem(index: number) {
  items.value.splice(index, 1);
}

/** 选择产品后，联动带出产品单位 */
function productChanged(item: ErpPurchasePlanApi.Item, value: any) {
  item.productId = value;
  const product = products.value.find((p) => p.id === item.productId);
  item.productUnitId = product?.unitId;
  item.amount = calcAmount(item);
}

/** 产品单位名称 */
function getUnitName(productId?: number) {
  return products.value.find((p) => p.id === productId)?.unitName ?? '-';
}

/** 按「数量 * 参考单价」计算金额 */
function calcAmount(item: ErpPurchasePlanApi.Item) {
  const count = Number(item.count ?? 0);
  const targetPrice = Number(item.targetPrice ?? 0);
  return Number((count * targetPrice).toFixed(2));
}

/** 数量变化时，联动计算金额 */
function handleCountChange(item: ErpPurchasePlanApi.Item, value: any) {
  item.count = value === null || value === undefined ? undefined : Number(value);
  item.amount = calcAmount(item);
}

/** 参考单价变化时，联动计算金额 */
function handlePriceChange(item: ErpPurchasePlanApi.Item, value: any) {
  item.targetPrice =
    value === null || value === undefined ? undefined : Number(value);
  item.amount = calcAmount(item);
}

const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    const { valid } = await formApi.validate();
    if (!valid) {
      return;
    }
    if (items.value.length === 0) {
      message.warning('请至少添加一条采购计划明细');
      return;
    }
    if (items.value.some((item) => !item.productId || !item.count)) {
      message.warning('请完整填写明细的产品和计划数量');
      return;
    }
    modalApi.lock();
    // 提交表单
    const data =
      (await formApi.getValues()) as ErpPurchasePlanApi.PurchasePlan;
    data.items = items.value.map((item) => ({
      ...item,
      amount: calcAmount(item),
    }));
    try {
      await (formData.value?.id
        ? updatePurchasePlan(data)
        : createPurchasePlan(data));
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
      items.value = [];
      return;
    }
    formData.value = undefined;
    items.value = [];
    modalApi.lock();
    try {
      // 加载产品下拉数据
      products.value = await getProductSimpleList();
      // 加载数据
      const data = modalApi.getData<ErpPurchasePlanApi.PurchasePlan>();
      if (!data || !data.id) {
        // 新增时，默认编制时间为当前时间
        await formApi.setValues({ planTime: Date.now(), budgetAmount: 0 });
        addItem();
        return;
      }
      formData.value = await getPurchasePlan(data.id);
      // 设置到 values
      await formApi.setValues(formData.value);
      items.value = (formData.value.items ?? []).map((item) => ({ ...item }));
      if (items.value.length === 0) {
        addItem();
      }
    } finally {
      modalApi.unlock();
    }
  },
});
</script>

<template>
  <Modal :title="getTitle" class="w-4/5">
    <Form class="mx-4" />
    <div class="mx-4 mb-2 flex items-center justify-between">
      <b>采购计划明细</b>
      <a-button type="primary" @click="addItem">添加明细</a-button>
    </div>
    <a-table
      class="mx-4"
      :data-source="items"
      :pagination="false"
      :scroll="{ x: 1100 }"
      :row-key="(_record: any, index: number) => String(index)"
      bordered
      size="small"
    >
      <a-table-column title="产品" :width="240">
        <template #default="{ record }">
          <a-select
            v-model:value="record.productId"
            class="w-full"
            option-filter-prop="label"
            placeholder="请选择产品"
            show-search
            @change="productChanged(record, $event)"
          >
            <a-select-option
              v-for="p in products"
              :key="p.id"
              :label="p.name"
              :value="p.id"
            >
              {{ p.name }}（{{ p.barCode }}）
            </a-select-option>
          </a-select>
        </template>
      </a-table-column>
      <a-table-column title="单位" :width="90">
        <template #default="{ record }">
          {{ getUnitName(record.productId) }}
        </template>
      </a-table-column>
      <a-table-column title="计划数量" :width="120">
        <template #default="{ record }">
          <a-input-number
            v-model:value="record.count"
            class="w-full"
            :min="0.001"
            @change="handleCountChange(record, $event)"
          />
        </template>
      </a-table-column>
      <a-table-column title="参考单价" :width="130">
        <template #default="{ record }">
          <a-input-number
            v-model:value="record.targetPrice"
            class="w-full"
            :min="0"
            @change="handlePriceChange(record, $event)"
          />
        </template>
      </a-table-column>
      <a-table-column title="金额" :width="120">
        <template #default="{ record }">
          {{ (record.amount ?? 0).toFixed(2) }}
        </template>
      </a-table-column>
      <a-table-column title="需求日期" :width="180">
        <template #default="{ record }">
          <a-date-picker
            v-model:value="record.requiredDate"
            class="w-full"
            show-time
            value-format="x"
          />
        </template>
      </a-table-column>
      <a-table-column title="备注" :width="160">
        <template #default="{ record }">
          <a-input v-model:value="record.remark" placeholder="请输入备注" />
        </template>
      </a-table-column>
      <a-table-column title="操作" :width="80">
        <template #default="{ index }">
          <a-button type="link" danger @click="removeItem(index)">删除</a-button>
        </template>
      </a-table-column>
    </a-table>
  </Modal>
</template>
