<!-- ============================================================================
  关联公告（投标流程跟踪 → 本站 + 外站合并，2026-10-01 合并改造）
  ---------------------------------------------------------------------------
  【用途】从「投标流程跟踪」的列表行 / 内联详情页头打开，列出该行编号下**已公开**的
  关联公告 —— 本站公告（bidding_notice，管理端 /bidding/notice/page）与外站公告
  （bidding_notice_external，公开分页 /bidding/notice-external/page）**合并**展示，
  每行可在新窗口打开门户详情，免登录。

  【为什么合并】此前只查外站公开分页：流程行的公告大多是**本站公告**（三级编号扇出落
  bidding_notice），外站分页按行编号查恒 0 →「搜不到东西」。
  实测（2026-10-01，租户1）：行 projectCode=I610100202600000k001（= 招标项目编号）：
    · /bidding/notice/page?projectCode=…      → total 0  ❌ 该参数匹配 notice.project_code，不是招标项目编号
    · /bidding/notice/page?tenderProjectCode=… → total 1  ✅ 命中「最严格闭环公告-59900」
    · /bidding/notice/page?bidSectionCode=…    → total 1  ✅
  故本站侧 projectCode 维度**两路并发**（projectCode + tenderProjectCode）精确匹配、按 id 去重合并；
  外站侧同样两路（其 projectCode / tenderProjectCode 语义见 NoticeExternalPageReqVO）。
  （门户同款弹窗的「本站块」仍只按 projectCode 查 —— 那是 B4 落地前的口径，本页不受其限制。）

  【接口清单】
    · GET /bidding/notice/page            管理端分页（权限 bidding:notice:query；此处补 status=PUBLISHED 限已发布）
    · GET /bidding/notice-external/page   公开分页（@PermitAll + @TenantIgnore，只返回 SOCIAL+ACTIVE）
  【入参口径（务必勿混）】projectCode / tenderProjectCode / bidSectionCode 三者 AND，**一次只传一个**；
    本弹窗按维度发 2~4 个**单参请求**再在浏览器侧合并，绝不三参同传（AND 恒 0 命中）。
  【行点击】EXTERNAL → /portal/bidding/detail/:id；SELF → /portal/bidding/internal-detail/:id（都是门户静态路由，免登录）。

  【本组件只读】不提供审核 / 驳回 / 下线入口：那些动作属各公告管理页。
============================================================================ -->
<script lang="ts" setup>
import type { ProcessTrackItem } from '#/api/bidding/processTrack';

import { computed, ref, watch } from 'vue';

import { formatDate } from '@vben/utils';

import { Alert, Button, Empty, message, Modal, Table, Tag } from 'ant-design-vue';

import { requestClient } from '#/api/request';

import {
  type InternalNoticeRow,
  mergeNoticeRows,
  type NoticeLinkItem,
  pageRows,
  rowKey,
  toSelfNoticeRow,
} from './noticeLink';

/* ============ 口径字典 ============ */
/** 关联维度：processTrack 行上只有这两个编号（ProcessTrackItemVO 无 tenderProjectCode 字段） */
type DimKey = 'bidSectionCode' | 'projectCode';

/** 分页出参（两个 page 端点同形 PageResult） */
interface NoticeLinkPage {
  list?: NoticeLinkItem[];
  total?: number;
}

const props = defineProps<{
  /** 目标行：编号取自 projectCode（项目编号/招标项目编号）/ followKey（标段编号） */
  item?: null | ProcessTrackItem;
}>();
const open = defineModel<boolean>('open', { default: false });

/** 维度中文名：模板与提示文案同源取这里，避免两处各写一份 */
const DIM_LABEL: Record<DimKey, string> = {
  projectCode: '项目编号',
  bidSectionCode: '标段编号',
};

/** 单源抓取条数（维度是精确匹配，命中量极小；仅当极端超过此数才提示截断） */
const FETCH_PAGE_SIZE = 100;
/** 展示分页条数 */
const PAGE_SIZE = 5;

/* ============ 状态 ============ */
/** 当前查询维度（用户可在"项目编号查空"后显式切换） */
const dim = ref<DimKey>('projectCode');
const rows = ref<NoticeLinkItem[]>([]);
const total = ref(0);
const pageNo = ref(1);
const loading = ref(false);
const failed = ref(false);
const errMsg = ref('');
/** 合并缓存：跨源去重排序后的全量行（本地分页从此切） */
let mergedCache: NoticeLinkItem[] = [];
/** 任一来源触顶 FETCH_PAGE_SIZE：提示"仅展示各来源前 N 条" */
const truncated = ref(false);
/** 本次查询实际使用的编号（空 = 该维度没有编号，不发请求，直接空态） */
const usedCode = ref('');

const projectCode = computed(() => (props.item?.projectCode ?? '').trim());
const sectionCode = computed(() => (props.item?.followKey ?? '').trim());
/** 取某维度对应的编号 */
const codeOf = (d: DimKey): string =>
  d === 'projectCode' ? projectCode.value : sectionCode.value;

const title = computed(() =>
  props.item?.projectName ? `关联公告 · ${props.item.projectName}` : '关联公告',
);
const dimLabel = computed(() => DIM_LABEL[dim.value]);

/** 起始维度：有项目编号先按项目编号，否则退标段编号（两者都无 → 仍是项目编号，走"无编号"空态） */
function initialDim(): DimKey {
  return projectCode.value ? 'projectCode' : 'bidSectionCode';
}

/** 空态文案：区分「行上没有编号」/「该编号下没有公告」/「加载失败」三种原因 */
const emptyText = computed(() => {
  if (failed.value) return '加载失败，可点击上方「重试」重新拉取';
  if (!usedCode.value) {
    return projectCode.value || sectionCode.value
      ? `该行没有${dimLabel.value}，无法按此维度关联公告`
      : '该行既无项目编号也无标段编号，无法关联公告';
  }
  return `${dimLabel.value} ${usedCode.value} 下暂无已发布关联公告（本站 + 外站均已查询）`;
});

/** 项目编号查空且确有标段编号时，给出「改用标段编号」的显式入口 */
const canFallback = computed(
  () =>
    !loading.value &&
    !failed.value &&
    dim.value === 'projectCode' &&
    !!projectCode.value &&
    !!sectionCode.value &&
    sectionCode.value !== projectCode.value &&
    rows.value.length === 0,
);

/* ============ 表格列 ============ */
const columns = [
  { dataIndex: 'title', key: 'title', title: '公告标题' },
  { dataIndex: 'publishTime', key: 'publishTime', title: '发布时间', width: 160 },
  { key: 'source', title: '来源', width: 150 },
  { key: 'issuer', title: '招标人', width: 180 },
  { key: 'action', title: '操作', width: 110 },
];

/** epoch 毫秒 / 时间串 → 'YYYY-MM-DD HH:mm'（与 processTrack 页同口径，走 @vben/utils） */
function fmtT(v?: null | number | string): string {
  if (v === null || v === undefined || v === '') return '—';
  return formatDate(v, 'YYYY-MM-DD HH:mm') || '—';
}

/* ============ 加载（两源并发 → 去重排序 → 本地分页） ============ */
async function load(): Promise<void> {
  const code = codeOf(dim.value);
  usedCode.value = code;
  rows.value = [];
  total.value = 0;
  mergedCache = [];
  truncated.value = false;
  failed.value = false;
  errMsg.value = '';
  pageNo.value = 1;
  if (!code) return; // 该维度没有编号：保持空态，不发无意义请求
  loading.value = true;
  try {
    const base = { pageNo: 1, pageSize: FETCH_PAGE_SIZE };
    const extReq = (p: Record<string, string>) =>
      requestClient.get<NoticeLinkPage>('/bidding/notice-external/page', { params: { ...base, ...p } });
    const selfReq = (p: Record<string, string>) =>
      requestClient.get<NoticeLinkPage>('/bidding/notice/page', {
        // 本站侧只列"已发布"（与公开分页"已审核公开"同义）；管理端接口能看到草稿，这里按公开口径收敛
        params: { ...base, status: 'PUBLISHED', ...p },
      });
    /** 单参请求清单：projectCode 维度 4 路（本站/外站 × projectCode/tenderProjectCode），标段维度 2 路 */
    const jobs: Promise<NoticeLinkPage>[] =
      dim.value === 'projectCode'
        ? [
            extReq({ projectCode: code }),
            extReq({ tenderProjectCode: code }),
            selfReq({ projectCode: code }),
            selfReq({ tenderProjectCode: code }),
          ]
        : [extReq({ bidSectionCode: code }), selfReq({ bidSectionCode: code })];
    const res = await Promise.all(jobs);
    const extCount = dim.value === 'projectCode' ? 2 : 1;
    const extRows: NoticeLinkItem[] = res
      .slice(0, extCount)
      .flatMap((r) => (r.list ?? []).map((x) => ({ ...x, sourceKind: 'EXTERNAL' as const })));
    const selfRows: NoticeLinkItem[] = res
      .slice(extCount)
      .flatMap((r) => (r.list ?? []).map((x) => toSelfNoticeRow(x as InternalNoticeRow)));
    mergedCache = mergeNoticeRows(extRows, selfRows);
    rows.value = pageRows(mergedCache, 1, PAGE_SIZE);
    total.value = mergedCache.length;
    truncated.value = mergedCache.length >= FETCH_PAGE_SIZE * (dim.value === 'projectCode' ? 4 : 2);
  } catch (e) {
    failed.value = true;
    errMsg.value = (e as Error).message || String(e);
    message.error('关联公告加载失败：' + errMsg.value);
  } finally {
    loading.value = false;
  }
}

/** 用户显式切换维度：项目编号 → 标段编号（不自动合并，见文件头口径说明） */
function switchToSection(): void {
  dim.value = 'bidSectionCode';
  message.info('已改用标段编号 ' + sectionCode.value + ' 查询关联公告');
  void load();
}

/** 分页联动：合并缓存本地切片（不再回源） */
function onTableChange(p: { current?: number }): void {
  pageNo.value = p?.current ?? 1;
  rows.value = pageRows(mergedCache, pageNo.value, PAGE_SIZE);
}

/** 新窗口打开门户公告详情（来源不同路由不同：外站 /detail/:id，本站 /internal-detail/:id，均免登录） */
function openPortalDetail(id?: number | string, sourceKind: NoticeLinkItem['sourceKind'] = 'EXTERNAL'): void {
  if (id === null || id === undefined || id === '') return;
  const path =
    sourceKind === 'SELF'
      ? `/portal/bidding/internal-detail/${encodeURIComponent(String(id))}`
      : `/portal/bidding/detail/${encodeURIComponent(String(id))}`;
  const win = window.open(path, '_blank', 'noopener,noreferrer');
  if (!win) message.warning('浏览器拦截了新窗口：请允许弹出窗口后重试');
}

/** 打开时按行数据重置维度并拉取；关闭时清空，避免下次打开看到上一条的残留 */
watch(open, (v) => {
  if (v) {
    dim.value = initialDim();
    void load();
  } else {
    rows.value = [];
    total.value = 0;
    failed.value = false;
    errMsg.value = '';
    usedCode.value = '';
    pageNo.value = 1;
    mergedCache = [];
    truncated.value = false;
  }
});
</script>

<template>
  <Modal
    v-model:open="open"
    :title="title"
    :width="920"
    :footer="null"
    destroy-on-close
  >
    <div class="nl-tip">
      <span>查询维度：{{ dimLabel }}</span>
      <span v-if="usedCode" class="nl-code font-mono">{{ usedCode }}</span>
      <span class="nl-weak">
        （本站 + 外站合并展示，仅已发布/已审核公开的公告；点击标题在新窗口打开门户详情）
      </span>
    </div>

    <Alert
      v-if="failed"
      class="nl-mb"
      type="error"
      show-icon
      message="关联公告加载失败"
      :description="errMsg"
    />
    <Button v-if="failed" class="nl-mb" size="small" @click="load()">
      重试
    </Button>

    <Alert
      v-if="truncated && !failed"
      class="nl-mb"
      type="warning"
      show-icon
      message="命中较多，仅展示各来源前 {{ FETCH_PAGE_SIZE }} 条"
    />

    <div v-if="canFallback" class="nl-fallback">
      <span class="nl-weak">
        项目编号 {{ projectCode }} 下暂无公告。若本项目公告是按标段编号关联的，可显式改用标段编号再查一次
        （不会自动合并，避免混入其它项目的公告）。
      </span>
      <Button size="small" @click="switchToSection">
        改用标段编号 {{ sectionCode }} 查询
      </Button>
    </div>

    <Table
      :columns="columns"
      :data-source="rows"
      :loading="loading"
      :pagination="{
        current: pageNo,
        pageSize,
        total,
        showSizeChanger: false,
        size: 'small',
        showTotal: (t: number) => `共 ${t} 条`,
      }"
      :row-key="rowKey"
      size="small"
      @change="onTableChange"
    >
      <template #emptyText>
        <Empty :description="emptyText" />
      </template>

      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'title'">
          <a class="nl-link" @click="openPortalDetail(record.id, record.sourceKind)">
            {{ record.title || '—' }}
          </a>
          <span v-if="record.noticeTypeName" class="nl-weak"> · {{ record.noticeTypeName }}</span>
        </template>

        <template v-else-if="column.key === 'publishTime'">
          {{ fmtT(record.publishTime) }}
        </template>

        <template v-else-if="column.key === 'source'">
          <Tag v-if="record.sourceKind === 'SELF'" color="blue">本站</Tag>
          <span v-else>{{ record.sourceName || record.sourceCode || '外站' }}</span>
        </template>

        <template v-else-if="column.key === 'issuer'">
          {{ record.issuerName || '—' }}
        </template>

        <template v-else-if="column.key === 'action'">
          <a class="nl-link" @click="openPortalDetail(record.id, record.sourceKind)">门户详情 ↗</a>
        </template>
      </template>
    </Table>
  </Modal>
</template>

<style scoped>
/* 提示行：查询维度 + 实际使用的编号 + 口径说明 */
.nl-tip {
  display: flex;
  gap: 6px;
  align-items: center;
  flex-wrap: wrap;
  margin-bottom: 10px;
  font-size: 12.5px;
  color: hsl(var(--muted-foreground));
}
.nl-code {
  padding: 1px 6px;
  color: hsl(var(--foreground));
  background: hsl(var(--muted));
  border-radius: 4px;
}
.nl-weak {
  font-size: 12px;
  color: hsl(var(--muted-foreground));
}
.nl-mb {
  margin-bottom: 10px;
}
/* 项目编号查空 → 显式改用标段编号的入口 */
.nl-fallback {
  display: flex;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
  padding: 8px 10px;
  margin-bottom: 10px;
  background: hsl(var(--muted) / 60%);
  border-radius: var(--radius);
}
.nl-link {
  font-size: 13px;
  font-weight: 500;
  color: hsl(var(--primary));
}
.nl-link:hover {
  text-decoration: underline;
}
</style>
