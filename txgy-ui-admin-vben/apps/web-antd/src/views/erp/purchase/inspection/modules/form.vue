<script lang="ts" setup>
import type { ErpArrivalInspectionApi } from '#/api/erp/purchase/inspection';

import { computed, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import { useVbenForm } from '#/adapter/form';
import { getProductSimpleList } from '#/api/erp/product/product';
import {
  createArrivalInspection,
  getArrivalInspection,
  updateArrivalInspection,
} from '#/api/erp/purchase/inspection';

import { itemResultOptions, useFormSchema } from '../data';

const emit = defineEmits(['success']);
const formData = ref<ErpArrivalInspectionApi.ArrivalInspection>({});
const items = ref<ErpArrivalInspectionApi.Item[]>([]);
const products = ref<any[]>([]);

const getTitle = computed(() => {
  return formData.value?.id ? '编辑到货检验' : '新增到货检验';
});

const [Form, formApi] = useVbenForm({
  commonConfig: {
    componentProps: {
      class: 'w-full',
    },
    labelWidth: 100,
  },
  wrapperClass: 'grid-cols-3',
  layout: 'vertical',
  schema: useFormSchema(),
  showDefaultActions: false,
});

/** 添加检验明细 */
function handleAddItem() {
  items.value.push({
    productId: undefined,
    deliveryCount: 0,
    qualifiedCount: 0,
    unqualifiedCount: 0,
    result: 1,
    remark: '',
  });
}

/** 删除检验明细 */
function handleRemoveItem(index: number) {
  items.value.splice(index, 1);
}

/** 校验明细：产品必填、到货数量大于零、合格数 + 不合格数等于到货数 */
function validateItems(): boolean {
  if (items.value.length === 0) {
    message.warning('请至少添加一条检验明细');
    return false;
  }
  for (const item of items.value) {
    if (!item.productId) {
      message.warning('请选择检验产品');
      return false;
    }
    const deliveryCount = Number(item.deliveryCount ?? 0);
    const qualifiedCount = Number(item.qualifiedCount ?? 0);
    const unqualifiedCount = Number(item.unqualifiedCount ?? 0);
    if (deliveryCount <= 0) {
      message.warning('到货数量必须大于零');
      return false;
    }
    if (Math.abs(qualifiedCount + unqualifiedCount - deliveryCount) > 1e-6) {
      message.warning('合格数量 + 不合格数量必须等于到货数量');
      return false;
    }
  }
  return true;
}

const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    const { valid } = await formApi.validate();
    if (!valid) {
      return;
    }
    if (!validateItems()) {
      return;
    }
    modalApi.lock();
    // 提交表单：表头 + 明细一并提交
    const data =
      (await formApi.getValues()) as ErpArrivalInspectionApi.ArrivalInspection;
    try {
      await (formData.value?.id
        ? updateArrivalInspection({
            ...data,
            id: formData.value.id,
            items: items.value,
          })
        : createArrivalInspection({ ...data, items: items.value }));
      // 关闭并提示
      await modalApi.close();
      emit('success');
      message.success('保存成功');
    } finally {
      modalApi.unlock();
    }
  },
  async onOpenChange(isOpen: boolean) {
    if (!isOpen) {
      formData.value = {};
      items.value = [];
      return;
    }
    modalApi.lock();
    try {
      // 加载产品下拉选项
      products.value = await getProductSimpleList();
      const data = modalApi.getData<{ id?: number }>();
      if (!data || !data.id) {
        // 新增：重置表头，并默认带一条空明细
        formData.value = {};
        items.value = [];
        await formApi.setValues(
          { id: undefined, no: undefined, inspectType: 1, inspectTime: Date.now() },
          false,
        );
        handleAddItem();
        return;
      }
      // 编辑：加载表头 + 明细
      formData.value = await getArrivalInspection(data.id);
      items.value = (formData.value.items ?? []).map((item) => ({ ...item }));
      await formApi.setValues(formData.value, false);
    } finally {
      modalApi.unlock();
    }
  },
});
</script>

<template>
  <Modal :title="getTitle" class="w-4/5" :close-on-click-modal="false">
    <Form class="mx-4" />
    <div class="mx-4 mb-3 flex items-center justify-between">
      <b>检验明细</b>
      <a-button type="primary" @click="handleAddItem">添加明细</a-button>
    </div>
    <a-table
      class="mx-4"
      :data-source="items"
      :pagination="false"
      :scroll="{ x: 1000 }"
      row-key="id"
      bordered
    >
      <a-table-column title="产品" :width="240">
        <template #default="{ record }">
          <a-select
            v-model:value="record.productId"
            show-search
            option-filter-prop="label"
            class="w-full"
            placeholder="请选择产品"
          >
            <a-select-option
              v-for="p in products"
              :key="p.id"
              :value="p.id"
              :label="p.name"
            >
              {{ p.name }}（{{ p.barCode }}）
            </a-select-option>
          </a-select>
        </template>
      </a-table-column>
      <a-table-column title="到货数量" :width="150">
        <template #default="{ record }">
          <a-input-number
            v-model:value="record.deliveryCount"
            :min="0"
            class="w-full"
          />
        </template>
      </a-table-column>
      <a-table-column title="合格数量" :width="150">
        <template #default="{ record }">
          <a-input-number
            v-model:value="record.qualifiedCount"
            :min="0"
            class="w-full"
          />
        </template>
      </a-table-column>
      <a-table-column title="不合格数量" :width="150">
        <template #default="{ record }">
          <a-input-number
            v-model:value="record.unqualifiedCount"
            :min="0"
            class="w-full"
          />
        </template>
      </a-table-column>
      <a-table-column title="检验结论" :width="140">
        <template #default="{ record }">
          <a-select
            v-model:value="record.result"
            :options="itemResultOptions"
            class="w-full"
            placeholder="请选择检验结论"
          />
        </template>
      </a-table-column>
      <a-table-column title="备注">
        <template #default="{ record }">
          <a-input v-model:value="record.remark" placeholder="请输入备注" />
        </template>
      </a-table-column>
      <a-table-column title="操作" :width="80">
        <template #default="{ index }">
          <a-button type="link" danger @click="handleRemoveItem(index)">
            删除
          </a-button>
        </template>
      </a-table-column>
    </a-table>
  </Modal>
</template>
