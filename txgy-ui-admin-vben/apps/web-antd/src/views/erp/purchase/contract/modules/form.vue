<script lang="ts" setup>
import type { ErpPurchaseContractApi } from '#/api/erp/purchase/contract';

import { computed, reactive, ref } from 'vue';
import { useVbenModal } from '@vben/common-ui';
import { message } from 'ant-design-vue';

import {
  createPurchaseContract,
  getPurchaseContract,
  getPurchaseContractTemplateList,
  initPurchaseContractFromOrder,
  updatePurchaseContract,
} from '#/api/erp/purchase/contract';
import { getPurchaseOrderPage } from '#/api/erp/purchase/order';
import { getSimpleUserList } from '#/api/system/user';

const emit = defineEmits(['success']);
const editing = ref(false);
const currentStep = ref(0);
const orders = ref<any[]>([]);
const users = ref<any[]>([]);
const templates = ref<ErpPurchaseContractApi.ContractTemplate[]>([]);
const saveBuyerDefault = ref(false);
const form = reactive<ErpPurchaseContractApi.PurchaseContract>({});
const BUYER_DEFAULT_KEY = 'erp.purchase-contract.buyer-default';

const title = computed(() => (editing.value ? '编辑采购合同' : '生成采购合同'));
const totalAmount = computed(() => Number(form.totalAmount ?? 0));
const amountByRatio = (ratio?: number) =>
  ((totalAmount.value * Number(ratio ?? 0)) / 100).toFixed(2);

function resetForm() {
  for (const key of Object.keys(form)) delete (form as any)[key];
  Object.assign(form, {
    currency: '人民币',
    advancePaymentRatio: 0,
    acceptancePaymentRatio: 100,
    warrantyRetentionRatio: 0,
  });
  try {
    const saved = localStorage.getItem(BUYER_DEFAULT_KEY);
    if (saved) Object.assign(form, JSON.parse(saved));
  } catch {
    // 浏览器存储不可用时仍允许正常填写合同。
  }
}

async function loadOptions() {
  const [orderPage, userList, templateList] = await Promise.all([
    getPurchaseOrderPage({ pageNo: 1, pageSize: 100 }),
    getSimpleUserList(),
    getPurchaseContractTemplateList(),
  ]);
  orders.value = (orderPage.list ?? []).filter((item: any) => item.status === 20);
  users.value = userList ?? [];
  templates.value = templateList ?? [];
}

async function onOrderChange(orderId?: number) {
  if (!orderId) return;
  const initialized = await initPurchaseContractFromOrder(orderId);
  const buyer = {
    buyerName: form.buyerName,
    buyerLegalRepresentative: form.buyerLegalRepresentative,
    buyerAddress: form.buyerAddress,
    buyerBankName: form.buyerBankName,
    buyerBankAccount: form.buyerBankAccount,
    buyerPhone: form.buyerPhone,
    buyerFax: form.buyerFax,
  };
  Object.assign(form, initialized, buyer);
  message.success('已带入采购订单、询价、商品及供应商信息');
}

function onTemplateChange(id?: number) {
  const selected = templates.value.find((item) => item.id === id);
  if (!selected) return;
  form.templateName = selected.name;
  form.templateVersion = selected.version;
  form.legalTerms = selected.content;
}

function validateBeforeSave() {
  if (!form.purchaseOrderId || !form.contractName || !form.supplierId) {
    message.warning('请选择已审批采购订单并填写合同名称');
    currentStep.value = 0;
    return false;
  }
  const total = Number(form.advancePaymentRatio ?? 0)
    + Number(form.acceptancePaymentRatio ?? 0)
    + Number(form.warrantyRetentionRatio ?? 0);
  if (Math.abs(total - 100) > 0.0001) {
    message.warning('预付款、验收款和质保金比例合计必须为 100%');
    currentStep.value = 3;
    return false;
  }
  return true;
}

const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    if (!validateBeforeSave()) return;
    modalApi.lock();
    try {
      await (editing.value ? updatePurchaseContract(form) : createPurchaseContract(form));
      if (saveBuyerDefault.value) {
        localStorage.setItem(BUYER_DEFAULT_KEY, JSON.stringify({
          buyerName: form.buyerName,
          buyerLegalRepresentative: form.buyerLegalRepresentative,
          buyerAddress: form.buyerAddress,
          buyerBankName: form.buyerBankName,
          buyerBankAccount: form.buyerBankAccount,
          buyerPhone: form.buyerPhone,
          buyerFax: form.buyerFax,
        }));
      }
      await modalApi.close();
      emit('success');
      message.success('采购合同已保存');
    } finally {
      modalApi.unlock();
    }
  },
  async onOpenChange(open) {
    if (!open) return;
    currentStep.value = 0;
    saveBuyerDefault.value = false;
    resetForm();
    await loadOptions();
    const data = modalApi.getData<ErpPurchaseContractApi.PurchaseContract>();
    editing.value = !!data?.id;
    if (data?.id) Object.assign(form, await getPurchaseContract(data.id));
  },
});
</script>

<template>
  <Modal :title="title" class="w-5/6">
    <div class="px-5">
      <a-steps
        v-model:current="currentStep"
        size="small"
        class="mb-5"
        :items="[
          { title: '关联订单' },
          { title: '基本信息' },
          { title: '质量质保' },
          { title: '付款计划' },
          { title: '违约责任' },
          { title: '签约资料' },
          { title: '条款预览' },
        ]"
      />

      <a-form layout="vertical" class="max-h-[62vh] overflow-y-auto pr-2">
        <template v-if="currentStep === 0">
          <a-alert class="mb-4" type="info" show-icon message="只能选择审批通过的采购订单；选择后自动带入询价、商品、价格和供应商资料。" />
          <a-row :gutter="16">
            <a-col :span="12"><a-form-item label="已审批采购订单" required>
              <a-select v-model:value="form.purchaseOrderId" show-search option-filter-prop="label"
                :disabled="editing && !!form.purchaseOrderId" @change="onOrderChange">
                <a-select-option v-for="order in orders" :key="order.id" :value="order.id" :label="order.no">
                  {{ order.no }}｜{{ order.supplierName }}｜{{ order.productNames }}
                </a-select-option>
              </a-select>
            </a-form-item></a-col>
            <a-col :span="12"><a-form-item label="供应商"><a-input :value="form.supplierName || form.supplierNameSnapshot" disabled /></a-form-item></a-col>
            <a-col :span="12"><a-form-item label="合同名称" required><a-input v-model:value="form.contractName" /></a-form-item></a-col>
            <a-col :span="6"><a-form-item label="合同金额"><a-input-number v-model:value="form.totalAmount" class="w-full" :min="0" :precision="2" /></a-form-item></a-col>
            <a-col :span="6"><a-form-item label="税率(%)"><a-input-number v-model:value="form.taxRate" class="w-full" :min="0" :max="100" /></a-form-item></a-col>
          </a-row>
        </template>

        <template v-else-if="currentStep === 1">
          <a-row :gutter="16">
            <a-col :span="8"><a-form-item label="合同编号"><a-input :value="form.no || '保存后系统自动生成'" disabled /></a-form-item></a-col>
            <a-col :span="8"><a-form-item label="签订日期"><a-date-picker v-model:value="form.signDate" value-format="x" show-time class="w-full" /></a-form-item></a-col>
            <a-col :span="8"><a-form-item label="签订地点"><a-input v-model:value="form.signPlace" placeholder="未填写将在合同中保留空白" /></a-form-item></a-col>
            <a-col :span="8"><a-form-item label="生效日期"><a-date-picker v-model:value="form.effectiveDate" value-format="x" show-time class="w-full" /></a-form-item></a-col>
            <a-col :span="8"><a-form-item label="到期日期"><a-date-picker v-model:value="form.expireDate" value-format="x" show-time class="w-full" /></a-form-item></a-col>
            <a-col :span="8"><a-form-item label="签订人"><a-select v-model:value="form.signerId" show-search option-filter-prop="label"><a-select-option v-for="user in users" :key="user.id" :value="user.id" :label="user.nickname">{{ user.nickname }}</a-select-option></a-select></a-form-item></a-col>
            <a-col :span="8"><a-form-item label="约定交货日期"><a-date-picker v-model:value="form.deliveryDeadline" value-format="x" show-time class="w-full" /></a-form-item></a-col>
            <a-col :span="8"><a-form-item label="交货地点"><a-input v-model:value="form.deliveryPlace" /></a-form-item></a-col>
            <a-col :span="8"><a-form-item label="币种"><a-input v-model:value="form.currency" /></a-form-item></a-col>
          </a-row>
        </template>

        <template v-else-if="currentStep === 2">
          <a-form-item label="质量标准（已合并商品规格与询价质量要求）"><a-textarea v-model:value="form.qualityStandard" :rows="4" /></a-form-item>
          <a-row :gutter="16">
            <a-col :span="8"><a-form-item label="验收期限（天）"><a-input-number v-model:value="form.acceptancePeriodDays" class="w-full" :min="0" /></a-form-item></a-col>
            <a-col :span="8"><a-form-item label="质保期限（月）"><a-input-number v-model:value="form.warrantyPeriodMonths" class="w-full" :min="0" /></a-form-item></a-col>
            <a-col :span="8"><a-form-item label="售后响应（小时）"><a-input-number v-model:value="form.afterSaleResponseHours" class="w-full" :min="0" /></a-form-item></a-col>
            <a-col :span="8"><a-form-item label="到场服务（小时）"><a-input-number v-model:value="form.onSiteServiceHours" class="w-full" :min="0" /></a-form-item></a-col>
            <a-col :span="8"><a-form-item label="最大维修次数"><a-input-number v-model:value="form.maxRepairTimes" class="w-full" :min="0" /></a-form-item></a-col>
          </a-row>
        </template>

        <template v-else-if="currentStep === 3">
          <a-alert class="mb-4" type="warning" show-icon message="三项比例合计必须为 100%，金额根据合同总额实时计算。" />
          <a-row :gutter="16">
            <a-col :span="8"><a-form-item label="预付款比例(%)"><a-input-number v-model:value="form.advancePaymentRatio" class="w-full" :min="0" :max="100" /></a-form-item></a-col>
            <a-col :span="8"><a-form-item label="预付款金额"><a-input :value="amountByRatio(form.advancePaymentRatio)" disabled /></a-form-item></a-col>
            <a-col :span="8"><a-form-item label="生效后支付期限（天）"><a-input-number v-model:value="form.advancePaymentDays" class="w-full" :min="0" /></a-form-item></a-col>
            <a-col :span="8"><a-form-item label="验收款比例(%)"><a-input-number v-model:value="form.acceptancePaymentRatio" class="w-full" :min="0" :max="100" /></a-form-item></a-col>
            <a-col :span="8"><a-form-item label="验收款金额"><a-input :value="amountByRatio(form.acceptancePaymentRatio)" disabled /></a-form-item></a-col>
            <a-col :span="8"><a-form-item label="验收后支付期限（天）"><a-input-number v-model:value="form.acceptancePaymentDays" class="w-full" :min="0" /></a-form-item></a-col>
            <a-col :span="8"><a-form-item label="质保金比例(%)"><a-input-number v-model:value="form.warrantyRetentionRatio" class="w-full" :min="0" :max="100" /></a-form-item></a-col>
            <a-col :span="8"><a-form-item label="质保金金额"><a-input :value="amountByRatio(form.warrantyRetentionRatio)" disabled /></a-form-item></a-col>
            <a-col :span="8"><a-form-item label="释放期限（天）"><a-input-number v-model:value="form.warrantyReleaseDays" class="w-full" :min="0" /></a-form-item></a-col>
          </a-row>
          <a-form-item label="质保金释放条件"><a-input v-model:value="form.warrantyReleaseCondition" /></a-form-item>
          <a-form-item label="其他付款条件"><a-textarea v-model:value="form.paymentTerms" :rows="3" /></a-form-item>
        </template>

        <template v-else-if="currentStep === 4">
          <a-row :gutter="16">
            <a-col :span="8"><a-form-item label="甲方拒收违约金(%)"><a-input-number v-model:value="form.buyerRejectPenaltyRatio" class="w-full" :min="0" /></a-form-item></a-col>
            <a-col :span="8"><a-form-item label="甲方逾期付款率(‰/日)"><a-input-number v-model:value="form.buyerOverdueRate" class="w-full" :min="0" /></a-form-item></a-col>
            <a-col :span="8"><a-form-item label="甲方逾期解约天数"><a-input-number v-model:value="form.buyerTerminationDays" class="w-full" :min="0" /></a-form-item></a-col>
            <a-col :span="8"><a-form-item label="乙方质量违约金(%)"><a-input-number v-model:value="form.sellerQualityPenaltyRatio" class="w-full" :min="0" /></a-form-item></a-col>
            <a-col :span="8"><a-form-item label="乙方逾期交货率(‰/日)"><a-input-number v-model:value="form.sellerOverdueRate" class="w-full" :min="0" /></a-form-item></a-col>
            <a-col :span="8"><a-form-item label="乙方逾期解约天数"><a-input-number v-model:value="form.sellerTerminationDays" class="w-full" :min="0" /></a-form-item></a-col>
            <a-col :span="8"><a-form-item label="乙方解约赔偿比例(%)"><a-input-number v-model:value="form.sellerTerminationCompensationRatio" class="w-full" :min="0" /></a-form-item></a-col>
            <a-col :span="8"><a-form-item label="权利瑕疵违约金(%)"><a-input-number v-model:value="form.ipDefectPenaltyRatio" class="w-full" :min="0" /></a-form-item></a-col>
          </a-row>
        </template>

        <template v-else-if="currentStep === 5">
          <a-divider orientation="left">甲方（采购人）</a-divider>
          <a-row :gutter="16">
            <a-col :span="8"><a-form-item label="企业名称"><a-input v-model:value="form.buyerName" /></a-form-item></a-col>
            <a-col :span="8"><a-form-item label="法定代表人/授权代表"><a-input v-model:value="form.buyerLegalRepresentative" /></a-form-item></a-col>
            <a-col :span="8"><a-form-item label="企业地址"><a-input v-model:value="form.buyerAddress" /></a-form-item></a-col>
            <a-col :span="8"><a-form-item label="开户行"><a-input v-model:value="form.buyerBankName" /></a-form-item></a-col>
            <a-col :span="8"><a-form-item label="银行账号"><a-input v-model:value="form.buyerBankAccount" /></a-form-item></a-col>
            <a-col :span="4"><a-form-item label="电话"><a-input v-model:value="form.buyerPhone" /></a-form-item></a-col>
            <a-col :span="4"><a-form-item label="传真"><a-input v-model:value="form.buyerFax" /></a-form-item></a-col>
          </a-row>
          <a-checkbox v-model:checked="saveBuyerDefault">保存为本浏览器默认甲方资料，下次自动带入</a-checkbox>
          <a-divider orientation="left">乙方（供应商，已从供应商档案带入）</a-divider>
          <a-row :gutter="16">
            <a-col :span="8"><a-form-item label="企业名称"><a-input v-model:value="form.supplierNameSnapshot" /></a-form-item></a-col>
            <a-col :span="8"><a-form-item label="法定代表人/授权代表"><a-input v-model:value="form.sellerLegalRepresentative" /></a-form-item></a-col>
            <a-col :span="8"><a-form-item label="企业地址"><a-input v-model:value="form.sellerAddress" /></a-form-item></a-col>
            <a-col :span="8"><a-form-item label="开户行"><a-input v-model:value="form.sellerBankName" /></a-form-item></a-col>
            <a-col :span="8"><a-form-item label="银行账号"><a-input v-model:value="form.sellerBankAccount" /></a-form-item></a-col>
            <a-col :span="4"><a-form-item label="电话"><a-input v-model:value="form.sellerPhone" /></a-form-item></a-col>
            <a-col :span="4"><a-form-item label="传真"><a-input v-model:value="form.sellerFax" /></a-form-item></a-col>
          </a-row>
        </template>

        <template v-else>
          <a-row :gutter="16">
            <a-col :span="12"><a-form-item label="合同条款模板"><a-select v-model:value="form.templateId" @change="onTemplateChange"><a-select-option v-for="item in templates" :key="item.id" :value="item.id">{{ item.name }}（{{ item.version }}）</a-select-option></a-select></a-form-item></a-col>
            <a-col :span="6"><a-form-item label="模板名称"><a-input v-model:value="form.templateName" disabled /></a-form-item></a-col>
            <a-col :span="6"><a-form-item label="模板版本"><a-input v-model:value="form.templateVersion" disabled /></a-form-item></a-col>
          </a-row>
          <a-form-item label="通用法律条款"><a-textarea v-model:value="form.legalTerms" :rows="10" /></a-form-item>
          <a-form-item label="备注"><a-textarea v-model:value="form.remark" :rows="3" /></a-form-item>
          <a-alert type="info" show-icon message="未填写的模板项目将在 PDF 中保留空白；段落、表格和页数会根据实际内容动态调整。" />
        </template>
      </a-form>

      <div class="mt-4 flex justify-between border-t pt-4">
        <a-button :disabled="currentStep === 0" @click="currentStep--">上一步</a-button>
        <a-button v-if="currentStep < 6" type="primary" @click="currentStep++">下一步</a-button>
        <span v-else class="text-gray-500">确认无误后点击弹窗底部“确定”保存合同草稿</span>
      </div>
    </div>
  </Modal>
</template>
