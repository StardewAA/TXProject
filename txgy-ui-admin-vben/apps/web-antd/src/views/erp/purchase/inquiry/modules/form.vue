<script lang="ts" setup>
import { computed, reactive, ref } from 'vue';
import { useVbenModal } from '@vben/common-ui';
import { message } from 'ant-design-vue';
import {
  createInquiry,
  getInquiry,
  updateInquiry,
} from '#/api/erp/purchase/inquiry';
import { getProductSimpleList } from '#/api/erp/product/product';
import { getSupplierSimpleList } from '#/api/erp/purchase/supplier';
import {
  getPurchaseRequest,
  getPurchaseRequestPage,
} from '#/api/erp/purchase/request';
const emit = defineEmits(['success']);
const products = ref<any[]>([]);
/** 可邀请的供应商（仅启用的） */
const suppliers = ref<any[]>([]);
/** 已审批的采购申请（可作为询价来源） */
const purchaseRequests = ref<any[]>([]);
const editing = ref(false);
const form = reactive<any>({ inquiryMode: 1, currency: 'CNY', items: [] });

/** 加载可作为来源的采购申请（仅已审批的） */
async function loadRequests() {
  const res: any = await getPurchaseRequestPage({ pageNo: 1, pageSize: 100 });
  purchaseRequests.value = (res.list ?? []).filter((r: any) => r.status === 20);
}

/**
 * 选择来源采购申请：带出申请明细并回填 purchaseRequestItemId，
 * 使手工创建的询价单也能与采购申请对齐（原实现无此入口，手工询价永远填不上来源）。
 */
async function onRequestChange(id?: number) {
  if (!id) {
    return;
  }
  const req: any = await getPurchaseRequest(id);
  form.items = (req.items ?? []).map((i: any) => ({
    purchaseRequestItemId: i.id,
    productId: i.productId,
    productUnitId: i.productUnitId,
    count: i.count,
    remark: i.remark,
  }));
}
const title = computed(() => (editing.value ? '编辑询价单' : '新增询价单'));
const add = () =>
  form.items.push({ productId: undefined, productUnitId: undefined, count: 1 });
const changed = (i: any) => {
  i.productUnitId = products.value.find((x) => x.id === i.productId)?.unitId;
};
const [Modal, api] = useVbenModal({
  async onConfirm() {
    if (
      !form.title ||
      !form.deadline ||
      !form.items.length ||
      form.items.some((x: any) => !x.productId || !x.count)
    ) {
      message.warning('请填写完整询价信息');
      return;
    }
    api.lock();
    try {
      await (editing.value ? updateInquiry(form) : createInquiry(form));
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
      title: '',
      inquiryMode: 1,
      deadline: undefined,
      deliveryDate: undefined,
      deliveryAddress: '',
      currency: 'CNY',
      paymentTerm: '',
      remark: '',
      purchaseRequestId: undefined,
      supplierIds: [],
      items: [],
    });
    products.value = await getProductSimpleList();
    suppliers.value = await getSupplierSimpleList();
    await loadRequests();
    const d = api.getData<any>();
    editing.value = !!d?.id;
    if (d?.id) {
      const detail: any = await getInquiry(d.id);
      Object.assign(form, detail);
      // 后端用 invitedSuppliers 表达受邀名单，回填时转换成 supplierIds 供多选使用
      form.supplierIds = (detail?.invitedSuppliers ?? []).map(
        (s: any) => s.supplierId,
      );
    }
    if (!form.items.length) add();
  },
});
</script>
<template>
  <Modal :title="title" class="w-4/5"
    ><a-form layout="vertical" class="px-4"
      ><a-row :gutter="16"
        ><a-col :span="8"
          ><a-form-item label="询价标题" required
            ><a-input v-model:value="form.title" /></a-form-item></a-col
        ><a-col :span="6"
          ><a-form-item label="来源采购申请"
            ><a-select
              v-model:value="form.purchaseRequestId"
              allow-clear
              show-search
              option-filter-prop="label"
              placeholder="可不选，选择后自动带出明细"
              :disabled="editing"
              @change="onRequestChange"
              ><a-select-option
                v-for="r in purchaseRequests"
                :key="r.id"
                :value="r.id"
                :label="r.no"
                >{{ r.no }}（{{ r.productNames || '' }}）</a-select-option
              ></a-select
            ></a-form-item
          ></a-col
        ><a-col :span="4"
          ><a-form-item label="询价方式"
            ><a-select v-model:value="form.inquiryMode"
              ><a-select-option :value="1">公开询价</a-select-option
              ><a-select-option :value="2">邀请询价</a-select-option></a-select
            ></a-form-item
          ></a-col
        ><a-col :span="6"
          ><a-form-item label="报价截止" required
            ><a-date-picker
              v-model:value="form.deadline"
              show-time
              value-format="x"
              class="w-full" /></a-form-item></a-col
        ><a-col :span="6"
          ><a-form-item label="要求交付"
            ><a-date-picker
              v-model:value="form.deliveryDate"
              show-time
              value-format="x"
              class="w-full" /></a-form-item></a-col></a-row
      ><a-row :gutter="16"
        ><a-col :span="8"
          ><a-form-item label="交付地址"
            ><a-input
              v-model:value="form.deliveryAddress" /></a-form-item></a-col
        ><a-col :span="8"
          ><a-form-item label="付款条件"
            ><a-input v-model:value="form.paymentTerm" /></a-form-item></a-col
        ><a-col :span="8"
          ><a-form-item label="备注"
            ><a-input v-model:value="form.remark" /></a-form-item></a-col
      ></a-row>
      <a-row :gutter="16"
        ><a-col :span="10"
          ><a-form-item label="受邀供应商"
            ><a-select
              v-model:value="form.supplierIds"
              mode="multiple"
              allow-clear
              show-search
              option-filter-prop="label"
              placeholder="留空表示不限受邀范围"
              ><a-select-option
                v-for="s in suppliers"
                :key="s.id"
                :value="s.id"
                :label="s.name"
                >{{ s.name }}</a-select-option
              ></a-select
            ></a-form-item
          ></a-col
        ><a-col :span="14"
          ><a-form-item label=" "
            ><span class="text-gray-500"
              >名单内的供应商才能看到该询价单并报价；供应商登录账号在「采购管理 →
              供应商」中绑定。</span
            ></a-form-item
          ></a-col
        ></a-row
      >
      <div class="mb-3 flex justify-between">
        <b>询价明细</b><a-button type="primary" @click="add">添加物料</a-button>
      </div>
      <a-table :data-source="form.items" :pagination="false" bordered
        ><a-table-column title="物料" :width="280"
          ><template #default="{ record }"
            ><a-select
              v-model:value="record.productId"
              show-search
              option-filter-prop="label"
              class="w-full"
              @change="changed(record)"
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
        ><a-table-column title="质量要求"
          ><template #default="{ record }"
            ><a-input
              v-model:value="
                record.qualityRequirement
              " /></template></a-table-column
        ><a-table-column title="备注"
          ><template #default="{ record }"
            ><a-input
              v-model:value="record.remark" /></template></a-table-column
        ><a-table-column title="操作" :width="80"
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
