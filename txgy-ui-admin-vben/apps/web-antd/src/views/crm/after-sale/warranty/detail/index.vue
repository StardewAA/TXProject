<script lang="ts" setup>
import type { CrmAfterSaleWarrantyApi } from '#/api/crm/after-sale/warranty';

import { onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { Page, useVbenModal } from '@vben/common-ui';
import { useTabs } from '@vben/hooks';

import { Card, Tabs } from 'ant-design-vue';

import { getAfterSaleWarranty } from '#/api/crm/after-sale/warranty';
import { getSimpleUserList } from '#/api/system/user';
import { useDescription } from '#/components/description';
import { ACTION_ICON, TableAction } from '#/components/table-action';
import { $t } from '#/locales';

import Form from '../modules/form.vue';
import { useDetailSchema } from './data';
import TicketList from './modules/ticket-list.vue';
import VisitRecordList from './modules/visit-record-list.vue';
import VisitTaskList from './modules/visit-task-list.vue';

const props = defineProps<{ id?: number }>();

const route = useRoute();
const router = useRouter();
const tabs = useTabs();

const loading = ref(false); // 加载中
const warrantyId = ref(0); // 质保档案编号
/** 质保档案详情（含负责人昵称等派生展示字段） */
const detail = ref<
  CrmAfterSaleWarrantyApi.Warranty & {
    ownerUserName?: string;
    serviceUserName?: string;
  }
>({});

const [Descriptions] = useDescription({
  bordered: false,
  column: 3,
  schema: useDetailSchema(),
});

const [FormModal, formModalApi] = useVbenModal({
  connectedComponent: Form,
  destroyOnClose: true,
});

/**
 * 解析负责人的用户昵称
 *
 * 质保档案接口只返回用户编号，详情页展示需要昵称；取不到时回退展示编号
 */
async function resolveUserNames(warranty: CrmAfterSaleWarrantyApi.Warranty) {
  const userIds = [warranty.ownerUserId, warranty.serviceUserId].filter(
    Boolean,
  ) as number[];
  if (userIds.length === 0) {
    return {};
  }
  try {
    const users = await getSimpleUserList();
    const nameMap = new Map(
      (users ?? []).map((user: { id: number; nickname: string }) => [
        user.id,
        user.nickname,
      ]),
    );
    return {
      ownerUserName: warranty.ownerUserId
        ? (nameMap.get(warranty.ownerUserId) ?? String(warranty.ownerUserId))
        : '-',
      serviceUserName: warranty.serviceUserId
        ? (nameMap.get(warranty.serviceUserId) ??
          String(warranty.serviceUserId))
        : '-',
    };
  } catch {
    // 用户列表获取失败不阻断详情展示
    return {};
  }
}

/** 加载质保档案详情 */
async function loadDetail() {
  loading.value = true;
  try {
    const data = await getAfterSaleWarranty(warrantyId.value);
    detail.value = { ...data, ...(await resolveUserNames(data)) };
  } finally {
    loading.value = false;
  }
}

/** 返回质保档案列表 */
function handleBack() {
  tabs.closeCurrentTab();
  router.push('/crm/after-sale/warranty');
}

/** 编辑质保档案 */
function handleEdit() {
  formModalApi.setData({ id: warrantyId.value }).open();
}

onMounted(() => {
  warrantyId.value = Number(props.id || route.params.id);
  loadDetail();
});
</script>

<template>
  <Page
    auto-content-height
    :title="`质保档案详情：${detail?.no ?? ''}`"
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
            auth: ['crm:after-sale-warranty:update'],
            onClick: handleEdit,
          },
        ]"
      />
    </template>
    <Card class="min-h-[10%]">
      <Descriptions :data="detail" />
    </Card>
    <Card class="mt-4 min-h-[60%]">
      <Tabs>
        <Tabs.TabPane tab="回访任务" key="1" :force-render="true">
          <VisitTaskList :warranty-id="warrantyId" />
        </Tabs.TabPane>
        <Tabs.TabPane tab="回访记录" key="2" :force-render="true">
          <VisitRecordList :warranty-id="warrantyId" />
        </Tabs.TabPane>
        <Tabs.TabPane tab="售后工单" key="3" :force-render="true">
          <TicketList :warranty-id="warrantyId" />
        </Tabs.TabPane>
      </Tabs>
    </Card>
  </Page>
</template>
