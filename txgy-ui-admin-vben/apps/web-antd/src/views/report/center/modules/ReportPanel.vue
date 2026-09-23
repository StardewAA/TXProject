<script lang="ts" setup>
import type { ReportCenterApi } from '#/api/report/center';

import type { CardState } from '../types';

import { computed } from 'vue';

const props = defineProps<{
  /** 当前选中的报表，为空表示尚未选择 */
  report: null | ReportCenterApi.Report;
  /** 该报表的查询状态 */
  card: CardState | null;
  /** 该报表是否正在导出 */
  exporting: boolean;
  /**
   * 是否嵌入抽屉等外部容器
   * 为 true 时去掉自身外框与内边距，避免与外层容器重复描边
   */
  embedded?: boolean;
}>();

const emit = defineEmits<{
  (e: 'dateChange', payload: DateChangePayload): void;
  (e: 'export', code: string): void;
  (e: 'pageChange', payload: { code: string; page: number }): void;
  (e: 'pageSizeChange', payload: { code: string; size: number }): void;
  (e: 'toggle', code: string): void;
  (e: 'view', code: string): void;
}>();

interface DateChangePayload {
  code: string;
  field: 'endDate' | 'startDate';
  value?: string;
}

const code = computed(() => props.report?.code ?? '');

/** 报表是否支持日期筛选 */
const hasDateParams = computed(
  () =>
    props.report?.params?.some(
      (param) => param.name === 'startDate' || param.name === 'endDate',
    ) ?? false,
);

/** 卡片内分页切片（pageSize <= 0 表示全部） */
const pagedRows = computed(() => {
  const card = props.card;
  if (!card) {
    return [];
  }
  if (card.pageSize <= 0) {
    return card.rows;
  }
  const start = (card.page - 1) * card.pageSize;
  return card.rows.slice(start, start + card.pageSize);
});

function handleDateChange(field: 'endDate' | 'startDate', dateString: string) {
  emit('dateChange', { code: code.value, field, value: dateString || undefined });
}
</script>

<template>
  <section class="rc-panel" :class="{ 'rc-panel-embedded': embedded }">
    <template v-if="report">
      <div class="rc-panel-head">
        <div class="rc-panel-title-wrap">
          <div class="rc-panel-title">
            <span class="rc-title-bar"></span>
            {{ report.name }}
          </div>
          <div class="rc-panel-desc">{{ report.description }}</div>
        </div>

        <div class="rc-panel-actions">
          <template v-if="hasDateParams">
            <span class="rc-field-label">日期</span>
            <a-date-picker
              :value="card?.startDate"
              placeholder="开始日期"
              size="large"
              value-format="YYYY-MM-DD"
              @change="(_: any, dateString: string) => handleDateChange('startDate', dateString)"
            />
            <span class="rc-field-sep">至</span>
            <a-date-picker
              :value="card?.endDate"
              placeholder="结束日期"
              size="large"
              value-format="YYYY-MM-DD"
              @change="(_: any, dateString: string) => handleDateChange('endDate', dateString)"
            />
          </template>
          <span v-else class="rc-no-param">全量数据，无需筛选</span>

          <a-button size="large" @click="emit('view', code)">
            查看数据
          </a-button>
          <a-button
            :loading="exporting"
            size="large"
            type="primary"
            @click="emit('export', code)"
          >
            导出 Excel
          </a-button>
        </div>
      </div>

      <div
        v-if="(card?.rows.length ?? 0) > 0 || card?.loading"
        class="rc-panel-table"
      >
        <div class="rc-table-bar">
          <a-button
            size="small"
            type="link"
            @click="emit('toggle', code)"
          >
            {{ card?.collapsed ? '展开 ▾' : '收起 ▴' }}
          </a-button>
        </div>
        <template v-if="!card?.collapsed">
          <a-table
            :columns="card?.columns"
            :data-source="pagedRows"
            :loading="card?.loading"
            :pagination="false"
            :row-key="(_record: any, index: number) => String(index)"
            :scroll="{ x: 'max-content' }"
            size="small"
          />
          <div class="rc-table-footer">
            <div class="rc-panel-left">
              <span class="rc-total">
                共 {{ card?.rows.length ?? 0 }} 条记录
              </span>
              <a-select
                :value="card?.pageSize"
                :options="[
                  { label: '6条/页', value: 6 },
                  { label: '10条/页', value: 10 },
                  { label: '20条/页', value: 20 },
                  { label: '全部/页', value: 0 },
                ]"
                size="small"
                style="width: 96px"
                @change="(size: number) => emit('pageSizeChange', { code, size })"
              />
            </div>
            <a-pagination
              v-if="(card?.pageSize ?? 0) > 0"
              :current="card?.page"
              :page-size="card?.pageSize"
              :show-size-changer="false"
              :total="card?.rows.length ?? 0"
              @change="(page: number) => emit('pageChange', { code, page })"
            />
          </div>
        </template>
        <div
          v-else
          class="rc-collapsed-tip"
          @click="emit('toggle', report.code)"
        >
          已收起，点击展开查看数据
        </div>
      </div>
      <div v-else-if="!card?.loading" class="rc-panel-empty">
        设置条件后点击「查看数据」，结果将显示在此处
      </div>
    </template>

    <div v-else class="rc-panel-placeholder">
      <div class="rc-panel-placeholder-main">请从左侧选择一张报表</div>
      <div class="rc-panel-placeholder-tip">
        可按名称、用途或报表内字段名搜索
      </div>
    </div>
  </section>
</template>

<style scoped>
.rc-panel {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  padding: 18px 22px;
  overflow-y: auto;
  background: #fff;
  border: 1px solid #e3e9f0;
  border-radius: 8px;
}

/* 嵌入抽屉等容器时：去掉自身外框与内边距，避免重复描边 */
.rc-panel-embedded {
  padding: 0;
  border: 0;
  border-radius: 0;
}

.rc-panel-head {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  align-items: flex-end;
  justify-content: space-between;
}

.rc-panel-title-wrap {
  min-width: 220px;
}

.rc-panel-title {
  display: flex;
  gap: 8px;
  align-items: center;
  font-size: 16px;
  font-weight: 600;
  color: #24344d;
}

/* 标题左侧蓝灰竖条，工业风层级锚点 */
.rc-title-bar {
  display: inline-block;
  width: 4px;
  height: 16px;
  background: #2f5480;
  border-radius: 2px;
}

.rc-panel-desc {
  margin-top: 6px;
  font-size: 13px;
  line-height: 1.5;
  color: #7b8794;
}

.rc-panel-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}

.rc-field-label {
  font-size: 13px;
  color: #4e5d6c;
}

.rc-field-sep {
  font-size: 13px;
  color: #9aa5b1;
}

.rc-no-param {
  font-size: 13px;
  color: #9aa5b1;
}

/* 结果区 */
.rc-panel-table {
  margin-top: 16px;
  padding-top: 8px;
  border-top: 1px dashed #e3e9f0;
}

.rc-table-bar {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  margin-bottom: 6px;
}

.rc-table-footer {
  display: flex;
  gap: 16px;
  align-items: center;
  justify-content: space-between;
  margin-top: 12px;
}

.rc-panel-left {
  display: flex;
  gap: 10px;
  align-items: center;
}

.rc-total {
  font-size: 13px;
  color: #4e5d6c;
}

.rc-collapsed-tip {
  padding: 10px 0 2px;
  font-size: 13px;
  color: #7b8794;
  cursor: pointer;
  user-select: none;
}

.rc-collapsed-tip:hover {
  color: #2f5480;
}

.rc-panel-table :deep(.ant-table-thead > tr > th) {
  color: #344356;
  background: #f3f6fa;
  font-weight: 600;
}

.rc-panel-empty {
  padding: 18px 0 4px;
  margin-top: 14px;
  font-size: 13px;
  color: #a5afbb;
  border-top: 1px dashed #eef1f5;
}

.rc-panel-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  min-height: 320px;
}

.rc-panel-placeholder-main {
  font-size: 15px;
  color: #7b8794;
}

.rc-panel-placeholder-tip {
  margin-top: 8px;
  font-size: 12px;
  color: #a5afbb;
}
</style>
