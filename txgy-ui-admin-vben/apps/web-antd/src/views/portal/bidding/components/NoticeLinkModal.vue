<!--
  门户 · 投标流程管理 →「关联公告」弹窗
  ---------------------------------------------------------------------------
  【用途】从「投标流程管理」的**关注项**行与**已建档流程**行打开，按该行的业务编号列出外部公告，
         点标题用 router.push 走门户路由 /portal/bidding/detail/:id（免登录，与公告大厅同一详情页）。

  【取数】复用**门户服务层** api.ts 的 fetchNoticePage（GET /bidding/notice-external/page）：
    · 维度 → 参数名：PROJECT→projectCode、TENDER_PROJECT→tenderProjectCode、SECTION→bidSectionCode
      （映射表见 DIM_OF_FOLLOW_TYPE，维度名/顺序仍取 api.ts 既有的 CODE_DIMS，不另造一份）；
    · ⚠️ 后端三个编号参数在**同一次请求里是 AND 关系**（同值同传恒 0 命中）——
      故本弹窗**一次只传一个**参数（paramsOf 只赋一个键），换维度由界面上的维度胶囊显式触发；
    · 分页 pageNo/pageSize（PAGE_SIZE=5：够看清「有没有公告」，又不会把弹窗撑得过长）。

  【空态分三种，文案各不相同（不能一律「暂无数据」）】
    ① plain   —— 该编号下暂无公开公告（查得到、只是没有）；
    ② error   —— 加载失败 + 「重试」按钮（可重试，不让用户以为是没有数据）；
    ③ lowcov  —— 该维度编号**覆盖率低**（低于 api.CODE_LOW_COVERAGE），搜不到属正常，并引导换个维度。
    另有 nocode —— 该行没有编号（不发无意义请求，直接说明）。

  【覆盖率口径】覆盖率取 api.ts 的 `CODE_DIMS.coverage`（ACTIVE 口径实测基线 88.7% / 1.0% / 15.0%，
    即大厅在展示的那批公告的有值率）—— 与「公告大厅·编号精确」提示同一份常量，不再各写一份。

  【标段编号的短码提示】后端 bidSectionCode 是 FIND_IN_SET 语义：实测传短码「001」会命中 183 条
    **别的项目**的同名标段公告（见下方 nl-hint）。所以选中该维度时显式提示，避免把别家公告读成本项目的。
-->
<script lang="ts" setup>
import type { InternalNoticeItem, NoticeCodeDim, NoticeExternalItem, NoticePageParams } from '../types';

import { computed, ref, watch } from 'vue';
import { useRouter } from 'vue-router';

import { IconifyIcon } from '@vben/icons';

import { Modal } from 'ant-design-vue';

import { CODE_DIMS, CODE_LOW_COVERAGE, fetchInternalNotices, fetchNoticePage } from '../api';
import { fmtDT } from '../format';

import '../styles/bid-ui.css';

const props = defineProps<{
  /** 业务编号（关注项 followKey / 流程档案 followKey） */
  followKey?: string;
  /** 关注维度：PROJECT / TENDER_PROJECT / SECTION（映射不到的维度按 SECTION 预选，用户可切换） */
  followType?: null | string;
  /** 行标题（只用于弹窗标题，便于确认查的是哪一行） */
  subjectName?: string;
}>();
/** 显隐由父组件 v-model:open 控制（与页面里其它弹窗同一种写法） */
const open = defineModel<boolean>('open', { default: false });

const router = useRouter();

/** 关注维度（notice-follow 的 followType）→ 后端编号参数（= NoticeCodeDim） */
const DIM_OF_FOLLOW_TYPE: Record<string, NoticeCodeDim> = {
  PROJECT: 'projectCode',
  SECTION: 'bidSectionCode',
  TENDER_PROJECT: 'tenderProjectCode',
};

/** 单页条数（编号关联公告是「有没有、最近几条」的场景，5 条足够，翻页可控总量） */
const PAGE_SIZE = 5;

/** 维度未知时的预选维度：流程档案的业务键落的是 bidSectionCode（见 useBiddingProcess.intentFromFollow），
 *  故默认按「标段编号」查，并在界面上说明这是预选、可切换（绝不静默猜一个当结论）。 */
const FALLBACK_DIM: NoticeCodeDim = 'bidSectionCode';

/* ============ 状态 ============ */
const dim = ref<NoticeCodeDim>(FALLBACK_DIM);
/** 维度来源：auto = 按该行的关注维度自动选定；unknown = 该行维度不可识别（按预选维度查）；
 *  manual = 用户手动切过。文案里如实说明，避免把「预选」说成「用户选择的」。 */
const dimSource = ref<'auto' | 'manual' | 'unknown'>('unknown');
const rows = ref<NoticeExternalItem[]>([]);
/* 本站公告（本平台发布）：与外部采集公告分两块列出 —— 两边的分页/维度口径不同，混成一个列表
 * 会出现"翻页翻到别人家第几页"的混乱。目前本站只能按**项目编号**查（后端 public/list 只支持
 * projectCode 一个编号入参，招标项目编号/标段编号待补，见契约《后端变更请求-本站公告三级编号
 * 跟踪扇出》B4），故只在项目编号维度下查，其余维度明确说明"当前维度只列外部公告"。 */
const selfRows = ref<InternalNoticeItem[]>([]);
const selfTotal = ref(0);
const selfFailed = ref(false);
const selfQueryable = computed(() => dim.value === 'projectCode');
const total = ref(0);
const pageNo = ref(1);
const loading = ref(false);
const failed = ref(false);
const errMsg = ref('');
/** 本次查询实际使用的编号（空 = 该行没有编号，不发请求） */
const queriedCode = ref('');

const code = computed(() => String(props.followKey ?? '').trim());
const meta = computed(() => metaOf(dim.value));
/** 项目编号维度元数据（低覆盖空态里用作"改查这个维度"的引导，覆盖率同样取 CODE_DIMS） */
const projectDimMeta = computed(() => CODE_DIMS.find((d) => d.dim === 'projectCode'));
/** 覆盖率取 api.ts 的 CODE_DIMS（ACTIVE 口径，全套只有这一份；本页不另立一份避免双口径漂移） */
const coverage = computed(() => CODE_DIMS.find((d) => d.dim === dim.value)?.coverage ?? 0);
const coverageText = computed(() => pct(coverage.value));
const lowCoverage = computed(() => coverage.value < CODE_LOW_COVERAGE);
const pageCount = computed(() => Math.max(1, Math.ceil(total.value / PAGE_SIZE)));
const title = computed(() => (props.subjectName ? `关联公告 · ${props.subjectName}` : '关联公告'));

/** 空态类型：四种文案互不相同的分支（见文件头） */
const emptyKind = computed<'error' | 'lowcov' | 'nocode' | 'plain'>(() => {
  if (failed.value) return 'error';
  if (!queriedCode.value) return 'nocode';
  return lowCoverage.value ? 'lowcov' : 'plain';
});

/* ============ 工具 ============ */
/** 覆盖率（0~1）→ 百分比文案 */
function pct(v: number): string {
  return `${(v * 100).toFixed(1)}%`;
}
/** 某维度的中文名（取 api.ts 的 CODE_DIMS；万一取不到则退回维度码本身，不编造文案） */
function metaOf(d: NoticeCodeDim): { label: string } {
  return CODE_DIMS.find((x) => x.dim === d) ?? { label: d };
}
/** 维度 → 请求参数：⚠ 只赋**一个**编号键（三参同传是 AND 关系，见文件头） */
function paramsOf(d: NoticeCodeDim, value: string, page: number): NoticePageParams {
  const base = { pageNo: page, pageSize: PAGE_SIZE };
  if (d === 'projectCode') return { ...base, projectCode: value };
  if (d === 'tenderProjectCode') return { ...base, tenderProjectCode: value };
  return { ...base, bidSectionCode: value };
}
/** 门户公告详情地址（静态路由 name=PortalBiddingDetail，免登录） */
function detailPath(row: NoticeExternalItem): string {
  return `/portal/bidding/detail/${encodeURIComponent(String(row.id))}`;
}

/* ============ 加载 ============ */
async function load(page = 1): Promise<void> {
  queriedCode.value = code.value;
  pageNo.value = page;
  rows.value = [];
  total.value = 0;
  failed.value = false;
  errMsg.value = '';
  if (!queriedCode.value) return; // 没有编号：保持空态，不发无意义请求
  loading.value = true;
  try {
    const [res] = await Promise.all([
      fetchNoticePage(paramsOf(dim.value, queriedCode.value, page)),
      loadSelf(),
    ]);
    rows.value = res.list ?? [];
    total.value = res.total ?? 0;
  } catch (e) {
    failed.value = true;
    errMsg.value = (e as Error)?.message || String(e);
  } finally {
    loading.value = false;
  }
}

/** 本站公告按项目编号精确查询（失败只影响本块，不影响外部公告列表；不重试不阻塞） */
async function loadSelf(): Promise<void> {
  selfRows.value = [];
  selfTotal.value = 0;
  selfFailed.value = false;
  if (!code.value || !selfQueryable.value) return;
  try {
    const res = await fetchInternalNotices({
      pageNo: 1,
      pageSize: PAGE_SIZE,
      projectCode: code.value,
    });
    selfRows.value = res.list ?? [];
    selfTotal.value = res.total ?? 0;
  } catch {
    selfFailed.value = true;
  }
}

/** 本站公告详情（门户公开页 /portal/bidding/internal-detail/:id） */
function selfDetailPath(row: InternalNoticeItem): string {
  return `/portal/bidding/internal-detail/${encodeURIComponent(String(row.id))}`;
}
function openSelfDetail(row: InternalNoticeItem): void {
  const id = row.id;
  if (id === null || id === undefined || id === '') return;
  void router.push(selfDetailPath(row));
}

/** 用户显式切换查询维度（一次只发一个维度的请求，绝不并发合并） */
function switchDim(d: NoticeCodeDim): void {
  if (d === dim.value) return;
  dim.value = d;
  dimSource.value = 'manual';
  void load(1);
}

/** 点标题 → 门户公告详情（router.push，与大厅同一 URL 口径；不开新窗口） */
function openDetail(row: NoticeExternalItem): void {
  const id = row.id;
  if (id === null || id === undefined || id === '') return;
  void router.push(detailPath(row));
}

/** 打开时按行数据重置维度并拉取；关闭时清空，避免下次打开看到上一条的残留 */
watch(open, (v) => {
  if (v) {
    const inferred = DIM_OF_FOLLOW_TYPE[String(props.followType ?? '').toUpperCase()];
    dim.value = inferred ?? FALLBACK_DIM;
    dimSource.value = inferred === undefined ? 'unknown' : 'auto';
    void load(1);
  } else {
    rows.value = [];
    total.value = 0;
    failed.value = false;
    errMsg.value = '';
    queriedCode.value = '';
    pageNo.value = 1;
    selfRows.value = [];
    selfTotal.value = 0;
    selfFailed.value = false;
  }
});
</script>

<template>
  <Modal
    v-model:open="open"
    :footer="null"
    :title="title"
    :width="720"
    destroy-on-close
    wrap-class-name="white-modal"
  >
    <div class="nl-body">
      <!-- 查询口径：实际使用的编号 + 维度胶囊（覆盖率随维度一并展示） -->
      <div class="nl-head">
        <div class="nl-code">
          <b>{{ meta.label }}</b>
          <span class="nl-code-val">{{ code || '—' }}</span>
        </div>
        <div class="nl-dims">
          <span class="nl-dims-label">按维度查询：</span>
          <button
            v-for="d in CODE_DIMS"
            :key="d.dim"
            class="bid-pill"
            :class="{ on: dim === d.dim }"
            type="button"
            @click="switchDim(d.dim)"
          >
            {{ d.label }}<span class="nl-cov">{{ pct(d.coverage) }}</span>
          </button>
        </div>
      </div>

      <div class="bid-muted">
        外部公告公开分页（只含已审核公开的公告）；三个编号参数在后端是 AND 关系，此处
        <b>一次只按一个维度查询</b>。
        <template v-if="dimSource === 'auto'">当前「{{ meta.label }}」按该行的关注维度自动选定，可切换。</template>
        <template v-else-if="dimSource === 'unknown'">
          该行未带可识别的关注维度，先按「{{ meta.label }}」预选查询，可切换到其它维度。
        </template>
        <template v-else>当前维度为手动选择。</template>
      </div>
      <div v-if="dim === 'bidSectionCode'" class="bid-muted">
        注意：标段编号在后端是 FIND_IN_SET 语义，短码（如「001」）会命中其它项目的同名标段
        ——列表里可能混入非本项目的公告，请以编号与项目名为准。
      </div>

      <!-- 本站公告（本平台发布）：与外部采集公告**分块列出**（两侧分页/维度口径不同）。
           目前本站只能按项目编号查（后端 public/list 只有 projectCode 一个编号入参，
           招标项目编号/标段编号待补 —— 契约《本站公告三级编号跟踪扇出》B4）。 -->
      <div v-if="selfQueryable" class="nl-self">
        <div class="nl-self-h">
          <span class="bid-chip bid-chip--self">本站</span>
          <span class="bid-muted">
            本平台发布的公开公告（按项目编号 <b>{{ queriedCode }}</b> 精确匹配）
            <template v-if="selfTotal > selfRows.length">· 共 {{ selfTotal }} 条，此处列出最新 {{ selfRows.length }} 条</template>
          </span>
        </div>
        <ul v-if="selfRows.length" class="nl-list">
          <li v-for="row in selfRows" :key="`self-${String(row.id)}`" class="nl-row">
            <a
              class="nl-title"
              :href="selfDetailPath(row)"
              :title="row.title"
              @click.prevent="openSelfDetail(row)"
            >
              {{ row.title || '（未命名公告）' }}
            </a>
            <div class="nl-meta">
              <span class="bid-chip"><b>发布时间</b>{{ fmtDT(row.publishTime) || '—' }}</span>
              <span class="bid-chip"><b>来源站</b>本站</span>
              <span class="bid-chip"><b>发布单位</b>{{ row.publisherName || '—' }}</span>
            </div>
          </li>
        </ul>
        <div v-else class="bid-muted nl-self-empty">
          {{ selfFailed ? '本站公告查询失败（不影响下方外部采集公告列表）' : '该项目编号下暂无本站（本平台发布）公告' }}
        </div>
      </div>
      <div v-else class="bid-muted">
        本站公告目前只支持按「项目编号」查询（后端待补招标项目编号 / 标段编号的查询入参），
        故当前维度下只列外部采集公告。
      </div>

      <!-- ① 加载中 -->
      <div v-if="loading" class="nl-state">
        <IconifyIcon class="nl-spin" icon="lucide:loader-circle" />
        <span>正在查询「{{ queriedCode }}」下的公告…</span>
      </div>

      <!-- ② 加载失败（可重试） -->
      <div v-else-if="failed" class="nl-state nl-state--error">
        <IconifyIcon class="nl-state-icon" icon="lucide:cloud-off" />
        <span>加载失败：{{ errMsg || '网络或服务异常' }}</span>
        <button class="bid-btn bid-btn--ghost bid-btn--sm" type="button" @click="load(pageNo)">
          <IconifyIcon icon="lucide:refresh-cw" /> 重试
        </button>
      </div>

      <!-- ③ 有数据 -->
      <ul v-else-if="rows.length > 0" class="nl-list">
        <li v-for="row in rows" :key="String(row.id)" class="nl-row">
          <a
            class="nl-title"
            :href="detailPath(row)"
            :title="row.title"
            @click.prevent="openDetail(row)"
          >
            {{ row.title || '（未命名公告）' }}
          </a>
          <div class="nl-meta">
            <span class="bid-chip"><b>发布时间</b>{{ fmtDT(row.publishTime) || '—' }}</span>
            <span class="bid-chip"><b>来源站</b>{{ row.sourceName || row.sourceCode || '—' }}</span>
            <span class="bid-chip"><b>招标人</b>{{ row.issuerName || '—' }}</span>
          </div>
        </li>
      </ul>

      <!-- ④ 空态（三种原因分别给文案；本站块有数据时不能再说"没有任何公告"） -->
      <div v-else class="nl-state">
        <IconifyIcon class="nl-state-icon" icon="lucide:inbox" />
        <template v-if="selfRows.length > 0">
          <span>外部采集公告里没有该编号的记录；<b>本站公告见上方（{{ selfRows.length }} 条）</b>。</span>
          <span class="bid-muted">
            外部采集公告与本站公告是两套数据源（两个平台的编号体系也不同），一边没有不代表另一边没有。
          </span>
        </template>
        <template v-else-if="emptyKind === 'nocode'">
          <span>该行没有可用的业务编号，无法按编号关联公告。</span>
        </template>
        <template v-else-if="emptyKind === 'lowcov'">
          <span>
            「{{ meta.label }}」「{{ queriedCode }}」下没有查到公告 —— 该维度编号覆盖率仅约
            {{ coverageText }}（ACTIVE 口径实测），<b>搜不到属正常</b>：编号字段只有部分公告采集到，
            不代表该公告不存在。
          </span>
          <span class="bid-muted">
            可改用上方其它维度再查一次（项目编号覆盖率约 {{ pct(projectDimMeta?.coverage ?? 0) }}）。
          </span>
        </template>
        <template v-else>
          <span>「{{ meta.label }}」「{{ queriedCode }}」下暂无公开公告。</span>
          <span class="bid-muted">
            公开分页只返回已审核公开（SOCIAL + ACTIVE）的外部公告；也可能是该公告未采集到本编号字段。
          </span>
        </template>
      </div>

      <!-- 分页：pageSize=5，超过一页才出现 -->
      <div v-if="rows.length > 0 && total > PAGE_SIZE" class="nl-page">
        <button
          class="bid-btn bid-btn--ghost bid-btn--sm"
          type="button"
          :disabled="pageNo <= 1"
          @click="load(pageNo - 1)"
        >
          上一页
        </button>
        <span class="bid-muted">第 {{ pageNo }} / {{ pageCount }} 页 · 共 {{ total }} 条</span>
        <button
          class="bid-btn bid-btn--ghost bid-btn--sm"
          type="button"
          :disabled="pageNo >= pageCount"
          @click="load(pageNo + 1)"
        >
          下一页
        </button>
      </div>
    </div>
  </Modal>
</template>

<style scoped>
/* 弹窗内容（白底深字由 styles/bid-ui.css 的 .white-modal 段统一处理，此处只管排版） */
.nl-body {
  display: flex;
  flex-direction: column;
  gap: 10px;
  background: #fff;
}

.nl-head {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  justify-content: space-between;
}

.nl-code {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  color: var(--bid-text-1);
  font-size: 13px;
}

.nl-code b {
  color: var(--bid-text-3);
  font-weight: 500;
}

.nl-code-val {
  padding: 1px 8px;
  border-radius: 6px;
  background: var(--bid-primary-soft);
  color: var(--bid-primary);
  font-family: var(--bid-font-mono);
  font-size: 12.5px;
}

.nl-dims {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
}

.nl-dims-label {
  color: var(--bid-text-3);
  font-size: 12.5px;
}

.nl-cov {
  margin-left: 4px;
  font-size: 11px;
  opacity: 0.72;
}

/* 公告行：标题做主视觉，元信息用共享胶囊 */
.nl-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 52vh;
  padding: 0;
  margin: 0;
  overflow: auto;
  list-style: none;
}

.nl-self { margin: 6px 0 10px; padding: 8px 10px; border: 1px dashed hsl(var(--primary) / 35%); background: hsl(var(--primary) / 5%); border-radius: 6px; }
.nl-self-h { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; font-size: 12px; }
.nl-self-empty { font-size: 12px; }
.bid-chip--self { background: hsl(var(--primary) / 12%); color: hsl(var(--primary)); }

.nl-row {
  padding: 10px 12px;
  border: 1px solid var(--bid-border-soft);
  border-radius: var(--bid-radius-sm);
  background: var(--bid-surface-2);
}

.nl-title {
  display: block;
  color: var(--bid-primary-500);
  font-size: 14px;
  font-weight: 600;
  line-height: 1.5;
  text-decoration: none;
  cursor: pointer;
}

.nl-title:hover {
  color: var(--bid-primary);
  text-decoration: underline;
}

.nl-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 8px;
  margin-top: 6px;
}

/* 状态区（加载中 / 失败 / 空态）：图标 + 说明，居中 */
.nl-state {
  display: flex;
  flex-direction: column;
  gap: 10px;
  align-items: center;
  padding: 34px 12px;
  color: var(--bid-text-3);
  font-size: 13px;
  line-height: 1.7;
  text-align: center;
}

.nl-state-icon {
  color: var(--bid-text-4);
  font-size: 30px;
}

.nl-state--error .nl-state-icon {
  color: var(--bid-red);
}

.nl-spin {
  color: var(--bid-primary);
  font-size: 30px;
  animation: nl-spin 1s linear infinite;
}

@keyframes nl-spin {
  to {
    transform: rotate(360deg);
  }
}

.nl-page {
  display: flex;
  gap: 10px;
  align-items: center;
  justify-content: center;
}
</style>
