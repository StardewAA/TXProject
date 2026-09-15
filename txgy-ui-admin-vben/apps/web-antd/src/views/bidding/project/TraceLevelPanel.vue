<script setup lang="ts">
/* 项目台账 · 追溯抽屉栈——单层面板（抽屉栈由 project/index.vue 持有，本组件只渲染"某一层"）
 * 口径（2026-09-14 定稿：抽屉可层叠、两种入口并存）：
 *   · 单击一行「拉出下级」→ 父组件 push 新一层抽屉（L1 项目 → L2 招标项目 → L3 标段）；
 *   · 行内「详情 ↗」→ 打开通用实体详情页（/bidding/entity/:type/:code，router 跳转，不挂菜单）；
 *   · L3 标段层：traceBidSection 一次取回「关联内部公告」，点公告行 → 弹详情（NoticeHtmlView，含附件/版式 PDF）；
 *   · 外部公告行「预览」→ 复用 noticeExternal DetailModal（审核权限 bidding:notice-external:update 由父组件判权）。
 * 层级由后端出参 queryType 决定，前端不另设状态；返回用「‹ 返回上级」逐层退栈。 */
import { computed, h } from 'vue';

import type { ProjectApi } from '#/api/bidding/project';

import { fmtDateTime } from '../certificate/format';
import { useCertMeta } from '../certificate/useCertMeta';
import CopyText from '../components/CopyText.vue';

export interface TraceLevel {
  key: number;
  queryType: 'BID_SECTION' | 'PROJECT' | 'TENDER_PROJECT';
  queryCode: string;
  label: string;
  open: boolean;
  trace: null | ProjectApi.Trace;
  loading: boolean;
}

const props = defineProps<{ level: TraceLevel; isTop: boolean }>();
const emit = defineEmits<{
  (e: 'drill', v: { type: 'BID_SECTION' | 'TENDER_PROJECT'; code: string; label: string }): void;
  (e: 'detail', v: { type: 'BID_SECTION' | 'PROJECT' | 'TENDER_PROJECT'; code: string }): void;
  (e: 'notice', row: ProjectApi.TraceNotice): void;
  (e: 'external', row: ProjectApi.TraceExternalNotice): void;
  (e: 'close'): void;
}>();

const { dictLabel, dictTag } = useCertMeta();

const trace = computed(() => props.level.trace);
const isTender = computed(() => props.level.queryType === 'TENDER_PROJECT');
const isSection = computed(() => props.level.queryType === 'BID_SECTION');
const isProject = computed(() => props.level.queryType === 'PROJECT');
/** 招标项目层自身信息（从出参 tenderProjects 按 queryCode 取，无则首条） */
const tenderSelf = computed(() => {
  const list = trace.value?.tenderProjects ?? [];
  const code = trace.value?.queryCode ?? '';
  return list.find((t) => (t.tenderProjectCode ?? '') === code) ?? list[0];
});
/** 标段层自身信息（从出参 sections 按 queryCode 取） */
const selfSection = computed(() => {
  const list = trace.value?.sections ?? [];
  const code = trace.value?.queryCode ?? '';
  return list.find((s) => (s.bidSectionCode ?? '') === code) ?? list[0];
});
const fieldEntries = computed(() => {
  const f = (trace.value?.project?.fields ?? {}) as Record<string, unknown>;
  return Object.entries(f).filter(([, v]) => v !== null && v !== undefined && v !== '');
});
</script>

<template>
  <!-- 层级查询说明 + 返回/打开详情 -->
  <div class="mb-2 flex items-center justify-between gap-2">
    <div class="ct-panel-meta">
      查询：{{ level.queryCode }}（{{ level.queryType }}）
    </div>
    <div class="flex items-center gap-3">
      <a v-if="!isTop" class="text-xs text-blue-600" @click="emit('close')">‹ 返回上级</a>
      <a
        class="text-xs text-blue-600"
        :class="{ 'cursor-not-allowed': isSection }"
        :title="isSection ? '标段新建/修改已收敛到标段详情页（菜单 6772 退场）' : '打开该实体详情页'"
        @click="!(isSection) && emit('detail', { type: isTender ? 'TENDER_PROJECT' : isSection ? 'BID_SECTION' : 'PROJECT', code: isTender ? (tenderSelf?.tenderProjectCode ?? level.queryCode) : isSection ? (selfSection?.bidSectionCode ?? level.queryCode) : (trace?.queryCode ?? level.queryCode) })"
      >详情 ↗ <span v-if="isProject || isTender" class="text-muted-foreground">（登记/编辑）</span></a>
    </div>
  </div>

  <a-spin :spinning="level.loading">
    <template v-if="trace">
      <!-- 统计（各域计数 + 最近动态） -->
      <a-descriptions v-if="trace.stats" :column="4" size="small" bordered class="mb-3">
        <a-descriptions-item label="关联招标项目">{{ trace.stats.tenderProjectCount ?? 0 }}</a-descriptions-item>
        <a-descriptions-item label="关联标段">{{ trace.stats.sectionCount ?? 0 }}</a-descriptions-item>
        <a-descriptions-item label="内部公告">{{ trace.stats.noticeCount ?? 0 }}</a-descriptions-item>
        <a-descriptions-item label="外部公告">{{ trace.stats.externalNoticeCount ?? 0 }}</a-descriptions-item>
        <a-descriptions-item label="备案记录">{{ trace.stats.filingCount ?? 0 }}</a-descriptions-item>
        <a-descriptions-item label="关注项">{{ trace.stats.followCount ?? 0 }}</a-descriptions-item>
        <a-descriptions-item label="各域最近动态" :span="4">
          {{ fmtDateTime(trace.stats.latestDynamicTime) }}
        </a-descriptions-item>
      </a-descriptions>

      <!-- 自身信息块（按层：项目 / 招标项目 / 标段） -->
      <template v-if="isTender">
        <div class="ct-panel-t mb-2">招标项目基本信息</div>
        <a-descriptions :column="2" size="small" bordered class="mb-3">
          <a-descriptions-item label="招标项目编号">{{ trace.queryCode || '—' }}</a-descriptions-item>
          <a-descriptions-item label="名称">{{ tenderSelf?.tenderProjectName || '—' }}</a-descriptions-item>
          <a-descriptions-item label="招标方式">{{ tenderSelf?.tenderMode || '—' }}</a-descriptions-item>
          <a-descriptions-item label="招标人">{{ tenderSelf?.tendererName || '—' }}</a-descriptions-item>
          <a-descriptions-item label="所属项目">{{ tenderSelf?.projectCode || '—' }}</a-descriptions-item>
          <a-descriptions-item label="管理态">{{ tenderSelf?.state || '—' }}</a-descriptions-item>
        </a-descriptions>
      </template>

      <template v-else-if="isSection">
        <div class="ct-panel-t mb-2">标段基本信息</div>
        <a-descriptions :column="2" size="small" bordered class="mb-3">
          <a-descriptions-item label="标段编号">{{ trace.queryCode || '—' }}</a-descriptions-item>
          <a-descriptions-item label="名称">{{ selfSection?.bidSectionName || '—' }}</a-descriptions-item>
          <a-descriptions-item label="所属招标项目">{{ selfSection?.tenderProjectCode || '—' }}</a-descriptions-item>
          <a-descriptions-item label="招标方式">{{ selfSection?.bidTenderMode || '—' }}</a-descriptions-item>
          <a-descriptions-item label="合同估算价">{{ selfSection?.contractReckonPrice || '—' }}<template v-if="selfSection?.currencyCode"> {{ selfSection.currencyCode }}</template></a-descriptions-item>
          <a-descriptions-item label="管理态">{{ selfSection?.state || '—' }}</a-descriptions-item>
        </a-descriptions>
        <div class="ct-panel-t mb-2">所属项目 / 招标项目</div>
        <a-descriptions :column="2" size="small" bordered class="mb-3">
          <a-descriptions-item label="项目编号">{{ trace.project?.projectCode || '—' }}</a-descriptions-item>
          <a-descriptions-item label="项目名称">{{ trace.project?.projectName || '—' }}</a-descriptions-item>
          <a-descriptions-item label="招标项目编号">{{ trace.tenderProjects?.[0]?.tenderProjectCode || '—' }}</a-descriptions-item>
          <a-descriptions-item label="招标项目名称">{{ trace.tenderProjects?.[0]?.tenderProjectName || '—' }}</a-descriptions-item>
        </a-descriptions>
      </template>

      <template v-else>
        <div class="ct-panel-t mb-2">项目基本信息</div>
        <a-descriptions :column="2" size="small" bordered class="mb-3">
          <a-descriptions-item label="项目编号">{{ trace.project?.projectCode || '—' }}</a-descriptions-item>
          <a-descriptions-item label="项目名称">{{ trace.project?.projectName || '—' }}</a-descriptions-item>
          <a-descriptions-item label="地区编码">{{ trace.project?.regionCode || '—' }}</a-descriptions-item>
          <a-descriptions-item label="法人">{{ trace.project?.legalPerson || '—' }}</a-descriptions-item>
          <a-descriptions-item label="投资代码">{{ trace.project?.investProjectCode || '—' }}</a-descriptions-item>
          <a-descriptions-item label="审批文号">{{ trace.project?.approvalDocNo || '—' }}</a-descriptions-item>
          <a-descriptions-item label="登记时间">{{ fmtDateTime(trace.project?.createTime) }}</a-descriptions-item>
          <a-descriptions-item label="管理态">{{ trace.project?.state || '—' }}</a-descriptions-item>
        </a-descriptions>

        <div class="ct-panel-t mb-2">数据集字段（ProjectVO）</div>
        <a-descriptions v-if="fieldEntries.length" :column="2" size="small" bordered class="mb-3">
          <a-descriptions-item v-for="[k, v] in fieldEntries" :key="k" :label="k">
            {{ typeof v === 'object' ? JSON.stringify(v) : v }}
          </a-descriptions-item>
        </a-descriptions>
        <div v-else class="ct-tip mb-3">该记录未带数据集字段（fields 为空）</div>
      </template>

      <!-- 关联招标项目（行：拉出下级 / 详情） -->
      <div class="ct-panel-t mb-2">关联招标项目（{{ trace.tenderProjects?.length ?? 0 }}）</div>
      <a-table
        class="ct-table mb-3"
        :columns="[
          { title: '招标项目编号', customRender: ({ text }: { text?: null | number | string }) => h(CopyText, { text, label: '招标项目编号' }), dataIndex: 'tenderProjectCode', key: 'code', width: 200 },
          { title: '名称', dataIndex: 'tenderProjectName', key: 'name' },
          { title: '招标方式', dataIndex: 'tenderMode', key: 'mode', width: 120 },
          { title: '招标人', dataIndex: 'tendererName', key: 'tenderer', width: 180 },
          { title: '操作', key: 'op', width: 120 },
        ]"
        :data-source="trace.tenderProjects ?? []"
        :pagination="false"
        row-key="id"
        size="small"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'op'">
            <a-space size="small">
              <!-- 单击行拉下级抽屉（L2 招标项目 → L3 标段由该层再钻） -->
              <a class="text-xs text-blue-600" @click="emit('drill', { type: 'TENDER_PROJECT', code: record.tenderProjectCode, label: record.tenderProjectName || record.tenderProjectCode })">拉出下级</a>
              <a class="text-xs text-blue-600" @click="emit('detail', { type: 'TENDER_PROJECT', code: record.tenderProjectCode })">详情 ↗</a>
            </a-space>
          </template>
        </template>
        <template #emptyText><div class="ct-empty">无关联招标项目</div></template>
      </a-table>

      <!-- 关联标段（行：拉出下级 / 详情；仅招标项目层以下有意义，项目层也展示便于直达） -->
      <div class="ct-panel-t mb-2">关联标段（{{ trace.sections?.length ?? 0 }}）</div>
      <a-table
        class="ct-table mb-3"
        :columns="[
          { title: '标段编号', customRender: ({ text }: { text?: null | number | string }) => h(CopyText, { text, label: '标段编号' }), dataIndex: 'bidSectionCode', key: 'code', width: 200 },
          { title: '名称', dataIndex: 'bidSectionName', key: 'name' },
          { title: '所属招标项目', dataIndex: 'tenderProjectCode', key: 'tp', width: 190 },
          { title: '合同估算价', dataIndex: 'contractReckonPrice', key: 'price', width: 120 },
          { title: '操作', key: 'op', width: 120 },
        ]"
        :data-source="trace.sections ?? []"
        :pagination="false"
        row-key="id"
        size="small"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'op'">
            <a-space size="small">
              <a class="text-xs text-blue-600" @click="emit('drill', { type: 'BID_SECTION', code: record.bidSectionCode, label: record.bidSectionName || record.bidSectionCode })">拉出下级</a>
              <a class="text-xs text-blue-600" @click="emit('detail', { type: 'BID_SECTION', code: record.bidSectionCode })">详情 ↗</a>
            </a-space>
          </template>
        </template>
        <template #emptyText><div class="ct-empty">无关联标段</div></template>
      </a-table>

      <!-- 关联公告（内部公告）——L3 标段层重点：traceBidSection.notices -->
      <div class="ct-panel-t mb-2">关联公告（内部公告，{{ trace.notices?.length ?? 0 }}）</div>
      <a-table
        class="ct-table mb-3"
        :columns="[
          { title: '公告编号', customRender: ({ text }: { text?: null | number | string }) => h(CopyText, { text, label: '公告编号' }), dataIndex: 'noticeCode', key: 'code', width: 200 },
          { title: '标题', dataIndex: 'title', key: 'title' },
          { title: '类型', dataIndex: 'noticeTypeName', key: 'type', width: 130 },
          { title: '发布时间', dataIndex: 'publishTime', key: 'publishTime', width: 160 },
          { title: '操作', key: 'op', width: 90 },
        ]"
        :data-source="trace.notices ?? []"
        :pagination="false"
        row-key="id"
        size="small"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'publishTime'">{{ fmtDateTime(record.publishTime) }}</template>
          <template v-else-if="column.key === 'op'">
            <!-- 点公告行 → 弹出详情（NoticeHtmlView：附件 / 版式 PDF / 招标文件获取指引） -->
            <a class="text-xs text-blue-600" @click="emit('notice', record)">详情</a>
          </template>
        </template>
        <template #emptyText><div class="ct-empty">无关联公告</div></template>
      </a-table>

      <!-- 外部公告（只读 + 预览入口，审核权限由父组件判权） -->
      <div class="ct-panel-t mb-2">外部公告（{{ trace.externalNotices?.length ?? 0 }}）</div>
      <a-table
        class="ct-table mb-3"
        :columns="[
          { title: '标题', dataIndex: 'title', key: 'title' },
          { title: '信息源', dataIndex: 'sourceName', key: 'sourceName', width: 150 },
          { title: '内容类型', dataIndex: 'contentType', key: 'contentType', width: 120 },
          { title: '状态', dataIndex: 'status', key: 'status', width: 120 },
          { title: '审核', dataIndex: 'auditStatus', key: 'auditStatus', width: 120 },
          { title: '发布时间', dataIndex: 'publishTime', key: 'publishTime', width: 150 },
          { title: '操作', key: 'op', width: 80 },
        ]"
        :data-source="trace.externalNotices ?? []"
        :pagination="false"
        row-key="id"
        size="small"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'publishTime'">{{ fmtDateTime(record.publishTime) }}</template>
          <template v-else-if="column.key === 'contentType'">
            {{ dictLabel('notice_external_content_type', record.contentType) }}
          </template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="dictTag('notice_external_status', record.status).color">
              {{ dictTag('notice_external_status', record.status).label }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'auditStatus'">
            <a-tag :color="dictTag('notice_external_audit_status', record.auditStatus).color">
              {{ dictTag('notice_external_audit_status', record.auditStatus).label }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'op'">
            <a class="text-xs text-blue-600" @click="emit('external', record)">预览</a>
          </template>
        </template>
        <template #emptyText><div class="ct-empty">无关联外部公告</div></template>
      </a-table>

      <!-- 备案 / 关注 / 参与主体（只读留痕） -->
      <div class="ct-panel-t mb-2">备案记录（{{ trace.filings?.length ?? 0 }}）</div>
      <a-table
        class="ct-table mb-3"
        :columns="[
          { title: '备案类型', dataIndex: 'filingType', key: 'filingType', width: 140 },
          { title: '状态', dataIndex: 'status', key: 'status', width: 140 },
          { title: '提交时间', dataIndex: 'submitTime', key: 'submitTime', width: 170 },
          { title: '项目编号', dataIndex: 'projectCode', key: 'projectCode', width: 200 },
          { title: '招标项目编号', dataIndex: 'tenderProjectCode', key: 'tenderProjectCode' },
        ]"
        :data-source="trace.filings ?? []"
        :pagination="false"
        row-key="id"
        size="small"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'submitTime'">{{ fmtDateTime(record.submitTime) }}</template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="dictTag('filing_status', record.status).color">
              {{ dictTag('filing_status', record.status).label }}
            </a-tag>
          </template>
        </template>
        <template #emptyText><div class="ct-empty">无关联备案</div></template>
      </a-table>

      <div class="ct-panel-t mb-2">关注项（{{ trace.follows?.length ?? 0 }}）</div>
      <a-table
        class="ct-table mb-3"
        :columns="[
          { title: '名称', dataIndex: 'displayName', key: 'displayName' },
          { title: '关注类型', dataIndex: 'followType', key: 'followType', width: 120 },
          { title: '状态', dataIndex: 'followStatus', key: 'followStatus', width: 110 },
          { title: '最近动态', dataIndex: 'latestDynamicTime', key: 'latestDynamicTime', width: 170 },
          { title: '未读', dataIndex: 'unreadCount', key: 'unreadCount', width: 90 },
        ]"
        :data-source="trace.follows ?? []"
        :pagination="false"
        row-key="id"
        size="small"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'latestDynamicTime'">
            {{ fmtDateTime(record.latestDynamicTime) }}
          </template>
          <template v-else-if="column.key === 'unreadCount'">
            <a-badge v-if="Number(record.unreadCount ?? 0) > 0" :count="record.unreadCount" :overflow-count="99" />
            <span v-else>—</span>
          </template>
        </template>
        <template #emptyText><div class="ct-empty">无关注项</div></template>
      </a-table>

      <div class="ct-panel-t mb-2">参与主体（{{ trace.subjects?.length ?? 0 }}）</div>
      <a-table
        class="ct-table"
        :columns="[
          { title: '来源', dataIndex: 'source', key: 'source', width: 190 },
          { title: '主体角色', dataIndex: 'subjectRole', key: 'subjectRole', width: 120 },
          { title: '主体名称', dataIndex: 'subjectName', key: 'subjectName' },
          { title: '证件类型', dataIndex: 'codeType', key: 'codeType', width: 130 },
          { title: '证件号', customRender: ({ text }: { text?: null | number | string }) => h(CopyText, { text, label: '证件号' }), dataIndex: 'code', key: 'code', width: 190 },
          { title: '授权状态', dataIndex: 'authStatus', key: 'authStatus', width: 120 },
          { title: '档案完备', dataIndex: 'profileComplete', key: 'profileComplete', width: 100 },
        ]"
        :data-source="trace.subjects ?? []"
        :pagination="false"
        row-key="code"
        size="small"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'profileComplete'">
            <a-tag :color="record.profileComplete ? 'green' : 'orange'">
              {{ record.profileComplete ? '完备' : '缺项' }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'source'">
            {{ dictLabel('trace_subject_source', record.source) }}
          </template>
          <template v-else-if="column.key === 'subjectRole'">
            {{ dictLabel('subject_type', record.subjectRole) }}
          </template>
          <template v-else-if="column.key === 'codeType'">
            {{ record.codeType || '—' }}
          </template>
          <template v-else-if="column.key === 'authStatus'">
            <a-tag v-if="record.authStatus" :color="dictTag('subject_auth_status', record.authStatus).color">
              {{ dictTag('subject_auth_status', record.authStatus).label }}
            </a-tag>
            <span v-else>—</span>
          </template>
        </template>
        <template #emptyText><div class="ct-empty">无参与主体</div></template>
      </a-table>

      <div class="ct-tip mt-3">
        说明：状态与类型均取自字典；抽屉可层叠（逐层内缩 + 去遮罩），关闭只关闭当前层、上层保留（栈语义）。
        「详情 ↗」打开通用实体详情页（登记/编辑/发布在那里做，不限项目/标段/公告）。
      </div>
    </template>
    <div v-else-if="!level.loading" class="ct-empty">未取到追溯数据</div>
  </a-spin>
</template>