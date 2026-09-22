<script lang="ts" setup>
import type { CrmAfterSaleTicketApi } from '#/api/crm/after-sale/ticket';

import { onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { Page, useVbenModal } from '@vben/common-ui';
import { useTabs } from '@vben/hooks';

import { Card } from 'ant-design-vue';

import { getAfterSaleTicket } from '#/api/crm/after-sale/ticket';
import { useDescription } from '#/components/description';
import { ACTION_ICON, TableAction } from '#/components/table-action';
import { $t } from '#/locales';

import Form from '../modules/form.vue';
import { useDetailSchema } from './data';

const props = defineProps<{ id?: number }>();

const route = useRoute();
const router = useRouter();
const tabs = useTabs();

const loading = ref(false); // 加载中
const ticketId = ref(0); // 工单编号
const ticket = ref<CrmAfterSaleTicketApi.Ticket>({}); // 工单详情

const [Descriptions] = useDescription({
  bordered: false,
  column: 3,
  schema: useDetailSchema(),
});

const [FormModal, formModalApi] = useVbenModal({
  connectedComponent: Form,
  destroyOnClose: true,
});

/** 加载工单详情 */
async function loadDetail() {
  loading.value = true;
  try {
    ticket.value = await getAfterSaleTicket(ticketId.value);
  } finally {
    loading.value = false;
  }
}

/** 返回问题工单列表 */
function handleBack() {
  tabs.closeCurrentTab();
  router.push('/crm/after-sale/ticket');
}

/** 编辑问题工单 */
function handleEdit() {
  formModalApi.setData({ id: ticketId.value }).open();
}

onMounted(() => {
  ticketId.value = Number(props.id || route.params.id);
  loadDetail();
});
</script>

<template>
  <Page
    auto-content-height
    :title="`问题工单详情：${ticket?.no ?? ''}`"
    :loading="loading"
  >
    <FormModal @success="loadDetail" />
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
            auth: ['crm:after-sale-ticket:update'],
            onClick: handleEdit,
          },
        ]"
      />
    </template>
    <Card class="min-h-[10%]">
      <Descriptions :data="ticket" />
    </Card>
  </Page>
</template>
