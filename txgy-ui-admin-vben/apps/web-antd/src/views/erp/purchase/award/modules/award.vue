<script lang="ts" setup>
import { ref } from 'vue';
import { useVbenModal } from '@vben/common-ui';
import { message } from 'ant-design-vue';
import { getInquiry, saveAward } from '#/api/erp/purchase/inquiry';
const emit = defineEmits(['success']);
const inquiry = ref<any>({});
const selections = ref<any[]>([]);
function options(itemId: number) {
  return (inquiry.value.quotes || []).flatMap((q: any) =>
    (q.items || [])
      .filter((i: any) => i.inquiryItemId === itemId)
      .map((i: any) => ({
        label: `${q.supplierName}｜单价 ${i.productPrice}｜总价 ${i.totalPrice}`,
        value: i.id,
        count: i.count,
      })),
  );
}
function changed(row: any) {
  const o = options(row.inquiryItemId).find(
    (x: any) => x.value === row.quoteItemId,
  );
  row.awardCount = o?.count;
}
const [Modal, api] = useVbenModal({
  async onConfirm() {
    if (selections.value.some((x) => !x.quoteItemId || !x.awardCount)) {
      message.warning('每条物料必须选择一个中标报价');
      return;
    }
    await saveAward({ inquiryId: inquiry.value.id, items: selections.value });
    message.success('定标选择已保存');
    emit('success');
    await api.close();
  },
  async onOpenChange(o) {
    if (!o) return;
    const d = api.getData<any>();
    inquiry.value = await getInquiry(d.id);
    selections.value = (inquiry.value.items || []).map((i: any) => ({
      inquiryItemId: i.id,
      productName: i.productName,
      quoteItemId: undefined,
      awardCount: i.count,
      awardReason: '',
    }));
  },
});
</script>
<template>
  <Modal title="选择中标报价" class="w-4/5"
    ><a-alert
      class="mb-4"
      type="info"
      show-icon
      message="每条询价物料必须且只能选择一个中标报价；保存后再从列表提交定标审批。" /><a-table
      :data-source="selections"
      :pagination="false"
      bordered
      ><a-table-column
        title="物料"
        data-index="productName"
        :width="180" /><a-table-column title="中标供应商及报价"
        ><template #default="{ record }"
          ><a-select
            v-model:value="record.quoteItemId"
            :options="options(record.inquiryItemId)"
            class="w-full"
            @change="changed(record)" /></template></a-table-column
      ><a-table-column title="中标数量" :width="150"
        ><template #default="{ record }"
          ><a-input-number
            v-model:value="record.awardCount"
            :min="0.001"
            class="w-full" /></template></a-table-column
      ><a-table-column title="定标理由"
        ><template #default="{ record }"
          ><a-input
            v-model:value="
              record.awardReason
            " /></template></a-table-column></a-table
  ></Modal>
</template>
