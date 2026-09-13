<!--
  招投标门户 · 招标人发布公告详情（notice public/detail）— 自研渲染器 NoticeHtmlView（L1-L3 分层）
  数据源：公开详情（@PermitAll，仅 PUBLISHED+社会公开；payload 已 SOCIAL 裁剪）
-->
<script lang="ts" setup>
import type { InternalNoticeDetail } from './types';

import { onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import NoticeHtmlView from '#/views/bidding/notice/NoticeHtmlView.vue';

import { fetchInternalNoticeDetail } from './api';

const route = useRoute();
const router = useRouter();
const id = String(route.params.id || '');
const loading = ref(true);
const errorMsg = ref('');
const detail = ref<InternalNoticeDetail>();

async function load(): Promise<void> {
  loading.value = true;
  errorMsg.value = '';
  try {
    detail.value = await fetchInternalNoticeDetail(id);
  } catch (e) {
    errorMsg.value = (e as Error).message || String(e);
  } finally {
    loading.value = false;
  }
}
onMounted(() => void load());
</script>

<template>
<!--
  门户 · 本平台发布-公告详情（复用 NoticeHtmlView 自研渲染器）
  业务：管理端/门户共用渲染组件 NoticeHtmlView：权威字段表+正文+版式文件(PDF/L2附件/L3获取指引)分层展示。
  理由：公告=对外法律文书；版式 PDF 为对外最终件；文件可见性分层避免无条件下载；文本节点输出防 XSS。
    依据（法规/政府文件/法律）：
      · 招标公告和公示信息发布管理办法（发改委令第10号，部门规章/政府文件）第12/13条——免费、及时查阅完整公告公示信息；免费归类与检索、新公告醒目标识；
      · 电子招标投标办法（八部委令第20号，部门规章/政府文件）第9条——市场主体免费注册登录、获取依法公开的招标投标信息；
      · 招标投标法 第16条（法律）——公开招标应当发布招标公告（公告公开性的上位依据）。
    依据（技术规范）：GB 32100-2015（主体 uscc 原样展示，不改写）。
    后端契约：08 口径 §4；docs/公告发布-字段对齐与自研渲染器-附件公开-2026-09-10.md §二/§三（L1/L2/L3）；NoticeDetailRespVO。
-->

  <div class="bidding-page bid-ind">
    <button class="back" @click="router.push('/portal/bidding/internal-hall').catch(() => {})">‹ 返回本平台发布</button>

    <div v-if="loading" class="hint">加载中…</div>
    <div v-else-if="errorMsg" class="err">{{ errorMsg }}</div>
    <div v-else-if="detail" class="paper">
      <NoticeHtmlView :detail="detail" />
    </div>
  </div>
</template>

<style scoped>
.bidding-page { max-width: 880px; margin: 0 auto; padding: 16px 16px 48px; }
.back { border: none; background: none; color: #1d4ed8; font-size: 13px; cursor: pointer; margin-bottom: 10px; }
.hint { color: #9ca3af; text-align: center; padding: 60px 0; font-size: 13px; }
.err { color: #dc2626; padding: 30px 0; font-size: 13px; text-align: center; }
.paper { background: #fff; border: 1px solid #e4e7ec; border-radius: 10px; padding: 26px 32px; }
</style>
