<script lang="ts" setup>
import { computed, reactive, ref } from 'vue';
import { useVbenModal } from '@vben/common-ui';
import { message } from 'ant-design-vue';
import {
  createCostResult,
  getCostResult,
  updateCostResult,
} from '#/api/erp/sale/cost';
import { getProductSimpleList } from '#/api/erp/product/product';
const emit = defineEmits(['success']);
const products = ref<any[]>([]);
const editing = ref(false);
const detail = ref(false);
const form = reactive<any>({ costVersion: 1, currency: 'CNY', items: [] });
const title = computed(() =>
  detail.value
    ? '造价结果详情'
    : editing.value
      ? '编辑造价结果'
      : '新增造价结果',
);
const add = () =>
  form.items.push({
    lineNo: form.items.length + 1,
    materialName: '',
    count: 1,
    unitPrice: 0,
    mappingStatus: 0,
  });
function changed(i: any) {
  const p = products.value.find((x) => x.id === i.productId);
  if (p) {
    i.productUnitId = p.unitId;
    i.mappingStatus = 20;
  }
}
const [Modal, api] = useVbenModal({
  async onConfirm() {
    if (detail.value) {
      await api.close();
      return;
    }
    if (
      !form.name ||
      !form.costVersion ||
      !form.items.length ||
      form.items.some((i: any) => !i.materialName)
    ) {
      message.warning('请填写完整造价项目和材料明细');
      return;
    }
    api.lock();
    try {
      await (editing.value ? updateCostResult(form) : createCostResult(form));
      await api.close();
      message.success('保存成功');
      emit('success');
    } finally {
      api.unlock();
    }
  },
  async onOpenChange(o) {
    if (!o) return;
    Object.assign(form, {
      id: undefined,
      name: '',
      projectCode: '',
      costVersion: 1,
      sourceType: 1,
      currency: 'CNY',
      remark: '',
      items: [],
    });
    products.value = await getProductSimpleList();
    const d = api.getData<any>();
    editing.value = !!d?.id;
    detail.value = !!d?.detail;
    if (d?.id) Object.assign(form, await getCostResult(d.id));
    if (!form.items.length) add();
  },
});
</script>
<template>
  <Modal :title="title" class="w-5/6"
    ><a-form layout="vertical" class="px-4"
      ><a-row :gutter="16"
        ><a-col :span="8"
          ><a-form-item label="造价项目名称" required
            ><a-input
              v-model:value="form.name"
              :disabled="detail" /></a-form-item></a-col
        ><a-col :span="6"
          ><a-form-item label="项目编码"
            ><a-input
              v-model:value="form.projectCode"
              :disabled="detail" /></a-form-item></a-col
        ><a-col :span="4"
          ><a-form-item label="版本"
            ><a-input-number
              v-model:value="form.costVersion"
              :min="1"
              :disabled="detail" /></a-form-item></a-col
        ><a-col :span="6"
          ><a-form-item label="备注"
            ><a-input
              v-model:value="form.remark"
              :disabled="detail" /></a-form-item></a-col
      ></a-row>
      <div class="mb-3 flex justify-between">
        <b>造价材料清单与 ERP 商品映射</b
        ><a-button v-if="!detail" type="primary" @click="add"
          >添加材料</a-button
        >
      </div>
      <a-table :data-source="form.items" :pagination="false" bordered
        ><a-table-column
          title="行号"
          data-index="lineNo"
          :width="65"
        /><a-table-column title="外部编码" :width="130"
          ><template #default="{ record }"
            ><a-input
              v-model:value="record.externalMaterialCode"
              :disabled="detail" /></template></a-table-column
        ><a-table-column title="材料名称" :width="160"
          ><template #default="{ record }"
            ><a-input
              v-model:value="record.materialName"
              :disabled="detail" /></template></a-table-column
        ><a-table-column title="规格"
          ><template #default="{ record }"
            ><a-input
              v-model:value="record.specification"
              :disabled="detail" /></template></a-table-column
        ><a-table-column title="ERP商品映射" :width="220"
          ><template #default="{ record }"
            ><a-select
              v-model:value="record.productId"
              allow-clear
              show-search
              option-filter-prop="label"
              class="w-full"
              :disabled="detail"
              @change="changed(record)"
              ><a-select-option
                v-for="p in products"
                :key="p.id"
                :value="p.id"
                :label="p.name"
                >{{ p.name }}</a-select-option
              ></a-select
            ></template
          ></a-table-column
        ><a-table-column title="数量" :width="110"
          ><template #default="{ record }"
            ><a-input-number
              v-model:value="record.count"
              :min="0"
              :disabled="detail" /></template></a-table-column
        ><a-table-column title="单价" :width="120"
          ><template #default="{ record }"
            ><a-input-number
              v-model:value="record.unitPrice"
              :min="0"
              :disabled="detail" /></template></a-table-column
        ><a-table-column v-if="!detail" title="操作" :width="70"
          ><template #default="{ index }"
            ><a-button type="link" danger @click="form.items.splice(index, 1)"
              >删除</a-button
            ></template
          ></a-table-column
        ></a-table
      ></a-form
    ></Modal
  >
</template>
