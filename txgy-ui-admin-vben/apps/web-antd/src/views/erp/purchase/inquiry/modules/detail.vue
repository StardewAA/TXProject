<script lang="ts" setup>
import { computed, reactive, ref } from 'vue';
import { useVbenModal } from '@vben/common-ui';
import { message } from 'ant-design-vue';
import {
  getInquiry,
  getInquiryComparison,
  getLoginSupplierId,
  saveInquiryQuote,
} from '#/api/erp/purchase/inquiry';
import { getSupplierSimpleList } from '#/api/erp/purchase/supplier';
const emit = defineEmits(['success']);
const inquiry = ref<any>({});
/** 物料维度横向比价（含加权评分、排名、推荐、价格异常） */
const comparison = ref<any>({ materials: [] });
const suppliers = ref<any[]>([]);
/**
 * 当前登录账号绑定的供应商编号。
 * 非空表示这是供应商门户账号：只能看自己的报价，且看不到采购方的横向比价。
 */
const loginSupplierId = ref<null | number>(null);
const isSupplier = computed(() => loginSupplierId.value !== null);

/** 加载询价详情 + 横向比价（供应商视角不请求比价，避免越权读取他家报价） */
async function load(id: number) {
  inquiry.value = await getInquiry(id);
  comparison.value = isSupplier.value
    ? { materials: [] }
    : await getInquiryComparison(id);
}
const quoteVisible = ref(false);
const quote = reactive<any>({ items: [] });
function openQuote() {
  Object.assign(quote, {
    inquiryId: inquiry.value.id,
    // 供应商视角：供应商由登录态决定，不让前端选择，避免越权代录
    supplierId: loginSupplierId.value ?? undefined,
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
  await load(inquiry.value.id);
  emit('success');
}
const [Modal, api] = useVbenModal({
  onConfirm: () => api.close(),
  async onOpenChange(o) {
    if (!o) return;
    const d = api.getData<any>();
    loginSupplierId.value = (await getLoginSupplierId()) ?? null;
    await load(d.id);
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
    <div class="my-4 flex items-center justify-between">
      <b>{{ isSupplier ? '我的报价' : '物料横向比价（含加权评分与推荐）' }}</b
      ><a-button
        v-if="inquiry.businessStatus === 20"
        type="primary"
        @click="openQuote"
        >{{ isSupplier ? '录入 / 修改我的报价' : '录入供应商报价' }}</a-button
      >
    </div>
    <!-- 供应商视角：只能看到自己提交的报价，看不到采购方的横向比价 -->
    <template v-if="isSupplier">
      <a-empty v-if="!(inquiry.quotes || []).length" description="尚未报价" />
      <a-table
        v-else
        :data-source="inquiry.quotes"
        :pagination="false"
        bordered
        size="small"
        row-key="id"
        ><a-table-column title="报价单号" data-index="no" /><a-table-column
          title="版本"
          data-index="version"
          :width="80" /><a-table-column
          title="报价总额"
          data-index="totalPrice"
          :width="150" /><a-table-column
          title="交期"
          data-index="deliveryDate"
          :width="180" /><a-table-column
          title="账期"
          data-index="paymentTerm" /><a-table-column
          title="备注"
          data-index="remark"
      /></a-table>
    </template>
    <!-- 采购方视角：受邀名单 + 各家横向比价 -->
    <template v-else>
      <div v-if="(inquiry.invitedSuppliers || []).length" class="mb-4">
        <div class="mb-1"><b>受邀供应商</b></div>
        <a-table
          :data-source="inquiry.invitedSuppliers"
          :pagination="false"
          bordered
          size="small"
          row-key="supplierId"
          ><a-table-column
            title="供应商"
            data-index="supplierName" /><a-table-column title="报价状态" :width="120"
            ><template #default="{ record }"
              ><a-tag :color="record.quoteStatus === 20 ? 'green' : 'default'">{{
                record.quoteStatus === 20 ? '已报价' : '未报价'
              }}</a-tag></template
            ></a-table-column
          ></a-table
        >
      </div>
      <a-empty
        v-if="!comparison.materials || comparison.materials.length === 0"
        description="暂无报价数据"
      />
      <div
        v-for="m in comparison.materials"
        :key="m.inquiryItemId"
        class="mb-5"
      >
      <a-alert
        v-if="m.recommendedSupplierName"
        type="success"
        show-icon
        class="mb-2"
        :message="`推荐：${m.recommendedSupplierName}（总分 ${m.recommendedScore}）`"
        :description="`最低价 ${m.lowestPrice} ／ 中位 ${m.medianPrice} ／ 均价 ${m.averagePrice} ／ 最高 ${m.highestPrice}`"
      />
      <div class="mb-1">
        <b>{{ m.productCode }} {{ m.productName }}</b>
        <span class="ml-2 text-gray-500"
          >数量 {{ m.count }} {{ m.productUnitName || '' }}</span
        >
      </div>
      <a-table
        :data-source="m.quotes"
        bordered
        size="small"
        :pagination="false"
        row-key="quoteItemId"
        ><a-table-column title="名次" data-index="rank" :width="60" /><a-table-column
          title="供应商"
          data-index="supplierName" /><a-table-column
          title="未税单价"
          data-index="productPrice"
          :width="100" /><a-table-column
          title="税率%"
          data-index="taxPercent"
          :width="80" /><a-table-column
          title="含税总价"
          data-index="totalPrice"
          :width="120" /><a-table-column
          title="交期"
          data-index="deliveryDate"
          :width="170" /><a-table-column
          title="账期"
          data-index="paymentTerm"
          :width="110" /><a-table-column
          title="价格分"
          data-index="priceScore"
          :width="80" /><a-table-column
          title="交期分"
          data-index="deliveryScore"
          :width="80" /><a-table-column
          title="账期分"
          data-index="paymentScore"
          :width="80" /><a-table-column
          title="总分"
          data-index="totalScore"
          :width="80" /><a-table-column title="标记" :width="160"
          ><template #default="{ record }"
            ><a-tag v-if="record.recommended" color="green">推荐</a-tag
            ><a-tag v-if="record.lowestPrice" color="blue">最低价</a-tag
            ><a-tag v-if="record.priceAnomaly === 'HIGH'" color="red"
              >价格偏高</a-tag
            ><a-tag v-if="record.priceAnomaly === 'LOW'" color="orange"
              >价格偏低</a-tag
            ><a-tag v-if="record.awarded" color="purple">已中标</a-tag
            ><span
              v-if="
                !record.recommended &&
                !record.lowestPrice &&
                !record.priceAnomaly &&
                !record.awarded
              "
              >-</span
            ></template
          ></a-table-column
        ></a-table
      >
      </div>
    </template>
    <a-modal
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
                :disabled="isSupplier"
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
