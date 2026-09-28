<!--
  招投标门户 · 公告大厅（notice-external 域）
  视觉：企业级 B2B（2026-09-17 第二次改版）
    · 统一色彩系统：深蓝主色 + 暖金辅助色（替代此前与深蓝冲突的高饱和橙）；
    · 柔和弥散阴影 + 悬停浮起，卡片有层次；
    · Banner 满屏 + 用户提供的城市夜景底图（上压品牌深蓝蒙版）；
    · 筛选区改「胶囊标签」并收纳长选项到「更多筛选」下拉，消除标签墙；
    · 列表套白卡 + 斑马纹 + 表头减重，标题加粗；空列自动隐藏（避免满屏「—」）。
  功能（保持不变）：关键词全文检索、公告性质/来源站/内容形态/行业/时间多维筛选、列表分页与详情跳转。
  数据源：真实接口 notice-external（config.useMock=false 默认联调；VITE_BIDDING_USE_MOCK=1 走内置 Mock）。
-->
<script lang="ts" setup>
import type { NoticeCodeDim } from './types';

import { computed, onMounted, reactive, ref, watchEffect } from 'vue';
import { useRouter } from 'vue-router';

import { IconifyIcon } from '@vben/icons';

import {
  classifyResultRaw,
  CODE_ALL_DIMS,
  CODE_DIMS,
  CODE_LOW_COVERAGE,
  fetchHallPage,
  fetchNoticePage,
  hallMergesSelf,
  type HallRow,
  RESULT_TYPE_MERGE,
  SELF_SOURCE,
} from './api';
import HeroMods from './components/HeroMods.vue';
import { biddingConfig } from './config';
import { fmtDT } from './format';

import './styles/bid-ui.css';

const router = useRouter();

/** 内容形态（界面标签 ↔ 接口 code） */
const CONTENT_TYPES: Array<{ label: string; value: string }> = [
  { label: '全部形态', value: '' },
  { label: 'HTML 网页', value: 'HTML' },
  { label: 'PDF 文件', value: 'PDF' },
  { label: '附件下载', value: 'ATTACHMENT' },
  { label: '图片', value: 'IMAGE' },
  { label: '其他', value: 'OTHER' },
];
/** 行业判定：值域 BELONGS/NOT_BELONGS/UNCERTAIN（R10） */
const INDUSTRIES: Array<{ label: string; value: string }> = [
  { label: '不限', value: '' },
  { label: '防腐保温相关', value: 'BELONGS' },
  { label: '不相关', value: 'NOT_BELONGS' },
  { label: '未知', value: 'UNCERTAIN' },
];
const QUICK_RANGES = ['全部时间', '最近 7 天', '最近 30 天', '最近 90 天', '今年以来'];

/* 「编号精确搜索」维度选项：默认「全部维度」= 三个维度各发一次请求再合并。
 * 为什么不能同传三个参数：后端 projectCode / tenderProjectCode / bidSectionCode 在同一次请求里是
 * AND 关系（同值同传 → 恒 0 命中，已实测），拆请求合并的实现见 api.ts 的 fetchCodeMerged。 */
const CODE_DIM_OPTIONS: Array<{ label: string; value: '' | NoticeCodeDim }> = [
  { label: '全部维度', value: '' },
  ...CODE_DIMS.map((d) => ({ label: d.label, value: d.dim })),
];

/** 覆盖率（0~1）→ 百分比文案（1% / 15%；整数不补小数位） */
function fmtPct(v: number): string {
  const p = Math.round(v * 1000) / 10;
  return `${Number.isInteger(p) ? p : p.toFixed(1)}%`;
}

const state = reactive({
  keyword: '',
  /** 编号精确搜索输入值（与「关键词全文检索」区分：这里走三个编号字段的精确匹配） */
  code: '',
  /** 编号维度：'' = 全部维度（三维度并发查），否则只查单维度（见 api.ts CODE_DIMS） */
  codeDim: '' as '' | NoticeCodeDim,
  noticeType: '',
  sourceCode: '',
  contentType: '',
  industry: '',
  quick: '全部时间',
  customOn: false,
  begin: '',
  end: '',
});

/** 公告性质筛选项：默认仅“全部性质”，默认视图加载后按列表已发现 noticeTypeRaw 数据驱动补齐 */
const typeOptions = ref<Array<{ label: string; value: string }>>([{ label: '全部性质', value: '' }]);
/** 来源站筛选项：数据驱动自列表行 sourceCode+sourceName */
const sourceOptions = ref<Array<{ label: string; value: string }>>([{ label: '全部来源', value: '' }]);
/** 最近一次默认视图抓取发现的外站类型原文（合并视图分组依据） */
const discoveredRaws = ref<string[]>([]);

const pageNo = ref(1);
const pageSize = 10;
const loading = ref(false);
const errorMsg = ref('');
const rows = ref<HallRow[]>([]);
const total = ref(0);
/** 合并视图命中 200 行上限（页脚截断提示用） */
const totalTruncated = ref(false);
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)));
const pageList = computed(() => {
  const p = pageNo.value;
  const last = totalPages.value;
  const nums: ('…' | number)[] = [];
  const push = (n: number) => nums.push(n);
  push(1);
  for (let i = Math.max(2, p - 1); i <= Math.min(last - 1, p + 1); i++) push(i);
  if (last > 1) push(last);
  const out: ('…' | number)[] = [];
  nums.forEach((n, i) => {
    const prev = nums[i - 1];
    if (typeof n === 'number' && typeof prev === 'number' && n - prev > 1) out.push('…');
    if (out[out.length - 1] !== n) out.push(n);
  });
  return out;
});

/* 筛选区不再有「更多筛选」折叠面板：来源站 / 内容形态 / 行业 一律平铺展示（用户 2026-09-17 口径）。
 * 原有下拉的开关状态、点击外部收起、已选摘要等随之删除，避免留下无人使用的死代码。 */

/* ============ 编号精确搜索（2026-10-01 新增） ============ */
/** 本次查询参与的维度（'' = 全部维度，默认口径） */
const codeDims = computed<NoticeCodeDim[]>(() =>
  state.codeDim ? [state.codeDim] : CODE_ALL_DIMS,
);
/** 覆盖率提示：选中维度含低覆盖字段（招标项目编号 ~1% / 标段编号 ~15%，见 api.ts CODE_DIMS）时提示「搜不到属正常」 */
const codeCoverageHint = computed(() => {
  const low = CODE_DIMS.filter(
    (d) => codeDims.value.includes(d.dim) && d.coverage < CODE_LOW_COVERAGE,
  );
  if (!low.length) return '';
  const parts = low.map((d) => `${d.label}仅约 ${fmtPct(d.coverage)} 的公告有值`);
  return `该编号字段覆盖率低：${parts.join('，')}；精确匹配不到属正常，可改选「项目编号」或改用上方关键词检索。`;
});
/** 提示条显示时机：已输入编号，或已明确选中低覆盖维度（默认「全部维度」+ 空输入时不打扰） */
const showCodeHint = computed(
  () => !!codeCoverageHint.value && (!!state.code.trim() || state.codeDim !== ''),
);

/** 是否有任何筛选处于激活态（「重置」按钮的可用条件） */
const hasActiveFilter = computed(
  () =>
    !!state.keyword.trim() ||
    !!state.code.trim() ||
    !!state.noticeType ||
    !!state.sourceCode ||
    !!state.contentType ||
    !!state.industry ||
    state.customOn ||
    state.quick !== '全部时间',
);

/* ============ 空列自动隐藏（避免满屏「—」） ============
 * 只在**未筛选**时按数据决定列是否出现，并采用「见过即保留」策略：
 * 某列只要在当前会话里出现过非空值就一直保留，避免翻页时列闪烁。 */
const seen = reactive({ type: false, ct: false, ind: false, issuer: false });
watchEffect(() => {
  for (const r of rows.value) {
    if (r.noticeTypeRaw) seen.type = true;
    if (r.contentType) seen.ct = true;
    if (r.industryFlag) seen.ind = true;
    if (r.issuerName) seen.issuer = true;
  }
});
const gridCols = computed(() => {
  const cols = ['minmax(0, 1fr)'];
  if (seen.type) cols.push('128px');
  if (seen.ct) cols.push('96px');
  if (seen.ind) cols.push('102px');
  if (seen.issuer) cols.push('150px');
  cols.push('132px');
  return cols.join(' ');
});

/** 是否“全默认视图”（无任何筛参）——此时才允许以本页数据刷新筛选项 */
function isDefaultView(): boolean {
  return (
    !state.keyword.trim() &&
    !state.code.trim() &&
    !state.noticeType &&
    !state.sourceCode &&
    !state.contentType &&
    !state.industry &&
    !state.customOn &&
    state.quick === '全部时间'
  );
}

/** 性质筛选项 = 全部 + “中标公示”合并快捷项（候选与结果两类都存在时）+ 逐原始值（eq） */
function buildTypeOptions(raws: string[]): Array<{ label: string; value: string }> {
  const list: Array<{ label: string; value: string }> = [{ label: '全部性质', value: '' }];
  const hasCandidate = raws.some((r) => classifyResultRaw(r) === 'candidate');
  const hasResult = raws.some((r) => classifyResultRaw(r) === 'result');
  if (hasCandidate && hasResult) {
    list.push({ label: '中标公示', value: RESULT_TYPE_MERGE });
  }
  raws.forEach((r) => list.push({ label: r, value: r }));
  return list;
}

/** 合并视图涉及的原始值组（候选类 + 结果类） */
function mergedRawGroups(): string[] {
  return discoveredRaws.value.filter((r) => classifyResultRaw(r) !== null);
}

/** 刷新来源站/公告性质筛选项（数据驱动） */
async function refreshFilterOptions(): Promise<void> {
  try {
    /* [2026-10-02 修复] 来源站选项此前只从**首屏 100 条**提取 —— 而首屏恰好全是 QYZB，
     * 下拉里因此没有 GX（用户报障「来源的不能筛选来源」；实测后端 sourceCode=GX 过滤正常、共 167 条）。
     * 两处修正：① 单页拉到接口上限 200（实测 pageSize≥500 直接返回空）；
     *          ② **累积**已发现来源（跨刷新/翻页逐步补全，不把先前发现的来源丢掉）。 */
    const res = await fetchNoticePage({ pageNo: 1, pageSize: 200 });
    const src = new Map<string, { label: string; value: string }>();
    sourceOptions.value.forEach((o) => {
      if (o.value && o.value !== SELF_SOURCE) src.set(o.value, o);
    });
    res.list.forEach((r) => {
      if (r.sourceCode) {
        // 展示统一为「代码 · 名称」（用户口径示例：GX · 国信e采（山西））
        const label = r.sourceName ? r.sourceCode + ' · ' + r.sourceName : r.sourceCode;
        src.set(r.sourceCode, { label, value: r.sourceCode });
      }
    });
    // 「本站」（本平台发布）是前端口径的伪来源（不下发后端）：它不在采集源字典里，必须显式列出，
    // 否则用户永远看不到本平台自己发布的公告 —— 这正是"来源站少一个本站"的由来。
    sourceOptions.value = [
      { label: '全部来源', value: '' },
      { label: '本站（本平台发布）', value: SELF_SOURCE },
      ...src.values(),
    ];
    const rawSet = new Set<string>();
    res.list.forEach((r) => {
      if (r.noticeTypeRaw) rawSet.add(r.noticeTypeRaw);
    });
    discoveredRaws.value = [...rawSet];
    typeOptions.value = buildTypeOptions(discoveredRaws.value);
  } catch {
    /* 保留现有选项：接口错误统一由 load() 呈现 */
  }
}

/** 请求序号守卫：仅最后一次 load() 的结果生效 */
let loadSeq = 0;

async function load(): Promise<void> {
  const seq = ++loadSeq;
  loading.value = true;
  errorMsg.value = '';
  try {
    const mergeMode = state.noticeType === RESULT_TYPE_MERGE;
    const params = {
      keyword: state.keyword.trim() || undefined,
      // 编号精确搜索：空值由 api 层忽略（不下发），与关键词口径一致
      codeSearch: { dims: codeDims.value, value: state.code.trim() },
      noticeTypeRaw: mergeMode ? RESULT_TYPE_MERGE : state.noticeType || undefined,
      resultRaws: mergeMode ? mergedRawGroups() : undefined,
      sourceCode: state.sourceCode || undefined,
      contentType: state.contentType || undefined,
      industryFlag: state.industry || undefined,
      quick: state.quick === '全部时间' ? undefined : state.quick,
      beginTime: state.customOn ? state.begin || undefined : undefined,
      endTime: state.customOn ? state.end || undefined : undefined,
      pageNo: pageNo.value,
      pageSize,
    };
    // 大厅取数：默认视图 = 外部 + 本站（本平台发布）合并；来源站=本站 → 只列本站；其余筛选 → 只列外部
    const res = await fetchHallPage(params);
    if (seq !== loadSeq) return;
    rows.value = res.list;
    total.value = res.total;
    // 合并视图（中标公示 / 编号精确搜索）命中 200 行上限时为 true；普通分页不返回该字段
    totalTruncated.value = res.truncated === true;
    if (isDefaultView()) void refreshFilterOptions();
  } catch (e) {
    if (seq !== loadSeq) return;
    errorMsg.value = e instanceof Error ? e.message : String(e);
    rows.value = [];
    total.value = 0;
    totalTruncated.value = false;
  } finally {
    if (seq === loadSeq) loading.value = false;
  }
}

function search(goFirst = true): void {
  if (goFirst && pageNo.value !== 1) pageNo.value = 1;
  void load();
}

function pickType(v: string): void {
  state.noticeType = v;
  search();
}
function pickCodeDim(v: '' | NoticeCodeDim): void {
  state.codeDim = v;
  search();
}
function pickSource(v: string): void {
  state.sourceCode = v;
  search();
}
function pickContent(v: string): void {
  state.contentType = v;
  search();
}
function pickIndustry(v: string): void {
  state.industry = v;
  search();
}
function pickQuick(v: string): void {
  state.quick = v;
  state.customOn = false;
  search();
}
function turnCustomOn(): void {
  state.customOn = true;
  state.quick = '';
}
function resetFilters(): void {
  state.keyword = '';
  state.code = '';
  state.codeDim = '';
  state.noticeType = '';
  state.sourceCode = '';
  state.contentType = '';
  state.industry = '';
  state.quick = '全部时间';
  state.customOn = false;
  state.begin = '';
  state.end = '';
  search();
}
function refresh(): void {
  void load();
  void refreshFilterOptions();
}
function gotoPage(p: '…' | number): void {
  if (typeof p !== 'number' || p === pageNo.value) return;
  const target = Math.min(Math.max(1, p), totalPages.value);
  if (target === pageNo.value) return;
  pageNo.value = target;
  void load();
}
function goDetail(r: HallRow | number | string): void {
  const row = typeof r === 'object' ? r : undefined;
  const id = typeof r === 'object' ? r.id : r;
  // 本站公告走本平台发布详情页（/portal/bidding/internal-detail/:id）；外站公告走外部公告详情
  const path = row?.sourceKind === 'SELF'
    ? `/portal/bidding/internal-detail/${id}`
    : `/portal/bidding/detail/${id}`;
  router.push(path).catch(() => {});
}

/**
 * 本站来源下"外部专属筛选"提示：本站公告只有 标题/类型/项目编号/发布时间 这几类筛选，
 * 关键词、公告性质（外站原文）、内容形态、行业相关、编号精确都属外部采集口径 ——
 * 透传后通常查不到（属正常，不代表本站没有这类公告），界面必须说明，不能让用户以为筛选生效了。
 */
const selfFilterNote = computed(
  () =>
    state.sourceCode === SELF_SOURCE &&
    (!!state.keyword.trim() ||
      !!state.code.trim() ||
      !!state.contentType ||
      !!state.industry ||
      (!!state.noticeType && state.noticeType !== RESULT_TYPE_MERGE)),
);

/** 当前筛选是否会把本站公告排除在外（外部专属筛选生效时）：界面据此明示口径，不静默漏来源 */
const selfExcluded = computed(
  () =>
    state.sourceCode !== SELF_SOURCE &&
    !hallMergesSelf({
      beginTime: state.customOn ? state.begin || undefined : undefined,
      codeSearch: { dims: codeDims.value, value: state.code.trim() },
      contentType: state.contentType || undefined,
      endTime: state.customOn ? state.end || undefined : undefined,
      industryFlag: state.industry || undefined,
      keyword: state.keyword.trim() || undefined,
      noticeTypeRaw: state.noticeType || undefined,
      quick: state.quick === '全部时间' ? undefined : state.quick,
      sourceCode: state.sourceCode || undefined,
    }),
);

onMounted(() => {
  void load();
});
</script>

<template>
  <div class="hall">
    <!-- ① Banner：满屏「蓝条」。2026-09-21 起它自己**不再画背景** —— 整页的底图（城市夜景 + 品牌深蓝蒙版）
         已经上移到 layouts/portal.vue 的 .portal-shell--bidhero 上，是钉在视口上的一层；
         本元素只负责占位与排版（居中的标题 + 下方那排四个入口）。这样照片在整页里是连续的，
         蓝条下沿不会再切出一道色调/位置都不同的接缝。 -->
    <section class="hero">
      <!-- 上：平台名 + 副标题（.hero-text 是共享类，2026-09-21 下午起**居中**、宽度自适应） -->
      <div class="hero-text">
        <h1 class="hero-title">防腐保温招投标<span class="hl">智慧</span>平台</h1>
        <div class="hero-sub">《公告大厅》《免费查阅 · 依法公开》《智慧标书》</div>
      </div>

      <!-- 下：四个模块入口那整行。共享组件 components/HeroMods.vue（四个页面的 Banner 都要
           显示这四个按钮，写一份比分在四页各写一份稳）；active 传入当前页面的 key，
           让本页对应的那张卡保持「被点击时」的高亮色。
           2026-09-21 下午起它从标题右侧搬到标题下方，并压到下方玻璃卡顶边下 12px。 -->
      <HeroMods active="hall" />
    </section>

    <!-- ③④ 内容区底衬：搜索筛选卡 + 公告目录两块，一起放进一张「略大一些的半透明底圆角卡片」里
         （2026-09-21 用户口径）。这张卡片用共享类 .bid-glass-shell（见 styles/bid-ui.css），
         本页另加 .content-shell 给它列宽与上提位置。 -->
    <div class="content-shell bid-glass-shell">
    <!-- ③ 搜索与筛选（白卡 + 浅阴影，整块包住搜索框、搜索按钮与全部筛选条件） -->
    <section class="ops bid-card">
      <!-- 搜索框：加宽 + 放大镜图标 + 圆润按钮 -->
      <div class="bid-searchbar">
        <div class="bid-search-wrap">
          <span class="bid-search-icon"><IconifyIcon icon="lucide:search" /></span>
          <input
            v-model="state.keyword"
            class="bid-search-input"
            placeholder="输入关键词检索公告（标题 / 项目名称 / 编号 / 正文全文检索）"
            @keyup.enter="search()"
          />
        </div>
        <button class="bid-search-btn" type="button" @click="search()">搜索</button>
      </div>

      <!-- 编号精确搜索（2026-10-01 新增）：与上方「关键词全文检索」区分 —— 这里对三个编号字段做**精确匹配**，
           不做模糊/前缀。三维度各自发一次请求再合并（后端三个编号参数在同一次请求里是 AND 关系，
           同传必 0 命中，见 api.ts 注释），默认「全部维度」= 三维度并发查。 -->
      <div class="filter-line code-line">
        <span class="fl-title">编号精确</span>
        <div class="fl-opts">
          <input
            v-model="state.code"
            class="code-input"
            placeholder="输入完整编号（精确匹配，不支持模糊/前缀）"
            @keyup.enter="search()"
          />
          <span
            v-for="d in CODE_DIM_OPTIONS"
            :key="d.value"
            class="bid-pill"
            :class="{ on: state.codeDim === d.value }"
            @click="pickCodeDim(d.value)"
          >{{ d.label }}</span>
          <button
            class="bid-btn bid-btn--primary bid-btn--sm"
            type="button"
            :disabled="!state.code.trim()"
            @click="search()"
          >
            编号查询
          </button>
        </div>
      </div>

      <!-- 覆盖率提示条：招标项目编号 / 标段编号字段本身覆盖率极低，先说明白，免得用户以为功能坏了 -->
      <div v-if="showCodeHint" class="bid-note bid-note--info code-hint">
        <IconifyIcon icon="lucide:info" />
        <span>{{ codeCoverageHint }}</span>
      </div>

      <!-- 时间（常用，直接平铺） -->
      <div class="filter-line">
        <span class="fl-title">时间</span>
        <div class="fl-opts">
          <span
            v-for="q in QUICK_RANGES"
            :key="q"
            class="bid-pill"
            :class="{ on: state.quick === q && !state.customOn }"
            @click="pickQuick(q)"
          >{{ q }}</span>
          <span
            class="bid-pill bid-pill--dashed"
            :class="{ on: state.customOn }"
            @click="turnCustomOn()"
          >自定义</span>
        </div>
        <button class="bid-btn bid-btn--ghost bid-btn--sm refresh-btn" type="button" @click="refresh">
          <IconifyIcon icon="lucide:rotate-cw" /> 刷新
        </button>
      </div>

      <div v-if="state.customOn" class="filter-line custom-line">
        <span class="fl-title">自定义</span>
        <div class="fl-opts">
          <input v-model="state.begin" class="date-input" type="date" />
          <span class="tilde">至</span>
          <input v-model="state.end" class="date-input" type="date" />
          <button class="bid-btn bid-btn--primary bid-btn--sm" type="button" @click="search()">确定</button>
        </div>
      </div>

      <!-- 公告性质（常用，直接平铺） -->
      <div class="filter-line">
        <span class="fl-title">公告性质</span>
        <div class="fl-opts">
          <span
            v-for="t in typeOptions"
            :key="t.value"
            class="bid-pill"
            :class="{ on: state.noticeType === t.value }"
            @click="pickType(t.value)"
          >{{ t.label }}</span>
        </div>
      </div>

      <!-- 来源站（全部平铺，不做折叠） -->
      <div class="filter-line">
        <span class="fl-title">来源站</span>
        <div class="fl-opts">
          <span
            v-for="s in sourceOptions"
            :key="s.value"
            class="bid-pill"
            :class="{ on: state.sourceCode === s.value }"
            @click="pickSource(s.value)"
          >{{ s.label }}</span>
        </div>
      </div>

      <!-- 内容形态（全部平铺） -->
      <div class="filter-line">
        <span class="fl-title">内容形态</span>
        <div class="fl-opts">
          <span
            v-for="c in CONTENT_TYPES"
            :key="c.value"
            class="bid-pill"
            :class="{ on: state.contentType === c.value }"
            @click="pickContent(c.value)"
          >{{ c.label }}</span>
        </div>
      </div>

      <!-- 行业相关（全部平铺） -->
      <div class="filter-line">
        <span class="fl-title">行业相关</span>
        <div class="fl-opts">
          <span
            v-for="i in INDUSTRIES"
            :key="i.value"
            class="bid-pill"
            :class="{ on: state.industry === i.value }"
            @click="pickIndustry(i.value)"
          >{{ i.label }}</span>
        </div>
      </div>

      <!-- 底部：筛选条件已全部平铺展示，这里只保留「重置」 -->
      <div class="filter-foot">
        <button
          class="bid-btn bid-btn--ghost bid-btn--sm"
          type="button"
          :disabled="!hasActiveFilter"
          @click="resetFilters()"
        >
          重置全部筛选
        </button>
      </div>
    </section>

    <!-- ④ 公告目录（白卡 + 斑马纹 + 表头减重） -->
    <section class="table-wrap" :style="{ '--bid-cols': gridCols }">
      <div class="cat-head">
        <span class="cat-title">公告目录</span>
        <span class="cat-note">
          共 {{ total }} 条<template v-if="totalTruncated">（已达检索上限，仅展示前 200 条）</template>
          <template v-if="biddingConfig.useMock"> · 演示 Mock 数据</template>
        </span>
        <!-- 筛选口径明示：外部专属筛选生效时本站公告不参与，避免"看不到本站"被误当成"本站没有" -->
        <span v-if="selfExcluded" class="cat-self-note">
          当前筛选只作用于外部采集公告（本站公告请选「来源站 = 本站（本平台发布）」）
        </span>
        <span v-else-if="state.sourceCode === SELF_SOURCE" class="cat-self-note cat-self-on">
          本站 = 本平台发布的公开公告（发布工作台发出）
          <template v-if="selfFilterNote">
            · 关键词/编号精确/公告性质/内容形态/行业相关是外部采集公告的口径，对本站通常查不到（属正常）
          </template>
        </span>
      </div>

      <div class="table-card">
        <div class="table-head">
          <div class="th">公告标题</div>
          <div v-if="seen.type" class="th">公告性质</div>
          <div v-if="seen.ct" class="th">内容形态</div>
          <div v-if="seen.ind" class="th">行业相关</div>
          <div v-if="seen.issuer" class="th">发布单位</div>
          <div class="th th-time">发布时间</div>
        </div>

        <div class="table-body">
          <div v-if="errorMsg" class="empty error">{{ errorMsg }}</div>
          <div v-else-if="loading && !rows.length" class="empty">加载中…</div>
          <div v-else-if="!rows.length" class="empty">
            <template v-if="state.code.trim()">
              未匹配到编号「{{ state.code.trim() }}」的公告
              <div v-if="codeCoverageHint" class="empty-sub">{{ codeCoverageHint }}</div>
            </template>
            <template v-else>未查询到符合条件的公告</template>
          </div>

          <template v-else>
            <div
              v-for="r in rows"
              :key="r.id"
              class="table-row"
              role="button"
              tabindex="0"
              @click="goDetail(r)"
              @keydown.enter="goDetail(r)"
            >
              <div class="td td-title">
                <span v-if="r.sourceKind === 'SELF'" class="bid-tag bid-tag--blue src-self">本站</span>
                <span class="link">{{ r.title }}</span>
              </div>
              <div v-if="seen.type" class="td">
                <!-- 本站公告没有外站"公告性质原文"，但有平台认定的公告类型（招标公告/变更公告/公示…），
                     直接显示比一个"—"有用；外站行仍按原文展示 -->
                <span v-if="r.sourceKind === 'SELF' && r.noticeTypeName" class="bid-tag bid-tag--blue">{{ r.noticeTypeName }}</span>
                <span v-else-if="r.noticeTypeRaw" class="bid-tag bid-tag--red">{{ r.noticeTypeRaw }}</span>
                <span v-else class="dash">—</span>
              </div>
              <div v-if="seen.ct" class="td">{{ r.contentType || '—' }}</div>
              <div v-if="seen.ind" class="td">
                <span v-if="r.industryFlag === 'BELONGS'" class="bid-tag bid-tag--blue">行业相关</span>
                <span v-else-if="r.industryFlag === 'UNCERTAIN'" class="bid-tag bid-tag--orange">未知</span>
                <span v-else class="dash">—</span>
              </div>
              <div v-if="seen.issuer" class="td">{{ r.issuerName || '—' }}</div>
              <div class="td td-time">{{ fmtDT(r.publishTime) || '—' }}</div>
            </div>
          </template>
        </div>
      </div>

      <div class="pager">
        <span class="sum">共 {{ total }} 条</span>
        <span class="pg" :class="{ off: pageNo === 1 }" @click="pageNo > 1 && gotoPage(pageNo - 1)">‹</span>
        <span
          v-for="(p, i) in pageList"
          :key="i"
          class="pg"
          :class="{ on: p === pageNo, dot: p === '…' }"
          @click="gotoPage(p)"
        >{{ p }}</span>
        <span
          class="pg"
          :class="{ off: pageNo === totalPages }"
          @click="pageNo < totalPages && gotoPage(pageNo + 1)"
        >›</span>
      </div>
    </section>
    </div>
  </div>
</template>

<style scoped>
.hall {
  min-height: 100%;
  /* 底色透明（2026-09-21）：整页的底不归本页管 —— 底图钉在 layouts/portal.vue 的
     .portal-shell--bidhero 上（那里说明了为什么不能在本页用 position: fixed + z-index: -1）。
     原先这里是浅灰 --bid-bg，滚动时蓝条下沿会露出它，形成一道"截断"的浅色带。 */
  background: transparent;
  color: var(--bid-text-1);
}

/* 整页固定底图（2026-09-21 用户口径「上下滚动页面的时候蓝色背景静止不动」：
 *   「照片铺满整页、钉住不滚」）：
 * **不在这里**，已上移到 layouts/portal.vue 的 .portal-shell--bidhero。
 * 本页当天早些时候曾写过 `.hall::before { position: fixed; z-index: -1 }`，那是看不到的 ——
 *   按 CSS 2.1 附录 E 的绘制顺序，负 z-index 的定位后代会被提升到最近的**真实**层叠上下文（根），
 *   排在**祖先自身那层不透明背景之前**；而 .portal-shell 铺着 `hsl(var(--background-deep))` 浅灰底，
 *   照片被整个盖住（跟 .portal-shell 是否 position: relative 无关）。
 * 现在的口径：底图挂在 .portal-shell 上（它本来就是整页底色的主人，且覆盖整个文档高度），
 *   四个页面的根元素与 .hero 一律 `background: transparent`，让照片连续地透上来。
 * 详情与调法见 layouts/portal.vue 里那条规则的注释。 */

/* 内容区底衬（2026-09-21 用户口径「下面的内容部分加一张略大一些的半透明底圆角卡片」）：
 * 卡片本身的样子来自共享类 .bid-glass-shell（见 styles/bid-ui.css：半透明白 + 毛玻璃 + 圆角 + 描边 +
 * 内边距；上面那圈内边距比另外三面多一个 --bid-glass-lift，那是「往上长高」的部分）。
 * 这里只给这张卡片**列宽与上提位置** —— 口径与内容列一致（--bid-side-inset 收窄居中），
 * 上外边距是「顶边落在屏幕纵向正中再往上抬 lift」的那个负值（算式见下面那条注释）。
 * position/z-index 是**必需的**：Banner 是 position:relative，不给自己定位就会被蓝条盖住；
 * 有了这一层，里面的 .ops / .table-wrap 就不需要各自再定位了（它们都在这张卡片里，
 * 一起压在蓝条之上）。 */
.content-shell {
  position: relative;
  z-index: 1;
  width: calc(100% - var(--bid-side-inset));
  /* 顶边落在屏幕 50vh **再往上抬 `--bid-glass-lift`**（20px，2026-09-21 用户口径
     「四个按钮稍微往上移，然后增加玻璃卡的高度，让四个按钮上移后可以压到玻璃卡」）。
     多压的这 20px 与 `.bid-glass-shell` 多加的 20px 上内边距**成对**，所以卡是往上长高的、
     里面的白卡一动不动；按钮排那边也同步上移了 20px（见 components/HeroMods.vue）。
     算式：自然位置 = 顶栏 64px + 蓝条 100vh，要把它提到 `50vh − lift` 处，
     需要回压 `-(64px + 50vh + lift)`。想再调高度只改 --bid-glass-lift 一个数（令牌区）。 */
  margin: calc(-50vh - 64px - var(--bid-glass-lift, 0px)) auto 40px;
}

/* 内容区宽度口径：
 *   蓝色 Banner 通栏（占满窗口）；下面的内容（搜索筛选 + 公告目录）**左右对称**地收窄，
 *   两侧边距取共享变量 --bid-side-inset（随窗口缩放，每边 32~240px，见 styles/bid-ui.css）。
 *   注意：2026-09-21 起这层收窄只施加在**那一张玻璃卡片（.content-shell）**上，
 *   卡片里面的两张白卡（.ops / .table-wrap）写 100%、跟着卡片走，不再各自收一遍。
 *   （早先那句「与右侧固定悬浮栏留间隔」已作废 —— 那条悬浮栏当天已整体撤下。） */

/* ① Banner：满屏，底图由布局层的 .portal-shell--bidhero 铺满整页并钉住（见 layouts/portal.vue）；
 *   本元素只负责「条本身」：高度、内边距与两段的排布 —— 标题段（居中）+ 下方四个模块入口那整行。
 * 高度：**min-height: 100vh（满屏，2026-09-21 用户口径「加高背景部分高度，铺满试试」）**；
 *   沿革：33.333vh ≈ 360px → 160px → 200px → 100vh。见下面 .hero 里的注释。
 *   仍用 min-height 而非 height —— 窄屏按钮排降为两列/一列时条会被撑高，不会把内容挤出去。
 *   （沿革：160px → 200px 那一版是「四个入口移进蓝条后页面太空、同时把按钮放大」的产物；
 *    满屏这一版已不再需要为排版算高度。**四页一起改**，否则切页时蓝条会忽高忽低。）
 * 排版（2026-09-21 下午口径）：**上标题、下按钮排的一列** ——
 *   标题段 `.hero-text` 居中（共享类在 styles/bid-ui.css：自适应宽 + align-items/text-align 都居中；
 *   纵向位置靠它自己的 `margin-top: auto` 落在「导航栏与按钮之间」的正中，算式在那个文件里）；
 *   按钮排是 `components/HeroMods.vue`（共享组件，靠
 *   `margin-bottom: calc(50vh + 30px + var(--bid-glass-lift))` 压到下方玻璃卡顶边下 12px，
 *   规则与算式都在那个文件里）。
 *   本元素左右内边距取 `--bid-side-inset / 2` —— 于是**内容盒宽 = 100% − inset**，
 *   与下方玻璃卡 `.content-shell`（`width: calc(100% - var(--bid-side-inset))`）**同宽同左右边缘**
 *   （同源、包含块相同，必然对齐；.hall → .portal-main--bidding 一路没有横向 padding，
 *   故它同时就是距窗口左边的实际距离）。
 *   按钮排**不是**占满这个内容盒，而是再往里收一圈去对齐玻璃卡里的**白卡**
 *   （用户 2026-09-21 口径「四个按钮的宽度与白卡对齐而不是玻璃卡」）—— 见 HeroMods.vue。 */
.hero {
  position: relative;
  display: flex;
  /* **一列两段、水平居中**（2026-09-21 下午用户口径：标题与小字移到正中间、放大字号；
     四个模块入口从标题右侧搬到标题下方那**整行**）。
     `align-items: center` 管的是交叉轴（＝水平）对齐 —— 标题段与下方那排按钮都居中
     （按钮排宽度比内容盒窄一圈，靠这条居中）。
     沿革（别再绕回去）：曾是「一行两段」（左标题 + 右按钮）+ `align-items: flex-start` +
     `justify-content: space-between` + `flex-wrap: nowrap` —— 那套全是「按钮在标题右边」时的口径。 */
  flex-direction: column;
  align-items: center;
  justify-content: flex-start;
  /* **`gap: 0`**：标题段的纵向位置交给它自己的 `margin-top: auto`（见 styles/bid-ui.css 里
     `.hero-text` 的注释，算式也在那儿）。这里若还留 16px 行间距，标题与按钮之间就比上方多 16px，
     标题会被顶到中点上方约 8px —— 用户口径要的是「导航栏与按钮之间的正中间」。 */
  gap: 0;
  /* 满屏（2026-09-21 用户口径「加高背景部分高度，铺满试试」）：200px → min-height: 100vh。
     于是蓝条占满整个首屏（顶上门户导航条 64px 后，它的下缘刚好落到首屏之外一点点，
     下面的搜索卡要往下滚一点才看到）—— 这是用户明确选的，与当天早些时候「压到 160px」的口径相反。
     仍用 min-height 而非 height：屏幕特别矮、或入口按钮降列数后内容变高时，条会跟着变高、不会挤爆内容。
     若希望「门户导航条 + 蓝条」正好铺满首屏，改成 `calc(100vh - 64px)` 即可（64px 是顶栏高度）。 */
  min-height: 100vh;
  /* **上内边距 0**（原 26px）：标题段靠 `margin-top: auto` 定位，「导航栏与按钮之间的正中间」
     是拿本元素的内容盒顶边当上界的 —— 内容盒顶边必须正好等于门户导航栏下沿，留 26px 就会
     让标题比中点低 13px。左右仍是 `--bid-side-inset / 2`（决定内容盒宽＝内容列宽），下 22px 不变。 */
  padding: 0 calc(var(--bid-side-inset, 64px) / 2) 22px;
  overflow: hidden;
  color: #fff;
  /* 居中（2026-09-21 下午用户口径）。原来这里是 left（标题在左上角的口径）。 */
  text-align: center;
  /* 背景**透明**（2026-09-21 用户口径「照片铺满整页、钉住不滚」）：底图已上移到
     layouts/portal.vue 的 .portal-shell--bidhero —— 那是一层钉在视口上的背景，
     蓝条自己不再画底，照片在整页里才是连续的，滚动时蓝条下沿不会切出一道
     色调与裁切位置都不同的接缝（这是「蓝条自己画底 + 下面另有一层底」必然的后果）。
     蒙版与裁切位置一并移到那条规则上、取值一字未改（11 47 99 / 82% → 23 72 154 / 68%、
     `center top`），所以首屏观感与改动前一致。
     注意 `overflow: hidden` 与 `color: #fff` 仍要留着：白字还是要压在那层照片上。 */
  background: transparent;
}

/* Banner 的两段已各自下沉到共享处（2026-09-21 起四个页面共用同一套结构）：
 *   · 标题段 `.hero-text`（平台名 + 副标题）→ 共享样式表 styles/bid-ui.css。
 *     2026-09-21 下午起它**居中、宽度自适应**（曾是 `flex: 0 0 384px` 的定宽段，
 *     那是「按钮排在它右边」时为「切页按钮不左右跳」而定的，按钮搬走之后这个理由已不存在）。
 *   · 标题下方那排四个模块入口 → 共享组件 components/HeroMods.vue。
 *     它的宽度对齐（Banner 内容盒再往里收「玻璃卡内边距 + 描边」= 与白卡同宽）、
 *     压进玻璃卡 12px 的落点算式、四张等宽等高 / 说明任何宽度下都不隐藏 / 当前页高亮，
 *     连同 1400px、640px 两档列数降级，都在那个组件的 scoped 样式里。
 * 本页这里只留「条本身」：高度、内边距与两段的排布方式。 */

/* 标题与下面那行小字：2026-09-21 下午放大（用户口径「都移到正中间并放大字号」），
 *   26px → 44px、14px → 20px；**四页必须同值**（合规审查页是 .brw-hero__title / __sub）。 */
.hero-title {
  margin: 0;
  font-size: 44px;
  font-weight: 800;
  /* 字距随字号收一档：字号 26px 时 1px 约 3.8%，44px 时 1px 只有 2.3%、显得松散 */
  letter-spacing: 2px;
  line-height: 1.25;
}

.hero-title .hl {
  color: var(--bid-accent-on-dark);
}

.hero-sub {
  color: rgb(255 255 255 / 82%);
  font-size: 20px;
  line-height: 1.7;
  letter-spacing: 0.8px;
}
/* ③ 搜索与筛选：白卡 + 浅阴影，整块包住搜索框、搜索按钮与全部筛选条件
 * （白底/圆角/阴影都来自共享类 .bid-card，本页不加新样式）。
 * 宽度、上提位置（顶边落在屏幕纵向正中再往上抬 `--bid-glass-lift`：
 * `margin-top: calc(-50vh - 64px - var(--bid-glass-lift))`）、
 * 以及「压过蓝条」的定位，2026-09-21 起都交给了外层那张玻璃卡片 `.content-shell`
 * （算式见它的注释），故这里只负责自己作为「卡片里的一张白卡」的观感与内边距。 */
.ops {
  width: 100%;
  margin: 0;
  padding: 22px 26px 16px;
}

.filter-line {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 14px 0 0;
}

.custom-line {
  padding-top: 10px;
}

.fl-title {
  flex: none;
  width: 72px;
  color: var(--bid-text-2);
  font-size: 13px;
  font-weight: 600;
  line-height: 30px;
}

.fl-opts {
  display: flex;
  flex: 1;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  min-width: 0;
}

.refresh-btn {
  flex: none;
  margin-left: auto;
}

.date-input {
  height: 30px;
  padding: 0 10px;
  border: 1px solid var(--bid-border);
  border-radius: var(--bid-radius-sm);
  color: var(--bid-text-1);
  font-family: inherit;
  font-size: 12.5px;
  outline: none;
}

.date-input:focus {
  border-color: var(--bid-primary);
}

.tilde {
  color: var(--bid-text-3);
  font-size: 12.5px;
}

/* 编号精确搜索行（2026-10-01 新增）：输入框与筛选胶囊同高（30px），不抢上方关键词搜索框的视觉位 */
.code-input {
  width: 320px;
  max-width: 100%;
  height: 30px;
  padding: 0 10px;
  border: 1px solid var(--bid-border);
  border-radius: var(--bid-radius-sm);
  color: var(--bid-text-1);
  font-family: inherit;
  font-size: 12.5px;
  outline: none;
}

.code-input::placeholder {
  color: var(--bid-text-4);
}

.code-input:focus {
  border-color: var(--bid-primary);
}

/* 覆盖率提示条：左缩进到与上面的输入框、筛选胶囊同一起点（.fl-title 宽 72px + 行间距 12px） */
.code-hint {
  margin-left: 84px;
}

/* 空态里的补充说明（编号搜不到时的覆盖率解释） */
.empty-sub {
  margin-top: 6px;
  color: var(--bid-text-4);
  font-size: 12.5px;
}

/* 底部：筛选条件已全部平铺，这里只保留「重置」（右对齐） */
.filter-foot {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid var(--bid-border-soft);
}

/* ④ 公告目录 */
.table-wrap {
  /* 宽度与压过蓝条的定位都交给外层 `.content-shell`（2026-09-21）；
     下外边距由那张卡片的 24px 内边距负责，故这里只留与搜索卡之间的 16px。 */
  width: 100%;
  margin: 16px 0 0;
}

.cat-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  padding: 0 2px 10px;
}

.cat-title {
  color: var(--bid-text-1);
  font-size: 16px;
  font-weight: 700;
}

.cat-note {
  color: var(--bid-text-3);
  font-size: 12.5px;
}

.table-card {
  overflow: hidden;
  border: 1px solid var(--bid-border-soft);
  border-radius: var(--bid-radius);
  background: #fff;
  box-shadow: var(--bid-shadow-sm);
}

.src-self { margin-right: 6px; }
.cat-self-note { margin-left: 10px; font-size: 12px; color: hsl(var(--muted-foreground)); }
.cat-self-on { color: hsl(var(--primary)); }

.table-head,
.table-row {
  display: grid;
  grid-template-columns: var(--bid-cols);
  gap: 12px;
  align-items: center;
}

/* 表头背景（2026-09-21 用户口径：给表头加浅色背景以区分行列）：
 * 原来用的是 --bid-surface-2(#fafbfd)，与下方斑马纹的偶数行**是同一个色**，
 * 于是表头在视觉上和斑马行分不开。改用既有的 --bid-bg(#f4f6f9) —— 比斑马行的浅灰再深一档，
 * 表头能立起来，而且**没有引入任何新颜色**（仍是模块既有的中性底色令牌）。 */
.table-head {
  padding: 11px 18px;
  border-bottom: 1px solid var(--bid-border);
  background: var(--bid-bg);
  color: var(--bid-text-3);
  font-size: 12.5px;
  font-weight: 600;
}

.table-row {
  padding: 13px 18px;
  border-bottom: 1px solid var(--bid-border-soft);
  color: var(--bid-text-2);
  font-size: 13px;
  cursor: pointer;
  transition: background-color 0.12s;
}

/* 斑马纹（先）→ 悬停（后，同权重故生效） */
.table-body .table-row:nth-child(even) {
  background: var(--bid-surface-2);
}

.table-body .table-row:hover {
  background: var(--bid-primary-soft);
}

.table-row:last-child {
  border-bottom: 0;
}

.td-title {
  overflow: hidden;
}

.link {
  display: block;
  overflow: hidden;
  color: var(--bid-text-1);
  font-size: 13.5px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.table-body .table-row:hover .link {
  color: var(--bid-primary);
}

.dash {
  color: var(--bid-text-4);
}

.td-time {
  color: var(--bid-text-3);
  font-size: 12.5px;
}

.empty {
  padding: 48px 16px;
  color: var(--bid-text-3);
  font-size: 13px;
  text-align: center;
}

.empty.error {
  background: var(--bid-red-soft);
  color: var(--bid-red);
}

/* 分页 */
.pager {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  justify-content: flex-end;
  margin-top: 14px;
}

.sum {
  margin-right: 8px;
  color: var(--bid-text-3);
  font-size: 12.5px;
}

.pg {
  display: grid;
  place-items: center;
  min-width: 28px;
  height: 28px;
  border: 1px solid var(--bid-border);
  border-radius: var(--bid-radius-sm);
  background: #fff;
  color: var(--bid-text-2);
  font-size: 12.5px;
  cursor: pointer;
  transition: border-color 0.15s, color 0.15s, background-color 0.15s;
}

.pg:hover:not(.off):not(.dot) {
  border-color: var(--bid-primary);
  color: var(--bid-primary);
}

.pg.on {
  border-color: var(--bid-primary);
  background: var(--bid-primary);
  color: #fff;
  font-weight: 600;
}

.pg.off {
  opacity: 0.45;
  cursor: not-allowed;
}

.pg.dot {
  border: 0;
  background: transparent;
  cursor: default;
}

/* 原来这里有两档给 .cards 网格降列数的媒体查询（≤1100 两列、≤860 单列）：
 * 2026-09-21 四个入口小卡移进蓝条、改由 .hero-mods 排布后，这套给 .cards 用的列数降级已经不存在 ——
 * 现在窄屏由 .hero-mods 自己的三档列数（四列 / 两列 / 一列）接管，**不再折行**。 */

/* 本页已恢复单栏（2026-09-21 当天试过的两栏结构已撤回），窄屏无需额外规则。 */

/* 窄屏降级：列宽自适应 */
@media (max-width: 860px) {
  .refresh-btn {
    margin-left: 0;
  }

  /* 窄屏下 .fl-title 宽度改 auto，提示条不再缩进 */
  .code-hint {
    margin-left: 0;
  }

  .filter-line {
    flex-wrap: wrap;
  }

  .fl-title {
    width: auto;
    margin-right: 4px;
  }

  .filter-foot {
    justify-content: flex-start;
  }
}

/* 原来这里有一条 @media (max-width: 420px) 调 .cards 的负边距（防它压住折行的副标题）：
 * 2026-09-21 四个入口小卡移进蓝条、不再有负边距之后，这一档已无必要，整块删除。
 * 窄屏下按钮排只会降列数（始终是标题下方那整行），蓝条被撑高，不存在重叠。 */

/* 窄屏（≤720px）不需要额外覆盖：两侧边距由 --bid-side-inset 自动收敛
 * （该变量在窄窗口会降到每边 32px 左右）。
 * 注：原来这里写着「悬浮栏在 ≤720px 隐藏（见 components/FloatBar.vue）」——
 * 那条服务导航 2026-09-21 晚些时候已按用户要求整体撤下（组件文件保留但无任何引用），
 * 故本页不再需要为它留位或让它隐藏。 */
</style>
