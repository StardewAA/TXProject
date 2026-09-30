<script lang="ts" setup>
import type { HrmContractExpireApi } from '#/api/hrm/contract/expire';

import { ref } from 'vue';

import { Page } from '@vben/common-ui';
import { formatDate } from '@vben/utils';

import { Card, Empty, Radio, Space, Table, Tag } from 'ant-design-vue';

import { getContractExpireWarningList } from '#/api/hrm/contract/expire';

import { useGridColumns } from './data';

defineOptions({ name: 'HrmContractExpire' });

const loading = ref(false);
const warnDays = ref(30);
const list = ref<HrmContractExpireApi.ContractExpire[]>([]);
const columns = useGridColumns();

/** 时间戳 / 日期字符串统一格式化为 YYYY-MM-DD，避免表格里直接显示一长串毫秒数 */
function formatDay(value?: null | number | string) {
  if (value === null || value === undefined || value === '') {
    return '-';
  }
  return formatDate(value, 'YYYY-MM-DD') || '-';
}

async function load() {
  loading.value = true;
  try {
    list.value = (await getContractExpireWarningList(warnDays.value)) ?? [];
  } finally {
    loading.value = false;
  }
}

load();
</script>

<template>
  <Page auto-content-height>
    <Card :bordered="false">
      <Space class="mb-3" wrap>
        <span class="text-muted-foreground text-sm">预警范围：</span>
        <Radio.Group
          v-model:value="warnDays"
          button-style="solid"
          @change="load"
        >
          <Radio.Button :value="7">7 天内</Radio.Button>
          <Radio.Button :value="30">30 天内</Radio.Button>
          <Radio.Button :value="60">60 天内</Radio.Button>
          <Radio.Button :value="90">90 天内</Radio.Button>
          <!-- 「全部」= 不限到期窗口：签署完成的合同（含 1 年期）也能立即看到倒计时 -->
          <Radio.Button :value="0">全部</Radio.Button>
        </Radio.Group>
        <span class="text-muted-foreground text-xs">
          仅统计「在职员工 + 合同执行中 + 已开启到期提醒」的劳动合同；到期前建议提前联系续签
        </span>
      </Space>
      <Table
        :columns="columns"
        :data-source="list"
        :loading="loading"
        :pagination="false"
        :row-key="(r: HrmContractExpireApi.ContractExpire) => String(r.id)"
        :scroll="{ x: 1300 }"
        bordered
        size="small"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'startTime'">
            {{ formatDay(record.startTime) }}
          </template>
          <template v-else-if="column.key === 'endTime'">
            {{ formatDay(record.endTime) }}
          </template>
          <template v-else-if="column.key === 'remainDays'">
            <span
              :class="
                (record.remainDays ?? 0) <= 7 ? 'font-bold text-red-500' : ''
              "
            >
              {{ record.remainDays ?? '-' }} 天
            </span>
          </template>
          <template v-else-if="column.key === 'urgency'">
            <Tag
              :color="
                record.urgency === 3
                  ? 'error'
                  : record.urgency === 2
                    ? 'warning'
                    : 'default'
              "
            >
              {{ record.urgencyName ?? '-' }}
            </Tag>
          </template>
        </template>
        <template #emptyText>
          <Empty description="当前预警范围内没有即将到期的劳动合同" />
        </template>
      </Table>
    </Card>
  </Page>
</template>
