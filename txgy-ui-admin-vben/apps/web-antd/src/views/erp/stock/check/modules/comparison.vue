<script lang="ts" setup>
import type { ErpStockCheckApi } from '#/api/erp/stock/check';

import { ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { getStockCheckComparison } from '#/api/erp/stock/check';

/**
 * 盘点单三方比对
 *
 * 三方：① ERP 账面（erp_stock）② ERP 实盘（盘点单实盘数）③ WMS 实物（erp_stock_mirror）
 * WMS 镜像缺失时该方显示「无数据」，而不是当作 0。
 */
const data = ref<ErpStockCheckApi.Comparison>({});
const loading = ref(false);

async function load(id: number) {
  loading.value = true;
  try {
    data.value = await getStockCheckComparison(id);
  } finally {
    loading.value = false;
  }
}

function num(value?: null | number) {
  if (value === undefined || value === null) {
    return '-';
  }
  return Number(value).toLocaleString('zh-CN', { maximumFractionDigits: 3 });
}

/** 差异单元格的着色：非 0 才提示 */
function diffClass(value?: null | number, warnWhenNonZero = true) {
  if (value === undefined || value === null) {
    return 'text-gray-400';
  }
  if (!warnWhenNonZero) {
    return '';
  }
  return Number(value) === 0 ? 'text-gray-500' : 'text-red-500 font-bold';
}

const [Modal, api] = useVbenModal({
  onConfirm: () => api.close(),
  async onOpenChange(o) {
    if (!o) return;
    const d = api.getData<any>();
    if (d?.id) {
      await load(d.id);
    }
  },
});
</script>

<template>
  <Modal title="盘点单三方比对" class="w-5/6">
    <a-spin :spinning="loading">
      <a-descriptions bordered :column="3" size="small" class="mb-3">
        <a-descriptions-item label="盘点单号">
          {{ data.checkNo || '-' }}
        </a-descriptions-item>
        <a-descriptions-item label="盘点时间">
          {{ data.checkTime || '-' }}
        </a-descriptions-item>
        <a-descriptions-item label="盘点行数">
          {{ data.totalLines ?? 0 }}
        </a-descriptions-item>
      </a-descriptions>

      <!-- WMS 侧缺失时明确说明，避免把「没有数据」误读成「实物为 0」 -->
      <a-alert
        v-if="!data.mirrorAvailable"
        type="warning"
        show-icon
        class="mb-3"
        message="WMS 实物侧暂无镜像数据，当前为两方比对（ERP 账面 / ERP 实盘）"
        description="三方中的第三方取自 erp_stock_mirror（ERP→WMS 旁路同步的实物账）。待 WMS 侧补齐租户主数据并完成一次镜像同步后，此列会自动显示真实实物数，无需改代码。"
      />
      <a-alert
        v-else
        type="success"
        show-icon
        class="mb-3"
        :message="`三方比对可用：${data.mirrorCoveredLineCount} / ${data.totalLines} 行具备 WMS 实物数据`"
      />

      <a-row :gutter="12" class="mb-3">
        <a-col :span="6">
          <a-statistic
            title="存在盘点盈亏的行"
            :value="data.checkDiffLineCount ?? 0"
            :value-style="{ color: (data.checkDiffLineCount ?? 0) > 0 ? '#cf1322' : undefined }"
          />
        </a-col>
        <a-col :span="6">
          <a-statistic title="具备 WMS 实物数据的行" :value="data.mirrorCoveredLineCount ?? 0" />
        </a-col>
        <a-col :span="6">
          <a-statistic
            title="WMS 需调整的行"
            :value="data.wmsAdjustLineCount ?? 0"
            :value-style="{ color: (data.wmsAdjustLineCount ?? 0) > 0 ? '#cf1322' : undefined }"
          />
        </a-col>
        <a-col :span="6">
          <a-statistic
            title="盘点期间账面变动的行"
            :value="data.bookDriftLineCount ?? 0"
            :value-style="{ color: (data.bookDriftLineCount ?? 0) > 0 ? '#d46b08' : undefined }"
          />
        </a-col>
      </a-row>

      <a-table
        :data-source="data.lines || []"
        :pagination="false"
        bordered
        size="small"
        row-key="itemId"
        :scroll="{ x: 1500 }"
      >
        <a-table-column title="物料编码" data-index="productCode" :width="170" fixed="left" />
        <a-table-column title="产品名称" data-index="productName" :width="160" />
        <a-table-column title="单位" data-index="productUnitName" :width="70" />
        <a-table-column title="仓库" :width="150">
          <template #default="{ record }">
            {{ record.warehouseName || `#${record.warehouseId}` }}
            <div v-if="!record.wmsWarehouseCode" class="text-xs text-orange-500">
              未映射 WMS 仓库
            </div>
          </template>
        </a-table-column>
        <a-table-column title="① ERP 账面" :width="110" align="right">
          <template #default="{ record }">{{ num(record.bookCount) }}</template>
        </a-table-column>
        <a-table-column title="① 账面快照" :width="110" align="right">
          <template #default="{ record }">
            {{ num(record.snapshotBookCount) }}
          </template>
        </a-table-column>
        <a-table-column title="② ERP 实盘" :width="110" align="right">
          <template #default="{ record }">{{ num(record.actualCount) }}</template>
        </a-table-column>
        <a-table-column title="③ WMS 实物" :width="130" align="right">
          <template #default="{ record }">
            <span v-if="record.mirrorAvailable">{{ num(record.wmsCount) }}</span>
            <a-tag v-else color="default">无数据</a-tag>
          </template>
        </a-table-column>
        <a-table-column title="盘点盈亏" :width="110" align="right">
          <template #default="{ record }">
            <span :class="diffClass(record.checkDiff)">{{
              num(record.checkDiff)
            }}</span>
          </template>
        </a-table-column>
        <a-table-column title="账面变动" :width="110" align="right">
          <template #default="{ record }">
            <span :class="diffClass(record.bookDrift)">
              {{ num(record.bookDrift) }}
              <a-tag v-if="record.bookDriftDetected" color="orange">已变</a-tag>
            </span>
          </template>
        </a-table-column>
        <a-table-column title="WMS 需调整" :width="130" align="right">
          <template #default="{ record }">
            <span v-if="record.mirrorAvailable" :class="diffClass(record.wmsVsActual)">
              {{ num(record.wmsVsActual) }}
              <a-tag v-if="record.wmsAdjustRequired" color="red">需调整</a-tag>
            </span>
            <span v-else class="text-gray-400">-</span>
          </template>
        </a-table-column>
        <a-table-column title="账实差" :width="110" align="right">
          <template #default="{ record }">
            <span :class="diffClass(record.wmsVsBook)">
              {{ num(record.wmsVsBook) }}
            </span>
          </template>
        </a-table-column>
        <a-table-column title="镜像同步时间" :width="170">
          <template #default="{ record }">
            {{ record.wmsSyncTime || '-' }}
          </template>
        </a-table-column>
      </a-table>

      <div class="mt-3 text-xs text-gray-500">
        口径说明：盘点盈亏 = ERP 实盘 − 账面快照（ERP 侧调整依据）；账面变动 = 当前 ERP
        账面 − 账面快照（非 0 表示盘点期间账面已变动，结果可能过期）；WMS 需调整量 = WMS
        实物 − ERP 实盘（WMS 执行调整的输入）；账实差 = WMS 实物 − 当前 ERP 账面。
      </div>
    </a-spin>
  </Modal>
</template>
