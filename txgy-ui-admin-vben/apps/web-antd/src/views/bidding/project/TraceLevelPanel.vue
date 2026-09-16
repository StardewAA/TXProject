<script setup lang="ts">
import type { NoticeApi } from '#/api/bidding/notice';
import type { ProjectApi } from '#/api/bidding/project';

/* 项目台账 · 层级抽屉栈——单层面板（抽屉栈由 project/index.vue 持有，本组件只渲染"某一层"）
 * 层级模型（2026-09-15 定稿：项目 → 标段 → 公告，最多 3 层）：
 *   · L1 项目：项目基础信息 + 该项目下全部标段（traceProject.sections，跨招标项目聚合）；
 *       行「查看标段」→ 父组件 push L2；
 *   · L2 标段：标段基础信息 + 所属项目/招标项目摘要 + 该标段关联内部公告（traceBidSection.notices）；
 *       行「查看公告」→ 父组件 push L3；
 *   · L3 公告：公告详情（title/类型/状态/发布时间/发布人 + 正文 + 附件/版式 PDF，复用 NoticeHtmlView）。
 * 每个抽屉右下「详情 ↗」打开通用实体详情页（/bidding/entity/:type/:code，编辑/登记/发布在那里做）；
 * 抽屉内不做二次编辑，避免重复实现各域表单/附件/状态机（与实体详情页职责分离）。
 * 层级用系统生成/关联编号（projectCode/bidSectionCode/公告 id）驱动，前端不手工录入。 */
import { computed, h } from 'vue';

import { fmtDateTime } from '../certificate/format';
import CopyText from '../components/CopyText.vue';
import NoticeHtmlView from '../notice/NoticeHtmlView.vue';

export interface TraceLevel {
  key: number;
  queryType: 'BID_SECTION' | 'NOTICE' | 'PROJECT';
  queryCode: string;
  label: string;
  open: boolean;
  /** PROJECT / BID_SECTION 层：trace 快照（标段列表用 sections，公告列表用 notices） */
  trace: null | ProjectApi.Trace;
  /** NOTICE 层：公告详情快照 */
  notice: NoticeApi.Detail | null;
  loading: boolean;
}

const props = defineProps<{ crumbs: string[]; isTop: boolean; level: TraceLevel; }>();
const emit = defineEmits<{
  (e: 'drill', v: { code: string; label: string; type: 'BID_SECTION' | 'NOTICE'; }): void;
  (e: 'detail', v: { code: string; type: 'BID_SECTION' | 'NOTICE' | 'PROJECT'; }): void;
  (e: 'close'): void;
}>();

const trace = computed(() => props.level.trace);
const notice = computed(() => props.level.notice);
const isProject = computed(() => props.level.queryType === 'PROJECT');
const isSection = computed(() => props.level.queryType === 'BID_SECTION');
const isNotice = computed(() => props.level.queryType === 'NOTICE');

/** 项目层自身信息（traceProject.project） */
const selfProject = computed(() => trace.value?.project);
/** 标段层自身信息（traceBidSection.sections 按 queryCode 取） */
const selfSection = computed(() => {
  const list = trace.value?.sections ?? [];
  const code = trace.value?.queryCode ?? '';
  return list.find((s) => (s.bidSectionCode ?? '') === code) ?? list[0];
});

/** 详情页地址（编辑/登记/发布承载处，type 对齐 /bidding/entity/:type/:code） */
function detailLink(): { code: string; type: 'BID_SECTION' | 'NOTICE' | 'PROJECT'; } {
  if (isNotice.value) return { type: 'NOTICE', code: props.level.queryCode };
  if (isSection.value) return { type: 'BID_SECTION', code: selfSection.value?.bidSectionCode ?? props.level.queryCode };
  return { type: 'PROJECT', code: selfProject.value?.projectCode ?? props.level.queryCode };
}
</script>

<template>
  <div class="flex flex-col gap-3">
    <!-- 层级面包屑 + 返回上级 + 详情 ↗ -->
    <div class="flex items-center justify-between gap-2">
      <a-breadcrumb>
        <a-breadcrumb-item v-for="(c, idx) in crumbs" :key="idx">
          <span :class="{ 'text-muted-foreground': idx < crumbs.length - 1 }">{{ c }}</span>
        </a-breadcrumb-item>
      </a-breadcrumb>
      <div class="flex items-center gap-3">
        <a v-if="!isTop" class="text-xs text-blue-600" @click="emit('close')">‹ 返回上级</a>
        <a class="text-xs text-blue-600" @click="emit('detail', detailLink())">详情 ↗ <span class="text-muted-foreground">（登记/编辑）</span></a>
      </div>
    </div>

    <a-spin :spinning="level.loading">
      <!-- L3 公告 -->
      <template v-if="isNotice">
        <div v-if="notice" class="rounded-md border border-border p-3">
          <NoticeHtmlView :detail="notice" :show-head="false" />
        </div>
        <div v-else-if="!level.loading" class="ct-empty">未取到公告详情</div>
      </template>

      <!-- L1 项目 -->
      <template v-else-if="isProject && trace">
        <div class="ct-panel-t mb-2">项目基本信息</div>
        <a-descriptions :column="2" size="small" bordered class="mb-3">
          <a-descriptions-item label="项目编号">{{ selfProject?.projectCode || '—' }}</a-descriptions-item>
          <a-descriptions-item label="项目名称">{{ selfProject?.projectName || '—' }}</a-descriptions-item>
          <a-descriptions-item label="地区编码">{{ selfProject?.regionCode || '—' }}</a-descriptions-item>
          <a-descriptions-item label="法定代表人">{{ selfProject?.legalPerson || '—' }}</a-descriptions-item>
          <a-descriptions-item label="来源">{{ selfProject?.fundSource || '—' }}</a-descriptions-item>
          <a-descriptions-item label="管理态">
            <a-tag :color="(selfProject?.state ?? 'ACTIVE') === 'ACTIVE' ? 'green' : 'default'">
              {{ (selfProject?.state ?? 'ACTIVE') === 'ACTIVE' ? '在建' : '已关闭' }}
            </a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="登记时间">{{ fmtDateTime(selfProject?.createTime) }}</a-descriptions-item>
          <a-descriptions-item label="备注">{{ selfProject?.remark || '—' }}</a-descriptions-item>
        </a-descriptions>

        <div class="ct-panel-t mb-2">关联标段（{{ trace.sections?.length ?? 0 }}）</div>
        <a-table
          class="ct-table mb-3"
          :columns="[
            { title: '标段编号', customRender: ({ text }: { text?: null | number | string }) => h(CopyText, { text, label: '标段编号' }), dataIndex: 'bidSectionCode', key: 'code', width: 200 },
            { title: '标段名称', dataIndex: 'bidSectionName', key: 'name' },
            { title: '所属招标项目', dataIndex: 'tenderProjectCode', key: 'tp', width: 200 },
            { title: '招标方式', dataIndex: 'bidTenderMode', key: 'mode', width: 120 },
            { title: '合同估算价', dataIndex: 'contractReckonPrice', key: 'price', width: 140 },
            { title: '操作', key: 'op', width: 130 },
          ]"
          :data-source="trace.sections ?? []"
          :pagination="false"
          row-key="id"
          size="small"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'op'">
              <a-space size="small">
                <a class="text-xs text-blue-600" @click="emit('drill', { type: 'BID_SECTION', code: record.bidSectionCode, label: record.bidSectionName || record.bidSectionCode })">查看标段</a>
              </a-space>
            </template>
          </template>
          <template #emptyText><div class="ct-empty">无关联标段</div></template>
        </a-table>
      </template>

      <!-- L2 标段 -->
      <template v-else-if="isSection && trace">
        <div class="ct-panel-t mb-2">标段基本信息</div>
        <a-descriptions :column="2" size="small" bordered class="mb-3">
          <a-descriptions-item label="标段编号">{{ trace.queryCode || '—' }}</a-descriptions-item>
          <a-descriptions-item label="标段名称">{{ selfSection?.bidSectionName || '—' }}</a-descriptions-item>
          <a-descriptions-item label="所属招标项目">{{ selfSection?.tenderProjectCode || '—' }}</a-descriptions-item>
          <a-descriptions-item label="招标方式">{{ selfSection?.bidTenderMode || '—' }}</a-descriptions-item>
          <a-descriptions-item label="合同估算价">
            {{ selfSection?.contractReckonPrice || '—' }}<template v-if="selfSection?.currencyCode"> {{ selfSection.currencyCode }}</template>
          </a-descriptions-item>
          <a-descriptions-item label="管理态">
            <a-tag :color="(selfSection?.state ?? 'ACTIVE') === 'ACTIVE' ? 'green' : 'default'">
              {{ (selfSection?.state ?? 'ACTIVE') === 'ACTIVE' ? '在建' : '已关闭' }}
            </a-tag>
          </a-descriptions-item>
        </a-descriptions>

        <div class="ct-panel-t mb-2">所属项目 / 招标项目</div>
        <a-descriptions :column="2" size="small" bordered class="mb-3">
          <a-descriptions-item label="项目编号">{{ trace.project?.projectCode || '—' }}</a-descriptions-item>
          <a-descriptions-item label="项目名称">{{ trace.project?.projectName || '—' }}</a-descriptions-item>
          <a-descriptions-item label="招标项目编号">{{ trace.tenderProjects?.[0]?.tenderProjectCode || '—' }}</a-descriptions-item>
          <a-descriptions-item label="招标项目名称">{{ trace.tenderProjects?.[0]?.tenderProjectName || '—' }}</a-descriptions-item>
        </a-descriptions>

        <div class="ct-panel-t mb-2">关联公告（内部公告，{{ trace.notices?.length ?? 0 }}）</div>
        <a-table
          class="ct-table mb-3"
          :columns="[
            { title: '公告编号', customRender: ({ text }: { text?: null | number | string }) => h(CopyText, { text, label: '公告编号' }), dataIndex: 'noticeCode', key: 'code', width: 190 },
            { title: '标题', dataIndex: 'title', key: 'title' },
            { title: '类型', dataIndex: 'noticeTypeName', key: 'type', width: 130 },
            { title: '发布时间', dataIndex: 'publishTime', key: 'publishTime', width: 150 },
            { title: '操作', key: 'op', width: 110 },
          ]"
          :data-source="trace.notices ?? []"
          :pagination="false"
          row-key="id"
          size="small"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'publishTime'">{{ fmtDateTime(record.publishTime) }}</template>
            <template v-else-if="column.key === 'op'">
              <a-space size="small">
                <a class="text-xs text-blue-600" @click="emit('drill', { type: 'NOTICE', code: String(record.id), label: record.title || '公告' })">查看公告</a>
              </a-space>
            </template>
          </template>
          <template #emptyText><div class="ct-empty">无关联公告</div></template>
        </a-table>
      </template>

      <div v-else-if="!level.loading" class="ct-empty">未取到数据</div>

      <div class="ct-tip mt-3">
        说明：层级为「项目 → 标段 → 公告」，最多叠 3 层；关闭只关当前层（及以上），下层保留；
        「详情 ↗」打开通用实体详情页（登记/编辑/发布在那里做）。
      </div>
    </a-spin>
  </div>
</template>