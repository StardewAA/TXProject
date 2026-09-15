<!--
  招投标门户 · 本平台发布（招标人自营公开公告；notice public 域）
  数据源：真实接口 /bidding/notice/public/list（@PermitAll，仅 PUBLISHED + 社会公开）或内置 Mock
-->
<script lang="ts" setup>
import type { InternalNoticeItem } from './types';

import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import { fetchInternalNotices } from './api';
import { biddingConfig } from './config';
import { fmtDate } from './format';

const router = useRouter();
const loading = ref(false);
const errorMsg = ref('');
const keyword = ref('');
const typeFilter = ref('');
const scopeFilter = ref<'' | 'internal' | 'public'>('');
const timeDays = ref(0);
const projectCode = ref('');
const rows = ref<InternalNoticeItem[]>([]);
const total = ref(0);
const pageNo = ref(1);
const pageSize = 10;
const totalPages = ref(1);
// 可见范围筛选：两类公告（仅本租户 / 对社会公开）混在同一大厅里，只靠行内小标记辨认不够用，
// 用户需要能"只看内部""只看对外"（08 §4 的 internalFlag 语义，后端 NoticePageReqVO 直接支持）。
const SCOPE_FILTERS: Array<{ label: string; value: '' | 'internal' | 'public' }> = [
  { label: '全部可见范围', value: '' },
  { label: '仅本租户', value: 'internal' },
  { label: '对社会公开', value: 'public' },
];
// 发布时间快捷区间：只发下界（publishTimeStart），不发上界——宁可比目标范围多取几条，
// 也不要因本机与服务端的时钟/时区偏差把刚发布的公告挡在门外。
const TIME_FILTERS: Array<{ days: number; label: string }> = [
  { days: 0, label: '全部时间' },
  { days: 7, label: '最近 7 天' },
  { days: 30, label: '最近 30 天' },
];

const TYPE_FILTERS: Array<{ label: string; value: string }> = [
  { label: '全部类型', value: '' },
  { label: '招标公告', value: 'TENDER_NOTICE' },
  { label: '变更公告', value: 'CHANGE_NOTICE' },
  { label: '中标候选人公示', value: 'WIN_CANDIDATE_PUBLICITY' },
  { label: '中标结果公示', value: 'WIN_RESULT_PUBLICITY' },
];

function goNav(path: string): void {
  router.push(path).catch(() => {});
}
/** 请求序号守卫：仅最后一次 load() 的结果生效，避免翻页/切筛选时的竞态覆盖 */
let loadSeq = 0;

async function load(): Promise<void> {
  const seq = ++loadSeq;
  loading.value = true;
  errorMsg.value = '';
  try {
    const res = await fetchInternalNotices({
      pageNo: pageNo.value,
      pageSize,
      keyword: keyword.value.trim() || undefined,
      noticeType: typeFilter.value || undefined,
      // 可见范围 → internalFlag（true=仅本租户，false=对社会公开，不传=全部）
      internalFlag: scopeFilter.value === '' ? undefined : scopeFilter.value === 'internal',
      // 后端 NoticePageReqVO.projectCode（精确匹配）
      projectCode: projectCode.value.trim() || undefined,
      // 后端 NoticePageReqVO.publishTimeStart（@DateTimeFormat，'yyyy-MM-dd HH:mm:ss'）
      publishTimeStart: publishTimeStartOf(timeDays.value),
    });
    if (seq !== loadSeq) return;
    rows.value = res.list || [];
    total.value = res.total || 0;
    totalPages.value = Math.max(1, Math.ceil(total.value / pageSize));
  } catch (e) {
    if (seq !== loadSeq) return;
    errorMsg.value = (e as Error).message || String(e);
    rows.value = [];
    total.value = 0;
  } finally {
    if (seq === loadSeq) loading.value = false;
  }
}
function search(): void {
  pageNo.value = 1;
  void load();
}
function pickType(v: string): void {
  typeFilter.value = v;
  pageNo.value = 1;
  void load();
}
function pickScope(v: '' | 'internal' | 'public'): void {
  scopeFilter.value = v;
  pageNo.value = 1;
  void load();
}
function pickTime(days: number): void {
  timeDays.value = days;
  pageNo.value = 1;
  void load();
}
/**
 * 近 N 天的起始时刻，按后端 @DateTimeFormat 口径输出 'yyyy-MM-dd HH:mm:ss'（**查询参数是字符串，不是毫秒**；
 * 毫秒只用于 JSON 体与出参）。用本地时间构造：与用户在界面上看到的"7 天前"一致。
 */
function publishTimeStartOf(days: number): string | undefined {
  if (!days) return undefined;
  const d = new Date(Date.now() - days * 86_400_000);
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`;
}
function open(id: number | string): void {
  router.push('/portal/bidding/internal-detail/' + String(id)).catch(() => {});
}
function goPage(p: number): void {
  if (p < 1 || p > totalPages.value) return;
  pageNo.value = p;
  void load();
}
onMounted(() => void load());
</script>

<template>
<!--
  门户 · 本平台发布（内部公告大厅：仅本租户可见）
  业务：展示本平台企业（当前登录租户）自营发布的 PUBLISHED 公告（internalFlag=false 对外亦含在此，internalFlag=true 仅本租户）；
        是对“招标人发布→大厅公示”闭环的企业侧展示面。
  理由：招标公告依法公开；内部公告（internalFlag=true）仅本租户（08 §4）；对外公告走同源公开读接口。
    依据（法规/政府文件/法律）：
      · 招标公告和公示信息发布管理办法（发改委令第10号，部门规章/政府文件）第12/13条——免费、及时查阅完整公告公示信息；免费归类与检索、新公告醒目标识；
      · 电子招标投标办法（八部委令第20号，部门规章/政府文件）第9条——市场主体免费注册登录、获取依法公开的招标投标信息；
      · 招标投标法 第16条（法律）——公开招标应当发布招标公告（公告公开性的上位依据）。
    后端契约：08-公告公示前端交付-业务口径说明.md §4（internalFlag=false 对外/true 仅企业内部）；
      GET /bidding/notice/public/list、/public/detail（跨租户只放行 PUBLISHED+非内部+到发布时间+SOCIAL，08 §8）。
    筛选能力（对齐后端 NoticePageReqVO，前端只发它声明的字段）：keyword / noticeType / internalFlag /
      projectCode / publishTimeStart（近 N 天取本地时间下界，'yyyy-MM-dd HH:mm:ss'）。
-->

  <div class="bidding-page bid-ind">
    <div class="main-nav">
      <div class="nav-item kbd-btn" role="button" tabindex="0" @click="goNav('/portal/bidding')" @keydown.enter="goNav('/portal/bidding')">
        <span class="ic">≡</span>
        <div class="n-name">公告大厅</div>
        <div class="n-sub">外部平台公告 · 查询 · 详情</div>
      </div>
      <div class="nav-item on kbd-btn" role="button" tabindex="0" @click="goNav('/portal/bidding/internal-hall')" @keydown.enter="goNav('/portal/bidding/internal-hall')">
        <span class="ic">◈</span>
        <div class="n-name">本平台发布</div>
        <div class="n-sub">招标人公告 · 大厅公开</div>
      </div>
      <div class="nav-item pub kbd-btn" role="button" tabindex="0" @click="goNav('/portal/bidding/publish')" @keydown.enter="goNav('/portal/bidding/publish')">
        <span class="ic">✎</span>
        <div class="n-name">发布公告</div>
        <div class="n-sub">起草 · 四类公告 · 发布/撤下</div>
      </div>
      <div class="nav-item kbd-btn" role="button" tabindex="0" @click="goNav('/portal/bidding/subscription')" @keydown.enter="goNav('/portal/bidding/subscription')">
        <span class="ic">✦</span>
        <div class="n-name">我的关注</div>
        <div class="n-sub">关注的公告/项目 · 动态提醒</div>
      </div>
      <div class="nav-item kbd-btn" role="button" tabindex="0" @click="goNav('/portal/bidding/profile')" @keydown.enter="goNav('/portal/bidding/profile')">
        <span class="flag">二期</span><span class="ic">◎</span>
        <div class="n-name">个人中心</div>
        <div class="n-sub">自身信息 · 实名认证</div>
      </div>
    </div>

    <div class="panel">
      <div class="panel-head">
        <div>
          <span class="pt">招标人发布公告（本平台 · 大厅公开）</span>
          <span v-if="biddingConfig.useMock" class="mock-tag">演示 Mock 数据</span>
        </div>
        <div class="kwline">
          <input v-model="keyword" placeholder="关键词（标题 / 项目 / 发布单位）" @keyup.enter="search" />
          <input v-model="projectCode" placeholder="项目编号（精确）" @keyup.enter="search" />
          <button class="btn primary" @click="search">搜索</button>
        </div>
      </div>

      <!-- 可见范围：internalFlag=true 仅本租户 / false 对社会公开 / 不选=全部（两类混排时的主要痛点） -->
      <div class="chip-row">
        <span class="chip-label">可见范围</span>
        <span
          v-for="f in SCOPE_FILTERS"
          :key="f.value"
          class="chip kbd-btn"
          role="button"
          tabindex="0"
          :class="{ on: scopeFilter === f.value }"
          @click="pickScope(f.value)"
          @keydown.enter.prevent="pickScope(f.value)"
        >{{ f.label }}</span>
      </div>

      <!-- 发布时间：只做下界（见 publishTimeStartOf 注释） -->
      <div class="chip-row">
        <span class="chip-label">发布时间</span>
        <span
          v-for="f in TIME_FILTERS"
          :key="f.days"
          class="chip kbd-btn"
          role="button"
          tabindex="0"
          :class="{ on: timeDays === f.days }"
          @click="pickTime(f.days)"
          @keydown.enter.prevent="pickTime(f.days)"
        >{{ f.label }}</span>
      </div>

      <div class="chip-row">
        <span class="chip-label">公告类型</span>
        <span
          v-for="f in TYPE_FILTERS"
          :key="f.value"
          class="chip kbd-btn"
          role="button"
          tabindex="0"
          :class="{ on: typeFilter === f.value }"
          @click="pickType(f.value)"
          @keydown.enter.prevent="pickType(f.value)"
        >{{ f.label }}</span>
      </div>

      <div v-if="errorMsg" class="err">{{ errorMsg }}</div>
      <div v-else-if="loading" class="hint">加载中…</div>
      <div v-else-if="!rows.length" class="hint">暂无本平台发布的公告 — 平台/招标人在「管理端 · 公告/公示管理」发布后即在此公开</div>
      <div v-else class="rows">
        <div v-for="r in rows" :key="String(r.id)" class="row kbd-btn" role="button" tabindex="0" @click="open(r.id)" @keydown.enter="open(r.id)">
          <div class="row-title">
            <span class="tt">{{ r.title }}</span>
            <span class="tag-type">{{ r.noticeTypeName || r.noticeType }}</span>
            <!-- 可见范围：internalFlag=true 仅本租户可见（08 §4）；false/未给=对外社会公开。
                 两类公告都在这一个大厅里，不给标记的话用户无从判断"这条会不会对社会公开"。 -->
            <span v-if="r.internalFlag === true" class="tag-scope" title="仅本企业租户可见，不对社会公开">仅本租户</span>
          </div>
          <div class="row-meta">
            <span class="pub">{{ r.publisherName }}</span>
            <span v-if="r.projectCode" class="code">{{ r.projectCode }}</span>
            <span v-if="r.publishTime" class="time">{{ fmtDate(r.publishTime) }}</span>
            <span class="status">已发布</span>
          </div>
          <div v-if="r.contentPreview" class="row-prev">{{ r.contentPreview }}</div>
        </div>
      </div>

      <div v-if="total > pageSize" class="pages">
        <span class="pg-info">共 {{ total }} 条</span>
        <button class="pg-btn" :disabled="pageNo <= 1" @click="goPage(pageNo - 1)">上一页</button>
        <span class="pg-now">{{ pageNo }} / {{ totalPages }}</span>
        <button class="pg-btn" :disabled="pageNo >= totalPages" @click="goPage(pageNo + 1)">下一页</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.bidding-page { max-width: 1080px; margin: 0 auto; padding: 18px 16px 40px; }
/* 键盘可达性：可点击 span/div 聚焦时的可见焦点环（与主大厅 index.vue 同规格） */
.kbd-btn:focus-visible { outline: 2px solid hsl(var(--primary)); outline-offset: 2px; }
.main-nav { display: flex; gap: 12px; margin-bottom: 14px; }
.nav-item { flex: 1; border: 1px solid hsl(var(--border)); border-radius: 4px; padding: 12px 14px; cursor: pointer; background: hsl(var(--card)); text-align: center; }
.nav-item:hover { border-color: hsl(var(--primary)); }
.nav-item .ic { display: block; font-size: 20px; }
.nav-item .n-name { font-weight: 600; margin-top: 4px; }
.nav-item .n-sub { font-size: 12px; color: hsl(var(--muted-foreground)); margin-top: 2px; }
.nav-item.on { border-color: hsl(var(--primary)); background: hsl(var(--primary) / 8%); }
.nav-item.pub { border-color: var(--ind-warn); background: hsl(var(--warning) / 10%); }
.nav-item.pub:hover { border-color: var(--ind-warn); }
.nav-item.pub .ic { color: var(--ind-warn); }
.nav-item.pub .n-name { color: var(--ind-warn); }
.flag { position: absolute; }
.panel { background: hsl(var(--card)); border: 1px solid hsl(var(--border)); border-radius: 10px; padding: 16px 18px; }
.panel-head { display: flex; justify-content: space-between; align-items: center; gap: 16px; margin-bottom: 12px; }
.pt { font-size: 18px; font-weight: 700; margin-right: 8px; }
.mock-tag { background: hsl(var(--warning) / 15%); color: var(--ind-warn); font-size: 12px; padding: 2px 8px; border-radius: 999px; }
.kwline { display: flex; gap: 8px; }
.kwline input { width: 280px; border: 1px solid hsl(var(--border)); border-radius: 6px; padding: 7px 10px; font-size: 13px; outline: none; }
.btn.primary { background: hsl(var(--primary)); color: hsl(var(--primary-foreground)); border: none; border-radius: 6px; padding: 0 14px; font-size: 13px; cursor: pointer; }
.chip-row { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 12px; }
.chip { border: 1px solid hsl(var(--border)); border-radius: 999px; padding: 4px 12px; font-size: 12px; color: hsl(var(--muted-foreground)); cursor: pointer; }
.chip.on { border-color: hsl(var(--primary)); color: hsl(var(--primary)); background: hsl(var(--primary) / 8%); }
.err { color: hsl(var(--destructive)); font-size: 13px; padding: 12px 0; }
.hint { color: hsl(var(--muted-foreground)); font-size: 13px; padding: 40px 0; text-align: center; }
.rows .row { border: 1px solid hsl(var(--border)); border-radius: 8px; padding: 10px 12px; margin-bottom: 8px; cursor: pointer; }
.rows .row:hover { border-color: hsl(var(--primary)); background: hsl(var(--primary) / 5%); }
.row-title { display: flex; align-items: center; gap: 8px; }
.row-title .tt { font-weight: 600; color: hsl(var(--foreground)); }
.tag-type { font-size: 11px; color: hsl(var(--primary)); background: hsl(var(--primary) / 8%); border: 1px solid hsl(var(--primary) / 30%); border-radius: 4px; padding: 1px 6px; }
.row-meta { display: flex; gap: 12px; margin-top: 6px; font-size: 12px; color: hsl(var(--muted-foreground)); }
.chip-label { align-self: center; margin-right: 2px; font-size: 12px; color: hsl(var(--muted-foreground)); }
.tag-scope { font-size: 11px; color: var(--ind-warn); background: hsl(var(--warning) / 12%); border: 1px solid hsl(var(--warning) / 40%); border-radius: 4px; padding: 1px 6px; }

.row-meta .code { font-family: ui-monospace, monospace; }
.row-meta .time { color: hsl(var(--muted-foreground)); }
.status { color: var(--ind-ok); }
.row-prev { margin-top: 6px; font-size: 12px; color: hsl(var(--muted-foreground)); line-height: 1.5; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.pages { display: flex; align-items: center; justify-content: flex-end; gap: 10px; margin-top: 12px; font-size: 12px; color: hsl(var(--muted-foreground)); }
.pg-btn { border: 1px solid hsl(var(--border)); background: hsl(var(--card)); border-radius: 6px; padding: 4px 10px; cursor: pointer; font-size: 12px; }
.pg-btn:disabled { color: hsl(var(--muted-foreground) / 60%); cursor: not-allowed; }
</style>
