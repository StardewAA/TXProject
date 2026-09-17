<script setup lang="ts">
import type { CrmQuotationApi } from '#/api/crm/quotation';
import type { SystemOperateLogApi } from '#/api/system/operate-log';

import { onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { Page, useVbenModal } from '@vben/common-ui';
import { useTabs } from '@vben/hooks';

import { Card, message, Tabs } from 'ant-design-vue';

import { getOperateLogPage } from '#/api/crm/operateLog';
import { BizTypeEnum } from '#/api/crm/permission';
import {
  createContractByQuotation,
  getQuotation,
  submitQuotation,
} from '#/api/crm/quotation';
import { useDescription } from '#/components/description';
import { OperateLog } from '#/components/operate-log';
import { ACTION_ICON, TableAction } from '#/components/table-action';
import { $t } from '#/locales';
import { TransferForm } from '#/views/crm/permission';
import { ProductDetailsList } from '#/views/crm/product/components';

import Form from '../modules/form.vue';
import { useDetailBaseSchema, useDetailSchema } from './data';

const props = defineProps<{ id?: number }>();

const route = useRoute();
const router = useRouter();
const tabs = useTabs();

const loading = ref(false); // 加载中
const quotationId = ref(0); // 报价编号
const quotation = ref<CrmQuotationApi.Quotation>(
  {} as CrmQuotationApi.Quotation,
); // 报价详情
const logList = ref<SystemOperateLogApi.OperateLog[]>([]); // 操作日志

const [Descriptions] = useDescription({
  bordered: false,
  column: 4,
  schema: useDetailSchema(),
});

const [BaseDescriptions] = useDescription({
  bordered: false,
  column: 4,
  schema: useDetailBaseSchema(),
});

const [FormModal, formModalApi] = useVbenModal({
  connectedComponent: Form,
  destroyOnClose: true,
});

const [TransferModal, transferModalApi] = useVbenModal({
  connectedComponent: TransferForm,
  destroyOnClose: true,
});

/** 加载报价详情 */
async function loadQuotationDetail() {
  loading.value = true;
  try {
    quotation.value = await getQuotation(quotationId.value);
    // 操作日志
    const res = await getOperateLogPage({
      bizType: BizTypeEnum.CRM_QUOTATION,
      bizId: quotationId.value,
    });
    logList.value = res.list;
  } finally {
    loading.value = false;
  }
}

/** 返回列表页 */
function handleBack() {
  tabs.closeCurrentTab();
  router.push({ name: 'CrmQuotation' });
}

/** 编辑报价 */
function handleEdit() {
  formModalApi.setData({ id: quotationId.value }).open();
}

/** 转移报价 */
function handleTransfer() {
  transferModalApi.setData({ bizType: BizTypeEnum.CRM_QUOTATION }).open();
}

/** 提交报价审批 */
async function handleSubmit() {
  const hideLoading = message.loading({
    content: '提交审批中...',
    duration: 0,
  });
  try {
    await submitQuotation(quotationId.value);
    message.success('提交审批成功');
    await loadQuotationDetail();
  } finally {
    hideLoading();
  }
}

/** 转为合同 */
async function handleCreateContract() {
  const hideLoading = message.loading({
    content: '生成合同中...',
    duration: 0,
  });
  try {
    const contractId = await createContractByQuotation(quotationId.value);
    message.success('转为合同成功');
    router.push({ name: 'CrmContractDetail', params: { id: contractId } });
  } finally {
    hideLoading();
  }
}

/** 查看审批详情 */
function handleProcessDetail() {
  if (!quotation.value.processInstanceId) {
    return;
  }
  router.push({
    name: 'BpmProcessInstanceDetail',
    query: { id: quotation.value.processInstanceId },
  });
}

/** 加载数据 */
onMounted(() => {
  // 路由参数兜底：非法 id（如 /crm/quotation/detail/id）会得到 NaN，提示并退回列表
  const id = Number(props.id || route.params.id);
  if (!Number.isSafeInteger(id) || id <= 0) {
    message.warning('参数错误，报价不能为空！');
    handleBack();
    return;
  }
  quotationId.value = id;
  loadQuotationDetail();
});
</script>

<template>
  <Page auto-content-height :title="quotation?.name" :loading="loading">
    <FormModal @success="loadQuotationDetail" />
    <TransferModal @success="loadQuotationDetail" />
    <template #extra>
      <TableAction
        :actions="[
          {
            label: '返回',
            type: 'default',
            icon: 'lucide:arrow-left',
            onClick: handleBack,
          },
          {
            label: $t('ui.actionTitle.edit'),
            type: 'primary',
            icon: ACTION_ICON.EDIT,
            auth: ['crm:quotation:update'],
            ifShow: quotation.auditStatus === 0,
            onClick: handleEdit,
          },
          {
            label: '提交审批',
            type: 'primary',
            auth: ['crm:quotation:update'],
            ifShow: quotation.auditStatus === 0,
            onClick: handleSubmit,
          },
          {
            label: '转为合同',
            type: 'primary',
            auth: ['crm:quotation:update'],
            ifShow: quotation.auditStatus === 20 && !quotation.contractId,
            popConfirm: {
              title: '确认将报价单【' + quotation.name + '】转为合同？',
              confirm: handleCreateContract,
            },
          },
          {
            label: '查看审批',
            type: 'primary',
            auth: ['crm:quotation:update'],
            ifShow: quotation.auditStatus !== 0 && quotation.processInstanceId,
            onClick: handleProcessDetail,
          },
          {
            label: '转移',
            type: 'primary',
            ifShow: quotation.auditStatus === 0,
            onClick: handleTransfer,
          },
        ]"
      />
    </template>
    <Card class="min-h-[10%]">
      <Descriptions :data="quotation" />
    </Card>
    <Card class="mt-4 min-h-[60%]">
      <Tabs>
        <Tabs.TabPane tab="基本信息" key="1" :force-render="true">
          <BaseDescriptions :data="quotation" />
        </Tabs.TabPane>
        <Tabs.TabPane tab="产品" key="2" :force-render="true">
          <ProductDetailsList
            :biz-id="quotationId"
            :biz-type="BizTypeEnum.CRM_QUOTATION"
          />
        </Tabs.TabPane>
        <Tabs.TabPane tab="操作日志" key="3" :force-render="true">
          <OperateLog :log-list="logList" />
        </Tabs.TabPane>
      </Tabs>
    </Card>
  </Page>
</template>
