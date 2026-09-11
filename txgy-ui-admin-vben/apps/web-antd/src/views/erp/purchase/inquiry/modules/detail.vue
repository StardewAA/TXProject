<script lang="ts" setup>
import { reactive, ref } from 'vue';
import { useVbenModal } from '@vben/common-ui';
import { message } from 'ant-design-vue';
import { getInquiry, saveInquiryQuote } from '#/api/erp/purchase/inquiry';
import { getSupplierSimpleList } from '#/api/erp/purchase/supplier';
const emit = defineEmits(['success']);
const inquiry = ref<any>({});
const suppliers = ref<any[]>([]);
const quoteVisible = ref(false);
const quote = reactive<any>({ items: [] });
function openQuote() {
  Object.assign(quote, {
    inquiryId: inquiry.value.id,
    supplierId: undefined,
    version: 1,
    deliveryDate: undefined,
    paymentTerm: '',
    remark: '',
    items: (inquiry.value.items || []).map((i: any) => ({
      inquiryItemId: i.id,
      productId: i.productId,
      productUnitId: i.productUnitId,
      count: i.count,
      productPrice: 0,
      taxPercent: 13,
      freightPrice: 0,
      productName: i.productName,
    })),
  });
  quoteVisible.value = true;
}
async function save() {
  if (!quote.supplierId || quote.items.some((i: any) => i.productPrice < 0)) {
    message.warning('请选择供应商并填写报价');
    return;
  }
  await saveInquiryQuote(quote);
  message.success('供应商报价已保存');
  quoteVisible.value = false;
  inquiry.value = await getInquiry(inquiry.value.id);
  emit('success');
}
const [Modal, api] = useVbenModal({
  onConfirm: () => api.close(),
  async onOpenChange(o) {
    if (!o) return;
    const d = api.getData<any>();
    inquiry.value = await getInquiry(d.id);
    suppliers.value = await getSupplierSimpleList();
  },
});
</script>
<template>
  <Modal title="询价详情与报价比价" class="w-5/6"
    ><a-descriptions bordered :column="3" size="small"
      ><a-descriptions-item label="询价编号">{{
        inquiry.no
      }}</a-descriptions-item
      ><a-descriptions-item label="标题">{{
        inquiry.title
      }}</a-descriptions-item
      ><a-descriptions-item label="报价数量">{{
        inquiry.quoteCount
      }}</a-descriptions-item
      ><a-descriptions-item label="来源申请">{{
        inquiry.purchaseRequestNo || '-'
      }}</a-descriptions-item
      ><a-descriptions-item label="交付地址">{{
        inquiry.deliveryAddress || '-'
      }}</a-descriptions-item
      ><a-descriptions-item label="付款条件">{{
        inquiry.paymentTerm || '-'
      }}</a-descriptions-item></a-descriptions
    >
    <div class="my-4 flex justify-between">
      <b>供应商报价横向比价</b
      ><a-button
        v-if="inquiry.businessStatus === 20"
        type="primary"
        @click="openQuote"
        >录入供应商报价</a-button
      >
    </div>
    <a-table
      :data-source="inquiry.quotes"
      bordered
      :pagination="false"
      row-key="id"
      ><a-table-column title="报价编号" data-index="no" /><a-table-column
        title="供应商"
        data-index="supplierName" /><a-table-column
        title="版本"
        data-index="version"
        :width="70" /><a-table-column
        title="总价"
        data-index="totalPrice"
        :width="120" /><a-table-column
        title="交付日期"
        data-index="deliveryDate" /><a-table-column
        title="付款条件"
        data-index="paymentTerm" /></a-table
    ><a-modal
      v-model:open="quoteVisible"
      title="录入供应商报价"
      width="900px"
      @ok="save"
      ><a-form layout="vertical"
        ><a-row :gutter="16"
          ><a-col :span="12"
            ><a-form-item label="供应商" required
              ><a-select
                v-model:value="quote.supplierId"
                show-search
                option-filter-prop="label"
                ><a-select-option
                  v-for="s in suppliers"
                  :key="s.id"
                  :value="s.id"
                  :label="s.name"
                  >{{ s.name }}</a-select-option
                ></a-select
              ></a-form-item
            ></a-col
          ><a-col :span="6"
            ><a-form-item label="报价版本"
              ><a-input-number
                v-model:value="quote.version"
                :min="1" /></a-form-item></a-col
          ><a-col :span="6"
            ><a-form-item label="交付日期"
              ><a-date-picker
                v-model:value="quote.deliveryDate"
                value-format="x" /></a-form-item></a-col></a-row
        ><a-table :data-source="quote.items" :pagination="false" bordered
          ><a-table-column
            title="物料"
            data-index="productName" /><a-table-column
            title="数量"
            data-index="count"
            :width="100" /><a-table-column title="未税单价" :width="150"
            ><template #default="{ record }"
              ><a-input-number
                v-model:value="record.productPrice"
                :min="0" /></template></a-table-column
          ><a-table-column title="税率%" :width="120"
            ><template #default="{ record }"
              ><a-input-number
                v-model:value="record.taxPercent"
                :min="0" /></template></a-table-column
          ><a-table-column title="运费" :width="130"
            ><template #default="{ record }"
              ><a-input-number
                v-model:value="record.freightPrice"
                :min="
                  0
                " /></template></a-table-column></a-table></a-form></a-modal
  ></Modal>
</template>
