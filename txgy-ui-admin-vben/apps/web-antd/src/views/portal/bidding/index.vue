<!--
  招投标门户 · 公告大厅（notice-external 域）
  版式：关键词居中 → 行式属性查询区 → 公告目录（03-页面结构与建议 §1）
  数据源：真实接口 notice-external（config.useMock=false 默认联调；VITE_BIDDING_USE_MOCK=1 走内置 Mock）
  口径（docs/外部公告-前端对接 01/02）：
   - 大厅只读：不传状态/审核筛参，仅 ACTIVE+SOCIAL（后端 page 已限定，02 §3.4）；
   - 关键词 → keyword（ES 全文检索，R5；ES 未启用时接口报 1-060-106-015，页面提示检索暂不可用，02 §6）；
   - 关注：公告详情“＋关注/已关注” → 我的关注页（notice-follow 域，登录态）；本页“我的关注”入口即该列表，
   - 公告性质/来源站筛选项 = 列表数据驱动（外站原文 noticeTypeRaw / 来源字典 sourceCode+sourceName，R1/R3），
     “中标公示”= 候选类+结果类合并快捷口径（R1：后端不发明文聚合，前端按 noticeTypeRaw 组合）；
   - 行业：industryFlag 三态 eq（防腐保温相关/不相关/未知，R10）。
-->
<script lang="ts" setup>
import type { NoticeExternalItem } from './types';

import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';

import { classifyResultRaw, fetchNoticePage, RESULT_TYPE_MERGE } from './api';
import { biddingConfig } from './config';
import { fmtDate, fmtDT } from './format';

import ModuleNav from './components/ModuleNav.vue';

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
/** 行业判定：值域 BELONGS/NOT_BELONGS/UNCERTAIN（R10）；大厅高亮“防腐保温相关” */
const INDUSTRIES: Array<{ label: string; value: string }> = [
  { label: '不限', value: '' },
  { label: '防腐保温相关', value: 'BELONGS' },
  { label: '不相关', value: 'NOT_BELONGS' },
  { label: '未知', value: 'UNCERTAIN' },
];
const QUICK_RANGES = ['全部时间', '最近 7 天', '最近 30 天', '最近 90 天', '今年以来'];

const state = reactive({
  keyword: '',
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
/** 来源站筛选项：默认仅“全部来源”，数据驱动自 bidding_crawl_source（列表行 sourceCode+sourceName，R3） */
const sourceOptions = ref<Array<{ label: string; value: string }>>([{ label: '全部来源', value: '' }]);
/** 最近一次默认视图抓取发现的外站类型原文（合并视图分组依据） */
const discoveredRaws = ref<string[]>([]);

const pageNo = ref(1);
const pageSize = 10;
const loading = ref(false);
const errorMsg = ref('');
const rows = ref<NoticeExternalItem[]>([]);
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

/** 是否“全默认视图”（无任何筛参）——此时才允许以本页数据刷新筛选项 */
function isDefaultView(): boolean {
  return (
    !state.keyword.trim() &&
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

/** 刷新来源站/公告性质筛选项（来源与类型字典均为数据驱动，01 §6 R1/R3） */
async function refreshFilterOptions(): Promise<void> {
  try {
    const res = await fetchNoticePage({ pageNo: 1, pageSize: 100 });
    const src = new Map<string, { label: string; value: string }>();
    res.list.forEach((r) => {
      if (r.sourceCode) {
        src.set(r.sourceCode, { label: r.sourceName || r.sourceCode, value: r.sourceCode });
      }
    });
    sourceOptions.value = [{ label: '全部来源', value: '' }, ...src.values()];
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

/** 请求序号守卫：仅最后一次 load() 的结果生效，避免翻页/切筛选时的竞态覆盖 */
let loadSeq = 0;

async function load(): Promise<void> {
  const seq = ++loadSeq;
  loading.value = true;
  errorMsg.value = '';
  try {
    const mergeMode = state.noticeType === RESULT_TYPE_MERGE;
    const params = {
      keyword: state.keyword.trim() || undefined,
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
    const res = await fetchNoticePage(params);
    if (seq !== loadSeq) return;
    rows.value = res.list;
    total.value = res.total;
    // 合并视图命中 200 行上限时页脚提示截断（P5，避免 total 失真无感知）
    totalTruncated.value =
      mergeMode && (res as { truncated?: boolean }).truncated === true;
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
function gotoPage(p: '…' | number): void {
  if (typeof p !== 'number' || p === pageNo.value) return;
  const target = Math.min(Math.max(1, p), totalPages.value);
  if (target === pageNo.value) return;
  pageNo.value = target;
  void load();
}
function goDetail(id: number | string): void {
  router.push(`/portal/bidding/detail/${id}`).catch(() => {
    /* 导航被取消/重复导航：忽略 */
  });
}

onMounted(() => {
  void load();
});
</script>

<template>
<!--
  门户 · 招投标大厅（外部公告列表，公开只读）
  业务：面向公众/市场主体的“外部公告大厅”：展示采集并审核通过的依法公开招标公告/公示（ACTIVE+SOCIAL），
        免费查阅、归类检索；列表即免费信息服务的入口，无登录/租户门槛（G 轨 @PermitAll）。
  理由：公共资源交易信息依法须免费、及时、完整公开可查；大厅是“免费查阅”的落地页面。
    依据（法规/政府文件/法律）：
      · 招标公告和公示信息发布管理办法（发改委令第10号，部门规章/政府文件）第12/13条——免费、及时查阅完整公告公示信息；免费归类与检索、新公告醒目标识；
      · 电子招标投标办法（八部委令第20号，部门规章/政府文件）第9条——市场主体免费注册登录、获取依法公开的招标投标信息；
      · 招标投标法 第16条（法律）——公开招标应当发布招标公告（公告公开性的上位依据）。
    后端契约：前端规范-全域接口说明/01-接口逐一说明与设计依据.md §2（notice-external 大厅公开，@PermitAll 免登录免租户）；
      外部公告-前端对接/01-接口契约.md §1；03-页面结构与建议.md §1；GET /bidding/notice-external/page（keyword 走 ES 全文）。
-->

  <div class="bidding-page bid-ind">
    <!-- 招投标前台模块导航（公告大厅不再占按钮位，本页即公告大厅落地页） -->
    <ModuleNav />

    <!-- ① 关键词：全文检索（keyword → ES：标题/项目名/编号/正文，R5） -->
    <div class="kwbar">
      <div class="kw">
        <input
          v-model="state.keyword"
          placeholder="输入关键词检索公告（标题 / 项目名称 / 编号 / 正文全文检索）"
          @keyup.enter="search()"
        />
        <button class="btn primary" @click="search()">搜索</button>
      </div>
    </div>

    <!-- ② 行式属性查询区（属性名靠左，右侧一排文字选项） -->
    <div class="qarea">
      <div class="qrow">
        <div class="qlb">公告性质</div>
        <div class="qopts">
          <span
            v-for="t in typeOptions"
            :key="t.value"
            class="opt kbd-btn"
            role="button"
            tabindex="0"
            :class="{ on: state.noticeType === t.value }"
            :title="t.value === RESULT_TYPE_MERGE ? '中标候选人公示/中标结果公示等合并视图（前端按公告性质原文组合）' : ''"
            @click="pickType(t.value)"
            @keydown.enter.prevent="pickType(t.value)"
          >{{ t.label }}</span>
        </div>
      </div>
      <div class="qrow">
        <div class="qlb">来源站</div>
        <div class="qopts">
          <span
            v-for="s in sourceOptions"
            :key="s.value"
            class="opt kbd-btn"
            role="button"
            tabindex="0"
            :class="{ on: state.sourceCode === s.value }"
            @click="pickSource(s.value)"
            @keydown.enter.prevent="pickSource(s.value)"
          >{{ s.label }}</span>
        </div>
      </div>
      <div class="qrow">
        <div class="qlb">内容形态</div>
        <div class="qopts">
          <span
            v-for="c in CONTENT_TYPES"
            :key="c.value"
            class="opt kbd-btn"
            role="button"
            tabindex="0"
            :class="{ on: state.contentType === c.value }"
            @click="pickContent(c.value)"
            @keydown.enter.prevent="pickContent(c.value)"
          >{{ c.label }}</span>
        </div>
      </div>
      <div class="qrow">
        <div class="qlb">行业相关</div>
        <div class="qopts">
          <span
            v-for="i in INDUSTRIES"
            :key="i.value"
            class="opt kbd-btn"
            role="button"
            tabindex="0"
            :class="{ on: state.industry === i.value }"
            @click="pickIndustry(i.value)"
            @keydown.enter.prevent="pickIndustry(i.value)"
          >{{ i.label }}</span>
        </div>
      </div>
      <div class="qrow">
        <div class="qlb">时间</div>
        <div class="qopts">
          <span
            v-for="q in QUICK_RANGES"
            :key="q"
            class="opt kbd-btn"
            role="button"
            tabindex="0"
            :class="{ on: state.quick === q }"
            @click="pickQuick(q)"
            @keydown.enter.prevent="pickQuick(q)"
          >{{ q }}</span>
          <span class="opt custom kbd-btn" role="button" tabindex="0" @click="turnCustomOn()" @keydown.enter.prevent="turnCustomOn()">＋ 自定义</span>
        </div>
      </div>
      <div v-if="state.customOn" class="cust-line">
        <input v-model="state.begin" class="input" type="date" />
        <span class="tilde">至</span>
        <input v-model="state.end" class="input" type="date" />
        <button class="btn primary sm" @click="search()">确定</button>
        <span class="cust-note">精确日期行：点“全部时间”或其它快捷区间可收起</span>
      </div>
    </div>

    <!-- ③ 公告目录 -->
    <div class="catalog">
      <div class="cat-head">
        <span class="t">公告目录</span>
        <small>仅 ACTIVE + 面向社会 · 按发布时间倒序 · 共 {{ total }} 条<template v-if="totalTruncated">（已达检索上限，仅展示前 200 条）</template>
          <template v-if="biddingConfig.useMock">· 演示 Mock 数据（非后端）</template>
          <template v-else>· 已接入真实接口 notice-external</template>
        </small>
      </div>

      <div v-if="errorMsg" class="empty-box error">{{ errorMsg }}</div>

      <div class="table-card">
        <div class="nrow head">
          <div class="c-title">公告标题（性质 / 形态 / 行业 / 依法 / 来源）</div>
          <div class="n-main">发布单位 · 有效期</div>
          <div class="n-right">发布时间</div>
        </div>

        <div v-if="loading && rows.length" class="list-loading-bar"></div>
        <div v-if="loading && !rows.length" class="loading-line">加载中…</div>
        <div v-else-if="!loading && !rows.length && !errorMsg" class="empty-box">未查询到符合条件的公告</div>

        <template v-else-if="rows.length">
          <div
            v-for="r in rows"
            :key="r.id"
            class="nrow kbd-btn"
            role="button"
            tabindex="0"
            @click="goDetail(r.id)"
            @keydown.enter="goDetail(r.id)"
          >
            <div class="c-title">
              <div class="n-title">{{ r.title }}</div>
              <div class="n-tags">
                <span v-if="r.noticeTypeRaw" class="tag t">{{ r.noticeTypeRaw }}</span>
                <span class="tag m">{{ r.contentType }}</span>
                <span v-if="r.industryFlag === 'BELONGS'" class="tag a">行业相关</span>
                <span v-if="r.industryFlag === 'UNCERTAIN'" class="tag o">行业未知</span>
                <span v-if="r.legalFlag === 'Y'" class="tag g">依法必招</span>
                <span v-if="r.sourceName" class="tag m">来源：{{ r.sourceName }}</span>
              </div>
            </div>
            <div class="n-main">
              <span>{{ r.issuerName || '—' }}</span>
              <span v-if="r.effectiveFrom || r.effectiveTo">
                生效 {{ fmtDate(r.effectiveFrom) || '—' }} ~ {{ fmtDate(r.effectiveTo) || '—' }}
              </span>
              <span v-else>生效 —</span>
            </div>
            <div class="n-right">
              <span class="n-time">{{ fmtDT(r.publishTime) || '—' }}</span>
            </div>
          </div>
        </template>
      </div>

      <div class="pager">
        <span class="pg kbd-btn" role="button" tabindex="0" :class="{ off: pageNo === 1 }" @click="pageNo > 1 && gotoPage(pageNo - 1)" @keydown.enter.prevent="pageNo > 1 && gotoPage(pageNo - 1)">‹</span>
        <span
          v-for="(p, i) in pageList"
          :key="i"
          class="pg kbd-btn"
          role="button"
          tabindex="0"
          :class="{ on: p === pageNo, dot: p === '…' }"
          @click="gotoPage(p)"
          @keydown.enter.prevent="gotoPage(p)"
        >{{ p }}</span>
        <span class="pg kbd-btn" role="button" tabindex="0" :class="{ off: pageNo === totalPages }" @click="pageNo < totalPages && gotoPage(pageNo + 1)" @keydown.enter.prevent="pageNo < totalPages && gotoPage(pageNo + 1)">›</span>
        <span class="sum">共 {{ total }} 条</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.bidding-page {
  background: hsl(var(--background-deep));
  min-height: 100%;
}
/* 键盘可达性：可点击 span/div 聚焦时的可见焦点环（不改视觉样式，仅 :focus-visible 生效） */
.kbd-btn:focus-visible {
  outline: 2px solid hsl(var(--primary));
  outline-offset: 2px;
}
.kwbar {
  display: flex;
  justify-content: center;
  padding: 18px 20px 8px;
}
.kw {
  display: flex;
  gap: 8px;
  width: min(620px, 100%);
}
.kw input {
  flex: 1;
  border: 1px solid hsl(var(--border));
  border-radius: 4px;
  padding: 9px 14px;
  font-size: 14px;
  color: hsl(var(--foreground));
  outline: none;
}
.kw input:focus {
  border-color: hsl(var(--primary));
}
.btn {
  border-radius: 4px;
  padding: 8px 26px;
  font-size: 13.5px;
  font-weight: 600;
  cursor: pointer;
  border: 1px solid transparent;
  background: hsl(var(--primary));
  color: hsl(var(--primary-foreground));
}
.btn.sm {
  padding: 5px 16px;
  font-size: 12.5px;
}
.qarea {
  background: hsl(var(--card));
  border: 1px solid hsl(var(--border));
  border-radius: 6px;
  margin: 8px 20px 4px;
  padding: 8px 16px;
}
.qrow {
  display: flex;
  align-items: flex-start;
  padding: 7px 0;
  border-bottom: 1px dashed hsl(var(--border));
  line-height: 1.9;
}
.qrow:last-child {
  border-bottom: 0;
}
.qlb {
  flex: none;
  width: 90px;
  font-size: 13px;
  color: hsl(var(--foreground));
  font-weight: 800;
}
.qopts {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 2px 0;
  min-width: 0;
}
.opt {
  font-size: 13px;
  color: hsl(var(--muted-foreground));
  padding: 1px 13px;
  cursor: pointer;
  white-space: nowrap;
  border-radius: 3px;
}
.opt:hover {
  color: hsl(var(--primary));
  background: hsl(var(--primary) / 8%);
}
.opt.on {
  background: hsl(var(--primary));
  color: hsl(var(--primary-foreground));
  font-weight: 600;
}
.opt.custom {
  border: 1px dashed hsl(var(--border));
  color: hsl(var(--muted-foreground));
  margin-left: 6px;
}
.opt.custom:hover {
  border-color: hsl(var(--primary));
  color: hsl(var(--primary));
  background: hsl(var(--card));
}
.cust-line {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 6px 0 4px 90px;
  background: hsl(var(--muted) / 40%);
  border: 1px dashed hsl(var(--border));
  border-radius: 4px;
  padding: 6px 10px;
  flex-wrap: wrap;
}
.input {
  border: 1px solid hsl(var(--border));
  border-radius: 4px;
  padding: 4px 8px;
  font-size: 12.5px;
  color: hsl(var(--foreground));
}
.tilde {
  color: hsl(var(--muted-foreground));
}
.cust-note {
  font-size: 11.5px;
  color: hsl(var(--muted-foreground));
}
.catalog {
  margin: 10px 20px 4px;
}
.cat-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  padding: 6px 2px;
}
.cat-head .t {
  font-size: 16px;
  font-weight: 800;
}
.cat-head small {
  color: hsl(var(--muted-foreground));
  font-size: 12px;
}
.table-card {
  border: 1px solid hsl(var(--border));
  border-radius: 6px;
  overflow: hidden;
  background: hsl(var(--card));
}
.nrow {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 150px 88px;
  gap: 8px;
  padding: 0 12px;
  border-bottom: 1px solid hsl(var(--border));
  cursor: pointer;
  align-items: center;
  min-height: 54px;
}
.nrow:hover {
  background: hsl(var(--primary) / 5%);
}
.nrow.head {
  background: hsl(var(--background-deep));
  cursor: default;
  font-size: 12px;
  color: hsl(var(--muted-foreground));
  font-weight: 700;
}
.nrow:last-child {
  border-bottom: 0;
}
.n-title {
  font-size: 13.5px;
  font-weight: 700;
  color: hsl(var(--foreground));
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.nrow:not(.head):hover .n-title {
  color: hsl(var(--primary));
  text-decoration: underline;
}
.n-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
  margin-top: 4px;
}
.tag {
  display: inline-flex;
  padding: 0 8px;
  border-radius: 4px;
  font-size: 11.5px;
  line-height: 1.7;
  white-space: nowrap;
}
.tag.t {
  background: hsl(var(--primary) / 8%);
  color: hsl(var(--primary));
}
.tag.m {
  background: hsl(var(--muted) / 60%);
  color: hsl(var(--muted-foreground));
}
.tag.a {
  background: hsl(var(--warning) / 12%);
  color: var(--ind-warn);
}
.tag.g {
  background: hsl(var(--success) / 12%);
  color: var(--ind-ok);
}
.tag.o {
  background: hsl(var(--card));
  color: hsl(var(--muted-foreground));
  border: 1px solid hsl(var(--border));
}
.n-main {
  color: hsl(var(--muted-foreground));
  font-size: 11.5px;
  display: flex;
  flex-direction: column;
  gap: 2px;
  overflow: hidden;
}
.n-right {
  display: flex;
  flex-direction: column;
  gap: 3px;
  align-items: flex-end;
}
.n-time {
  font-size: 12px;
  color: hsl(var(--foreground));
  white-space: nowrap;
}
.loading-line,
.empty-box {
  padding: 42px 12px;
  text-align: center;
  color: hsl(var(--muted-foreground));
  font-size: 13px;
}
/* 翻页/切筛选时的细加载条：旧列表数据保持渲染，不整屏替换 */
.list-loading-bar {
  height: 4px;
  width: 40%;
  background: hsl(var(--primary));
  animation: loadbar-swing 1.1s ease-in-out infinite alternate;
}
@keyframes loadbar-swing {
  from {
    transform: translateX(-30%);
  }
  to {
    transform: translateX(190%);
  }
}
.empty-box.error {
  color: hsl(var(--destructive));
  background: hsl(var(--destructive) / 8%);
}
.pager {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 5px;
  padding: 12px 2px 18px;
  color: hsl(var(--muted-foreground));
  font-size: 12px;
  flex-wrap: wrap;
}
.pg {
  min-width: 26px;
  height: 26px;
  border-radius: 4px;
  border: 1px solid hsl(var(--border));
  background: hsl(var(--card));
  display: grid;
  place-items: center;
  font-size: 12px;
  color: hsl(var(--muted-foreground));
  cursor: pointer;
}
.pg.on {
  background: hsl(var(--primary));
  color: hsl(var(--primary-foreground));
  border-color: hsl(var(--primary));
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
.pg:not(.on):not(.off):not(.dot):hover {
  border-color: hsl(var(--primary));
  color: hsl(var(--primary));
}
.sum {
  margin-left: 10px;
}
/* 窄屏降级：列表行单列堆叠（元信息换行显示）、筛选区换行、分页可换行 */
@media (max-width: 768px) {
  .nrow {
    grid-template-columns: minmax(0, 1fr);
    align-items: flex-start;
    padding: 10px 12px;
    min-height: 0;
  }
  .nrow .n-main,
  .nrow .n-right {
    align-items: flex-start;
  }
  .nrow .n-right {
    flex-direction: column;
  }
  .nrow .n-time {
    white-space: normal;
  }
  .qrow {
    flex-wrap: wrap;
  }
  .qlb {
    width: auto;
    margin-right: 12px;
  }
  .qopts {
    flex-wrap: wrap;
  }
  .cust-line {
    margin-left: 0;
  }
  .pager {
    flex-wrap: wrap;
    justify-content: center;
  }
}
</style>