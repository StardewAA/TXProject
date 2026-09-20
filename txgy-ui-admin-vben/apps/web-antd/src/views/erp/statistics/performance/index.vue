<script lang="ts" setup>
import type { ErpSalesAttributionApi } from '#/api/erp/statistics/attribution';

import { computed, onMounted, ref } from 'vue';

import { Page } from '@vben/common-ui';

import {
  getPerformanceSummary,
  getSalesAttribution,
  getSourceBreakdown,
} from '#/api/erp/statistics/attribution';

/** 查询期间，格式 YYYY-MM-DD（由 range-picker 的 value-format 保证） */
const range = ref<[string, string] | undefined>();
const loading = ref(false);
const summary = ref<ErpSalesAttributionApi.PerformanceSummary[]>([]);
const details = ref<ErpSalesAttributionApi.Attribution[]>([]);
const breakdown = ref<ErpSalesAttributionApi.SourceBreakdown[]>([]);

function buildParams(): ErpSalesAttributionApi.QueryParams {
  const params: ErpSalesAttributionApi.QueryParams = {};
  const [start, end] = range.value ?? [];
  if (start) {
    params.startTime = `${start} 00:00:00`;
  }
  if (end) {
    params.endTime = `${end} 23:59:59`;
  }
  return params;
}

async function load() {
  loading.value = true;
  try {
    const params = buildParams();
    const [summaryData, detailData, breakdownData] = await Promise.all([
      getPerformanceSummary(params),
      getSalesAttribution(params),
      getSourceBreakdown(params),
    ]);
    summary.value = summaryData ?? [];
    details.value = detailData ?? [];
    breakdown.value = breakdownData ?? [];
  } finally {
    loading.value = false;
  }
}

function reset() {
  range.value = undefined;
  load();
}

onMounted(load);

/** 金额展示：保留两位小数并加千分位 */
function money(value?: number) {
  if (value === undefined || value === null) {
    return '-';
  }
  return Number(value).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
}

function num(value?: number) {
  if (value === undefined || value === null) {
    return '-';
  }
  return Number(value).toLocaleString('zh-CN', {
    maximumFractionDigits: 3,
  });
}

/** 汇总合计行：让「这个期间一共卖了多少、回了多少款」一眼可见 */
const totals = computed(() => {
  const acc = {
    netCount: 0,
    netAmount: 0,
    orderAmount: 0,
    orderCount: 0,
    receiptAmount: 0,
    receiptCount: 0,
    unreceiptedAmount: 0,
  };
  for (const row of summary.value) {
    acc.netCount += Number(row.netCount ?? 0);
    acc.netAmount += Number(row.netAmount ?? 0);
    acc.orderAmount += Number(row.orderAmount ?? 0);
    acc.orderCount += Number(row.orderCount ?? 0);
    acc.receiptAmount += Number(row.receiptAmount ?? 0);
    acc.receiptCount += Number(row.receiptCount ?? 0);
    acc.unreceiptedAmount += Number(row.unreceiptedAmount ?? 0);
  }
  return acc;
});
</script>

<template>
  <Page title="销售绩效报表">
    <a-card :bordered="false">
      <div class="mb-3 text-gray-500">
        销售员 × 产品 × 期间；回款经订单归因到销售员
      </div>
      <div class="mb-4 flex flex-wrap items-center gap-3">
        <span>统计期间</span>
        <a-range-picker v-model:value="range" value-format="YYYY-MM-DD" />
        <a-button type="primary" :loading="loading" @click="load">
          查询
        </a-button>
        <a-button @click="reset">重置</a-button>
        <span class="text-gray-500">
          留空表示全部期间；回款按回款时间、订单按下单时间、出入库按流水时间归入期间
        </span>
      </div>

      <a-spin :spinning="loading">
        <div class="mb-2 font-bold">一、销售员汇总（含回款归因）</div>
        <a-table
          :data-source="summary"
          :pagination="false"
          bordered
          size="small"
          row-key="userId"
        >
          <a-table-column title="销售员" data-index="userName" :width="140">
            <template #default="{ record }">
              {{ record.userName || `#${record.userId}` }}
            </template>
          </a-table-column>
          <a-table-column title="净销售数量" :width="120" align="right">
            <template #default="{ record }">{{ num(record.netCount) }}</template>
          </a-table-column>
          <a-table-column title="净销售金额" :width="150" align="right">
            <template #default="{ record }">
              {{ money(record.netAmount) }}
            </template>
          </a-table-column>
          <a-table-column title="订单金额" :width="150" align="right">
            <template #default="{ record }">
              {{ money(record.orderAmount) }}
            </template>
          </a-table-column>
          <a-table-column title="订单数" :width="90" align="right">
            <template #default="{ record }">
              {{ record.orderCount ?? 0 }}
            </template>
          </a-table-column>
          <a-table-column title="回款金额" :width="150" align="right">
            <template #default="{ record }">
              {{ money(record.receiptAmount) }}
            </template>
          </a-table-column>
          <a-table-column title="回款单数" :width="100" align="right">
            <template #default="{ record }">
              {{ record.receiptCount ?? 0 }}
            </template>
          </a-table-column>
          <a-table-column title="应收余额" :width="150" align="right">
            <template #default="{ record }">
              <span :class="Number(record.unreceiptedAmount ?? 0) < 0 ? 'text-red-500' : ''">
                {{ money(record.unreceiptedAmount) }}
              </span>
            </template>
          </a-table-column>
          <a-table-column title="回款率" :width="100" align="right">
            <template #default="{ record }">
              {{ record.receiptRate === undefined || record.receiptRate === null ? '-' : `${record.receiptRate}%` }}
            </template>
          </a-table-column>
          <template #summary>
            <a-table-summary fixed>
              <a-table-summary-row>
                <a-table-summary-cell>合计</a-table-summary-cell>
                <a-table-summary-cell align="right">
                  {{ num(totals.netCount) }}
                </a-table-summary-cell>
                <a-table-summary-cell align="right">
                  {{ money(totals.netAmount) }}
                </a-table-summary-cell>
                <a-table-summary-cell align="right">
                  {{ money(totals.orderAmount) }}
                </a-table-summary-cell>
                <a-table-summary-cell align="right">
                  {{ totals.orderCount }}
                </a-table-summary-cell>
                <a-table-summary-cell align="right">
                  {{ money(totals.receiptAmount) }}
                </a-table-summary-cell>
                <a-table-summary-cell align="right">
                  {{ totals.receiptCount }}
                </a-table-summary-cell>
                <a-table-summary-cell align="right">
                  {{ money(totals.unreceiptedAmount) }}
                </a-table-summary-cell>
                <a-table-summary-cell />
              </a-table-summary-row>
            </a-table-summary>
          </template>
        </a-table>

        <div class="mb-2 mt-6 font-bold">二、销售员 × 产品明细</div>
        <a-table
          :data-source="details"
          :pagination="{ pageSize: 20 }"
          bordered
          size="small"
          :row-key="(r: any) => `${r.userId}-${r.productCode}`"
        >
          <a-table-column title="销售员" :width="140">
            <template #default="{ record }">
              {{ record.userName || `#${record.userId}` }}
            </template>
          </a-table-column>
          <a-table-column title="产品编码" data-index="productCode" :width="150" />
          <a-table-column title="产品名称" data-index="productName" />
          <a-table-column title="出库数量" :width="110" align="right">
            <template #default="{ record }">{{ num(record.outCount) }}</template>
          </a-table-column>
          <a-table-column title="退货数量" :width="110" align="right">
            <template #default="{ record }">
              {{ num(record.returnCount) }}
            </template>
          </a-table-column>
          <a-table-column title="净数量" :width="110" align="right">
            <template #default="{ record }">{{ num(record.netCount) }}</template>
          </a-table-column>
          <a-table-column title="出库金额" :width="150" align="right">
            <template #default="{ record }">{{ money(record.outAmount) }}</template>
          </a-table-column>
          <a-table-column title="退货金额" :width="150" align="right">
            <template #default="{ record }">
              {{ money(record.returnAmount) }}
            </template>
          </a-table-column>
          <a-table-column title="净金额" :width="150" align="right">
            <template #default="{ record }">{{ money(record.netAmount) }}</template>
          </a-table-column>
        </a-table>

        <div class="mb-2 mt-6 font-bold">
          三、出库来源构成（回答「仓库里多少是 ERP 销售的」）
        </div>
        <a-table
          :data-source="breakdown"
          :pagination="false"
          bordered
          size="small"
          :row-key="(r: any) => `${r.sourceSystem}-${r.bizType}`"
        >
          <a-table-column title="来源系统" :width="140">
            <template #default="{ record }">
              {{ record.sourceSystemName || record.sourceSystem || '未标注（历史数据）' }}
            </template>
          </a-table-column>
          <a-table-column title="业务类型" :width="140">
            <template #default="{ record }">
              {{ record.bizTypeName || record.bizType }}
            </template>
          </a-table-column>
          <a-table-column title="变动数量" :width="140" align="right">
            <template #default="{ record }">{{ num(record.totalCount) }}</template>
          </a-table-column>
          <a-table-column title="流水条数" :width="120" align="right">
            <template #default="{ record }">{{ record.recordCount }}</template>
          </a-table-column>
          <a-table-column title="是否 ERP 销售" :width="140">
            <template #default="{ record }">
              <a-tag :color="record.erpSale ? 'green' : 'default'">
                {{ record.erpSale ? 'ERP 销售' : '非 ERP 销售' }}
              </a-tag>
            </template>
          </a-table-column>
        </a-table>
      </a-spin>
    </a-card>
  </Page>
</template>
