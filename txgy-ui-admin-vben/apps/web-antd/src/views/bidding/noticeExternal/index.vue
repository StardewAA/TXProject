<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { NoticeExternalApi } from '#/api/bidding/noticeExternal';

import { reactive, ref } from 'vue';

import { Page, useVbenModal } from '@vben/common-ui';

import { Button, Drawer, Dropdown, message, Modal, Table, Tag } from 'ant-design-vue';

import { TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  aiExtractNotice,
  approveNotice,
  backfillPdfUrls,
  bodyStructureNotice,
  getManagePage,
  getOperateLogPage,
  reindexNoticeExternalIndex,
  rejectNotice,
  takeDownNotice,
} from '#/api/bidding/noticeExternal';

import { useCertMeta } from '../certificate/useCertMeta';
import {
  AUDIT_MAP,
  BODY_MAP,
  CONTENT_TYPE_MAP,
  EXTRACTION_MAP,
  fmtT,
  INDUSTRY_MAP,
  STATUS_MAP,
  useGridColumns,
  useGridFormSchema,
} from './data';
import DetailModal from './modules/detail.vue';

/* 字典（状态 / 审核 / 内容形态）：**字典优先、页面内映射表兜底**。
 * 兜底不是欠债而是必需——本页用户未必有 /bidding/meta/* 权限（后端 @PreAuthorize 只列 5 个电子证照/资质/人员域权限，
 * 已按 docs/前端规范-全域接口说明/12-字典元数据读取权限契约-2026-09-25.md 请后端放开）；
 * 纯字典化会让这些角色从"中文"退化成"英文 code"，所以两条路径都要在。
 * 读取发生在**渲染期**：字典加载完成后 Vue 自动重渲染为字典文案，无需刷新。 */
const { dictLabel, dictTag } = useCertMeta();

const [Detail, detailModalApi] = useVbenModal({
  connectedComponent: DetailModal,
  destroyOnClose: true,
});

/* ========== 操作日志（GET /bidding/notice-external/manage/operate-log/page） ==========
 * 回答"谁上传的、谁审查的、AI 什么时候审的"：数据源是平台 system_operate_log（不另建表），
 * 人工动作由 @LogRecord 写入、系统/AI 动作由 NoticeOperateLogHelper 写入。
 * AI 审查的理由与命中词就在 action / extra 里——这也是"审核留痕"在界面上的落点。
 * 只读：无任何写操作，不进操作锁、不做二次确认。 */
const logOpen = ref(false);
const logLoading = ref(false);
const logRows = ref<NoticeExternalApi.OperateLogItem[]>([]);
const logTotal = ref(0);
const logNotice = ref<NoticeExternalApi.ManageItem | null>(null);
const logQuery = reactive({ noticeId: '' as number | string, pageNo: 1, pageSize: 20 });

/** 操作人展示：后端已按 userId 翻译姓名；userType=0/未给且 userId=0 时是系统/AI 动作 */
function logUserText(row: NoticeExternalApi.OperateLogItem) {
  const name = (row.userName || '').trim();
  if (name) return name;
  if (String(row.userId ?? '') === '0') return '系统/AI';
  return row.userId != null ? '用户 #' + row.userId : '—';
}

const logColumns = [
  { title: '时间', dataIndex: 'createTime', key: 'createTime', width: 150 },
  { title: '操作人', key: 'user', width: 120 },
  { title: '操作', dataIndex: 'subType', key: 'subType', width: 160 },
  { title: '内容', dataIndex: 'action', key: 'action' },
  // 「结果」列已移除：后端 NoticeExternalOperateLogRespVO 无 success 字段（2026-09-25 复核，
  // 由"类型对等"门禁反向检查发现前端声明了 VO 没有的字段），留着只会永远显示「—」。
  { title: '来源', key: 'source', width: 200 },
];

async function loadLog() {
  logLoading.value = true;
  try {
    const page = await getOperateLogPage({ ...logQuery });
    logRows.value = page.list ?? [];
    logTotal.value = page.total ?? 0;
  } catch {
    // 失败提示由 requestClient 全局拦截器统一弹出，页面层不重复 toast
    logRows.value = [];
    logTotal.value = 0;
  } finally {
    logLoading.value = false;
  }
}

/** 打开某条公告的操作日志（行内"更多 ▾ → 操作日志"） */
function openLog(row: NoticeExternalApi.ManageItem) {
  logNotice.value = row;
  logQuery.noticeId = row.id!;
  logQuery.pageNo = 1;
  logRows.value = [];
  logTotal.value = 0;
  logOpen.value = true;
  void loadLog();
}

function onLogTableChange(pagination: { current?: number; pageSize?: number }) {
  logQuery.pageNo = pagination.current || 1;
  logQuery.pageSize = pagination.pageSize || 20;
  void loadLog();
}

/* 运维：重建外部公告检索索引（无入参；权限 bidding:notice-external:update） */
const reindexing = ref(false);

async function doReindex() {
  reindexing.value = true;
  try {
    const result = await reindexNoticeExternalIndex();
    message.success(result || '外部公告索引已重建');
  } catch {
    // 静默：403/失败由请求层提示
  } finally {
    reindexing.value = false;
  }
}

function refresh() {
  gridApi.query();
}

function tagOf(map: Record<string, { color: string; text: string; }>, v?: string) {
  const c = v ? map[v] : undefined;
  return c ? { text: c.text, color: c.color } : null;
}

/** 字典优先、映射表兜底。判定命中：dictTag 未命中时会回落成 label=原值，故 label !== v 即字典命中。
 *  （字典文案可后台调整；未命中或字典未加载 → 回落本页映射表，绝不出现空白或英文 code） */
function dictTagFirst(
  dictType: string,
  map: Record<string, { color: string; text: string; }>,
  v?: string,
) {
  if (!v) return null;
  const hit = dictTag(dictType, v);
  if (hit.label !== v) return { text: hit.label, color: hit.color };
  return tagOf(map, v);
}

const statusTagOf = (row: NoticeExternalApi.ManageItem) =>
  dictTagFirst('notice_external_status', STATUS_MAP, row.status);
const auditTagOf = (row: NoticeExternalApi.ManageItem) =>
  dictTagFirst('notice_external_audit_status', AUDIT_MAP, row.auditStatus);
const industryTagOf = (row: NoticeExternalApi.ManageItem) => tagOf(INDUSTRY_MAP, row.industryFlag);
const extractionTagOf = (row: NoticeExternalApi.ManageItem) =>
  tagOf(EXTRACTION_MAP, row.extractionStatus);
const bodyTagOf = (row: NoticeExternalApi.ManageItem) => tagOf(BODY_MAP, row.bodyBlocksStatus);

/** 行内操作执行锁（防双击重复提交；批量审核用独立的 approving） */
const acting = ref(false);

async function runAction(label: string, fn: () => Promise<unknown>) {
  if (acting.value) return;
  acting.value = true;
  const hideLoading = message.loading({ content: `${label}处理中…`, duration: 0 });
  try {
    await fn();
    message.success(`${label}成功`);
    // 行状态已变化：清空勾选，避免工具栏"批量审查通过（N）"计数与实际不符
    checkedRows.value = [];
    (gridApi.grid as unknown as undefined | { clearCheckboxRow?: () => void })?.clearCheckboxRow?.();
    refresh();
  } catch {
    // 失败提示由 requestClient 全局拦截器统一弹出，页面层不重复 toast
  } finally {
    hideLoading();
    acting.value = false;
  }
}

/** 审核通过：仅 NEW（NEW→ACTIVE，触发关注扇出 + ES 建索引） */
function handleApprove(row: NoticeExternalApi.ManageItem) {
  if (row.status !== 'NEW') {
    message.warning('仅“待审核(NEW)”公告可执行审核通过');
    return;
  }
  void runAction('审核通过', () => approveNotice(row.id!));
}


/** 下线：仅 ACTIVE（ACTIVE→INACTIVE，大厅展示随即消失） */
function handleTakeDown(row: NoticeExternalApi.ManageItem) {
  if (row.status !== 'ACTIVE') {
    message.warning('仅“已生效(ACTIVE)”公告可下线');
    return;
  }
  void runAction('下线', () => takeDownNotice(row.id!));
}



/** 行主操作：预览 + 当前状态下的关键动作（下线/审核通过）；次要动作全部收进“更多 ▾”避免按钮挤压 */
function rowActions(row: NoticeExternalApi.ManageItem): any[] {
  const acts: Array<Record<string, unknown>> = [
    { label: '预览', type: 'link', auth: ['bidding:notice-external:update'], disabled: acting.value || approving.value, onClick: () => handlePreview(row) },
  ];
  if (row.status === 'ACTIVE') {
    // 主操作按 status 优先：ACTIVE 行给“下线”（防御 ACTIVE+UNREVIEWED 并存时按钮错乱）
    acts.push({
      label: '下线',
      type: 'link',
      danger: true,
      auth: ['bidding:notice-external:update'],
      disabled: acting.value || approving.value,
      popConfirm: { title: '确认下线？（大厅展示随即消失）', confirm: () => handleTakeDown(row) },
    });
  } else if (row.auditStatus === 'UNREVIEWED' && row.status === 'NEW') {
    acts.push({
      label: '审核通过',
      type: 'link',
      auth: ['bidding:notice-external:update'],
      disabled: acting.value || approving.value,
      // 需求：点击审查不弹确认框（减少多余点击），直接执行幂等审核通过
      onClick: () => void handleApprove(row),
    });
  }
  return acts;
}

/** 更多菜单项（按行状态裁剪） */
function moreItems(row: NoticeExternalApi.ManageItem): Array<{ danger?: boolean; key: string; label: string; }> {
  const items: Array<{ danger?: boolean; key: string; label: string; }> = [];
  // 操作日志：只读且与行状态无关，故不参与"按状态裁剪"，恒定提供（审核留痕是审核页的核心诉求之一）
  items.push({ key: 'log', label: '操作日志' });
  if (row.auditStatus === 'UNREVIEWED') items.push({ key: 'reject', label: '驳回', danger: true });
  if (row.status === 'ACTIVE') items.push({ key: 'takeDown', label: '下线', danger: true });
  if (row.status !== 'ARCHIVED') {
    items.push({ key: 'aiExtract', label: 'AI 抽取' });
    items.push({ key: 'bodyStructure', label: '正文结构化' });
  }
  return items;
}

/** 更多菜单项点击：二次确认后执行 */
function onMoreAction(row: NoticeExternalApi.ManageItem, key: string): void {
  // 操作日志是只读查看，不进"操作进行中"锁，也不弹二次确认
  if (key === 'log') {
    openLog(row);
    return;
  }
  if (acting.value || approving.value) return; // 操作进行中：防重复触发
  const title: Record<string, string> = {
    reject: '确认驳回该公告？（将不对外展示）',
    takeDown: '确认下线？（大厅展示随即消失）',
    aiExtract: '执行 AI 结构化抽取（含行业判定回写）？可能需要一些时间。',
    bodyStructure: '执行正文结构化（解析/兜底排版为段落表格）？',
  };
  const label: Record<string, string> = { reject: '驳回', takeDown: '下线', aiExtract: 'AI 抽取', bodyStructure: '正文结构化' };
  const runner: Record<string, () => Promise<unknown>> = {
    reject: () => rejectNotice(row.id!),
    takeDown: () => takeDownNotice(row.id!),
    aiExtract: () => aiExtractNotice(row.id!),
    bodyStructure: () => bodyStructureNotice(row.id!),
  };
  const fn = runner[key];
  if (!fn) return;
  Modal.confirm({ title: title[key] || label[key], okText: '确认', cancelText: '取消', onOk: () => runAction(label[key] ?? key, fn) });
}

/** 勾选行（批量审查通过用） */
const checkedRows = ref<NoticeExternalApi.ManageItem[]>([]);
/**
 * 并发执行器（修复“逐条 await 串行”：批量/一键审核原本 N 条串 N 个请求，后端逐个等；
 * 改为固定小并发（batch=6）分片跑，既消除排队等待，又避免全量并发打爆审核/扇出/ES 链路）。
 * 幂等前提：approve 接口幂等（后端按 status=NEW 校验），失败项仍逐条归因上报。
 */
async function runConcurrent<T>(
  items: T[],
  worker: (item: T) => Promise<void>,
  onFail: (item: T) => void,
  batch = 6,
): Promise<number> {
  let ok = 0;
  for (let i = 0; i < items.length; i += batch) {
    const slice = items.slice(i, i + batch);
    const results = await Promise.allSettled(slice.map((it) => worker(it)));
    results.forEach((r, j) => {
      if (r.status === 'fulfilled') ok += 1;
      else {
        const item = slice[j];
        if (item != null) onFail(item);
      }
    });
  }
  return ok;
}

/** 审查类操作进行中（批量/一键共用，防重复触发） */
const approving = ref(false);
function handleRowCheckboxChange({
  records,
}: {
  records: NoticeExternalApi.ManageItem[];
}) {
  checkedRows.value = records || [];
}

/* PDF 归档回填（04-契约 §1：存量外部公告 pdf_download_url 回填，重下载外站 PDF → infra-file 归档；一次性人工触发，约几十秒）。
 * 业务/理由：采集 PDF 经文件服务归档后才回填可访问 URL；旧数据为空需触发本端点补全，此后门户 PDF 内嵌预览/下载才可用。
 * 权限 bidding:notice-external:update；POST /bidding/notice-external/backfill-pdf-urls 返回 total/ok/failed/skipped。 */
function handleBackfillPdf() {
  Modal.confirm({
    title: 'PDF 归档回填',
    content:
      '将重下载外部公告 PDF 并归档回填 pdf_download_url（14 条 GX 数据约几十秒；期间详情 PDF 预览由空变有）。' +
      '回填期间请勿重复触发。是否开始？',
    okText: '开始回填',
    cancelText: '取消',
    onOk: () =>
      runAction('PDF 归档回填', async () => {
        const res = await backfillPdfUrls();
        const s = res as { failed?: number; ok?: number; skipped?: number; total?: number; };
        message.info(
          '回填完成：总数 ' +
            (s?.total ?? '-') +
            '，成功 ' +
            (s?.ok ?? '-') +
            '，失败 ' +
            (s?.failed ?? '-') +
            '，跳过 ' +
            (s?.skipped ?? '-'),
        );
      }),
  });
}

/**
 * 批量审查通过：对勾选行逐条调用幂等 approve（不弹确认）。
 * 失败项汇总提示，可刷新后到列表重试。
 */
async function handleApproveBatch() {
  const targets = checkedRows.value.filter(
    (r) => r.auditStatus === 'UNREVIEWED' && r.status === 'NEW',
  );
  if (!targets.length) {
    message.info('勾选记录中没有“待审核(NEW)”公告');
    return;
  }
  approving.value = true;
  const hideLoading = message.loading({
    content: '批量审查通过中（' + targets.length + ' 条，逐条幂等提交）…',
    duration: 0,
  });
  const failed: Array<number | string> = [];
  try {
    const ok = await runConcurrent(
      targets,
      (t) => approveNotice(t.id!),
      (t) => failed.push(t.id!),
    );
    message.success(
      '批量审查通过完成：成功 ' +
        ok +
        ' 条' +
        (failed.length ? '，失败 ' + failed.length + ' 条（可到列表重试）' : ''),
    );
  } finally {
    hideLoading();
    approving.value = false;
    checkedRows.value = [];
    refresh();
  }
}

/**
 * 一键审查通过：拉取全部待审核(NEW)公告（按审核状态筛选翻页，上限 3 页 × 200），
 * 逐条幂等审核通过，同样不弹确认；操作员由后端 updater 审计记录。
 */
async function handleApproveAll() {
  approving.value = true;
  const hideLoading = message.loading({ content: '正在拉取全部待审核公告…', duration: 0 });
  try {
    const ids: Array<number | string> = [];
    for (let pageNo = 1; pageNo <= 3; pageNo += 1) {
      const res = await getManagePage({
        pageNo,
        pageSize: 200,
        auditStatus: 'UNREVIEWED',
      });
      const list = (res as { list?: NoticeExternalApi.ManageItem[] }).list || [];
      list.forEach((r) => {
        if (r.status === 'NEW' && r.id != null) ids.push(r.id);
      });
      if (list.length < 200) break;
    }
    hideLoading();
    if (!ids.length) {
      message.info('当前没有待审核(NEW)公告');
      return;
    }
    const hide2 = message.loading({
      content: '一键审查通过中（' + ids.length + ' 条）…',
      duration: 0,
    });
    const failed: Array<number | string> = [];
    try {
      const ok = await runConcurrent(
        ids,
        (id) => approveNotice(id),
        (id) => failed.push(id),
      );
      message.success(
        '一键审查通过完成：成功 ' +
          ok +
          ' 条' +
          (failed.length ? '，失败 ' + failed.length + ' 条（可到列表重试）' : ''),
      );
    } finally {
      hide2();
      refresh();
    }
  } catch {
    // 失败提示由 requestClient 全局拦截器统一弹出，页面层不重复 toast
  } finally {
    approving.value = false;
    checkedRows.value = [];
  }
}

/** 管理预览（manage/get 全状态：读完正文再操作） */
function handlePreview(row: NoticeExternalApi.ManageItem) {
  detailModalApi.setData(row).open();
}

const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: {
    schema: useGridFormSchema(),
  },
  gridOptions: {
    columns: useGridColumns({
      // 内容形态列：字典优先、CONTENT_TYPE_MAP 兜底（同上"两条路径都要在"）
      contentTypeLabel: (v?: string) => {
        if (!v) return '';
        const byDict = dictLabel('notice_external_content_type', v);
        return byDict === v ? CONTENT_TYPE_MAP[v] || v : byDict;
      },
    }),
    height: 'auto',
    proxyConfig: {
      ajax: {
        query: async ({ page }, formValues) => {
          return await getManagePage({
            pageNo: page.currentPage,
            pageSize: page.pageSize,
            ...formValues,
          });
        },
      },
    },
    rowConfig: {
      keyField: 'id',
      isHover: true,
    },
    toolbarConfig: {
      refresh: true,
      search: true,
    },
  } as VxeTableGridOptions<NoticeExternalApi.ManageItem>,
  gridEvents: {
    checkboxAll: handleRowCheckboxChange,
    checkboxChange: handleRowCheckboxChange,
  },
});
</script>

<template>
<!--
  管理端 · 外部公告审核管理（平台职能：采集入库→人工审核→进大厅）
  业务：平台运营审核爬虫采集入库的外部公告：检查状态(NEW)/抽取/正文/归属行业，通过后进入大厅对外可见（ACTIVE+SOCIAL）；
        支持单条与批量审查、一键全过。
  理由：外部采集数据未经核验不得直接面向公众展示（防虚假/违规信息误导）；审核通过=公示信息真实来源的把关（职能角色矩阵）。
    业务边界：本页属平台职能（G 轨，无租户语义）——职能角色矩阵（后端执行清单 §7）：爬虫管理/外部公告审核=平台职能，
        勿授企业租户；企业租户只使用发布/档案/跟踪等 T 轨功能。
    依据（法规/政府文件）：10号令 第12/13条（部门规章）——公开信息免费查阅与归类的合规来源（采集对象=依法公开公告）；电子办法 第9条（部门规章）。
    后端契约：01-接口逐一说明与设计依据.md §2（manage/page 审核列表）、外部公告-前端对接/02-字段枚举（状态/抽取/正文/行业、virusStatus）；
      GET /bidding/notice-external/manage/page、审核写接口（bidding:notice-external:update）。
-->

  <Page auto-content-height class="bid-ind">
    <Detail />

    <!-- 操作日志抽屉：谁上传/谁审查/AI 何时审（数据源=平台 system_operate_log，不另建表）。只读、无写操作。 -->
    <Drawer
      v-model:open="logOpen"
      class="bid-ind"
      :width="900"
      :title="logNotice ? `操作日志 · ${ logNotice.title || String(logNotice.id)}` : '操作日志'"
    >
      <div class="mb-2 text-xs font-medium text-gray-500">
        留痕来自平台操作日志（人工动作经 @LogRecord、系统/AI 动作经 NoticeOperateLogHelper 写入）；
        AI 审查的理由与命中词在「内容 / 拓展」里。
      </div>
      <Table
        size="small"
        row-key="id"
        :columns="logColumns"
        :data-source="logRows"
        :loading="logLoading"
        :pagination="{
          current: logQuery.pageNo,
          pageSize: logQuery.pageSize,
          total: logTotal,
          showSizeChanger: true,
          showTotal: (t: number) => `共 ${ t } 条`,
        }"
        @change="onLogTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'createTime'">{{ fmtT(record.createTime) }}</template>
          <template v-else-if="column.key === 'user'">
            <div>{{ logUserText(record) }}</div>
            <div v-if="record.userIp" class="text-xs text-gray-400">{{ record.userIp }}</div>
          </template>
          <template v-else-if="column.key === 'action'">
            <div>{{ record.action || '—' }}</div>
            <div v-if="record.extra" class="break-all text-xs text-gray-400" :title="record.extra">
              拓展：{{ record.extra }}
            </div>
          </template>
          <template v-else-if="column.key === 'source'">
            <div class="text-xs">{{ record.requestMethod || '—' }}</div>
            <div class="break-all text-xs text-gray-400" :title="record.requestUrl">
              {{ record.requestUrl || '' }}
            </div>
          </template>
        </template>
      </Table>
    </Drawer>

    <Grid table-title="外部公告审核管理（招投标）">
      <template #toolbar-tools>
        <Button :loading="reindexing" @click="doReindex">重建索引</Button>
        <TableAction
          :actions="[
            {
              label: checkedRows.length ? `批量审查通过（${ checkedRows.length }）` : '批量审查通过',
              type: 'primary',
              disabled: approving || checkedRows.length === 0,
              auth: ['bidding:notice-external:update'],
              onClick: () => void handleApproveBatch(),
            },
            {
              label: '一键审查通过',
              type: 'link',
              disabled: approving,
              auth: ['bidding:notice-external:update'],
              onClick: () => void handleApproveAll(),
            },
            {
              label: 'PDF 归档回填',
              type: 'link',
              danger: false,
              auth: ['bidding:notice-external:update'],
              onClick: () => handleBackfillPdf(),
            },
          ]"
        />
      </template>
      <template #col_industry="{ row }">
        <Tag v-if="industryTagOf(row)" :color="industryTagOf(row)!.color">{{ industryTagOf(row)!.text }}</Tag>
        <span v-else>—</span>
      </template>
      <template #col_status="{ row }">
        <div class="flex flex-wrap items-center justify-center gap-1">
          <Tag v-if="statusTagOf(row)" :color="statusTagOf(row)!.color">{{ statusTagOf(row)!.text }}</Tag>
          <Tag v-if="auditTagOf(row)" :color="auditTagOf(row)!.color">{{ auditTagOf(row)!.text }}</Tag>
          <span v-if="!statusTagOf(row) && !auditTagOf(row)">—</span>
        </div>
      </template>
      <template #col_proc="{ row }">
        <div class="flex flex-wrap items-center justify-center gap-1">
          <Tag v-if="extractionTagOf(row)" :color="extractionTagOf(row)!.color">抽：{{ extractionTagOf(row)!.text }}</Tag>
          <Tag v-if="bodyTagOf(row)" :color="bodyTagOf(row)!.color">正文：{{ bodyTagOf(row)!.text }}</Tag>
          <span v-if="!extractionTagOf(row) && !bodyTagOf(row)">—</span>
        </div>
      </template>
      <template #operation="{ row }">
        <div class="flex items-center">
          <TableAction :actions="rowActions(row)" />
          <template v-if="moreItems(row).length">
            <Dropdown trigger="click" :menu="{ items: moreItems(row), onClick: ({ key }) => onMoreAction(row, String(key)) }">
              <span class="cx-more-btn">更多 ▾</span>
            </Dropdown>
          </template>
        </div>
      </template>
    </Grid>
  </Page>
</template>

<style scoped>
.cx-more-btn {
  margin-left: 4px;
  padding: 1px 6px;
  font-size: 12px;
  line-height: 20px;
  color: #17539a;
  cursor: pointer;
  white-space: nowrap;
  user-select: none;
}
.cx-more-btn:hover {
  color: #1f6fd6;
}
</style>
