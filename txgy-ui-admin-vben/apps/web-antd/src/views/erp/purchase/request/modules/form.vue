<script lang="ts" setup>
import { computed, reactive, ref } from 'vue';
import { useVbenModal } from '@vben/common-ui';
import { message } from 'ant-design-vue';
import {
  createPurchaseRequest,
  getPurchaseRequest,
  updatePurchaseRequest,
} from '#/api/erp/purchase/request';
import { getProductSimpleList } from '#/api/erp/product/product';
const emit = defineEmits(['success']);
const products = ref<any[]>([]);
const editing = ref(false);
const form = reactive<any>({ requestTime: Date.now(), items: [] });
const title = computed(() => (editing.value ? '编辑采购申请' : '新增采购申请'));
function addItem() {
  form.items.push({
    productId: undefined,
    productUnitId: undefined,
    count: 1,
    targetPrice: 0,
    remark: '',
  });
}
function removeItem(i: number) {
  form.items.splice(i, 1);
}
function productChanged(item: any) {
  const p = products.value.find((x) => x.id === item.productId);
  item.productUnitId = p?.unitId;
}
const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    if (
      !form.requestTime ||
      !form.items.length ||
      form.items.some((i: any) => !i.productId || !i.count)
    ) {
      message.warning('请填写申请时间和完整物料明细');
      return;
    }
    modalApi.lock();
    try {
      await (editing.value
        ? updatePurchaseRequest(form)
        : createPurchaseRequest(form));
      await modalApi.close();
      message.success('保存成功');
      emit('success');
    } finally {
      modalApi.unlock();
    }
  },
  async onOpenChange(open) {
    if (!open) return;
    Object.assign(form, {
      id: undefined,
      requestTime: Date.now(),
      requiredDate: undefined,
      budgetAmount: 0,
      purpose: '',
      remark: '',
      items: [],
    });
    products.value = await getProductSimpleList();
    const data = modalApi.getData<any>();
    editing.value = !!data?.id;
    if (data?.id) Object.assign(form, await getPurchaseRequest(data.id));
    if (!form.items?.length) addItem();
  },
});
</script>
<template>
  <Modal :title="title" class="w-4/5"
    ><a-form layout="vertical" class="px-4"
      ><a-row :gutter="16"
        ><a-col :span="6"
          ><a-form-item label="申请时间" required
            ><a-date-picker
              v-model:value="form.requestTime"
              show-time
              value-format="x"
              class="w-full" /></a-form-item></a-col
        ><a-col :span="6"
          ><a-form-item label="需求日期"
            ><a-date-picker
              v-model:value="form.requiredDate"
              show-time
              value-format="x"
              class="w-full" /></a-form-item></a-col
        ><a-col :span="6"
          ><a-form-item label="预算金额"
            ><a-input-number
              v-model:value="form.budgetAmount"
              :min="0"
              class="w-full" /></a-form-item></a-col
        ><a-col :span="6"
          ><a-form-item label="采购用途"
            ><a-input
              v-model:value="form.purpose" /></a-form-item></a-col></a-row
      ><a-form-item label="备注"
        ><a-textarea v-model:value="form.remark"
      /></a-form-item>
      <div class="mb-3 flex justify-between">
        <b>采购物料明细</b
        ><a-button type="primary" @click="addItem">添加明细</a-button>
      </div>
      <a-table
        :data-source="form.items"
        :pagination="false"
        row-key="id"
        bordered
        ><a-table-column title="物料" :width="260"
          ><template #default="{ record }"
            ><a-select
              v-model:value="record.productId"
              show-search
              option-filter-prop="label"
              class="w-full"
              @change="productChanged(record)"
              ><a-select-option
                v-for="p in products"
                :key="p.id"
                :value="p.id"
                :label="p.name"
                >{{ p.name }}（{{ p.barCode }}）</a-select-option
              ></a-select
            ></template
          ></a-table-column
        ><a-table-column title="数量" :width="140"
          ><template #default="{ record }"
            ><a-input-number
              v-model:value="record.count"
              :min="0.001"
              class="w-full" /></template></a-table-column
        ><a-table-column title="目标单价" :width="140"
          ><template #default="{ record }"
            ><a-input-number
              v-model:value="record.targetPrice"
              :min="0"
              class="w-full" /></template></a-table-column
        ><a-table-column title="备注"
          ><template #default="{ record }"
            ><a-input
              v-model:value="record.remark" /></template></a-table-column
        ><a-table-column title="操作" :width="80"
          ><template #default="{ index }"
            ><a-button type="link" danger @click="removeItem(index)"
              >删除</a-button
            ></template
          ></a-table-column
        ></a-table
      ></a-form
    ></Modal
  >
</template>
