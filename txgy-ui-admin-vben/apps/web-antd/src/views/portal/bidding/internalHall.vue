<!--
  招投标门户 · 本平台发布（招标人自营公开公告；notice public 域）
  数据源：真实接口 /bidding/notice/public/list（@PermitAll，仅 PUBLISHED + 社会公开）或内置 Mock
-->
<script lang="ts" setup>
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import { fetchInternalNotices } from './api';
import { biddingConfig } from './config';
import { fmtDate } from './format';
import type { InternalNoticeItem } from './types';

const router = useRouter();
const loading = ref(false);
const errorMsg = ref('');
const keyword = ref('');
const typeFilter = ref('');
const rows = ref<InternalNoticeItem[]>([]);
const total = ref(0);
const pageNo = ref(1);
const pageSize = 10;
const totalPages = ref(1);
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
      GET /bidding/notice/public/page、/public/detail（跨租户只放行 PUBLISHED+非内部+到发布时间+SOCIAL，08 §8）。
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
          <input v-model="keyword" placeholder="关键词（标题 / 项目 / 发布单位 / 编号）" @keyup.enter="search" />
          <button class="btn primary" @click="search">搜索</button>
        </div>
      </div>

      <div class="chip-row">
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
.kbd-btn:focus-visible { outline: 2px solid #17539a; outline-offset: 2px; }
.main-nav { display: flex; gap: 12px; margin-bottom: 14px; }
.nav-item { flex: 1; border: 1px solid #e4e7ec; border-radius: 4px; padding: 12px 14px; cursor: pointer; background: #fff; text-align: center; }
.nav-item:hover { border-color: #17539a; }
.nav-item .ic { display: block; font-size: 20px; }
.nav-item .n-name { font-weight: 600; margin-top: 4px; }
.nav-item .n-sub { font-size: 12px; color: #909399; margin-top: 2px; }
.nav-item.on { border-color: #17539a; background: #eff4ff; }
.nav-item.pub { border-color: #b45309; background: #fff7ed; }
.nav-item.pub:hover { border-color: #b45309; }
.nav-item.pub .ic { color: #b45309; }
.nav-item.pub .n-name { color: #92400e; }
.flag { position: absolute; }
.panel { background: #fff; border: 1px solid #e4e7ec; border-radius: 10px; padding: 16px 18px; }
.panel-head { display: flex; justify-content: space-between; align-items: center; gap: 16px; margin-bottom: 12px; }
.pt { font-size: 18px; font-weight: 700; margin-right: 8px; }
.mock-tag { background: #fef3c7; color: #92400e; font-size: 12px; padding: 2px 8px; border-radius: 999px; }
.kwline { display: flex; gap: 8px; }
.kwline input { width: 280px; border: 1px solid #d9dde3; border-radius: 6px; padding: 7px 10px; font-size: 13px; outline: none; }
.btn.primary { background: #17539a; color: #fff; border: none; border-radius: 6px; padding: 0 14px; font-size: 13px; cursor: pointer; }
.chip-row { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 12px; }
.chip { border: 1px solid #d9dde3; border-radius: 999px; padding: 4px 12px; font-size: 12px; color: #4b5563; cursor: pointer; }
.chip.on { border-color: #17539a; color: #17539a; background: #eff4ff; }
.err { color: #dc2626; font-size: 13px; padding: 12px 0; }
.hint { color: #9ca3af; font-size: 13px; padding: 40px 0; text-align: center; }
.rows .row { border: 1px solid #eef0f3; border-radius: 8px; padding: 10px 12px; margin-bottom: 8px; cursor: pointer; }
.rows .row:hover { border-color: #17539a; background: #fafcff; }
.row-title { display: flex; align-items: center; gap: 8px; }
.row-title .tt { font-weight: 600; color: #1f2937; }
.tag-type { font-size: 11px; color: #17539a; background: #eff4ff; border: 1px solid #c7d7f7; border-radius: 4px; padding: 1px 6px; }
.row-meta { display: flex; gap: 12px; margin-top: 6px; font-size: 12px; color: #6b7280; }
.row-meta .code { font-family: ui-monospace, monospace; }
.row-meta .time { color: #9ca3af; }
.status { color: #16a34a; }
.row-prev { margin-top: 6px; font-size: 12px; color: #9ca3af; line-height: 1.5; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.pages { display: flex; align-items: center; justify-content: flex-end; gap: 10px; margin-top: 12px; font-size: 12px; color: #6b7280; }
.pg-btn { border: 1px solid #d9dde3; background: #fff; border-radius: 6px; padding: 4px 10px; cursor: pointer; font-size: 12px; }
.pg-btn:disabled { color: #c0c4cc; cursor: not-allowed; }
</style>
